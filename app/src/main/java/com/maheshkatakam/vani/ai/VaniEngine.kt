package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.audio.AudioClip
import com.maheshkatakam.vani.core.ApiKeyStore
import com.maheshkatakam.vani.core.AppSettings
import com.maheshkatakam.vani.core.Languages
import com.maheshkatakam.vani.core.ModelKind
import com.maheshkatakam.vani.core.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.net.SocketTimeoutException

data class AudioOutcome(val transcript: String, val language: String, val output: String)

enum class Stage { SENDING, THINKING, WRITING }

/** Live status for the sheet: what's happening, upload progress, and text as it streams in. */
data class Progress(
    val stage: Stage,
    val fraction: Float = 0f,
    val heard: String = "",
    val draft: String = "",
    val note: String? = null,
)

/**
 * Turns audio or text into polished text.
 *
 * Fast path: when Gemini does both jobs, one streamed request hears the audio and writes the result
 * (it also returns the raw transcript, so switching styles later needs no new audio).
 * Otherwise speech-to-text and rewriting are two steps, possibly on different providers.
 * If a provider fails and fallback is on, the next provider with a key takes over; if a Gemini
 * model is too slow, Vani retries once on the fastest Flash-Lite model.
 */
class VaniEngine(private val keys: ApiKeyStore, private val models: ModelResolver) {

    /** Set once a provider rejects the compressed (AAC) upload; from then on WAV is sent. */
    @Volatile private var compressedRejected = false

    suspend fun processAudio(
        clip: AudioClip,
        task: Task,
        style: StyleSpec,
        ctx: PromptContext,
        existing: String?,
        s: AppSettings,
        onProgress: (Progress) -> Unit = {},
    ): AudioOutcome {
        val failed = mutableSetOf<Provider>()
        var firstError: Exception? = null

        if (s.sttProvider == Provider.GEMINI && s.llmProvider == Provider.GEMINI && keys.has(Provider.GEMINI)) {
            try {
                val r = geminiOneShot(clip, task, style, ctx, existing, s, onProgress)
                if (r.output.isNotBlank() || r.transcript.isBlank()) return r
                // It heard words but wrote nothing: finish with a text-only rewrite of the transcript.
                return r.copy(output = rewrite(task, r.transcript, existing, style, ctx, s, mutableSetOf(), onProgress))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!s.providerFallback) throw e
                firstError = e
                failed += Provider.GEMINI
            }
        }

        val stt = try {
            firstSuccess(order(s.sttProvider, s), failed) { p -> transcribe(p, clip, ctx, s, onProgress) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw firstError ?: e
        }
        if (stt.text.isBlank()) return AudioOutcome("", stt.language, "")
        onProgress(Progress(Stage.THINKING, 1f, heard = stt.text))

        val output = try {
            rewrite(task, stt.text, existing, style, ctx, s, failed, onProgress, heard = stt.text)
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
        onProgress: (Progress) -> Unit = {},
    ): String = rewrite(task, input, existing, style, ctx, s, mutableSetOf(), onProgress)

    /** Checks a key and picks the models it will use. Returns a short human summary. */
    suspend fun testKey(p: Provider, key: String): String {
        val available = models.listModels(p, key)
        return if (p == Provider.GEMINI) {
            val m = ModelCatalog.pick(available, p, ModelKind.LLM) ?: return "Key works, but no suitable Gemini model was found."
            models.remember(p, ModelKind.LLM, m)
            "Key works. Automatic model: $m."
        } else {
            val stt = ModelCatalog.pick(available, p, ModelKind.STT)
            val llm = ModelCatalog.pick(available, p, ModelKind.LLM)
            stt?.let { models.remember(p, ModelKind.STT, it) }
            llm?.let { models.remember(p, ModelKind.LLM, it) }
            "Key works. Speech: ${stt ?: "none found"} · Writing: ${llm ?: "none found"}."
        }
    }

    // ------------------------------------------------------------------ Gemini

    private suspend fun geminiOneShot(
        clip: AudioClip,
        task: Task,
        style: StyleSpec,
        ctx: PromptContext,
        existing: String?,
        s: AppSettings,
        onProgress: (Progress) -> Unit,
    ): AudioOutcome {
        val system = Prompts.system(task, style, tagged = true)
        val user = Prompts.audioUser(task, existing, ctx)
        return withModel(Provider.GEMINI, ModelKind.LLM, s) { key, model ->
            withSpeedFallback(key, model, onProgress) { m ->
                withAudioFallback(clip, { r: AudioOutcome -> r.transcript.isBlank() && r.output.isBlank() }) { bytes, mime ->
                    val raw = GeminiApi.stream(
                        key = key,
                        model = m,
                        system = system,
                        parts = listOf(GeminiApi.audioPart(bytes, mime), GeminiApi.textPart(user)),
                        what = "dictation, ${clip.durationMs / 1000}s audio as ${mime.substringAfter('/')}",
                        onText = { text ->
                            val t = TextCleanup.parseTagged(text)
                            onProgress(
                                Progress(
                                    stage = if (t.output.isEmpty()) Stage.THINKING else Stage.WRITING,
                                    fraction = 1f,
                                    heard = t.transcript,
                                    draft = t.output,
                                )
                            )
                        },
                        onUpload = { f -> onProgress(Progress(if (f >= 1f) Stage.THINKING else Stage.SENDING, f)) },
                    )
                    val t = TextCleanup.parseTagged(raw)
                    AudioOutcome(t.transcript, t.language, TextCleanup.clean(t.output))
                }
            }
        }
    }

    /** If a Gemini model goes quiet for too long, retry once on the fastest Flash-Lite model. */
    private suspend fun <T> withSpeedFallback(
        key: String,
        model: String,
        onProgress: (Progress) -> Unit,
        block: suspend (String) -> T,
    ): T = try {
        block(model)
    } catch (e: SocketTimeoutException) {
        val fast = models.fastestGemini(key)
        if (fast == null || fast == model) throw e
        onProgress(Progress(Stage.SENDING, 0f, note = "$model was too slow, so Vani switched to $fast."))
        block(fast)
    }

    /**
     * Sends the small AAC file first. If the provider rejects it, or hears nothing in it (we know there
     * was speech), the original WAV is sent instead; a rejection is remembered for next time.
     */
    private suspend fun <T> withAudioFallback(
        clip: AudioClip,
        heardNothing: (T) -> Boolean,
        block: suspend (ByteArray, String) -> T,
    ): T {
        val m4a = clip.m4a
        if (m4a == null || compressedRejected) return block(clip.wav, "audio/wav")
        val result = try {
            block(m4a, "audio/mp4")
        } catch (e: ApiException) {
            val audioProblem = e.code == 400 &&
                (e.body.contains("mime", ignoreCase = true) || e.body.contains("audio", ignoreCase = true))
            if (!audioProblem) throw e
            compressedRejected = true
            return block(clip.wav, "audio/wav")
        }
        return if (heardNothing(result)) block(clip.wav, "audio/wav") else result
    }

    // ------------------------------------------------------------------ Two-step path

    private suspend fun transcribe(
        p: Provider,
        clip: AudioClip,
        ctx: PromptContext,
        s: AppSettings,
        onProgress: (Progress) -> Unit,
    ): SttResult = withModel(p, ModelKind.STT, s) { key, model ->
        onProgress(Progress(Stage.SENDING, 0f))
        when (p) {
            Provider.GEMINI -> withSpeedFallback(key, model, onProgress) { m ->
                withAudioFallback(clip, { r: SttResult -> r.text.isBlank() }) { bytes, mime ->
                    val text = GeminiApi.stream(
                        key = key,
                        model = m,
                        system = Prompts.transcriptionSystem(ctx),
                        parts = listOf(GeminiApi.audioPart(bytes, mime), GeminiApi.textPart("Transcribe this audio.")),
                        what = "speech-to-text",
                        onText = { onProgress(Progress(Stage.THINKING, 1f, heard = it)) },
                        onUpload = { f -> onProgress(Progress(if (f >= 1f) Stage.THINKING else Stage.SENDING, f)) },
                    )
                    SttResult(TextCleanup.clean(text), "")
                }
            }
            else -> {
                // Whisper only takes one language hint; send it only when the user speaks a single language.
                val hint = s.spokenLanguages.singleOrNull()?.let { Languages.isoCodes[it] }
                    .takeIf { p == Provider.GROQ }
                withAudioFallback(clip, { r: SttResult -> r.text.isBlank() }) { bytes, mime ->
                    val name = if (mime == "audio/wav") "speech.wav" else "speech.m4a"
                    OpenAiApi.transcribe(p, key, model, bytes, mime, name, Prompts.sttPrompt(ctx), hint)
                }
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
        onProgress: (Progress) -> Unit,
        heard: String = "",
    ): String {
        val system = Prompts.system(task, style)
        val user = Prompts.textUser(task, input, existing, ctx)
        return firstSuccess(order(s.llmProvider, s), failed) { p ->
            withModel(p, ModelKind.LLM, s) { key, model ->
                onProgress(Progress(Stage.THINKING, 1f, heard = heard))
                val raw = when (p) {
                    Provider.GEMINI -> withSpeedFallback(key, model, onProgress) { m ->
                        GeminiApi.stream(
                            key = key,
                            model = m,
                            system = system,
                            parts = listOf(GeminiApi.textPart(user)),
                            what = "rewrite",
                            onText = { onProgress(Progress(Stage.WRITING, 1f, heard = heard, draft = TextCleanup.stripPartialTag(it))) },
                        )
                    }
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
        val override = ModelCatalog.normalizeId(s.modelOverride(p, kind))
        var model = override.ifBlank { models.current(p, kind) }
        var discovered = false
        var retried = false
        while (true) {
            try {
                return block(key, model)
            } catch (e: ApiException) {
                when {
                    (e.isNotFound || e.isQuotaZero) && !discovered -> {
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
