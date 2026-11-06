package org.politisationpopulairefrancaise.server.relay

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min

/**
 * Token bucket per key, in memory only. The key is the sender's public key, taken from the event
 * after its signature has been verified (so nobody can spend someone else's budget). Client
 * addresses are never used: the server does not look at them (CLAUDE.md, section 7).
 *
 * Each key starts with [permitsPerMinute] tokens, spends one per event and earns them back at
 * [permitsPerMinute] per minute. Buckets that are full again are forgotten once the table grows
 * past [maxTrackedKeys], which bounds memory without ever logging anything.
 *
 * This is the minimal anti-flood measure; the self-hosted proof of work (cahier des charges,
 * section 5.4) is still to be added in front of it.
 */
class RateLimiter(
    private val permitsPerMinute: Int,
    private val maxTrackedKeys: Int = DEFAULT_MAX_TRACKED_KEYS,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    init {
        require(permitsPerMinute >= 1) { "At least one permit per minute is needed" }
        require(maxTrackedKeys >= 1) { "At least one key must be tracked" }
    }

    sealed interface Outcome {
        data object Allowed : Outcome

        /** Refused; a token will be available after [retryAfterSeconds] (at least 1). */
        data class Limited(val retryAfterSeconds: Long) : Outcome
    }

    private class Bucket(var tokens: Double, var updatedAt: Long)

    private val capacity: Double = permitsPerMinute.toDouble()
    private val tokensPerNano: Double = permitsPerMinute / NANOS_PER_MINUTE
    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryAcquire(key: String): Outcome {
        val now = nanoTime()
        val bucket = buckets.computeIfAbsent(key) { Bucket(capacity, now) }
        val outcome = synchronized(bucket) {
            refill(bucket, now)
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                Outcome.Allowed
            } else {
                val waitNanos = (1.0 - bucket.tokens) / tokensPerNano
                Outcome.Limited(ceil(waitNanos / NANOS_PER_SECOND).toLong().coerceAtLeast(1))
            }
        }
        if (buckets.size > maxTrackedKeys) forgetIdleBuckets(now)
        return outcome
    }

    /** Number of keys currently tracked (for tests and for a future memory gauge). */
    val trackedKeys: Int get() = buckets.size

    private fun refill(bucket: Bucket, now: Long) {
        val elapsed = (now - bucket.updatedAt).coerceAtLeast(0)
        bucket.tokens = min(capacity, bucket.tokens + elapsed * tokensPerNano)
        bucket.updatedAt = now
    }

    private fun forgetIdleBuckets(now: Long) {
        buckets.entries.removeIf { (_, bucket) ->
            synchronized(bucket) {
                refill(bucket, now)
                bucket.tokens >= capacity
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_TRACKED_KEYS: Int = 10_000
        private const val NANOS_PER_SECOND: Double = 1_000_000_000.0
        private const val NANOS_PER_MINUTE: Double = 60 * NANOS_PER_SECOND
    }
}
