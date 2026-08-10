package it.hydr4.argo

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoConstants
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.models.Dashboard
import it.hydr4.argo.sync.PollDecision
import it.hydr4.argo.testing.FakeEngine
import it.hydr4.argo.testing.Fixtures
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Milestone scenario end-to-end over the fake transport: a full credential login
 * that lands on a [Dashboard] with `mediaGenerale` and `voti` populated, followed
 * by change-probe driven synchronization and logout hygiene.
 */
class ArgoClientTest {

    private val credentials = Credentials(schoolCode = "SS13325", username = "RSSLCA05T01", password = "s3cret")

    private val fixedClock: Clock = Clock.fixed(FakeEngine.SERVER_INSTANT, ZoneOffset.UTC)

    private fun fullEngine(): FakeEngine = FakeEngine(
        "oauth2/auth" to { FakeEngine.redirect("https://auth.portaleargo.it/login?login_challenge=ch-123") },
        "/auth/sso/login" to { FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?code=code-abc") },
        "oauth2/token" to { FakeEngine.json(Fixtures.text("oauth-token-success.json")) },
        "appfamiglia/api/rest/login" to { FakeEngine.json(Fixtures.text("login-family-success.json")) },
        "api/rest/profilo" to { FakeEngine.json(Fixtures.text("profile-success.json")) },
        "dashboard/dashboard" to { FakeEngine.json(Fixtures.text("dashboard-full.json")) },
        "dashboard/aggiornadata" to { FakeEngine.json("""{"success":true}""") },
        "rimuoviprofilo" to { FakeEngine.json("""{"success":true}""") },
    )

    @Test
    fun `login returns a dashboard with overall average and grades populated`() = runTest {
        val engine = fullEngine()
        val client = ArgoClient.create(config = ArgoClientConfig(), engine = engine, clock = fixedClock)

        val dashboard = client.login(credentials)

        assertEquals(7.25, dashboard.overallAverage)
        assertEquals(2, dashboard.grades.size)
        assertEquals("MATEMATICA", dashboard.grades.first().subjectName)
        assertEquals(7.5, dashboard.grades.first().value)
        assertEquals(2, dashboard.subjects.size)
        assertEquals(1, dashboard.bulletins.size)
        assertEquals(1, dashboard.lessons.size)
        assertTrue(client.dashboard() === dashboard, "facade caches the last snapshot")
        assertNotNull(dashboard.fetchedAt)

        // The login choreography hit every expected endpoint exactly once.
        val urls = engine.requests.map { it.url }
        assertEquals(7, urls.size)
        assertTrue(urls.any { it.contains("oauth2/auth") })
        assertTrue(urls.any { it.contains("oauth2/token") })
        assertTrue(urls.any { it.contains("appfamiglia/api/rest/login") })
        assertTrue(urls.any { it.contains("api/rest/profilo") })
        assertTrue(urls.any { it.contains("dashboard/dashboard") })
        assertTrue(urls.any { it.contains("dashboard/aggiornadata") })
        // Session headers ride on data endpoints.
        val dashboardRequest = engine.requests.first { it.url.contains("dashboard/dashboard") }
        assertEquals("Bearer at-secret-123", dashboardRequest.headers["authorization"])
        assertEquals("x-auth-session-token", dashboardRequest.headers["x-auth-token"])
        assertEquals("SS13325", dashboardRequest.headers["x-cod-min"])
        assertEquals(ArgoConstants.DIDUP_VERSION, dashboardRequest.headers["argo-client-version"])
    }

    @Test
    fun `synchronize with a clean probe reuses the cached snapshot`() = runTest {
        val engine =
            FakeEngine(
                "oauth2/auth" to { FakeEngine.redirect("https://auth.portaleargo.it/login?login_challenge=ch-123") },
                "/auth/sso/login" to { FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?code=code-abc") },
                "oauth2/token" to { FakeEngine.json(Fixtures.text("oauth-token-success.json")) },
                "appfamiglia/api/rest/login" to { FakeEngine.json(Fixtures.text("login-family-success.json")) },
                "api/rest/profilo" to { FakeEngine.json(Fixtures.text("profile-success.json")) },
                "dashboard/dashboard" to { FakeEngine.json(Fixtures.text("dashboard-full.json")) },
                "dashboard/aggiornadata" to { FakeEngine.json("""{"success":true}""") },
                "dashboard/what" to { FakeEngine.json(Fixtures.text("dashboard-what-clean.json")) },
            )
        val client = ArgoClient.create(config = ArgoClientConfig(), engine = engine, clock = fixedClock)
        val first = client.login(credentials)

        val (snapshot, decision) = client.synchronize()

        assertEquals(PollDecision.Clean, decision)
        assertTrue(snapshot === first, "clean probe must short-circuit to the cached snapshot")
        val probeCount = engine.requests.count { it.url.contains("dashboard/what") }
        assertEquals(1, probeCount)
        val dashboardFetchCount = engine.requests.count { it.url.contains("dashboard/dashboard") }
        assertEquals(1, dashboardFetchCount, "no second full fetch after a clean probe")
    }

    @Test
    fun `synchronize with modifications triggers a fresh dashboard fetch`() = runTest {
        val engine =
            FakeEngine(
                "oauth2/auth" to { FakeEngine.redirect("https://auth.portaleargo.it/login?login_challenge=ch-123") },
                "/auth/sso/login" to { FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?code=code-abc") },
                "oauth2/token" to { FakeEngine.json(Fixtures.text("oauth-token-success.json")) },
                "appfamiglia/api/rest/login" to { FakeEngine.json(Fixtures.text("login-family-success.json")) },
                "api/rest/profilo" to { FakeEngine.json(Fixtures.text("profile-success.json")) },
                "dashboard/dashboard" to { FakeEngine.json(Fixtures.text("dashboard-full.json")) },
                "dashboard/aggiornadata" to { FakeEngine.json("""{"success":true}""") },
                "dashboard/what" to { FakeEngine.json(Fixtures.text("dashboard-what-modified.json")) },
            )
        val client = ArgoClient.create(config = ArgoClientConfig(), engine = engine, clock = fixedClock)
        client.login(credentials)

        val (snapshot, decision) = client.synchronize()

        assertTrue(decision is PollDecision.FetchDashboard)
        assertNotNull(snapshot)
        assertEquals(2, engine.requests.count { it.url.contains("dashboard/dashboard") })
    }

    @Test
    fun `synchronize with a forceLogin probe requires re-authentication`() = runTest {
        val engine =
            FakeEngine(
                "oauth2/auth" to { FakeEngine.redirect("https://auth.portaleargo.it/login?login_challenge=ch-123") },
                "/auth/sso/login" to { FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?code=code-abc") },
                "oauth2/token" to { FakeEngine.json(Fixtures.text("oauth-token-success.json")) },
                "appfamiglia/api/rest/login" to { FakeEngine.json(Fixtures.text("login-family-success.json")) },
                "api/rest/profilo" to { FakeEngine.json(Fixtures.text("profile-success.json")) },
                "dashboard/dashboard" to { FakeEngine.json(Fixtures.text("dashboard-full.json")) },
                "dashboard/aggiornadata" to { FakeEngine.json("""{"success":true}""") },
                "dashboard/what" to { FakeEngine.json("""{"success":true,"data":{"forceLogin":true}}""") },
            )
        val client = ArgoClient.create(config = ArgoClientConfig(), engine = engine, clock = fixedClock)
        client.login(credentials)

        val error = assertFailsWith<it.hydr4.argo.exceptions.AuthenticationException> {
            client.synchronize()
        }
        assertTrue("re-authentication" in error.detail)
    }

    @Test
    fun `logout wipes the local session`() = runTest {
        val engine = fullEngine()
        val client = ArgoClient.create(config = ArgoClientConfig(), engine = engine, clock = fixedClock)
        client.login(credentials)

        client.logout()

        assertNull(client.dashboard())
        assertTrue(!client.session.isAuthenticated())
    }
}
