package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoConstants
import it.hydr4.argo.api.HttpMethod
import it.hydr4.argo.exceptions.ArgoApiException
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.exceptions.NetworkException
import it.hydr4.argo.exceptions.RefreshRejectedException
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.models.Token
import it.hydr4.argo.storage.InMemoryTokenStore
import it.hydr4.argo.storage.SessionSnapshot
import it.hydr4.argo.testing.FakeEngine
import it.hydr4.argo.testing.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Full PKCE credential flow, transparent refresh and durable restore. */
class ArgoSessionTest {

    private val fixedClock: Clock = Clock.fixed(FakeEngine.SERVER_INSTANT, ZoneOffset.UTC)

    private val credentials =
        Credentials(schoolCode = "SS13325", username = "RSSLCA05T01", password = "s3cret")

    /** Engine replaying the complete SSO + OAuth + family-login choreography. */
    private fun loginEngine(): FakeEngine = FakeEngine(
        "oauth2/auth" to {
            FakeEngine.redirect("https://auth.portaleargo.it/login?login_challenge=ch-123")
        },
        "/auth/sso/login" to {
            FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?code=code-abc&state=state")
        },
        "oauth2/token" to { FakeEngine.json(Fixtures.text("oauth-token-success.json")) },
        "appfamiglia/api/rest/login" to { FakeEngine.json(Fixtures.text("login-family-success.json")) },
    )

    private fun session(engine: FakeEngine, store: InMemoryTokenStore = InMemoryTokenStore()): ArgoSession = ArgoSession(
        engine = engine,
        tokens = CachedTokenRepository(store),
        store = store,
        config = ArgoClientConfig(),
        clock = fixedClock,
    )

    @Test
    fun `credential login walks the full state machine`() = runTest {
        val engine = loginEngine()
        val store = InMemoryTokenStore()
        val session = session(engine, store)

        val login = session.loginWithCredentials(credentials)

        assertEquals("SS13325", login.codMin)
        assertEquals("x-auth-session-token", login.xAuthToken)
        assertTrue(session.isAuthenticated())
        val state = session.authState
        assertTrue(state is AuthState.Authenticated, "expected Authenticated, was $state")
        assertEquals(login, (state as AuthState.Authenticated).loginData)

        // The dance must have hit exactly the four hops.
        assertEquals(4, engine.requests.size)
        assertTrue(engine.requests[0].url.contains("oauth2/auth"))
        assertEquals(HttpMethod.POST, engine.requests[1].method)
        assertTrue(engine.requests[2].url.contains("oauth2/token"))
        assertTrue(engine.requests[3].url.contains("appfamiglia/api/rest/login"))
        // The SSO hop carries the form with the credential fields required by Hydra.
        val ssoBody = engine.requests[1].body.orEmpty()
        assertTrue("famiglia_customer_code=SS13325" in ssoBody)
        assertTrue(engine.requests[2].body.orEmpty().contains("code=code-abc"))
        assertTrue(engine.requests[3].headers["argo-client-version"] == ArgoConstants.DIDUP_VERSION)

        // Persisted snapshot is complete and encrypted.
        val persisted = store.load()
        assertNotNull(persisted?.token)
        assertEquals("at-secret-123", persisted.token.accessToken)

        // Authenticated headers carry session material.
        val headers = session.headersWithFreshBearer()
        assertEquals("at-secret-123", headers.bearer)
        assertEquals("x-auth-session-token", headers.xAuthToken)
        assertEquals("SS13325", headers.codMin)
        assertEquals(FakeEngine.SERVER_INSTANT.plusSeconds(3600), headers.tokenExpiresAt)
    }

    @Test
    fun `expired token triggers transparent refresh`() = runTest {
        val store = InMemoryTokenStore()
        store.save(
            SessionSnapshot(
                token = Token(
                    accessToken = "at-old",
                    refreshToken = "rt-old",
                    expiresAt = FakeEngine.SERVER_INSTANT.plusSeconds(60), // inside the 120s slack
                    scope = "openid offline",
                    tokenType = "Bearer",
                ),
            ),
        )
        val engine =
            FakeEngine(
                "auth/refresh-token" to { FakeEngine.json(Fixtures.text("refresh-token-success.json")) },
            )
        val session = session(engine, store)
        session.restorePersistedSession()

        val headers = session.headersWithFreshBearer()

        assertEquals("at-refreshed-789", headers.bearer)
        assertTrue(session.authState is AuthState.TokenRefreshed)
        assertEquals("at-refreshed-789", store.load()?.token?.accessToken, "rotated token must be persisted")
        // Old bearer was sent for rotation, new one is live.
        val refreshBody = engine.requests.single().body.orEmpty()
        assertTrue("\"r-token\":\"rt-old\"" in refreshBody)
    }

    @Test
    fun `concurrent callers share a single refresh round`() = runTest {
        val store = InMemoryTokenStore()
        store.save(
            SessionSnapshot(
                token = Token(
                    accessToken = "at-old",
                    refreshToken = "rt-old",
                    expiresAt = FakeEngine.SERVER_INSTANT.minusSeconds(60), // firmly expired
                    scope = "openid offline",
                    tokenType = "Bearer",
                ),
            ),
        )
        val engine =
            FakeEngine(
                "auth/refresh-token" to { FakeEngine.json(Fixtures.text("refresh-token-success.json")) },
            )
        val session = session(engine, store)
        session.restorePersistedSession()

        // Eight truly-parallel callers all need a fresh bearer from the same
        // expired token; the single-flight mutex must collapse them into one POST.
        val headers =
            withContext(Dispatchers.Default) {
                (1..8).map { async { session.headersWithFreshBearer() } }.awaitAll()
            }

        val refreshes = engine.requests.count { it.url.contains("auth/refresh-token") }
        assertEquals(1, refreshes, "concurrent callers must share one rotation round")
        assertTrue(headers.all { it.bearer == "at-refreshed-789" }, "every caller gets the rotated bearer")
    }

    /** Firmly expired token: any refresh path must trigger rotation. */
    private fun expiredToken(): Token = Token(
        accessToken = "at-old",
        refreshToken = "rt-old",
        expiresAt = FakeEngine.SERVER_INSTANT.minusSeconds(60),
        scope = "openid offline",
        tokenType = "Bearer",
    )

    @Test
    fun `terminal refresh rejection wipes the session material`() = runTest {
        val store = InMemoryTokenStore()
        store.save(SessionSnapshot(token = expiredToken()))
        val engine =
            FakeEngine(
                "auth/refresh-token" to {
                    FakeEngine.json("""{"error":"invalid_grant","error_description":"pii"}""", status = 400)
                },
            )
        val session = session(engine, store)
        session.restorePersistedSession()

        val error = assertFailsWith<RefreshRejectedException> { session.headersWithFreshBearer() }

        assertTrue("re-authentication required" in error.detail)
        assertFalse("pii" in error.detail)
        assertNull(store.load(), "a dead grant must not survive on disk")
        assertFalse(session.isAuthenticated())
        assertTrue(session.authState is AuthState.Failed, "the machine must land on a terminal state")
        assertNull(session.headersWithFreshBearer().bearer, "headers return to unauthenticated")
    }

    @Test
    fun `transient refresh rejection keeps the session`() = runTest {
        val store = InMemoryTokenStore()
        store.save(SessionSnapshot(token = expiredToken()))
        val engine =
            FakeEngine(
                "auth/refresh-token" to {
                    FakeEngine.json("""{"error":"temporarily_unavailable"}""", status = 503)
                },
            )
        val session = session(engine, store)
        session.restorePersistedSession()

        val error = assertFailsWith<ArgoApiException> { session.headersWithFreshBearer() }

        assertEquals(503, error.httpStatus)
        assertTrue(session.isAuthenticated(), "a busy server must not log the user out")
        assertNotNull(store.load()?.token, "session material stays for a later retry")
    }

    @Test
    fun `refresh network failure keeps the session`() = runTest {
        val store = InMemoryTokenStore()
        store.save(SessionSnapshot(token = expiredToken()))
        val engine = FakeEngine("auth/refresh-token" to { throw IOException("connection reset") })
        val session = session(engine, store)
        session.restorePersistedSession()

        assertFailsWith<NetworkException> { session.headersWithFreshBearer() }

        assertTrue(session.isAuthenticated(), "transport noise must not log the user out")
        assertNotNull(store.load()?.token)
    }

    @Test
    fun `restore persists session material across instances`() = runTest {
        val store = InMemoryTokenStore()
        store.save(
            SessionSnapshot(
                token = Token(
                    accessToken = "at-restored",
                    refreshToken = "rt-restored",
                    expiresAt = FakeEngine.SERVER_INSTANT.plusSeconds(3600),
                    scope = "openid",
                    tokenType = "Bearer",
                ),
                loginData = it.hydr4.argo.models.LoginData("SS13325", "xat-restored"),
            ),
        )
        val session = session(FakeEngine(), store)

        assertTrue(session.restorePersistedSession())
        assertTrue(session.isAuthenticated())
        assertEquals("at-restored", session.headersWithFreshBearer().bearer)
        assertEquals("xat-restored", session.headersWithFreshBearer().xAuthToken)
    }

    @Test
    fun `missing snapshot reports false`() = runTest {
        val session = session(FakeEngine())
        assertFalse(session.restorePersistedSession())
    }

    @Test
    fun `unauthenticated headers are empty`() = runTest {
        val session = session(FakeEngine())
        val headers = session.headersWithFreshBearer()
        assertNull(headers.bearer)
        assertNull(headers.xAuthToken)
    }

    @Test
    fun `rejected credentials collapse to Failed state with an authentication error`() = runTest {
        val engine =
            FakeEngine(
                "oauth2/auth" to { FakeEngine.redirect("https://auth.portaleargo.it/login?login_challenge=ch-123") },
                "/auth/sso/login" to { FakeEngine.json("""{"error":"invalid"}""", status = 401) },
            )
        val session = session(engine)

        assertFailsWith<AuthenticationException> { session.loginWithCredentials(credentials) }
        assertTrue(session.authState is AuthState.Failed)
        assertFalse(session.isAuthenticated())
    }

    @Test
    fun `clear locally wipes memory and store`() = runTest {
        val store = InMemoryTokenStore()
        val engine = loginEngine()
        val session = session(engine, store)
        session.loginWithCredentials(credentials)
        session.clearLocally()
        assertFalse(session.isAuthenticated())
        assertNull(store.load())
        assertNull(session.headersWithFreshBearer().bearer)
    }
}
