package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoConstants
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Byte-level shape of the OAuth2 authorize URL the reference server accepts. */
class LoginLinkBuilderTest {

    private val builder = LoginLinkBuilder(ArgoClientConfig())

    @Test
    fun `url carries every required oauth parameter`() {
        val start = builder.build(redirectUri = "it.argosoft.didup.famiglia.new://login-callback")
        val query = start.authorizeUrl.substringAfter('?')

        val params = parseQuery(query)
        assertEquals("code", params["response_type"])
        assertEquals("login", params["prompt"])
        assertEquals("S256", params["code_challenge_method"])
        assertEquals(ArgoConstants.CLIENT_ID, params["client_id"])
        assertEquals("openid offline profile user.roles argo", params["scope"])
        assertEquals("it.argosoft.didup.famiglia.new://login-callback", params["redirect_uri"])
        assertTrue(params.containsKey("code_challenge"))
        assertTrue(params.containsKey("state"))
        assertTrue(params.containsKey("nonce"))
        assertTrue(start.authorizeUrl.startsWith(ArgoConstants.OAUTH_AUTHORIZE_URL + "?"))
    }

    @Test
    fun `challenge matches the derived S256 of the generated verifier`() {
        val start = builder.build()
        assertEquals(PkceGenerator.deriveChallenge(start.challenge.codeVerifier), start.challenge.codeChallenge)
        val params = parseQuery(start.authorizeUrl.substringAfter('?'))
        assertEquals(start.challenge.codeChallenge, params["code_challenge"])
    }

    @Test
    fun `verifier is redacted in rendering`() {
        val start = builder.build()
        assertTrue("codeVerifier=●" in start.challenge.toString())
        kotlin.test.assertFalse(start.challenge.codeVerifier in start.challenge.toString())
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&').mapNotNull { part ->
        val (k, v) = part.split('=', limit = 2)
        k to URLDecoder.decode(v, Charsets.UTF_8)
    }.toMap()
}
