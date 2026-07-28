package it.hydr4.argo.models

import it.hydr4.argo.models.ModelTimeSerializers.IsoInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * OAuth2 bearer token issued by `https://auth.portaleargo.it/oauth2/token`,
 * either through the PKCE code exchange or the `auth/refresh-token` endpoint.
 *
 * `expires_in` (a relative duration on the wire) is normalized into [expiresAt]
 * at receipt time using the server `Date` header, so callers compare absolute
 * instants instead of bookkeeping clocks.
 *
 * **Security:** [toString] is overridden to a redacted form so accidental
 * interpolation into logs can never leak [accessToken] or [refreshToken].
 *
 * @property accessToken Bearer credential sent via the `authorization` header.
 * @property refreshToken Credential for the `auth/refresh-token` endpoint.
 * @property expiresAt Absolute expiry instant derived from `expires_in`.
 * @property scope Space-separated granted scopes (e.g. `openid offline profile`).
 * @property tokenType Wire token type; Argo issues `Bearer`.
 * @property idToken OpenID Connect identity token; opaque to this client.
 */
@Serializable
public data class Token(
    @SerialName("access_token") public val accessToken: String,
    @SerialName("refresh_token") public val refreshToken: String,
    @Serializable(with = IsoInstant::class)
    public val expiresAt: Instant,
    public val scope: String,
    @SerialName("token_type") public val tokenType: String,
    @SerialName("id_token") public val idToken: String? = null,
) {
    /** `true` when the token is expired or expires within [slackSeconds]. */
    public fun isExpiredOrExpiringWithin(slackSeconds: Long, now: Instant = Instant.now()): Boolean =
        !expiresAt.isAfter(now.plusSeconds(slackSeconds))

    override fun toString(): String = "Token(accessToken=●, refreshToken=●, expiresAt=$expiresAt, scope=$scope)"
}
