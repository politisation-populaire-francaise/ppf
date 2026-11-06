package org.politisationpopulairefrancaise.server.agir

import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Settings of the review queue of the Agir space (held contents), read from the environment only:
 *
 * - PPF_HELD_MAX_PENDING_PER_KEY: how many contents one public key may have waiting for review at
 *   the same time (default 10). A new version of a waiting content replaces it and does not count twice.
 * - PPF_HELD_PER_KEY_PER_DAY: how many contents (new versions included) one public key may send to
 *   the queue per 24 hours (default 10), on top of the general limit per minute of the API.
 * - PPF_HELD_MAX_PENDING: how many contents may wait for review on the whole server (default 1000):
 *   keys are free to mint, so the limits per key alone do not bound the queue.
 * - PPF_HELD_RETENTION_DAYS: how long a decided content (approved or rejected) stays in the queue
 *   after the decision (default 30); an approved content lives on in the events table.
 * - PPF_HELD_CLEANUP_INTERVAL_MINUTES: how often the decided contents past their retention are
 *   deleted (default 60; 0 disables the periodic task, for tests).
 *
 * Invalid numbers fall back to the defaults.
 */
data class AgirConfig(
    val maxPendingPerKey: Int = DEFAULT_MAX_PENDING_PER_KEY,
    val perKeyPerDay: Int = DEFAULT_PER_KEY_PER_DAY,
    val maxPending: Int = DEFAULT_MAX_PENDING,
    val decidedRetention: Duration = DEFAULT_RETENTION,
    val cleanupInterval: Duration = DEFAULT_CLEANUP_INTERVAL,
) {
    init {
        require(maxPendingPerKey >= 1) { "At least one pending content per key must be allowed" }
        require(perKeyPerDay >= 1) { "At least one held content per day and per key must be allowed" }
        require(maxPending >= 1) { "At least one pending content must be allowed" }
        require(decidedRetention.isPositive()) { "The retention of decided contents must be positive" }
        require(!cleanupInterval.isNegative()) { "The cleanup interval cannot be negative" }
    }

    companion object {
        const val MAX_PENDING_PER_KEY_VARIABLE: String = "PPF_HELD_MAX_PENDING_PER_KEY"
        const val PER_KEY_PER_DAY_VARIABLE: String = "PPF_HELD_PER_KEY_PER_DAY"
        const val MAX_PENDING_VARIABLE: String = "PPF_HELD_MAX_PENDING"
        const val RETENTION_VARIABLE: String = "PPF_HELD_RETENTION_DAYS"
        const val CLEANUP_INTERVAL_VARIABLE: String = "PPF_HELD_CLEANUP_INTERVAL_MINUTES"

        const val DEFAULT_MAX_PENDING_PER_KEY: Int = 10
        const val DEFAULT_PER_KEY_PER_DAY: Int = 10
        const val DEFAULT_MAX_PENDING: Int = 1_000
        val DEFAULT_RETENTION: Duration = 30.days
        val DEFAULT_CLEANUP_INTERVAL: Duration = 1.hours

        fun fromEnvironment(env: (String) -> String? = System::getenv): AgirConfig = AgirConfig(
            maxPendingPerKey = env.positive(MAX_PENDING_PER_KEY_VARIABLE) ?: DEFAULT_MAX_PENDING_PER_KEY,
            perKeyPerDay = env.positive(PER_KEY_PER_DAY_VARIABLE) ?: DEFAULT_PER_KEY_PER_DAY,
            maxPending = env.positive(MAX_PENDING_VARIABLE) ?: DEFAULT_MAX_PENDING,
            decidedRetention = env.positive(RETENTION_VARIABLE)?.days ?: DEFAULT_RETENTION,
            cleanupInterval = env(CLEANUP_INTERVAL_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it >= 0 }?.minutes ?: DEFAULT_CLEANUP_INTERVAL,
        )

        private fun ((String) -> String?).positive(name: String): Int? = this(name)?.trim()?.toIntOrNull()?.takeIf { it >= 1 }
    }
}

/** What the Agir space needs from outside: its settings and the clock (injectable for the tests of dates). */
class AgirSetup(
    val config: AgirConfig = AgirConfig(),
    val clock: Clock = Clock.systemUTC(),
) {
    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): AgirSetup = AgirSetup(AgirConfig.fromEnvironment(env))
    }
}
