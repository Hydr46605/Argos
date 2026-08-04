package it.hydr4.argo.util

import it.hydr4.argo.exceptions.NetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.random.Random

/**
 * Retry policy for the flaky, undocumented transport this library talks to.
 *
 * Defaults retry transport-level failures ([IOException], [NetworkException])
 * with exponential backoff and bounded jitter. Application-level failures
 * ([it.hydr4.argo.exceptions.ArgoApiException], authentication rejections)
 * are never retried by default — those indicate a state change, not noise.
 *
 * @property maxAttempts Total executions of the block (1 = no retries).
 * @property baseDelayMillis Backoff for the first retry; doubled per attempt.
 * @property maxDelayMillis Upper bound for the backoff.
 * @property jitterMillis Half-width of the uniform jitter window around the backoff.
 * @property retryOn Predicate deciding which failures are retryable.
 */
public data class RetryPolicy(
    public val maxAttempts: Int = 3,
    public val baseDelayMillis: Long = 200,
    public val maxDelayMillis: Long = 2_000,
    public val jitterMillis: Long = 50,
    public val retryOn: (Throwable) -> Boolean = { it is IOException || it is NetworkException },
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
        require(baseDelayMillis > 0) { "baseDelayMillis must be positive" }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis must be >= baseDelayMillis" }
        require(jitterMillis >= 0) { "jitterMillis must be >= 0" }
    }

    /**
     * Runs [block] up to [maxAttempts] times, backing off between retries.
     *
     * Cancellation is never swallowed: [CancellationException] propagates
     * immediately so coroutine cancellation stays responsive.
     *
     * @throws Throwable The last failure when attempts are exhausted.
     */
    @Suppress("TooGenericExceptionCaught")
    // The retryOn predicate decides which exceptions are noise; catching Throwable is the mechanism.
    public suspend fun <T> retry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                attempt += 1
                if (attempt >= maxAttempts || !retryOn(e)) throw e
                delay(backoffFor(attempt) + jitter())
            }
        }
    }

    /** Deterministic backoff curve for attempt [attempt] (1-based). */
    public fun backoffFor(attempt: Int): Long = minOf(baseDelayMillis shl (attempt - 1), maxDelayMillis)

    /** Uniform jitter in `[-jitterMillis, +jitterMillis]`. */
    public fun jitter(): Long = if (jitterMillis == 0L) 0L else Random.nextLong(-jitterMillis, jitterMillis + 1)
}
