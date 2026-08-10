package it.hydr4.argo

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoHttpRequest
import it.hydr4.argo.exceptions.ArgoApiException
import it.hydr4.argo.exceptions.ArgoException
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.models.Dashboard
import it.hydr4.argo.testing.FakeEngine
import it.hydr4.argo.testing.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Contract tests for [ArgoClient.dashboardChanges]: change-driven emission,
 * snapshot deduplication, transient-error resilience and auth-failure shutdown.
 */
class DashboardChangesFlowTest {

    private val credentials = Credentials(schoolCode = "SS13325", username = "RSSLCA05T01", password = "s3cret")

    private val fixedClock: Clock = Clock.fixed(FakeEngine.SERVER_INSTANT, ZoneOffset.UTC)

    /** Routes shared by every scenario; per-test dashboards and probes are appended. */
    private fun loginRoutes(): Array<Pair<String, (ArgoHttpRequest) -> it.hydr4.argo.api.ArgoHttpResponse>> = arrayOf(
        "oauth2/auth" to { FakeEngine.redirect("https://auth.portaleargo.it/login?login_challenge=ch-123") },
        "/auth/sso/login" to { FakeEngine.redirect("it.argosoft.didup.famiglia.new://login-callback?code=code-abc") },
        "oauth2/token" to { FakeEngine.json(Fixtures.text("oauth-token-success.json")) },
        "appfamiglia/api/rest/login" to { FakeEngine.json(Fixtures.text("login-family-success.json")) },
        "api/rest/profilo" to { FakeEngine.json(Fixtures.text("profile-success.json")) },
        "dashboard/aggiornadata" to { FakeEngine.json("""{"success":true}""") },
    )

    private fun loginClient(engine: FakeEngine): ArgoClient =
        ArgoClient.create(config = ArgoClientConfig(), engine = engine, clock = fixedClock)

    private fun engineWith(vararg extra: Pair<String, (ArgoHttpRequest) -> it.hydr4.argo.api.ArgoHttpResponse>): FakeEngine =
        FakeEngine(*loginRoutes(), *extra)

    @Test
    fun `emits on upstream change then stays silent on clean rounds`() = runTest {
        var dashboardCalls = 0
        var whatCalls = 0
        val full = Fixtures.text("dashboard-full.json")
        val engine =
            engineWith(
                "dashboard/dashboard" to { _ ->
                    FakeEngine.json(
                        if (++dashboardCalls == 1) {
                            full
                        } else {
                            full.replace("\"mediaGenerale\": \"7.25\"", "\"mediaGenerale\": \"8.00\"")
                        },
                    )
                },
                "dashboard/what" to { _ ->
                    FakeEngine.json(Fixtures.text(if (++whatCalls == 1) "dashboard-what-modified.json" else "dashboard-what-clean.json"))
                },
            )
        val client = loginClient(engine)
        assertEquals(7.25, client.login(credentials).overallAverage)

        val emissions = mutableListOf<Dashboard>()
        val errors = mutableListOf<Throwable>()
        val job =
            backgroundScope.launch {
                client.dashboardChanges(interval = Duration.ofMinutes(5), onError = { errors += it })
                    .collect { emissions += it }
            }
        runCurrent() // round 1: probe says modified, fetch returns a new snapshot
        assertEquals(1, emissions.size)
        assertEquals(8.0, emissions.single().overallAverage)

        advanceTimeBy(Duration.ofMinutes(10).toMillis()) // rounds 2-3: clean probes
        runCurrent()
        assertEquals(1, emissions.size, "clean rounds must not re-emit the cached snapshot")
        assertTrue(errors.isEmpty())
        job.cancel()
    }

    @Test
    fun `modified rounds whose snapshot is unchanged are deduplicated`() = runTest {
        val engine =
            engineWith(
                "dashboard/dashboard" to { _ -> FakeEngine.json(Fixtures.text("dashboard-full.json")) },
                "dashboard/what" to { _ -> FakeEngine.json(Fixtures.text("dashboard-what-modified.json")) },
            )
        val client = loginClient(engine)
        client.login(credentials)

        val emissions = mutableListOf<Dashboard>()
        val job =
            backgroundScope.launch {
                client.dashboardChanges(interval = Duration.ofMinutes(5)).collect { emissions += it }
            }
        runCurrent()
        advanceTimeBy(Duration.ofMinutes(15).toMillis())
        runCurrent()
        assertTrue(emissions.isEmpty(), "a refetch that yields an equal snapshot is not a change")
        job.cancel()
    }

    @Test
    fun `transient errors are reported and polling resumes`() = runTest {
        var dashboardCalls = 0
        var failing = true
        val full = Fixtures.text("dashboard-full.json")
        val engine =
            engineWith(
                "dashboard/dashboard" to { _ ->
                    FakeEngine.json(
                        if (++dashboardCalls == 1) {
                            full
                        } else {
                            full.replace("\"mediaGenerale\": \"7.25\"", "\"mediaGenerale\": \"8.00\"")
                        },
                    )
                },
                "dashboard/what" to { _ ->
                    // The transport retries 5xx noise, so the probe must stay failing
                    // through every retry attempt before the error reaches the sink.
                    if (failing) throw ArgoApiException("dashboard/what", 500, "boom")
                    FakeEngine.json(Fixtures.text("dashboard-what-modified.json"))
                },
            )
        val client = loginClient(engine)
        client.login(credentials)

        val emissions = mutableListOf<Dashboard>()
        val errors = mutableListOf<Throwable>()
        val job =
            backgroundScope.launch {
                client.dashboardChanges(interval = Duration.ofMinutes(5), onError = { errors += it })
                    .collect { emissions += it }
            }
        runCurrent() // round 1 attempt 1: probe blows up, a retry is scheduled
        advanceTimeBy(Duration.ofMinutes(1).toMillis()) // remaining attempts also fail
        runCurrent()
        assertEquals(1, errors.size, "one failing round reports one error after retries")
        assertTrue(errors.single() is ArgoException)

        failing = false
        advanceTimeBy(Duration.ofMinutes(10).toMillis()) // next poll round: recovers and emits
        runCurrent()
        assertEquals(1, emissions.size)
        assertEquals(8.0, emissions.single().overallAverage)
        job.cancel()
    }

    @Test
    fun `failing rounds back off exponentially up to the cap`() = runTest {
        // A 200 success:false answer is not retryable transport noise, so every
        // probe fails instantly and error timestamps land on exact minute marks.
        val engine =
            engineWith(
                "dashboard/dashboard" to { _ -> FakeEngine.json(Fixtures.text("dashboard-full.json")) },
                "dashboard/what" to { _ -> FakeEngine.json("""{"success":false,"msg":"boom"}""") },
            )
        val client = loginClient(engine)
        client.login(credentials)

        val errorTimes = mutableListOf<Long>()
        val job =
            backgroundScope.launch {
                client.dashboardChanges(
                    interval = Duration.ofMinutes(1),
                    maxBackoff = Duration.ofMinutes(8),
                    jitter = Duration.ZERO,
                    onError = { errorTimes += currentTime },
                ).collect { }
            }
        runCurrent() // round 1 fails at t=0, backoff doubles to 2 minutes
        advanceTimeBy(Duration.ofMinutes(60).toMillis())
        runCurrent()

        // Rounds at 0, 2, 6, 14 then every 8 minutes once the cap is hit.
        val gaps = errorTimes.zipWithNext().map { (a, b) -> b - a }
        assertEquals(
            listOf(2L, 4L, 8L, 8L, 8L, 8L, 8L, 8L).map { Duration.ofMinutes(it).toMillis() },
            gaps,
            "failure delays must double and cap at maxBackoff",
        )
        job.cancel()
    }

    @Test
    fun `a healthy round restores the base schedule`() = runTest {
        var failing = true
        val engine =
            engineWith(
                "dashboard/dashboard" to { _ -> FakeEngine.json(Fixtures.text("dashboard-full.json")) },
                "dashboard/what" to { _ ->
                    if (failing) {
                        FakeEngine.json("""{"success":false,"msg":"boom"}""")
                    } else {
                        FakeEngine.json(Fixtures.text("dashboard-what-clean.json"))
                    }
                },
            )
        val client = loginClient(engine)
        client.login(credentials)

        val errorTimes = mutableListOf<Long>()
        val job =
            backgroundScope.launch {
                client.dashboardChanges(
                    interval = Duration.ofMinutes(1),
                    maxBackoff = Duration.ofMinutes(8),
                    jitter = Duration.ZERO,
                    onError = { errorTimes += currentTime },
                ).collect { }
            }
        runCurrent() // t=0: round 1 fails → backoff 2m
        failing = false
        advanceTimeBy(Duration.ofMinutes(2).toMillis()) // t=2: clean round resets to 1m
        runCurrent()
        failing = true
        advanceTimeBy(Duration.ofMinutes(1).toMillis()) // t=3: fails again → backoff 2m, not 4m
        runCurrent()
        advanceTimeBy(Duration.ofMinutes(2).toMillis()) // t=5: fails
        runCurrent()

        // Errors at 0, 3 and 5 minutes: the 3→5 gap is the base interval doubled
        // again after the reset — without the reset it would be 4 minutes.
        assertEquals(
            listOf(0L, 180_000L, 300_000L),
            errorTimes,
            "a clean round must reset the backoff to the base interval",
        )
        job.cancel()
    }

    @Test
    fun `invalid backoff parameters are rejected at call time`() {
        val client = loginClient(FakeEngine())
        assertFailsWith<IllegalArgumentException> {
            client.dashboardChanges(interval = Duration.ofMinutes(5), maxBackoff = Duration.ofMinutes(1))
        }
        assertFailsWith<IllegalArgumentException> {
            client.dashboardChanges(interval = Duration.ofMinutes(5), jitter = Duration.ofSeconds(-1))
        }
    }

    @Test
    fun `authentication failure terminates the flow`() = runTest {
        val engine =
            engineWith(
                "dashboard/dashboard" to { _ -> FakeEngine.json(Fixtures.text("dashboard-full.json")) },
                "dashboard/what" to { _ -> FakeEngine.json("""{"success":true,"data":{"forceLogin":true}}""") },
            )
        val client = loginClient(engine)
        client.login(credentials)

        val error =
            assertFailsWith<AuthenticationException> {
                client.dashboardChanges(interval = Duration.ofMinutes(5)).first()
            }
        assertTrue("re-authentication" in error.detail)
    }
}
