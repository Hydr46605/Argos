package it.hydr4.argo.auth

import it.hydr4.argo.exceptions.ArgoException
import it.hydr4.argo.models.LoginData
import it.hydr4.argo.models.Profile
import it.hydr4.argo.models.Token

/**
 * Explicit authentication lifecycle.
 *
 * Happy path: `Idle` → `ChallengeRequested` → `CodeExchanged` → `Authenticated`
 * → (`TokenRefreshed` while staying authenticated).
 * Every terminal failure collapses into [Failed] carrying the typed cause.
 */
public sealed class AuthState {
    /** Nothing attempted yet or logout completed. */
    public object Idle : AuthState()

    /**
     * Authorize URL was generated and the Hydra challenge resolved.
     *
     * @property start Materials needed to open the browser (or run headless SSO).
     */
    public data class ChallengeRequested(public val start: AuthStart) : AuthState()

    /**
     * Authorization code exchanged for a bearer token pair.
     */
    public data class CodeExchanged(public val token: Token) : AuthState()

    /**
     * Fully logged into the family register: session token, codMin and profile available.
     */
    public data class Authenticated(public val token: Token, public val loginData: LoginData?, public val profile: Profile?) : AuthState()

    /**
     * Bearer rotated through `auth/refresh-token`; logical session unchanged.
     */
    public data class TokenRefreshed(public val token: Token) : AuthState()

    /**
     * The flow stopped because of [failure]; no partial state is ever exposed.
     */
    public data class Failed(public val fromStep: String, public val failure: ArgoException) : AuthState()
}
