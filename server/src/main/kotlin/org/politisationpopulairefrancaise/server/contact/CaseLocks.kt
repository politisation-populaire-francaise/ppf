package org.politisationpopulairefrancaise.server.contact

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The wrong tries of the access token, per case number, in memory only (never in the database, never in a
 * log): [maxFailures] wrong tries lock the number for [lockFor], whether the number exists or not, so that the
 * lock tells nothing about the existence of a case. A try older than [lockFor] is forgotten. The table holds
 * [maxTracked] numbers at most: beyond, the entries that lock nothing are dropped first. The token has 256
 * bits: the lock only slows down a client gone wrong, it is not what protects the case.
 */
class CaseLocks(
    private val clock: Clock,
    val maxFailures: Int = 5,
    val lockFor: Duration = 15.minutes,
    private val maxTracked: Int = 10_000,
) {
    private class Entry(var failures: Int, var lastFailure: Instant, var lockedUntil: Instant?)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Seconds left (at least 1) when [number] is locked; null otherwise. */
    fun lockedFor(number: String): Long? {
        val entry = entries[number] ?: return null
        val now = clock.instant()
        synchronized(entry) {
            val until = entry.lockedUntil ?: return null
            if (!now.isBefore(until)) {
                entries.remove(number, entry)
                return null
            }
            return (until.epochSecond - now.epochSecond).coerceAtLeast(1)
        }
    }

    /** One more wrong try for [number]. */
    fun failed(number: String) {
        val now = clock.instant()
        if (entries.size >= maxTracked && !entries.containsKey(number)) sweep(now)
        val entry = entries.computeIfAbsent(number) { Entry(0, now, null) }
        synchronized(entry) {
            if (entry.lockedUntil != null) return
            if (now.isAfter(entry.lastFailure.plusSeconds(lockFor.inWholeSeconds))) entry.failures = 0
            entry.failures++
            entry.lastFailure = now
            if (entry.failures >= maxFailures) entry.lockedUntil = now.plusSeconds(lockFor.inWholeSeconds)
        }
    }

    /** A right token: the wrong tries of [number] are forgotten. */
    fun succeeded(number: String) {
        entries.remove(number)
    }

    /** Number of numbers tracked (for the tests). */
    val tracked: Int get() = entries.size

    /** Drops every entry that locks nothing now (tries not yet locking, locks over). */
    private fun sweep(now: Instant) {
        entries.entries.removeIf { (_, entry) ->
            synchronized(entry) {
                val until = entry.lockedUntil
                until == null || !now.isBefore(until)
            }
        }
    }
}
