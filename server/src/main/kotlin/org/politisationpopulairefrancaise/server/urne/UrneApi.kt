package org.politisationpopulairefrancaise.server.urne

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.urne.Ballot
import org.politisationpopulairefrancaise.core.urne.BallotReceipt
import org.politisationpopulairefrancaise.core.urne.Difficulties
import org.politisationpopulairefrancaise.core.urne.Nip98
import org.politisationpopulairefrancaise.core.urne.Nip98ReplayGuard
import org.politisationpopulairefrancaise.core.urne.UrneList
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import java.time.Clock

/**
 * The ballot-box API (D-110, `docs/FORMATS.md`, section 4 decies). No request is ever logged
 * (no request plugin is installed), and no answer quotes a value of the request.
 *
 * Public: GET /api/v1/urnes?after=&limit=, GET /api/v1/urnes/{number}/journal, GET /api/v1/urnes/{number}/closing.
 * With the account (NIP-98): POST /api/v1/urnes/register, GET /api/v1/urnes/me, POST /api/v1/urnes/tokens.
 * Anonymous: POST /api/v1/ballots, with a proof of work and without any authentication.
 */
fun Route.urneApi(service: UrneService?, config: UrneConfig, clock: Clock = Clock.systemUTC()) {
    val replay = Nip98ReplayGuard()
    val perKey = RateLimiter(TOKEN_REQUESTS_PER_MINUTE)
    val ballots = RateLimiter(BALLOTS_PER_MINUTE, maxTrackedKeys = 1)

    suspend fun RoutingCall.unavailable() = respond(HttpStatusCode.ServiceUnavailable, ApiError("voting_unavailable", "Voting is not available on this server."))

    suspend fun RoutingCall.refuse(refusal: UrneRefusal) = respond(HttpStatusCode.fromValue(refusal.status), ApiError(refusal.code, MESSAGES[refusal.code] ?: "The request was refused."))

    /** The account behind a NIP-98 authorization, or null after answering 401 or 429. */
    suspend fun RoutingCall.account(body: String?, difficulty: Int): String? {
        val now = clock.instant().epochSecond
        val path = request.uri
        val matches: (String) -> Boolean = { u -> config.publicUrl?.let { u == it + path } ?: (u.substringAfter("://").substringAfter('/', "").let { "/$it" } == path) }
        return when (val result = Nip98.verify(request.headers[HttpHeaders.Authorization], matches, request.httpMethod.value, body?.encodeToByteArray(), now, difficulty)) {
            is Nip98.Result.Invalid -> {
                respond(HttpStatusCode.Unauthorized, ApiError(result.reason, "The NIP-98 authorization is missing or invalid."))
                null
            }
            is Nip98.Result.Valid -> {
                if (!synchronized(replay) { replay.firstUse(result.eventId, now) }) {
                    respond(HttpStatusCode.Unauthorized, ApiError("replayed_authorization", "This authorization was already used."))
                    return null
                }
                when (val budget = perKey.tryAcquire(result.pubkey)) {
                    is RateLimiter.Outcome.Limited -> {
                        response.header(HttpHeaders.RetryAfter, budget.retryAfterSeconds.toString())
                        respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "Too many requests from this account; try again later."))
                        null
                    }
                    RateLimiter.Outcome.Allowed -> result.pubkey
                }
            }
        }
    }

    suspend fun RoutingCall.boundedBody(max: Long): String? {
        if ((request.contentLength() ?: 0) > max) {
            respond(HttpStatusCode.PayloadTooLarge, ApiError("too_large", "The body is too large."))
            return null
        }
        val text = receiveText()
        if (text.length > max) {
            respond(HttpStatusCode.PayloadTooLarge, ApiError("too_large", "The body is too large."))
            return null
        }
        return text
    }

    route("/api/v1/urnes") {
        get {
            if (service == null || !service.available) return@get call.unavailable()
            val after = call.request.queryParameters["after"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val difficulties = Difficulties(config.registerDifficulty, config.tokenDifficulty, config.ballotDifficulty)
            call.respond(UrneList(withContext(Dispatchers.IO) { service.list(after, limit) }, difficulties))
        }
        get("/{number}/journal") {
            if (service == null || !service.available) return@get call.unavailable()
            val number = call.parameters["number"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_urne", "No such ballot box."))
            try {
                call.respond(withContext(Dispatchers.IO) { service.journal(number) })
            } catch (r: UrneRefusal) {
                call.refuse(r)
            }
        }
        get("/{number}/closing") {
            if (service == null || !service.available) return@get call.unavailable()
            val number = call.parameters["number"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_urne", "No such ballot box."))
            try {
                call.respond(withContext(Dispatchers.IO) { service.closing(number) })
            } catch (r: UrneRefusal) {
                call.refuse(r)
            }
        }
        post("/register") {
            if (service == null || !service.available) return@post call.unavailable()
            val body = call.boundedBody(0) ?: return@post
            val pubkey = call.account(body, config.registerDifficulty) ?: return@post
            val (status, created) = withContext(Dispatchers.IO) { service.register(pubkey) }
            call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK, status)
        }
        get("/me") {
            if (service == null || !service.available) return@get call.unavailable()
            val pubkey = call.account(null, 0) ?: return@get
            try {
                call.respond(withContext(Dispatchers.IO) { service.status(pubkey) })
            } catch (r: UrneRefusal) {
                call.refuse(r)
            }
        }
        post("/tokens") {
            if (service == null || !service.available) return@post call.unavailable()
            val body = call.boundedBody(MAX_TOKEN_REQUEST_BYTES) ?: return@post
            val pubkey = call.account(body, config.tokenDifficulty) ?: return@post
            try {
                call.respond(withContext(Dispatchers.IO) { service.issue(pubkey, body) })
            } catch (r: UrneRefusal) {
                call.refuse(r)
            }
        }
    }

    post("/api/v1/ballots") {
        if (service == null || !service.available) return@post call.unavailable()
        when (val ceiling = ballots.tryAcquire("*")) {
            is RateLimiter.Outcome.Limited -> {
                call.response.header(HttpHeaders.RetryAfter, ceiling.retryAfterSeconds.toString())
                return@post call.respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "Too many ballots at the moment; try again later."))
            }
            RateLimiter.Outcome.Allowed -> Unit
        }
        val body = call.boundedBody(MAX_BALLOT_BYTES) ?: return@post
        val ballot = runCatching { Json.decodeFromString(Ballot.serializer(), body) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("malformed_ballot", MESSAGES.getValue("malformed_ballot")))
        try {
            withContext(Dispatchers.IO) { service.ballot(ballot) }
            // The same answer for a new ballot and for a replaced one.
            call.respond(HttpStatusCode.Accepted, BallotReceipt("recorded"))
        } catch (r: UrneRefusal) {
            call.refuse(r)
        }
    }
}

private const val TOKEN_REQUESTS_PER_MINUTE = 30
private const val BALLOTS_PER_MINUTE = 6000
private const val MAX_TOKEN_REQUEST_BYTES = 512L * 1024
private const val MAX_BALLOT_BYTES = 4L * 1024

private val MESSAGES = mapOf(
    "voting_unavailable" to "Voting is not available on this server.",
    "unknown_urne" to "No such ballot box.",
    "not_closed" to "The ballot box is still open.",
    "not_registered" to "This account is not registered to vote.",
    "malformed_request" to "The request is not a token request.",
    "too_many_tokens" to "Too many tokens in one request.",
    "pending_changed" to "The ballot boxes to serve have changed; ask for the status again.",
    "invalid_element" to "A blinded element is not valid.",
    "urne_closed" to "The ballot box is not open.",
    "unknown_level" to "Unknown level.",
    "unknown_choice" to "This choice is not allowed in this ballot box.",
    "malformed_ballot" to "The body is not a ballot.",
    "proof_of_work_too_low" to "The proof of work is too low.",
    "invalid_token" to "The token does not match this ballot box.",
)
