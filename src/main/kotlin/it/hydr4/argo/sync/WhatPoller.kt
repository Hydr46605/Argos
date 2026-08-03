package it.hydr4.argo.sync

import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.auth.ArgoSession
import it.hydr4.argo.models.WhatResult
import it.hydr4.argo.time.TimeFormats
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Cheap change-probe ahead of expensive `dashboard/dashboard` fetches.
 *
 * Round-trips `dashboard/what` with the exact payload of the reference client:
 * last-sync timestamp, stringified options map and the JSON-encoded session token list.
 */
public class WhatPoller(private val http: ArgoHttpClient, private val session: ArgoSession) {
    /**
     * Runs one probe round.
     *
     * @param lastSync Timestamp carried to upstream (`dataultimoaggiornamento`).
     * @param hasLocalDashboard Whether a cached dashboard exists on the caller side.
     * @throws it.hydr4.argo.exceptions.ArgoException typed failures from transport.
     */
    public suspend fun probe(lastSync: Instant, hasLocalDashboard: Boolean): PollDecision {
        val what =
            http.fetch(
                path = Endpoints.DASHBOARD_WHAT,
                body = buildPayload(lastSync),
                dataSerializer = WhatResult.serializer(),
            )
        return interpret(what, hasLocalDashboard)
    }

    /**
     * Pure interpretation of a probe answer — extracted so policy stays unit-testable.
     *
     * Priority (mirroring reference semantics):
     * 1. `forceLogin` → the session token was rejected; callers must re-authenticate.
     * 2. scheda difference → profile refresh first.
     * 3. modification or badge with no dashboard → fetch.
     */
    public fun interpret(what: WhatResult, hasLocalDashboard: Boolean): PollDecision = when {
        what.forceLogin -> PollDecision.SessionInvalid
        what.differenzaSchede -> PollDecision.SchedaChanged
        what.isModified -> PollDecision.FetchDashboard(badgeRequested = what.showBadge)
        what.showBadge && !hasLocalDashboard -> PollDecision.FetchDashboard(badgeRequested = true)
        else -> PollDecision.Clean
    }

    private suspend fun buildPayload(lastSync: Instant) = buildJsonObject {
        put("dataultimoaggiornamento", TimeFormats.formatWire(lastSync))
        val login = session.loginDataOrNull()
        // Options travel as a pre-stringified map; true/false lowercase per JSON.stringify.
        val optionString =
            login?.options.orEmpty().joinToString(",", "{", "}") { opt ->
                "${JsonPrimitive(opt.key)}:${opt.value}"
            }
        put("opzioni", optionString)
        val authTokenList =
            login?.xAuthToken?.let { token ->
                "[${JsonPrimitive(token)}]"
            } ?: "[]"
        put("lista-x-auth-token", authTokenList)
        put("lista-x-auth-token-account", authTokenList)
    }
}
