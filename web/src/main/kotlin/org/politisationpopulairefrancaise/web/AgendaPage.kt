package org.politisationpopulairefrancaise.web

import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.strong
import kotlinx.html.time
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The filters of the agenda page, as typed in its form (the server normalizes the territory). */
data class AgendaQuery(val territory: String? = null, val type: String? = null)

/** Where the agenda page reads the published mobilizations: provided by the server (its agenda index). */
fun interface AgendaSource {
    /** The mobilizations of the coming weeks matching [query], sorted by start. */
    suspend fun mobilizations(query: AgendaQuery): List<SignedEvent>
}

/** French labels of the types and statuses of mobilizations (wire values of the contract of the Agir space). */
val mobilizationTypeLabels: Map<String, String> = mapOf(
    "manifestation" to "Manifestation",
    "blocage" to "Blocage",
    "rassemblement" to "Rassemblement",
    "greve" to "Grève",
    "assemblee-generale" to "Assemblée générale",
    "reunion" to "Réunion",
    "autre" to "Autre",
)

private val statusLabels: Map<String, String> = mapOf("annule" to "Annulé", "reporte" to "Reporté")

/** Longest territory typed in the form, as the API accepts it. */
private const val MAX_TERRITORY_LENGTH: Int = 200

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH 'h' mm", Locale.FRENCH)
private val MACHINE_DATE: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

/**
 * GET /agenda: the agenda of the mobilizations, readable without JavaScript. A form sent by GET
 * filters by territory and by type (the only way to filter without script; the application filters
 * on the device). Each event shows its date in its own time zone (Paris by default), its place, its
 * territory and its status; links lead only to web addresses and are marked as leaving the site.
 * The iCalendar feed of the same selection is offered for subscription.
 */
fun Route.agendaPage(source: AgendaSource) {
    get("/agenda") {
        val parameters = call.request.queryParameters
        val territory = parameters["territory"]?.trim()?.take(MAX_TERRITORY_LENGTH)?.takeIf { it.isNotEmpty() }
        val type = parameters["type"]?.takeIf { it in AgirFormats.EVENT_TYPES }
        val query = AgendaQuery(territory, type)
        val events = source.mobilizations(query)
        call.respondHtml { agendaDocument(query, events) }
    }
}

/** The whole page; separate from the route so that tests render it without a server. */
fun HTML.agendaDocument(query: AgendaQuery, events: List<SignedEvent>) {
    siteLayout(pageTitle = "Agenda des mobilisations", currentSpace = Space.AGIR) {
        h1 { +"Agenda des mobilisations" }
        p { +"Les manifestations, rassemblements, grèves et réunions des prochaines semaines, publiés par l’équipe, par les organisations vérifiées ou relus avant publication." }
        form(action = "/agenda", method = FormMethod.get) {
            attributes["role"] = "search"
            attributes["aria-label"] = "Filtrer l’agenda"
            label {
                htmlFor = "agenda-territory"
                +"Territoire"
            }
            input(type = InputType.text, name = "territory") {
                id = "agenda-territory"
                maxLength = MAX_TERRITORY_LENGTH.toString()
                attributes["autocomplete"] = "off"
                query.territory?.let { value = it }
            }
            label {
                htmlFor = "agenda-type"
                +"Type"
            }
            select {
                id = "agenda-type"
                name = "type"
                option {
                    value = ""
                    if (query.type == null) selected = true
                    +"Tous les types"
                }
                AgirFormats.EVENT_TYPES.forEach { type ->
                    option {
                        value = type
                        if (query.type == type) selected = true
                        +mobilizationTypeLabels[type].orEmpty().ifEmpty { type }
                    }
                }
            }
            button(type = kotlinx.html.ButtonType.submit) { +"Filtrer" }
        }
        p {
            a(href = calendarLink(query)) { +"S’abonner à cet agenda dans un logiciel de calendrier (iCalendar)" }
        }
        section {
            attributes["aria-labelledby"] = "agenda-results"
            h2 {
                id = "agenda-results"
                +when (events.size) {
                    0 -> "Aucune mobilisation prévue"
                    1 -> "1 mobilisation"
                    else -> "${events.size} mobilisations"
                }
            }
            events.forEach { event -> mobilization(event) }
        }
    }
}

internal fun kotlinx.html.FlowContent.mobilization(event: SignedEvent) {
    val start = event.firstTagValue(AgirFormats.TAG_START)?.toLongOrNull() ?: return
    val end = event.firstTagValue(AgirFormats.TAG_END)?.toLongOrNull()?.takeIf { it > start }
    val zone = zoneOf(event)
    article(classes = "agenda-event") {
        h3 {
            event.firstTagValue(AgirFormats.TAG_EVENT_STATUS)?.let(statusLabels::get)?.let { status ->
                strong { +"$status : " }
            }
            +(event.firstTagValue(AgirFormats.TAG_TITLE) ?: "Mobilisation")
        }
        dl {
            dt { +"Quand" }
            dd {
                dateTime(start, zone)
                if (end != null) {
                    +" jusqu’au "
                    dateTime(end, zone)
                }
            }
            event.firstTagValue(AgirFormats.TAG_LOCATION)?.let { place ->
                dt { +"Où" }
                dd { +place }
            }
            event.firstTagValue(AgirFormats.TAG_TERRITORY)?.let { territory ->
                dt { +"Territoire" }
                dd { +territory }
            }
            event.firstTagValue(AgirFormats.TAG_EVENT_TYPE)?.let { type ->
                dt { +"Type" }
                dd { +(mobilizationTypeLabels[type] ?: type) }
            }
        }
        val text = event.content.takeIf { it.isNotBlank() } ?: event.firstTagValue(AgirFormats.TAG_SUMMARY)
        text?.lines()?.filter { it.isNotBlank() }?.forEach { line -> p { +line } }
        event.tagValues(AgirFormats.TAG_LINK).firstOrNull(::isWebAddress)?.let { link ->
            p {
                a(href = link) {
                    attributes["rel"] = "noopener noreferrer nofollow external"
                    +"En savoir plus (site extérieur)"
                }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.dateTime(seconds: Long, zone: ZoneId) {
    val moment = Instant.ofEpochSecond(seconds).atZone(zone)
    time {
        attributes["datetime"] = MACHINE_DATE.format(moment)
        +DATE.format(moment)
    }
}

/** The time zone the organiser gave (`start_tzid`), Paris otherwise. */
private fun zoneOf(event: SignedEvent): ZoneId =
    event.firstTagValue(AgirFormats.TAG_START_TZID)?.let {
        try {
            ZoneId.of(it)
        } catch (e: DateTimeException) {
            null
        }
    } ?: ZoneId.of(AgirFormats.TIME_ZONE)

private fun isWebAddress(value: String): Boolean =
    (value.startsWith("https://") || value.startsWith("http://")) && value.none { it.isWhitespace() || it.isISOControl() }

/** The feed of the same selection; values encoded for an address. */
private fun calendarLink(query: AgendaQuery): String {
    val parameters = listOfNotNull(
        query.territory?.let { "territory=" + java.net.URLEncoder.encode(it, Charsets.UTF_8) },
        query.type?.let { "type=$it" },
    )
    return "/api/v1/agenda.ics" + if (parameters.isEmpty()) "" else "?" + parameters.joinToString("&")
}
