package org.politisationpopulairefrancaise.server.decider

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.FlowContent
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import org.politisationpopulairefrancaise.core.decider.Amendment
import org.politisationpopulairefrancaise.core.decider.DeliberationFormats
import org.politisationpopulairefrancaise.core.decider.Proposition
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PublishResponse
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeQueue
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.QueueAction
import org.politisationpopulairefrancaise.server.backoffice.QueueItem
import org.politisationpopulairefrancaise.server.backoffice.QueuePage
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import java.time.ZoneOffset

/**
 * The queue `decider.deliberation` of the team back-office (contract docs/integration/back-office.md,
 * section 7): the propositions and amendments of citizens waiting for review. Approve publishes exactly
 * what the author signed (while its phase is open); refuse needs a reason, shown to the author. The
 * reviewer checks the charter and the legal feasibility, never the team's preferences (deliberation.md, 1).
 */
class DeliberationQueue(private val submissions: DeliberationSubmissions) : BackOfficeQueue {
    override val id: String = ID
    override val title: String = "Propositions et amendements des délibérations"
    override val viewPermission: String = Permissions.queueDecide(ID)

    override fun pendingCount(): Int = submissions.pendingCount()

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val listed = submissions.pending(offset, limit + 1)
        val items = listed.take(limit).map { submission ->
            QueueItem(
                id = submission.id,
                title = titleOf(submission),
                receivedAt = submission.receivedDay.atStartOfDay().toEpochSecond(ZoneOffset.UTC),
                summary = if (submission.type == DeliberationFormats.TYPE_PROPOSITION) "Proposition" else "Amendement",
            )
        }
        return QueuePage(items, (offset + limit).takeIf { listed.size > limit }?.toString())
    }

    private fun titleOf(submission: Submission): String = when (submission.type) {
        DeliberationFormats.TYPE_PROPOSITION -> Proposition.parse(submission.event)?.title?.take(200) ?: "Proposition"
        else -> "Amendement de ${submission.parent.substringAfterLast(':').take(120)}"
    }

    override fun render(itemId: String, html: FlowContent) {
        val submission = submissions.find(itemId)
        if (submission == null) {
            html.h1 { +"Cette contribution n'est plus dans la file." }
            return
        }
        val deliberation = runCatching { ContentAddress.parse(submission.deliberation) }.getOrNull()?.let(submissions::deliberation)
        html.h1 { +(if (submission.type == DeliberationFormats.TYPE_PROPOSITION) "Proposition : " else "Amendement : ") ; +titleOf(submission) }
        if (submission.status != DeliberationSubmissions.PENDING) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +(if (submission.status == DeliberationSubmissions.APPROVED) "Déjà approuvée et publiée." else "Déjà refusée.")
                submission.reason?.let { +" Motif : $it" }
            }
        }
        html.dl(classes = "bo-facts") {
            dt { +"Délibération" }
            dd { +(deliberation?.title ?: submission.deliberation) }
            if (submission.type == DeliberationFormats.TYPE_AMENDMENT) {
                dt { +"Proposition amendée" }
                dd { code { +submission.parent } }
            }
            dt { +"Reçue le" }
            dd { +submission.receivedDay.toString() }
            dt { +"Auteur (pseudonyme)" }
            dd { code { +runCatching { PublicKey.fromHex(submission.pubkey).npub }.getOrDefault(submission.pubkey) } }
        }
        val (text, justification) = when (submission.type) {
            DeliberationFormats.TYPE_PROPOSITION -> Proposition.parse(submission.event)?.let { it.text to it.justification }
            else -> Amendment.parse(submission.event)?.let { it.text to it.justification }
        } ?: (submission.event.content to null)
        html.h2 { +(if (submission.type == DeliberationFormats.TYPE_PROPOSITION) "Texte proposé" else "Nouvelle rédaction complète") }
        text.lines().filter { it.isNotBlank() }.forEach { line -> html.p { +line } }
        justification?.let { html.h2 { +"Justification" }; html.p { +it } }
        html.h2 { +"Relecture" }
        html.p { +"Vérifiez la charte et la faisabilité juridique, jamais les préférences de l'équipe. Une contribution approuvée est publiée telle que son auteur l'a signée." }
    }

    override fun actions(itemId: String): List<QueueAction> {
        if (submissions.find(itemId)?.status != DeliberationSubmissions.PENDING) return emptyList()
        return listOf(
            QueueAction(APPROVE, "Approuver et publier", viewPermission, sensitive = true),
            QueueAction(REJECT, "Refuser (motif montré à l'auteur)", viewPermission, reasonRequired = true, sensitive = true),
        )
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome {
        val decision = when (action) {
            APPROVE -> submissions.approve(itemId)
            REJECT -> {
                val text = reason?.trim().orEmpty()
                if (text.isEmpty() || text.length > MAX_REASON) return ActionOutcome.Refused("Un motif de 1 à $MAX_REASON caractères est obligatoire : il est montré à l'auteur.")
                submissions.reject(itemId, text)
            }
            else -> return ActionOutcome.Refused("Action inconnue.")
        }
        return when (decision) {
            DeliberationSubmissions.Decision.Done -> ActionOutcome.Done
            DeliberationSubmissions.Decision.NotFound -> ActionOutcome.NotFound
            DeliberationSubmissions.Decision.AlreadyDecided -> ActionOutcome.AlreadyDecided
            is DeliberationSubmissions.Decision.Refused -> ActionOutcome.Refused(decision.message)
        }
    }

    companion object {
        const val ID: String = "decider.deliberation"
        const val APPROVE: String = "approve"
        const val REJECT: String = "reject"
        private const val MAX_REASON = 1_000
    }
}

/** The answer of the events API to a contribution of a citizen: 202 while it waits, or the refusal. */
suspend fun DeliberationSubmissions.receive(call: ApplicationCall, eventId: String, outcome: DeliberationSubmissions.Outcome) = when (outcome) {
    DeliberationSubmissions.Outcome.Queued, DeliberationSubmissions.Outcome.AlreadyPending ->
        call.respond(HttpStatusCode.Accepted, PublishResponse(eventId, PENDING_REVIEW))
    is DeliberationSubmissions.Outcome.Refused -> call.respond(HttpStatusCode.fromValue(outcome.status), ApiError(outcome.code, outcome.message))
}

/** `GET /api/v1/deliberations/submissions/{id}`: the status of a contribution, for its author (and anyone with its id). */
fun Route.deliberationApi(submissions: DeliberationSubmissions?) {
    get("/api/v1/deliberations/submissions/{id}") {
        if (submissions == null) return@get call.respond(HttpStatusCode.ServiceUnavailable, ApiError("deliberation_unavailable", "Deliberations are not available on this server."))
        val id = call.parameters["id"]?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_submission", "No such contribution."))
        val status = withContext(Dispatchers.IO) { submissions.status(id) }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_submission", "No such contribution."))
        call.respond(status)
    }
}

private const val PENDING_REVIEW = "pending_review"
