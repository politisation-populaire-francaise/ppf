package org.politisationpopulairefrancaise.server.relay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RateLimiterTest {

    private var now: Long = 0
    private val limiter = RateLimiter(permitsPerMinute = 60, maxTrackedKeys = 2, nanoTime = { now })
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    @Test
    fun eachKeyHasItsOwnBudgetWhichRefillsOverTime() {
        repeat(60) { assertEquals(RateLimiter.Outcome.Allowed, limiter.tryAcquire(alice), "event ${it + 1}") }
        val limited = assertIs<RateLimiter.Outcome.Limited>(limiter.tryAcquire(alice))
        assertEquals(1, limited.retryAfterSeconds)
        // Another key is not affected.
        assertEquals(RateLimiter.Outcome.Allowed, limiter.tryAcquire(bob))

        // One token comes back every second at 60 per minute (half-second margins against rounding).
        now += 500_000_000
        assertIs<RateLimiter.Outcome.Limited>(limiter.tryAcquire(alice))
        now += 2_000_000_000
        assertEquals(RateLimiter.Outcome.Allowed, limiter.tryAcquire(alice))
        assertEquals(RateLimiter.Outcome.Allowed, limiter.tryAcquire(alice))
        assertIs<RateLimiter.Outcome.Limited>(limiter.tryAcquire(alice))

        // After a full minute the bucket is full again, never more.
        now += 120_000_000_000
        repeat(60) { assertEquals(RateLimiter.Outcome.Allowed, limiter.tryAcquire(alice)) }
        assertIs<RateLimiter.Outcome.Limited>(limiter.tryAcquire(alice))
    }

    @Test
    fun idleKeysAreForgottenWhenTheTableGrows() {
        limiter.tryAcquire(alice)
        limiter.tryAcquire(bob)
        assertEquals(2, limiter.trackedKeys)
        // A third key while the others are still refilling: nothing can be dropped yet.
        limiter.tryAcquire("c".repeat(64))
        assertEquals(3, limiter.trackedKeys)
        // Once the first keys are full again, the next overflow forgets them.
        now += 60_000_000_000
        limiter.tryAcquire("d".repeat(64))
        assertTrue(limiter.trackedKeys <= 2, "tracked ${limiter.trackedKeys}")
    }

    @Test
    fun configurationRejectsNonsense() {
        assertFailsWith<IllegalArgumentException> { RateLimiter(permitsPerMinute = 0) }
        assertFailsWith<IllegalArgumentException> { RelayConfig(postRatePerMinute = 0) }
        val fromEnvironment = RelayConfig.fromEnvironment { name ->
            when (name) {
                RelayConfig.WRAP_RETENTION_VARIABLE -> "7"
                RelayConfig.CLEANUP_INTERVAL_VARIABLE -> "abc"
                RelayConfig.POST_RATE_VARIABLE -> "-3"
                else -> null
            }
        }
        assertEquals(RelayConfig(wrapRetention = kotlin.time.Duration.parse("7d")), fromEnvironment)
        assertEquals(RelayConfig(), RelayConfig.fromEnvironment { null })
    }
}
