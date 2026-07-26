package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoConstants
import it.hydr4.argo.api.ArgoHttpEngine
import it.hydr4.argo.api.ArgoHttpRequest
import it.hydr4.argo.api.ArgoHttpResponse
import it.hydr4.argo.api.AuthHeaders
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.api.HttpMethod
import it.hydr4.argo.api.Nonces
import it.hydr4.argo.api.SessionContext
import it.hydr4.argo.api.UNAUTHENTICATED_HEADERS
import it.hydr4.argo.exceptions.ArgoApiException
import it.hydr4.argo.exceptions.ArgoException
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.exceptions.DeserializationException
import it.hydr4.argo.exceptions.NetworkException
import it.hydr4.argo.exceptions.RefreshRejectedException
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.models.LoginData
import it.hydr4.argo.models.Profile
import it.hydr4.argo.models.Token
import it.hydr4.argo.storage.SessionSnapshot
import it.hydr4.argo.storage.TokenStore
import it.hydr4.argo.time.TimeFormats
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Clock

/**
 * Owns everything about *being logged in*: the PKCE dance, family-login exchange,
 * transparent bearer refresh and durable restore/logout.
 *
 * The transport layer consumes this through [SessionContext]; user-facing flows
 * consume the higher-level facade which additionally drives repositories for data.
 *
 * Failure contract: every thrown error is an [ArgoException]; [authState] always
 * reflects the last transition reached before the throw.
 *
 * This is the session state machine: every public member is a lifecycle entry
 * point or readout of that machine, not an unrelated concern, so the function
 * count is cohesive rather than a sign of a god class.
 */
@Suppress("TooManyFunctions")
public class ArgoSession(
    private val engine: ArgoHttpEngine,
    private val tokens: TokenRepository,
    private val store: TokenStore,
    private val config: ArgoClientConfig = ArgoClientConfig(),
    internal val clock: Clock = Clock.systemUTC(),
    private val pkce: PkceGenerator = PkceGenerator(),
) : SessionContext {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedLogin: LoginData? = null

    @Volatile
    private var cachedProfile: Profile? = null

    /** Last transition of the lifecycle state machine (observability/testing hook). */
    @Volatile
    public var authState: AuthState = AuthState.Idle
        private set

    /**
     * Runs the complete headless credential flow:
     * authorize-link → Hydra challenge → SSO credentials → code exchange → family login.
     *
     * @return The resulting [LoginData] containing codMin/session options.
     * @throws ArgoException typed per failure stage; state collapses to [AuthState.Failed].
     */
    public suspend fun loginWithCredentials(credentials: Credentials): LoginData = step("pkce-challenge") {
        val start = LoginLinkBuilder(config, pkce).build()
        authState = AuthState.ChallengeRequested(start)
        val broker = DefaultSsoCodeBroker(engine, config)

        step("sso-credentials") {
            val hydraChallenge = broker.fetchChallenge(start.authorizeUrl)
            val code = broker.exchangeCredentialsForCode(hydraChallenge, credentials)

            step("code-exchange") {
                val token =
                    OAuthTokenExchanger(engine, config)
                        .exchange(code, start.challenge.codeVerifier, start.redirectUri)
                tokens.update(token)
                authState = AuthState.CodeExchanged(token)

                step("family-login") {
                    val loginData = postFamilyLogin()
                    cachedLogin = loginData
                    store.save(SessionSnapshot(token = token, loginData = loginData))
                    authState = AuthState.Authenticated(token, loginData, cachedProfile)
                    loginData
                }
            }
        }
    }

    /** Explicitly rotates the bearer pair via the custom refresh protocol. */
    public suspend fun refreshTokenExplicitly(): Token {
        tokens.current() ?: throw NotAuthenticatedYet("refresh")
        return refreshMutex.withLock { attemptRefresh(tokens.current()!!) }
    }

    /**
     * Restores a persisted session; `true` when a usable token was found.
     *
     * Does not verify freshness remotely — that happens lazily at first call.
     */
    public suspend fun restorePersistedSession(): Boolean {
        val snapshot = store.load()
        val token = snapshot?.token ?: return false
        cachedLogin = snapshot.loginData
        cachedProfile = snapshot.profile
        tokens.update(token)
        authState = AuthState.Authenticated(token, snapshot.loginData, snapshot.profile)
        return true
    }

    /** Session-scoped login metadata (codMin, opzioni), restored or freshly fetched. */
    public fun loginDataOrNull(): LoginData? = cachedLogin

    /** Latest known profile when [recordProfile] ran during this process lifetime. */
    public fun profileOrNull(): Profile? = cachedProfile

    /** Records the profile fetched by repositories so persistence stays complete. */
    public suspend fun recordProfile(profile: Profile) {
        cachedProfile = profile
        tokens.current()?.let { token ->
            store.save(SessionSnapshot(token = token, loginData = cachedLogin, profile = profile))
        }
    }

    /** True after either a live login or a successful [restorePersistedSession]. */
    public fun isAuthenticated(): Boolean = authState is AuthState.Authenticated || authState is AuthState.TokenRefreshed

    /** Clears every local artifact (in-memory + encrypted store). Network no-op. */
    public suspend fun clearLocally() {
        tokens.clear()
        cachedLogin = null
        cachedProfile = null
        authState = AuthState.Idle
    }

    // --- SessionContext -------------------------------------------------------

    override suspend fun headersWithFreshBearer(): AuthHeaders {
        val token = tokens.current()
        if (token == null) {
            if (!isAuthenticated()) return UNAUTHENTICATED_HEADERS
            throw NotAuthenticatedYet("request")
        }
        refreshIfNeeded()
        return currentHeadersInternal() ?: throw NotAuthenticatedYet("request")
    }

    override suspend fun currentHeaders(): AuthHeaders? = currentHeadersInternal()

    override suspend fun forceRefresh(): AuthHeaders {
        refreshTokenExplicitly()
        return currentHeadersInternal() ?: throw NotAuthenticatedYet("request")
    }

    // --- internals -------------------------------------------------------------

    /**
     * Single-flight refresh: concurrent callers share one rotation round instead of
     * firing duplicate `auth/refresh-token` POSTs with the same (soon-stale) bearer.
     * Losers re-read the winner's fresh token and proceed without a second request.
     */
    private val refreshMutex = Mutex()

    private suspend fun refreshIfNeeded() {
        if (!needsRefresh()) return
        refreshMutex.withLock {
            if (needsRefresh()) {
                val stale = tokens.current() ?: return@withLock
                attemptRefresh(stale)
            }
        }
    }

    private suspend fun needsRefresh(): Boolean {
        val token = tokens.current() ?: return false
        return token.isExpiredOrExpiringWithin(ArgoConstants.REFRESH_SLACK_SECONDS, clock.instant())
    }

    @Suppress("SwallowedException")
    // Rejections are re-typed per classification (terminal wipe / transient API failure)
    // so callers can distinguish "session dead" from "busy server".
    private suspend fun attemptRefresh(current: Token): Token {
        val login = cachedLogin
        val body =
            RefreshProtocol.buildBody(
                refreshToken = current.refreshToken,
                oldBearer = current.accessToken,
                currentExpiry = current.expiresAt,
                scope = current.scope,
                username = login?.username,
                now = clock.instant(),
            )
        // Transport noise must not kill the session: network failures surface as
        // NetworkException (transient, retryable) instead of an auth failure.
        val response =
            try {
                rawCall(
                    path = Endpoints.REFRESH_TOKEN,
                    method = HttpMethod.POST,
                    body = body,
                )
            } catch (e: IOException) {
                throw NetworkException(e)
            }
        val refreshed =
            try {
                // Parse the body first: the server may answer a terminal rejection
                // (invalid_grant) with HTTP 400 and an {error} payload, and the
                // status code alone must not hide that classification.
                RefreshProtocol.parseResponse(response.body, response.header("date"))
            } catch (e: RefreshRejectedException) {
                invalidateSession(e) // grant is dead: wipe material, then rethrow
                throw e
            } catch (e: AuthenticationException) {
                // Rejection that does not prove the grant dead (e.g. temporarily_unavailable):
                // surface as an API failure and keep the session for a later attempt.
                throw ArgoApiException(Endpoints.REFRESH_TOKEN, response.statusCode, e.detail)
            } catch (e: DeserializationException) {
                if (response.statusCode !in 200..299) {
                    throw ArgoApiException(Endpoints.REFRESH_TOKEN, response.statusCode, message = null)
                }
                throw e
            }
        if (response.statusCode !in 200..299) {
            throw ArgoApiException(Endpoints.REFRESH_TOKEN, response.statusCode, message = null)
        }
        tokens.update(refreshed)
        store.save(
            SessionSnapshot(
                token = refreshed,
                loginData = cachedLogin,
                profile = cachedProfile,
            ),
        )
        authState = AuthState.TokenRefreshed(refreshed)
        return refreshed
    }

    /**
     * Wipes every local artifact after a terminal grant rejection.
     *
     * The state machine lands on [AuthState.Failed] carrying the cause, so
     * nothing observable claims the session is still alive and a later
     * [restorePersistedSession] cannot resurrect the dead grant.
     */
    private suspend fun invalidateSession(failure: ArgoException) {
        tokens.clear()
        store.clear()
        cachedLogin = null
        cachedProfile = null
        authState = AuthState.Failed("refresh", failure)
    }

    private suspend fun postFamilyLogin(): LoginData {
        val response =
            rawCall(
                path = Endpoints.LOGIN,
                method = HttpMethod.POST,
                body =
                buildJsonObject {
                    put("lista-opzioni-notifiche", "{}")
                    put("lista-x-auth-token", "[]")
                    put("clientID", Nonces.alphanumeric(CLIENT_ID_LENGTH))
                },
            )
        if (response.statusCode !in 200..299) {
            throw AuthenticationException("login answered HTTP ${response.statusCode}")
        }
        return try {
            val envelope = json.decodeFromString(LoginEnvelopeWire.serializer(), response.body)
            when {
                !envelope.success -> AuthenticationException(envelope.msg ?: "login failed without a message")
                else -> null
            }?.let { throw it }
            envelope.data.firstOrNull()
                ?: throw AuthenticationException("login returned an empty data array")
        } catch (e: SerializationException) {
            throw DeserializationException(Endpoints.LOGIN, e)
        }
    }

    private suspend fun rawCall(path: String, method: HttpMethod, body: JsonObject?): ArgoHttpResponse {
        val headers = currentHeadersInternal()
        return engine.execute(
            ArgoHttpRequest(
                url = "${config.restBaseUrl.trimEnd('/')}/$path",
                method = method,
                body = body?.toString(),
                followRedirects = false,
                headers =
                buildMap {
                    put("accept", "application/json")
                    put("argo-client-version", config.didUpVersion)
                    if (method == HttpMethod.POST) put("content-type", "application/json")
                    headers?.bearer?.let { put("authorization", "Bearer $it") }
                    headers?.xAuthToken?.let { put("x-auth-token", it) }
                    headers?.codMin?.let { put("x-cod-min", it) }
                    headers?.tokenExpiresAt?.let { put("x-date-exp-auth", TimeFormats.formatWire(it)) }
                },
            ),
        )
    }

    private suspend fun currentHeadersInternal(): AuthHeaders? {
        val token = tokens.current() ?: return null
        val login = cachedLogin
        return AuthHeaders(
            bearer = token.accessToken,
            xAuthToken = login?.xAuthToken,
            codMin = login?.codMin,
            tokenExpiresAt = token.expiresAt,
        )
    }

    private inline fun <T> step(name: String, block: () -> T): T = try {
        block()
    } catch (e: ArgoException) {
        authState = AuthState.Failed(name, e)
        throw e
    }

    internal companion object {
        const val CLIENT_ID_LENGTH = 163
    }
}

/** Sentinel used by [ArgoSession] to signal "user must log in first". */
internal class NotAuthenticatedYet(step: String) : AuthenticationException("$step requires an authenticated session")

/** Local envelope carrier for the login payload (adds wire `total`). */
@kotlinx.serialization.Serializable
internal data class LoginEnvelopeWire(
    val success: Boolean,
    val msg: String? = null,
    val total: Int? = null,
    val data: List<LoginData> = emptyList(),
)
