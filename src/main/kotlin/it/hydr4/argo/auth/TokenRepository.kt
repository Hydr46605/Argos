package it.hydr4.argo.auth

import it.hydr4.argo.models.Token
import it.hydr4.argo.storage.SessionSnapshot
import it.hydr4.argo.storage.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Read/write authority over the current bearer token, decoupling the state
 * machine and transport layer from persistence details.
 */
public interface TokenRepository {
    /** The active token, or `null` before any authentication happened. */
    public suspend fun current(): Token?

    /**
     * Replaces the active token (login result or refresh rotation).
     *
     * Implementations persist write-through so restored sessions stay current.
     */
    public suspend fun update(token: Token)

    /** Drops in-memory and persisted material; invoked at logout or after irrevocable failures. */
    public suspend fun clear()
}

/**
 * Default repository: memory-first with write-through to [store] so processes
 * restore sessions across restarts.
 */
public class CachedTokenRepository(private val store: TokenStore) : TokenRepository {
    @Volatile
    private var cached: Token? = null
    private val mutex = Mutex()

    override suspend fun current(): Token? = cached ?: store.load()?.token?.also { cached = it }

    override suspend fun update(token: Token) {
        mutex.withLock {
            cached = token
            store.save(SessionSnapshot(token = token))
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            cached = null
            store.clear()
        }
    }
}
