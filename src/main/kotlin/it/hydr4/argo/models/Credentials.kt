package it.hydr4.argo.models

/**
 * Login credentials for the ScuolaNext family register.
 *
 * **Security:** [toString] is overridden to a redacted form so accidental
 * interpolation into logs can never leak the password.
 */
public data class Credentials(public val schoolCode: String, public val username: String, public val password: String) {
    override fun toString(): String = "Credentials(schoolCode=$schoolCode, username=$username, password=●)"
}
