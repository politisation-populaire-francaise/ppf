package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.html.FlowContent
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.html.strong
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts.HeldVerdict
import org.politisationpopulairefrancaise.server.agir.AgirCategory
import org.politisationpopulairefrancaise.server.agir.AgirRules
import org.politisationpopulairefrancaise.server.agir.AgirSpace
import org.politisationpopulairefrancaise.server.agir.HeldContent
import org.politisationpopulairefrancaise.server.agir.HeldDecision
import org.politisationpopulairefrancaise.server.agir.HeldQueue
import org.politisationpopulairefrancaise.web.mobilizationTypeLabels

/**
 * The queue `agir.held` (contract, section 7): the mobilizations and calls of the Agir space waiting for
 * review, over the existing [HeldQueue]. Plugged by the base as the example for the poles.
 *
 * - approve: publishes the event exactly as its author signed it (the replacement rule of the versions
 *   applies, [AgirSpace.approve]); sensitive (a public publication), so the key is asked again after 5 minutes;
 * - reject: with a mandatory reason, shown to its author by `GET /api/v1/held`; sensitive too (D-127);
 * - a mobilization whose date has passed is refused by [HeldQueue] itself right before an approval: the
 *   refusal is shown to the team ([HeldQueue.EXPIRED_REASON]);
 * - a call for witnesses says that it is always reviewed, whoever its author;
 * - no edit: the author publishes a new version.
 *
 * Shown to the team: the content in question and the pseudonymous key of its author, nothing else.
 * When that key has since been declared compromised (S-146, decision of the team lead of 8 November 2026),
 * a content signed on or after "compromised since" can only be refused; one signed before stays, with a
 * visible mention.
 */
class AgirHeldQueue(
    private val space: AgirSpace,
    private val compromised: CompromisedAccounts? = null,
) : BackOfficeQueue {
    override val id: String = ID
    override val title: String = BackOfficeTexts.AGIR_HELD_TITLE
    override val viewPermission: String = Permissions.queueDecide(ID)

    override fun pendingCount(): Int = space.queue.pending().size

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val pending = space.queue.pending()
        val items = pending.drop(offset).take(limit).map(::itemOf)
        val next = (offset + limit).takeIf { it < pending.size }?.toString()
        return QueuePage(items, next)
    }

    override fun render(itemId: String, html: FlowContent) {
        val held = space.queue.find(itemId)
        if (held == null) {
            html.h1 { +BackOfficeTexts.AGIR_HELD_GONE }
            return
        }
        val event = held.event
        html.h1 { +(event.firstTagValue(AgirFormats.TAG_TITLE) ?: BackOfficeTexts.AGIR_UNTITLED) }
        when (val verdict = verdictOf(event)) {
            is HeldVerdict.CreatedAfter -> html.div(classes = "bo-notice") {
                attributes["role"] = "alert"
                +COMPROMISED_AFTER
                +" "
                dateTime(verdict.since)
            }
            is HeldVerdict.CreatedBefore -> html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +COMPROMISED_BEFORE
                +" "
                dateTime(verdict.since)
            }
            HeldVerdict.Clear -> Unit
        }
        if (AgirRules.callTypeOf(event) == AgirFormats.CALL_TYPE_WITNESSES) {
            html.div(classes = "bo-notice bo-notice-warning") { +BackOfficeTexts.AGIR_WITNESSES_ALWAYS_REVIEWED }
        }
        if (AgirRules.callTypeOf(event) == AgirFormats.CALL_TYPE_BLOCKADE) {
            html.div(classes = "bo-notice bo-notice-warning") { +BackOfficeTexts.AGIR_BLOCKADE_ALWAYS_REVIEWED }
        }
        if (held.status != HeldStatus.PENDING) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +(if (held.status == HeldStatus.APPROVED) BackOfficeTexts.AGIR_ALREADY_APPROVED else BackOfficeTexts.AGIR_ALREADY_REJECTED)
                held.reason?.let { +" ${BackOfficeTexts.REASON_LABEL} : $it" }
            }
        }
        html.dl(classes = "bo-facts") {
            dt { +BackOfficeTexts.AGIR_KIND }
            dd { +kindLabel(event) }
            dt { +BackOfficeTexts.RECEIVED }
            dd { dateTime(held.receivedAt) }
            event.firstTagValue(AgirFormats.TAG_START)?.toLongOrNull()?.let { start ->
                dt { +BackOfficeTexts.AGIR_START }
                dd { dateTime(start) }
            }
            event.firstTagValue(AgirFormats.TAG_END)?.toLongOrNull()?.let { end ->
                dt { +BackOfficeTexts.AGIR_END }
                dd { dateTime(end) }
            }
            event.firstTagValue(AgirFormats.TAG_DEADLINE)?.toLongOrNull()?.let { deadline ->
                dt { +BackOfficeTexts.DEADLINE }
                dd { dateTime(deadline) }
            }
            event.firstTagValue(AgirFormats.TAG_LOCATION)?.let { place -> dt { +BackOfficeTexts.AGIR_LOCATION }; dd { +place } }
            event.firstTagValue(AgirFormats.TAG_TERRITORY)?.let { territory -> dt { +BackOfficeTexts.AGIR_TERRITORY }; dd { +territory } }
            event.tagValues(AgirFormats.TAG_LINK).takeIf { it.isNotEmpty() }?.let { links ->
                dt { +BackOfficeTexts.AGIR_LINKS }
                // Shown as text, never as links: the team checks an address before anyone follows it.
                links.forEach { link -> dd { code { +link } } }
            }
            dt { +BackOfficeTexts.AGIR_AUTHOR }
            dd { code { +npubOf(event.pubkey) } }
        }
        event.firstTagValue(AgirFormats.TAG_SUMMARY)?.let { summary -> html.p { strong { +summary } } }
        event.content.lines().filter { it.isNotBlank() }.forEach { line -> html.p { +line } }
    }

    override fun actions(itemId: String): List<QueueAction> {
        val held = space.queue.find(itemId) ?: return emptyList()
        if (held.status != HeldStatus.PENDING) return emptyList()
        val reject = QueueAction(REJECT, BackOfficeTexts.AGIR_REJECT, viewPermission, reasonRequired = true, sensitive = true)
        if (verdictOf(held.event) is HeldVerdict.CreatedAfter) return listOf(reject)
        return listOf(QueueAction(APPROVE, BackOfficeTexts.AGIR_APPROVE, viewPermission, sensitive = true), reject)
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome = when (action) {
        APPROVE -> if (space.queue.find(itemId)?.event?.let(::verdictOf) is HeldVerdict.CreatedAfter) {
            ActionOutcome.Refused(REFUSED_COMPROMISED)
        } else when (val decision = space.approve(itemId)) {
            is HeldDecision.Approved -> ActionOutcome.Done
            HeldDecision.NotFound -> ActionOutcome.NotFound
            is HeldDecision.AlreadyDecided -> {
                // The queue refuses a past mobilization right before the approval: its reason is shown.
                val held = space.queue.find(itemId)
                if (decision.status == HeldStatus.REJECTED && held?.reason == HeldQueue.EXPIRED_REASON) ActionOutcome.Refused(HeldQueue.EXPIRED_REASON) else ActionOutcome.AlreadyDecided
            }
            HeldDecision.Rejected -> ActionOutcome.AlreadyDecided
        }
        REJECT -> {
            val text = reason?.trim().orEmpty()
            if (text.isEmpty() || text.length > HeldQueue.MAX_REASON_LENGTH) {
                ActionOutcome.Refused(BackOfficeTexts.error("reason_required"))
            } else {
                when (space.reject(itemId, text)) {
                    HeldDecision.Rejected -> ActionOutcome.Done
                    HeldDecision.NotFound -> ActionOutcome.NotFound
                    else -> ActionOutcome.AlreadyDecided
                }
            }
        }
        else -> ActionOutcome.Refused(BackOfficeTexts.error("unknown_action"))
    }

    private fun itemOf(held: HeldContent): QueueItem {
        val event = held.event
        val start = event.firstTagValue(AgirFormats.TAG_START)?.toLongOrNull()
        val deadline = if (AgirRules.categoryOf(event) == AgirCategory.MOBILIZATION) start else event.firstTagValue(AgirFormats.TAG_DEADLINE)?.toLongOrNull()
        return QueueItem(
            id = held.id,
            title = event.firstTagValue(AgirFormats.TAG_TITLE) ?: BackOfficeTexts.AGIR_UNTITLED,
            receivedAt = held.receivedAt,
            deadline = deadline,
            summary = listOfNotNull(kindLabel(event), event.firstTagValue(AgirFormats.TAG_TERRITORY), COMPROMISED_SHORT.takeIf { verdictOf(event) != HeldVerdict.Clear }).joinToString(", "),
        )
    }

    private fun kindLabel(event: SignedEvent): String = when (AgirRules.categoryOf(event)) {
        AgirCategory.MOBILIZATION -> BackOfficeTexts.AGIR_MOBILIZATION + (event.firstTagValue(AgirFormats.TAG_EVENT_TYPE)?.let { " (${mobilizationTypeLabels[it] ?: it})" } ?: "")
        AgirCategory.CALL -> BackOfficeTexts.AGIR_CALL + (AgirRules.callTypeOf(event)?.let { " (${BackOfficeTexts.callType(it)})" } ?: "")
        else -> BackOfficeTexts.AGIR_OTHER
    }

    private fun verdictOf(event: SignedEvent): HeldVerdict = compromised?.heldVerdict(event.pubkey, event.createdAt) ?: HeldVerdict.Clear

    private fun npubOf(hex: String): String = runCatching { PublicKey.fromHex(hex).npub }.getOrDefault(hex)

    companion object {
        const val ID: String = "agir.held"
        const val APPROVE: String = "approve"
        const val REJECT: String = "reject"

        // French texts of the compromised accounts (S-146), shown to the team.
        const val COMPROMISED_SHORT: String = "compte de l’auteur déclaré compromis"
        const val COMPROMISED_AFTER: String = "Signé après que le compte de son auteur a été déclaré compromis : ce contenu ne peut qu’être refusé. Compromis depuis le"
        const val COMPROMISED_BEFORE: String = "Le compte de son auteur a été déclaré compromis, mais ce contenu a été signé avant. Compromis depuis le"
        const val REFUSED_COMPROMISED: String = "Ce contenu a été signé après la compromission du compte de son auteur : refusez-le avec un motif."
    }
}
