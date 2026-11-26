package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.Provider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Google Gemini via the REST generateContent endpoint. */
object GeminiApi {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun audioPart(wav: ByteArray): JSONObject = JSONObject().put(
        "inlineData",
        JSONObject().put("mimeType", "audio/wav").put("data", Base64.getEncoder().encodeToString(wav)),
    )

    fun textPart(text: String): JSONObject = JSONObject().put("text", text)

    /** JSON schema for the one-shot call: transcript first, so the model "listens" before it writes. */
    val dictationSchema: JSONObject
        get() = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("transcript", JSONObject().put("type", "STRING"))
                    .put("language", JSONObject().put("type", "STRING"))
                    .put("output", JSONObject().put("type", "STRING")),
            )
            .put("required", JSONArray().put("transcript").put("language").put("output"))
            .put("propertyOrdering", JSONArray().put("transcript").put("language").put("output"))

    /**
     * Calls generateContent. Gemini 3 models get a low thinking level for speed; if a model
     * rejects that setting we retry without it, so older or newer models keep working.
     */
    suspend fun generate(
        key: String,
        model: String,
        system: String,
        parts: List<JSONObject>,
        schema: JSONObject? = null,
    ): String {
        val usesThinkingLevel = model.startsWith("gemini-3") || model.contains("-latest")
        val thinkingOptions: List<String?> = if (usesThinkingLevel) listOf("low", "LOW", null) else listOf(null)
        var last: ApiException? = null
        for (thinking in thinkingOptions) {
            try {
                return generateOnce(key, model, system, parts, schema, thinking)
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

    private suspend fun generateOnce(
        key: String,
        model: String,
        system: String,
        parts: List<JSONObject>,
        schema: JSONObject?,
        thinking: String?,
    ): String {
        val userParts = JSONArray().apply { parts.forEach { put(it) } }
        val generationConfig = JSONObject().apply {
            put("maxOutputTokens", 8192)
            // Gemini 3 models are tuned for the default temperature; older ones do better a bit lower.
            if (!model.startsWith("gemini-3")) put("temperature", 0.3)
            if (schema != null) {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
            }
            if (thinking != null) put("thinkingConfig", JSONObject().put("thinkingLevel", thinking))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(textPart(system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
            .put("generationConfig", generationConfig)

        val request = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON))
            .build()
        return parse(Http.call(Provider.GEMINI, request))
    }

    private fun parse(resp: String): String {
        val root = JSONObject(resp)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val block = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw EngineException(
                if (block.isNotBlank()) "Gemini blocked this request ($block)." else "Gemini returned no answer. Try again."
            )
        }
        val first = candidates.getJSONObject(0)
        val parts = first.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text"))
            }
        }
        if (sb.isBlank()) {
            when (val reason = first.optString("finishReason")) {
                "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST" -> throw EngineException("Gemini blocked this answer ($reason).")
                "MAX_TOKENS" -> throw EngineException("Gemini ran out of room for the answer. Try a shorter dictation.")
            }
        }
        return sb.toString()
    }

    /** Model ids this key can use for generateContent, e.g. "gemini-3.8-flash". */
    suspend fun listModels(key: String): List<String> {
        val request = Request.Builder()
            .url("$BASE/models?pageSize=1000")
            .header("x-goog-api-key", key)
            .get()
            .build()
        val root = JSONObject(Http.call(Provider.GEMINI, request))
        val arr = root.optJSONArray("models") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val methods = m.optJSONArray("supportedGenerationMethods")
            val canGenerate = methods == null || (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            if (!canGenerate) null else m.optString("name").removePrefix("models/").ifBlank { null }
        }
    }
}
