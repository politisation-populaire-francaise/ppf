package org.politisationpopulairefrancaise.server.api

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.agir.AgirSpace
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.events.TagIndex
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.groups.SimilarGroup
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import org.politisationpopulairefrancaise.server.relay.RelayConfig
import org.politisationpopulairefrancaise.server.relay.RelayKinds
import org.politisationpopulairefrancaise.server.revendications.RevendicationProposals

@Serializable
data class PublishResponse(val id: String, val status: String)

@Serializable
data class ApiError(val error: String, val message: String)

/** The `409 group_exists` answer: [existing] is the id of the group to join instead of creating a duplicate (D-028). */
@Serializable
data class GroupExistsError(val error: String, val message: String, val existing: String)

/** The `409 group_similar` answer (D-132): the rooms of close name on the same territory or a parent one. */
@Serializable
data class GroupSimilarError(val error: String, val message: String, val similar: List<SimilarGroup>)

/** Events above this size are refused before being read in full. */
const val MAX_EVENT_BYTES: Long = 64 * 1024

/** At most this many `tag=` and `identifier=` parameters per request, so that a query stays cheap. */
const val MAX_TAG_FILTERS: Int = 20

/**
 * The limits applied to POST /api/v1/events, all in memory and never tied to a client address:
 * a budget per authenticated public key, a ceiling for the whole server (keys are free to mint),
 * and the number of envelopes kept per recipient.
 */
class PostLimits(
    val perKey: RateLimiter,
    val global: RateLimiter,
    val wrapMaxPerRecipient: Int,
) {
    constructor(config: RelayConfig) : this(
        perKey = RateLimiter(config.postRatePerMinute),
        global = RateLimiter(config.globalPostRatePerMinute, maxTrackedKeys = 1),
        wrapMaxPerRecipient = config.wrapMaxPerRecipient,
    )

    companion object {
        /** The single key of the global bucket; it cannot collide with a hex public key. */
        const val GLOBAL_KEY: String = "*"
    }
}

/**
 * The signed-events API, the server side of the publication port and of the message relay:
 *
 * - POST /api/v1/events: publish one signed event (JSON body). Only events whose signature checks
 *   out, which fit the [PostLimits] and which the [PublicationPolicy] and the [GroupDirectory]
 *   allow are stored; nothing is logged about the sender. Encrypted envelopes (kind 1059) are
 *   stored as they are, never opened. A citizen demand goes to the review queue ([RevendicationProposals]);
 *   a content of the Agir space that its key may not publish directly goes to the queue of [AgirSpace].
 * - GET /api/v1/events?kind=&author=&identifier=&tag=<name>:<value>&since=&until=&limit=: newest first.
 * - GET /api/v1/events/{id}
 */
fun Route.eventsApi(
    repository: EventRepository?,
    policy: PublicationPolicy,
    limits: PostLimits,
    groups: GroupDirectory? = null,
    proposals: RevendicationProposals? = null,
    agir: AgirSpace? = null,
    /** Told of every event this route stored (the push wake-ups); must return at once. */
    onStored: (SignedEvent) -> Unit = {},
) {
    if (repository == null) {
        route("/api/v1/{...}") {
            handle {
                call.respond(HttpStatusCode.ServiceUnavailable, ApiError("database_unavailable", "The database is not configured."))
            }
        }
        return
    }
    val directory = groups ?: GroupDirectory(repository)
    // Without one, the rules of the Agir space still hold (only the cleanup of its queue is missing).
    val agirSpace = agir ?: AgirSpace.create(repository.dataSource, repository, policy)
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
            // A replay of a stored event costs nobody anything: neither a signature check nor a token.
            // A body with a stored id but another content is not authentic: its id does not match it.
            val stored = if (isLowercaseHex64(event.id)) withContext(Dispatchers.IO) { repository.find(event.id) } else null
            if (stored != null) {
                return@post if (stored.unsigned() == event.unsigned()) {
                    call.respond(HttpStatusCode.OK, PublishResponse(event.id, "duplicate"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ApiError("invalid_signature", "The id or the signature does not match the content."))
                }
            }
            // The server-wide ceiling comes before the signature check: it bounds the work a flood can cause.
            when (val ceiling = limits.global.tryAcquire(PostLimits.GLOBAL_KEY)) {
                is RateLimiter.Outcome.Limited -> return@post call.rateLimited(ceiling, "The server is receiving too many events; try again later.")
                RateLimiter.Outcome.Allowed -> Unit
            }
            if (!event.verify()) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("invalid_signature", "The id or the signature does not match the content."))
            }
            // The budget is per authenticated public key (the signature was just checked), never per address.
            when (val budget = limits.perKey.tryAcquire(event.pubkey)) {
                is RateLimiter.Outcome.Limited -> return@post call.rateLimited(budget, "Too many events from this key; try again later.")
                RateLimiter.Outcome.Allowed -> Unit
            }
            // A citizen demand is not published at once: it waits for the review of a person (202).
            if (proposals != null && policy.isCitizenDemand(event)) {
                return@post proposals.receive(call, event, limits.perKey)
            }
            when (val decision = policy.check(event)) {
                is PublicationPolicy.Decision.Refused -> return@post call.refuse(decision)
                PublicationPolicy.Decision.Allowed -> Unit
            }
            // Agir space (contract, section 5): a content the key may not publish directly waits for review (202) or is refused;
            // a cancellation by its author withdraws the version still waiting (200).
            if (agirSpace.concerns(event)) {
                when (val route = withContext(Dispatchers.IO) { agirSpace.route(event) }) {
                    AgirSpace.Route.Publish -> Unit
                    is AgirSpace.Route.Refuse -> return@post call.refuse(route.refusal)
                    AgirSpace.Route.Hold, is AgirSpace.Route.Withdraw -> {
                        when (val admission = withContext(Dispatchers.IO) { directory.admit(event) }) {
                            is PublicationPolicy.Decision.Refused -> return@post call.refuse(admission)
                            PublicationPolicy.Decision.Allowed -> return@post agirSpace.hold(call, event, route, limits.perKey)
                        }
                    }
                }
            }
            // Group rules that need the database: the group must exist, be open, be managed by this key,
            // and not duplicate another group of the same name on the same territory. A description is
            // checked and stored under a lock, so that two identical rooms cannot be created at once.
            val outcome = when (val admission = withContext(Dispatchers.IO) { directory.admitAndStore(event) }) {
                is GroupDirectory.Admission.Refused -> return@post call.refuse(admission.refusal)
                // A new room whose name resembles a verified organisation's waits for the team (D-132).
                is GroupDirectory.Admission.Held -> return@post call.respond(HttpStatusCode.Accepted, PublishResponse(event.id, "held"))
                is GroupDirectory.Admission.Stored -> admission.outcome
            }
            when (outcome) {
                StoreOutcome.STORED -> {
                    withContext(Dispatchers.IO) { agirSpace.stored(event) }
                    directory.stored(event)
                    if (event.kind == RelayKinds.GIFT_WRAP) {
                        // One recipient per envelope (policy); the mailbox keeps the newest envelopes only.
                        val recipient = event.firstTagValue("p")
                        if (recipient != null) {
                            withContext(Dispatchers.IO) { repository.keepNewestByTag(RelayKinds.GIFT_WRAP, "p", recipient, limits.wrapMaxPerRecipient) }
                        }
                    }
                    onStored(event)
                    call.respond(HttpStatusCode.Created, PublishResponse(event.id, "stored"))
                }
                StoreOutcome.DUPLICATE -> {
                    limits.perKey.refund(event.pubkey)
                    call.respond(HttpStatusCode.OK, PublishResponse(event.id, "duplicate"))
                }
                StoreOutcome.SUPERSEDED -> {
                    // Replaying an old version of someone's content must not drain their budget.
                    limits.perKey.refund(event.pubkey)
                    call.respond(HttpStatusCode.OK, PublishResponse(event.id, "superseded"))
                }
            }
        }

        get {
            val parameters = call.request.queryParameters
            val kinds = parameters.getAll("kind").orEmpty().map { it.toIntOrNull() ?: return@get badParameter("kind") }
            val authors = parameters.getAll("author").orEmpty()
            if (authors.any { !isLowercaseHex64(it) }) return@get badParameter("author")
            val identifiers = parameters.getAll("identifier").orEmpty()
            if (identifiers.any { !TagIndex.isIndexable("d", it) }) return@get badParameter("identifier")
            val rawTags = parameters.getAll("tag").orEmpty()
            if (identifiers.size + rawTags.size > MAX_TAG_FILTERS) return@get badParameter("tag")
            val tags = parseTagFilters(rawTags) ?: return@get badParameter("tag")
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

/** Answers a refusal of the policy or of the group directory; `group_exists` carries the group to join. */
private suspend fun RoutingCall.refuse(decision: PublicationPolicy.Decision.Refused) {
    val status = HttpStatusCode.fromValue(decision.status)
    val existing = decision.existing
    if (existing != null) {
        respond(status, GroupExistsError(decision.error, decision.message, existing))
    } else if (decision.similar.isNotEmpty()) {
        respond(status, GroupSimilarError(decision.error, decision.message, decision.similar))
    } else {
        respond(status, ApiError(decision.error, decision.message))
    }
}

private suspend fun RoutingCall.rateLimited(outcome: RateLimiter.Outcome.Limited, message: String) {
    response.headers.append(HttpHeaders.RetryAfter, outcome.retryAfterSeconds.toString())
    respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", message))
}

internal suspend fun io.ktor.server.routing.RoutingContext.badParameter(name: String) =
    call.respond(HttpStatusCode.BadRequest, ApiError("bad_parameter", "Invalid value for \"$name\"."))

private fun isLowercaseHex64(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
