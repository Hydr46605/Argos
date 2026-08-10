package it.hydr4.argo.models

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Expiry-boundary behavior driving the automatic refresh trigger. */
class TokenTest {

    private val now: Instant = Instant.parse("2026-08-25T08:00:00Z")

    private fun token(expiresAt: Instant): Token = Token(
        accessToken = "at",
        refreshToken = "rt",
        expiresAt = expiresAt,
        scope = "openid",
        tokenType = "Bearer",
    )

    @Test
    fun `expired token is flagged for refresh`() {
        assertTrue(token(now.minusSeconds(1)).isExpiredOrExpiringWithin(120, now))
    }

    @Test
    fun `token inside the slack window is flagged`() {
        assertTrue(token(now.plusSeconds(60)).isExpiredOrExpiringWithin(120, now))
    }

    @Test
    fun `token at exactly the slack boundary is flagged`() {
        assertTrue(token(now.plusSeconds(120)).isExpiredOrExpiringWithin(120, now))
    }

    @Test
    fun `token beyond the slack window is left alone`() {
        assertFalse(token(now.plusSeconds(121)).isExpiredOrExpiringWithin(120, now))
    }

    @Test
    fun `toString never renders the credentials`() {
        val rendered = token(now).toString()
        assertFalse("at" in rendered || "rt" in rendered)
        kotlin.test.assertTrue("●" in rendered)
    }
}
