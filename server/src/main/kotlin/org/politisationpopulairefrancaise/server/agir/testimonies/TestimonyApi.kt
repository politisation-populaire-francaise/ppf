package org.politisationpopulairefrancaise.server.agir.testimonies

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyJson
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonySubmission
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyTransport
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.relay.RateLimiter

@Serializable
data class FileAnswer(val id: String)

@Serializable
data class TestimonyAnswer(val id: String, val status: String)

/** In-memory ceilings for the whole server (one-time keys sign the envelopes: no per-key budget is possible), never tied to an address. */
class TestimonyLimits(val files: RateLimiter = RateLimiter(120, maxTrackedKeys = 1), val submissions: RateLimiter = RateLimiter(60, maxTrackedKeys = 1))

/**
 * The testimony routes (docs/integration/agir-temoignages.md, section 3):
 *
 * - POST /api/v1/testimonies/files: one encrypted file, raw bytes (application/octet-stream).
 *   `201 {"id"}` (SHA-256 of the bytes), `200 {"id"}` when already there; `413 too_large`,
 *   `400 not_encrypted`, `507 storage_full`, `429 rate_limited`.
 * - POST /api/v1/testimonies: a [TestimonySubmission] (JSON). `202 {"id","status":"pending_review"}`,
 *   `200 {"id","status":"duplicate"}`; `400` `malformed` | `bad_envelope` | `wrong_recipient` |
 *   `bad_evidence_envelope` | `bad_evidence` | `bad_files` | `bad_stamps` | `invalid_signature`,
 *   `404 missing_file`, `409 file_taken`, `413 too_large`, `429 rate_limited`.
 * - GET /api/v1/testimonies/status?id=<hex>&id=<hex>: 1 to 20 envelope ids; the known ones, in the
 *   order asked, as [org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyState];
 *   `Cache-Control: no-store`. No authentication: only the witness knows the id of their envelope.
 * - GET /api/v1/media/{sha256}: a photo of a published testimony; `404` otherwise.
 *
 * When the testimonies are closed (no folder, or example keys in production), every route answers
 * `503 testimonies_unavailable`. Nothing of a request is logged or kept.
 */
fun Route.testimonyApi(space: TestimonySpace?, maxFileBytes: Long, limits: TestimonyLimits = TestimonyLimits()) {
    route("/api/v1/testimonies") {
        post("files") {
            space ?: return@post call.unavailable()
            val declared = call.request.contentLength()
            if (declared != null && declared > maxFileBytes) return@post call.refuse(TestimonySpace.TOO_LARGE)
            if (limits.files.tryAcquire(GLOBAL) is RateLimiter.Outcome.Limited) return@post call.limited()
            val bytes = call.receiveChannel().readRemaining(maxFileBytes + 1).readByteArray()
            if (bytes.size > maxFileBytes) return@post call.refuse(TestimonySpace.TOO_LARGE)
            when (val outcome = withContext(Dispatchers.IO) { space.receiveFile(bytes) }) {
                is FileOutcome.Stored -> call.respond(if (outcome.created) HttpStatusCode.Created else HttpStatusCode.OK, FileAnswer(outcome.id))
                is FileOutcome.Refused -> call.refuse(outcome.refusal)
            }
        }
        post {
            space ?: return@post call.unavailable()
            val declared = call.request.contentLength()
            if (declared != null && declared > TestimonySpace.MAX_SUBMISSION_BYTES) {
                return@post call.refuse(TestimonyRefusal(413, "too_large", "A testimony is at most ${TestimonySpace.MAX_SUBMISSION_BYTES} bytes."))
            }
            if (limits.submissions.tryAcquire(GLOBAL) is RateLimiter.Outcome.Limited) return@post call.limited()
            val body = call.receiveChannel().readRemaining(TestimonySpace.MAX_SUBMISSION_BYTES + 1).readByteArray()
            if (body.size > TestimonySpace.MAX_SUBMISSION_BYTES) {
                return@post call.refuse(TestimonyRefusal(413, "too_large", "A testimony is at most ${TestimonySpace.MAX_SUBMISSION_BYTES} bytes."))
            }
            val submission = runCatching { TestimonyJson.decodeFromString(TestimonySubmission.serializer(), body.decodeToString()) }.getOrNull()
                ?: return@post call.refuse(TestimonyRefusal(400, "malformed", "The body is not a testimony."))
            when (val outcome = withContext(Dispatchers.IO) { space.submit(submission) }) {
                is SubmitOutcome.Queued -> call.respond(HttpStatusCode.Accepted, TestimonyAnswer(outcome.id, "pending_review"))
                is SubmitOutcome.Duplicate -> call.respond(HttpStatusCode.OK, TestimonyAnswer(outcome.id, "duplicate"))
                is SubmitOutcome.Refused -> call.refuse(outcome.refusal)
            }
        }
        get("status") {
            space ?: return@get call.unavailable()
            val ids = call.request.queryParameters.getAll("id").orEmpty()
            if (ids.isEmpty() || ids.size > TestimonyTransport.MAX_IDS || ids.any { !MediaDigest.isHex(it) }) {
                return@get call.respond(HttpStatusCode.BadRequest, ApiError("bad_parameter", "Invalid value for \"id\"."))
            }
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(withContext(Dispatchers.IO) { space.statuses(ids) })
        }
    }
    get("/api/v1/media/{sha256}") {
        space ?: return@get call.unavailable()
        val sha = call.parameters["sha256"].orEmpty()
        val media = withContext(Dispatchers.IO) { space.publicMedia(sha) }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("not_found", "No published photo with this SHA-256."))
        // Named by its content: it never changes.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondBytes(media.second, ContentType.parse(media.first))
    }
}

private const val GLOBAL = "*"

private suspend fun RoutingCall.unavailable() =
    respond(HttpStatusCode.ServiceUnavailable, ApiError("testimonies_unavailable", "Testimonies are not open on this server."))

private suspend fun RoutingCall.limited() {
    response.headers.append(HttpHeaders.RetryAfter, "60")
    respond(HttpStatusCode.TooManyRequests, ApiError("rate_limited", "The server is receiving too many testimonies; try again later."))
}

private suspend fun RoutingCall.refuse(refusal: TestimonyRefusal) = respond(HttpStatusCode.fromValue(refusal.status), ApiError(refusal.code, refusal.message))
