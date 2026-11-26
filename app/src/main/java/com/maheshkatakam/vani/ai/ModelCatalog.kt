package com.maheshkatakam.vani.ai

import android.content.Context
import com.maheshkatakam.vani.core.ModelKind
import com.maheshkatakam.vani.core.Provider

/**
 * Model names change every few months, so Vani never hard-depends on one: it starts with a
 * preferred list and, if a model is missing on your account, asks the provider which models
 * exist and picks the best match.
 */
object ModelCatalog {
    // Dictation needs speed: 3.6 Flash with minimal thinking is quick and writes well. 3.8 Flash is a
    // "Smartest" option in Settings; it can't turn thinking down as far, so it's slower.
    val geminiPreferred = listOf(
        "gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.8-flash", "gemini-flash-latest",
        "gemini-3-flash-preview", "gemini-3.5-flash-lite", "gemini-2.5-flash",
    )
    val geminiFastest = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-flash-lite-latest", "gemini-2.5-flash-lite")

    data class Preset(val model: String, val title: String, val subtitle: String)

    val geminiPresets = listOf(
        Preset("", "⚡ Fast (recommended)", "Gemini 3.6 Flash with minimal thinking: quick and well written."),
        Preset("gemini-3.8-flash", "🧠 Smartest", "Gemini 3.8 Flash: best for tricky rewrites, a few seconds slower."),
        Preset("gemini-3.5-flash-lite", "🚀 Fastest", "Gemini 3.5 Flash-Lite: near-instant, simpler wording."),
    )

    /** "Gemini 3.8 Flash" or "models/gemini-3.8-flash" → "gemini-3.8-flash". */
    fun normalizeId(input: String): String = input.trim()
        .removePrefix("models/")
        .lowercase()
        .replace(Regex("[\\s_]+"), "-")
        .replace(Regex("-{2,}"), "-")
        .trim('-')

    fun pickFastestGemini(available: List<String>): String? =
        geminiFastest.firstOrNull { it in available }
            ?: available.filter { it.contains("flash-lite") && !it.contains("tts") && !it.contains("image") && !it.contains("live") }.maxOrNull()
    val openAiSttPreferred = listOf("gpt-transcribe", "gpt-4o-transcribe", "gpt-4o-mini-transcribe", "whisper-1")
    val openAiLlmPreferred = listOf(
        "gpt-6.1-terra", "gpt-6-luna", "gpt-6.2-sol", "gpt-5.1", "gpt-5-mini", "gpt-4.1-mini", "gpt-4o-mini",
    )
    val groqSttPreferred = listOf("whisper-large-v3", "whisper-large-v3-turbo")
    val groqLlmPreferred = listOf(
        "openai/gpt-oss-120b", "llama-3.3-70b-versatile", "openai/gpt-oss-20b", "llama-3.1-8b-instant",
    )

    fun preferred(p: Provider, kind: ModelKind): List<String> = when (p) {
        Provider.GEMINI -> geminiPreferred
        Provider.OPENAI -> if (kind == ModelKind.STT) openAiSttPreferred else openAiLlmPreferred
        Provider.GROQ -> if (kind == ModelKind.STT) groqSttPreferred else groqLlmPreferred
    }

    /** Best model from what the account actually has. */
    fun pick(available: List<String>, p: Provider, kind: ModelKind): String? {
        preferred(p, kind).firstOrNull { it in available }?.let { return it }
        val excluded = listOf("tts", "image", "live", "embed", "audio", "realtime", "search", "vision", "guard", "moderation")
        fun ok(id: String) = excluded.none { id.contains(it, ignoreCase = true) }
        return when (p) {
            Provider.GEMINI -> available.filter { it.contains("flash") && !it.contains("lite") && ok(it) }.maxOrNull()
                ?: available.filter { it.startsWith("gemini") && ok(it) }.maxOrNull()
            Provider.OPENAI -> if (kind == ModelKind.STT) {
                available.filter { it.contains("transcribe") && !it.contains("diarize") }.maxOrNull()
                    ?: available.firstOrNull { it.contains("whisper") }
            } else {
                available.filter { it.startsWith("gpt-") && !it.contains("transcribe") && ok(it) }.maxOrNull()
            }
            Provider.GROQ -> if (kind == ModelKind.STT) {
                available.firstOrNull { it.contains("whisper") }
            } else {
                available.firstOrNull { it.contains("gpt-oss") && ok(it) } ?: available.firstOrNull { it.contains("llama") && ok(it) }
            }
        }
    }
}

/** Remembers which model works for each provider (unless you pick one yourself in Settings). */
class ModelResolver(context: Context) {
    private val prefs = context.getSharedPreferences("vani_models", Context.MODE_PRIVATE)

    init {
        // Model preferences changed (speed first); forget automatic picks made by older versions.
        if (prefs.getInt("catalog", 1) < CATALOG_VERSION) {
            prefs.edit().clear().putInt("catalog", CATALOG_VERSION).apply()
        }
    }

    private fun key(p: Provider, kind: ModelKind) = if (p == Provider.GEMINI) "GEMINI" else "${p.name}_${kind.name}"

    fun current(p: Provider, kind: ModelKind): String =
        prefs.getString(key(p, kind), null) ?: ModelCatalog.preferred(p, kind).first()

    fun remember(p: Provider, kind: ModelKind, model: String) {
        prefs.edit().putString(key(p, kind), model).apply()
    }

    suspend fun listModels(p: Provider, apiKey: String): List<String> = when (p) {
        Provider.GEMINI -> GeminiApi.listModels(apiKey)
        else -> OpenAiApi.listModels(p, apiKey)
    }

    /** The quickest Gemini model this key can use (for when the chosen one is too slow). */
    suspend fun fastestGemini(apiKey: String): String? {
        prefs.getString("GEMINI_FAST", null)?.let { return it }
        val pick = runCatching { ModelCatalog.pickFastestGemini(listModels(Provider.GEMINI, apiKey)) }.getOrNull()
            ?: ModelCatalog.geminiFastest.first()
        prefs.edit().putString("GEMINI_FAST", pick).apply()
        return pick
    }

    /** Looks up the account's models and stores the best one. Returns null if nothing fits. */
    suspend fun discover(p: Provider, kind: ModelKind, apiKey: String, exclude: Set<String> = emptySet()): String? {
        val available = listModels(p, apiKey).filterNot { it in exclude }
        val pick = ModelCatalog.pick(available, p, kind) ?: return null
        remember(p, kind, pick)
        return pick
    }
}

private const val CATALOG_VERSION = 2
