package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.code
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.voir.Victory
import org.politisationpopulairefrancaise.core.voir.VictoryKind
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.web.siteLayout

/** The address of « Nos victoires » on the site (PP-03). */
const val VICTORIES_PATH: String = "/victoires"

/**
 * « Nos victoires » (M34, PP-03), readable without script: what the movement obtained, newest first, each with its
 * day, its kind, the blocks of « Voir » it concerns and its sources, without any repeated identifier in the page.
 */
fun HTML.victoriesDocument(catalog: VoirCatalog) {
    siteLayout(pageTitle = "Nos victoires", currentSpace = Space.VOIR) {
        h1 { +"Nos victoires" }
        p { +"Ce que le mouvement a obtenu : des engagements tenus, des revendications satisfaites, des lois modifiées. Chaque victoire a sa date et ses sources ; elle appartient à tous ceux qui l’ont portée." }
        if (catalog.victories.any { it.title.startsWith(VoirCatalog.EXAMPLE_MARKER) }) {
            p(classes = "callout callout-attention") { +"Contenus de démonstration [EXEMPLE] : ces victoires sont fictives." }
        }
        pageSection("victoires", "Les victoires, de la plus récente à la plus ancienne") {
            if (catalog.victories.isEmpty()) {
                p(classes = "muted") { +"Aucune victoire n’est encore publiée." }
            }
            catalog.victories.forEach { victory -> article(classes = "victory") { victoryBody(victory, catalog) } }
        }
        p { a(href = TRANSPARENCY_PATH) { +"Transparence de PPF" } }
    }
}

/** One victory, as the site shows it: shared by the page and the preview of the back-office. */
internal fun FlowContent.victoryBody(victory: Victory, catalog: VoirCatalog) {
    p(classes = "muted") {
        +"${victory.kind.webLabel} · "
        date(victory.date)
    }
    h3 { +victory.title }
    victory.article.summary?.let { p { +it } }
    if (victory.article.body.isNotBlank()) markdownBelow(victory.article.body, levels = 2)
    val related = victory.related.mapNotNull(catalog::item)
    if (related.isNotEmpty()) {
        h4 { +"Dans « Voir »" }
        ul { related.forEach { item -> li { a(href = VoirLinks.pathOf(item)) { +item.title } } } }
    }
    if (victory.archives.isNotEmpty()) {
        h4 { +"Documents archivés" }
        ul {
            victory.archives.forEach { copy ->
                li {
                    +"Empreinte "
                    code(classes = "hash") { +copy.sha256 }
                    copy.archiveUrl?.takeIf(VoirTags::isWebAddress)?.let {
                        +". "
                        externalLink(it, "Voir le document archivé")
                    }
                }
            }
        }
    }
    h4 { +"Sources" }
    ul(classes = "source-list") {
        victory.sources.forEach { source ->
            li {
                +"${source.kind.label} : "
                val url = source.url?.takeIf(VoirTags::isWebAddress)
                if (url != null) externalLink(url, source.name) else +source.name
                source.date?.let {
                    +", "
                    date(it)
                }
            }
        }
    }
    if (victory.corrections.isNotEmpty()) {
        h4 { +"Corrections" }
        ul {
            victory.corrections.forEach { correction ->
                li {
                    +"Corrigé le "
                    date(correction.date)
                    +" : ${correction.reason}"
                }
            }
        }
    }
}

internal val VictoryKind.webLabel: String get() = when (this) {
    VictoryKind.COMMITMENT_KEPT -> "Engagement tenu"
    VictoryKind.DEMAND_MET -> "Revendication satisfaite"
    VictoryKind.LAW_CHANGED -> "Loi modifiée"
    VictoryKind.OTHER -> "Autre victoire"
}
