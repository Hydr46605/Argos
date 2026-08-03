package it.hydr4.argo.sync

/**
 * Outcome of a change-probe round, interpreted through the reference client's
 * login-loop rules (`mostraPallino`, `differenzaSchede`, `forceLogin`).
 */
public sealed class PollDecision {
    /** Nothing changed upstream; skip the full dashboard fetch this round. */
    public object Clean : PollDecision()

    /**
     * Register data changed; fetch `dashboard/dashboard` now.
     *
     * @property badgeRequested Server additionally asked for a UI badge pulse.
     */
    public data class FetchDashboard(public val badgeRequested: Boolean) : PollDecision()

    /**
     * Enrollment scheda drifted (school switch/year rollover).
     *
     * Callers should refetch the profile before trusting repository defaults.
     */
    public object SchedaChanged : PollDecision()

    /**
     * Upstream rejected the session token (`forceLogin`); re-authentication
     * through the credential flow is required before further data calls.
     */
    public object SessionInvalid : PollDecision()
}
