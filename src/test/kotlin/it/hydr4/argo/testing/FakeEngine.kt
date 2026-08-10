package it.hydr4.argo.testing

import it.hydr4.argo.api.ArgoHttpEngine
import it.hydr4.argo.api.ArgoHttpRequest
import it.hydr4.argo.api.ArgoHttpResponse

/**
 * Deterministic in-memory [ArgoHttpEngine] replaying canned responses.
 *
 * Routes are (url-fragment, handler) pairs; the first fragment contained in the
 * request URL wins, so tests pin distinct endpoints with short needles. Every
 * executed request is recorded for transport assertions (headers, bodies, verbs).
 */
public class FakeEngine(vararg routes: Pair<String, (ArgoHttpRequest) -> ArgoHttpResponse>) : ArgoHttpEngine {

    private val routes: List<Pair<String, (ArgoHttpRequest) -> ArgoHttpResponse>> = routes.toList()

    /** Every request this engine served, in order. */
    public val requests: MutableList<ArgoHttpRequest> = mutableListOf()

    /** All responses this engine produced, in order (aligned with [requests]). */
    public val responses: MutableList<ArgoHttpResponse> = mutableListOf()

    // Each early return mirrors a distinct OkHttp redirect decision (no-follow
    // requests, custom-scheme deep links, missing Location) — early exits are
    // clearer here than a flattened control structure.
    @Suppress("ReturnCount")
    override suspend fun execute(request: ArgoHttpRequest): ArgoHttpResponse {
        var current = request
        repeat(MAX_REDIRECT_HOPS + 1) { hop ->
            requests += current
            val handler =
                routes.firstOrNull { (needle, _) -> current.url.contains(needle) }?.second
                    ?: throw AssertionError("FakeEngine: no route matches ${current.method} ${current.url}")
            val response = handler(current)
            responses += response
            if (!request.followRedirects || hop == MAX_REDIRECT_HOPS) return response
            // Mirror OkHttp: follow http(s) Locations, stop at custom schemes like the
            // deep-link redirect URI, and never follow when no Location is present.
            val location = response.header("location") ?: return response
            val target =
                runCatching { java.net.URI(location) }.getOrNull()
                    ?: return response
            if (target.scheme !in setOf("http", "https")) return response
            current = current.copy(url = java.net.URI(current.url).resolve(target).toString())
        }
        error("FakeEngine: too many redirect hops for ${request.url}")
    }

    /** Requests whose URL contained [needle], asserted non-empty. */
    public fun requestsTo(needle: String): List<ArgoHttpRequest> {
        val matched = requests.filter { it.url.contains(needle) }
        check(matched.isNotEmpty()) { "FakeEngine: expected at least one request to '$needle', got none" }
        return matched
    }

    public companion object {
        private const val MAX_REDIRECT_HOPS = 10

        /** Canonical fixed server date used so expiry math stays deterministic. */
        public const val SERVER_DATE: String = "Tue, 25 Aug 2026 08:00:00 GMT"

        /** [SERVER_DATE] as an [java.time.Instant]. */
        public val SERVER_INSTANT: java.time.Instant = java.time.Instant.parse("2026-08-25T08:00:00Z")

        /** Plain 200 JSON response carrying the fixture text plus the fixed Date header. */
        public fun json(body: String, status: Int = 200): ArgoHttpResponse =
            ArgoHttpResponse(status, ArgoHttpResponse.headersOf("Date" to SERVER_DATE, "content-type" to "application/json"), body)

        /** 302 redirect response. */
        public fun redirect(location: String): ArgoHttpResponse =
            ArgoHttpResponse(302, ArgoHttpResponse.headersOf("Location" to location, "Date" to SERVER_DATE), "")
    }
}
