package it.hydr4.argo.repositories

import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.api.HttpMethod
import it.hydr4.argo.auth.ArgoSession
import it.hydr4.argo.models.Dashboard
import it.hydr4.argo.models.Profile
import it.hydr4.argo.sync.PollDecision
import it.hydr4.argo.time.TimeFormats
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Fetches and maintains the aggregated register snapshot, applying upstream
 * delta operations against the locally held copy exactly like the reference client.
 */
public class DashboardRepository(
    private val http: ArgoHttpClient,
    private val session: ArgoSession,
    private val profileLoader: suspend () -> Profile?,
) {
    /**
     * Performs a full dashboard round-trip merged onto [previous].
     *
     * @param previous Locally held snapshot for delta application; `null` forces
     *   a clean initial state even if upstream wouldn't request one.
     * @param sinceOverride Explicit synchronization anchor overriding derivation rules.
     */
    public suspend fun fetch(previous: Dashboard? = null, sinceOverride: Instant? = null): Dashboard {
        val wire = DashboardWire(http.json)
        val body = wire.buildRequestPayload(previous, sinceOverride, session, profileLoader())
        return wire.assemble(http.fetchEnvelope(Endpoints.DASHBOARD, body, HttpMethod.POST), previous)
    }

    /** Confirms the newest sync instant upstream (`dashboard/aggiornadata`). */
    public suspend fun acknowledgeSync(at: Instant = Instant.now()): Boolean = runCatching {
        http.fetchEnvelope(
            Endpoints.DASHBOARD_UPDATE_DATE,
            buildJsonObject {
                put("dataultimoaggiornamento", TimeFormats.formatWire(at))
            },
            HttpMethod.POST,
        )
    }.map { shell -> shell.asBooleanSuccess() }
        .getOrDefault(false)

    /**
     * Runs one change-probe round through [it.hydr4.argo.sync.WhatPoller] and returns its decision.
     *
     * Exposed here so callers orchestrate skip-vs-fetch without touching transports.
     */
    public suspend fun probeChanges(hasLocalDashboard: Boolean, poller: it.hydr4.argo.sync.WhatPoller, lastSync: Instant): PollDecision =
        poller.probe(lastSync, hasLocalDashboard)
}
