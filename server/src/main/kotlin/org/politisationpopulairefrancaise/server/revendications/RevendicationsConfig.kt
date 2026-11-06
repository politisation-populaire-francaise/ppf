package org.politisationpopulairefrancaise.server.revendications

import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReviewModel
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Settings of the review queue of citizen demands, read from the environment only:
 *
 * - PPF_PROPOSALS_MAX_PENDING_PER_KEY: how many proposals one public key may have waiting for
 *   review at the same time (default 10). A new version of a waiting proposal replaces it and does
 *   not count twice.
 * - PPF_PROPOSALS_PER_KEY_PER_DAY: how many proposals (new versions included) one public key may
 *   send per 24 hours (default 5), on top of the general limit per minute of the API.
 * - PPF_PROPOSALS_MAX_PENDING: how many proposals may wait for review on the whole server (default
 *   1000): keys are free to mint, so the limits per key alone do not bound the queue.
 * - PPF_PROPOSALS_RETENTION_DAYS: how long a decided proposal (approved or rejected) is kept after
 *   the decision (default 30); an approved demand lives on in the events table.
 * - PPF_PROPOSALS_CLEANUP_INTERVAL_MINUTES: how often the decided proposals past their retention are
 *   deleted and the proposals left without a report are checked again (default 60; 0 disables the
 *   periodic task, for tests).
 * - PPF_AI_*: the AI model that reads the proposals, see [AiReviewConfig]; off unless configured.
 * - PPF_AI_MAX_REVIEWS_PER_DAY: how many proposals the AI model may read per 24 hours, all keys
 *   together (default 200): a ceiling on what a flood of proposals could cost with a paid service.
 *
 * Invalid numbers fall back to the defaults; an invalid AI configuration stops the server at startup.
 */
data class RevendicationsConfig(
    val maxPendingPerKey: Int = DEFAULT_MAX_PENDING_PER_KEY,
    val perKeyPerDay: Int = DEFAULT_PER_KEY_PER_DAY,
    val maxPending: Int = DEFAULT_MAX_PENDING,
    val decidedRetention: Duration = DEFAULT_RETENTION,
    val cleanupInterval: Duration = DEFAULT_CLEANUP_INTERVAL,
    val aiReviewsPerDay: Int = DEFAULT_AI_REVIEWS_PER_DAY,
    val ai: AiReviewConfig? = null,
) {
    init {
        require(maxPendingPerKey >= 1) { "At least one pending proposal per key must be allowed" }
        require(perKeyPerDay >= 1) { "At least one proposal per day and per key must be allowed" }
        require(maxPending >= 1) { "At least one pending proposal must be allowed" }
        require(decidedRetention.isPositive()) { "The retention of decided proposals must be positive" }
        require(!cleanupInterval.isNegative()) { "The cleanup interval cannot be negative" }
        require(aiReviewsPerDay >= 1) { "At least one AI review per day must be allowed" }
    }

    companion object {
        const val MAX_PENDING_PER_KEY_VARIABLE: String = "PPF_PROPOSALS_MAX_PENDING_PER_KEY"
        const val PER_KEY_PER_DAY_VARIABLE: String = "PPF_PROPOSALS_PER_KEY_PER_DAY"
        const val MAX_PENDING_VARIABLE: String = "PPF_PROPOSALS_MAX_PENDING"
        const val RETENTION_VARIABLE: String = "PPF_PROPOSALS_RETENTION_DAYS"
        const val CLEANUP_INTERVAL_VARIABLE: String = "PPF_PROPOSALS_CLEANUP_INTERVAL_MINUTES"
        const val AI_REVIEWS_PER_DAY_VARIABLE: String = "PPF_AI_MAX_REVIEWS_PER_DAY"

        const val DEFAULT_MAX_PENDING_PER_KEY: Int = 10
        const val DEFAULT_PER_KEY_PER_DAY: Int = 5
        const val DEFAULT_MAX_PENDING: Int = 1_000
        val DEFAULT_RETENTION: Duration = 30.days
        val DEFAULT_CLEANUP_INTERVAL: Duration = 1.hours
        const val DEFAULT_AI_REVIEWS_PER_DAY: Int = 200

        fun fromEnvironment(env: (String) -> String? = System::getenv): RevendicationsConfig = RevendicationsConfig(
            maxPendingPerKey = env.positive(MAX_PENDING_PER_KEY_VARIABLE) ?: DEFAULT_MAX_PENDING_PER_KEY,
            perKeyPerDay = env.positive(PER_KEY_PER_DAY_VARIABLE) ?: DEFAULT_PER_KEY_PER_DAY,
            maxPending = env.positive(MAX_PENDING_VARIABLE) ?: DEFAULT_MAX_PENDING,
            decidedRetention = env.positive(RETENTION_VARIABLE)?.days ?: DEFAULT_RETENTION,
            cleanupInterval = env(CLEANUP_INTERVAL_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it >= 0 }?.minutes ?: DEFAULT_CLEANUP_INTERVAL,
            aiReviewsPerDay = env.positive(AI_REVIEWS_PER_DAY_VARIABLE) ?: DEFAULT_AI_REVIEWS_PER_DAY,
            ai = AiReviewConfig.fromEnvironment(env),
        )

        private fun ((String) -> String?).positive(name: String): Int? = this(name)?.trim()?.toIntOrNull()?.takeIf { it >= 1 }
    }
}

/**
 * Everything the review queue needs from outside: its settings, the AI model (null: the AI check is
 * reported unavailable) and the clock (injectable for the tests of the retention).
 */
class RevendicationsSetup(
    val config: RevendicationsConfig = RevendicationsConfig(),
    val model: ProposalReviewModel? = null,
    val clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    /** Releases the HTTP client of the AI connector, if this setup created one. */
    override fun close() {
        (model as? AutoCloseable)?.close()
    }

    companion object {
        /** The settings of the environment, with the AI connector when PPF_AI_BASE_URL is set. */
        fun fromEnvironment(env: (String) -> String? = System::getenv): RevendicationsSetup {
            val config = RevendicationsConfig.fromEnvironment(env)
            return RevendicationsSetup(config, config.ai?.let(OpenAiCompatibleReviewModel::create))
        }
    }
}
