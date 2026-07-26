package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoConstants
import it.hydr4.argo.api.ArgoHttpEngine
import it.hydr4.argo.api.ArgoHttpRequest
import it.hydr4.argo.api.HttpMethod
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.exceptions.DeserializationException
import it.hydr4.argo.exceptions.NetworkException
import it.hydr4.argo.models.Token
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.IOException

/**
 * Exchanges an authorization code for the bearer [Token] via `oauth2/token`.
 *
 * The wire responds either with token fields (relative `expires_in`) or with an
 * `{error, error_description}` body; the latter maps to [AuthenticationException]
 * without echoing unknown payload text. The response `Date` header anchors
 * expiry so client clock skew never shifts validity windows.
 *
 * @property engine Plain engine; no cookie jar required for this hop.
 */
public class OAuthTokenExchanger(private val engine: ArgoHttpEngine, private val config: ArgoClientConfig = ArgoClientConfig()) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Performs the exchange.
     *
     * @param code Authorization code captured from the SSO redirect.
     * @param verifier PKCE verifier created when building the login link — must match the challenge.
     * @param redirectUri Redirect used on the authorize leg; must be byte-identical.
     * @throws AuthenticationException on protocol rejection (`invalid_grant` and friends).
     */
    public suspend fun exchange(code: String, verifier: String, redirectUri: String = ArgoConstants.REDIRECT_URI): Token {
        val form =
            listOf(
                "code" to code,
                "grant_type" to "authorization_code",
                "redirect_uri" to redirectUri,
                "code_verifier" to verifier,
                "client_id" to config.clientId,
            ).joinToString("&") { (k, v) -> "$k=${urlEncode(v)}" }

        val response =
            try {
                engine.execute(
                    ArgoHttpRequest(
                        url = config.oauthTokenUrl,
                        method = HttpMethod.POST,
                        headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                        body = form,
                        contentType = "application/x-www-form-urlencoded",
                    ),
                )
            } catch (e: IOException) {
                throw NetworkException(e)
            }
        if (response.statusCode !in 200..299) {
            throw AuthenticationException("Token exchange failed with HTTP ${response.statusCode}")
        }
        val payload: JsonObject =
            try {
                json.parseToJsonElement(response.body).jsonObject
            } catch (e: IllegalArgumentException) {
                throw DeserializationException("oauth2/token", e)
            }
        val rawError = payload["error"] as? JsonPrimitive
        if (!rawError?.content.isNullOrBlank()) {
            // Deliberately excludes error_description content: upstream may echo PII there.
            throw AuthenticationException("Token exchange rejected: ${rawError.content}")
        }
        return try {
            val wire = json.decodeFromJsonElement(TokenWire.serializer(), payload)
            wire.toModel(ServerInstant.fromHeader(response.header("date")))
        } catch (e: SerializationException) {
            throw DeserializationException("oauth2/token", e)
        }
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)
}

/** Wire shape of the token endpoint before normalization onto [it.hydr4.argo.models.Token]. */
@Serializable
internal data class TokenWire(
    @kotlinx.serialization.SerialName("access_token") val accessToken: String,
    @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String,
    @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long,
    @kotlinx.serialization.SerialName("scope") val scope: String,
    @kotlinx.serialization.SerialName("token_type") val tokenType: String,
    @kotlinx.serialization.SerialName("id_token") val idToken: String? = null,
)

internal fun TokenWire.toModel(serverNow: java.time.Instant): it.hydr4.argo.models.Token = it.hydr4.argo.models.Token(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = serverNow.plusSeconds(expiresIn),
    scope = scope,
    tokenType = tokenType,
    idToken = idToken,
)
