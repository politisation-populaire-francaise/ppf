package org.politisationpopulairefrancaise.server.agir.testimonies

import kotlinx.html.FlowContent
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.p
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeQueue
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTexts
import org.politisationpopulairefrancaise.server.backoffice.Permissions
import org.politisationpopulairefrancaise.server.backoffice.QueueAction
import org.politisationpopulairefrancaise.server.backoffice.QueueItem
import org.politisationpopulairefrancaise.server.backoffice.QueuePage
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.dateTime

/**
 * The queue `agir.testimonies` of the team back-office (contract docs/integration/back-office.md,
 * section 7): the testimonies waiting for review, over [TestimonySpace].
 *
 * The server cannot read a testimony: it is encrypted on the device for the key of the moderation team.
 * The queue therefore shows only what the server knows (date of reception rounded to the hour, number of
 * encrypted files, whether an original was entrusted to the lawyers) and offers one action, a refusal with
 * a mandatory reason (shown to the witness by `POST /api/v1/testimonies/status`). Reading and publishing
 * the public version wait for the team client, which decrypts on the team's own devices and has the public
 * version signed by an editorial key outside the server (see [TestimonySpace.approve]); meanwhile, in
 * development, `/dev/testimonies` stands in for it. The base writes the journal of every action, never this queue.
 */
class TestimonyQueue(private val space: TestimonySpace) : BackOfficeQueue {
    override val id: String = ID
    override val title: String = TITLE
    override val viewPermission: String = Permissions.queueDecide(ID)

    override fun pendingCount(): Int = space.pending().size

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val pending = space.pending()
        val items = pending.drop(offset).take(limit).map { testimony ->
            QueueItem(id = testimony.id, title = ITEM_TITLE, receivedAt = testimony.receivedAt, summary = summaryOf(testimony))
        }
        return QueuePage(items, (offset + limit).takeIf { it < pending.size }?.toString())
    }

    override fun render(itemId: String, html: FlowContent) {
        val testimony = space.find(itemId)
        if (testimony == null) {
            html.h1 { +GONE }
            return
        }
        html.h1 { +ITEM_TITLE }
        html.div(classes = "bo-notice bo-notice-warning") { +UNREADABLE }
        if (testimony.status != HeldStatus.PENDING) {
            html.div(classes = "bo-notice") {
                attributes["role"] = "status"
                +(if (testimony.status == HeldStatus.APPROVED) PUBLISHED else REJECTED)
                testimony.reason?.let { +" ${BackOfficeTexts.REASON_LABEL} : $it" }
            }
        }
        html.dl(classes = "bo-facts") {
            dt { +BackOfficeTexts.RECEIVED }
            dd { dateTime(testimony.receivedAt) }
            dt { +FILES }
            dd { +filesLabel(testimony.files.size) }
            dt { +ORIGINAL }
            dd { +(if (testimony.evidenceId != null) ORIGINAL_GIVEN else ORIGINAL_NOT_GIVEN) }
        }
        html.p { +HOW_TO_PUBLISH }
    }

    override fun actions(itemId: String): List<QueueAction> {
        val testimony = space.find(itemId) ?: return emptyList()
        if (testimony.status != HeldStatus.PENDING) return emptyList()
        return listOf(QueueAction(REJECT, REJECT_LABEL, viewPermission, reasonRequired = true, sensitive = true))
    }

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome {
        if (action != REJECT) return ActionOutcome.Refused(BackOfficeTexts.error("unknown_action"))
        val text = reason?.trim().orEmpty()
        if (text.isEmpty() || text.length > TestimonySpace.MAX_REASON_LENGTH) return ActionOutcome.Refused(BackOfficeTexts.error("reason_required"))
        return when (space.reject(itemId, text)) {
            DecisionResult.Rejected -> ActionOutcome.Done
            DecisionResult.NotFound -> ActionOutcome.NotFound
            else -> ActionOutcome.AlreadyDecided
        }
    }

    private fun summaryOf(testimony: StoredTestimony): String =
        listOfNotNull(filesLabel(testimony.files.size), ORIGINAL_GIVEN.takeIf { testimony.evidenceId != null }).joinToString(", ")

    private fun filesLabel(count: Int): String = when (count) {
        0 -> "Aucune photo"
        1 -> "1 photo chiffrée"
        else -> "$count photos chiffrées"
    }

    companion object {
        const val ID: String = "agir.testimonies"
        const val REJECT: String = "reject"

        const val TITLE: String = "Agir : témoignages en relecture"
        const val ITEM_TITLE: String = "Témoignage chiffré"
        const val GONE: String = "Ce témoignage n’est plus dans la file (décidé, effacé selon les durées de conservation, ou inconnu)."
        const val UNREADABLE: String =
            "Le serveur ne peut pas lire ce témoignage : il est chiffré sur l’appareil du témoin pour la clé de l’équipe de modération. " +
                "Seules les informations ci-dessous sont connues du serveur."
        const val HOW_TO_PUBLISH: String =
            "La lecture et la publication de la version publique se feront avec le client de l’équipe, qui déchiffre sur vos appareils " +
                "et fait signer la version publique par une clé éditoriale. Ici, vous pouvez seulement refuser, avec un motif qui sera montré au témoin."
        const val FILES: String = "Photos"
        const val ORIGINAL: String = "Original pour les avocats"
        const val ORIGINAL_GIVEN: String = "Original confié aux avocats"
        const val ORIGINAL_NOT_GIVEN: String = "Non confié"
        const val PUBLISHED: String = "Ce témoignage a été publié."
        const val REJECTED: String = "Ce témoignage a été refusé."
        const val REJECT_LABEL: String = "Refuser le témoignage"
    }
}
