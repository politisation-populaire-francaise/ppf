package org.politisationpopulairefrancaise.core.publication

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The phase 1 adapter of the publication port: the platform's own server, through its signed-events
 * API (POST and GET /api/v1/events). Network failures become [PublicationResult.Unavailable] or
 * [FetchResult.Unavailable] so that callers keep the event and retry later.
 */
class ServerPublicationPort(
    private val client: HttpClient,
    /** Origin of the server, without trailing slash, e.g. "https://politisation-populaire-francaise.org". */
    private val baseUrl: String,
) : RemoteEvents {

    override suspend fun publish(event: SignedEvent): PublicationResult {
        val response = try {
            client.post("$baseUrl/api/v1/events") {
                contentType(ContentType.Application.Json)
                setBody(event.toJson())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return PublicationResult.Unavailable(e)
        }
        return when (response.status.value) {
            in 200..299 -> PublicationResult.Accepted(event.id)
            // Rate limited: not a refusal of this event, keep it and retry after the delay the server gives.
            429 -> PublicationResult.Unavailable(ServerException(429, response.headers["Retry-After"]?.trim()?.toLongOrNull()))
            in 500..599 -> PublicationResult.Unavailable(ServerException(response.status.value))
            else -> {
                val error = runCatching { json.decodeFromString(ApiErrorBody.serializer(), response.bodyAsText()) }.getOrNull()
                val reason = if (error?.error == "invalid_signature") RejectionReason.INVALID_SIGNATURE else RejectionReason.REFUSED
                PublicationResult.Rejected(event.id, reason)
            }
        }
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult = fetchEvents(filter, emptyList())

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult = fetchEvents(filter, tags)

    private suspend fun fetchEvents(filter: RemoteFilter, tags: List<TagFilter>): FetchResult {
        require(tags.size <= TagFilter.MAX_PER_REQUEST) { "The server accepts at most ${TagFilter.MAX_PER_REQUEST} tag filters per request" }
        val response: HttpResponse = try {
            client.get("$baseUrl/api/v1/events") {
                applyFilter(filter)
                tags.forEach { parameter("tag", it.parameter) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FetchResult.Unavailable(e)
        }
        if (response.status.value !in 200..299) return FetchResult.Unavailable(ServerException(response.status.value))
        val events = try {
            json.decodeFromString(ListSerializer(SignedEvent.serializer()), response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FetchResult.Unavailable(e)
        }
        return FetchResult.Events(events)
    }

    private fun HttpRequestBuilder.applyFilter(filter: RemoteFilter) {
        filter.kinds.forEach { parameter("kind", it) }
        filter.authors.forEach { parameter("author", it) }
        filter.identifiers.forEach { parameter("identifier", it) }
        filter.since?.let { parameter("since", it) }
        filter.until?.let { parameter("until", it) }
        parameter("limit", filter.limit)
    }

    /** A server answer that is not a final decision on the event; [retryAfterSeconds] comes from `Retry-After` when given. */
    class ServerException(val status: Int, val retryAfterSeconds: Long? = null) : RuntimeException("Server answered $status")

    @Serializable
    private data class ApiErrorBody(val error: String, val message: String = "")

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
