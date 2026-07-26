package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoClientConfig
import java.net.URLEncoder

/**
 * Produces the OAuth2 authorize URL that starts the PKCE dance.
 *
 * Parameter order/encoding mirrors the reference client (`prompt=login`, encoded
 * space-joined scopes, urlencoded redirect URI) because the upstream server
 * validates them strictly.
 *
 * @property config Endpoint configuration.
 * @property pkce Challenge supplier so state machines can reuse pinned pairs.
 */
public class LoginLinkBuilder(private val config: ArgoClientConfig, private val pkce: PkceGenerator = PkceGenerator()) {
    /**
     * Builds the authorize URL plus everything needed for the later exchange.
     *
     * @param redirectUri Deep link target registered for the app.
     * @param scopes Requested scope list.
     * @param state Anti-CSRF marker echoed back by Hydra.
     * @param nonce OpenID nonce echoed back into the id_token.
     * @throws IllegalArgumentException when parameter limits are violated (rare).
     */
    public fun build(
        redirectUri: String = it.hydr4.argo.api.ArgoConstants.REDIRECT_URI,
        scopes: List<String> = it.hydr4.argo.api.ArgoConstants.SCOPES,
        state: String = NoncesBridge.alphanumeric22(),
        nonce: String = NoncesBridge.alphanumeric22(),
    ): AuthStart {
        val challenge = pkce.generate()
        val query =
            listOf(
                "redirect_uri" to URLEncoder.encode(redirectUri, Charsets.UTF_8),
                "client_id" to config.clientId,
                "response_type" to "code",
                "prompt" to "login",
                "state" to state,
                "nonce" to nonce,
                "scope" to URLEncoder.encode(scopes.joinToString(" "), Charsets.UTF_8),
                "code_challenge" to challenge.codeChallenge,
                "code_challenge_method" to "S256",
            ).joinToString("&") { (k, v) -> "$k=$v" }

        return AuthStart(
            authorizeUrl = "${config.oauthAuthorizeUrl}?$query",
            redirectUri = redirectUri,
            scopes = scopes,
            state = state,
            nonce = nonce,
            challenge = challenge,
        )
    }
}

/** Bridge supplying reference-compatible randomness sizes without leaking primitives. */
internal object NoncesBridge {
    fun alphanumeric22(): String = it.hydr4.argo.api.Nonces
        .alphanumeric(22)
}

/**
 * Everything a caller needs between starting the flow and exchanging the code.
 *
 * @property authorizeUrl URL to open (browser or embedded web view).
 * @property redirectUri Redirect registered for the deep-link callback.
 * @property scopes Scopes actually requested.
 * @property state CSRF marker to verify on callback.
 * @property nonce OIDC nonce to verify against `id_token`.
 * @property challenge PKCE pair completing the exchange.
 */
public data class AuthStart(
    public val authorizeUrl: String,
    public val redirectUri: String,
    public val scopes: List<String>,
    public val state: String,
    public val nonce: String,
    public val challenge: PkceChallenge,
)
