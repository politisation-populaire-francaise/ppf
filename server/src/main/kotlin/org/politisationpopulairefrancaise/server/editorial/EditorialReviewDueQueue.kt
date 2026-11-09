package org.politisationpopulairefrancaise.server.editorial

import io.ktor.http.encodeURLParameter
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.p
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ReviewDates
import org.politisationpopulairefrancaise.core.editorial.ReviewStatus
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialDates
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeQueue
import org.politisationpopulairefrancaise.server.backoffice.QueueAction
import org.politisationpopulairefrancaise.server.backoffice.QueueItem
import org.politisationpopulairefrancaise.server.backoffice.QueuePage
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.sectionPath
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The follow-up queue « À revoir » of an editorial section (`editorial.<space>.a_revoir`; proposal of the
 * Directeur technique, approved by the team lead on 9 November 2026): the published contents of the space whose
 * review date ([ReviewDates], tag `ppf-review-by`) is past or falls within [ReviewDates.WINDOW_DAYS] days, the
 * oldest date first. The date is the deadline of the item, so the dashboard puts the late ones first; a past
 * date has level 3, a coming one level 2. Nothing is stored: the list is read from the published contents.
 *
 * The only action is to open an update draft: the detail links to the page of the section that creates it
 * (with the form token of the section). A review that changes nothing still publishes a new version with a new
 * date, shown as « mise à jour » in the public journal of corrections (D-095); the date never hides a content.
 */
class EditorialReviewDueQueue(
    private val space: EditorialSpace,
    private val published: PublishedContents,
    private val clock: Clock = Clock.systemUTC(),
) : BackOfficeQueue {
    override val id: String = idOf(space)
    override val title: String = if (space == EditorialSpace.COMPRENDRE) EditorialTexts.REVIEW_DUE_COMPRENDRE else EditorialTexts.REVIEW_DUE_VOIR

    /** The editors of the space, who already see its review queue. */
    override val viewPermission: String = EditorialQueue.reviewPermission(space)

    override fun pendingCount(): Int = due().size

    override fun page(cursor: String?, limit: Int): QueuePage {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val all = due()
        val items = all.drop(offset).take(limit).map(::itemOf)
        return QueuePage(items, (offset + limit).takeIf { it < all.size }?.toString())
    }

    override fun render(itemId: String, html: FlowContent) {
        val item = due().firstOrNull { it.slug == itemId }
        if (item == null) {
            html.h1 { +EditorialTexts.REVIEW_DUE_GONE }
            return
        }
        html.h1 { +item.title }
        html.dl(classes = "bo-facts") {
            dt { +EditorialTexts.COLUMN_TYPE }
            dd { +typeLabel(item.event) }
            dt { +EditorialTexts.SLUG_LABEL }
            dd { code { +item.slug } }
            dt { +EditorialTexts.REVIEW_DUE_DATE }
            dd { +item.date.toFrench() }
            dt { +EditorialTexts.REVIEW_DUE_STATE }
            dd { +stateText(item.status) }
            dt { +EditorialTexts.REVIEW_DUE_UPDATED }
            dd { +CivilDate.fromEpochSeconds(item.event.createdAt).toFrench() }
        }
        html.p { +EditorialTexts.REVIEW_DUE_HOW }
        html.p { a(href = updatePath(space, item.slug)) { +EditorialTexts.REVIEW_DUE_OPEN } }
    }

    /** None: the update draft is opened on the page of the section (see the class). */
    override fun actions(itemId: String): List<QueueAction> = emptyList()

    override fun perform(itemId: String, action: String, reason: String?, actor: TeamActor): ActionOutcome =
        ActionOutcome.Refused(EditorialTexts.REVIEW_DUE_ON_PAGE)

    /** One published content to review: its event, its slug, its title, its date and where it stands. */
    private class Due(val event: SignedEvent, val slug: String, val title: String, val date: CivilDate, val status: ReviewStatus)

    /** The contents due on the day of Paris, the oldest review date first, then by title. */
    private fun due(): List<Due> {
        val today = LocalDate.now(clock.withZone(PARIS)).let { CivilDate(it.year, it.monthValue, it.dayOfMonth) }
        return published.snapshot().ofSpace(space).mapNotNull { event ->
            val date = ReviewDates.of(event) ?: return@mapNotNull null
            val slug = event.firstTagValue("d") ?: return@mapNotNull null
            if (!ReviewDates.isDue(date, today)) return@mapNotNull null
            val title = event.firstTagValue(Article.TAG_TITLE).orEmpty().ifBlank { EditorialTexts.UNTITLED }
            Due(event, slug, title, date, ReviewDates.status(date, today))
        }.sortedWith(compareBy<Due> { it.date }.thenBy { it.title.lowercase() })
    }

    private fun itemOf(due: Due): QueueItem = QueueItem(
        id = due.slug,
        title = due.title,
        receivedAt = due.event.createdAt,
        deadline = EditorialDates.noonUtc(due.date.toIso()),
        level = if (due.status is ReviewStatus.Overdue) LEVEL_OVERDUE else LEVEL_SOON,
        summary = "${typeLabel(due.event)}, ${stateText(due.status)}",
    )

    private fun stateText(status: ReviewStatus): String = when (status) {
        is ReviewStatus.Overdue -> EditorialTexts.format(EditorialTexts.REVIEW_DUE_LATE, status.daysLate)
        is ReviewStatus.Soon -> if (status.daysLeft == 0L) EditorialTexts.REVIEW_DUE_TODAY else EditorialTexts.format(EditorialTexts.REVIEW_DUE_SOON, status.daysLeft)
        is ReviewStatus.Later, ReviewStatus.None -> ""
    }

    private fun typeLabel(event: SignedEvent): String {
        val type = event.firstTagValue(Article.TAG_TYPE).orEmpty()
        return EditorialSchemas.of(space).firstOrNull { it.type.tagValue == type }?.let { EditorialTexts.label(it.labelKey, type) } ?: type
    }

    companion object {
        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
        const val LEVEL_OVERDUE: Int = 3
        const val LEVEL_SOON: Int = 2

        fun idOf(space: EditorialSpace): String = "${EditorialQueue.idOf(space)}.a_revoir"

        /** The page of the section that opens an update draft of [slug] (GET; the creation itself is a POST with the form token). */
        fun updatePath(space: EditorialSpace, slug: String): String =
            sectionPath(EditorialQueue.idOf(space), "mise-a-jour") + "?slug=" + slug.encodeURLParameter()
    }
}
