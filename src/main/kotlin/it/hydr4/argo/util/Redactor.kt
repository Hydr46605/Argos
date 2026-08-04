package it.hydr4.argo.util

/**
 * Central redaction used by every `toString` implementation in the library, so
 * credential-bearing fields can never reach logs through object rendering.
 *
 * @see it.hydr4.argo.models.Token
 * @see it.hydr4.argo.api.ArgoHttpRequest
 */
public object Redactor {
    /** Header names whose values are secrets (bearer, session, cookies). */
    private val SENSITIVE_HEADERS =
        setOf("authorization", "x-auth-token", "cookie", "set-cookie", "proxy-authorization")

    /**
     * Renders a header map for logging: sensitive values become `●`, every
     * other value becomes `<omitted>` so payloads are never echoed.
     */
    public fun headers(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, _) ->
        if (name.lowercase() in SENSITIVE_HEADERS) "●" else "<omitted>"
    }

    /** Renders a body for logging as its byte length instead of its content. */
    public fun body(body: String?): String = body?.let { "<${it.length} bytes>" } ?: "null"
}
