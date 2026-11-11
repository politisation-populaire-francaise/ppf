package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirTags.archives
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/*
 * « Espace presse » (M34, PP-04; docs/integration/espace-presse.md): the press releases of PPF, signed and reviewed
 * like every content, with their day, their sources and the public log of their corrections. The media kit, the
 * reusable figures and the press contact (through M30) are pages of the application and of the site.
 */

/** A press release (`communique`): its day is mandatory; its text is the body. */
data class PressRelease(
    val article: Article,
    val date: CivilDate,
    val sources: List<Source>,
    val archives: List<ArchivedCopy>,
    val corrections: List<Correction>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    companion object {
        fun from(article: Article): PressRelease? {
            if (article.type != ArticleType.COMMUNIQUE) return null
            val event = article.event
            val date = CivilDate.parseIso(event.value(VoirTags.DATE)) ?: return null
            return PressRelease(article, date, event.sources(), event.archives(), event.corrections())
        }

        /** Newest first. */
        val ORDER: Comparator<PressRelease> = compareByDescending<PressRelease> { it.date }.thenBy { FrenchText.fold(it.title) }
    }
}

/** The media kit: the facts a journalist needs to name and show PPF, the same everywhere (D-025, D-103). */
object MediaKit {
    const val NAME: String = "Politisation Populaire Française"
    const val ACRONYM: String = "PPF"
    const val SITE: String = "politisation-populaire-francaise.org"

    /** The colours of the identity (PpfTheme, D-025): deep blue brand, carmine accent, never the colours of the State. */
    const val BRAND_COLOR: String = "#2B3A8F"
    const val ACCENT_COLOR: String = "#C8213A"
    const val INK_COLOR: String = "#14182B"

    const val TYPEFACE: String = "Atkinson Hyperlegible (Braille Institute, SIL Open Font License 1.1)"
    const val ICONS: String = "Lucide (licence ISC)"

    /** The press contact goes through the contact of PPF (M30), category « journalist », answered within this goal. */
    const val PRESS_REPLY_GOAL_DAYS: Int = 15
}
