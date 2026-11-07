package org.politisationpopulairefrancaise.core.agir.testimonies

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** The body of `POST /api/v1/testimonies`: two envelopes, the names of the files already sent, the digests to timestamp. */
@Serializable
data class TestimonySubmission(
    val envelope: SignedEvent,
    @SerialName("evidence_envelope") val evidenceEnvelope: SignedEvent? = null,
    val files: List<String> = emptyList(),
    @SerialName("evidence_files") val evidenceFiles: List<String> = emptyList(),
    val stamps: List<String> = emptyList(),
)

/** What is sent for one testimony, as kept on the device until the server has it: the request and the encrypted files. */
class OutgoingTestimony(val submission: TestimonySubmission, val files: Map<String, ByteArray>) {
    val id: String get() = submission.envelope.id
}

/** Where a testimony stands, as `GET /api/v1/testimonies/status` tells its witness. */
@Serializable
data class TestimonyState(
    /** The id of the envelope for the moderation team. */
    val id: String,
    val status: HeldStatus,
    /** Seconds since 1970, rounded down to the hour (the exact moment is not kept). */
    @SerialName("received_at") val receivedAt: Long,
    @SerialName("decided_at") val decidedAt: Long? = null,
    /** The team's reason, for a refusal. */
    val reason: String? = null,
    /** The id of the published testimony (kind 30023, signed by the team), once approved. */
    val published: String? = null,
)

sealed interface TestimonySendResult {
    data class Sent(val id: String) : TestimonySendResult

    /** The server refused it for good ([code] as in the API); sending it again changes nothing. */
    data class Refused(val code: String) : TestimonySendResult

    /** Not reachable or busy: the testimony stays on the device, to be sent again. */
    data class Unavailable(val cause: Throwable? = null) : TestimonySendResult
}

sealed interface TestimonyStatuses {
    data class Known(val states: List<TestimonyState>) : TestimonyStatuses

    data class Unavailable(val cause: Throwable? = null) : TestimonyStatuses
}

/** The way testimonies leave the device; [ServerTestimonyTransport] in the applications, a fake in tests. */
interface TestimonyTransport {
    suspend fun send(testimony: OutgoingTestimony): TestimonySendResult

    suspend fun statuses(ids: List<String>): TestimonyStatuses

    companion object {
        const val MAX_IDS: Int = 20
    }
}

/**
 * The platform's server: each encrypted file first (`POST /api/v1/testimonies/files`, raw bytes),
 * then the envelopes (`POST /api/v1/testimonies`). Sending again after a failure is harmless: a
 * file is named by its SHA-256 and an envelope by its id.
 */
class ServerTestimonyTransport(private val client: HttpClient, private val baseUrl: String) : TestimonyTransport {
    override suspend fun send(testimony: OutgoingTestimony): TestimonySendResult {
        try {
            val submission = testimony.submission
            for (id in submission.files + submission.evidenceFiles) {
                val bytes = testimony.files[id] ?: return TestimonySendResult.Refused("missing_file")
                val response = client.post("$baseUrl/api/v1/testimonies/files") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(bytes)
                }
                when (response.status.value) {
                    in 200..299 -> Unit
                    429, in 500..599 -> return TestimonySendResult.Unavailable()
                    else -> return TestimonySendResult.Refused(errorCode(response.bodyAsText()))
                }
            }
            val response = client.post("$baseUrl/api/v1/testimonies") {
                contentType(ContentType.Application.Json)
                setBody(TestimonyJson.encodeToString(TestimonySubmission.serializer(), submission))
            }
            return when (response.status.value) {
                in 200..299 -> TestimonySendResult.Sent(testimony.id)
                429, in 500..599 -> TestimonySendResult.Unavailable()
                else -> TestimonySendResult.Refused(errorCode(response.bodyAsText()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return TestimonySendResult.Unavailable(e)
        }
    }

    override suspend fun statuses(ids: List<String>): TestimonyStatuses {
        require(ids.size in 1..TestimonyTransport.MAX_IDS) { "1 to ${TestimonyTransport.MAX_IDS} ids per request" }
        return try {
            val response = client.get("$baseUrl/api/v1/testimonies/status") { ids.forEach { parameter("id", it) } }
            if (response.status.value !in 200..299) return TestimonyStatuses.Unavailable()
            TestimonyStatuses.Known(TestimonyJson.decodeFromString(ListSerializer(TestimonyState.serializer()), response.bodyAsText()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TestimonyStatuses.Unavailable(e)
        }
    }

    private fun errorCode(body: String): String = runCatching {
        TestimonyJson.decodeFromString(ErrorBody.serializer(), body).error
    }.getOrNull() ?: "refused"

    @Serializable
    private data class ErrorBody(val error: String)

    companion object {
        /** The transport of the server the application already publishes to. */
        fun of(port: ServerPublicationPort): ServerTestimonyTransport = ServerTestimonyTransport(port.client, port.baseUrl)
    }
}

/** The JSON of the testimony API, the same on the device and on the server. */
val TestimonyJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
