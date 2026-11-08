package org.politisationpopulairefrancaise.server.revendications

import kotlinx.html.FlowContent
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.strong
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.revendications.ProposalStatus
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts.HeldVerdict
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeQueue
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.QueueAction
import org.politisationpopulairefrancaise.server.backoffice.QueueItem
import org.politisationpopulairefrancaise.server.backoffice.QueuePage
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.dateTime
import org.politisationpopulairefrancaise.server.decider.CompromisedAuthors
import org.politisationpopulairefrancaise.server.decider.compromisedNotice

/**
 * The queue `revendications.proposals` of the team back-office (contract of the back-office, section 7):
 * the demands proposed by citizens, waiting for the review of a person, over the existing [ProposalQueue].
 *
 * - approve: publishes the demand exactly as its author signed it (« nouvelle »: only the team moves a
 *   demand forward, by a status label of its own, D-114); sensitive (a public publication);
 * - reject: with a mandatory reason, shown to its author by `GET /api/v1/revendications/proposals/...`;
 * - no edit: the author sends a corrected version, which replaces the pending one.
 *
 * Shown to the team: the demand, its themes and territory, the findings of the automatic checks
 * (rules and, when on, the AI model: an aid, never a decision), and the pseudonymous key of its author.
 */
class ProposalsBackOfficeQueue(
    private val queue: ProposalQueue,
    /** The authors whose key was declared compromised since (S-146): what they signed after it is only refused. */
    private val authors: CompromisedAuthors = CompromisedAuthors.NONE,
) : BackOfficeQueue {
    override val id: String = ID
    override val title: String = "Revendications proposées"
    override val viewPermission: String = Permissions.queueDecide(ID)

    override fun pendingCount(): Int = queue.pending().size

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val pending = queue.pending()
        val items = pending.drop(offset).take(limit).map(::itemOf)
        val next = (offset + limit).takeIf { it < pending.size }?.toString()
        return QueuePage(items, next)
    }

    override fun render(itemId: String, html: FlowContent) {
        val proposal = queue.find(itemId)
        if (proposal == null) {
            html.h1 { +"Cette revendication n'est plus dans la file." }
            return
        }
        val event = proposal.event
        val article = Article.from(event)
        html.h1 { +(article?.title ?: UNTITLED) }
        if (proposal.status != ProposalStatus.PENDING) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +(if (proposal.status == ProposalStatus.APPROVED) "Déjà approuvée et publiée." else "Déjà refusée.")
                proposal.reason?.let { +" Motif : $it" }
            }
        }
        if (proposal.status == ProposalStatus.PENDING) html.compromisedNotice(authors.verdictOf(event))
        html.dl(classes = "bo-facts") {
            dt { +"Reçue" }
            dd { dateTime(proposal.receivedAt) }
            article?.territory?.let { territory -> dt { +"Territoire" }; dd { +territory } }
            event.firstTagValue(DemandDrafts.TAG_TERRITORY_CODE)?.let { code -> dt { +"Code du territoire" }; dd { code { +code } } }
            article?.topics?.takeIf { it.isNotEmpty() }?.let { topics -> dt { +"Thèmes" }; dd { +topics.joinToString(", ") } }
            dt { +"Auteur (pseudonyme)" }
            dd { code { +npubOf(event.pubkey) } }
        }
        article?.summary?.let { summary -> html.p { strong { +summary } } }
        // The automatic checks: what the team must look at, never a decision taken for it.
        val issues = proposal.report?.issues.orEmpty()
        html.h2 { +"Vérifications automatiques" }
        if (proposal.report == null) {
            html.p { +"Pas encore faites." }
        } else if (issues.isEmpty()) {
            html.p { +"Aucun constat." }
        } else {
            html.ul { issues.forEach { issue -> li { +"${issue.kind.name.lowercase()} (${issue.severity.name.lowercase()}, ${issue.source.name.lowercase()})${issue.field?.let { " : ${it.name.lowercase()}" } ?: ""}" } } }
        }
        html.h2 { +"Texte" }
        event.content.lines().filter { it.isNotBlank() }.forEach { line -> html.p { +line } }
    }

    override fun actions(itemId: String): List<QueueAction> {
        val proposal = queue.find(itemId) ?: return emptyList()
        if (proposal.status != ProposalStatus.PENDING) return emptyList()
        val reject = QueueAction(REJECT, "Refuser (motif montré à l'auteur)", viewPermission, reasonRequired = true, sensitive = true)
        if (authors.onlyRefusable(proposal.event)) return listOf(reject)
        return listOf(QueueAction(APPROVE, "Approuver et publier", viewPermission, sensitive = true), reject)
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome = when (action) {
        APPROVE -> if (queue.find(itemId)?.event?.let(authors::onlyRefusable) == true) {
            ActionOutcome.Refused(CompromisedAuthors.REFUSED)
        } else when (queue.approve(itemId)) {
            is DecisionOutcome.Approved -> ActionOutcome.Done
            DecisionOutcome.NotFound -> ActionOutcome.NotFound
            is DecisionOutcome.AlreadyDecided, DecisionOutcome.Rejected -> ActionOutcome.AlreadyDecided
        }
        REJECT -> {
            val text = reason?.trim().orEmpty()
            if (text.isEmpty() || text.length > ProposalQueue.MAX_REASON_LENGTH) {
                ActionOutcome.Refused("Un motif de 1 à ${ProposalQueue.MAX_REASON_LENGTH} caractères est obligatoire : il est montré à l'auteur.")
            } else {
                when (queue.reject(itemId, text)) {
                    DecisionOutcome.Rejected -> ActionOutcome.Done
                    DecisionOutcome.NotFound -> ActionOutcome.NotFound
                    else -> ActionOutcome.AlreadyDecided
                }
            }
        }
        else -> ActionOutcome.Refused("Action inconnue.")
    }

    private fun itemOf(proposal: Proposal): QueueItem {
        val article = Article.from(proposal.event)
        return QueueItem(
            id = proposal.id,
            title = article?.title?.take(200) ?: UNTITLED,
            receivedAt = proposal.receivedAt,
            summary = listOfNotNull(
                article?.territory,
                article?.topics?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                CompromisedAuthors.SHORT.takeIf { authors.verdictOf(proposal.event) != HeldVerdict.Clear },
            ).joinToString(" · ").ifEmpty { null },
        )
    }

    private fun npubOf(hex: String): String = runCatching { PublicKey.fromHex(hex).npub }.getOrDefault(hex)

    companion object {
        const val ID: String = "revendications.proposals"
        const val APPROVE: String = "approve"
        const val REJECT: String = "reject"
        private const val UNTITLED = "Revendication sans titre"
    }
}
