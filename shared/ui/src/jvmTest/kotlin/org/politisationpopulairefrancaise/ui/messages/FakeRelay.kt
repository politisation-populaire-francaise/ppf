package org.politisationpopulairefrancaise.ui.messages

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.util.Collections

/**
 * Stands for our server as a relay, in memory: it keeps what it accepts and serves it back with
 * the server's filters, tags included ([InMemoryPublicationPort]). [online] false makes it
 * unreachable. Several devices may share one instance: every request is serialized. [calls]
 * records the order of the requests ("publish", "fetch", "tags"), [tagRequests] the tag filters.
 */
internal class FakeRelay : RemoteEvents {
    private val port = InMemoryPublicationPort()
    private val lock = Mutex()
    private val accepted: MutableList<SignedEvent> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var online: Boolean = true

    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val tagRequests: MutableList<Pair<List<TagFilter>, RemoteFilter>> = Collections.synchronizedList(mutableListOf())

    /** What the relay holds, in the order it accepted it. */
    val events: List<SignedEvent> get() = synchronized(accepted) { accepted.toList() }

    override suspend fun publish(event: SignedEvent): PublicationResult = lock.withLock {
        calls += "publish"
        if (!online) return@withLock PublicationResult.Unavailable()
        val result = port.publish(event)
        if (result is PublicationResult.Accepted && accepted.none { it.id == event.id }) accepted += event
        result
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult = lock.withLock {
        calls += "fetch"
        if (!online) FetchResult.Unavailable() else port.fetch(filter)
    }

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult = lock.withLock {
        calls += "tags"
        tagRequests += tags to filter
        if (!online) FetchResult.Unavailable() else port.fetchByTags(tags, filter)
    }
}
