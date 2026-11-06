package org.politisationpopulairefrancaise.server.api

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

@Serializable
data class PublishResponse(val id: String, val status: String)

@Serializable
data class ApiError(val error: String, val message: String)

/** Events above this size are refused before being read in full. */
const val MAX_EVENT_BYTES: Long = 64 * 1024

/**
 * The signed-events API, the server side of the publication port:
 *
 * - POST /api/v1/events: publish one signed event (JSON body). Only events whose signature checks
 *   out and which the [PublicationPolicy] allows are stored; nothing is logged about the sender.
 * - GET /api/v1/events?kind=&author=&identifier=&since=&until=&limit=: newest first.
 * - GET /api/v1/events/{id}
 */
fun Route.eventsApi(repository: EventRepository?, policy: PublicationPolicy) {
    if (repository == null) {
        route("/api/v1/{...}") {
            handle {
                call.respond(HttpStatusCode.ServiceUnavailable, ApiError("database_unavailable", "The database is not configured."))
            }
        }
        return
    }
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
            when (val decision = policy.check(event)) {
                is PublicationPolicy.Decision.Refused ->
                    return@post call.respond(HttpStatusCode.fromValue(decision.status), ApiError(decision.error, decision.message))
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
            val since = parameters["since"]?.let { it.toLongOrNull() ?: return@get badParameter("since") }
            val until = parameters["until"]?.let { it.toLongOrNull() ?: return@get badParameter("until") }
            val limit = parameters["limit"]?.let { it.toIntOrNull() ?: return@get badParameter("limit") } ?: EventFilter.DEFAULT_LIMIT
            val filter = EventFilter(kinds, authors, identifiers, since, until, limit)
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

private suspend fun io.ktor.server.routing.RoutingContext.badParameter(name: String) =
    call.respond(HttpStatusCode.BadRequest, ApiError("bad_parameter", "Invalid value for \"$name\"."))

private fun isLowercaseHex64(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
