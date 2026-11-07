package org.politisationpopulairefrancaise.server.backoffice

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Failures that no account explains (a key nobody registered, a refused registration from an
 * invitation), counted in memory by period of [bucket] (D-127): the journal receives one line per
 * period with the counts, never one line per attempt, so that nobody can fill it from the tunnel.
 * Failures of a known account are journaled one by one, as before.
 */
class AnonymousFailures(private val clock: Clock = Clock.systemUTC(), private val bucket: Duration = 10.minutes) {
    private val counts = ConcurrentHashMap<Pair<Long, String>, AtomicInteger>()

    /** One more failure with the stable [code] (for example `unknown_key`). */
    fun count(code: String) {
        counts.computeIfAbsent(bucketOf(clock.instant().epochSecond) to code.take(40)) { AtomicInteger() }.incrementAndGet()
    }

    /** Whether a period before the current one still has counts to write. */
    fun hasClosed(): Boolean {
        val current = bucketOf(clock.instant().epochSecond)
        return counts.keys.any { it.first < current }
    }

    /**
     * Takes the counts of the periods that are over (all of them when [all]), by period start: each map
     * holds the codes and their numbers, never anything about the attempts themselves.
     */
    fun drain(all: Boolean = false): Map<Long, Map<String, Int>> {
        val current = bucketOf(clock.instant().epochSecond)
        val closed = counts.keys.filter { all || it.first < current }
        val result = mutableMapOf<Long, MutableMap<String, Int>>()
        closed.forEach { key ->
            val value = counts.remove(key)?.get() ?: 0
            if (value > 0) result.getOrPut(key.first) { mutableMapOf() }.merge(key.second, value, Int::plus)
        }
        return result
    }

    val bucketSeconds: Long get() = bucket.inWholeSeconds

    private fun bucketOf(epochSecond: Long): Long = epochSecond - Math.floorMod(epochSecond, bucket.inWholeSeconds)
}
