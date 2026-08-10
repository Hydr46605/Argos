package it.hydr4.argo.util

import it.hydr4.argo.exceptions.ArgoApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Backoff curve and retry-decision behavior of the resilience helper. */
class RetryPolicyTest {

    @Test
    fun `retryable failure is retried until success`() = runTest {
        val policy = RetryPolicy(maxAttempts = 5, baseDelayMillis = 10, maxDelayMillis = 100, jitterMillis = 0)
        var attempts = 0
        val result = policy.retry {
            attempts += 1
            if (attempts < 3) throw IOException("flaky") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `attempts are exhausted after maxAttempts`() = runTest {
        val policy = RetryPolicy(maxAttempts = 2, baseDelayMillis = 10, jitterMillis = 0)
        var attempts = 0
        assertFailsWith<IOException> {
            policy.retry {
                attempts += 1
                throw IOException("always fails")
            }
        }
        assertEquals(2, attempts)
    }

    @Test
    fun `application-level failures are never retried`() = runTest {
        val policy = RetryPolicy(maxAttempts = 3, baseDelayMillis = 10, jitterMillis = 0)
        var attempts = 0
        assertFailsWith<ArgoApiException> {
            policy.retry {
                attempts += 1
                throw ArgoApiException("login", 403, "rejected")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `custom predicate drives retryability`() = runTest {
        val policy = RetryPolicy(maxAttempts = 3, baseDelayMillis = 10, jitterMillis = 0, retryOn = { it is IllegalStateException })
        var attempts = 0
        val result = policy.retry {
            attempts += 1
            if (attempts < 2) error("transient") else "done"
        }
        assertEquals("done", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `cancellation is never swallowed`() = runTest {
        val policy = RetryPolicy(maxAttempts = 5, baseDelayMillis = 10, jitterMillis = 0)
        assertFailsWith<CancellationException> {
            policy.retry<Unit> { throw CancellationException("user cancelled") }
        }
    }

    @Test
    fun `backoff doubles per attempt and caps at the maximum`() {
        val policy = RetryPolicy(baseDelayMillis = 100, maxDelayMillis = 500)
        assertEquals(100, policy.backoffFor(1))
        assertEquals(200, policy.backoffFor(2))
        assertEquals(400, policy.backoffFor(3))
        assertEquals(500, policy.backoffFor(4))
        assertEquals(500, policy.backoffFor(10))
    }

    @Test
    fun `invalid policies are rejected`() {
        assertFailsWith<IllegalArgumentException> { RetryPolicy(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { RetryPolicy(baseDelayMillis = 0) }
        assertFailsWith<IllegalArgumentException> { RetryPolicy(maxDelayMillis = 1) }
        assertFailsWith<IllegalArgumentException> { RetryPolicy(jitterMillis = -1) }
    }

    @Test
    fun `default policy retries transport noise but not api errors`() {
        val policy = RetryPolicy()
        assertTrue(policy.retryOn(IOException("reset")))
        assertTrue(policy.retryOn(it.hydr4.argo.exceptions.NetworkException(IOException("timeout"))))
        assertTrue(!policy.retryOn(ArgoApiException("x", 500, null)))
    }
}
