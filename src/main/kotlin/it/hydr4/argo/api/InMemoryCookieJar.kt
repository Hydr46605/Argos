package it.hydr4.argo.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cookie jar used by the default engine.
 *
 * The SSO dance binds the login session to cookies across redirect hops; a
 * plain `NO_COOKIES` jar breaks it and a persistent jar would leak session
 * material to disk. This keeps everything process-local and forgets expired
 * cookies on read.
 */
public class InMemoryCookieJar : CookieJar {

    private val store = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Snapshot-and-swap: readers see an immutable list, so no locking is needed.
        store[url.host] = store[url.host].orEmpty() + cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host]
        ?.filter { it.expiresAt > System.currentTimeMillis() }
        .orEmpty()
}
