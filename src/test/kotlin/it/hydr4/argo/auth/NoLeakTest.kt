package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.ArgoHttpRequest
import it.hydr4.argo.api.AuthHeaders
import it.hydr4.argo.api.SessionContext
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.models.LoginData
import it.hydr4.argo.models.Token
import it.hydr4.argo.testing.FakeEngine
import it.hydr4.argo.testing.Fixtures
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The library's central security property: no credential material ever escapes
 * through `toString`, exception messages or serialized requests.
 */
class NoLeakTest {

    private val token = Token(
        accessToken = "TOP-SECRET-ACCESS",
        refreshToken = "TOP-SECRET-REFRESH",
        expiresAt = Instant.parse("2026-08-26T08:00:00Z"),
        scope = "openid",
        tokenType = "Bearer",
    )

    @Test
    fun `token toString is redacted`() {
        val rendered = token.toString()
        assertFalse("TOP-SECRET" in rendered)
        assertFalse("at-secret" in rendered)
    }

    @Test
    fun `credentials toString is redacted`() {
        val credentials = Credentials("SS13325", "user", "HUNTER2")
        val rendered = credentials.toString()
        assertFalse("HUNTER2" in rendered)
        assertTrue("SS13325" in rendered, "non-secret identifiers may stay readable")
    }

    @Test
    fun `login data toString redacts the session token`() {
        val rendered = LoginData("SS13325", "SESSION-SECRET").toString()
        assertFalse("SESSION-SECRET" in rendered)
    }

    @Test
    fun `token exchange rejection does not echo error descriptions`() = runTest {
        val engine =
            FakeEngine("oauth2/token" to { FakeEngine.json(Fixtures.text("oauth-token-error.json")) })
        val exchanger = OAuthTokenExchanger(engine, ArgoClientConfig())
        val error = kotlin.test.assertFailsWith<AuthenticationException> {
            exchanger.exchange(code = "code", verifier = "verifier")
        }
        assertTrue("invalid_grant" in error.detail)
        assertFalse("sensitive-pii-marker-do-not-echo" in error.detail)
    }

    @Test
    fun `request bodies never embed the access token`() {
        val engine = FakeEngine("dashboard/what" to { FakeEngine.json("{\"success\":true,\"data\":{}}") })
        val client =
            ArgoHttpClient(
                engine,
                ArgoClientConfig(),
                object : SessionContext {
                    override suspend fun headersWithFreshBearer() = AuthHeaders("Bearer-TOKEN-VALUE", null, null, null)
                    override suspend fun currentHeaders(): AuthHeaders? = null
                    override suspend fun forceRefresh() = headersWithFreshBearer()
                },
            )
        runTest { client.fetch("dashboard/what", dataSerializer = kotlinx.serialization.json.JsonObject.serializer()) }
        val request: ArgoHttpRequest = engine.requests.single()
        assertFalse("Bearer-TOKEN-VALUE" in request.body.orEmpty())
        assertFalse("Bearer-TOKEN-VALUE" in request.toString())
    }

    @Test
    fun `auth headers toString redacts bearer and session token`() {
        val rendered = AuthHeaders("BEARER-SECRET", "XTOKEN-SECRET", "SS13325", Instant.parse("2026-08-25T09:00:00Z")).toString()
        assertFalse("BEARER-SECRET" in rendered)
        assertFalse("XTOKEN-SECRET" in rendered)
        kotlin.test.assertTrue("●" in rendered)
    }

    @Test
    fun `response toString never echoes headers or body`() {
        val response =
            it.hydr4.argo.api.ArgoHttpResponse(
                200,
                it.hydr4.argo.api.ArgoHttpResponse.headersOf("authorization" to "Bearer RESP-SECRET", "x-auth-token" to "X-RESP-SECRET"),
                "{\"data\":\"TOP-SECRET-PAYLOAD\"}",
            )
        val rendered = response.toString()
        assertFalse("RESP-SECRET" in rendered)
        assertFalse("X-RESP-SECRET" in rendered)
        assertFalse("TOP-SECRET-PAYLOAD" in rendered)
        kotlin.test.assertTrue("bytes" in rendered)
    }

    @Test
    fun `envelope shell toString never renders the raw payload`() {
        val shell = it.hydr4.argo.api.EnvelopeShell(
            Json.parseToJsonElement("""{"success":true,"data":{"token":"SHELL-SECRET"}}""").jsonObject,
            it.hydr4.argo.api.ArgoHttpResponse(200, emptyMap(), """{"token":"SHELL-SECRET"}"""),
        )
        val rendered = shell.toString()
        assertFalse("SHELL-SECRET" in rendered)
    }

    @Test
    fun `unauthenticated requests carry no authorization header at all`() {
        val engine = FakeEngine("profilo" to { FakeEngine.json("""{"success":true,"data":{}}""") })
        val client =
            ArgoHttpClient(
                engine,
                ArgoClientConfig(),
                object : SessionContext {
                    override suspend fun headersWithFreshBearer(): AuthHeaders = it.hydr4.argo.api.UNAUTHENTICATED_HEADERS
                    override suspend fun currentHeaders(): AuthHeaders? = it.hydr4.argo.api.UNAUTHENTICATED_HEADERS
                    override suspend fun forceRefresh(): AuthHeaders = it.hydr4.argo.api.UNAUTHENTICATED_HEADERS
                },
            )
        runTest { client.fetch("profilo", dataSerializer = kotlinx.serialization.json.JsonObject.serializer()) }
        val request = engine.requests.single()
        kotlin.test.assertNull(request.headers["authorization"])
    }
}
