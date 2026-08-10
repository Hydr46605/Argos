package it.hydr4.argo.sync

import io.mockk.mockk
import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.auth.ArgoSession
import it.hydr4.argo.models.WhatResult
import kotlin.test.Test
import kotlin.test.assertEquals

/** Decision matrix of the dashboard change probe. */
class WhatPollerTest {
    // `interpret` is a pure policy function; transport collaborators are never touched.
    private val poller = WhatPoller(mockk<ArgoHttpClient>(relaxed = true), mockk<ArgoSession>(relaxed = true))

    @Test
    fun `no changes and no badge is Clean`() {
        val decision = poller.interpret(WhatResult(isModified = false, showBadge = false), hasLocalDashboard = true)
        assertEquals(PollDecision.Clean, decision)
    }

    @Test
    fun `modification always triggers a fetch carrying the badge flag`() {
        val decision = poller.interpret(WhatResult(isModified = true, showBadge = true), hasLocalDashboard = true)
        assertEquals(PollDecision.FetchDashboard(badgeRequested = true), decision)
    }

    @Test
    fun `badge without local dashboard forces the first fetch`() {
        val decision = poller.interpret(WhatResult(isModified = false, showBadge = true), hasLocalDashboard = false)
        assertEquals(PollDecision.FetchDashboard(badgeRequested = true), decision)
    }

    @Test
    fun `badge with an existing dashboard stays clean`() {
        val decision = poller.interpret(WhatResult(isModified = false, showBadge = true), hasLocalDashboard = true)
        assertEquals(PollDecision.Clean, decision)
    }

    @Test
    fun `scheda drift takes priority over plain modification`() {
        val decision = poller.interpret(WhatResult(isModified = true, showBadge = true, differenzaSchede = true), hasLocalDashboard = true)
        assertEquals(PollDecision.SchedaChanged, decision)
    }

    @Test
    fun `forceLogin takes priority over every other signal`() {
        val decision = poller.interpret(
            WhatResult(isModified = true, showBadge = true, differenzaSchede = true, forceLogin = true),
            hasLocalDashboard = true,
        )
        assertEquals(PollDecision.SessionInvalid, decision)
    }

    @Test
    fun `forceLogin alone is never Clean`() {
        val decision = poller.interpret(WhatResult(forceLogin = true), hasLocalDashboard = true)
        assertEquals(PollDecision.SessionInvalid, decision)
    }
}
