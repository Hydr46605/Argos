package it.hydr4.argo.repositories

import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.auth.ArgoSession
import it.hydr4.argo.models.LoginData
import it.hydr4.argo.models.Profile
import it.hydr4.argo.models.ProfileDetails

/**
 * Access to the authenticated family/student identity records.
 *
 * Caches the [Profile] both locally (for fast callers) and inside
 * [ArgoSession] so persistence writes stay complete across restarts.
 */
public class ProfileRepository(private val http: ArgoHttpClient, private val session: ArgoSession) {
    /** Latest known profile; `null` before the first [profilo] call in this process. */
    public fun currentOrNull(): Profile? = session.profileOrNull()

    /**
     * Fetches the `profilo` endpoint and updates the session cache.
     *
     * @throws it.hydr4.argo.exceptions.ArgoApiException on upstream failure.
     */
    public suspend fun profilo(): Profile {
        val profile = http.fetch(Endpoints.PROFILE, dataSerializer = Profile.serializer())
        session.recordProfile(profile)
        return profile
    }

    /**
     * Fetches extended personal details (`dettaglioprofilo`).
     *
     * Sections are individually nullable upstream — see [ProfileDetails].
     */
    public suspend fun dettagliProfilo(): ProfileDetails = http.fetch(
        Endpoints.PROFILE_DETAILS,
        body = kotlinx.serialization.json.buildJsonObject { },
        dataSerializer = ProfileDetails.serializer(),
    )

    /** Session login metadata access for orchestration layers. */
    public suspend fun loginData(): LoginData? = session.loginDataOrNull()
}
