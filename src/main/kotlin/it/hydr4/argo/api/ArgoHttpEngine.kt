package it.hydr4.argo.api

import it.hydr4.argo.util.Redactor

/**
 * Minimal HTTP execution surface so the whole stack (including auth) runs over
 * any transport: production OkHttp here, deterministic fixture replay in tests.
 *
 * Implementations must not follow redirects unless [ArgoHttpRequest.followRedirects]
 * is set — the SSO dance inspects intermediate locations.
 */
public fun interface ArgoHttpEngine {
    /**
     * Executes the request and returns the raw response.
     *
     * Transport errors surface as [java.io.IOException]; mapping into domain
     * exceptions belongs to the caller ([ArgoHttpClient]).
     */
    public suspend fun execute(request: ArgoHttpRequest): ArgoHttpResponse
}

/** Supported methods; Argo needs only these two. */
public enum class HttpMethod { GET, POST }

/**
 * Immutable request descriptor.
 *
 * @property url Absolute URL.
 * @property method Method; defaults to POST when a body is present.
 * @property headers Exact header set to send (already includes auth state).
 * @property body Pre-encoded body text; `null` for GETs without payload.
 * @property contentType Body media type (JSON or form-urlencoded payloads occur).
 * @property followRedirects Whether the engine may auto-follow redirects.
 */
public data class ArgoHttpRequest(
    public val url: String,
    public val method: HttpMethod = HttpMethod.GET,
    public val headers: Map<String, String> = emptyMap(),
    public val body: String? = null,
    public val contentType: String = "application/json",
    public val followRedirects: Boolean = false,
) {
    override fun toString(): String = "ArgoHttpRequest(url=$url, method=$method, headers=${Redactor.headers(
        headers,
    )}, body=${Redactor.body(body)}, contentType=$contentType)"
}

/**
 * Immutable response snapshot.
 *
 * Header names are normalized to lowercase at construction time by [headersOf].
 *
 * @property statusCode Raw status.
 * @property headers Lowercase-keyed header map (first value wins on duplicates).
 * @property body Body text or empty string.
 */
public data class ArgoHttpResponse(public val statusCode: Int, public val headers: Map<String, String>, public val body: String) {
    /** Case-insensitive first-value lookup helper for engines and callers. */
    public fun header(name: String): String? = headers[name.lowercase()]

    /** Security: header values and body content are never echoed in rendering. */
    override fun toString(): String =
        "ArgoHttpResponse(statusCode=$statusCode, headers=${Redactor.headers(headers)}, body=${Redactor.body(body)})"

    /** Builds the lowercase-keyed header map used by [ArgoHttpResponse.headers]. */
    public companion object {
        public fun headersOf(vararg pairs: Pair<String, String>): Map<String, String> = buildMap {
            for ((name, value) in pairs) putIfAbsent(name.lowercase(), value)
        }
    }
}
