package org.politisationpopulairefrancaise.server.agir.testimonies

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.agir.testimonies.OpenedTestimony
import org.politisationpopulairefrancaise.core.agir.testimonies.PublishedTestimony
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyReader
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyRecipients
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.server.api.ApiError

@Serializable
private data class DevPhoto(val sha256: String, val blurred: Boolean, val reencoded: Boolean, val url: String)

@Serializable
private data class DevTestimony(
    val id: String,
    val author: String,
    val text: String,
    @SerialName("fact_date") val factDate: String?,
    @SerialName("fact_time") val factTime: String?,
    val place: String?,
    val call: String?,
    val photos: List<DevPhoto>,
    @SerialName("original_given") val originalGiven: Boolean,
    @SerialName("received_at") val receivedAt: Long,
)

@Serializable
private data class DevTestimonyDecision(val id: String, val outcome: String)

/**
 * The development stand-in for the team's review of testimonies (127.0.0.1 only, no
 * authentication; the real back-office comes with M15, with two-factor authentication and an audit
 * log, and decrypts on the team's own devices, never on the server). Here the server opens the
 * envelopes with the EXAMPLE moderation key, whose secret is public:
 * - GET /dev/testimonies: the pending testimonies, decrypted;
 * - GET /dev/testimonies/{id}/photo/{n}: the n-th photo, decrypted (the copy cleaned by the device);
 * - POST /dev/testimonies/{id}/approve: publishes a public version signed by [editor], title "[EXEMPLE]
 *   Témoignage du <date>", the text and the photos as the witness sent them;
 * - POST /dev/testimonies/{id}/reject, with the reason as the request body.
 */
fun Route.devTestimonyRoutes(space: TestimonySpace, editor: EventSigner) {
    val moderation = LocalMessagingSigner(TestimonyRecipients.exampleModerationKeys())
    fun open(stored: StoredTestimony): OpenedTestimony? = runCatching { TestimonyReader.open(stored.envelope, moderation) }.getOrNull()
    fun photos(opened: OpenedTestimony): List<ByteArray> = opened.media.map { media ->
        TestimonyReader.decrypt(space.encryptedFile(media.fileId) ?: error("missing file"), media)
    }
    route("/dev/testimonies") {
        get {
            val pending = withContext(Dispatchers.IO) { space.pending() }.mapNotNull { stored ->
                val opened = open(stored) ?: return@mapNotNull null
                DevTestimony(
                    id = stored.id,
                    author = opened.author,
                    text = opened.text,
                    factDate = opened.factDate,
                    factTime = opened.factTime,
                    place = opened.place,
                    call = opened.callAddress,
                    photos = opened.media.mapIndexed { index, media -> DevPhoto(media.sha256, media.blurred, media.reencoded, "/dev/testimonies/${stored.id}/photo/$index") },
                    originalGiven = opened.originalGiven,
                    receivedAt = stored.receivedAt,
                )
            }
            call.respond(pending)
        }
        get("{id}/photo/{index}") {
            val stored = withContext(Dispatchers.IO) { space.find(call.parameters["id"].orEmpty()) } ?: return@get call.notFound()
            val opened = open(stored) ?: return@get call.notFound()
            val index = call.parameters["index"]?.toIntOrNull() ?: return@get call.notFound()
            val media = opened.media.getOrNull(index) ?: return@get call.notFound()
            val bytes = withContext(Dispatchers.IO) { photos(opened)[index] }
            call.respondBytes(bytes, ContentType.parse(media.mimeType))
        }
        post("{id}/approve") {
            val id = call.parameters["id"].orEmpty()
            val stored = withContext(Dispatchers.IO) { space.find(id) } ?: return@post call.notFound()
            val opened = open(stored) ?: return@post call.notFound()
            val photos = withContext(Dispatchers.IO) { photos(opened) }
            val published = editor.sign(
                PublishedTestimony.unsigned(
                    editor = editor.publicKey,
                    title = "[EXEMPLE] Témoignage du ${opened.factDate ?: "jour non précisé"}",
                    text = opened.text,
                    factDate = opened.factDate,
                    factTime = opened.factTime,
                    place = opened.place,
                    callAddress = opened.callAddress,
                    photos = photos.zip(opened.media).map { (bytes, media) -> PublishedTestimony.Photo(bytes, media.width, media.height) },
                ),
            )
            call.answer(id, withContext(Dispatchers.IO) { space.approve(id, published, photos) })
        }
        post("{id}/reject") {
            val id = call.parameters["id"].orEmpty()
            val reason = call.receiveText().trim()
            if (reason.isEmpty() || reason.length > TestimonySpace.MAX_REASON_LENGTH) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("bad_reason", "Give the reason (1 to ${TestimonySpace.MAX_REASON_LENGTH} characters) as the request body."))
            }
            call.answer(id, withContext(Dispatchers.IO) { space.reject(id, reason) })
        }
    }
}

private suspend fun ApplicationCall.notFound() = respond(HttpStatusCode.NotFound, ApiError("not_found", "No such testimony or photo."))

private suspend fun ApplicationCall.answer(id: String, result: DecisionResult) = when (result) {
    is DecisionResult.Approved -> respond(DevTestimonyDecision(id, "approved, published as ${result.publishedId}"))
    DecisionResult.Rejected -> respond(DevTestimonyDecision(id, "rejected"))
    DecisionResult.NotFound -> notFound()
    DecisionResult.AlreadyDecided -> respond(HttpStatusCode.Conflict, ApiError("already_decided", "This testimony is already decided."))
    is DecisionResult.Refused -> respond(HttpStatusCode.BadRequest, ApiError("refused", result.reason))
}
