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
import org.politisationpopulairefrancaise.core.revendications.ProposalState
import org.politisationpopulairefrancaise.core.revendications.ProposalStatus
import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.DraftRules
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSeverity
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PublishResponse
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import java.time.Clock
import javax.sql.DataSource

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
        // The rules of the form, again: a modified application must not skip them. Only kinds and
        // fields are named back, never what was found. Lengths first: the patterns of the rules
        // must never run on a text longer than the form allows.
        val draft = DemandDrafts.draftOf(article)
        val tooLong = DemandDrafts.tooLong(draft)
        if (tooLong.isNotEmpty()) return call.refuseDraft(tooLong.joinToString { "too_long (${it.name.lowercase()})" })
        val blocking = withContext(Dispatchers.Default) {
            DraftRules.check(draft).filter { it.severity == IssueSeverity.BLOCKING }
                .map { issue -> issue.kind.name.lowercase() + (issue.field?.let { " (${it.name.lowercase()})" } ?: "") } +
                // The identifier becomes part of the public address: no personal data there either.
                if (DraftRules.containsPersonalData(article.slug)) listOf("personal_data (identifier)") else emptyList()
        }
        if (blocking.isNotEmpty()) return call.refuseDraft(blocking.joinToString())

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
     * Where the proposals among [ids] stand, in the order asked, as the core reads it
     * ([ProposalState]); ids found nowhere are left out.
     * - Pending or rejected: the report of the automatic checks, which never names a demand that is
     *   not published (the checks also compare with the other pending proposals, whose authors have
     *   published nothing yet) and shows the published ones as they read now.
     * - Approved: no report. The event id is public once the demand is published, and the opinions
     *   of the checks on a published demand are the team's business, not anyone's.
     * - A published demand without a proposal (deleted 30 days after its approval, or published by
     *   the team directly): "approved", received when it reached the events table.
     */
    fun statusesForAuthor(ids: List<String>): List<ProposalState> {
        val wanted = ids.distinct()
        val proposals = queue.findAll(wanted).associateBy { it.id }
        val published = queue.publishedDemandReceptions(wanted.filter { it !in proposals })
        val mentioned = proposals.values.filter { it.status != ProposalStatus.APPROVED }
            .flatMap { proposal -> proposal.report?.issues.orEmpty().flatMap { issue -> issue.related.map { it.address } } }
        val current = queue.publishedDemandsAt(mentioned)
        return wanted.mapNotNull { id ->
            val proposal = proposals[id]
            when {
                proposal != null -> ProposalState(
                    id = proposal.id,
                    status = proposal.status,
                    receivedAt = proposal.receivedAt,
                    decidedAt = proposal.decidedAt,
                    reason = proposal.reason,
                    report = if (proposal.status == ProposalStatus.APPROVED) null else proposal.report?.let { forAuthor(it, current) },
                )
                id in published -> ProposalState(id = id, status = ProposalStatus.APPROVED, receivedAt = published.getValue(id))
                else -> null
            }
        }
    }

    private suspend fun ApplicationCall.refuseDraft(found: String) =
        respond(HttpStatusCode.BadRequest, ApiError("draft_rules", "The demand does not follow the rules of the form: $found."))

    private suspend fun ApplicationCall.tooMany(retryAfterSeconds: Long, error: String, message: String) {
        response.headers.append(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
        respond(HttpStatusCode.TooManyRequests, ApiError(error, message))
    }

    companion object {
        const val PENDING_REVIEW: String = "pending_review"

        /** A full queue empties as the team reviews: no precise date to give, the device tries again in an hour. */
        const val RETRY_PENDING_SECONDS: Long = 3_600

        /**
         * [report] as its author may read it: only the demands of [published] (by address), as they
         * read now. An issue that only pointed at other demands goes; one whose demands changed or
         * went loses its explanation too (it may quote what the author must not read).
         */
        fun forAuthor(report: ProposalReport, published: Map<String, DemandText>): ProposalReport = report.copy(
            issues = report.issues.mapNotNull { issue ->
                val kept = issue.related.mapNotNull { related ->
                    published[related.address]?.let { now ->
                        related.copy(title = now.title, statusTag = now.status?.tagValue, territory = now.territory)
                    }
                }
                when {
                    kept == issue.related -> issue
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
            val checkTimeout = (config.ai?.timeout ?: AiReviewConfig.DEFAULT_TIMEOUT) + ProposalReviewer.DEFAULT_CHECK_TIMEOUT
            val reviewer = ProposalReviewer(queue, model, setup.clock, application.log, checkTimeout).also { it.start(application) }
            if (config.cleanupInterval.isPositive()) {
                ProposalMaintenance(queue, reviewer, config.decidedRetention, setup.clock).start(application, config.cleanupInterval, application.log)
            }
            application.monitor.subscribe(ApplicationStopped) { setup.close() }
            if (model == null) application.log.info("No AI model configured: the AI check of proposed demands is reported unavailable.")
            return RevendicationProposals(queue, reviewer, config, setup.clock)
        }
    }
}
