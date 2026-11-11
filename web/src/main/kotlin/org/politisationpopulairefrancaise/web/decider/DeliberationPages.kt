package org.politisationpopulairefrancaise.web.decider

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.strong
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.decider.DeliberationBoard
import org.politisationpopulairefrancaise.core.decider.DeliberationPhase
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.web.siteLayout
import org.politisationpopulairefrancaise.web.voir.BadgeTone
import org.politisationpopulairefrancaise.web.voir.badge
import org.politisationpopulairefrancaise.web.voir.pageSection

/**
 * The citizen deliberation on the web site (DE-25, MVP 1), readable without script: its dated steps, its
 * propositions and their amendments. Proposing, amending and voting happen in the application, where the
 * ballot is anonymous (D-110); the site only reads.
 */
internal object DeliberationLinks {
    const val PATH: String = "deliberations"
    fun of(board: DeliberationBoard): String = "${DeciderLinks.HOME}/$PATH/${board.address.identifier}"
}

private val PHASE_LABELS: Map<DeliberationPhase, String> = mapOf(
    DeliberationPhase.PROPOSITIONS to "Propositions",
    DeliberationPhase.AMENDMENTS to "Amendements",
    DeliberationPhase.VOTE to "Vote final",
)

private fun day(seconds: Long): String = CivilDate.fromEpochSeconds(seconds).toFrench()

private fun stateLine(board: DeliberationBoard, now: Long): String {
    val phase = board.phaseAt(now)
    return when {
        phase != null -> "Étape en cours : ${PHASE_LABELS.getValue(phase)}, jusqu’au ${day(board.deliberation.dates(phase).end)}."
        board.deliberation.isOver(now) -> "Délibération terminée."
        else -> "Ouverture des propositions le ${day(board.deliberation.dates(DeliberationPhase.PROPOSITIONS).start)}."
    }
}

internal fun FlowContent.deliberationCard(board: DeliberationBoard, now: Long) {
    article(classes = "card") {
        h3 { a(href = DeliberationLinks.of(board)) { +board.deliberation.title } }
        p { +stateLine(board, now) }
        p(classes = "muted") { +"${board.entries.size} proposition(s) publiée(s)." }
    }
}

fun HTML.deliberationDocument(board: DeliberationBoard, now: Long) {
    val deliberation = board.deliberation
    siteLayout(pageTitle = deliberation.title, currentSpace = Space.DECIDER) {
        p(classes = "breadcrumb") { a(href = DeciderLinks.HOME) { +"Décider" } }
        h1 { +deliberation.title }
        deliberation.subject?.let { p(classes = "lead") { +it } }
        p { strong { +stateLine(board, now) } }
        pageSection("etapes", "Les étapes") {
            ol {
                DeliberationPhase.entries.forEach { phase ->
                    val dates = deliberation.dates(phase)
                    li {
                        +"${PHASE_LABELS.getValue(phase)} : du ${day(dates.start)} au ${day(dates.end)}"
                        if (board.phaseAt(now) == phase) { +" " ; badge("En cours", BadgeTone.BRAND) }
                    }
                }
            }
            p {
                +"PPF n’adopte rien : les votes des citoyens choisissent la rédaction de chaque mesure, puis les mesures du Pacte, de ${deliberation.minMeasures} à ${deliberation.maxMeasures} parmi celles qui ont plus de « pour » que de « contre ». Le vote blanc est compté. Chaque contribution est relue par une personne avant d’être publiée."
            }
            p(classes = "notice") { +"Proposer, amender et voter se font dans l’application PPF, où le vote est anonyme : personne, pas même PPF, ne peut relier un bulletin à un compte." }
        }
        pageSection("propositions", "Les propositions") {
            if (board.entries.isEmpty()) p(classes = "muted") { +"Aucune proposition publiée pour l’instant." }
            board.entries.forEach { entry ->
                val proposition = entry.proposition
                article(classes = "card") {
                    h3 { +proposition.title }
                    if (proposition.isStart) p { badge("Proposition de départ, pas encore votée", BadgeTone.WARNING) }
                    p { +proposition.text }
                    proposition.justification?.let { p(classes = "muted") { +"Pourquoi : $it" } }
                    if (entry.amendments.isNotEmpty()) {
                        p { strong { +"Amendements mis au vote (${entry.amendments.size})" } }
                        ol {
                            entry.amendments.forEach { amendment ->
                                li {
                                    p { +amendment.text }
                                    amendment.justification?.let { p(classes = "muted") { +"Pourquoi : $it" } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
