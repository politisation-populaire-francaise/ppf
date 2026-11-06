package org.politisationpopulairefrancaise.core.publication

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

sealed interface FetchResult {
    data class Events(val events: List<SignedEvent>) : FetchResult

    data class Unavailable(val cause: Throwable? = null) : FetchResult
}

/** A publication destination that can also be read: the server now, Nostr relays later. */
interface RemoteEvents : PublicationPort {
    suspend fun fetch(filter: RemoteFilter): FetchResult
}
