package org.politisationpopulairefrancaise.core.publication

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.elus.ElusReader
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.revendications.ProposalState
import org.politisationpopulairefrancaise.core.revendications.ProposalStatusSource
import org.politisationpopulairefrancaise.core.revendications.ProposalStatuses
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat

/**
 * The phase 1 adapter of the publication port: the platform's own server, through its signed-events
 * API (POST and GET /api/v1/events, GET /api/v1/groups, GET /api/v1/elus). Network failures become
 * [PublicationResult.Unavailable], [FetchResult.Unavailable], [GroupsResult.Unavailable] or
 * [ElusDownload.Unavailable] so that
 * callers keep the event and retry later.
 */
class ServerPublicationPort(
    private val client: HttpClient,
    /** Origin of the server, without trailing slash, e.g. "https://politisation-populaire-francaise.org". */
    private val baseUrl: String,
) : RemoteEvents, ProposalStatusSource, HeldStatusSource {

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
            // 202: kept by the server for a review by the team before publication.
            202 -> PublicationResult.Accepted(event.id, heldForReview = true)
            in 200..299 -> PublicationResult.Accepted(event.id)
            // Rate limited: not a refusal of this event, keep it and retry after the delay the server gives.
            429 -> PublicationResult.Unavailable(ServerException(429, response.headers["Retry-After"]?.trim()?.toLongOrNull()))
            in 500..599 -> PublicationResult.Unavailable(ServerException(response.status.value))
            else -> rejection(event.id, runCatching { response.bodyAsText() }.getOrNull())
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

    override suspend fun proposalStatuses(ids: List<String>): ProposalStatuses {
        require(ids.size <= ProposalStatusSource.MAX_IDS) { "At most ${ProposalStatusSource.MAX_IDS} ids per request" }
        if (ids.isEmpty()) return ProposalStatuses.Known(emptyList())
        val response: HttpResponse = try {
            client.get("$baseUrl/api/v1/revendications/proposals") {
                ids.forEach { parameter("id", it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ProposalStatuses.Unavailable(e)
        }
        if (response.status.value !in 200..299) return ProposalStatuses.Unavailable(ServerException(response.status.value))
        return try {
            ProposalStatuses.Known(json.decodeFromString(ListSerializer(ProposalState.serializer()), response.bodyAsText()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProposalStatuses.Unavailable(e)
        }
    }

    /** `GET /api/v1/held?id=…`: where the author's held contents stand (contract of the Agir space). */
    override suspend fun heldStatuses(ids: List<String>): HeldStatuses {
        require(ids.size <= HeldStatusSource.MAX_IDS) { "At most ${HeldStatusSource.MAX_IDS} ids per request" }
        if (ids.isEmpty()) return HeldStatuses.Known(emptyList())
        val response: HttpResponse = try {
            client.get("$baseUrl/api/v1/held") {
                ids.forEach { parameter("id", it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HeldStatuses.Unavailable(e)
        }
        if (response.status.value !in 200..299) return HeldStatuses.Unavailable(ServerException(response.status.value))
        return try {
            HeldStatuses.Known(json.decodeFromString(ListSerializer(HeldState.serializer()), response.bodyAsText()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HeldStatuses.Unavailable(e)
        }
    }

    /** `GET /api/v1/groups?q=…&territory=…&limit=…`: the rooms of collectives known to the server. */
    override suspend fun fetchGroups(query: String?, territory: String?, limit: Int): GroupsResult {
        val response: HttpResponse = try {
            client.get("$baseUrl/api/v1/groups") {
                query?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
                territory?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("territory", it) }
                parameter("limit", limit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return GroupsResult.Unavailable(e)
        }
        if (response.status.value !in 200..299) return GroupsResult.Unavailable(ServerException(response.status.value))
        val groups = try {
            parseGroups(response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return GroupsResult.Unavailable(e)
        }
        return GroupsResult.Groups(groups)
    }

    /**
     * `GET /api/v1/elus` or `GET /api/v1/elus/{code}`: the exact bytes of the body, which the caller
     * checks against the index. The sha256 already held goes in `If-None-Match` (the server's ETag).
     */
    override suspend fun fetchElus(code: String?, etag: String?): ElusDownload {
        require(code == null || ElusReader.isPackageCode(code)) { "Not a package code of the directory: $code" }
        val response: HttpResponse = try {
            client.get(elusUrl(baseUrl, code)) {
                etag?.trim()?.takeIf { it.isNotEmpty() }?.let { header(HttpHeaders.IfNoneMatch, "\"$it\"") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ElusDownload.Unavailable(e)
        }
        val body = if (response.status.value in 200..299) {
            try {
                response.bodyAsBytes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ElusDownload.Unavailable(e)
            }
        } else {
            null
        }
        return elusAnswer(response.status.value, body, response.headers[HttpHeaders.ETag], isPackage = code != null)
    }

    /**
     * `GET /api/v1/parlement` or `GET /api/v1/parlement/{name}`: the exact bytes of the body, which
     * the caller checks (signature of the index, sha256 of a package). The sha256 already held goes
     * in `If-None-Match` (the server's ETag).
     */
    override suspend fun fetchParliament(name: String?, etag: String?): OpenDataDownload {
        require(name == null || ParliamentFormat.isPackageName(name)) { "Not a package name of the open data: $name" }
        val response: HttpResponse = try {
            client.get(parliamentUrl(baseUrl, name)) {
                etag?.trim()?.takeIf { it.isNotEmpty() }?.let { header(HttpHeaders.IfNoneMatch, "\"$it\"") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OpenDataDownload.Unavailable(e)
        }
        val body = if (response.status.value in 200..299) {
            try {
                response.bodyAsBytes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return OpenDataDownload.Unavailable(e)
            }
        } else {
            null
        }
        return parliamentAnswer(response.status.value, body, response.headers[HttpHeaders.ETag], isPackage = name != null)
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
    private data class ApiErrorBody(val error: String, val message: String = "", val existing: String? = null)

    /** One entry of `GET /api/v1/groups`; a description without a name falls back to its id. */
    @Serializable
    private data class GroupBody(
        val id: String,
        val name: String? = null,
        val about: String? = null,
        val picture: String? = null,
        val territory: String? = null,
        val charter: String? = null,
        val public: Boolean = true,
        val open: Boolean = true,
        val event: SignedEvent,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The error codes of the server (docs/FORMATS.md, section 5) as reasons the caller can act on. */
        internal fun rejectionReason(code: String?): RejectionReason = when (code) {
            "invalid_signature" -> RejectionReason.INVALID_SIGNATURE
            "group_exists" -> RejectionReason.GROUP_EXISTS
            "group_taken" -> RejectionReason.GROUP_TAKEN
            "group_closed" -> RejectionReason.GROUP_CLOSED
            "group_not_found" -> RejectionReason.GROUP_NOT_FOUND
            "missing_territory" -> RejectionReason.MISSING_TERRITORY
            "missing_charter" -> RejectionReason.MISSING_CHARTER
            "missing_name" -> RejectionReason.MISSING_NAME
            "description_too_long" -> RejectionReason.DESCRIPTION_TOO_LONG
            else -> RejectionReason.REFUSED
        }

        /**
         * A definitive refusal (a 4xx answer) from its body, `{"error":…,"message":…,"existing":…}`:
         * the reason and, for `group_exists` and `group_taken`, the room it points at. A body that is
         * missing or unreadable is a plain refusal. Separate from the HTTP call so that tests can
         * cover it without a mock engine.
         */
        internal fun rejection(eventId: String, body: String?): PublicationResult.Rejected {
            val error = body?.let { runCatching { json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull() }
            val existing = error?.existing?.takeIf { Group.isValidId(it) }
            return PublicationResult.Rejected(eventId, rejectionReason(error?.error), existing)
        }

        /** The address of the index ([code] null) or of one package of the directory of elected officials. */
        internal fun elusUrl(baseUrl: String, code: String?): String =
            if (code == null) "$baseUrl/api/v1/elus" else "$baseUrl/api/v1/elus/$code"

        /**
         * The answer of the directory from its status: the body for a success, [ElusDownload.NotModified]
         * for 304, [ElusDownload.UnknownPackage] for a 404 on a package (a server without the
         * directory answers 404 to the index too: it is then unavailable), unavailable otherwise.
         * Separate from the HTTP call so that tests can cover it without a mock engine.
         */
        internal fun elusAnswer(status: Int, body: ByteArray?, etag: String?, isPackage: Boolean): ElusDownload = when {
            status == 304 -> ElusDownload.NotModified
            status in 200..299 && body != null -> ElusDownload.Body(body, etag?.trim()?.removePrefix("W/")?.trim('"'))
            status == 404 && isPackage -> ElusDownload.UnknownPackage
            else -> ElusDownload.Unavailable(ServerException(status))
        }

        /** The address of the index ([name] null) or of one package of the parliamentary open data. */
        internal fun parliamentUrl(baseUrl: String, name: String?): String =
            if (name == null) "$baseUrl/api/v1/parlement" else "$baseUrl/api/v1/parlement/$name"

        /** Like [elusAnswer], for the parliamentary open data. */
        internal fun parliamentAnswer(status: Int, body: ByteArray?, etag: String?, isPackage: Boolean): OpenDataDownload = when {
            status == 304 -> OpenDataDownload.NotModified
            status in 200..299 && body != null -> OpenDataDownload.Body(body, etag?.trim()?.removePrefix("W/")?.trim('"'))
            status == 404 && isPackage -> OpenDataDownload.UnknownPackage
            else -> OpenDataDownload.Unavailable(ServerException(status))
        }

        /** The body of `GET /api/v1/groups` as rooms. Separate from the HTTP call so that tests can cover it without a mock engine. */
        internal fun parseGroups(body: String): List<Group> =
            json.decodeFromString(ListSerializer(GroupBody.serializer()), body).map {
                Group(
                    id = it.id,
                    name = it.name?.takeIf { name -> name.isNotBlank() } ?: it.id,
                    about = it.about,
                    picture = it.picture,
                    territory = it.territory,
                    charter = it.charter,
                    public = it.public,
                    open = it.open,
                    event = it.event,
                )
            }
    }
}
