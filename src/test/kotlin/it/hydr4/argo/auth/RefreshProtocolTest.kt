package it.hydr4.argo.auth

import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.exceptions.DeserializationException
import it.hydr4.argo.exceptions.RefreshRejectedException
import it.hydr4.argo.testing.FakeEngine
import it.hydr4.argo.testing.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Byte-shape tests of the custom `auth/refresh-token` protocol. */
class RefreshProtocolTest {
    @Test
    fun `body mirrors the reference client key set`() {
        val body = RefreshProtocol.buildBody(
            refreshToken = "rt-old",
            oldBearer = "at-old",
            currentExpiry = FakeEngine.SERVER_INSTANT.plusSeconds(600),
            scope = "openid offline profile",
            username = "RSSLCA05T01",
            now = FakeEngine.SERVER_INSTANT,
        )
        assertEquals("rt-old", body["r-token"]!!.toString().trim('"'))
        assertEquals("at-old", body["old-bearer"]!!.toString().trim('"'))
        assertEquals("[openid, offline, profile]", body["scopes"]!!.toString().trim('"'))
        assertEquals("false", body["primo-accesso"]!!.toString().trim('"'))
        assertNull(body["username-list"], "no stray keys may appear")
        assertTrue(body.containsKey("exp-bearer"))
        assertTrue(body.containsKey("ts-app"))
        assertTrue(body.containsKey("proc"))
        assertTrue(body.containsKey("client-id"))
        assertTrue(body.containsKey("username"))
    }

    @Test
    fun `username key is omitted when login data is missing`() {
        val body = RefreshProtocol.buildBody("rt", "at", FakeEngine.SERVER_INSTANT, "openid", username = null)
        assertFalse(body.containsKey("username"))
    }

    @Test
    fun `response parses onto a token anchored on the server date header`() {
        val token = RefreshProtocol.parseResponse(Fixtures.text("refresh-token-success.json"), FakeEngine.SERVER_DATE)
        assertEquals("at-refreshed-789", token.accessToken)
        assertEquals("rt-refreshed-012", token.refreshToken)
        assertEquals(FakeEngine.SERVER_INSTANT.plusSeconds(3600), token.expiresAt)
        assertEquals("Bearer", token.tokenType)
    }

    @Test
    fun `terminal rejection maps to RefreshRejectedException without echoing the description`() {
        val exception = assertFailsWith<RefreshRejectedException> {
            RefreshProtocol.parseResponse("""{"error":"invalid_grant","error_description":"pii-here"}""", FakeEngine.SERVER_DATE)
        }
        assertTrue("invalid_grant" in exception.detail)
        assertFalse("pii-here" in exception.detail, "error_description content must never be surfaced")
    }

    @Test
    fun `non-terminal rejection stays a plain authentication failure`() {
        val exception = assertFailsWith<AuthenticationException> {
            RefreshProtocol.parseResponse("""{"error":"temporarily_unavailable","error_description":"busy"}""", FakeEngine.SERVER_DATE)
        }
        assertFalse(exception is RefreshRejectedException, "a busy server must not kill the session")
        assertFalse("busy" in exception.detail, "error_description content must never be surfaced")
    }

    @Test
    fun `malformed body maps to deserialization failure`() {
        assertFailsWith<DeserializationException> {
            RefreshProtocol.parseResponse("<html>gateway error</html>", FakeEngine.SERVER_DATE)
        }
        assertFailsWith<DeserializationException> { RefreshProtocol.parseResponse("[1,2,3]", FakeEngine.SERVER_DATE) }
    }
}
