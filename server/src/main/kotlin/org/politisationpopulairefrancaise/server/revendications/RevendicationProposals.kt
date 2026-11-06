package org.politisationpopulairefrancaise.server.revendications

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.politisationpopulairefrancaise.core.revendications.checks.DraftRules
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSeverity
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalChecker
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PublishResponse
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import java.time.Clock
import javax.sql.DataSource

/** What the author of a proposal may read about it (GET /api/v1/revendications/proposals): never its content nor its key. */
@Serializable
data class ProposalStatusView(
    val id: String,
    /** "pending", "approved" or "rejected". */
    val status: String,
    /** Seconds since 1970. */
    @SerialName("received_at") val receivedAt: Long,
    @SerialName("decided_at") val decidedAt: Long?,
    /** The team's reason, for a rejected proposal. */
    val reason: String?,
    /** The [ProposalReport] of the automatic checks; null until they ran. */
    val report: JsonElement?,
)

/**
 * Citizen demands on the server: a demand (kind 30023, `ppf-type` = revendication) signed by a key
 * outside the editorial team is never published at once. POST /api/v1/events (the single
 * publication port) hands it to [receive], which checks its shape and the rules of the form, then
 * queues it for the review of a person (decision of the owner, 2026-11-05) and answers
 * `202 {"id": …, "status": "pending_review"}`. The automatic checks run afterwards, in the
 * background ([ProposalReviewer]); the author follows the proposal with [statusesForAuthor].
 */
class RevendicationProposals(
    val queue: ProposalQueue,
    private val reviewer: ProposalReviewer?,
    private val config: RevendicationsConfig,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dailyBudget = PeriodBudget(config.perKeyPerDay)

    /**
     * Answers the POST of a citizen demand whose signature was checked and which fitted the limit
     * per minute ([perKey], refunded when the request turns out to cost nothing).
     */
    suspend fun receive(call: ApplicationCall, event: SignedEvent, perKey: RateLimiter) {
        val article = when (val shape = DemandDrafts.shapeOf(event, clock.instant().epochSecond)) {
            is DemandDrafts.Shape.Invalid -> return call.respond(HttpStatusCode.BadRequest, ApiError(shape.error, shape.message))
            is DemandDrafts.Shape.Valid -> shape.article
        }
        // The rules of the form, again: a modified application must not skip them. Only kinds and fields are named back.
        val blocking = DraftRules.check(DemandDrafts.draftOf(article)).filter { it.severity == IssueSeverity.BLOCKING }
        if (blocking.isNotEmpty()) {
            val found = blocking.joinToString { issue -> issue.kind.name.lowercase() + (issue.field?.let { " (${it.name.lowercase()})" } ?: "") }
            return call.respond(HttpStatusCode.BadRequest, ApiError("draft_rules", "The demand does not follow the rules of the form: $found."))
        }
        val outcome = withContext(Dispatchers.IO) {
            queue.submit(event, article.slug, config.maxPendingPerKey, config.maxPending, dailyBudget)
        }
        when (outcome) {
            is SubmitOutcome.Queued -> {
                reviewer?.schedule(event.id)
                call.respond(HttpStatusCode.Accepted, PublishResponse(event.id, PENDING_REVIEW))
            }
            SubmitOutcome.AlreadyPending -> {
                perKey.refund(event.pubkey)
                call.respond(HttpStatusCode.Accepted, PublishResponse(event.id, PENDING_REVIEW))
            }
            SubmitOutcome.AlreadyApproved -> {
                perKey.refund(event.pubkey)
                call.respond(HttpStatusCode.OK, PublishResponse(event.id, "duplicate"))
            }
            SubmitOutcome.Superseded -> {
                perKey.refund(event.pubkey)
                call.respond(HttpStatusCode.OK, PublishResponse(event.id, "superseded"))
            }
            SubmitOutcome.AlreadyRejected ->
                call.respond(HttpStatusCode.Conflict, ApiError("rejected", "This version was refused by the review team; send a corrected version."))
            SubmitOutcome.TooManyPendingForKey ->
                call.tooMany(RETRY_PENDING_SECONDS, "too_many_pending", "This key already has ${config.maxPendingPerKey} demands waiting for review.")
            SubmitOutcome.QueueFull ->
                call.tooMany(RETRY_PENDING_SECONDS, "queue_full", "Too many demands are waiting for review; try again later.")
            is SubmitOutcome.OverDailyBudget ->
                call.tooMany(outcome.retryAfterSeconds, "daily_limit", "This key has sent ${config.perKeyPerDay} demands in the last 24 hours.")
        }
    }

    /**
     * The status of the proposals among [ids] (unknown ids are left out), in the order asked. The
     * report served here never names a proposal that is not published: the checks also compare
     * with the other pending proposals, whose authors have not published anything yet.
     */
    fun statusesForAuthor(ids: List<String>): List<ProposalStatusView> {
        val known = queue.findAll(ids).associateBy { it.id }
        val found = ids.distinct().mapNotNull { known[it] }
        val mentioned = found.flatMap { proposal -> proposal.report?.issues.orEmpty().flatMap { issue -> issue.related.map { it.address } } }
        val published = queue.publishedAddresses(mentioned)
        return found.map { proposal ->
            ProposalStatusView(
                id = proposal.id,
                status = proposal.status.value,
                receivedAt = proposal.receivedAt,
                decidedAt = proposal.decidedAt,
                reason = proposal.reason,
                report = proposal.report?.let { report ->
                    ProposalQueue.reportJson.encodeToJsonElement(ProposalReport.serializer(), forAuthor(report, published))
                },
            )
        }
    }

    private suspend fun ApplicationCall.tooMany(retryAfterSeconds: Long, error: String, message: String) {
        response.headers.append(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
        respond(HttpStatusCode.TooManyRequests, ApiError(error, message))
    }

    companion object {
        const val PENDING_REVIEW: String = "pending_review"

        /** A full queue empties as the team reviews: no precise date to give, the device tries again in an hour. */
        const val RETRY_PENDING_SECONDS: Long = 3_600

        /**
         * [report] without the demands that are not in [published]; an issue that only pointed at
         * such demands goes, and one that loses some of them loses its explanation too (the model
         * may have quoted them).
         */
        fun forAuthor(report: ProposalReport, published: Set<String>): ProposalReport = report.copy(
            issues = report.issues.mapNotNull { issue ->
                val kept = issue.related.filter { it.address in published }
                when {
                    kept.size == issue.related.size -> issue
                    kept.isEmpty() -> null
                    else -> issue.copy(related = kept, detail = null)
                }
            },
        )

        /**
         * The queue, its background checks and its periodic task, all tied to the life of
         * [application]. The AI model, if any, is capped at [RevendicationsConfig.aiReviewsPerDay].
         */
        fun start(application: Application, dataSource: DataSource, events: EventRepository, setup: RevendicationsSetup): RevendicationProposals {
            val config = setup.config
            val queue = ProposalQueue(dataSource, events, setup.clock)
            val model = setup.model?.let { CappedReviewModel(it, PeriodBudget(config.aiReviewsPerDay, maxTrackedKeys = 1)) }
            val checker = ProposalChecker(legal = null, model = model, clock = { setup.clock.instant().epochSecond })
            val reviewer = ProposalReviewer(queue, checker, application.log).also { it.start(application) }
            if (config.cleanupInterval.isPositive()) {
                ProposalMaintenance(queue, reviewer, config.decidedRetention, setup.clock).start(application, config.cleanupInterval, application.log)
            }
            application.monitor.subscribe(ApplicationStopped) { setup.close() }
            if (model == null) application.log.info("No AI model configured: the AI check of proposed demands is reported unavailable.")
            return RevendicationProposals(queue, reviewer, config, setup.clock)
        }
    }
}
