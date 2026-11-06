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
}
