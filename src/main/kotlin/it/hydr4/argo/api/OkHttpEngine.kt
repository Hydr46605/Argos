package it.hydr4.argo.api

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production [ArgoHttpEngine] delegating to OkHttp.
 *
 * Redirect behavior is request-driven: when any request asks for redirects the
 * call goes through a redirect-following derived client; otherwise through the
 * configured (non-following) client — the SSO/PKCE dance requires inspecting
 * intermediate `Location` hops.
 *
 * @param baseClient Prebuilt OkHttp client holding timeouts, cookies and proxy setup.
 * @param followsRedirectsByDefault Whether [baseClient] auto-follows redirects.
 */
public class OkHttpEngine(private val baseClient: OkHttpClient, private val followsRedirectsByDefault: Boolean = false) : ArgoHttpEngine {
    /**
     * Convenience constructor with reference-mirroring timeout posture.
     *
     * Redirects are request-driven (see class docs); timeouts are generous
     * because school networks are the primary deployment environment.
     */
    public constructor(
        cookieJar: CookieJar = CookieJar.NO_COOKIES,
        connectTimeoutSeconds: Long = 15,
        readTimeoutSeconds: Long = 30,
    ) : this(
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // Redirects are request-driven here: OkHttp follows redirects by default,
            // which would break the SSO dance that must inspect intermediate Locations.
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(java.time.Duration.ofSeconds(connectTimeoutSeconds))
            .readTimeout(java.time.Duration.ofSeconds(readTimeoutSeconds))
            .build(),
    )

    override suspend fun execute(request: ArgoHttpRequest): ArgoHttpResponse {
        val client =
            if (request.followRedirects && !followsRedirectsByDefault) {
                baseClient
                    .newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
            } else if (!request.followRedirects && followsRedirectsByDefault) {
                baseClient
                    .newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            } else {
                baseClient
            }
        val builder = Request.Builder().url(request.url)
        for ((name, value) in request.headers) builder.header(name, value)
        when (request.method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.POST -> {
                val mediaType = request.contentType.toMediaType()
                builder.post((request.body ?: "").toRequestBody(mediaType))
            }
        }
        return client.newCall(builder.build()).awaitResponse()
    }

    /** Releases dispatcher threads, connection pool and executor backing the client. */
    public fun close() {
        baseClient.dispatcher.executorService.shutdown()
        baseClient.connectionPool.evictAll()
    }
}

/**
 * Bridges an OkHttp [Call] into a coroutine, mapping failures onto
 * [IOException] so callers translate transport noise uniformly.
 */
private suspend fun Call.awaitResponse(): ArgoHttpResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyText = it.body?.string() ?: ""
                    val headers =
                        it.headers.let { hs ->
                            buildMap {
                                for ((name, value) in hs) putIfAbsent(name.lowercase(), value)
                            }
                        }
                    if (continuation.isActive) {
                        continuation.resume(ArgoHttpResponse(it.code, headers, bodyText))
                    }
                }
            }
        },
    )
}
