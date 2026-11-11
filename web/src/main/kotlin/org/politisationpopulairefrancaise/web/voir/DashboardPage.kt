package org.politisationpopulairefrancaise.web.voir

import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.caption
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.Dashboard
import org.politisationpopulairefrancaise.core.voir.DashboardFormat
import org.politisationpopulairefrancaise.core.voir.DashboardMetric
import org.politisationpopulairefrancaise.core.voir.DashboardUnit
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.web.siteLayout

/** The address of « Tableau de bord » on the site (PP-07). */
const val DASHBOARD_PATH: String = "/tableau-de-bord"

/** Where the page reads the figures (the server computes them, `DashboardService`). */
fun interface DashboardSource {
    suspend fun dashboard(): Dashboard
}

/** `/tableau-de-bord`, with a period chosen by `?depuis=AAAA-MM&jusqua=AAAA-MM` (a form, no script). */
fun Route.dashboardPage(source: DashboardSource) {
    get(DASHBOARD_PATH) {
        val dashboard = source.dashboard()
        val from = call.request.queryParameters["depuis"]?.takeIf(DashboardFormat::isMonth)
        val to = call.request.queryParameters["jusqua"]?.takeIf(DashboardFormat::isMonth)
        call.respondHtml { dashboardDocument(dashboard, from, to) }
    }
}

/**
 * « Tableau de bord fédérateur » (M34, PP-07, D-107), readable without script: every figure of the list in the same
 * order, its total on the period chosen (all the period by default) and month by month; a figure without an honest
 * source yet is « non mesuré », never zero. The same JSON is at [DashboardFormat.PATH].
 */
fun HTML.dashboardDocument(dashboard: Dashboard, from: String?, to: String?) {
    siteLayout(pageTitle = "Tableau de bord", currentSpace = Space.VOIR) {
        h1 { +"Tableau de bord du mouvement" }
        p { +"La force du mouvement en chiffres, mois par mois. Les chiffres sont comptés par le serveur à partir de ce qu’il publie, sans aucun traceur : jamais une personne, seulement des nombres. Un chiffre qui n’a pas encore de source fiable est « non mesuré », jamais zéro." }
        form(action = DASHBOARD_PATH, method = FormMethod.get, classes = "dashboard-period") {
            label {
                +"Depuis "
                input(type = InputType.month, name = "depuis") { from?.let { value = it } }
            }
            +" "
            label {
                +"Jusqu’à "
                input(type = InputType.month, name = "jusqua") { to?.let { value = it } }
            }
            +" "
            button(type = ButtonType.submit) { +"Afficher" }
        }
        val period = when {
            from != null && to != null -> "de ${monthLabel(from)} à ${monthLabel(to)}"
            from != null -> "depuis ${monthLabel(from)}"
            to != null -> "jusqu’à ${monthLabel(to)}"
            else -> "depuis le début"
        }
        table(classes = "dashboard") {
            caption { +"Chiffres $period (calculés le ${dayLabel(dashboard.updatedOn)})" }
            thead { tr { th { +"Chiffre" }; th { +"Total sur la période" } } }
            tbody {
                DashboardMetric.entries.forEach { metric ->
                    val series = dashboard.series(metric)
                    tr {
                        th { +metric.webLabel }
                        td { +(series.total(from, to)?.let { valueText(metric, it) } ?: "non mesuré") }
                    }
                }
            }
        }
        val months = dashboard.months.filter { (from == null || it >= from) && (to == null || it <= to) }
        val measured = DashboardMetric.entries.filter { dashboard.series(it).measured }
        if (months.isNotEmpty() && measured.isNotEmpty()) {
            table(classes = "dashboard-months") {
                caption { +"Mois par mois : ce que chaque mois a ajouté" }
                thead {
                    tr {
                        th { +"Mois" }
                        measured.forEach { th { +it.webLabel } }
                    }
                }
                tbody {
                    months.forEach { month ->
                        tr {
                            th { +monthLabel(month) }
                            measured.forEach { metric ->
                                td { +valueText(metric, dashboard.series(metric).months.firstOrNull { it.month == month }?.added ?: 0) }
                            }
                        }
                    }
                }
            }
        }
        p {
            +"Ces chiffres sont réutilisables : "
            a(href = DashboardFormat.PATH) { +"le fichier JSON" }
            +", même contenu pour tous. Voir aussi les "
            a(href = "/donnees") { +"données ouvertes" }
            +" et la "
            a(href = TRANSPARENCY_PATH) { +"transparence de PPF" }
            +"."
        }
    }
}

private fun valueText(metric: DashboardMetric, value: Long): String = when (metric.unit) {
    DashboardUnit.COUNT -> FrenchNumbers.format(value.toDouble())
    DashboardUnit.EUROS -> euros(value)
}

private fun monthLabel(month: String): String {
    val year = month.take(4).toIntOrNull() ?: return month
    val number = month.drop(5).toIntOrNull()?.takeIf { it in 1..12 } ?: return month
    return CivilDate(year, number, 1).monthYearFrench()
}

private fun dayLabel(iso: String): String = CivilDate.parseIso(iso)?.toFrench() ?: iso

internal val DashboardMetric.webLabel: String get() = when (this) {
    DashboardMetric.MEMBERS -> "Adhérents"
    DashboardMetric.ACTIVE_ACCOUNTS -> "Comptes actifs"
    DashboardMetric.DEMANDS -> "Revendications publiées"
    DashboardMetric.VOTES -> "Votes"
    DashboardMetric.CALLS -> "Appels publiés"
    DashboardMetric.ORGANISATIONS -> "Organisations inscrites"
    DashboardMetric.COLLECTIVES -> "Collectifs"
    DashboardMetric.SIGNATURES -> "Signatures des pétitions relayées"
    DashboardMetric.COMMITMENTS -> "Engagements de candidats"
    DashboardMetric.MONEY_COLLECTED -> "Argent collecté"
    DashboardMetric.MONEY_REDISTRIBUTED -> "Argent redistribué"
}
