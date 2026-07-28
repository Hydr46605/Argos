package it.hydr4.argo.models

import kotlinx.serialization.Serializable

/**
 * Standard Argo response envelope: `{"success": bool, "msg": string?, "data": T}`.
 *
 * Repositories translate `success == false` into
 * [it.hydr4.argo.exceptions.ArgoApiException] using [message].
 *
 * @property success Whether the logical operation succeeded (HTTP may still be 200).
 * @property message Human-readable failure message; present only on failures.
 * @property data Payload; shape depends on the endpoint.
 */
@Serializable
public data class ArgoEnvelope<T>(public val success: Boolean, public val message: String? = null, public val data: T? = null)

/**
 * Envelope variant returned by the `login` endpoint, which adds a `total` count.
 *
 * @property total Number of returned profile entries; observed to be `1` for family accounts.
 */
@Serializable
public data class LoginEnvelope<T>(
    public val success: Boolean,
    public val message: String? = null,
    public val total: Int? = null,
    public val data: List<T> = emptyList(),
)
