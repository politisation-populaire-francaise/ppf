package org.politisationpopulairefrancaise.server.api

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.events.TagIndex
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.relay.RateLimiter

@Serializable
data class PublishResponse(val id: String, val status: String)

@Serializable
data class ApiError(val error: String, val message: String)

/** Events above this size are refused before being read in full. */
const val MAX_EVENT_BYTES: Long = 64 * 1024

/**
 * The signed-events API, the server side of the publication port and of the message relay:
 *
 * - POST /api/v1/events: publish one signed event (JSON body). Only events whose signature checks
 *   out, whose author is within the [RateLimiter] budget and which the [PublicationPolicy] allows
 *   are stored; nothing is logged about the sender. Encrypted envelopes (kind 1059) are stored as
 *   they are, never opened.
 * - GET /api/v1/events?kind=&author=&identifier=&tag=<name>:<value>&since=&until=&limit=: newest first.
 * - GET /api/v1/events/{id}
 */
fun Route.eventsApi(repository: EventRepository?, policy: PublicationPolicy, rateLimiter: RateLimiter, groups: GroupDirectory? = null) {
    if (repository == null) {
        route("/api/v1/{...}") {
            handle {
                call.respond(HttpStatusCode.ServiceUnavailable, ApiError("database_unavailable", "The database is not configured."))
            }
        }
        return
    }
    val directory = groups ?: GroupDirectory(repository)
    route("/api/v1/events") {
        post {
            val declaredLength = call.request.contentLength() ?: 0
            if (declaredLength > MAX_EVENT_BYTES) {
                return@post call.respond(HttpStatusCode.PayloadTooLarge, ApiError("too_large", "An event is at most $MAX_EVENT_BYTES bytes."))
            }
            val body = call.receiveText()
            if (body.length > MAX_EVENT_BYTES) {
                return@post call.respond(HttpStatusCode.PayloadTooLarge, ApiError("too_large", "An event is at most $MAX_EVENT_BYTES bytes."))
            }
            val event = runCatching { SignedEvent.fromJson(body) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("malformed_event", "The body is not a signed event."))
            if (!event.verify()) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("invalid_signature", "The id or the signature does not match the content."))
            }
            // The budget is per authenticated public key (the signature was just checked), never per address.
            when (val budget = rateLimiter.tryAcquire(event.pubkey)) {
                is RateLimiter.Outcome.Limited -> {
                    call.response.headers.append(HttpHeaders.RetryAfter, budget.retryAfterSeconds.toString())
                    return@post call.respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "Too many events from this key; try again later."))
                }
                RateLimiter.Outcome.Allowed -> Unit
            }
            when (val decision = policy.check(event)) {
                is PublicationPolicy.Decision.Refused ->
                    return@post call.respond(HttpStatusCode.fromValue(decision.status), ApiError(decision.error, decision.message))
                PublicationPolicy.Decision.Allowed -> Unit
            }
            // Group rules that need the database: the group must exist, be open, or be managed by this key.
            when (val admission = withContext(Dispatchers.IO) { directory.admit(event) }) {
                is PublicationPolicy.Decision.Refused ->
                    return@post call.respond(HttpStatusCode.fromValue(admission.status), ApiError(admission.error, admission.message))
                PublicationPolicy.Decision.Allowed -> Unit
            }
            val outcome = withContext(Dispatchers.IO) { repository.store(event) }
            when (outcome) {
                StoreOutcome.STORED -> call.respond(HttpStatusCode.Created, PublishResponse(event.id, "stored"))
                StoreOutcome.DUPLICATE -> call.respond(HttpStatusCode.OK, PublishResponse(event.id, "duplicate"))
                StoreOutcome.SUPERSEDED -> call.respond(HttpStatusCode.OK, PublishResponse(event.id, "superseded"))
            }
        }

        get {
            val parameters = call.request.queryParameters
            val kinds = parameters.getAll("kind").orEmpty().map { it.toIntOrNull() ?: return@get badParameter("kind") }
            val authors = parameters.getAll("author").orEmpty()
            if (authors.any { !isLowercaseHex64(it) }) return@get badParameter("author")
            val identifiers = parameters.getAll("identifier").orEmpty()
            val tags = parseTagFilters(parameters.getAll("tag").orEmpty()) ?: return@get badParameter("tag")
            val since = parameters["since"]?.let { it.toLongOrNull() ?: return@get badParameter("since") }
            val until = parameters["until"]?.let { it.toLongOrNull() ?: return@get badParameter("until") }
            val limit = parameters["limit"]?.let { it.toIntOrNull() ?: return@get badParameter("limit") } ?: EventFilter.DEFAULT_LIMIT
            val filter = EventFilter(kinds, authors, identifiers, tags, since, until, limit)
            call.respond(withContext(Dispatchers.IO) { repository.query(filter) })
        }

        get("{id}") {
            val id = call.parameters["id"] ?: return@get badParameter("id")
            if (!isLowercaseHex64(id)) return@get badParameter("id")
            val event = withContext(Dispatchers.IO) { repository.find(id) }
            if (event == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("not_found", "No event with this id."))
            } else {
                call.respond(event)
            }
        }
    }
}

/** At most this many `tag=` parameters per request, so that a query stays cheap. */
const val MAX_TAG_FILTERS: Int = 20

/**
 * Parses repeatable `tag=<name>:<value>` parameters: the first colon separates the name from the
 * value, which may itself contain colons. Values of the same name are alternatives, different
 * names are all required. Returns null when a parameter is malformed or the limits are exceeded.
 */
fun parseTagFilters(raw: List<String>): Map<String, List<String>>? {
    if (raw.size > MAX_TAG_FILTERS) return null
    val tags = mutableMapOf<String, MutableList<String>>()
    for (parameter in raw) {
        val separator = parameter.indexOf(':')
        if (separator < 1) return null
        val name = parameter.substring(0, separator)
        val value = parameter.substring(separator + 1)
        if (!TagIndex.isIndexable(name, value)) return null
        tags.getOrPut(name) { mutableListOf() } += value
    }
    return tags
}

internal suspend fun io.ktor.server.routing.RoutingContext.badParameter(name: String) =
    call.respond(HttpStatusCode.BadRequest, ApiError("bad_parameter", "Invalid value for \"$name\"."))

private fun isLowercaseHex64(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
