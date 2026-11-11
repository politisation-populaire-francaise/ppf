package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirTags.archives
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.links
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/*
 * « Nos victoires » (M34, PP-03; docs/integration/victoires.md): what the movement obtained, each victory with the
 * day it was obtained, its sources (at least one) and the blocks of Voir it concerns. A victory is the movement's:
 * the page never claims it for PPF alone, and a victory without source is not shown.
 */

/** What was obtained (`ppf-victory`), in the order of the filters. */
enum class VictoryKind(val tagValue: String) {
    COMMITMENT_KEPT("engagement-tenu"),
    DEMAND_MET("revendication-satisfaite"),
    LAW_CHANGED("loi-modifiee"),
    OTHER("autre"),
    ;

    companion object {
        fun fromTag(value: String?): VictoryKind? = entries.firstOrNull { it.tagValue == value }
    }
}

data class Victory(
    val article: Article,
    val kind: VictoryKind,
    /** The day it was obtained (the decision, the publication of the law, the announcement kept). */
    val date: CivilDate,
    /** The blocks of Voir it concerns (`a` tags of role `lie`): a promise kept, a bill, a person. */
    val related: List<ContentAddress>,
    val sources: List<Source>,
    val archives: List<ArchivedCopy>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    companion object {
        fun from(article: Article): Victory? {
            if (article.type != ArticleType.VICTOIRE) return null
            val event = article.event
            val kind = VictoryKind.fromTag(event.value(VoirTags.VICTORY)) ?: return null
            val date = CivilDate.parseIso(event.value(VoirTags.DATE)) ?: return null
            val sources = event.sources()
            // No victory without its proof.
            if (sources.isEmpty()) return null
            return Victory(
                article = article,
                kind = kind,
                date = date,
                related = event.links(VoirTags.ROLE_RELATED),
                sources = sources,
                archives = event.archives(),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
            )
        }

        /** Newest first, then by title: never ranked by importance. */
        val ORDER: Comparator<Victory> = compareByDescending<Victory> { it.date }.thenBy { FrenchText.fold(it.title) }
    }
}
