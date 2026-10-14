package me.weishu.kernelsu.jwsk.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AiError { CONFIGURATION, AUTHENTICATION, RATE_LIMIT, SERVICE, REQUEST, NETWORK, RESPONSE, TOO_LARGE, EMPTY_RESPONSE }
class AiFailure(val error: AiError) : IOException(error.name)

/** Network-only text service. Never receives a Context, device handle or executor. */
interface AiTextService {
    suspend fun chat(profile: AiProfile, messages: List<AiMessage>): String
}

class AiClient : AiTextService {
    internal val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .build()

    override suspend fun chat(profile: AiProfile, messages: List<AiMessage>): String {
        val request = try { AiProtocol.request(profile, messages) }
        catch (_: IllegalArgumentException) { throw AiFailure(AiError.CONFIGURATION) }
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(AiFailure(AiError.NETWORK))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful) throw AiFailure(when (response.code) {
                                401, 403 -> AiError.AUTHENTICATION
                                429 -> AiError.RATE_LIMIT
                                in 500..599 -> AiError.SERVICE
                                else -> AiError.REQUEST
                            })
                            val body = response.body
                            if (body.contentLength() > MAX_RESPONSE) throw AiFailure(AiError.TOO_LARGE)
                            val source = body.source()
                            if (source.request(MAX_RESPONSE + 1L)) throw AiFailure(AiError.TOO_LARGE)
                            val text = AiProtocol.response(profile.configuration.provider, source.readUtf8())
                            if (continuation.isActive) continuation.resume(text)
                        } catch (failure: Exception) {
                            val safe = failure as? AiFailure ?: AiFailure(AiError.RESPONSE)
                            if (continuation.isActive) continuation.resumeWithException(safe)
                        }
                    }
                }
            })
        }
    }

    private companion object { const val MAX_RESPONSE = 2L * 1024 * 1024 }
}
