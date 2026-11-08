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
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.groups.GroupOrphan
import org.politisationpopulairefrancaise.server.groups.GroupOrphans
import java.time.Instant

/**
 * The queue `groups.orphans` (S-146, decision of the team lead of 8 November 2026, design
 * docs/integration/salons-reprise.md): the public rooms whose only administrator was declared
 * compromised, and so left without one.
 *
 * Shown to the team, nothing more than the decision needs: the room, since when and why it has no
 * administrator, the moderators in place, the successor the notice names (a claim of its former
 * owner, never a proof of identity), and how many messages it had in the last 30 days. Never a list
 * of members: there is none.
 *
 * - leave: leaves the room without administrator and closes the item; two people, a reason.
 * - Naming an administrator (a moderator in place, the successor, a key typed by the team) comes with
 *   the platform signing use `groups.takeover` (contract of the signing service 1.3): a 9000 signed by
 *   the platform and approved on the page of the service by a second person.
 */
class GroupOrphansQueue(
    private val orphans: GroupOrphans,
    private val directory: GroupDirectory,
    private val compromised: CompromisedAccounts,
    private val clock: () -> Instant = Instant::now,
) : BackOfficeQueue {
    override val id: String = ID
    override val title: String = TITLE
    override val viewPermission: String = Permissions.queueDecide(ID)

    override fun pendingCount(): Int = orphans.pendingCount()

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val pending = orphans.pending()
        val items = pending.drop(offset).take(limit).map(::itemOf)
        val next = (offset + limit).takeIf { it < pending.size }?.toString()
        return QueuePage(items, next)
    }

    override fun render(itemId: String, html: FlowContent) {
        val orphan = orphans.find(itemId)
        if (orphan == null) {
            html.h1 { +GONE }
            return
        }
        val group = directory.find(orphan.groupId)
        html.h1 { +(group?.name ?: orphan.groupId) }
        if (!orphan.pending) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +(if (orphan.decision == GroupOrphans.LEAVE) ALREADY_LEFT else ALREADY_TAKEN)
            }
        } else if (orphans.hasAdministrator(orphan.groupId)) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +HAS_ADMIN_AGAIN
            }
        }
        val moderators = directory.moderation.roles(orphan.groupId).roles.filter { it.role == MODERATOR }
        val successor = compromised.successorOf(orphan.compromisedPubkey)
        val since = clock().epochSecond - ACTIVITY_DAYS * 86_400
        val messages = orphans.recentMessages(orphan.groupId, since)
        html.dl(classes = "bo-facts") {
            dt { +TERRITORY }
            dd { +(group?.territory ?: "") }
            dt { +ROOM_ID }
            dd { code { +orphan.groupId } }
            dt { +WITHOUT_ADMIN_SINCE }
            dd { dateTime(orphan.orphanedAt) }
            dt { +CAUSE }
            dd {
                +CAUSE_COMPROMISED
                +" "
                code { +npubOf(orphan.compromisedPubkey) }
                +" ($NOTICE "
                code { +orphan.noticeId }
                +")"
            }
            dt { +MODERATORS }
            dd {
                if (moderators.isEmpty()) {
                    +NONE
                } else {
                    ul { moderators.forEach { role -> li { code { +npubOf(role.pubkey) } } } }
                }
            }
            dt { +SUCCESSOR }
            dd {
                when {
                    successor == null -> +NONE
                    compromised.isCompromised(successor) -> {
                        code { +npubOf(successor) }
                        +" $SUCCESSOR_COMPROMISED"
                    }
                    else -> {
                        code { +npubOf(successor) }
                        +" $SUCCESSOR_CLAIM"
                    }
                }
            }
            dt { +ACTIVITY }
            dd { +activityLabel(messages) }
        }
        html.p(classes = "bo-hint") { +TAKEOVER_LATER }
    }

    override fun actions(itemId: String): List<QueueAction> {
        val orphan = orphans.find(itemId) ?: return emptyList()
        if (!orphan.pending) return emptyList()
        return listOf(QueueAction(LEAVE, LEAVE_LABEL, viewPermission, reasonRequired = true, sensitive = true, twoPerson = true))
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome = when (action) {
        LEAVE -> when {
            orphans.find(itemId) == null -> ActionOutcome.NotFound
            orphans.decide(itemId, GroupOrphans.LEAVE) -> ActionOutcome.Done
            orphans.find(itemId)?.pending == true -> ActionOutcome.Refused(HAS_ADMIN_AGAIN)
            else -> ActionOutcome.AlreadyDecided
        }
        else -> ActionOutcome.Refused(BackOfficeTexts.error("unknown_action"))
    }

    private fun itemOf(orphan: GroupOrphan): QueueItem {
        val group = directory.find(orphan.groupId)
        return QueueItem(
            id = orphan.groupId,
            title = group?.name ?: orphan.groupId,
            receivedAt = orphan.orphanedAt,
            summary = listOfNotNull(group?.territory, CAUSE_SHORT).joinToString(", "),
        )
    }

    private fun npubOf(hex: String): String = runCatching { PublicKey.fromHex(hex).npub }.getOrDefault(hex)

    companion object {
        const val ID: String = "groups.orphans"
        const val LEAVE: String = "leave"
        const val MODERATOR: String = "moderator"
        const val ACTIVITY_DAYS: Long = 30

        // French texts of the queue, shown to the team.
        const val TITLE: String = "Salons sans administrateur"
        const val GONE: String = "Ce salon n’est pas dans la file."
        const val ALREADY_LEFT: String = "L’équipe a décidé de laisser ce salon sans administrateur."
        const val ALREADY_TAKEN: String = "Un administrateur a été nommé par l’équipe."
        const val HAS_ADMIN_AGAIN: String = "Ce salon a de nouveau un administrateur."
        const val TERRITORY: String = "Territoire"
        const val ROOM_ID: String = "Identifiant"
        const val WITHOUT_ADMIN_SINCE: String = "Sans administrateur depuis le"
        const val CAUSE: String = "Pourquoi"
        const val CAUSE_COMPROMISED: String = "Son seul administrateur a été déclaré compromis :"
        const val CAUSE_SHORT: String = "administrateur déclaré compromis"
        const val NOTICE: String = "avis"
        const val MODERATORS: String = "Modérateurs en place"
        const val NONE: String = "Aucun"
        const val SUCCESSOR: String = "Nouveau compte annoncé par l’avis"
        const val SUCCESSOR_CLAIM: String = "(affirmation de son ancien titulaire, pas une preuve d’identité)"
        const val SUCCESSOR_COMPROMISED: String = "(lui-même déclaré compromis : à écarter)"
        const val ACTIVITY: String = "Messages des 30 derniers jours"
        const val TAKEOVER_LATER: String =
            "Nommer un administrateur (un modérateur en place, le nouveau compte annoncé ou un compte qui l’a demandé) " +
                "sera possible ici avec la signature de l’équipe, approuvée par une deuxième personne sur la page du service de signature."
        const val LEAVE_LABEL: String = "Laisser ce salon sans administrateur"

        fun activityLabel(messages: Int): String =
            if (messages >= GroupOrphans.ACTIVITY_CAP) "${GroupOrphans.ACTIVITY_CAP} et plus" else messages.toString()
    }
}
