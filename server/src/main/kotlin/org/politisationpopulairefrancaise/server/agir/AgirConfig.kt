package org.politisationpopulairefrancaise.server.agir

import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Settings of the review queue of the Agir space (held contents), read from the environment only:
 *
 * - PPF_HELD_RETENTION_DAYS: how long a decided content (approved or rejected) stays in the queue
 *   after the decision (default 30); an approved content lives on in the events table.
 * - PPF_HELD_CLEANUP_INTERVAL_MINUTES: how often the pending mobilizations whose date has passed are
 *   refused and the decided contents past their retention deleted (default 60; 0 disables the
 *   periodic task, for tests).
 *
 * No ceiling on the queue itself (decision of the owner, 2026-11-07): the limit per minute of
 * POST /api/v1/events (RelayConfig) is the only anti-spam measure. Invalid numbers fall back to the defaults.
 */
data class AgirConfig(
    val decidedRetention: Duration = DEFAULT_RETENTION,
    val cleanupInterval: Duration = DEFAULT_CLEANUP_INTERVAL,
) {
    init {
        require(decidedRetention.isPositive()) { "The retention of decided contents must be positive" }
        require(!cleanupInterval.isNegative()) { "The cleanup interval cannot be negative" }
    }

    companion object {
        const val RETENTION_VARIABLE: String = "PPF_HELD_RETENTION_DAYS"
        const val CLEANUP_INTERVAL_VARIABLE: String = "PPF_HELD_CLEANUP_INTERVAL_MINUTES"

        val DEFAULT_RETENTION: Duration = 30.days
        val DEFAULT_CLEANUP_INTERVAL: Duration = 1.hours

        fun fromEnvironment(env: (String) -> String? = System::getenv): AgirConfig = AgirConfig(
            decidedRetention = env(RETENTION_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it >= 1 }?.days ?: DEFAULT_RETENTION,
            cleanupInterval = env(CLEANUP_INTERVAL_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it >= 0 }?.minutes ?: DEFAULT_CLEANUP_INTERVAL,
        )
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
