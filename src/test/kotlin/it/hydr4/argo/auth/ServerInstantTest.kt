package it.hydr4.argo.auth

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Expiry anchoring on the server-issued Date header. */
class ServerInstantTest {
    @Test
    fun `rfc1123 header parses onto an instant`() {
        assertEquals(
            Instant.parse("2026-08-25T08:00:00Z"),
            ServerInstant.fromHeader("Tue, 25 Aug 2026 08:00:00 GMT"),
        )
    }

    @Test
    fun `absent header falls back`() {
        val fallback = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals(fallback, ServerInstant.fromHeader(null, fallback))
    }

    @Test
    fun `unparseable header falls back instead of throwing`() {
        val fallback = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals(fallback, ServerInstant.fromHeader("not a date", fallback))
    }
}
