package org.politisationpopulairefrancaise.server.editorial

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The editorial contents already published, as the drafts need them: the type of every slug (links of a
 * draft), the published version of a slug (diff, update) and the editorial key of the space.
 *
 * The key of a space is the current key of its use on the signing service ([keyOf], D-095) once one is
 * configured and answers; otherwise, as before (D-093), the editorial key ([isEditor]) that signed the most
 * published contents of that space; with none, the [PREVIEW_KEY] stands in, only to build the previews
 * (nothing is ever signed with it). Kept [ttl], the keys of the uses with the contents.
 */
class PublishedContents(
    private val events: EventRepository,
    private val isEditor: (String) -> Boolean,
    private val ttl: Duration = 30.seconds,
    private val clock: Clock = Clock.systemUTC(),
    /** The current key of the use of a space (`PlatformSigner.currentKey`), null without a signing service. */
    private val keyOf: (suspend (EditorialSpace) -> String?)? = null,
) {
    /** The latest version of each address of the editorial keys, by author then slug; [keys]: the current key of each space's use. */
    class Snapshot(val latest: Map<String, Map<String, SignedEvent>>, val keys: Map<EditorialSpace, String> = emptyMap()) {
        /** The editorial key of [space]: the current key of its use, else the one that signed the most contents of its types. */
        fun authorOf(space: EditorialSpace): String {
            keys[space]?.let { return it }
            val types = EditorialSchemas.of(space).map { it.type.tagValue }.toSet()
            return latest.maxByOrNull { (_, bySlug) -> bySlug.values.count { it.firstTagValue(Article.TAG_TYPE) in types } }
                ?.takeIf { (_, bySlug) -> bySlug.values.any { it.firstTagValue(Article.TAG_TYPE) in types } }?.key
                ?: PREVIEW_KEY
        }

        /** The published version of [slug] under [author], if any. */
        fun published(author: String, slug: String): SignedEvent? = latest[author]?.get(slug)

        /** The published contents of [space] under its key, by title. */
        fun ofSpace(space: EditorialSpace): List<SignedEvent> {
            val types = EditorialSchemas.of(space).map { it.type.tagValue }.toSet()
            return latest[authorOf(space)].orEmpty().values.filter { it.firstTagValue(Article.TAG_TYPE) in types }
                .sortedBy { it.firstTagValue(Article.TAG_TITLE).orEmpty().lowercase() }
        }

        /** The type of every published slug of [author]. */
        fun types(author: String): Map<String, ArticleType> = latest[author].orEmpty().mapNotNull { (slug, event) ->
            ArticleType.entries.firstOrNull { it.tagValue == event.firstTagValue(Article.TAG_TYPE) }?.let { slug to it }
        }.toMap()

        /** The context of the checks of [space]: published contents, then [drafts] (the other open drafts) where no content is published. */
        fun context(space: EditorialSpace, drafts: Map<String, ArticleType>): CheckContext {
            val author = authorOf(space)
            return CheckContext.of(author, drafts + types(author))
        }
    }

    private var cached: Snapshot? = null
    private var readAt: Long = 0

    @Synchronized
    fun snapshot(): Snapshot {
        val now = clock.millis()
        cached?.takeIf { now - readAt < ttl.inWholeMilliseconds }?.let { return it }
        val fresh = Snapshot(read(), keys())
        cached = fresh
        readAt = now
        return fresh
    }

    /** Forgets the cache (after a publication, and in the tests). */
    @Synchronized
    fun refresh() {
        cached = null
    }

    /** The current key of each space's use; a space whose use has no key, or a service that fails, keeps the fallback. */
    private fun keys(): Map<EditorialSpace, String> {
        val lookup = keyOf ?: return emptyMap()
        return EditorialSpace.entries.mapNotNull { space ->
            val key = try {
                kotlinx.coroutines.runBlocking { lookup(space) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            key?.takeIf { HEX_KEY.matches(it) }?.let { space to it }
        }.toMap()
    }

    private fun read(): Map<String, Map<String, SignedEvent>> {
        val latest = HashMap<String, HashMap<String, SignedEvent>>()
        var until: Long? = null
        var seen = 0
        while (seen < MAX_EVENTS) {
            val page = events.query(EventFilter(kinds = listOf(Article.KIND), until = until, limit = EventFilter.MAX_LIMIT))
            var added = 0
            for (event in page) {
                if (!isEditor(event.pubkey)) continue
                val slug = event.firstTagValue("d") ?: continue
                val bySlug = latest.getOrPut(event.pubkey) { HashMap() }
                val known = bySlug[slug]
                if (known == null || known.createdAt < event.createdAt || (known.createdAt == event.createdAt && event.id < known.id)) {
                    if (known == null) added++
                    bySlug[slug] = event
                }
            }
            seen += page.size
            if (page.size < EventFilter.MAX_LIMIT || page.isEmpty()) break
            val last = page.last().createdAt
            // Same-second events repeat on the next page; stop when nothing older can come.
            if (until == last && added == 0) break
            until = last
        }
        return latest
    }

    companion object {
        const val MAX_EVENTS: Int = 20_000
        private val HEX_KEY = Regex("^[0-9a-f]{64}$")

        /**
         * The x coordinate of the generator of secp256k1: a valid key that nobody uses to sign, only to build
         * a preview when no editorial content is published yet.
         */
        const val PREVIEW_KEY: String = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    }
}
