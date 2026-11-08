package org.politisationpopulairefrancaise.server.editorial

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.p
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeQueue
import org.politisationpopulairefrancaise.server.backoffice.QueueAction
import org.politisationpopulairefrancaise.server.backoffice.QueueItem
import org.politisationpopulairefrancaise.server.backoffice.QueuePage
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.dateTime
import org.politisationpopulairefrancaise.server.backoffice.sectionPath
import java.util.UUID

/**
 * The queue `editorial.<space>` of the editorial circuit (docs/integration/circuit-editorial.md, section 5,
 * step 4), one class for both spaces: `editorial.comprendre` now, `editorial.voir` registered by « Voir »
 * once its schemas exist (step 6). Its items are the submitted versions (`<draft uuid>:v<version>`) in review
 * or ready to sign; it gives the dashboard their counts, deadlines and levels (a correction of a published
 * content: 48 hours, level 4; otherwise 7 days, level 2).
 *
 * The decisions are taken on the review page of the section `editorial.<space>` ([EditorialDraftsSection]),
 * not by the actions of the base: the base keeps the reason of a queue action in the journal, while the
 * message to the writers and the reason of a refusal are free text that stays in `editorial_review`, the
 * journal keeping identifiers and codes only. [actions] is therefore empty and the detail links to that page.
 */
class EditorialQueue(
    private val space: EditorialSpace,
    private val reviews: EditorialReviews,
    private val pseudonymOf: (UUID) -> String? = { null },
) : BackOfficeQueue {
    override val id: String = idOf(space)
    override val title: String = if (space == EditorialSpace.COMPRENDRE) EditorialTexts.QUEUE_COMPRENDRE else EditorialTexts.QUEUE_VOIR
    override val viewPermission: String = reviewPermission(space)

    override fun pendingCount(): Int = reviews.inReview(space)

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val queued = reviews.queued(space)
        val items = queued.drop(offset).take(limit).map(::itemOf)
        return QueuePage(items, (offset + limit).takeIf { it < queued.size }?.toString())
    }

    override fun render(itemId: String, html: FlowContent) {
        val queued = find(itemId)
        if (queued == null) {
            html.h1 { +EditorialTexts.QUEUE_GONE }
            return
        }
        val draft = queued.draft
        html.h1 { +draft.title.ifBlank { EditorialTexts.UNTITLED } }
        html.dl(classes = "bo-facts") {
            dt { +EditorialTexts.COLUMN_TYPE }
            dd { +typeLabel(queued) }
            dt { +EditorialTexts.COLUMN_STATUS }
            dd { +EditorialTexts.status(draft.status) }
            dt { +EditorialTexts.VERSION }
            dd { +queued.version.toString() }
            draft.slug?.let { slug -> dt { +EditorialTexts.SLUG_LABEL }; dd { code { +slug } } }
            dt { +EditorialTexts.REVIEW_AUTHOR }
            dd { +(pseudonymOf(draft.author) ?: "") }
            dt { +EditorialTexts.REVIEW_SUBMITTED }
            dd { dateTime(queued.submittedAt) }
            if (queued.correction) {
                dt { +EditorialTexts.CHANGE_LEGEND }
                dd { +EditorialTexts.CHANGE_CORRECTION }
            }
        }
        html.p { +EditorialTexts.QUEUE_DECIDE_ON_PAGE }
        html.p { a(href = reviewPath(space, draft.id)) { +EditorialTexts.QUEUE_OPEN_REVIEW } }
    }

    /** None: see the class (decisions on the review page of the section, free text kept out of the journal). */
    override fun actions(itemId: String): List<QueueAction> = emptyList()

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome =
        ActionOutcome.Refused(EditorialTexts.QUEUE_DECIDE_ON_PAGE)

    private fun find(itemId: String): QueuedDraft? {
        val (draftId, version) = QueuedDraft.parseItemId(itemId) ?: return null
        return reviews.queued(space).firstOrNull { it.draft.id == draftId && it.version == version }
    }

    private fun itemOf(queued: QueuedDraft): QueueItem = QueueItem(
        id = queued.itemId,
        title = queued.draft.title.ifBlank { EditorialTexts.UNTITLED },
        receivedAt = queued.submittedAt.epochSecond,
        deadline = queued.deadline,
        level = queued.level,
        summary = listOfNotNull(
            typeLabel(queued),
            EditorialTexts.status(queued.draft.status).lowercase(),
            EditorialTexts.QUEUE_CORRECTION.takeIf { queued.correction },
        ).joinToString(", "),
    )

    private fun typeLabel(queued: QueuedDraft): String =
        EditorialSchemas.of(queued.draft.type)?.let { EditorialTexts.label(it.labelKey, it.type.tagValue) } ?: queued.draft.type.tagValue

    companion object {
        fun idOf(space: EditorialSpace): String = "editorial.${space.id}"

        /** Request changes and reject (role editor, already in [org.politisationpopulairefrancaise.server.backoffice.TeamRole.EDITOR]). */
        fun reviewPermission(space: EditorialSpace): String = "queue.${idOf(space)}.review"

        /** Approve for publication (role editor). */
        fun publishPermission(space: EditorialSpace): String = "queue.${idOf(space)}.publish"

        /** The review page of draft [draftId] in the section of [space]. */
        fun reviewPath(space: EditorialSpace, draftId: UUID): String = sectionPath(idOf(space), "relecture/$draftId")
    }
}
