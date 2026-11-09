package org.politisationpopulairefrancaise.core.editorial

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * The date by which the team reviews a content (`review_by` of the front matter and of the form, tag
 * `ppf-review-by`, a date `AAAA-MM-JJ`; decision of the team lead of 9 November 2026, with « Voir » and the
 * Directeur technique). One definition for every type of « Comprendre » and « Voir », optional everywhere.
 *
 * The date never hides nor blocks a content, and nothing of it is shown to the public for now: it only feeds
 * the follow-up queue « À revoir » of each editorial section, which lists the published contents whose date
 * is past or falls within [WINDOW_DAYS] days. A review that changes nothing still publishes a new version, with
 * a new date and the mention « mise à jour » in the public journal of corrections (D-095).
 */
object ReviewDates {
    const val TAG: String = "ppf-review-by"

    /** How far ahead the queue « À revoir » looks, in days. */
    const val WINDOW_DAYS: Int = 60

    /** The review date of [event]; null when it has none or when the tag is not a valid date. */
    fun of(event: SignedEvent): CivilDate? = CivilDate.parseIso(event.firstTagValue(TAG))

    /** The review date of [article]. */
    fun of(article: Article): CivilDate? = of(article.event)

    /** Where a content stands on [today] against its review [date]. */
    fun status(date: CivilDate?, today: CivilDate): ReviewStatus {
        if (date == null) return ReviewStatus.None
        val days = date.toEpochDays() - today.toEpochDays()
        return when {
            days < 0 -> ReviewStatus.Overdue(date, -days)
            days <= WINDOW_DAYS -> ReviewStatus.Soon(date, days)
            else -> ReviewStatus.Later(date)
        }
    }

    /** True when the content belongs in « À revoir »: its date is past, today, or within [WINDOW_DAYS] days. */
    fun isDue(date: CivilDate?, today: CivilDate): Boolean = status(date, today).let { it is ReviewStatus.Overdue || it is ReviewStatus.Soon }
}

/** Where a content stands against its review date. */
sealed interface ReviewStatus {
    /** No review date: the content never enters « À revoir ». */
    data object None : ReviewStatus

    /** The date is more than [ReviewDates.WINDOW_DAYS] days away. */
    data class Later(val date: CivilDate) : ReviewStatus

    /** The date is today ([daysLeft] = 0) or within [ReviewDates.WINDOW_DAYS] days. */
    data class Soon(val date: CivilDate, val daysLeft: Long) : ReviewStatus

    /** The date has passed, [daysLate] days ago. */
    data class Overdue(val date: CivilDate, val daysLate: Long) : ReviewStatus
}
