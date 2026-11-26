package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.Provider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Google Gemini via REST. Answers are streamed (streamGenerateContent + server-sent events) so the
 * text appears while Gemini is still writing, instead of a long silent wait.
 */
object GeminiApi {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Thinking level that worked for each model ("" = send none), so we never pay for a rejected request twice. */
    private val thinkingThatWorked = ConcurrentHashMap<String, String>()

    fun audioPart(bytes: ByteArray, mime: String): JSONObject = JSONObject().put(
        "inlineData",
        JSONObject().put("mimeType", mime).put("data", Base64.getEncoder().encodeToString(bytes)),
    )

    fun textPart(text: String): JSONObject = JSONObject().put("text", text)

    /**
     * The lowest thinking level the model accepts comes first: dictation needs speed, not deep reasoning.
     * Values use the API's enum spelling (MINIMAL, LOW). Gemini 3.8 Flash and Gemini 4 don't offer MINIMAL.
     */
    fun thinkingCandidates(model: String): List<String?> {
        val m = model.lowercase()
        thinkingThatWorked[m]?.let { return listOf(it.ifEmpty { null }) }
        return when {
            m.startsWith("gemini-2") || m.startsWith("gemini-1") -> listOf(null)
            m.contains("lite") -> listOf("MINIMAL", "LOW", null)
            m.startsWith("gemini-3.8") || m.startsWith("gemini-3.7") || m.startsWith("gemini-4") -> listOf("LOW", null)
            else -> listOf("MINIMAL", "LOW", null)
        }
    }

    /**
     * Streams one answer. [onText] gets the full text so far after each chunk; [onUpload] gets 0..1
     * while the request (including audio) is being sent.
     */
    suspend fun stream(
        key: String,
        model: String,
        system: String,
        parts: List<JSONObject>,
        what: String,
        onText: (String) -> Unit = {},
        onUpload: (Float) -> Unit = {},
    ): String {
        var last: ApiException? = null
        for (thinking in thinkingCandidates(model)) {
            try {
                val text = streamOnce(key, model, system, parts, thinking, what, onText, onUpload)
                thinkingThatWorked[model.lowercase()] = thinking.orEmpty()
                return text
            } catch (e: ApiException) {
                if (e.code == 400 && thinking != null && e.body.contains("thinking", ignoreCase = true)) {
                    last = e
                    continue
                }
                throw e
            }
        }
        throw last ?: EngineException("Gemini request failed")
    }

    private suspend fun streamOnce(
        key: String,
        model: String,
        system: String,
        parts: List<JSONObject>,
        thinking: String?,
        what: String,
        onText: (String) -> Unit,
        onUpload: (Float) -> Unit,
    ): String {
        val userParts = JSONArray().apply { parts.forEach { put(it) } }
        val generationConfig = JSONObject().apply {
            put("maxOutputTokens", 8192)
            // Temperature is ignored by Gemini 3.6 Flash and newer; it still helps older models stay faithful.
            if (model.startsWith("gemini-2")) put("temperature", 0.3)
            if (thinking != null) put("thinkingConfig", JSONObject().put("thinkingLevel", thinking))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(textPart(system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
            .put("generationConfig", generationConfig)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val request = Request.Builder()
            .url("$BASE/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", key)
            .post(ProgressBody(body, JSON, onUpload))
            .build()

        val sb = StringBuilder()
        var blockReason = ""
        var finishReason = ""
        Http.sse(Provider.GEMINI, request, model, what) { data ->
            val o = runCatching { JSONObject(data) }.getOrNull() ?: return@sse
            o.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let { blockReason = it }
            val candidate = o.optJSONArray("candidates")?.optJSONObject(0) ?: return@sse
            candidate.optString("finishReason").takeIf { it.isNotBlank() }?.let { finishReason = it }
            val chunkParts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return@sse
            var added = false
            for (i in 0 until chunkParts.length()) {
                val p = chunkParts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                val t = p.optString("text")
                if (t.isNotEmpty()) {
                    sb.append(t)
                    added = true
                }
            }
            if (added) onText(sb.toString())
        }
        if (sb.isBlank()) {
            when {
                blockReason.isNotBlank() -> throw EngineException("Gemini blocked this request ($blockReason).")
                finishReason in setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII") ->
                    throw EngineException("Gemini blocked this answer ($finishReason).")
                finishReason == "MAX_TOKENS" -> throw EngineException("Gemini ran out of room for the answer. Try a shorter dictation.")
            }
        }
        return sb.toString()
    }

    /** Model ids this key can use for generateContent, e.g. "gemini-3.6-flash". */
    suspend fun listModels(key: String): List<String> {
        val request = Request.Builder()
            .url("$BASE/models?pageSize=1000")
            .header("x-goog-api-key", key)
            .get()
            .build()
        val root = JSONObject(Http.call(Provider.GEMINI, request, what = "list models"))
        val arr = root.optJSONArray("models") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val methods = m.optJSONArray("supportedGenerationMethods")
            val canGenerate = methods == null || (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            if (!canGenerate) null else m.optString("name").removePrefix("models/").ifBlank { null }
        }
    }
}
