package it.hydr4.argo.registry

import it.hydr4.argo.annotations.ArgoEndpoint
import it.hydr4.argo.annotations.RequiresAuthentication
import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.ArgoHttpRequest
import it.hydr4.argo.api.AuthHeaders
import it.hydr4.argo.api.HttpMethod
import it.hydr4.argo.api.SessionContext
import it.hydr4.argo.api.UNAUTHENTICATED_HEADERS
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.testing.FakeEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Registration DX: annotation-driven endpoints, auth guards and typed calls. */
class EndpointRegistryTest {

    @ArgoEndpoint(path = "schools/custom-route", name = "customRoute")
    @RequiresAuthentication
    @Serializable
    private data class CustomRequest(@SerialName("scuola") val schoolCode: String)

    @Serializable
    private data class CustomResponse(val result: String, val count: Int)

    private val authenticatedSession =
        object : SessionContext {
            override suspend fun headersWithFreshBearer(): AuthHeaders =
                AuthHeaders("bearer-x", "xauth-x", "SS13325", Instant.parse("2026-08-25T09:00:00Z"))
            override suspend fun currentHeaders(): AuthHeaders? = headersWithFreshBearer()
            override suspend fun forceRefresh(): AuthHeaders = headersWithFreshBearer()
        }

    private fun registry(engine: FakeEngine, authenticated: Boolean): EndpointRegistry {
        val session = if (authenticated) authenticatedSession else UnauthenticatedSession
        val http = ArgoHttpClient(engine, ArgoClientConfig(), session)
        return EndpointRegistry(http) { authenticated }
    }

    @Test
    fun `annotation-driven registration executes a typed call`() = runTest {
        val engine =
            FakeEngine(
                "custom-route" to {
                    FakeEngine.json("""{"success":true,"data":{"result":"ok","count":2}}""")
                },
            )
        val registry = registry(engine, authenticated = true)

        val endpoint = registry.registerAnnotated(CustomRequest::class, CustomRequest.serializer(), CustomResponse.serializer())

        assertEquals("customRoute", endpoint.name)
        assertEquals("schools/custom-route", endpoint.path)
        assertEquals(HttpMethod.POST, endpoint.method)
        assertTrue(endpoint.requiresAuthentication)

        val response = endpoint.call(CustomRequest("SS13325"))
        assertEquals("ok", response.result)
        assertEquals(2, response.count)

        val request: ArgoHttpRequest = engine.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertTrue("\"scuola\":\"SS13325\"" in request.body.orEmpty())
        assertEquals("Bearer bearer-x", request.headers["authorization"])
    }

    @Test
    fun `auth-required endpoint fails fast before any network traffic`() = runTest {
        val engine = FakeEngine()
        val registry = registry(engine, authenticated = false)
        val endpoint = registry.registerAnnotated(CustomRequest::class, CustomRequest.serializer(), CustomResponse.serializer())

        val error = assertFailsWith<AuthenticationException> { endpoint.call(CustomRequest("SS13325")) }
        assertTrue("authenticated session" in error.detail)
        assertTrue(engine.requests.isEmpty(), "no request may reach the wire unauthenticated")
    }

    @Test
    fun `unannotated class is rejected at registration`() {
        val registry = registry(FakeEngine(), authenticated = true)
        assertFailsWith<IllegalArgumentException> {
            registry.registerAnnotated(UnannotatedRequest::class, UnannotatedRequest.serializer(), CustomResponse.serializer())
        }
    }

    @Test
    fun `explicit registration and typed resolution round-trip`() = runTest {
        val engine = FakeEngine("route/x" to { FakeEngine.json("""{"success":true,"data":{"result":"explicit","count":0}}""") })
        val registry = registry(engine, authenticated = true)

        val endpoint =
            Endpoint(
                path = "route/x",
                method = HttpMethod.POST,
                requestSerializer = CustomRequest.serializer(),
                responseSerializer = CustomResponse.serializer(),
                http = ArgoHttpClient(engine, ArgoClientConfig(), authenticatedSession),
                isAuthenticated = { true },
                name = "explicitRoute",
                requiresAuthentication = false,
            )
        registry.register(endpoint)

        assertEquals(setOf("explicitRoute"), registry.registered())
        assertEquals("explicit", registry.endpoint<CustomRequest, CustomResponse>("explicitRoute").call(CustomRequest("x")).result)
    }

    @Test
    fun `unknown name is rejected with the registered set`() {
        val registry = registry(FakeEngine(), authenticated = true)
        val error = assertFailsWith<IllegalArgumentException> { registry.endpoint<String, String>("missing") }
        assertTrue("missing" in error.message.orEmpty())
    }

    @Test
    fun `unregister removes the endpoint`() {
        val registry = registry(FakeEngine(), authenticated = true)
        val endpoint =
            Endpoint(
                path = "route/y",
                method = HttpMethod.POST,
                requestSerializer = CustomRequest.serializer(),
                responseSerializer = CustomResponse.serializer(),
                http = ArgoHttpClient(FakeEngine(), ArgoClientConfig(), authenticatedSession),
                isAuthenticated = { true },
                name = "tempRoute",
            )
        registry.register(endpoint)
        assertTrue(registry.unregister("tempRoute"))
        assertTrue(registry.registered().isEmpty())
        assertFailsWith<IllegalArgumentException> { registry.endpoint<CustomRequest, CustomResponse>("tempRoute") }
    }

    @Test
    fun `annotations carry runtime retention for consumer introspection`() {
        val endpoint = CustomRequest::class.annotations.filterIsInstance<ArgoEndpoint>().firstOrNull()!!
        assertEquals("schools/custom-route", endpoint.path)
        assertEquals("customRoute", endpoint.name)
        assertTrue(CustomRequest::class.annotations.filterIsInstance<RequiresAuthentication>().isNotEmpty())
        assertTrue(
            ArgoEndpoint::class.java.getAnnotation(java.lang.annotation.Retention::class.java)?.value ==
                java.lang.annotation.RetentionPolicy.RUNTIME,
        )
    }

    @Serializable
    private data class UnannotatedRequest(val x: String)

    private object UnauthenticatedSession : SessionContext {
        override suspend fun headersWithFreshBearer(): AuthHeaders = UNAUTHENTICATED_HEADERS
        override suspend fun currentHeaders(): AuthHeaders? = UNAUTHENTICATED_HEADERS
        override suspend fun forceRefresh(): AuthHeaders = UNAUTHENTICATED_HEADERS
    }
}
