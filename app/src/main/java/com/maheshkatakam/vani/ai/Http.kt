package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.Provider
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An HTTP error from a provider, with a message that makes sense to a person. */
class ApiException(
    val provider: Provider,
    val code: Int,
    val body: String,
    message: String,
) : Exception(message) {
    val isAuth: Boolean
        get() = code == 401 ||
            (code == 403 && provider != Provider.GEMINI) ||
            body.contains("API_KEY_INVALID") ||
            body.contains("API key not valid", ignoreCase = true)
    val isNotFound: Boolean get() = code == 404
    val isQuotaZero: Boolean get() = code == 429 && body.contains("limit: 0")
    val isServer: Boolean get() = code >= 500
}

/** Something went wrong that isn't an HTTP error (no key, empty answer, blocked, …). */
class EngineException(message: String) : Exception(message)

/** Request body that reports upload progress, so the sheet can show "Sending… 60%". */
class ProgressBody(
    private val bytes: ByteArray,
    private val type: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = type
    override fun contentLength(): Long = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        var offset = 0
        val total = bytes.size
        while (offset < total) {
            val n = minOf(32 * 1024, total - offset)
            sink.write(bytes, offset, n)
            offset += n
            sink.flush()
            onProgress(if (total == 0) 1f else offset.toFloat() / total)
        }
    }
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /** For streamed answers: if the model sends nothing for 40 s, give up (and try a faster model). */
    val streamClient: OkHttpClient = client.newBuilder()
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Runs a request and returns the body; cancelling the coroutine cancels the HTTP call. */
    suspend fun call(provider: Provider, request: Request, model: String = "", what: String = "request"): String {
        val started = System.nanoTime()
        val bytes = runCatching { request.body?.contentLength() ?: 0L }.getOrDefault(0L).toInt()
        return try {
            val body = suspendCancellableCoroutine<String> { cont ->
                val call = client.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { r ->
                            val text = try {
                                r.body?.string().orEmpty()
                            } catch (e: IOException) {
                                if (cont.isActive) cont.resumeWithException(e)
                                return
                            }
                            if (!cont.isActive) return
                            if (r.isSuccessful) {
                                cont.resume(text)
                            } else {
                                cont.resumeWithException(ApiException(provider, r.code, text, friendly(provider, r.code, text)))
                            }
                        }
                    }
                })
            }
            log(provider, model, what, bytes, null, started, "OK")
            body
        } catch (e: Exception) {
            log(provider, model, what, bytes, null, started, shortError(e))
            throw e
        }
    }

    /**
     * Server-sent events: calls [onData] with each `data:` payload as it arrives (on a network
     * thread). Used to show Gemini's answer while it's still being written.
     */
    suspend fun sse(
        provider: Provider,
        request: Request,
        model: String,
        what: String,
        onData: (String) -> Unit,
    ) {
        val started = System.nanoTime()
        var firstByte: Long? = null
        val bytes = runCatching { request.body?.contentLength() ?: 0L }.getOrDefault(0L).toInt()
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val call = streamClient.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { r ->
                            try {
                                if (!r.isSuccessful) {
                                    val text = r.body?.string().orEmpty()
                                    if (cont.isActive) {
                                        cont.resumeWithException(ApiException(provider, r.code, text, friendly(provider, r.code, text)))
                                    }
                                    return
                                }
                                val source = r.body?.source()
                                if (source == null) {
                                    if (cont.isActive) cont.resume(Unit)
                                    return
                                }
                                while (cont.isActive) {
                                    val line = source.readUtf8Line() ?: break
                                    if (!line.startsWith("data:")) continue
                                    if (firstByte == null) firstByte = (System.nanoTime() - started) / 1_000_000
                                    val payload = line.substring(5).trim()
                                    if (payload.isNotEmpty() && payload != "[DONE]") onData(payload)
                                }
                                if (cont.isActive) cont.resume(Unit)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resumeWithException(e)
                            }
                        }
                    }
                })
            }
            log(provider, model, what, bytes, firstByte, started, "OK")
        } catch (e: Exception) {
            log(provider, model, what, bytes, firstByte, started, shortError(e))
            throw e
        }
    }

    private fun log(provider: Provider, model: String, what: String, bytes: Int, firstByte: Long?, started: Long, result: String) {
        Diagnostics.log(
            Diagnostics.Entry(
                time = System.currentTimeMillis(),
                provider = provider.label,
                model = model,
                what = what,
                bytes = bytes,
                firstByteMs = firstByte,
                totalMs = (System.nanoTime() - started) / 1_000_000,
                result = result,
            )
        )
    }

    private fun shortError(e: Exception): String = when (e) {
        is ApiException -> "HTTP ${e.code}: ${extractMessage(e.body).take(160)}"
        is kotlinx.coroutines.CancellationException -> "cancelled"
        else -> "${e.javaClass.simpleName}: ${e.message.orEmpty().take(120)}"
    }

    fun extractMessage(body: String): String = runCatching {
        val o = JSONObject(body)
        when (val err = o.opt("error")) {
            is JSONObject -> err.optString("message")
            is String -> err
            else -> o.optString("message")
        }
    }.getOrNull().orEmpty().ifBlank { body.take(200) }.trim()

    fun friendly(p: Provider, code: Int, body: String): String {
        val msg = extractMessage(body).take(220)
        val authProblem = code == 401 || (code == 403 && p != Provider.GEMINI) ||
            body.contains("API_KEY_INVALID") || body.contains("API key not valid", ignoreCase = true)
        return when {
            authProblem -> "${p.label} didn't accept your API key. Check it in Vani → Settings → AI engine."
            code == 403 -> "${p.label} refused the request (403). $msg"
            code == 404 -> "${p.label} couldn't find that model. $msg"
            code == 429 -> "${p.label} rate limit or free quota reached. Wait a minute or switch provider. ($msg)"
            code >= 500 -> "${p.label} is having trouble right now (error $code). Try again in a moment."
            else -> "${p.label} error $code: $msg"
        }
    }
}

object Errors {
    fun describe(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "API error"
        is EngineException -> e.message ?: "Something went wrong"
        is UnknownHostException -> "No internet connection."
        is SocketTimeoutException -> "The AI took too long to answer. Try again, or pick a faster model in Settings."
        is InterruptedIOException -> "The request timed out. Try again."
        is IOException -> "Network problem: ${e.message ?: e.javaClass.simpleName}"
        else -> e.message ?: e.javaClass.simpleName
    }
}
