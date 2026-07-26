package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoHttpEngine
import it.hydr4.argo.api.ArgoHttpRequest
import it.hydr4.argo.exceptions.ArgoException
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.exceptions.NetworkException
import it.hydr4.argo.models.Credentials
import java.io.IOException

/**
 * The browser part of the flow, executed headlessly: fetches Hydra's
 * `login_challenge` from the authorize URL, then posts credentials to the SSO
 * endpoint exactly like the official web app does.
 *
 * Implementations must keep cookies between the two calls (Hydra binds them) and
 * must not follow redirects during either hop.
 *
 * **Security:** implementations accept credentials only through parameters and
 * never log request bodies.
 */
public interface SsoCodeBroker {
    /** Extracts the `login_challenge` query parameter from the authorize redirect. */
    public suspend fun fetchChallenge(authorizeUrl: String): String

    /**
     * Submits [credentials] against [loginChallenge] and returns the authorization code.
     *
     * @throws AuthenticationException when credentials are rejected or redirect is malformed.
     */
    public suspend fun exchangeCredentialsForCode(loginChallenge: String, credentials: Credentials): String
}

/**
 * Engine-backed implementation mirroring the reference HTTP choreography.
 *
 * @property engine Must be constructed with an in-memory cookie jar.
 * @property config Endpoint configuration for the SSO URL and client id.
 */
public class DefaultSsoCodeBroker(private val engine: ArgoHttpEngine, private val config: ArgoClientConfig = ArgoClientConfig()) :
    SsoCodeBroker {
    override suspend fun fetchChallenge(authorizeUrl: String): String {
        val location =
            try {
                engine.execute(ArgoHttpRequest(url = authorizeUrl)).header("location")
            } catch (e: IOException) {
                throw NetworkException(e)
            } ?: throw ProtocolFailure("authorize endpoint did not redirect; login page unavailable")
        return parseQuery(location, CHALLENGE_KEY)
            ?: throw ProtocolFailure("redirect of the login page carried no $CHALLENGE_KEY")
    }

    override suspend fun exchangeCredentialsForCode(loginChallenge: String, credentials: Credentials): String {
        val form =
            listOf(
                "challenge" to loginChallenge,
                "client_id" to config.clientId,
                "famiglia_customer_code" to credentials.schoolCode,
                "login" to "true",
                "password" to credentials.password,
                "username" to credentials.username,
            ).joinToString("&") { (k, v) -> "$k=${urlEncode(v)}" }

        val response =
            try {
                engine.execute(
                    ArgoHttpRequest(
                        url = config.ssoLoginUrl,
                        method = it.hydr4.argo.api.HttpMethod.POST,
                        headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                        body = form,
                        contentType = "application/x-www-form-urlencoded",
                        // The reference client follows this hop: the SSO form 302s back to
                        // Hydra, which 302s again to the redirect URI carrying the code.
                        // OkHttp stops following at the custom-scheme URI and returns that
                        // final 3xx, whose Location holds the authorization code.
                        followRedirects = true,
                    ),
                )
            } catch (e: IOException) {
                throw NetworkException(e)
            }
        val status = response.statusCode
        if (status !in 300..399) {
            throw CredentialRejected("SSO endpoint answered HTTP $status instead of redirecting")
        }
        val location =
            response.header("location")
                ?: throw ProtocolFailure("SSO success response without Location header")
        return parseQuery(location, CODE_KEY)
            ?: throw CredentialRejected("SSO redirect did not contain an authorization code")
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private companion object {
        const val CHALLENGE_KEY = "login_challenge"
        const val CODE_KEY = "code"
    }
}

/**
 * Failure flavors confined to the SSO dance, surfaced as authentication errors so
 * the "every thrown error is an [ArgoException]" contract holds for callers.
 */
public sealed class SsoFailure(detail: String) : AuthenticationException(detail)

internal class ProtocolFailure(detail: String) : SsoFailure(detail)

internal class CredentialRejected(detail: String) : SsoFailure(detail)

private fun parseQuery(url: String, key: String): String? = runCatching {
    java.net
        .URI(url)
        .rawQuery
        ?.split('&')
        ?.mapNotNull { fragment ->
            fragment.split('=', limit = 2).takeIf { it.size == 2 && it[0] == key }?.get(1)
        }?.firstOrNull()
        ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
}.getOrNull()
