package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.testing.FakeEngine
import kotlinx.coroutines.test.runTest
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Hydra challenge extraction and SSO credential-post choreography. */
class SsoCodeBrokerTest {

    private val credentials = Credentials("SS13325", "RSSLCA05T01", "s3cret")

    @Test
    fun `fetchChallenge extracts login_challenge from the authorize redirect`() = runTest {
        val engine = FakeEngine("auth" to { FakeEngine.redirect("https://auth.portaleargo.it/login?login_challenge=ch-xyz") })
        val broker = DefaultSsoCodeBroker(engine)
        assertEquals("ch-xyz", broker.fetchChallenge("https://auth.portaleargo.it/oauth2/auth?state=abc"))
    }

    @Test
    fun `exchange posts the credential form and extracts the code`() = runTest {
        val engine =
            FakeEngine(
                // Deep-link Location: following stops here and the 3xx is returned as final.
                "sso/login" to {
                    FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?code=code-42&state=abc")
                },
            )
        val broker = DefaultSsoCodeBroker(engine, ArgoClientConfig())
        val code = broker.exchangeCredentialsForCode("ch-1", credentials)

        assertEquals("code-42", code)
        assertTrue(engine.requests.single().followRedirects, "the SSO hop must follow redirects like the reference client")
        val request = engine.requests.single()
        assertEquals("application/x-www-form-urlencoded", request.contentType)
        val body = request.body.orEmpty()
        assertTrue("challenge=ch-1" in body)
        assertTrue("famiglia_customer_code=SS13325" in body)
        assertTrue("username=RSSLCA05T01" in body)
        assertTrue(URLEncoder.encode("s3cret", Charsets.UTF_8) in body, "password travels URL-encoded")
    }

    @Test
    fun `non-redirect SSO answer surfaces an authentication error`() = runTest {
        val engine = FakeEngine("sso/login" to { FakeEngine.json("""{"error":"invalid_credentials"}""", status = 401) })
        val broker = DefaultSsoCodeBroker(engine, ArgoClientConfig())
        assertFailsWith<AuthenticationException> {
            broker.exchangeCredentialsForCode("ch-1", credentials)
        }
    }

    @Test
    fun `missing location header on the authorize hop is a protocol failure`() = runTest {
        val engine = FakeEngine("auth" to { FakeEngine.json("""{"error":"down"}""", status = 500) })
        val broker = DefaultSsoCodeBroker(engine)
        assertFailsWith<AuthenticationException> { broker.fetchChallenge("https://auth.portaleargo.it/oauth2/auth") }
    }

    @Test
    fun `redirect without code is rejected as credential failure`() = runTest {
        val engine =
            FakeEngine(
                "sso/login" to {
                    FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?error=access_denied")
                },
            )
        val broker = DefaultSsoCodeBroker(engine, ArgoClientConfig())
        assertFailsWith<AuthenticationException> { broker.exchangeCredentialsForCode("ch-1", credentials) }
    }

    @Test
    fun `exchange follows the redirect chain back to hydra until the code URI`() = runTest {
        val engine =
            FakeEngine(
                "auth/sso/login" to {
                    FakeEngine.redirect("https://auth.portaleargo.it/oauth2/auth?login_challenge=ch-1&login_verifier=v-1")
                },
                "oauth2/auth" to {
                    FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?code=code-final&state=abc")
                },
            )
        val broker = DefaultSsoCodeBroker(engine, ArgoClientConfig())

        val code = broker.exchangeCredentialsForCode("ch-1", credentials)

        assertEquals("code-final", code)
        assertEquals(2, engine.requests.size, "SSO post + hydra consent hop")
        assertTrue(engine.requests[1].url.contains("oauth2/auth"), "the hydra hop must be executed")
    }
}
