package it.hydr4.argo.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * PKCE contract tests including the RFC 7636 Appendix B vector, so the S256
 * derivation is provably byte-compatible with any conforming OAuth2 server.
 */
class PkceGeneratorTest {
    @Test
    fun `challenge derivation matches RFC 7636 appendix B vector`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", PkceGenerator.deriveChallenge(verifier))
    }

    @Test
    fun `default verifier has 43 alphanumeric characters`() {
        val generator = PkceGenerator()
        val challenge = generator.generate()
        assertEquals(43, challenge.codeVerifier.length)
        assertTrue(challenge.codeVerifier.all { it.isLetterOrDigit() }, "verifier must stay alphanumeric")
    }

    @Test
    fun `challenge is unpadded base64url of the verifier digest`() {
        val generator = PkceGenerator()
        val challenge = generator.generate(64)
        assertEquals(64, challenge.codeVerifier.length)
        assertEquals(PkceGenerator.deriveChallenge(challenge.codeVerifier), challenge.codeChallenge)
        assertTrue(
            !challenge.codeChallenge.contains('=') && !challenge.codeChallenge.contains('+') && !challenge.codeChallenge.contains('/'),
        )
    }

    @Test
    fun `verifier length outside RFC bounds is rejected`() {
        val generator = PkceGenerator()
        assertFailsWith<IllegalArgumentException> { generator.generate(42) }
        assertFailsWith<IllegalArgumentException> { generator.generate(129) }
    }
}
