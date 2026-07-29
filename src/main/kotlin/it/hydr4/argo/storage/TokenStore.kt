package it.hydr4.argo.storage

import it.hydr4.argo.models.LoginData
import it.hydr4.argo.models.Profile
import it.hydr4.argo.models.Token
import kotlinx.serialization.Serializable

/**
 * Everything worth persisting across app restarts, encrypted at rest by stores.
 *
 * Fields are independently nullable: callers may persist tokens only during the
 * OAuth phase before family login has produced login data.
 */
@Serializable
public data class SessionSnapshot(
    public val token: Token? = null,
    public val loginData: LoginData? = null,
    public val profile: Profile? = null,
)

/**
 * Persistence contract for [SessionSnapshot].
 *
 * Implementations must encrypt payloads at rest ([AesGcmFileStore] does) and be
 * safe under concurrent process-single-writer access.
 */
public interface TokenStore {
    /** Loads the stored snapshot or `null` when absent/corrupted. */
    public suspend fun load(): SessionSnapshot?

    /** Atomically replaces the snapshot. */
    public suspend fun save(snapshot: SessionSnapshot)

    /** Removes any stored material (logout / corruption recovery). */
    public suspend fun clear()
}

/** Memory-only implementation used as fallback when no durable storage is wired. */
public class InMemoryTokenStore : TokenStore {
    @Volatile
    private var snapshot: SessionSnapshot? = null

    override suspend fun load(): SessionSnapshot? = snapshot

    override suspend fun save(snapshot: SessionSnapshot) {
        this.snapshot = snapshot
    }

    override suspend fun clear() {
        snapshot = null
    }
}
