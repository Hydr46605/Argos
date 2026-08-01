package it.hydr4.argo.registry

import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.HttpMethod
import it.hydr4.argo.exceptions.AuthenticationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

/**
 * A registered, typed call into the Argo REST surface.
 *
 * Wraps an endpoint path plus its request/response serializers and executes
 * through the same authenticated transport as the built-in repositories, so
 * custom endpoints inherit header handling, envelope decoding and the typed
 * exception tree.
 *
 * @param path Endpoint path relative to the REST base URL.
 * @param method HTTP verb used for the call.
 * @param requestSerializer Wire serializer of [Req].
 * @param responseSerializer Wire serializer of [Res] (the envelope `data`).
 * @param http Shared transport; every call runs through it.
 * @param isAuthenticated Live session probe used for the fast-fail guard.
 * @param name Registry name used in error messages.
 * @param requiresAuthentication When `true`, unauthenticated calls fail fast
 *   before any network traffic.
 */
public class Endpoint<Req, Res>(
    public val path: String,
    public val method: HttpMethod,
    private val requestSerializer: KSerializer<Req>,
    private val responseSerializer: KSerializer<Res>,
    private val http: ArgoHttpClient,
    private val isAuthenticated: () -> Boolean,
    public val name: String = path,
    public val requiresAuthentication: Boolean = true,
) {
    /**
     * Executes the endpoint with [request] and returns the decoded envelope `data`.
     *
     * @throws AuthenticationException when the endpoint requires a session and none exists.
     * @throws IllegalArgumentException when [request] does not serialize to a JSON object.
     * @throws it.hydr4.argo.exceptions.ArgoException typed failures from the transport.
     */
    public suspend fun call(request: Req): Res {
        if (requiresAuthentication && !isAuthenticated()) {
            throw AuthenticationException("Endpoint '$name' requires an authenticated session")
        }
        val body =
            http.json.encodeToJsonElement(requestSerializer, request).let { element ->
                element as? JsonObject
                    ?: throw IllegalArgumentException(
                        "Request of endpoint '$name' must serialize to a JSON object, was ${element::class.simpleName}",
                    )
            }
        return http.fetch(path = path, body = body, dataSerializer = responseSerializer, method = method)
    }
}
