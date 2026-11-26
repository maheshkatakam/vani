package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.Provider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SttResult(val text: String, val language: String)

/**
 * OpenAI and Groq. Both speak the OpenAI wire format for speech-to-text and model listing;
 * rewriting uses the Responses API on OpenAI and Chat Completions on Groq.
 */
object OpenAiApi {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun base(p: Provider): String = when (p) {
        Provider.GROQ -> "https://api.groq.com/openai/v1"
        else -> "https://api.openai.com/v1"
    }

    suspend fun transcribe(
        p: Provider,
        key: String,
        model: String,
        audio: ByteArray,
        mime: String,
        fileName: String,
        prompt: String,
        language: String?,
    ): SttResult {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", fileName, audio.toRequestBody(mime.toMediaType()))
        if (prompt.isNotBlank()) form.addFormDataPart("prompt", prompt)
        if (p == Provider.GROQ) {
            form.addFormDataPart("response_format", "verbose_json")
            form.addFormDataPart("temperature", "0")
        }
        if (!language.isNullOrBlank()) form.addFormDataPart("language", language)
        val request = Request.Builder()
            .url("${base(p)}/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(form.build())
            .build()
        val raw = Http.call(p, request, model, "speech-to-text")
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return SttResult(raw.trim(), "")
        val lang = o.optString("language").ifBlank {
            o.optJSONArray("languages")?.optJSONObject(0)?.optString("code").orEmpty()
        }
        return SttResult(o.optString("text").trim(), lang.replaceFirstChar { it.uppercase() })
    }

    /** OpenAI Responses API. Tries the fastest reasoning setting first and backs off if the model rejects it. */
    suspend fun respond(key: String, model: String, system: String, user: String): String {
        val efforts: List<String?> = listOf("none", "low", null)
        var last: ApiException? = null
        for (effort in efforts) {
            val body = JSONObject()
                .put("model", model)
                .put("instructions", system)
                .put("input", user)
                .put("store", false)
            if (effort != null) body.put("reasoning", JSONObject().put("effort", effort))
            val request = Request.Builder()
                .url("${base(Provider.OPENAI)}/responses")
                .header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody(JSON))
                .build()
            try {
                return parseResponses(Http.call(Provider.OPENAI, request, model, "rewrite"))
            } catch (e: ApiException) {
                if (e.code == 400 && effort != null && e.body.contains("reasoning", ignoreCase = true)) {
                    last = e
                    continue
                }
                throw e
            }
        }
        throw last ?: EngineException("OpenAI request failed")
    }

    private fun parseResponses(resp: String): String {
        val root = JSONObject(resp)
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val out = root.optJSONArray("output") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until out.length()) {
            val item = out.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val c = content.optJSONObject(j) ?: continue
                if (c.optString("type") == "output_text") sb.append(c.optString("text"))
            }
        }
        return sb.toString()
    }

    /** Chat Completions (used for Groq). Extra reasoning options are dropped if a model rejects them. */
    suspend fun chat(p: Provider, key: String, model: String, system: String, user: String): String {
        val reasoningModel = model.contains("gpt-oss") || model.contains("qwen3")
        val attempts = if (reasoningModel) listOf(true, false) else listOf(false)
        var last: ApiException? = null
        for (withReasoning in attempts) {
            val body = JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user)),
                )
                .put("temperature", 0.3)
            if (withReasoning) {
                body.put("reasoning_effort", "low")
                body.put("include_reasoning", false)
            }
            val request = Request.Builder()
                .url("${base(p)}/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody(JSON))
                .build()
            try {
                val root = JSONObject(Http.call(p, request, model, "rewrite"))
                return root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            } catch (e: ApiException) {
                if (e.code == 400 && withReasoning) {
                    last = e
                    continue
                }
                throw e
            }
        }
        throw last ?: EngineException("${p.label} request failed")
    }

    suspend fun listModels(p: Provider, key: String): List<String> {
        val request = Request.Builder()
            .url("${base(p)}/models")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        val arr = JSONObject(Http.call(p, request, what = "list models")).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.ifBlank { null } }
    }
}
