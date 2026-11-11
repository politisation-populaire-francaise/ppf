package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.p
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.CapNature
import org.politisationpopulairefrancaise.core.comprendre.CapText
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.web.siteLayout

/*
 * The series « Le cap du mouvement » (docs/ROADMAP-MVP1.md, section 5; D-200): short, sourced texts linked to an
 * action. A position of PPF written with the help of an AI says so in a badge and in its last lines (D-212).
 */

internal const val CAP_TITLE: String = "Le cap du mouvement"
internal const val CAP_INTRO: String = "Des textes courts et sourcés pour tenir dans la durée et agir autrement qu’en défilant."

/** The label of origin of a position of PPF written with the help of an AI and not yet reviewed (D-212). */
internal const val CAP_POSITION_LABEL: String = "Position de PPF, rédigée par l’IA, en attente de relecture"

/** The kicker of a text of the series: its nature. */
internal fun capKicker(text: CapText): String = if (text.nature == CapNature.POSITION) "Position de PPF" else "Faits sourcés"

internal fun FlowContent.capCard(text: CapText, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +"${text.rank}. ${capKicker(text)}" }
        headingOf(this, level) { a(href = ComprendreLinks.pathOf(text.article)!!) { +text.article.title } }
        text.article.summary?.let { p { +it } }
    }
}

fun HTML.capListDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = CAP_TITLE, currentSpace = Space.COMPRENDRE) {
        listDocumentBody(CAP_TITLE, CAP_INTRO, catalog.capTexts.isEmpty()) {
            catalog.capTexts.forEach { capCard(it, 2) }
        }
        offlineScript()
    }
}

fun HTML.capDocument(text: CapText, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    val article = text.article
    val parts = splitBody(article.body)
    siteLayout(pageTitle = article.title, currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb(CAP_TITLE to ComprendreLinks.listPath(ComprendreLinks.CAP), article.title)
        comprendreHead("$CAP_TITLE · ${capKicker(text)}", article.title, article.summary) { contentBadges(article) }
        readingBody { renderer.markdown(this, parts.text) }
        glossaryTerms(renderer)
        nextSection(renderer, parts.nextActions, listOf(
            SiteStep("Agir : se mobiliser, s’organiser, s’entraider", spacePath(Space.AGIR)),
            SiteStep("Décider : voter les revendications", spacePath(Space.REVENDICATIONS)),
        ))
        sourcesSection(renderer, parts.sources)
        comprendreOrigin(article)
        offlineScript()
    }
}
