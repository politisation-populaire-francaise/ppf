package org.politisationpopulairefrancaise.signer

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.politisationpopulairefrancaise.core.platform.DraftRequest
import org.politisationpopulairefrancaise.core.platform.DraftResponse
import org.politisationpopulairefrancaise.core.platform.DraftStatusResponse
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.SignRequest
import org.politisationpopulairefrancaise.core.platform.SignResponse
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.security.MessageDigest

/**
 * The token the server presents in `Authorization: Bearer …`, compared in constant time (the SHA-256 of
 * both sides, so that neither the content nor the length leaks through timing).
 */
class ClientToken(secret: String) {
    private val digest: ByteArray = sha256(secret)

    fun matches(presented: String?): Boolean = presented != null && MessageDigest.isEqual(digest, sha256(presented))

    private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))

    override fun toString(): String = "ClientToken(***)"
}

@Serializable
internal data class LoadResult(val id: String?, val status: String)

@Serializable
internal data class ErrorBody(val status: String = SignResponse.REFUSED, val code: String)

/**
 * The internal HTTP API of the service (D-129), on its own port, reachable only by the server on a
 * dedicated network (no TLS inside the Docker network; the caller is authenticated by [token]). Nothing of
 * the approval pages ([approvalModule]) answers here, and nothing of this API on the approval port.
 *
 * - `GET /health`: liveness, without authentication, says nothing else;
 * - `GET /v1/keys`: public keys per use, their certificate state and last signature, the end of the team list;
 * - `POST /v1/drafts`: `{use, event, double_validation, author}` of a human use → `201 {"status":"submitted",
 *   "draft":id,"approval_url":…}` (`200` when the same draft was known), or a refusal (`422`, `409`, `429`);
 * - `GET /v1/drafts/{use}/{id}`: the approvals recorded so far → `200`, `404 unknown_draft`, `409 already_signed`;
 * - `POST /v1/sign`: `{use, event}` → `200 {"status":"signed","event":…}` or a refusal
 *   `{"status":"refused","code":…}` (`422`, `409 already_signed`, `429 rate_limited`, `400 malformed_request`);
 *   a body that still carries an `approval` is refused (`unexpected_approval`);
 * - `POST /v1/certificates`: root-signed certificates and revocations (an array of events) → one status each;
 * - `POST /v1/team-list`: the root-signed team list → its status;
 * - `GET /v1/certificates`, `GET /v1/team-list`: what the service holds (public);
 * - `GET /v1/journal?after=<seq>&limit=<n>` and `GET /v1/journal/head`: the journal, to copy it into the
 *   audit log of the back-office and to anchor its head.
 *
 * Every answer has `Cache-Control: no-store`. Nothing about the caller is logged.
 */
fun Application.signerModule(service: SignerService, token: ClientToken) {
    routing {
        get("/health") {
            call.respondJson(HttpStatusCode.OK, """{"status":"ok","service":"ppf-signer"}""")
        }
        get("/v1/keys") {
            if (!call.authorized(token)) return@get
            val keys = withContext(Dispatchers.IO) { service.keys() }
            call.respondJson(HttpStatusCode.OK, encode(KeysResponse.serializer(), keys))
        }
        post("/v1/drafts") {
            if (!call.authorized(token)) return@post
            val body = call.boundedBody(MAX_SIGN_BODY) ?: return@post
            val request = try {
                SigningProtocol.json.decodeFromString(DraftRequest.serializer(), body)
            } catch (e: Exception) {
                return@post call.refusal(HttpStatusCode.BadRequest, SigningRefusals.MALFORMED_REQUEST)
            }
            when (val outcome = withContext(Dispatchers.IO) { service.submitDraft(request) }) {
                is DraftOutcome.Submitted -> {
                    val response = DraftResponse(DraftResponse.SUBMITTED, draft = outcome.id, approvalUrl = outcome.approvalUrl)
                    call.respondJson(if (outcome.created) HttpStatusCode.Created else HttpStatusCode.OK, encode(DraftResponse.serializer(), response))
                }
                is DraftOutcome.Refused -> {
                    val response = DraftResponse(DraftResponse.REFUSED, code = outcome.refusal.code, detail = outcome.refusal.detail)
                    call.respondJson(statusOf(outcome.refusal.code), encode(DraftResponse.serializer(), response))
                }
            }
        }
        get("/v1/drafts/{use}/{id}") {
            if (!call.authorized(token)) return@get
            val use = call.parameters["use"].orEmpty()
            val id = call.parameters["id"].orEmpty()
            when (val state = withContext(Dispatchers.IO) { service.draftState(use, id) }) {
                is DraftState.Pending -> call.respondJson(HttpStatusCode.OK, encode(DraftStatusResponse.serializer(), state.status))
                DraftState.Signed -> call.refusal(HttpStatusCode.Conflict, SigningRefusals.ALREADY_SIGNED)
                DraftState.Unknown -> call.refusal(HttpStatusCode.NotFound, SigningRefusals.UNKNOWN_DRAFT)
            }
        }
        post("/v1/sign") {
            if (!call.authorized(token)) return@post
            val body = call.boundedBody(MAX_SIGN_BODY) ?: return@post
            val parsed = try {
                val element = SigningProtocol.json.parseToJsonElement(body) as JsonObject
                // Since version 1.2 the approvals are collected by the service itself: a body that still carries one is refused.
                SigningProtocol.json.decodeFromJsonElement(SignRequest.serializer(), element) to ("approval" in element || "assertions" in element)
            } catch (e: Exception) {
                return@post call.refusal(HttpStatusCode.BadRequest, SigningRefusals.MALFORMED_REQUEST)
            }
            val (request, carriesApproval) = parsed
            when (val outcome = withContext(Dispatchers.IO) { service.sign(request, carriesApproval) }) {
                is SignOutcome.Signed -> call.respondJson(HttpStatusCode.OK, encode(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, outcome.event)))
                is SignOutcome.Refused -> {
                    val status = statusOf(outcome.refusal.code)
                    if (status == HttpStatusCode.TooManyRequests) call.response.header(HttpHeaders.RetryAfter, "3600")
                    val response = SignResponse(SignResponse.REFUSED, code = outcome.refusal.code, detail = outcome.refusal.detail)
                    call.respondJson(status, encode(SignResponse.serializer(), response))
                }
            }
        }
        post("/v1/certificates") {
            if (!call.authorized(token)) return@post
            call.loadCertificates(service)
        }
        post("/v1/team-list") {
            if (!call.authorized(token)) return@post
            call.loadTeamList(service)
        }
        get("/v1/certificates") {
            if (!call.authorized(token)) return@get
            val events = withContext(Dispatchers.IO) { service.certificates() }
            call.respondJson(HttpStatusCode.OK, encode(ListSerializer(SignedEvent.serializer()), events))
        }
        get("/v1/team-list") {
            if (!call.authorized(token)) return@get
            val event = withContext(Dispatchers.IO) { service.teamListEvent() }
                ?: return@get call.refusal(HttpStatusCode.NotFound, SigningRefusals.NO_TEAM_LIST)
            call.respondJson(HttpStatusCode.OK, event.toJson())
        }
        get("/v1/journal") {
            if (!call.authorized(token)) return@get
            val after: Long = call.request.queryParameters["after"]?.let { it.toLongOrNull() ?: -1L } ?: 0L
            val limit: Int = call.request.queryParameters["limit"]?.let { it.toIntOrNull() ?: -1 } ?: 500
            if (after < 0 || limit !in 1..1_000) return@get call.refusal(HttpStatusCode.BadRequest, SigningRefusals.MALFORMED_REQUEST)
            val entries = withContext(Dispatchers.IO) { service.journal(after, limit) }
            call.respondJson(HttpStatusCode.OK, encode(ListSerializer(JournalEntry.serializer()), entries))
        }
        get("/v1/journal/head") {
            if (!call.authorized(token)) return@get
            val head = withContext(Dispatchers.IO) { service.journalHead() }
            call.respondJson(HttpStatusCode.OK, encode(JournalHead.serializer(), head))
        }
    }
}

private const val MAX_SIGN_BODY = 160 * 1024
internal const val MAX_LOAD_BODY = 512 * 1024
private const val MAX_CERTIFICATES = 100

/** The HTTP status of a refusal code. */
internal fun statusOf(code: String): HttpStatusCode = when (code) {
    SigningRefusals.RATE_LIMITED, SigningRefusals.TOO_MANY_DRAFTS -> HttpStatusCode.TooManyRequests
    SigningRefusals.ALREADY_SIGNED, SigningRefusals.DRAFT_CONFLICT, SigningRefusals.ALREADY_APPROVED, SigningRefusals.URN_ALREADY_CLOSED -> HttpStatusCode.Conflict
    else -> HttpStatusCode.UnprocessableEntity
}

internal fun <T> encode(serializer: KSerializer<T>, value: T): String = SigningProtocol.json.encodeToString(serializer, value)

internal suspend fun ApplicationCall.respondJson(status: HttpStatusCode, json: String) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondText(json, ContentType.Application.Json, status)
}

internal suspend fun ApplicationCall.refusal(status: HttpStatusCode, code: String) =
    respondJson(status, SigningProtocol.json.encodeToString(ErrorBody.serializer(), ErrorBody(code = code)))

/** Whether the call carries the token of the server; answers 401 otherwise. */
private suspend fun ApplicationCall.authorized(token: ClientToken): Boolean {
    val header = request.headers[HttpHeaders.Authorization]
    val presented = header?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
    if (token.matches(presented)) return true
    refusal(HttpStatusCode.Unauthorized, SigningRefusals.UNAUTHORIZED)
    return false
}

/** The body as UTF-8 text, at most [max] bytes (read through a bounded channel); answers 413 otherwise. */
internal suspend fun ApplicationCall.boundedBody(max: Int): String? {
    val bytes = receiveChannel().readBuffer(max.toLong() + 1).readByteArray()
    if (bytes.size > max) {
        refusal(HttpStatusCode.PayloadTooLarge, SigningRefusals.TOO_LARGE)
        return null
    }
    return String(bytes, Charsets.UTF_8)
}

/**
 * Root-signed certificates and revocations (one event or an array of 100 at most): one status each. The
 * same on the internal port (from the server) and on the approval port (from the owner's administration
 * app): the events authenticate themselves, and a direct upload means the main server cannot withhold one.
 */
internal suspend fun ApplicationCall.loadCertificates(service: SignerService) {
    val body = boundedBody(MAX_LOAD_BODY) ?: return
    val events = try {
        when (val element = SigningProtocol.json.parseToJsonElement(body)) {
            is JsonArray -> element.map { SigningProtocol.json.decodeFromJsonElement(SignedEvent.serializer(), it) }
            is JsonObject -> listOf(SigningProtocol.json.decodeFromJsonElement(SignedEvent.serializer(), element))
            else -> null
        }
    } catch (e: Exception) {
        null
    }
    if (events == null || events.size > MAX_CERTIFICATES) return refusal(HttpStatusCode.BadRequest, SigningRefusals.MALFORMED_REQUEST)
    val outcomes = withContext(Dispatchers.IO) { service.loadCertificates(events) }
    respondJson(HttpStatusCode.OK, encode(ListSerializer(LoadResult.serializer()), outcomes.map { LoadResult(it.id, it.status) }))
}

/** The root-signed team list: its status (internal port and approval port alike, see [loadCertificates]). */
internal suspend fun ApplicationCall.loadTeamList(service: SignerService) {
    val body = boundedBody(MAX_LOAD_BODY) ?: return
    val event = try {
        SignedEvent.fromJson(body)
    } catch (e: Exception) {
        return refusal(HttpStatusCode.BadRequest, SigningRefusals.MALFORMED_REQUEST)
    }
    val outcome = withContext(Dispatchers.IO) { service.loadTeamList(event) }
    val status = if (outcome.status in setOf("stored", "duplicate", "superseded")) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity
    respondJson(status, encode(LoadResult.serializer(), LoadResult(outcome.id, outcome.status)))
}
