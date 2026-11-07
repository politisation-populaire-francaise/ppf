package org.politisationpopulairefrancaise.server.elus

import kotlinx.html.FlowContent
import kotlinx.html.ThScope
import kotlinx.html.caption
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.time
import kotlinx.html.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** French texts of the lookup fragment (the back-office is in French). */
object ElusLookupTexts {
    const val CAPTION: String = "Mandats en cours dans le Répertoire national des élus"
    const val NONE: String = "Aucun mandat en cours ne correspond dans le Répertoire national des élus."
    const val UNAVAILABLE: String = "L'annuaire des élus n'est pas encore chargé sur ce serveur : impossible de vérifier un mandat pour l'instant."
    const val NAME: String = "Nom"
    const val MANDATE: String = "Mandat"
    const val FUNCTION: String = "Fonction"
    const val TERRITORY: String = "Territoire"
    const val SINCE: String = "Début du mandat"
    const val ID: String = "Identifiant"
    const val SOURCE_PREFIX: String = "Selon la source mise à jour le "
    const val SOURCE_SUFFIX: String = " : le répertoire ne donne ni date de fin ni identifiant officiel ; un mandat absent a pu prendre fin ou être corrigé."
    const val TRUNCATED: String = "Liste limitée : précisez le type de mandat ou le territoire."

    fun mandate(mandate: Mandate): String = when (mandate) {
        Mandate.MUNICIPAL -> "Conseil municipal"
        Mandate.COMMUNAUTAIRE -> "Conseil communautaire"
        Mandate.DEPARTEMENTAL -> "Conseil départemental"
        Mandate.REGIONAL -> "Conseil régional"
        Mandate.COLLECTIVITE -> "Assemblée d'une collectivité à statut particulier"
        Mandate.DEPUTE -> "Assemblée nationale"
        Mandate.SENATEUR -> "Sénat"
        Mandate.EUROPEEN -> "Parlement européen"
    }
}

private val FRENCH_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

private fun FlowContent.day(date: LocalDate) {
    time {
        attributes["datetime"] = date.toString()
        +FRENCH_DATE.format(date)
    }
}

/**
 * The mandates in progress that match a request, for the detail of an item of `elus.certifications`
 * (contract docs/integration/back-office.md, section 7): an accessible table with a caption, the date of
 * the source, and the identifier `rne1:` of each mandate. [sourceUpdatedOn] null means that nothing is
 * served yet. [limit] is the limit given to [ElusLookup.find], to say when the list may be cut.
 */
fun FlowContent.elusMatches(matches: List<ElusMatch>, sourceUpdatedOn: LocalDate?, limit: Int = ElusLookup.DEFAULT_LIMIT) {
    div(classes = "bo-elus-matches") {
        if (sourceUpdatedOn == null) {
            div(classes = "bo-notice bo-notice-warning") {
                attributes["role"] = "status"
                +ElusLookupTexts.UNAVAILABLE
            }
            return@div
        }
        p {
            +ElusLookupTexts.SOURCE_PREFIX
            day(sourceUpdatedOn)
            +ElusLookupTexts.SOURCE_SUFFIX
        }
        if (matches.isEmpty()) {
            p(classes = "bo-notice") {
                attributes["role"] = "status"
                +ElusLookupTexts.NONE
            }
            return@div
        }
        table(classes = "bo-table") {
            caption { +ElusLookupTexts.CAPTION }
            thead {
                tr {
                    listOf(ElusLookupTexts.NAME, ElusLookupTexts.MANDATE, ElusLookupTexts.FUNCTION, ElusLookupTexts.TERRITORY, ElusLookupTexts.SINCE, ElusLookupTexts.ID)
                        .forEach { label -> th(scope = ThScope.col) { +label } }
                }
            }
            tbody {
                matches.forEach { match ->
                    tr {
                        th(scope = ThScope.row) { +match.name }
                        td { +ElusLookupTexts.mandate(match.mandate) }
                        td { +(match.function ?: "") }
                        td {
                            +match.territoryName
                            if (match.territoryName != match.territory) +" (${match.territory})"
                        }
                        td { runCatching { LocalDate.parse(match.since) }.getOrNull()?.let { day(it) } ?: +match.since }
                        td { code { +match.id } }
                    }
                }
            }
        }
        if (matches.size >= limit) p(classes = "bo-notice") { +ElusLookupTexts.TRUNCATED }
    }
}
