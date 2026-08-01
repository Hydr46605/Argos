package it.hydr4.argo.registry

import it.hydr4.argo.annotations.ArgoEndpoint
import it.hydr4.argo.annotations.RequiresAuthentication
import it.hydr4.argo.api.ArgoHttpClient
import kotlinx.serialization.KSerializer
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Thread-safe registry of custom [Endpoint] calls, bound to one client's
 * transport and session.
 *
 * Two registration styles:
 * - explicit: `registry.register(Endpoint(path, method, reqSerializer, resSerializer, http, auth))`;
 * - annotation-driven: mark the request model with [ArgoEndpoint] (and
 *   optionally [RequiresAuthentication]), then `registerAnnotated(...)` reads the
 *   metadata and enforces the auth contract automatically.
 */
public class EndpointRegistry internal constructor(private val http: ArgoHttpClient, private val isAuthenticated: () -> Boolean) {
    private val endpoints = ConcurrentHashMap<String, Endpoint<*, *>>()

    /**
     * Registers [endpoint] under its [Endpoint.name].
     *
     * Registration is idempotent per name: the first registration wins so
     * duplicate wiring during app setup cannot silently replace calls.
     */
    public fun <Req, Res> register(endpoint: Endpoint<Req, Res>): Endpoint<Req, Res> {
        endpoints.putIfAbsent(endpoint.name, endpoint)
        return endpoint
    }

    /** Removes the endpoint registered under [name]; `false` when absent. */
    public fun unregister(name: String): Boolean = endpoints.remove(name) != null

    /** Names of every registered endpoint. */
    public fun registered(): Set<String> = endpoints.keys.toSet()

    /**
     * Resolves the endpoint registered under [name].
     *
     * @throws IllegalArgumentException when nothing is registered under [name].
     */
    @Suppress("UNCHECKED_CAST")
    public fun <Req, Res> endpoint(name: String): Endpoint<Req, Res> = endpoints[name] as? Endpoint<Req, Res>
        ?: throw IllegalArgumentException(
            "No endpoint registered under '$name'; registered: ${registered().sorted()}",
        )

    /**
     * Builds an [Endpoint] from the [ArgoEndpoint] metadata on [requestType] and
     * registers it. [RequiresAuthentication] on the same class turns on the
     * fast-fail auth guard.
     *
     * @param requestType Annotated request class; its simple name becomes the registry name.
     * @param requestSerializer Wire serializer of the request type.
     * @param responseSerializer Wire serializer of the envelope `data`.
     * @throws IllegalArgumentException when [requestType] lacks [ArgoEndpoint].
     */
    public fun <Req : Any, Res> registerAnnotated(
        requestType: KClass<Req>,
        requestSerializer: KSerializer<Req>,
        responseSerializer: KSerializer<Res>,
    ): Endpoint<Req, Res> {
        val annotation =
            requestType.findAnnotation<ArgoEndpoint>()
                ?: throw IllegalArgumentException(
                    "@ArgoEndpoint is missing on ${requestType.simpleName}; annotate the request class to register it",
                )
        return register(
            Endpoint(
                path = annotation.path,
                method = annotation.method,
                requestSerializer = requestSerializer,
                responseSerializer = responseSerializer,
                http = http,
                isAuthenticated = isAuthenticated,
                name = annotation.name.ifBlank { requestType.simpleName ?: annotation.path },
                requiresAuthentication = requestType.findAnnotation<RequiresAuthentication>() != null,
            ),
        )
    }

    private inline fun <reified T : Annotation> KClass<*>.findAnnotation(): T? = annotations.filterIsInstance<T>().firstOrNull()
}
