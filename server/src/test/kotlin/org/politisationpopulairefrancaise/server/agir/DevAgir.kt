package org.politisationpopulairefrancaise.server.agir

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.revendications.PeriodBudget

/** A held content as the development routes list it: everything the team will see. */
@Serializable
private data class DevHeld(
    val id: String,
    val address: String?,
    val kind: Int,
    val type: String?,
    @SerialName("call_type") val callType: String?,
    val title: String?,
    val territory: String?,
    @SerialName("received_at") val receivedAt: Long,
    val event: SignedEvent,
)

@Serializable
private data class DevHeldDecision(val id: String, val outcome: String)

/**
 * The development stand-in for the team's review of the Agir space (127.0.0.1 only, no
 * authentication; the real back-office comes with the moderation, M15, with two-factor
 * authentication and an audit log):
 * - GET /dev/held: the pending contents, oldest first;
 * - POST /dev/held/{id}/approve: publishes one, exactly as signed;
 * - POST /dev/held/{id}/reject, with the reason as the request body.
 */
fun Route.devHeldRoutes(queue: HeldQueue) {
    route("/dev/held") {
        get {
            val pending = withContext(Dispatchers.IO) { queue.pending() }.map { held ->
                val event = held.event
                DevHeld(
                    id = held.id,
                    address = EventReplacement.replacementKey(event),
                    kind = event.kind,
                    type = event.firstTagValue(AgirFormats.TAG_EVENT_TYPE) ?: event.firstTagValue(AgirFormats.TAG_TYPE),
                    callType = event.firstTagValue(AgirFormats.TAG_CALL_TYPE),
                    title = event.firstTagValue(AgirFormats.TAG_TITLE),
                    territory = event.firstTagValue(AgirFormats.TAG_TERRITORY),
                    receivedAt = held.receivedAt,
                    event = event,
                )
            }
            call.respond(pending)
        }
        post("{id}/approve") {
            val id = call.parameters["id"].orEmpty()
            call.answer(id, withContext(Dispatchers.IO) { queue.approve(id) })
        }
        post("{id}/reject") {
            val id = call.parameters["id"].orEmpty()
            val reason = call.receiveText().trim()
            if (reason.isEmpty() || reason.length > HeldQueue.MAX_REASON_LENGTH) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("bad_reason", "Give the reason (1 to ${HeldQueue.MAX_REASON_LENGTH} characters) as the request body."))
            }
            call.answer(id, withContext(Dispatchers.IO) { queue.reject(id, reason) })
        }
    }
}

private suspend fun ApplicationCall.answer(id: String, outcome: HeldDecision) = when (outcome) {
    is HeldDecision.Approved -> respond(DevHeldDecision(id, "approved (${outcome.stored.name.lowercase()})"))
    HeldDecision.Rejected -> respond(DevHeldDecision(id, "rejected"))
    HeldDecision.NotFound -> respond(HttpStatusCode.NotFound, ApiError("not_found", "No held content with this id."))
    is HeldDecision.AlreadyDecided -> respond(HttpStatusCode.Conflict, ApiError("already_decided", "This content is already ${outcome.status.column}."))
}

/**
 * Two example mobilizations of the development server, marked [EXEMPLE], so that the agenda, its
 * iCalendar feed and the review can be tried even before agenda.json exists: one published by
 * [publisher] (an editor key of the session), one proposed by a throwaway key and left in [queue].
 * Returns the published one, to store.
 */
fun exampleMobilizations(publisher: LocalEventSigner, queue: HeldQueue): SignedEvent = runBlocking {
    val now = System.currentTimeMillis() / 1000
    val day = 86_400L
    val published = publisher.sign(
        kind = AgirFormats.KIND_MOBILIZATION,
        content = "[EXEMPLE] Assemblée ouverte du serveur de développement : ordre du jour, tour de parole, prochaines actions.",
        tags = listOf(
            listOf("d", "exemple-assemblee-dev"),
            listOf(AgirFormats.TAG_TITLE, "[EXEMPLE] Assemblée générale de démonstration"),
            listOf(AgirFormats.TAG_START, (now + 3 * day).toString()),
            listOf(AgirFormats.TAG_END, (now + 3 * day + 7_200).toString()),
            listOf(AgirFormats.TAG_START_TZID, AgirFormats.TIME_ZONE),
            listOf(AgirFormats.TAG_LOCATION, "[EXEMPLE] Salle municipale, place de la Mairie"),
            listOf(AgirFormats.TAG_EVENT_TYPE, "assemblee-generale"),
            listOf(AgirFormats.TAG_TERRITORY, "Lyon"),
            listOf(AgirFormats.TAG_CHARTER, AgirFormats.CHARTER_VERSION),
        ),
        createdAt = now - 600,
    )
    val proposed = LocalEventSigner(IdentityKeys.generate()).sign(
        kind = AgirFormats.KIND_MOBILIZATION,
        content = "[EXEMPLE] Rassemblement proposé par un compte ordinaire : il attend la relecture (GET /dev/held).",
        tags = listOf(
            listOf("d", "exemple-rassemblement-propose"),
            listOf(AgirFormats.TAG_TITLE, "[EXEMPLE] Rassemblement à relire"),
            listOf(AgirFormats.TAG_START, (now + 7 * day).toString()),
            listOf(AgirFormats.TAG_LOCATION, "[EXEMPLE] Parvis de la gare"),
            listOf(AgirFormats.TAG_EVENT_TYPE, "rassemblement"),
            listOf(AgirFormats.TAG_TERRITORY, "France"),
            listOf(AgirFormats.TAG_CHARTER, AgirFormats.CHARTER_VERSION),
        ),
        createdAt = now - 300,
    )
    queue.submit(proposed, maxPendingPerKey = 1, maxPending = 1, budget = PeriodBudget(1))
    published
}
