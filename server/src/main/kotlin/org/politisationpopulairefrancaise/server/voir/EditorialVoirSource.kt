package org.politisationpopulairefrancaise.server.voir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.web.voir.VoirSource
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The contents of "Voir" for the web site: the editorial contents (kind 30023) whose `ppf-type` is
 * one of [VoirCatalog.TYPES], signed by a key of the editorial team ([PublicationPolicy.isEditor],
 * checked again at reading time: a key removed from the team stops being shown), assembled by
 * [VoirCatalog.from] exactly as the applications do.
 *
 * The catalog is kept for [ttl] (30 seconds by default) so that a burst of visits costs one
 * reading of the database; a correction appears on the site at the latest [ttl] after it is published.
 */
class EditorialVoirSource(
    private val events: EventRepository,
    private val policy: PublicationPolicy,
    private val ttl: Duration = 30.seconds,
    private val clock: Clock = Clock.systemUTC(),
) : VoirSource {
    private val lock = Mutex()
    private var cached: VoirCatalog? = null
    private var readAt: Long = 0

    override suspend fun catalog(): VoirCatalog = lock.withLock {
        val now = clock.millis()
        cached?.takeIf { now - readAt < ttl.inWholeMilliseconds }?.let { return@withLock it }
        val fresh = withContext(Dispatchers.IO) { read() }
        cached = fresh
        readAt = now
        fresh
    }

    private fun read(): VoirCatalog {
        val articles = readEvents()
            .filter { policy.isEditor(it.pubkey) }
            .mapNotNull(Article::from)
        return VoirCatalog.from(articles)
    }

    /** Every stored block of "Voir", page after page (newest first), at most [MAX_EVENTS]. */
    private fun readEvents(): List<SignedEvent> {
        val seen = LinkedHashMap<String, SignedEvent>()
        var until: Long? = null
        while (seen.size < MAX_EVENTS) {
            val page = events.query(
                EventFilter(
                    kinds = listOf(Article.KIND),
                    tags = mapOf(Article.TAG_TYPE to VoirCatalog.TYPES.map { it.tagValue }),
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
        /** A bound on what one page view may read; far above what the team publishes. */
        const val MAX_EVENTS: Int = 20_000
    }
}
