package org.politisationpopulairefrancaise.server.agir

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.publication.HeldState
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PublishResponse
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import org.politisationpopulairefrancaise.server.revendications.PeriodBudget
import org.politisationpopulairefrancaise.server.revendications.RevendicationProposals
import java.time.Clock
import javax.sql.DataSource

/**
 * The Agir space on the server (docs/integration/agir-contrat.md, section 5): who may publish the
 * mobilizations, calls and directory entries directly, what waits for the review of a person, and
 * the review queue itself. The shape of these contents is checked before, by the publication policy
 * ([AgirRules]); POST /api/v1/events asks [route] once the policy allowed the event.
 *
 * | Content | Editorial key or verified organisation | Other key |
 * |---|---|---|
 * | Mobilization (31923) | published | held for review |
 * | Call, except below | published | held for review |
 * | Call for witnesses (`temoins`) | always held | always held |
 * | Fundraising call (`collecte`) | verified organisations only | refused (`403 not_allowed`) |
 * | Directory entry (`organisation`) | editorial keys only (policy) | refused (`403 not_allowed`) |
 *
 * An editorial key is not an organisation: a fundraising call needs a verified organisation, whoever
 * signs it (point of attention 12).
 */
class AgirSpace(
    val queue: HeldQueue,
    val verified: VerifiedOrganisations,
    val agenda: AgendaIndex,
    private val policy: PublicationPolicy,
    private val config: AgirConfig = AgirConfig(),
    val clock: Clock = Clock.systemUTC(),
) {
    private val dailyBudget = PeriodBudget(config.perKeyPerDay)

    /** What POST /api/v1/events does with an event the policy allowed. */
    sealed interface Route {
        data object Publish : Route

        data object Hold : Route

        data class Refuse(val refusal: PublicationPolicy.Decision.Refused) : Route
    }

    /** Whether [route] may answer anything but [Route.Publish]: cheap, no database. */
    fun concerns(event: SignedEvent): Boolean = AgirRules.categoryOf(event) != null

    /** Reads the verified organisations (cached) only for the contents of the space. */
    fun route(event: SignedEvent): Route = when (AgirRules.categoryOf(event)) {
        AgirCategory.MOBILIZATION -> if (isTrusted(event.pubkey)) Route.Publish else Route.Hold
        AgirCategory.CALL -> when (AgirRules.callTypeOf(event)) {
            AgirFormats.CALL_TYPE_WITNESSES -> Route.Hold
            AgirFormats.CALL_TYPE_FUNDRAISING ->
                if (verified.isVerified(event.pubkey)) Route.Publish else Route.Refuse(FUNDRAISING_REFUSED)
            else -> if (isTrusted(event.pubkey)) Route.Publish else Route.Hold
        }
        // The policy already refused the entries signed by another key than an editorial one.
        AgirCategory.ORGANISATION -> if (policy.isEditor(event.pubkey)) Route.Publish else Route.Refuse(ORGANISATION_REFUSED)
        null -> Route.Publish
    }

    private fun isTrusted(pubkey: String): Boolean = policy.isEditor(pubkey) || verified.isVerified(pubkey)

    /**
     * Answers the POST of a content to hold, whose signature was checked and which fitted the limit
     * per minute ([perKey], refunded when the request turns out to cost nothing). The answer has the
     * same body as for a citizen demand: `202 {"id": …, "status": "pending_review"}`.
     */
    suspend fun hold(call: ApplicationCall, event: SignedEvent, perKey: RateLimiter) {
        val outcome = withContext(Dispatchers.IO) { queue.submit(event, config.maxPendingPerKey, config.maxPending, dailyBudget) }
        when (outcome) {
            is HoldOutcome.Queued -> call.respond(HttpStatusCode.Accepted, PublishResponse(event.id, RevendicationProposals.PENDING_REVIEW))
            HoldOutcome.AlreadyPending -> {
                perKey.refund(event.pubkey)
                call.respond(HttpStatusCode.Accepted, PublishResponse(event.id, RevendicationProposals.PENDING_REVIEW))
            }
            HoldOutcome.AlreadyApproved -> {
                perKey.refund(event.pubkey)
                call.respond(HttpStatusCode.OK, PublishResponse(event.id, "duplicate"))
            }
            HoldOutcome.Superseded -> {
                perKey.refund(event.pubkey)
                call.respond(HttpStatusCode.OK, PublishResponse(event.id, "superseded"))
            }
            HoldOutcome.AlreadyRejected ->
                call.respond(HttpStatusCode.Conflict, ApiError("rejected", "This version was refused by the review team; send a corrected version."))
            HoldOutcome.TooManyPendingForKey ->
                call.tooMany(RevendicationProposals.RETRY_PENDING_SECONDS, "too_many_pending", "This key already has ${config.maxPendingPerKey} contents waiting for review.")
            HoldOutcome.QueueFull ->
                call.tooMany(RevendicationProposals.RETRY_PENDING_SECONDS, "queue_full", "Too many contents are waiting for review; try again later.")
            is HoldOutcome.OverDailyBudget ->
                call.tooMany(outcome.retryAfterSeconds, "daily_limit", "This key has sent ${config.perKeyPerDay} contents for review in the last 24 hours.")
        }
    }

    /** Called with every event the server has just stored (forgets the verified keys when the directory changes). */
    fun stored(event: SignedEvent) = verified.stored(event)

    /** Where the held contents among [ids] stand, for their author ([HeldState]: never the content nor the key). */
    fun statuses(ids: List<String>): List<HeldState> = queue.states(ids)

    /** Publishes a held content (team only: development routes for now, back-office M15 later). */
    fun approve(id: String): HeldDecision = queue.approve(id).also { decision ->
        if (decision is HeldDecision.Approved && decision.stored == StoreOutcome.STORED) stored(decision.event)
    }

    fun reject(id: String, reason: String): HeldDecision = queue.reject(id, reason)

    private suspend fun ApplicationCall.tooMany(retryAfterSeconds: Long, error: String, message: String) {
        response.headers.append(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
        respond(HttpStatusCode.TooManyRequests, ApiError(error, message))
    }

    companion object {
        val FUNDRAISING_REFUSED = PublicationPolicy.Decision.Refused(
            403,
            "not_allowed",
            "Only verified organisations publish calls to raise funds, towards their own collection page.",
        )

        val ORGANISATION_REFUSED = PublicationPolicy.Decision.Refused(403, "not_allowed", "Only the editorial team publishes the directory of organisations.")

        /**
         * The space on [dataSource], without periodic task: what the events API builds when it is given
         * none (the rules then hold as everywhere; only the cleanup of decided contents is missing).
         */
        fun create(dataSource: DataSource, events: EventRepository, policy: PublicationPolicy, setup: AgirSetup = AgirSetup()): AgirSpace =
            AgirSpace(
                queue = HeldQueue(dataSource, events, setup.clock),
                verified = VerifiedOrganisations(dataSource, policy::isEditor),
                agenda = AgendaIndex(dataSource),
                policy = policy,
                config = setup.config,
                clock = setup.clock,
            )

        /**
         * The space, its index of the agenda brought up to date and the cleanup of the queue, all tied
         * to the life of [application].
         */
        fun start(application: Application, dataSource: DataSource, events: EventRepository, policy: PublicationPolicy, setup: AgirSetup): AgirSpace {
            val space = create(dataSource, events, policy, setup)
            // Mobilizations stored before the agenda table existed (V5) enter it, once.
            space.agenda.indexMissing().takeIf { it > 0 }?.let { application.log.info("Agenda: $it mobilization(s) indexed.") }
            val interval = setup.config.cleanupInterval
            if (interval.isPositive()) {
                HeldMaintenance(space.queue, setup.config.decidedRetention, setup.clock).start(application, interval, application.log)
            }
            return space
        }
    }
}
