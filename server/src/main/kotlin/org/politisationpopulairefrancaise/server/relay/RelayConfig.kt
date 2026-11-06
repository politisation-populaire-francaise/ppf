package org.politisationpopulairefrancaise.server.relay

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Kinds the relay handles specially (NIP numbers in the comments). */
object RelayKinds {
    /** Gift wrap (NIP-59): the sealed, encrypted envelope of a private message (NIP-17). */
    const val GIFT_WRAP: Int = 1059
}

/**
 * Settings of the relay role of the server, read from the environment only:
 *
 * - PPF_WRAP_RETENTION_DAYS: how long encrypted envelopes (kind 1059) are kept, counted from their
 *   reception by the server (default 30 days). The recipient's device fetches them within that time.
 * - PPF_WRAP_CLEANUP_INTERVAL_MINUTES: how often expired envelopes are deleted (default 60; 0 disables
 *   the periodic task, for tests).
 * - PPF_POST_RATE_PER_MINUTE: how many events one public key may publish per minute (default 60).
 *
 * Invalid values fall back to the defaults.
 */
data class RelayConfig(
    val wrapRetention: Duration = DEFAULT_WRAP_RETENTION,
    val cleanupInterval: Duration = DEFAULT_CLEANUP_INTERVAL,
    val postRatePerMinute: Int = DEFAULT_POST_RATE_PER_MINUTE,
) {
    init {
        require(wrapRetention.isPositive()) { "The envelope retention must be positive" }
        require(!cleanupInterval.isNegative()) { "The cleanup interval cannot be negative" }
        require(postRatePerMinute >= 1) { "The publication rate must be at least 1 per minute" }
    }

    companion object {
        const val WRAP_RETENTION_VARIABLE: String = "PPF_WRAP_RETENTION_DAYS"
        const val CLEANUP_INTERVAL_VARIABLE: String = "PPF_WRAP_CLEANUP_INTERVAL_MINUTES"
        const val POST_RATE_VARIABLE: String = "PPF_POST_RATE_PER_MINUTE"

        val DEFAULT_WRAP_RETENTION: Duration = 30.days
        val DEFAULT_CLEANUP_INTERVAL: Duration = 1.hours
        const val DEFAULT_POST_RATE_PER_MINUTE: Int = 60

        fun fromEnvironment(env: (String) -> String? = System::getenv): RelayConfig = RelayConfig(
            wrapRetention = env(WRAP_RETENTION_VARIABLE)?.toIntOrNull()?.takeIf { it >= 1 }?.days ?: DEFAULT_WRAP_RETENTION,
            cleanupInterval = env(CLEANUP_INTERVAL_VARIABLE)?.toIntOrNull()?.takeIf { it >= 0 }?.minutes ?: DEFAULT_CLEANUP_INTERVAL,
            postRatePerMinute = env(POST_RATE_VARIABLE)?.toIntOrNull()?.takeIf { it >= 1 } ?: DEFAULT_POST_RATE_PER_MINUTE,
        )
    }
}
