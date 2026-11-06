package org.politisationpopulairefrancaise.server.agir

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.publication.HeldStatusSource
import org.politisationpopulairefrancaise.server.api.badParameter
import org.politisationpopulairefrancaise.server.groups.GroupNames
import org.politisationpopulairefrancaise.web.AgendaSource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Longest `territory` parameter of the agenda, in characters (as the search of the rooms). */
const val MAX_AGENDA_TERRITORY_LENGTH: Int = 200

/** At most this many values of `type` or of `organizer` per request. */
const val MAX_AGENDA_ALTERNATIVES: Int = 20

/** The default period of the agenda: from 12 hours ago (events under way) to 90 days ahead. */
val AGENDA_DEFAULT_PAST = 12.hours
val AGENDA_DEFAULT_AHEAD = 90.days

/**
 * Read side of the Agir space (contract, section 5); publication goes through POST /api/v1/events.
 *
 * - GET /api/v1/held?id=<hex>&id=<hex>: where the author's held contents stand (1 to 20 event ids of
 *   64 lowercase hexadecimal characters), as a JSON list of [org.politisationpopulairefrancaise.core.publication.HeldState]
 *   (`id`, `status`, `received_at`, `decided_at`, `reason`), in the order asked; ids the queue does
 *   not know are left out. Never the content nor the key; `Cache-Control: no-store`. No
 *   authentication: while a content is pending, its event id is known to its author only.
 * - GET /api/v1/agenda?from=&to=&territory=&type=&organizer=&limit=: the published mobilizations
 *   (signed events) that overlap the period, by start then id. `from` and `to` in seconds since 1970
 *   (default: 12 hours ago and 90 days ahead; `from` at most `to`), `territory` compared once
 *   normalized as for the rooms, `type` one of the event types and `organizer` a hex key (author or
 *   organiser named in a `p` tag), both repeatable as alternatives (20 at most), `limit` from 1 to 500
 *   (100 by default). Every other parameter at most once; otherwise 400 bad_parameter.
 * - GET /api/v1/agenda.ics: the same selection as an iCalendar feed ([AgendaCalendar]).
 */
fun Route.agirApi(agir: AgirSpace?) {
    // Without a database the 503 answer of eventsApi covers every /api/v1/ address.
    if (agir == null) return
    get("/api/v1/held") {
        val ids = call.request.queryParameters.getAll("id").orEmpty()
        if (ids.isEmpty() || ids.size > HeldStatusSource.MAX_IDS || ids.any { !isEventId(it) }) return@get badParameter("id")
        // A status changes and concerns one author: no cache may keep it.
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respond(withContext(Dispatchers.IO) { agir.statuses(ids) })
    }
    get("/api/v1/agenda") {
        val filter = agendaFilter(call.request.queryParameters, agir.clock.instant().epochSecond) ?: return@get
        call.respond(withContext(Dispatchers.IO) { agir.agenda.query(filter) })
    }
    get("/api/v1/agenda.ics") {
        val filter = agendaFilter(call.request.queryParameters, agir.clock.instant().epochSecond) ?: return@get
        val events = withContext(Dispatchers.IO) { agir.agenda.query(filter) }
        call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"agenda-ppf.ics\"")
        call.respondText(AgendaCalendar.render(events), ContentType.parse(AgendaCalendar.CONTENT_TYPE))
    }
}

/** Most mobilizations on the agenda page of the site. */
const val AGENDA_PAGE_LIMIT: Int = 200

/**
 * The source of the agenda page of the site (module web, GET /agenda): the default period of the
 * API, the territory normalized as for the rooms.
 */
fun AgirSpace.agendaSource(): AgendaSource = AgendaSource { query ->
    val now = clock.instant().epochSecond
    val filter = AgendaFilter(
        from = now - AGENDA_DEFAULT_PAST.inWholeSeconds,
        to = now + AGENDA_DEFAULT_AHEAD.inWholeSeconds,
        territory = query.territory?.let(GroupNames::normalize)?.takeIf { it.isNotEmpty() },
        types = listOfNotNull(query.type?.takeIf { it in AgirFormats.EVENT_TYPES }),
        limit = AGENDA_PAGE_LIMIT,
    )
    withContext(Dispatchers.IO) { agenda.query(filter) }
}

/** The filter of the agenda from the query, or null once a `400 bad_parameter` was answered. */
private suspend fun RoutingContext.agendaFilter(parameters: Parameters, nowSeconds: Long): AgendaFilter? {
    for (name in listOf("from", "to", "territory", "limit")) {
        if (parameters.getAll(name).orEmpty().size > 1) return refused(name)
    }
    val from = parameters["from"]?.let { it.toLongOrNull()?.takeIf { value -> value >= 0 } ?: return refused("from") }
        ?: (nowSeconds - AGENDA_DEFAULT_PAST.inWholeSeconds)
    val to = parameters["to"]?.let { it.toLongOrNull()?.takeIf { value -> value >= 0 } ?: return refused("to") }
        ?: (nowSeconds + AGENDA_DEFAULT_AHEAD.inWholeSeconds)
    if (from > to) return refused("to")
    val territory = parameters["territory"]?.let { raw ->
        if (raw.length > MAX_AGENDA_TERRITORY_LENGTH) return refused("territory")
        GroupNames.normalize(raw).takeIf { it.isNotEmpty() }
    }
    val types = parameters.getAll("type").orEmpty()
    if (types.size > MAX_AGENDA_ALTERNATIVES || types.any { it !in AgirFormats.EVENT_TYPES }) return refused("type")
    val organizers = parameters.getAll("organizer").orEmpty()
    if (organizers.size > MAX_AGENDA_ALTERNATIVES || organizers.any { !isEventId(it) }) return refused("organizer")
    val limit = parameters["limit"]?.let { raw ->
        raw.toIntOrNull()?.takeIf { it in 1..AgendaFilter.MAX_LIMIT } ?: return refused("limit")
    } ?: AgendaFilter.DEFAULT_LIMIT
    return AgendaFilter(from, to, territory, types.distinct(), organizers.distinct(), limit)
}

private suspend fun RoutingContext.refused(name: String): AgendaFilter? {
    badParameter(name)
    return null
}

/** 64 lowercase hexadecimal characters: an event id or a public key. */
private fun isEventId(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
