package it.hydr4.argo.api

import it.hydr4.argo.exceptions.ArgoApiException
import it.hydr4.argo.exceptions.DeserializationException
import it.hydr4.argo.exceptions.NetworkException
import it.hydr4.argo.exceptions.ProtocolException
import it.hydr4.argo.time.TimeFormats
import it.hydr4.argo.util.Redactor
import it.hydr4.argo.util.RetryPolicy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

/**
 * The authenticated REST facade every repository delegates to.
 *
 * Responsibilities, each deliberately tiny:
 * 1. Build requests with the exact header set of the reference client.
 * 2. Ask [SessionContext] for a fresh bearer before each call (transparent refresh).
 * 3. Decode envelopes strictly and translate failures into the typed exception tree.
 *
 * **Security:** tokens reach this class only through [SessionContext] and are placed
 * directly into headers; they never appear in messages, `toString` or thrown details.
 */
public class ArgoHttpClient(
    private val engine: ArgoHttpEngine,
    private val config: ArgoClientConfig = ArgoClientConfig(),
    private val session: SessionContext,
) {
    /** Transient-failure policy; consumers tune it through [ArgoClientConfig.retryPolicy]. */
    private val retryPolicy: RetryPolicy = config.retryPolicy

    /** Strict JSON mapper: unknown keys tolerated (schema drift), leniency off. */
    public val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = false
            encodeDefaults = true
        }

    /** Absolute URL for a relative endpoint path. */
    public fun url(path: String): String = "${config.restBaseUrl.trimEnd('/')}/${path.trimStart('/')}"

    /**
     * Executes an authenticated call and decodes its envelope `data` into [T].
     *
     * @param path Relative endpoint path from [Endpoints].
     * @param body JSON body; presence upgrades GET to POST like the reference does.
     * @param dataSerializer Serializer for the expected `data` shape.
     * @throws ArgoApiException when `success` is false or status unexpected.
     * @throws NetworkException on transport failure.
     * @throws DeserializationException when the known schema no longer matches.
     */
    public suspend fun <T> fetch(path: String, body: JsonObject? = null, dataSerializer: KSerializer<T>, method: HttpMethod? = null): T =
        // Transient noise (network failures, server 5xx) is retried; application
        // rejections and auth failures propagate immediately. Each attempt re-derives
        // fresh headers, so a rotation triggered mid-retry is never reused stale.
        retryPolicy.retry {
            val response = execute(path, method ?: if (body != null) HttpMethod.POST else HttpMethod.GET, body)
            val envelopeBody = requireEnvelope(response, path)
            decodeData(envelopeBody, response, path, dataSerializer)
        }

    /**
     * Executes an authenticated call and returns the raw parsed envelope element,
     * for endpoints whose payload deviates from the standard envelope (e.g. login).
     */
    public suspend fun fetchEnvelope(
        path: String,
        body: JsonObject? = null,
        method: HttpMethod = if (body != null) HttpMethod.POST else HttpMethod.GET,
    ): EnvelopeShell = retryPolicy.retry {
        val response = execute(path, method, body)
        EnvelopeShell(requireEnvelope(response, path), response)
    }

    /** Executes a fully-formed request without envelope decoding (OAuth/sso helpers reuse this). */
    public suspend fun raw(request: ArgoHttpRequest): ArgoHttpResponse = try {
        engine.execute(request)
    } catch (e: IOException) {
        throw NetworkException(e)
    }

    private suspend fun execute(path: String, method: HttpMethod, body: JsonObject?): ArgoHttpResponse {
        var response = executeOnce(path, method, body)
        // In-flight hardening: the request was already sent with a bearer that
        // passed the local freshness check, yet the server rejected it (401).
        // Rotate once and re-send — bounded by design: a second 401, or a
        // terminal rotation failure, propagates without looping.
        if (response.statusCode == UNAUTHORIZED_STATUS) {
            session.forceRefresh()
            response = executeOnce(path, method, body)
        }
        return response
    }

    private suspend fun executeOnce(path: String, method: HttpMethod, body: JsonObject?): ArgoHttpResponse {
        val headers = session.headersWithFreshBearer()
        return raw(
            ArgoHttpRequest(
                url = url(path),
                method = method,
                body = body?.let { json.encodeToString(JsonObject.serializer(), it) },
                followRedirects = false,
                headers =
                buildMap {
                    put("accept", "application/json")
                    put("argo-client-version", config.didUpVersion)
                    headers.bearer?.let { put("authorization", "Bearer $it") }
                    if (method == HttpMethod.POST) put("content-type", "application/json")
                    headers.xAuthToken?.let { put("x-auth-token", it) }
                    headers.codMin?.let { put("x-cod-min", it) }
                    headers.tokenExpiresAt?.let {
                        put("x-date-exp-auth", TimeFormats.formatWire(it))
                    }
                },
            ),
        )
    }

    private companion object {
        const val UNAUTHORIZED_STATUS = 401
    }

    private fun requireEnvelope(response: ArgoHttpResponse, path: String): JsonObject {
        val text = response.body
        if (response.statusCode !in 200..299 && !text.contains("\"success\"")) {
            throw ArgoApiException(path, response.statusCode, message = null)
        }
        val parsed: JsonElement =
            try {
                json.decodeFromString(JsonElement.serializer(), text)
            } catch (e: SerializationException) {
                throw DeserializationException(path, e)
            } catch (e: IllegalArgumentException) {
                throw DeserializationException(path, e)
            }
        if (parsed !is JsonObject) {
            throw ProtocolException("Response of '$path' is not a JSON object")
        }
        val success =
            (parsed["success"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                ?: throw ProtocolException("Envelope of '$path' has no boolean 'success' flag")
        if (!success) {
            val msg = (parsed["msg"] as? JsonPrimitive)?.content
            throw ArgoApiException(path, response.statusCode, msg)
        }
        return parsed
    }

    private fun <T> decodeData(envelope: JsonObject, response: ArgoHttpResponse, path: String, serializer: KSerializer<T>): T {
        val data =
            envelope["data"]
                ?.takeIf { it != kotlinx.serialization.json.JsonNull }
                ?: throw ArgoApiException(path, response.statusCode, "envelope carried no data")
        return try {
            json.decodeFromJsonElement(serializer, data)
        } catch (e: SerializationException) {
            throw DeserializationException(path, e)
        } catch (e: IllegalArgumentException) {
            throw DeserializationException(path, e)
        }
    }
}

/**
 * Default retry predicate for the authenticated transport.
 *
 * Retries only failures that are plausibly noise: network-level failures and
 * server-side (>= 500) API errors. Application rejections (`success:false`,
 * 4xx) and authentication failures are excluded — they are answers, not noise,
 * and repeating them would hammer a server that already decided.
 */
public val TRANSIENT_RETRYABLE: (Throwable) -> Boolean = { e ->
    e is IOException ||
        e is NetworkException ||
        (e is ArgoApiException && (e.httpStatus ?: 0) >= 500)
}

/**
 * Container pairing the raw parsed envelope with the original HTTP response so
 * special-shaped endpoints can read both (`total`, top-level flags, Date header…).
 *
 * @property envelope Parsed envelope object (always contains `success`).
 * @property response Raw HTTP snapshot for header access.
 */
public class EnvelopeShell internal constructor(public val envelope: JsonObject, public val response: ArgoHttpResponse) {
    /** Security: raw payload (may echo tokens) and response details are never rendered. */
    override fun toString(): String = "EnvelopeShell(envelope=<json>, response=${Redactor.body(response.body)})"
}
