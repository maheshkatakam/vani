package com.maheshkatakam.vani.ai

import com.maheshkatakam.vani.core.Provider
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
    val apiMessage: String get() = Http.extractMessage(body)
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

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Runs a request; cancelling the coroutine cancels the HTTP call. */
    suspend fun call(provider: Provider, request: Request): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val body = try {
                        r.body?.string().orEmpty()
                    } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                        return
                    }
                    if (!cont.isActive) return
                    if (r.isSuccessful) {
                        cont.resume(body)
                    } else {
                        cont.resumeWithException(ApiException(provider, r.code, body, friendly(provider, r.code, body)))
                    }
                }
            }
        })
    }

    fun extractMessage(body: String): String = runCatching {
        val o = JSONObject(body)
        val err = o.opt("error")
        when (err) {
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
        is SocketTimeoutException -> "The AI took too long to answer. Try again."
        is InterruptedIOException -> "The request timed out. Try again."
        is IOException -> "Network problem: ${e.message ?: e.javaClass.simpleName}"
        else -> e.message ?: e.javaClass.simpleName
    }
}
