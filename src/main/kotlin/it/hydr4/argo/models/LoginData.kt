package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result of the authenticated-family `login` call.
 *
 * The endpoint returns a single-element array wrapped in the standard envelope;
 * the repository layer unwraps it before handing this model to callers.
 *
 * @property codMin Ministerial school code (e.g. `SS13325`), required by `x-cod-min`.
 * @property xAuthToken Session token echoed as `x-auth-token`. *Security:* redacted
 *   in [toString].
 * @property options Dashboard feature flags echoed back on dashboard requests.
 * @property isFirstAccess `true` on the account's very first login.
 * @property isProfileDisabled `true` when the school disabled this profile.
 * @property isPasswordResetPending `true` when a password reset is pending.
 * @property isSpid `true` when the account authenticates through SPID.
 * @property username Account username as known server-side.
 */
@Serializable
public data class LoginData(
    @SerialName("codMin") public val codMin: String,
    @SerialName("token") public val xAuthToken: String,
    @SerialName("opzioni") public val options: List<LoginOption> = emptyList(),
    @SerialName("isPrimoAccesso") public val isFirstAccess: Boolean = false,
    @SerialName("profiloDisabilitato") public val isProfileDisabled: Boolean = false,
    @SerialName("isResetPassword") public val isPasswordResetPending: Boolean = false,
    @SerialName("isSpid") public val isSpid: Boolean = false,
    @SerialName("username") public val username: String? = null,
) {
    override fun toString(): String = "LoginData(codMin=$codMin, xAuthToken=●, username=$username)"
}

/**
 * A single dashboard feature flag.
 *
 * @property key Wire key (e.g. `opz-vot_annuale`).
 * @property value Flag state.
 */
@Serializable
public data class LoginOption(@SerialName("chiave") public val key: String, @SerialName("valore") public val value: Boolean)
