package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.voir.FigureUnit
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.PpfInstance
import org.politisationpopulairefrancaise.core.voir.ReportFigure
import org.politisationpopulairefrancaise.core.voir.TransparencyEntry
import org.politisationpopulairefrancaise.core.voir.TransparencyPages
import org.politisationpopulairefrancaise.core.voir.TransparencyRubric
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.web.siteLayout

/** The address of « Transparence de PPF » on the site (PP-01), linked from the footer of every page. */
const val TRANSPARENCY_PATH: String = "/transparence"

/**
 * « Transparence de PPF » (M34, PP-01, D-104), readable without script: the instances, the important decisions,
 * the accounts and the reports, always in this order, a rubric with nothing published saying so. Each page keeps
 * its own text, archived documents, sources and corrections, without any repeated identifier in the page.
 */
fun HTML.transparencyDocument(pages: TransparencyPages) {
    siteLayout(pageTitle = "Transparence de PPF", currentSpace = Space.VOIR) {
        h1 { +"Transparence de PPF" }
        p { +"Une plateforme qui demande des comptes au pouvoir doit rendre les siens. Voici les instances de l’association, ses décisions importantes, ses comptes et ses rapports de modération et de réclamations, avec les mêmes rubriques chaque année." }
        val all = pages.instances + pages.decisions + pages.accounts + pages.reports
        if (all.any { it.title.startsWith(VoirCatalog.EXAMPLE_MARKER) }) {
            p(classes = "callout callout-attention") { +"Contenus de démonstration [EXEMPLE] : l’association n’existe pas encore. Aucun nom, aucun chiffre réel." }
        }
        rubric("instances", "Les instances", pages.instances)
        if (pages.instances.isNotEmpty()) p(classes = "muted") { +"Le bureau et le conseil d’administration sont publiés avec le nom et la fonction de chaque membre, rien d’autre ; chaque membre en est informé avant la publication (RGPD). Dans les autres équipes, un nom n’est publié qu’à la demande de la personne." }
        rubric("decisions", "Les décisions importantes", pages.decisions)
        rubric("comptes", "Les comptes", pages.accounts)
        rubric("rapports", "Rapports de modération et de réclamations", pages.reports)
        p {
            a(href = "/donnees") { +"Données ouvertes" }
            +" · "
            a(href = "/verifier") { +"Vérifier un contenu" }
            +" · "
            a(href = "/voir/corrections") { +"Journal des corrections" }
        }
    }
}

private fun FlowContent.rubric(id: String, title: String, entries: List<TransparencyEntry>) {
    pageSection(id, title) {
        if (entries.isEmpty()) {
            p(classes = "muted") { +"Rien n’est encore publié dans cette rubrique." }
            return@pageSection
        }
        entries.forEach { entry -> article(classes = "transparency-entry") { transparencyEntryBody(entry) } }
    }
}

/** One page of transparency, as the site shows it: shared by the page and the preview of the back-office. */
internal fun FlowContent.transparencyEntryBody(entry: TransparencyEntry) {
    when (entry.rubric) {
        TransparencyRubric.INSTANCE -> instance(entry)
        TransparencyRubric.DECISION -> decision(entry)
        TransparencyRubric.ACCOUNTS -> accounts(entry)
        TransparencyRubric.REPORT -> report(entry)
    }
}

private fun FlowContent.entryTitle(entry: TransparencyEntry) {
    h3 { +entry.title }
    entry.article.summary?.let { p { +it } }
}

/** What every page ends with, under small headings: no id, so that several pages fit in one document. */
private fun FlowContent.entryFooter(entry: TransparencyEntry) {
    if (entry.article.body.isNotBlank()) markdownBelow(entry.article.body, levels = 2)
    if (entry.archives.isNotEmpty()) {
        h4 { +"Documents archivés" }
        ul {
            entry.archives.forEach { copy ->
                li {
                    copy.archivedAt?.let {
                        +"Archivé le "
                        date(it)
                        +", empreinte "
                    } ?: +"Empreinte "
                    code(classes = "hash") { +copy.sha256 }
                    copy.archiveUrl?.takeIf(VoirTags::isWebAddress)?.let {
                        +". "
                        externalLink(it, "Voir le document archivé")
                    }
                }
            }
        }
    }
    if (entry.sources.isNotEmpty()) {
        h4 { +"Sources" }
        ul(classes = "source-list") {
            entry.sources.forEach { source ->
                li {
                    +"${source.kind.label} : "
                    val url = source.url?.takeIf(VoirTags::isWebAddress)
                    if (url != null) externalLink(url, source.name) else +source.name
                }
            }
        }
    }
    if (entry.corrections.isNotEmpty()) {
        h4 { +"Corrections" }
        ul {
            entry.corrections.forEach { correction ->
                li {
                    +"Corrigé le "
                    date(correction.date)
                    +" : ${correction.reason}"
                }
            }
        }
    }
}

private fun FlowContent.instance(entry: TransparencyEntry) {
    entryTitle(entry)
    entry.instance?.let { p(classes = "muted") { +it.webLabel } }
    if (entry.seats.isNotEmpty()) {
        ul {
            entry.seats.forEach { seat ->
                li {
                    +seat.function
                    seat.name?.let { +" : ${it.removePrefix(VoirCatalog.EXAMPLE_MARKER).trim()}" }
                    seat.since?.let {
                        +", depuis le "
                        date(it)
                    }
                    seat.designation?.let { +" ($it)" }
                }
            }
        }
    }
    entryFooter(entry)
}

private fun FlowContent.decision(entry: TransparencyEntry) {
    entryTitle(entry)
    p(classes = "muted") {
        entry.date?.let { date(it) }
        entry.instance?.let { +" · ${it.webLabel}" }
    }
    entry.tally?.let { p { +"Pour : ${it.inFavour} · Contre : ${it.against} · Abstention : ${it.abstention}" } }
    entryFooter(entry)
}

private fun FlowContent.accounts(entry: TransparencyEntry) {
    entryTitle(entry)
    entry.year?.let { p(classes = "muted") { +"Exercice $it" } }
    p(classes = "muted") {
        val approved = entry.approvedOn
        if (approved != null) {
            +"Approuvés par l’assemblée générale le "
            date(approved)
        } else {
            +"Pas encore approuvés par l’assemblée générale"
        }
    }
    // The totals and the result, computed from the lines, never typed.
    dl {
        dt { +"Produits" }
        dd { +euros(entry.incomeCents) }
        dt { +"Charges" }
        dd { +euros(entry.expenseCents) }
        dt { +"Résultat (produits moins charges)" }
        dd { +euros(entry.resultCents) }
    }
    if (entry.lines.isNotEmpty()) {
        ul { entry.lines.forEach { line -> li { +"${line.category} : ${euros(line.amountCents)}" } } }
    }
    entryFooter(entry)
}

private fun FlowContent.report(entry: TransparencyEntry) {
    entryTitle(entry)
    p(classes = "muted") {
        val start = entry.periodStart
        val end = entry.periodEnd
        if (start != null && end != null) {
            +"Du "
            date(start)
            +" au "
            date(end)
        } else if (start != null) {
            +"Depuis le "
            date(start)
        }
    }
    listOf(true to "Modération", false to "Réclamations").forEach { (moderation, title) ->
        h4 { +title }
        // Every figure of the list, always in this order: one absent is « non mesuré », never zero.
        dl {
            ReportFigure.entries.filter { it.moderation == moderation }.forEach { figure ->
                dt { +figure.webLabel }
                dd { +(entry.figures[figure]?.let { valueText(figure, it) } ?: "non mesuré") }
            }
        }
    }
    entryFooter(entry)
}

private fun valueText(figure: ReportFigure, value: Double): String {
    val number = FrenchNumbers.format(value)
    return when (figure.unit) {
        FigureUnit.COUNT -> number
        FigureUnit.HOURS -> "$number heures"
        FigureUnit.PERCENT -> "$number %"
    }
}

/** « 1 234,56 € », a deficit with its sign. */
internal fun euros(cents: Long): String {
    val negative = cents < 0
    val size = kotlin.math.abs(cents)
    val whole = FrenchNumbers.format((size / 100).toDouble())
    val rest = (size % 100).toInt()
    val text = if (rest == 0) "$whole${FrenchNumbers.GROUP_SEPARATOR}€" else "$whole,${rest.toString().padStart(2, '0')}${FrenchNumbers.GROUP_SEPARATOR}€"
    return if (negative) "-$text" else text
}

internal val PpfInstance.webLabel: String get() = when (this) {
    PpfInstance.GENERAL_ASSEMBLY -> "Assemblée générale"
    PpfInstance.BOARD -> "Conseil d’administration"
    PpfInstance.BUREAU -> "Bureau"
    PpfInstance.EDITORIAL_TEAM -> "Équipe éditoriale"
    PpfInstance.MODERATION_TEAM -> "Équipe de modération"
    PpfInstance.OTHER -> "Autre instance"
}

internal val ReportFigure.webLabel: String get() = when (this) {
    ReportFigure.AUTHORITY_ORDERS -> "Injonctions et demandes d’information des autorités"
    ReportFigure.REPORTS_RECEIVED -> "Signalements reçus"
    ReportFigure.DECISIONS_ON_REPORTS -> "Décisions prises sur signalement"
    ReportFigure.DECISIONS_OWN_INITIATIVE -> "Décisions prises à l’initiative de l’équipe"
    ReportFigure.AUTOMATIC_HIDINGS -> "Contenus masqués provisoirement par la modération automatique"
    ReportFigure.AUTOMATIC_ALERTS -> "Contenus signalés sans masquage par la modération automatique"
    ReportFigure.MEDIAN_DELAY_HOURS -> "Délai médian de décision"
    ReportFigure.INTERNAL_APPEALS -> "Réclamations contre une décision de modération"
    ReportFigure.DECISIONS_CHANGED -> "Décisions annulées ou modifiées après réclamation"
    ReportFigure.AUTHORITY_TRANSMISSIONS -> "Transmissions aux autorités"
    ReportFigure.AUTOMATIC_PRECISION -> "Précision de la modération automatique"
    ReportFigure.AUTOMATIC_ERROR_RATE -> "Taux d’erreur de la modération automatique"
    ReportFigure.CLAIMS_RECEIVED -> "Réclamations reçues"
    ReportFigure.CLAIMS_LATE -> "Réclamations traitées hors délai"
    ReportFigure.DATA_RIGHTS -> "Demandes d’exercice des droits sur les données"
    ReportFigure.REQUISITIONS -> "Réquisitions judiciaires reçues"
}
