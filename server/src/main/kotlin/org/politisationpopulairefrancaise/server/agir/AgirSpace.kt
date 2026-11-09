package org.politisationpopulairefrancaise.server.agir

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.publication.HeldState
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.agir.collectives.CollectiveRoles
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PublishResponse
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.relay.RateLimiter
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
 * | Fundraising call (`collecte`) | published (owner, 2026-11-07: PPF, an association, may call for funds) | refused (`403 not_allowed`) |
 * | Directory entry (`organisation`) | editorial keys only (policy) | refused (`403 not_allowed`) |
 * | Response to a mobilization (31925, contract version 2) | published | published (shown by no device) |
 *
 * Decisions of the owner of 2026-11-07: a verified organisation publishes directly as soon as it is
 * verified (no probation period); an author may always cancel their mobilization or close their call
 * ([AuthorChanges]): such a version is published directly, and a version of theirs still waiting for
 * review is withdrawn from the queue.
 */
class AgirSpace(
    val queue: HeldQueue,
    val verified: VerifiedOrganisations,
    val agenda: AgendaIndex,
    private val events: EventRepository,
    private val policy: PublicationPolicy,
    val clock: Clock = Clock.systemUTC(),
    /** Who publishes in the name of a collectif (M24, D-087): its founder, administrators and moderators. */
    private val collectives: CollectiveRoles = CollectiveRoles(events),
) {
    /** What POST /api/v1/events does with an event the policy allowed. */
    sealed interface Route {
        data object Publish : Route

        data object Hold : Route

        /** The author cancels or closes a version that waits for review ([pendingId]): it is withdrawn, nothing is published. */
        data class Withdraw(val pendingId: String) : Route

        data class Refuse(val refusal: PublicationPolicy.Decision.Refused) : Route
    }

    /** Whether [route] may answer anything but [Route.Publish]: cheap, no database. */
    fun concerns(event: SignedEvent): Boolean = AgirRules.categoryOf(event) != null

    /** Reads the verified organisations (cached), and the known versions for a cancellation or a closure. */
    fun route(event: SignedEvent): Route {
        // In the name of a collectif: only its founder, administrators and moderators; an author still ends what they published.
        collectives.refusal(event)?.let { refusal -> return authorsEnding(event) ?: Route.Refuse(refusal) }
        val normal = normalRoute(event)
        if (normal == Route.Publish) return normal
        return authorsEnding(event) ?: normal
    }

    private fun normalRoute(event: SignedEvent): Route = when (AgirRules.categoryOf(event)) {
        AgirCategory.MOBILIZATION -> if (isTrusted(event.pubkey)) Route.Publish else Route.Hold
        AgirCategory.CALL -> when (AgirRules.callTypeOf(event)) {
            AgirFormats.CALL_TYPE_WITNESSES -> Route.Hold
            AgirFormats.CALL_TYPE_FUNDRAISING -> if (isTrusted(event.pubkey)) Route.Publish else Route.Refuse(FUNDRAISING_REFUSED)
            else -> if (isTrusted(event.pubkey)) Route.Publish else Route.Hold
        }
        // The policy already refused the entries signed by another key than an editorial one.
        AgirCategory.ORGANISATION -> if (policy.isEditor(event.pubkey)) Route.Publish else Route.Refuse(ORGANISATION_REFUSED)
        // A response to a mobilization holds no text the application shows: no review (D-044). The
        // devices show only the responses of verified organisations and editorial keys.
        AgirCategory.PARTICIPATION -> Route.Publish
        null -> Route.Publish
    }

    /**
     * A cancellation or a closure by the author ([AuthorChanges]): published when it only ends the
     * published version, withdrawing the queued version when it only ends that one; null otherwise.
     */
    private fun authorsEnding(event: SignedEvent): Route? {
        if (!AuthorChanges.isEnding(event)) return null
        val identifier = event.firstTagValue("d") ?: return null
        val published = events.query(EventFilter(kinds = listOf(event.kind), authors = listOf(event.pubkey), identifiers = listOf(identifier), limit = 1))
            .firstOrNull { EventReplacement.replacementKey(it) == EventReplacement.replacementKey(event) }
        if (published != null && EventReplacement.supersedes(event, published) && AuthorChanges.onlyEnds(published, event)) return Route.Publish
        val pending = EventReplacement.replacementKey(event)?.let(queue::pendingAt)
        if (pending != null && EventReplacement.supersedes(event, pending.event) && AuthorChanges.onlyEnds(pending.event, event)) return Route.Withdraw(pending.id)
        return null
    }

    private fun isTrusted(pubkey: String): Boolean = policy.isEditor(pubkey) || verified.isVerified(pubkey)

    /**
     * Answers the POST of a content to hold ([Route.Hold]) or of a withdrawal ([Route.Withdraw]),
     * whose signature was checked and which fitted the limit per minute ([perKey], refunded when the
     * request turns out to cost nothing). A held content gets the same answer as a citizen demand:
     * `202 {"id": …, "status": "pending_review"}`; a withdrawal `200 {"id": …, "status": "withdrawn"}`.
     */
    suspend fun hold(call: ApplicationCall, event: SignedEvent, route: Route, perKey: RateLimiter) {
        if (route is Route.Withdraw) {
            // Only the cancellation is answered: the cancelled version was never published, nor is this one.
            if (withContext(Dispatchers.IO) { queue.withdrawOlderPending(event) }) {
                return call.respond(HttpStatusCode.OK, PublishResponse(event.id, WITHDRAWN))
            }
            // A newer version came in meanwhile, or the team decided first.
            perKey.refund(event.pubkey)
            return call.respond(HttpStatusCode.OK, PublishResponse(event.id, "superseded"))
        }
        when (withContext(Dispatchers.IO) { queue.submit(event) }) {
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
        }
    }

    /**
     * Called with every event the server has just stored: forgets the verified keys when the directory
     * changes; a published cancellation or closure withdraws the older version of its author still in the queue.
     */
    fun stored(event: SignedEvent) {
        verified.stored(event)
        if (AuthorChanges.isEnding(event)) queue.withdrawOlderPending(event)
    }

    /** Where the held contents among [ids] stand, for their author ([HeldState]: never the content nor the key). */
    fun statuses(ids: List<String>): List<HeldState> = queue.states(ids)

    /** Publishes a held content (team only: development routes for now, back-office M15 later). */
    fun approve(id: String): HeldDecision = queue.approve(id).also { decision ->
        if (decision is HeldDecision.Approved && decision.stored == StoreOutcome.STORED) stored(decision.event)
    }

    fun reject(id: String, reason: String): HeldDecision = queue.reject(id, reason)

    companion object {
        /** The status answered when a cancellation or a closure withdrew a version waiting for review. */
        const val WITHDRAWN: String = "withdrawn"

        val FUNDRAISING_REFUSED = PublicationPolicy.Decision.Refused(
            403,
            "not_allowed",
            "Only verified organisations and the editorial team publish calls to raise funds, towards their own collection page.",
        )

        val ORGANISATION_REFUSED = PublicationPolicy.Decision.Refused(403, "not_allowed", "Only the editorial team publishes the directory of organisations.")

        /**
         * The space on [dataSource], without periodic task: what the events API builds when it is given
         * none (the rules then hold as everywhere; only the periodic cleanup of the queue is missing).
         */
        fun create(dataSource: DataSource, events: EventRepository, policy: PublicationPolicy, setup: AgirSetup = AgirSetup()): AgirSpace =
            AgirSpace(
                queue = HeldQueue(dataSource, events, setup.clock),
                verified = VerifiedOrganisations(dataSource, policy::isEditor),
                agenda = AgendaIndex(dataSource),
                events = events,
                policy = policy,
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
