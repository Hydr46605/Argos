package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cheap change-probe answer from `dashboard/what`.
 *
 * Drives [it.hydr4.argo.sync.WhatPoller]: the poller skips the expensive
 * `dashboard/dashboard` call when nothing changed.
 *
 * @property isModified `true` when register data changed since last sync.
 * @property showBadge `true` when the UI badge should be displayed (drives full fetch).
 * @property differenzaSchede `true` when enrollment scheda changed — profile refresh needed.
 * @property forceLogin `true` when the session token was rejected; caller must re-login.
 * @property student Updated student identity when provided by the probe.
 */
@Serializable
public data class WhatResult(
    @SerialName("isModificato") public val isModified: Boolean = false,
    @SerialName("mostraPallino") public val showBadge: Boolean = false,
    @SerialName("differenzaSchede") public val differenzaSchede: Boolean = false,
    @SerialName("forceLogin") public val forceLogin: Boolean = false,
    @SerialName("pk") public val pk: String? = null,
    @SerialName("alunno") public val student: StudentIdentity? = null,
)
