package org.politisationpopulairefrancaise.server.contact

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import org.politisationpopulairefrancaise.core.contact.CaseApi
import org.politisationpopulairefrancaise.core.contact.CaseCreated
import org.politisationpopulairefrancaise.core.contact.CaseCreation
import org.politisationpopulairefrancaise.core.contact.CaseFollowUp
import org.politisationpopulairefrancaise.core.contact.CaseMessageAccepted
import org.politisationpopulairefrancaise.core.contact.CaseState
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.relay.RateLimiter

/**
 * In-memory ceilings for the whole server, like the testimonies of Agir (the envelopes are signed by
 * one-time keys: no budget per key is possible), never tied to an address: 30 creations and 120 messages a
 * minute.
 */
class ContactLimits(
    val creations: RateLimiter = RateLimiter(30, maxTrackedKeys = 1),
    val messages: RateLimiter = RateLimiter(120, maxTrackedKeys = 1),
)

/**
 * The public routes of the cases (docs/integration/contact-reclamations.md, section 6), on the public port:
 *
 * - `POST /api/v1/cases`: a [CaseCreation]. `201` [CaseCreated]; `200` for the very same creation sent again;
 *   `409 number_taken` | `envelope_taken`; `422` for a rule (`bad_number`, `bad_nature`, `bad_access_hash`,
 *   `bad_case_pubkey`, `decision_required`, `bad_decision`, `decision_out_of_time`, and the codes of the envelope:
 *   `bad_envelope`, `wrong_recipient`, `too_large`, `bad_date`, `not_enough_work`, `invalid_signature`);
 *   `400 malformed`; `413 too_large`; `429 rate_limited`.
 * - `GET /api/v1/cases/{number}?after=<id>` with `Authorization: Bearer <token>`: [CaseState]; `404 not_found`
 *   for an unknown number as for a wrong token; `429 locked` after 5 wrong tries on a number (15 minutes).
 * - `POST /api/v1/cases/{number}/messages` with the token: a [CaseFollowUp]. `201` [CaseMessageAccepted],
 *   `200` when the envelope was already received; `409 case_closed` | `envelope_taken`; `422` for the envelope;
 *   `404`, `429` as above.
 *
 * When the cases are closed (no inbox key), every route answers `503 contact_not_open`. Answers are never
 * cached; nothing of a request is logged or kept beyond the envelope and the metadata.
 */
fun Route.contactApi(space: ContactSpace?, limits: ContactLimits = ContactLimits()) {
    route(CaseApi.CASES) {
        post {
            space ?: return@post call.closed()
            val body = call.boundedBody(ContactSpace.MAX_CREATION_BYTES) ?: return@post call.tooLarge()
            if (limits.creations.tryAcquire(GLOBAL) is RateLimiter.Outcome.Limited) return@post call.limited()
            val request = decode(CaseCreation.serializer(), body) ?: return@post call.malformed()
            when (val outcome = withContext(Dispatchers.IO) { space.create(request) }) {
                is CreateOutcome.Created -> call.answer(HttpStatusCode.Created, CaseCreated.serializer(), outcome.answer)
                is CreateOutcome.Again -> call.answer(HttpStatusCode.OK, CaseCreated.serializer(), outcome.answer)
                is CreateOutcome.Refused -> call.refuse(outcome.refusal)
            }
        }
        get("{number}") {
            space ?: return@get call.closed()
            val number = call.parameters["number"].orEmpty()
            val outcome = withContext(Dispatchers.IO) { space.state(number, call.request.header(HttpHeaders.Authorization), call.request.queryParameters["after"]) }
            when (outcome) {
                is ReadOutcome.Found -> call.answer(HttpStatusCode.OK, CaseState.serializer(), outcome.state)
                is ReadOutcome.Refused -> call.refuse(outcome.refusal, outcome.retryAfterSeconds)
            }
        }
        post("{number}/messages") {
            space ?: return@post call.closed()
            val body = call.boundedBody(ContactSpace.MAX_MESSAGE_BYTES) ?: return@post call.tooLarge()
            if (limits.messages.tryAcquire(GLOBAL) is RateLimiter.Outcome.Limited) return@post call.limited()
            val request = decode(CaseFollowUp.serializer(), body) ?: return@post call.malformed()
            val number = call.parameters["number"].orEmpty()
            val outcome = withContext(Dispatchers.IO) { space.followUp(number, call.request.header(HttpHeaders.Authorization), request) }
            when (outcome) {
                is FollowUpOutcome.Accepted ->
                    call.answer(if (outcome.created) HttpStatusCode.Created else HttpStatusCode.OK, CaseMessageAccepted.serializer(), outcome.answer)
                is FollowUpOutcome.Refused -> call.refuse(outcome.refusal, outcome.retryAfterSeconds)
            }
        }
    }
}

private const val GLOBAL = "*"

private fun <T> decode(serializer: KSerializer<T>, body: ByteArray): T? =
    runCatching { CaseApi.json.decodeFromString(serializer, body.decodeToString()) }.getOrNull()

/** The body, or null when it is (or announces itself) larger than [max] bytes. */
private suspend fun RoutingCall.boundedBody(max: Long): ByteArray? {
    val declared = request.contentLength()
    if (declared != null && declared > max) return null
    val bytes = receiveChannel().readRemaining(max + 1).readByteArray()
    return bytes.takeIf { it.size <= max }
}

private suspend fun <T> RoutingCall.answer(status: HttpStatusCode, serializer: KSerializer<T>, value: T) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondText(CaseApi.json.encodeToString(serializer, value), ContentType.Application.Json, status)
}

private suspend fun RoutingCall.closed() =
    respond(HttpStatusCode.ServiceUnavailable, ApiError("contact_not_open", "Contact and claims are not open on this server."))

private suspend fun RoutingCall.tooLarge() =
    respond(HttpStatusCode.PayloadTooLarge, ApiError("too_large", "The request is larger than the server accepts."))

private suspend fun RoutingCall.malformed() =
    respond(HttpStatusCode.BadRequest, ApiError("malformed", "The body is not a request of this route."))

private suspend fun RoutingCall.limited() {
    response.headers.append(HttpHeaders.RetryAfter, "60")
    respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "The server is receiving too many messages; try again later."))
}

private suspend fun RoutingCall.refuse(refusal: ContactRefusal, retryAfter: Long? = null) {
    retryAfter?.let { response.headers.append(HttpHeaders.RetryAfter, it.toString()) }
    response.header(HttpHeaders.CacheControl, "no-store")
    respond(HttpStatusCode.fromValue(refusal.status), ApiError(refusal.code, refusal.message))
}
