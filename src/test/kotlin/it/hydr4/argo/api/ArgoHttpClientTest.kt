package it.hydr4.argo.api

import it.hydr4.argo.exceptions.ArgoApiException
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.exceptions.DeserializationException
import it.hydr4.argo.exceptions.NetworkException
import it.hydr4.argo.exceptions.ProtocolException
import it.hydr4.argo.exceptions.RefreshRejectedException
import it.hydr4.argo.models.WhatResult
import it.hydr4.argo.testing.FakeEngine
import it.hydr4.argo.testing.Fixtures
import it.hydr4.argo.time.TimeFormats
import it.hydr4.argo.util.RetryPolicy
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Transport contract of the authenticated REST facade. */
class ArgoHttpClientTest {

    private val fixedHeaders =
        AuthHeaders(
            bearer = "bearer-secret",
            xAuthToken = "xauth-secret",
            codMin = "SS13325",
            tokenExpiresAt = Instant.parse("2026-08-25T09:00:00Z"),
        )

    private val session =
        object : SessionContext {
            override suspend fun headersWithFreshBearer(): AuthHeaders = fixedHeaders
            override suspend fun currentHeaders(): AuthHeaders? = fixedHeaders
            override suspend fun forceRefresh(): AuthHeaders = fixedHeaders
        }

    @Test
    fun `request carries the complete reference header set`() {
        val engine = FakeEngine("dashboard/what" to { FakeEngine.json(Fixtures.text("dashboard-what-clean.json")) })
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)
        kotlinx.coroutines.test.runTest {
            client.fetch(
                path = Endpoints.DASHBOARD_WHAT,
                body = buildJsonObject { put("dataultimoaggiornamento", "2026-08-25 08:00:00.000") },
                dataSerializer = WhatResult.serializer(),
            )
        }
        val request = engine.requests.single()
        assertEquals(HttpMethod.POST, request.method, "body presence must upgrade GET to POST")
        assertEquals("Bearer bearer-secret", request.headers["authorization"])
        assertEquals("xauth-secret", request.headers["x-auth-token"])
        assertEquals("SS13325", request.headers["x-cod-min"])
        // Rendered in the machine zone exactly like the transport does, so the test is zone-independent.
        assertEquals(TimeFormats.formatWire(fixedHeaders.tokenExpiresAt!!), request.headers["x-date-exp-auth"])
        assertEquals("1.27.0", request.headers["argo-client-version"])
        assertEquals("application/json", request.headers["content-type"])
        assertEquals("application/json", request.headers["accept"])
        assertTrue(request.url.startsWith(ArgoConstants.REST_BASE_URL))
        assertTrue("dashboard/what" in request.url)
    }

    @Test
    fun `success false maps to ArgoApiException carrying the server message`() {
        val engine = FakeEngine("profilo" to { FakeEngine.json(Fixtures.text("error-envelope.json")) })
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)
        val error = assertFailsWith<ArgoApiException> {
            kotlinx.coroutines.test.runTest { client.fetch(Endpoints.PROFILE, dataSerializer = WhatResult.serializer()) }
        }
        assertEquals(Endpoints.PROFILE, error.endpoint)
        assertTrue("Sessione scaduta" in error.detail)
    }

    @Test
    fun `malformed body maps to DeserializationException`() {
        val engine = FakeEngine("profilo" to { FakeEngine.json("{not json") })
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)
        assertFailsWith<DeserializationException> {
            kotlinx.coroutines.test.runTest { client.fetch(Endpoints.PROFILE, dataSerializer = WhatResult.serializer()) }
        }
    }

    @Test
    fun `envelope without a success flag maps to ProtocolException`() {
        val engine = FakeEngine("profilo" to { FakeEngine.json("""{"data":{}}""") })
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)
        assertFailsWith<ProtocolException> {
            kotlinx.coroutines.test.runTest { client.fetch(Endpoints.PROFILE, dataSerializer = WhatResult.serializer()) }
        }
    }

    @Test
    fun `envelope without data maps to ArgoApiException`() {
        val engine = FakeEngine("profilo" to { FakeEngine.json("""{"success":true,"data":null}""") })
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)
        assertFailsWith<ArgoApiException> {
            kotlinx.coroutines.test.runTest { client.fetch(Endpoints.PROFILE, dataSerializer = WhatResult.serializer()) }
        }
    }

    @Test
    fun `transport failure maps to NetworkException`() {
        val engine = FakeEngine("profilo" to { throw IOException("connection reset") })
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)
        val error = assertFailsWith<NetworkException> {
            kotlinx.coroutines.test.runTest { client.fetch(Endpoints.PROFILE, dataSerializer = WhatResult.serializer()) }
        }
        assertFalse("connection reset" in error.detail, "network details may carry sensitive hints")
        assertTrue("Network failure" in error.detail)
    }

    @Test
    fun `transient network failure is retried and succeeds`() = kotlinx.coroutines.test.runTest {
        var calls = 0
        val engine =
            FakeEngine(
                "dashboard/what" to { _ ->
                    if (++calls == 1) throw IOException("connection reset")
                    FakeEngine.json(Fixtures.text("dashboard-what-clean.json"))
                },
            )
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)

        client.fetch(Endpoints.DASHBOARD_WHAT, dataSerializer = WhatResult.serializer())

        assertEquals(2, engine.requests.size, "the failed attempt must be retried")
    }

    @Test
    fun `server 5xx is retried and succeeds`() = kotlinx.coroutines.test.runTest {
        var calls = 0
        val engine =
            FakeEngine(
                "dashboard/what" to { _ ->
                    if (++calls == 1) {
                        FakeEngine.json("""{"success":false,"msg":"boom"}""", status = 500)
                    } else {
                        FakeEngine.json(Fixtures.text("dashboard-what-clean.json"))
                    }
                },
            )
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)

        client.fetch(Endpoints.DASHBOARD_WHAT, dataSerializer = WhatResult.serializer())

        assertEquals(2, engine.requests.size, "server noise must be retried")
    }

    @Test
    fun `application rejection is not retried`() = kotlinx.coroutines.test.runTest {
        var calls = 0
        val engine =
            FakeEngine(
                "profilo" to { _ ->
                    calls++
                    FakeEngine.json(Fixtures.text("error-envelope.json"))
                },
            )
        val client = ArgoHttpClient(engine, ArgoClientConfig(), session)

        assertFailsWith<ArgoApiException> { client.fetch(Endpoints.PROFILE, dataSerializer = WhatResult.serializer()) }

        assertEquals(1, calls, "an answered rejection is not noise and must not be repeated")
    }

    @Test
    fun `consumer tuned retry policy is honored by the transport`() = kotlinx.coroutines.test.runTest {
        var calls = 0
        val engine =
            FakeEngine(
                "dashboard/what" to { _ ->
                    if (++calls == 1) throw IOException("connection reset")
                    FakeEngine.json(Fixtures.text("dashboard-what-clean.json"))
                },
            )
        val client =
            ArgoHttpClient(
                engine,
                ArgoClientConfig(retryPolicy = RetryPolicy(maxAttempts = 1)),
                session,
            )

        assertFailsWith<NetworkException> {
            client.fetch(Endpoints.DASHBOARD_WHAT, dataSerializer = WhatResult.serializer())
        }

        assertEquals(1, calls, "maxAttempts=1 must disable retries entirely")
    }

    @Test
    fun `a 401 rejection rotates the bearer once and re-sends`() = kotlinx.coroutines.test.runTest {
        var calls = 0
        var rotations = 0
        val engine =
            FakeEngine(
                "dashboard/what" to { _ ->
                    if (++calls == 1) {
                        FakeEngine.json("""{"success":false,"msg":"Sessione scaduta"}""", status = 401)
                    } else {
                        FakeEngine.json(Fixtures.text("dashboard-what-clean.json"))
                    }
                },
            )
        val rotatingSession =
            object : SessionContext {
                override suspend fun headersWithFreshBearer(): AuthHeaders = fixedHeaders
                override suspend fun currentHeaders(): AuthHeaders? = fixedHeaders
                override suspend fun forceRefresh(): AuthHeaders {
                    rotations++
                    return fixedHeaders
                }
            }
        val client = ArgoHttpClient(engine, ArgoClientConfig(), rotatingSession)

        client.fetch(Endpoints.DASHBOARD_WHAT, dataSerializer = WhatResult.serializer())

        assertEquals(2, calls, "the rejected request must be re-sent once")
        assertEquals(1, rotations, "exactly one rotation before the re-send")
    }

    @Test
    fun `a second 401 after rotation propagates without looping`() = kotlinx.coroutines.test.runTest {
        var calls = 0
        var rotations = 0
        val engine =
            FakeEngine(
                "dashboard/what" to { _ ->
                    calls++
                    FakeEngine.json("""{"success":false,"msg":"Sessione scaduta"}""", status = 401)
                },
            )
        val rotatingSession =
            object : SessionContext {
                override suspend fun headersWithFreshBearer(): AuthHeaders = fixedHeaders
                override suspend fun currentHeaders(): AuthHeaders? = fixedHeaders
                override suspend fun forceRefresh(): AuthHeaders {
                    rotations++
                    return fixedHeaders
                }
            }
        val client = ArgoHttpClient(engine, ArgoClientConfig(), rotatingSession)

        assertFailsWith<ArgoApiException> {
            client.fetch(Endpoints.DASHBOARD_WHAT, dataSerializer = WhatResult.serializer())
        }

        assertEquals(2, calls, "exactly one re-send, never a loop")
        assertEquals(1, rotations)
    }

    @Test
    fun `terminal rotation failure propagates without re-sending`() = kotlinx.coroutines.test.runTest {
        val engine =
            FakeEngine(
                "dashboard/what" to { _ -> FakeEngine.json("""{"success":false,"msg":"Sessione scaduta"}""", status = 401) },
            )
        val deadSession =
            object : SessionContext {
                override suspend fun headersWithFreshBearer(): AuthHeaders = fixedHeaders
                override suspend fun currentHeaders(): AuthHeaders? = fixedHeaders
                override suspend fun forceRefresh(): AuthHeaders = throw RefreshRejectedException("grant dead")
            }
        val client = ArgoHttpClient(engine, ArgoClientConfig(), deadSession)

        assertFailsWith<RefreshRejectedException> {
            client.fetch(Endpoints.DASHBOARD_WHAT, dataSerializer = WhatResult.serializer())
        }

        assertEquals(1, engine.requests.size, "no re-send after a dead grant")
    }

    @Test
    fun `authentication failure is not retried and no request goes out`() = kotlinx.coroutines.test.runTest {
        val rejectingSession =
            object : SessionContext {
                override suspend fun headersWithFreshBearer(): AuthHeaders = throw AuthenticationException("session dead")

                override suspend fun currentHeaders(): AuthHeaders? = null

                override suspend fun forceRefresh(): AuthHeaders = throw AuthenticationException("session dead")
            }
        val engine = FakeEngine("dashboard/what" to { FakeEngine.json(Fixtures.text("dashboard-what-clean.json")) })
        val client = ArgoHttpClient(engine, ArgoClientConfig(), rejectingSession)

        assertFailsWith<AuthenticationException> {
            client.fetch(Endpoints.DASHBOARD_WHAT, dataSerializer = WhatResult.serializer())
        }
        assertTrue(engine.requests.isEmpty(), "auth failures must fail before any network traffic")
    }
}
