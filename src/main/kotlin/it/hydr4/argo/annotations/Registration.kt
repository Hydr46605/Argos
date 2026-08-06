package it.hydr4.argo.annotations

import it.hydr4.argo.api.HttpMethod

/**
 * Declares a wire endpoint on a request model so it can be registered into the
 * client's [it.hydr4.argo.registry.EndpointRegistry] with a single call.
 *
 * Argo's API is undocumented and evolves per deployment: consumers discover new
 * routes on their school's instance and can plug them into the typed client
 * without waiting for an Argos release.
 *
 * ```kotlin
 * @ArgoEndpoint(path = "schools/custom-route")
 * @Serializable
 * data class CustomPayload(val schoolCode: String)
 * ```
 *
 * @property path Endpoint path relative to the REST base URL.
 * @property method HTTP verb used for the call.
 * @property name Optional registry name; defaults to the annotated class simple name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class ArgoEndpoint(
    public val path: String,
    public val method: HttpMethod = HttpMethod.POST,
    public val name: String = "",
)

/**
 * Marks a request model whose endpoint requires an authenticated session.
 *
 * The registry enforces this before any network traffic: calling such an
 * endpoint while unauthenticated fails fast with an
 * [it.hydr4.argo.exceptions.AuthenticationException] instead of a confusing
 * server-side rejection.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class RequiresAuthentication
