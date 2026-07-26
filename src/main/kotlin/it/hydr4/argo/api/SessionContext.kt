package it.hydr4.argo.api

import java.time.Instant

/**
 * Header snapshot describing the current session state.
 *
 * @property bearer OAuth access token for the `authorization` header.
 * @property xAuthToken Session token for `x-auth-token`; absent right after OAuth exchange.
 * @property codMin School code for `x-cod-min`; absent until login completes.
 * @property tokenExpiresAt Expiry instant for `x-date-exp-auth`; present once a token exists.
 */
public data class AuthHeaders(
    public val bearer: String?,
    public val xAuthToken: String?,
    public val codMin: String?,
    public val tokenExpiresAt: Instant?,
) {
    /** Security: bearer and session token are redacted in rendering. */
    override fun toString(): String =
        "AuthHeaders(bearer=${bearer.redacted()}, xAuthToken=${xAuthToken.redacted()}, codMin=$codMin, tokenExpiresAt=$tokenExpiresAt)"
}

/**
 * Bridge between the transport layer and the authentication state machine.
 *
 * Implemented by the auth session; consumed by [ArgoHttpClient] before every call.
 *
 * Token transparency contract: [ensureFreshBearer] must perform the refresh
 * internally (via `auth/refresh-token`) so callers never orchestrate tokens themselves.
 */
public interface SessionContext {
    /**
     * Guarantees a fresh bearer and returns the header snapshot afterwards.
     *
     * Refresh triggers when the stored token is expired or inside the slack window.
     * Implementations may throw [it.hydr4.argo.exceptions.AuthenticationException]
     * when refresh fails and re-authentication is required.
     */
    public suspend fun headersWithFreshBearer(): AuthHeaders

    /**
     * Best-effort snapshot without triggering refreshes (used by callers that
     * intentionally bypass freshness, e.g. the refresh call itself).
     */
    public suspend fun currentHeaders(): AuthHeaders?

    /**
     * Rotates the bearer immediately and returns the fresh header snapshot.
     *
     * Invoked by the transport when the server rejected an already-sent request
     * as unauthorized (HTTP 401): the local expiry check passed but the server
     * disagrees, so a real rotation must happen — re-serving the current token
     * would loop. Implementations must not fall back to the existing bearer.
     *
     * @throws it.hydr4.argo.exceptions.AuthenticationException when rotation
     *   fails terminally (the grant is dead; local material gets wiped).
     */
    public suspend fun forceRefresh(): AuthHeaders
}

/** Snapshot used before any authentication happened. */
public val UNAUTHENTICATED_HEADERS: AuthHeaders =
    AuthHeaders(bearer = null, xAuthToken = null, codMin = null, tokenExpiresAt = null)

private fun String?.redacted(): String = if (this != null) "●" else "null"
