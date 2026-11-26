package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.ApiKeyStore
import com.maheshkatakam.vani.core.AppSettings
import com.maheshkatakam.vani.core.Languages
import com.maheshkatakam.vani.core.ModelKind
import com.maheshkatakam.vani.core.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class AudioOutcome(val transcript: String, val language: String, val output: String)

/**
 * Turns audio or text into polished text.
 *
 * Fast path: when Gemini does both jobs, one request hears the audio and writes the result
 * (it also returns the raw transcript, so switching styles later needs no new audio).
 * Otherwise speech-to-text and rewriting are two steps, possibly on different providers.
 * If a provider fails and fallback is on, the next provider with a key takes over.
 */
class VaniEngine(private val keys: ApiKeyStore, private val models: ModelResolver) {

    suspend fun processAudio(
        wav: ByteArray,
        task: Task,
        style: StyleSpec,
        ctx: PromptContext,
        existing: String?,
        s: AppSettings,
    ): AudioOutcome {
        val failed = mutableSetOf<Provider>()
        var firstError: Exception? = null

        if (s.sttProvider == Provider.GEMINI && s.llmProvider == Provider.GEMINI && keys.has(Provider.GEMINI)) {
            try {
                val r = geminiOneShot(wav, task, style, ctx, existing, s)
                if (r.output.isNotBlank() || r.transcript.isBlank()) return r
                // It heard words but wrote nothing: finish with a text-only rewrite of the transcript.
                return r.copy(output = rewrite(task, r.transcript, existing, style, ctx, s, mutableSetOf()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!s.providerFallback) throw e
                firstError = e
                failed += Provider.GEMINI
            }
        }

        val stt = try {
            firstSuccess(order(s.sttProvider, s), failed) { p -> transcribe(p, wav, ctx, s) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw firstError ?: e
        }
        if (stt.text.isBlank()) return AudioOutcome("", stt.language, "")

        val output = try {
            rewrite(task, stt.text, existing, style, ctx, s, failed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw firstError ?: e
        }
        return AudioOutcome(stt.text, stt.language, output)
    }

    /** Text-only: polish typed text, re-style an existing transcript, or apply a voice edit. */
    suspend fun processText(
        task: Task,
        input: String,
        existing: String?,
        style: StyleSpec,
        ctx: PromptContext,
        s: AppSettings,
    ): String = rewrite(task, input, existing, style, ctx, s, mutableSetOf())

    /** Checks a key and picks the models it will use. Returns a short human summary. */
    suspend fun testKey(p: Provider, key: String): String {
        val available = models.listModels(p, key)
        return if (p == Provider.GEMINI) {
            val m = ModelCatalog.pick(available, p, ModelKind.LLM) ?: return "Key works, but no suitable Gemini model was found."
            models.remember(p, ModelKind.LLM, m)
            "Key works. Using $m."
        } else {
            val stt = ModelCatalog.pick(available, p, ModelKind.STT)
            val llm = ModelCatalog.pick(available, p, ModelKind.LLM)
            stt?.let { models.remember(p, ModelKind.STT, it) }
            llm?.let { models.remember(p, ModelKind.LLM, it) }
            "Key works. Speech: ${stt ?: "none found"} · Writing: ${llm ?: "none found"}."
        }
    }

    private suspend fun geminiOneShot(
        wav: ByteArray,
        task: Task,
        style: StyleSpec,
        ctx: PromptContext,
        existing: String?,
        s: AppSettings,
    ): AudioOutcome = withModel(Provider.GEMINI, ModelKind.LLM, s) { key, model ->
        val raw = GeminiApi.generate(
            key = key,
            model = model,
            system = Prompts.system(task, style),
            parts = listOf(GeminiApi.audioPart(wav), GeminiApi.textPart(Prompts.audioUser(task, existing, ctx))),
            schema = GeminiApi.dictationSchema,
        )
        val json = TextCleanup.parseJsonLoose(raw)
        if (json == null) {
            AudioOutcome("", "", TextCleanup.clean(raw))
        } else {
            AudioOutcome(
                transcript = json.optString("transcript").trim(),
                language = json.optString("language").trim(),
                output = TextCleanup.clean(json.optString("output")),
            )
        }
    }

    private suspend fun transcribe(p: Provider, wav: ByteArray, ctx: PromptContext, s: AppSettings): SttResult =
        withModel(p, ModelKind.STT, s) { key, model ->
            when (p) {
                Provider.GEMINI -> {
                    val text = GeminiApi.generate(
                        key = key,
                        model = model,
                        system = Prompts.transcriptionSystem(ctx),
                        parts = listOf(GeminiApi.audioPart(wav), GeminiApi.textPart("Transcribe this audio.")),
                    )
                    SttResult(TextCleanup.clean(text), "")
                }
                else -> {
                    // Whisper only takes one language hint; send it only when the user speaks a single language.
                    val hint = s.spokenLanguages.singleOrNull()?.let { Languages.isoCodes[it] }
                        .takeIf { p == Provider.GROQ }
                    OpenAiApi.transcribe(p, key, model, wav, Prompts.sttPrompt(ctx), hint)
                }
            }
        }

    private suspend fun rewrite(
        task: Task,
        input: String,
        existing: String?,
        style: StyleSpec,
        ctx: PromptContext,
        s: AppSettings,
        failed: MutableSet<Provider>,
    ): String {
        val system = Prompts.system(task, style)
        val user = Prompts.textUser(task, input, existing, ctx)
        return firstSuccess(order(s.llmProvider, s), failed) { p ->
            withModel(p, ModelKind.LLM, s) { key, model ->
                val raw = when (p) {
                    Provider.GEMINI -> GeminiApi.generate(key, model, system, listOf(GeminiApi.textPart(user)))
                    Provider.OPENAI -> OpenAiApi.respond(key, model, system, user)
                    Provider.GROQ -> OpenAiApi.chat(p, key, model, system, user)
                }
                TextCleanup.clean(raw)
            }
        }
    }

    private fun order(primary: Provider, s: AppSettings): List<Provider> =
        if (s.providerFallback) listOf(primary) + Provider.entries.filter { it != primary } else listOf(primary)

    private suspend fun <T> firstSuccess(
        order: List<Provider>,
        failed: MutableSet<Provider>,
        block: suspend (Provider) -> T,
    ): T {
        var firstError: Exception? = null
        for (p in order) {
            if (p in failed || !keys.has(p)) continue
            try {
                return block(p)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
                failed += p
            }
        }
        throw firstError ?: EngineException(
            "Add your ${order.first().label} API key in Vani → Settings → AI engine (it's free to get one)."
        )
    }

    /**
     * Runs [block] with the right model. If the model doesn't exist (or has no free quota) on this
     * account, finds a replacement once. Server hiccups get one quick retry.
     */
    private suspend fun <T> withModel(
        p: Provider,
        kind: ModelKind,
        s: AppSettings,
        block: suspend (key: String, model: String) -> T,
    ): T {
        val key = keys.get(p)
        if (key.isBlank()) throw EngineException("Add your ${p.label} API key in Vani → Settings → AI engine.")
        val override = s.modelOverride(p, kind)
        var model = override.ifBlank { models.current(p, kind) }
        var discovered = false
        var retried = false
        while (true) {
            try {
                return block(key, model)
            } catch (e: ApiException) {
                when {
                    (e.isNotFound || e.isQuotaZero) && override.isBlank() && !discovered -> {
                        discovered = true
                        model = runCatching { models.discover(p, kind, key, exclude = setOf(model)) }.getOrNull() ?: throw e
                    }
                    (e.isServer || (e.code == 429 && !e.isQuotaZero)) && !retried -> {
                        retried = true
                        delay(900)
                    }
                    else -> throw e
                }
            }
        }
    }
}
