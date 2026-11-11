package org.politisationpopulairefrancaise.server.decider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.web.decider.DeciderSource
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The contents of "Décider" for the web site, read like those of "Voir" and "Comprendre": the editorial
 * contents (kind 30023) whose `ppf-type` is one of [DeciderCatalog.TYPES] (petitions, pacts, answers of the
 * candidates, comparators and positions), signed by a key of the editorial team (checked again at reading
 * time: a citizen's content never enters these pages). Kept [ttl] (30 seconds by default).
 */
class EditorialDeciderSource(
    private val events: EventRepository,
    private val policy: PublicationPolicy,
    private val ttl: Duration = 30.seconds,
    private val clock: Clock = Clock.systemUTC(),
) : DeciderSource {
    private val lock = Mutex()
    private var cached: DeciderCatalog? = null
    private var readAt: Long = 0

    override suspend fun catalog(): DeciderCatalog = lock.withLock {
        val now = clock.millis()
        cached?.takeIf { now - readAt < ttl.inWholeMilliseconds }?.let { return@withLock it }
        val fresh = withContext(Dispatchers.IO) {
            DeciderCatalog.from(readEvents().filter { policy.isEditor(it.pubkey) }.mapNotNull(Article::from))
        }
        cached = fresh
        readAt = now
        fresh
    }

    /**
     * The citizen deliberations (DE-25): a deliberation counts only signed by an editorial key; its
     * propositions and amendments are the published ones, a citizen's being stored only after the review of
     * a person (DeliberationSubmissions). Read on each call: they change while a phase is open.
     */
    override suspend fun deliberations(): List<org.politisationpopulairefrancaise.core.decider.DeliberationBoard> = withContext(Dispatchers.IO) {
        val types = org.politisationpopulairefrancaise.core.decider.DeliberationBoard.TYPES
        org.politisationpopulairefrancaise.core.decider.DeliberationBoard.from(readEvents(types)) { policy.isEditor(it) }
    }

    /**
     * The demands of the wall signed by the editorial team (D-152: those of the movement, the citizen demands
     * the team relays, the examples), the latest version of each, newest first.
     */
    override suspend fun demands(): List<Article> = withContext(Dispatchers.IO) {
        readEvents(listOf(org.politisationpopulairefrancaise.core.editorial.ArticleType.REVENDICATION.tagValue))
            .filter { policy.isEditor(it.pubkey) }
            .mapNotNull(Article::from)
            .groupBy { it.address }.values.map { versions -> versions.maxBy { it.updatedAt } }
            .sortedWith(compareByDescending<Article> { it.updatedAt }.thenBy { it.title.lowercase() })
    }

    /** Every stored content of these types, page after page (newest first), at most [MAX_EVENTS]. */
    private fun readEvents(types: List<String> = DeciderCatalog.TYPES.map { it.tagValue }): List<SignedEvent> {
        val seen = LinkedHashMap<String, SignedEvent>()
        var until: Long? = null
        while (seen.size < MAX_EVENTS) {
            val page = events.query(
                EventFilter(
                    kinds = listOf(Article.KIND),
                    tags = mapOf(Article.TAG_TYPE to types),
                    until = until,
                    limit = EventFilter.MAX_LIMIT,
                ),
            )
            val added = page.count { seen.putIfAbsent(it.id, it) == null }
            if (page.size < EventFilter.MAX_LIMIT || added == 0) break
            until = page.last().createdAt
        }
        return seen.values.toList()
    }

    companion object {
        const val MAX_EVENTS: Int = 5_000
    }
}
