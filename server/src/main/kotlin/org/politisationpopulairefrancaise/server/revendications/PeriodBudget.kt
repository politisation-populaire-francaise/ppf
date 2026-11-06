package org.politisationpopulairefrancaise.server.revendications

import org.politisationpopulairefrancaise.server.relay.RateLimiter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * A budget of [permits] per [period] and per key (a token bucket, like [RateLimiter] but over a
 * period of hours instead of a minute), in memory only: it is forgotten when the server restarts,
 * and never tied to a client address. The key is a public key whose signature was checked, or a
 * fixed key for a budget of the whole server.
 *
 * Each key starts with [permits] tokens and earns them back at [permits] per [period]: with 5 per
 * 24 hours, a key that spent its five tokens gets the next one after 4 h 48 min.
 */
class PeriodBudget(
    private val permits: Int,
    private val period: Duration = 24.hours,
    private val maxTrackedKeys: Int = RateLimiter.DEFAULT_MAX_TRACKED_KEYS,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    init {
        require(permits >= 1) { "At least one permit per period is needed" }
        require(period.isPositive()) { "The period must be positive" }
        require(maxTrackedKeys >= 1) { "At least one key must be tracked" }
    }

    private class Bucket(var tokens: Double, var updatedAt: Long)

    private val capacity: Double = permits.toDouble()
    private val tokensPerNano: Double = permits / period.inWholeNanoseconds.toDouble()
    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryAcquire(key: String): RateLimiter.Outcome {
        val now = nanoTime()
        val bucket = buckets.computeIfAbsent(key) { Bucket(capacity, now) }
        val outcome = synchronized(bucket) {
            refill(bucket, now)
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                RateLimiter.Outcome.Allowed
            } else {
                val waitNanos = (1.0 - bucket.tokens) / tokensPerNano
                RateLimiter.Outcome.Limited(ceil(waitNanos / NANOS_PER_SECOND).toLong().coerceAtLeast(1))
            }
        }
        if (buckets.size > maxTrackedKeys) forgetFullBuckets(now)
        return outcome
    }

    /** Gives one token back, when what it paid for did not happen after all. */
    fun refund(key: String) {
        val bucket = buckets[key] ?: return
        synchronized(bucket) {
            bucket.tokens = min(capacity, bucket.tokens + 1.0)
        }
    }

    /** Number of keys currently tracked (for tests and for a future memory gauge). */
    val trackedKeys: Int get() = buckets.size

    private fun refill(bucket: Bucket, now: Long) {
        val elapsed = (now - bucket.updatedAt).coerceAtLeast(0)
        bucket.tokens = min(capacity, bucket.tokens + elapsed * tokensPerNano)
        bucket.updatedAt = now
    }

    private fun forgetFullBuckets(now: Long) {
        buckets.entries.removeIf { (_, bucket) ->
            synchronized(bucket) {
                refill(bucket, now)
                bucket.tokens >= capacity
            }
        }
    }

    companion object {
        /** The single key of a budget shared by the whole server; it cannot collide with a hex public key. */
        const val GLOBAL_KEY: String = "*"

        private const val NANOS_PER_SECOND: Double = 1_000_000_000.0
    }
}
