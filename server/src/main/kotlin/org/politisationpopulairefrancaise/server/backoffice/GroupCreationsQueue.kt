package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.html.FlowContent
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts.HeldVerdict
import org.politisationpopulairefrancaise.server.groups.Group
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.HeldCreation
import org.politisationpopulairefrancaise.server.groups.HoldCause

/**
 * The queue `groups.creations` (D-132, contract salons-roles.md, section 3 bis): the rooms whose name
 * resembles a verified organisation's, or a very active room of another territory, wait for the team.
 *
 * - approve: publishes the description exactly as its creator signed it, unless a room of the same
 *   name has appeared on the territory meanwhile, or its id has been taken (the refusal is shown);
 * - reject: with a mandatory reason, shown to the creator by `GET /api/v1/group-creations`.
 * Both are sensitive (a public publication, or a refusal shown to someone).
 *
 * Shown to the team: the description, what it resembles and the pseudonymous key of its creator.
 * When that key has since been declared compromised (S-146), a creation signed on or after
 * "compromised since" can only be refused; one signed before stays, with a visible mention.
 */
class GroupCreationsQueue(
    private val directory: GroupDirectory,
    private val compromised: CompromisedAccounts? = null,
) : BackOfficeQueue {
    override val id: String = ID
    override val title: String = TITLE
    override val viewPermission: String = Permissions.queueDecide(ID)

    private val creations get() = directory.guardrails.creations

    override fun pendingCount(): Int = creations.pendingCount()

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val pending = creations.pending()
        val items = pending.drop(offset).take(limit).map(::itemOf)
        val next = (offset + limit).takeIf { it < pending.size }?.toString()
        return QueuePage(items, next)
    }

    override fun render(itemId: String, html: FlowContent) {
        val held = creations.find(itemId)
        if (held == null) {
            html.h1 { +GONE }
            return
        }
        val group = Group.from(held.event)
        html.h1 { +(group?.name ?: held.event.tagValues("d").firstOrNull().orEmpty()) }
        if (!held.pending) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +(if (held.status == HeldCreation.APPROVED) ALREADY_APPROVED else ALREADY_REJECTED)
                held.reason?.let { +" ${BackOfficeTexts.REASON_LABEL} : $it" }
            }
        }
        when (val verdict = verdictOf(held)) {
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
        html.dl(classes = "bo-facts") {
            dt { +CAUSE }
            dd { +causeLabel(held.cause) }
            dt { +RESEMBLES }
            dd { ul { held.resembles.forEach { li { +it } } } }
            dt { +TERRITORY }
            dd { +(group?.territory ?: "") }
            dt { +ROOM_ID }
            dd { code { +(group?.id ?: "") } }
            dt { +BackOfficeTexts.RECEIVED }
            dd { dateTime(held.receivedAt) }
            dt { +CREATOR }
            dd { code { +npubOf(held.event.pubkey) } }
            held.event.firstTagValue(GroupKinds.DIFFERENCE_TAG)?.let { difference ->
                dt { +DIFFERENCE }
                dd { +difference }
            }
        }
        group?.about?.lines()?.filter { it.isNotBlank() }?.forEach { line -> html.p { +line } }
    }

    override fun actions(itemId: String): List<QueueAction> {
        val held = creations.find(itemId) ?: return emptyList()
        if (!held.pending) return emptyList()
        val reject = QueueAction(REJECT, REJECT_LABEL, viewPermission, reasonRequired = true, sensitive = true)
        if (verdictOf(held) is HeldVerdict.CreatedAfter) return listOf(reject)
        return listOf(
            QueueAction(APPROVE, APPROVE_LABEL, viewPermission, sensitive = true),
            QueueAction(REJECT, REJECT_LABEL, viewPermission, reasonRequired = true, sensitive = true),
        )
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome = when (action) {
        APPROVE -> if (creations.find(itemId)?.let(::verdictOf) is HeldVerdict.CreatedAfter) {
            ActionOutcome.Refused(REFUSED_COMPROMISED)
        } else when (val decision = directory.approveCreation(itemId)) {
            GroupDirectory.CreationDecision.Done -> ActionOutcome.Done
            GroupDirectory.CreationDecision.NotFound -> ActionOutcome.NotFound
            GroupDirectory.CreationDecision.AlreadyDecided -> ActionOutcome.AlreadyDecided
            is GroupDirectory.CreationDecision.Refused -> ActionOutcome.Refused(refusalLabel(decision.error))
        }
        REJECT -> {
            val text = reason?.trim().orEmpty()
            if (text.isEmpty() || text.length > GroupKinds.MAX_REASON_LENGTH) {
                ActionOutcome.Refused(BackOfficeTexts.error("reason_required"))
            } else {
                when (directory.rejectCreation(itemId, text)) {
                    GroupDirectory.CreationDecision.Done -> ActionOutcome.Done
                    GroupDirectory.CreationDecision.NotFound -> ActionOutcome.NotFound
                    else -> ActionOutcome.AlreadyDecided
                }
            }
        }
        else -> ActionOutcome.Refused(BackOfficeTexts.error("unknown_action"))
    }

    private fun verdictOf(held: HeldCreation): HeldVerdict =
        compromised?.heldVerdict(held.event.pubkey, held.event.createdAt) ?: HeldVerdict.Clear

    private fun itemOf(held: HeldCreation): QueueItem {
        val group = Group.from(held.event)
        val mention = if (verdictOf(held) == HeldVerdict.Clear) null else COMPROMISED_SHORT
        return QueueItem(
            id = held.id,
            title = group?.name ?: held.id,
            receivedAt = held.receivedAt,
            summary = listOfNotNull(causeLabel(held.cause), group?.territory, mention).joinToString(", "),
        )
    }

    private fun npubOf(hex: String): String = runCatching { PublicKey.fromHex(hex).npub }.getOrDefault(hex)

    companion object {
        const val ID: String = "groups.creations"
        const val APPROVE: String = "approve"
        const val REJECT: String = "reject"

        // French texts of the queue, shown to the team.
        const val TITLE: String = "Salons en attente de vérification"
        const val GONE: String = "Cette création de salon n’existe plus dans la file."
        const val ALREADY_APPROVED: String = "Ce salon a déjà été approuvé."
        const val ALREADY_REJECTED: String = "Cette création a déjà été refusée."
        const val CAUSE: String = "Pourquoi il attend"
        const val RESEMBLES: String = "Nom proche de"
        const val TERRITORY: String = "Territoire"
        const val ROOM_ID: String = "Identifiant"
        const val CREATOR: String = "Créateur (pseudonyme)"
        const val DIFFERENCE: String = "En quoi ce salon est différent (selon son créateur)"
        const val APPROVE_LABEL: String = "Approuver et publier le salon"
        const val REJECT_LABEL: String = "Refuser"
        const val CAUSE_ORGANISATION: String = "Nom proche d’une organisation vérifiée"
        const val CAUSE_ACTIVE_ROOM: String = "Nom proche d’un salon très actif d’un autre territoire"
        const val REFUSED_EXISTS: String = "Un salon du même nom existe déjà sur ce territoire : refusez cette création en indiquant lequel rejoindre."
        const val REFUSED_TAKEN: String = "L’identifiant de ce salon a été pris entre-temps : refusez cette création, son auteur en créera une autre."
        const val COMPROMISED_SHORT: String = "compte du créateur déclaré compromis"
        const val COMPROMISED_AFTER: String = "Créé après que le compte de son créateur a été déclaré compromis : ce salon ne peut qu’être refusé. Compromis depuis le"
        const val COMPROMISED_BEFORE: String = "Le compte de son créateur a été déclaré compromis, mais ce salon a été créé avant. Compromis depuis le"
        const val REFUSED_COMPROMISED: String = "Ce salon a été créé après la compromission du compte de son créateur : refusez-le avec un motif."
        const val REFUSED_OTHER: String = "Ce salon ne peut plus être publié tel quel : refusez cette création avec un motif."

        fun causeLabel(cause: HoldCause): String = when (cause) {
            HoldCause.ORGANISATION -> CAUSE_ORGANISATION
            HoldCause.ACTIVE_ROOM -> CAUSE_ACTIVE_ROOM
        }

        fun refusalLabel(error: String): String = when (error) {
            "group_exists" -> REFUSED_EXISTS
            "group_taken" -> REFUSED_TAKEN
            else -> REFUSED_OTHER
        }
    }
}
