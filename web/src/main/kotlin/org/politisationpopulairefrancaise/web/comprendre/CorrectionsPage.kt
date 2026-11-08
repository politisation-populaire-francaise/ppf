package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.web.siteLayout
import org.politisationpopulairefrancaise.web.voir.badge
import org.politisationpopulairefrancaise.web.voir.date

/**
 * One line of the public journal of corrections (circuit éditorial, section 3.4): a tag
 * `["ppf-correction", <date AAAA-MM-JJ>, <summary>, <correction | mise-a-jour>]` of the published version of
 * a content. Read from the signed events only: no table, nothing that the events do not say.
 */
data class PublicCorrection(
    val date: CivilDate,
    val title: String,
    /** The page of the content, null when it has none on the site. */
    val path: String?,
    val summary: String,
    /** `correction` (an error of substance) or `mise-a-jour`; null when the tag does not say. */
    val kind: String?,
) {
    companion object {
        const val TAG: String = "ppf-correction"
        const val KIND_CORRECTION: String = "correction"
        const val KIND_UPDATE: String = "mise-a-jour"

        /** The corrections of [articles] (the newest version of each address), the most recent first, then by title. */
        fun of(articles: List<Article>): List<PublicCorrection> = articles
            .filter { it.type != ArticleType.PERSONNE }
            .groupBy { it.address.toString() }
            .map { (_, versions) -> versions.maxBy { it.updatedAt } }
            .flatMap { article ->
                article.event.tags.filter { it.size >= 3 && it[0] == TAG }.mapNotNull { tag ->
                    val date = CivilDate.parseIso(tag[1]) ?: return@mapNotNull null
                    val summary = tag[2].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    PublicCorrection(date, article.title, pathOf(article), summary, tag.getOrNull(3)?.takeIf { it == KIND_CORRECTION || it == KIND_UPDATE })
                }
            }
            .sortedWith(compareByDescending<PublicCorrection> { it.date.toIso() }.thenBy { it.title })

        private fun pathOf(article: Article): String? =
            if (article.type == ArticleType.ARTICLE && article.slug == ComprendreCatalog.METHOD_SLUG) ComprendreLinks.METHOD else ComprendreLinks.pathOf(article)
    }
}

/** The address of the public journal of corrections. */
const val CORRECTIONS_PATH: String = "/corrections"

/** The public journal of corrections, readable without script, in the layout of the pages of "Comprendre". */
fun HTML.correctionsDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = "Journal des corrections", currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb(null, "Journal des corrections")
        comprendreHead(
            null,
            "Journal des corrections",
            "Chaque fois qu’un contenu publié est corrigé ou mis à jour, nous le disons ici : la date, le contenu et ce qui a changé. Les anciennes lignes ne sont jamais effacées.",
        )
        section {
            attributes["aria-label"] = "Corrections et mises à jour"
            if (catalog.corrections.isEmpty()) {
                p(classes = "muted") { +"Aucune correction publiée pour l’instant." }
            } else {
                ol(classes = "card-list") {
                    catalog.corrections.forEach { correction ->
                        li(classes = "card") {
                            p(classes = "muted") {
                                date(correction.date)
                                correction.kind?.let { kind ->
                                    +" "
                                    badge(if (kind == PublicCorrection.KIND_CORRECTION) "Correction" else "Mise à jour")
                                }
                            }
                            p {
                                val path = correction.path
                                if (path != null) a(href = path) { +correction.title } else +correction.title
                            }
                            p { +correction.summary }
                        }
                    }
                }
            }
        }
        if (catalog.method != null) p { a(href = ComprendreLinks.METHOD) { +"Notre méthode : comment demander une correction" } }
    }
}
