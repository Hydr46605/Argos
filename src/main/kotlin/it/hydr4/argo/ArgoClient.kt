package it.hydr4.argo

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.ArgoHttpEngine
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.api.InMemoryCookieJar
import it.hydr4.argo.api.OkHttpEngine
import it.hydr4.argo.auth.ArgoSession
import it.hydr4.argo.auth.CachedTokenRepository
import it.hydr4.argo.auth.TokenRepository
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.exceptions.ProtocolException
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.models.Dashboard
import it.hydr4.argo.registry.EndpointRegistry
import it.hydr4.argo.repositories.BulletinRepository
import it.hydr4.argo.repositories.CurriculumRepository
import it.hydr4.argo.repositories.DashboardRepository
import it.hydr4.argo.repositories.FeesRepository
import it.hydr4.argo.repositories.MeetingsRepository
import it.hydr4.argo.repositories.PctoRepository
import it.hydr4.argo.repositories.ProfileRepository
import it.hydr4.argo.repositories.RecoveryCoursesRepository
import it.hydr4.argo.repositories.ScheduleRepository
import it.hydr4.argo.repositories.ScrutinioRepository
import it.hydr4.argo.storage.AesGcmFileStore
import it.hydr4.argo.storage.InMemoryTokenStore
import it.hydr4.argo.storage.TokenStore
import it.hydr4.argo.sync.PollDecision
import it.hydr4.argo.sync.WhatPoller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.CookieJar
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

/**
 * Composition root of Argos.
 *
 * Owns no protocol logic; it wires the engine, session state machine, transport
 * and repositories together and exposes small-domain accessors in a stable order.
 *
 * @property config Connection defaults/staging overrides.
 */
public class ArgoClient private constructor(
    public val config: ArgoClientConfig,
    private val engine: ArgoHttpEngine,
    internal val session: ArgoSession,
    public val http: ArgoHttpClient,
    public val profiles: ProfileRepository,
    public val dashboards: DashboardRepository,
) {
    /** Daily timetable repository. */
    public val schedule: ScheduleRepository by lazy { ScheduleRepository(http) }

    /** Scrutinio votes repository. */
    public val scrutinio: ScrutinioRepository by lazy { ScrutinioRepository(http) }

    /** Bulletin history/attachments. */
    public val bulletins: BulletinRepository = BulletinRepository(http)

    /** Fees and receipts. */
    public val fees: FeesRepository = FeesRepository(http)

    /** Parent-teacher meetings. */
    public val meetings: MeetingsRepository = MeetingsRepository(http)

    /** Internship pathways. */
    public val internships: PctoRepository = PctoRepository(http)

    /** Recovery courses. */
    public val recoveryCourses: RecoveryCoursesRepository = RecoveryCoursesRepository(http)

    /** Curriculum years. */
    public val curriculum: CurriculumRepository = CurriculumRepository(http)

    private val poller = WhatPoller(http, session)

    /**
     * Custom-endpoint registration bound to this client's transport and session.
     *
     * Annotation-driven registration ([it.hydr4.argo.annotations.ArgoEndpoint])
     * inherits the client's header handling, envelope decoding and the typed
     * exception tree without touching built-in repositories.
     */
    public val registry: EndpointRegistry = EndpointRegistry(http) { session.isAuthenticated() }

    /**
     * Runs the whole credential flow and returns the first fetched [Dashboard].
     *
     * @throws it.hydr4.argo.exceptions.ArgoException typed per failing stage.
     */
    public suspend fun login(credentials: Credentials): Dashboard {
        session.loginWithCredentials(credentials)
        profiles.profilo()
        return synchronize(forceRefresh = true).first
            ?: throw ProtocolException("fresh login produced no dashboard snapshot")
    }

    /**
     * Restores an encrypted persisted session without network I/O.
     *
     * @return Whether usable login material was found on disk.
     */
    public suspend fun restorePersistedSession(): Boolean = session.restorePersistedSession()

    /**
     * Change-probe driven synchronization round.
     *
     * - `Clean` short-circuits and reuses the last known snapshot;
     * - scheda drift triggers a profile refetch before the dashboard fetch;
     * - otherwise performs the delta fetch directly.
     *
     * @param forceRefresh Skips the probe entirely when `true`.
     */
    public suspend fun synchronize(forceRefresh: Boolean = false, previous: Dashboard? = null): Pair<Dashboard?, PollDecision> {
        val cached = previous ?: lastDashboard
        if (!forceRefresh && cached?.fetchedAt != null) {
            val decision = poller.probe(cached.fetchedAt, hasLocalDashboard = true)
            when (decision) {
                is PollDecision.Clean -> return cached to decision
                is PollDecision.SchedaChanged -> profiles.profilo()
                is PollDecision.SessionInvalid ->
                    throw AuthenticationException("Session token rejected upstream; re-authentication required")
                is PollDecision.FetchDashboard -> Unit
            }
        }
        val merged = dashboards.fetch(previous = cached)
        lastDashboard = merged
        dashboards.acknowledgeSync()
        return merged to PollDecision.FetchDashboard(badgeRequested = false)
    }

    /** Reusable shorthand mirroring the milestone goal: current dashboard snapshot. */
    public suspend fun dashboard(): Dashboard? = lastDashboard

    /**
     * Emits a fresh [Dashboard] whenever a synchronization round finds upstream
     * changes, skipping clean rounds and snapshots identical to the previous one.
     *
     * Polling is cold and lazy: nothing happens until collected, and the loop
     * pauses when the collector stops. Transient [it.hydr4.argo.exceptions.ArgoException]s
     * are reported through [onError] and the flow keeps polling; authentication
     * failures ([it.hydr4.argo.exceptions.AuthenticationException]) terminate the
     * flow so callers can re-login instead of hammering a dead session.
     *
     * Backoff: healthy rounds poll at [interval]; failing rounds double the gap
     * (plus uniform [jitter], so many clients never synchronize as a herd) up to
     * [maxBackoff]. One healthy round restores the base schedule, so a sick
     * server is probed increasingly rarely while recovery is detected promptly.
     *
     * @param interval Base delay between probe rounds.
     * @param onError Sink for non-fatal errors; defaults to ignoring them.
     * @param maxBackoff Upper bound for the delay after consecutive failures.
     * @param jitter Random extra delay in `[0, jitter]` added to failure backoffs.
     * @throws IllegalArgumentException when [maxBackoff] is smaller than [interval].
     */
    public fun dashboardChanges(
        interval: Duration = Duration.ofMinutes(5),
        onError: (Throwable) -> Unit = {},
        maxBackoff: Duration = Duration.ofMinutes(30),
        jitter: Duration = Duration.ofSeconds(30),
    ): Flow<Dashboard> {
        require(maxBackoff >= interval) { "maxBackoff ($maxBackoff) must be >= interval ($interval)" }
        require(jitter >= Duration.ZERO) { "jitter must be non-negative, was $jitter" }
        return flow {
            // Baseline against the current snapshot so a clean first round after
            // subscription does not produce a spurious "change" notification.
            var lastEmitted = lastDashboard
            var backoff = interval
            while (true) {
                try {
                    val (snapshot, _) = synchronize()
                    if (snapshot != null && snapshot != lastEmitted) {
                        lastEmitted = snapshot
                        emit(snapshot)
                    }
                    backoff = interval // healthy round restores the base schedule
                } catch (e: AuthenticationException) {
                    throw e // re-login required; the collector decides how
                } catch (e: it.hydr4.argo.exceptions.ArgoException) {
                    onError(e)
                    backoff = minOf(backoff.multipliedBy(2), maxBackoff)
                }
                delay(backoff.toMillis() + jitterMillis(jitter))
            }
        }
    }

    private fun jitterMillis(jitter: Duration): Long = if (jitter.isZero) 0L else kotlin.random.Random.nextLong(0, jitter.toMillis() + 1)

    @Volatile
    private var lastDashboard: Dashboard? = null

    /**
     * Performs server-side profile removal then wipes local material.
     *
     * Best-effort upstream call: local wipe happens even when the endpoint fails.
     */
    @Suppress("SwallowedException")
    // The upstream removal is best-effort by contract: failures are logged away
    // deliberately so the local wipe always runs; cancellation still propagates.
    public suspend fun logout() {
        try {
            http.fetchEnvelope(Endpoints.RIMUOVI_PROFILO, body = kotlinx.serialization.json.buildJsonObject { })
        } catch (e: CancellationException) {
            throw e // never swallow cancellation, not even on a best-effort path
        } catch (e: it.hydr4.argo.exceptions.ArgoException) {
            // Best-effort upstream call: the local wipe below happens regardless.
        }
        session.clearLocally()
        lastDashboard = null
    }

    /** Releases OkHttp dispatcher threads and connection pool. */
    public fun close() {
        (engine as? OkHttpEngine)?.close()
    }

    public companion object {
        /**
         * Creates a client wired for production endpoints.
         *
         * @param storage Optional durable encrypted store; omit for memory-only sessions.
         */
        public fun create(
            config: ArgoClientConfig = ArgoClientConfig(),
            storage: TokenStore? = defaultStorage(),
            // The SSO dance binds the login session to cookies across redirect hops,
            // so the default engine needs a real in-memory jar, not NO_COOKIES.
            cookieJar: CookieJar = InMemoryCookieJar(),
        ): ArgoClient = create(config, OkHttpEngine(cookieJar), storage ?: InMemoryTokenStore())

        /**
         * Creates a client over a caller-provided engine — staging/proxy transports,
         * custom TLS setups and deterministic test fakes all plug in here.
         *
         * @param clock Injectable wall clock used for expiry decisions; tests pin it.
         */
        public fun create(
            config: ArgoClientConfig = ArgoClientConfig(),
            engine: ArgoHttpEngine,
            storage: TokenStore = InMemoryTokenStore(),
            clock: Clock = Clock.systemUTC(),
        ): ArgoClient {
            val tokens: TokenRepository = CachedTokenRepository(storage)
            val session = ArgoSession(engine = engine, tokens = tokens, store = storage, config = config, clock = clock)
            val http = ArgoHttpClient(engine, config, session)
            val profiles = ProfileRepository(http, session)
            val dashboards = DashboardRepository(http, session) { profiles.currentOrNull() }
            return ArgoClient(config, engine, session, http, profiles, dashboards)
        }

        /** Encrypted store under `$HOME/.argos/tokens.bin` when writable, else memory-only. */
        private fun defaultStorage(): TokenStore? = runCatching {
            val dir = Path.of(System.getProperty("user.home"), AesGcmFileStore.DEFAULT_DIRECTORY_NAME)
            val passphrase = storagePassphrase()
            AesGcmFileStore(
                dir.resolve("tokens.bin"),
                it.hydr4.argo.storage
                    .AesGcmCredentialCipher(passphrase),
            )
        }.getOrNull()

        private fun storagePassphrase(): CharArray {
            System.getProperty("argos.store.passphrase")?.let { return it.toCharArray() }
            // Machine-derived fallback keeps confidentiality against casual copying only.
            val seed =
                listOf(
                    System.getProperty("user.name"),
                    System.getProperty("user.home"),
                    System.getenv("COMPUTERNAME") ?: "argos",
                ).joinToString("|")
            return ("argos-local::" + Integer.toHexString(seed.hashCode())).toCharArray()
        }
    }
}
