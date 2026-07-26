package it.hydr4.argo.api

import it.hydr4.argo.util.RetryPolicy

/**
 * Immutable connection settings. All endpoints default to the production
 * deployment observed in the reference implementation; overriding them enables
 * pointing Argos at staging environments during reverse-engineering sessions.
 */
public data class ArgoClientConfig(
    /** REST root; every [Endpoints] path hangs off it. */
    public val restBaseUrl: String = ArgoConstants.REST_BASE_URL,
    /** Credential form-post endpoint of the SSO dance. */
    public val ssoLoginUrl: String = ArgoConstants.SSO_LOGIN_URL,
    /** OAuth2 authorize endpoint serving the PKCE login page. */
    public val oauthAuthorizeUrl: String = ArgoConstants.OAUTH_AUTHORIZE_URL,
    /** OAuth2 token exchange endpoint. */
    public val oauthTokenUrl: String = ArgoConstants.OAUTH_TOKEN_URL,
    /** App version echoed through `argo-client-version`. */
    public val didUpVersion: String = ArgoConstants.DIDUP_VERSION,
    /** Fixed embedded app client-id. */
    public val clientId: String = ArgoConstants.CLIENT_ID,
    /**
     * Transient-failure retry policy applied to every authenticated repository
     * call: network failures and server 5xx are retried with exponential backoff,
     * application rejections and auth failures propagate immediately. Tune
     * [RetryPolicy.maxAttempts] / [RetryPolicy.baseDelayMillis] / [RetryPolicy.maxDelayMillis]
     * here; the predicate defaults to [TRANSIENT_RETRYABLE].
     */
    public val retryPolicy: RetryPolicy = RetryPolicy(retryOn = TRANSIENT_RETRYABLE),
)
