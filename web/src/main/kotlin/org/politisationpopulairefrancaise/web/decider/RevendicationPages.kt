package org.politisationpopulairefrancaise.web.decider

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.p
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.revendications.MovementDemands
import org.politisationpopulairefrancaise.web.comprendre.ComprendreRenderer
import org.politisationpopulairefrancaise.web.siteLayout
import org.politisationpopulairefrancaise.web.voir.BadgeTone
import org.politisationpopulairefrancaise.web.voir.badge
import org.politisationpopulairefrancaise.web.voir.originLine
import org.politisationpopulairefrancaise.web.voir.pageSection

/**
 * The demands on the web site (MVP 1, D-152), readable without script: those already carried by the
 * movement first, then the others, each with who carries it, who else supports it publicly and its sources.
 * Voting happens in the application, where the ballot is anonymous (D-110); the site only reads.
 */
internal object RevendicationLinks {
    const val HOME: String = "/revendications"
    fun of(article: Article): String = "$HOME/${article.slug}"
}

private val DEMAND_STATUS_LABELS: Map<RevendicationStatus, String> = mapOf(
    RevendicationStatus.NOUVELLE to "Nouvelle",
    RevendicationStatus.EN_DEBAT to "En débat",
    RevendicationStatus.TRES_SOUTENUE to "Très soutenue",
    RevendicationStatus.RETENUE to "Retenue",
    RevendicationStatus.CAMPAGNE to "Campagne",
)

private const val EXAMPLE = "[EXEMPLE]"

private fun FlowContent.attribution(article: Article) {
    if (MovementDemands.isMarked(article)) {
        p {
            badge("Du mouvement", BadgeTone.BRAND)
            +" Relevé par PPF, rédigé par l’IA."
        }
        MovementDemands.carriers(article).takeIf { it.isNotEmpty() }?.let { p(classes = "muted") { +"Portée par : ${it.joinToString(" ; ")}" } }
    }
    MovementDemands.supporters(article).takeIf { it.isNotEmpty() }?.let { p(classes = "muted") { +"Également portée par : ${it.joinToString(" ; ")}" } }
}

private fun FlowContent.demandCard(article: Article) {
    article(classes = "card") {
        article.status?.let { p { badge(DEMAND_STATUS_LABELS.getValue(it), if (it == RevendicationStatus.CAMPAGNE) BadgeTone.WARNING else BadgeTone.NEUTRAL) } }
        h3 { a(href = RevendicationLinks.of(article)) { +article.title } }
        article.summary?.let { p { +it } }
        attribution(article)
    }
}

/** [demands] are the demands signed by the editorial team (checked by the source), newest first. */
fun HTML.revendicationsDocument(demands: List<Article>) {
    siteLayout(pageTitle = "Revendications", currentSpace = Space.REVENDICATIONS) {
        h1 { +"Revendications" }
        p(classes = "lead") { +"Ce qui doit changer, proposé et voté par les citoyens : une personne, une voix. PPF présente aussi les revendications que le mouvement porte déjà, attribuées à celles et ceux qui les portent, sans les reprendre à son compte." }
        p(classes = "notice") { +"Voter se fait dans l’application PPF, où le vote est anonyme : personne, pas même PPF, ne peut relier un bulletin à un compte." }
        if (demands.isEmpty()) {
            p(classes = "muted") { +"Aucune revendication publiée pour l’instant." }
            return@siteLayout
        }
        val (movement, others) = demands.partition { MovementDemands.isMarked(it) }
        if (movement.isNotEmpty()) {
            pageSection("mouvement", "Ce que le mouvement demande déjà") {
                p { +"Présentées toutes de la même façon, sans ordre de préférence ; chacune renvoie à ses sources." }
                movement.sortedBy { it.title.lowercase() }.forEach { demandCard(it) }
            }
        }
        val (real, examples) = others.partition { !it.title.startsWith(EXAMPLE) }
        if (real.isNotEmpty()) pageSection("citoyennes", "Revendications citoyennes") { real.forEach { demandCard(it) } }
        if (examples.isNotEmpty()) pageSection("exemples", "Exemples") { examples.forEach { demandCard(it) } }
    }
}

fun HTML.revendicationDocument(article: Article) {
    siteLayout(pageTitle = article.title, currentSpace = Space.REVENDICATIONS) {
        p(classes = "breadcrumb") { a(href = RevendicationLinks.HOME) { +"Revendications" } }
        h1 { +article.title }
        article.status?.let { p { badge(DEMAND_STATUS_LABELS.getValue(it)) } }
        article.summary?.let { p(classes = "lead") { +it } }
        attribution(article)
        p(classes = "notice") { +"Pour voter Pour, Contre ou Blanc, ouvrez cette revendication dans l’application PPF." }
        ComprendreRenderer().markdown(this, article.body, headingOffset = 1, withGlossary = false)
        originLine(article)
    }
}
