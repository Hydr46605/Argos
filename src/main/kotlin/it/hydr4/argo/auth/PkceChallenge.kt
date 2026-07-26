package it.hydr4.argo.auth

/**
 * Immutable PKCE pair carried through the whole flow.
 *
 * **Security:** the verifier is only ever rendered redacted; losing it before
 * exchange aborts authentication rather than retrying with a stale pair.
 *
 * @property codeVerifier Raw verifier sent on token exchange.
 * @property codeChallenge Derived S256 challenge embedded into the authorize URL.
 */
public data class PkceChallenge(public val codeVerifier: String, public val codeChallenge: String) {
    override fun toString(): String = "PkceChallenge(codeVerifier=●, codeChallenge=$codeChallenge)"
}
