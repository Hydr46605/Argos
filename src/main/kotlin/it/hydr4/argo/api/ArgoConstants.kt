package it.hydr4.argo.api

/**
 * Immutable wire constants reverse-engineered from the reference
 * `portaleargo-api` TypeScript implementation.
 *
 * `clientId` is a fixed embedded identifier used by the official DidUp app;
 * it is not a secret and is required by both OAuth endpoints and login payloads.
 */
public object ArgoConstants {
    /** Fixed app client-id accepted by the Argo Hydra OAuth2 server. */
    public const val CLIENT_ID: String = "72fd6dea-d0ab-4bb9-8eaa-3ac24c84886c"

    /** DidUp app version echoed through `argo-client-version`. */
    public const val DIDUP_VERSION: String = "1.27.0"

    /** Default PKCE redirect target registered for the Android family app. */
    public const val REDIRECT_URI: String = "it.argosoft.didup.famiglia.new://login-callback"

    /** Scopes requested on the authorize redirect. */
    public val SCOPES: List<String> = listOf("openid", "offline", "profile", "user.roles", "argo")

    /** Proc tag sent inside refresh/log-token bodies. */
    public const val PROC_TAG: String = "initState_global_random_12345"

    /** Slack applied around token expiry before an automatic refresh fires. */
    public const val REFRESH_SLACK_SECONDS: Long = 120

    /** Base URL of all REST calls (`BaseClient.BASE_URL + /appfamiglia/api/rest`). */
    public const val REST_BASE_URL: String = "https://www.portaleargo.it/appfamiglia/api/rest"

    /** Credential form-post endpoint of the SSO dance. */
    public const val SSO_LOGIN_URL: String = "https://www.portaleargo.it/auth/sso/login"

    /** OAuth2 authorize endpoint. */
    public const val OAUTH_AUTHORIZE_URL: String = "https://auth.portaleargo.it/oauth2/auth"

    /** OAuth2 token exchange endpoint. */
    public const val OAUTH_TOKEN_URL: String = "https://auth.portaleargo.it/oauth2/token"
}
