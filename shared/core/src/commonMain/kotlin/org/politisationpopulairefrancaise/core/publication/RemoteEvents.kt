package org.politisationpopulairefrancaise.core.publication

import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** What to fetch from a remote source of events. */
data class RemoteFilter(
    val kinds: List<Int> = emptyList(),
    val authors: List<String> = emptyList(),
    val identifiers: List<String> = emptyList(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int = 200,
)

/**
 * Events carrying a tag named [name] whose value is [value] (for example `p` = a public key).
 * The bounds are those of the server: a name of 1 to 64 characters, a value of at most 1024 bytes.
 */
data class TagFilter(val name: String, val value: String) {
    init {
        require(name.length in 1..MAX_NAME_LENGTH && ':' !in name) { "A tag name has 1 to $MAX_NAME_LENGTH characters and no colon" }
        require(value.isNotEmpty() && value.encodeToByteArray().size <= MAX_VALUE_BYTES) { "A tag value has 1 to $MAX_VALUE_BYTES bytes" }
    }

    /** The server's query parameter form: `tag=<name>:<value>` (the value may contain colons). */
    val parameter: String get() = "$name:$value"

    companion object {
        const val MAX_NAME_LENGTH: Int = 64
        const val MAX_VALUE_BYTES: Int = 1024

        /** The server accepts at most this many `tag` parameters in one request. */
        const val MAX_PER_REQUEST: Int = 20
    }
}

sealed interface FetchResult {
    data class Events(val events: List<SignedEvent>) : FetchResult

    data class Unavailable(val cause: Throwable? = null) : FetchResult
}

/** The directory of rooms (NIP-29 groups) of a remote source. */
sealed interface GroupsResult {
    /** The rooms matching the request, sorted by name. */
    data class Groups(val groups: List<Group>) : GroupsResult

    data class Unavailable(val cause: Throwable? = null) : GroupsResult
}

/**
 * One document of the directory of elected officials (D-047): the index or the package of a
 * department, as the exact bytes the source sent, so that the caller checks them against the sha256
 * the index announces before using them.
 */
sealed interface ElusDownload {
    class Body(val bytes: ByteArray, val etag: String? = null) : ElusDownload

    /** The document has not changed since the version whose sha256 was given. */
    data object NotModified : ElusDownload

    /** The source has no package with this code (`404 unknown_package`). */
    data object UnknownPackage : ElusDownload

    data class Unavailable(val cause: Throwable? = null) : ElusDownload
}

/**
 * One document of the parliamentary open data (D-064, D-065): the signed index or one package, as
 * the exact bytes the source sent, so that the caller checks the signature of the index and the
 * sha256 of every package before using them.
 */
sealed interface OpenDataDownload {
    class Body(val bytes: ByteArray, val etag: String? = null) : OpenDataDownload

    /** The document has not changed since the version whose sha256 was given. */
    data object NotModified : OpenDataDownload

    /** The source has no package with this name (`404 unknown_package`). */
    data object UnknownPackage : OpenDataDownload

    data class Unavailable(val cause: Throwable? = null) : OpenDataDownload
}

/** A publication destination that can also be read: the server now, Nostr relays later. */
interface RemoteEvents : PublicationPort {
    suspend fun fetch(filter: RemoteFilter): FetchResult

    /**
     * The rooms this source knows (`GET /api/v1/groups` on the server), sorted by name, at most
     * [limit] of them (the server serves 500 at most). [query] narrows to rooms whose name or
     * description contains the text, [territory] to one territory. Sources without a directory of
     * rooms answer [GroupsResult.Unavailable].
     */
    suspend fun fetchGroups(query: String? = null, territory: String? = null, limit: Int = 100): GroupsResult =
        GroupsResult.Unavailable(UnsupportedOperationException("This source has no directory of rooms"))

    /**
     * Like [fetch], restricted to events carrying tags: several values for one tag name match any
     * of them, different names must all match (NIP-01 filter semantics). On the server this is the
     * repeatable `tag=<name>:<value>` parameter, combinable with kinds, since, until and limit.
     * Sources that cannot filter by tag answer [FetchResult.Unavailable].
     */
    suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter = RemoteFilter()): FetchResult =
        FetchResult.Unavailable(UnsupportedOperationException("This source cannot filter events by tag"))

    /**
     * The directory of elected officials (contract of the third wave of "Comprendre", 1.4): the index
     * (`GET /api/v1/elus`) when [code] is null, otherwise the package of a department or `FR`
     * (`GET /api/v1/elus/{code}`). [etag], the sha256 of the version already on the device, lets the
     * source answer [ElusDownload.NotModified]. Nothing personal is sent: no commune, no postal code.
     * Sources without the directory answer [ElusDownload.Unavailable].
     */
    suspend fun fetchElus(code: String? = null, etag: String? = null): ElusDownload =
        ElusDownload.Unavailable(UnsupportedOperationException("This source has no directory of elected officials"))

    /**
     * The referential of territories (D-090, docs/integration/territoires.md): the national level
     * (`GET /api/v1/territoires`) when [code] is null, otherwise the package of a department or
     * overseas collectivity, or `communes` for the index of the names (`GET /api/v1/territoires/{code}`).
     * The four answers are those of [ElusDownload]. Nothing personal is sent: no commune, no
     * postal code, no search. Sources without the referential answer [ElusDownload.Unavailable].
     */
    suspend fun fetchTerritoires(code: String? = null, etag: String? = null): ElusDownload =
        ElusDownload.Unavailable(UnsupportedOperationException("This source has no referential of territories"))

    /**
     * The parliamentary open data (contract docs/integration/voir-donnees-ouvertes.md, section 3):
     * the signed index event (`GET /api/v1/parlement`) when [name] is null, otherwise one package
     * (`GET /api/v1/parlement/{name}`). [etag], the sha256 of the version already on the device,
     * lets the source answer [OpenDataDownload.NotModified]. Nothing is sent but the package name.
     * Sources without these data answer [OpenDataDownload.Unavailable].
     */
    suspend fun fetchParliament(name: String? = null, etag: String? = null): OpenDataDownload =
        OpenDataDownload.Unavailable(UnsupportedOperationException("This source has no parliamentary open data"))
}
