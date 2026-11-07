package org.politisationpopulairefrancaise.server.comprendre

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.web.comprendre.ComprendreCatalog
import org.politisationpopulairefrancaise.web.comprendre.ComprendreSource
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The contents of "Comprendre" for the web site (D-091), read like those of "Voir"
 * (`EditorialVoirSource`): the editorial contents (kind 30023) whose `ppf-type` is one of
 * [ComprendreCatalog.TYPES], signed by a key of the editorial team ([PublicationPolicy.isEditor],
 * checked again at reading time). The catalog is kept for [ttl] (30 seconds by default).
 */
class EditorialComprendreSource(
    private val events: EventRepository,
    private val policy: PublicationPolicy,
    private val ttl: Duration = 30.seconds,
    private val clock: Clock = Clock.systemUTC(),
) : ComprendreSource {
    private val lock = Mutex()
    private var cached: ComprendreCatalog? = null
    private var readAt: Long = 0

    override suspend fun catalog(): ComprendreCatalog = lock.withLock {
        val now = clock.millis()
        cached?.takeIf { now - readAt < ttl.inWholeMilliseconds }?.let { return@withLock it }
        val fresh = withContext(Dispatchers.IO) { ComprendreCatalog.fromEvents(readEvents(), policy::isEditor) }
        cached = fresh
        readAt = now
        fresh
    }

    /** Every stored content of these types, page after page (newest first), at most [MAX_EVENTS]. */
    private fun readEvents(): List<SignedEvent> {
        val seen = LinkedHashMap<String, SignedEvent>()
        var until: Long? = null
        while (seen.size < MAX_EVENTS) {
            val page = events.query(
                EventFilter(
                    kinds = listOf(Article.KIND),
                    tags = mapOf(Article.TAG_TYPE to ComprendreCatalog.TYPES.map { it.tagValue }),
                    until = until,
                    limit = EventFilter.MAX_LIMIT,
                ),
            )
            val added = page.count { seen.putIfAbsent(it.id, it) == null }
            if (page.size < EventFilter.MAX_LIMIT || added == 0) break
            // Same-second events may repeat on the next page: they are counted once.
            until = page.last().createdAt
        }
        return seen.values.toList()
    }

    companion object {
        const val MAX_EVENTS: Int = 20_000
    }
}
