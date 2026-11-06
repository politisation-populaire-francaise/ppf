package org.politisationpopulairefrancaise.server.agir

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The verified organisations (contract, section 4): a key is verified when it is named in a `p` tag
 * of an entry of the directory (kind 30023, `ppf-type` = organisation) signed by a key for which
 * [isEditor] holds (`PPF_EDITOR_KEYS`) and stored in the events table. The same rule as `EditorialTrust` on
 * the device. Only the version of an entry that is stored counts: a new version without the `p` tag
 * withdraws the verification.
 *
 * The keys are read from the events table and kept in memory. [stored] is told of every event the
 * server stores and forgets them when an entry of the directory comes in (new or replaced); they are
 * read again at most [maxAge] later anyway, for the entries another process stored.
 */
class VerifiedOrganisations(
    private val dataSource: DataSource,
    private val isEditor: (String) -> Boolean,
    private val maxAge: Duration = DEFAULT_MAX_AGE,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private class Snapshot(val generation: Long, val loadedAt: Long, val keys: Set<String>)

    private val generation = AtomicLong()

    @Volatile
    private var snapshot: Snapshot? = null

    fun isVerified(pubkey: String): Boolean = pubkey in keys()

    /** Every verified key, read again when the cache is stale. */
    fun keys(): Set<String> {
        val now = nanoTime()
        val current = snapshot
        val wanted = generation.get()
        if (current != null && current.generation == wanted && now - current.loadedAt < maxAge.inWholeNanoseconds) return current.keys
        val keys = load()
        // A directory entry stored while the keys were being read makes this reading stale: not kept.
        if (generation.get() == wanted) snapshot = Snapshot(wanted, now, keys)
        return keys
    }

    /** Forgets the keys: the next question reads them again. */
    fun invalidate() {
        generation.incrementAndGet()
    }

    /** Called with every event the server has just stored. */
    fun stored(event: SignedEvent) {
        if (AgirRules.categoryOf(event) == AgirCategory.ORGANISATION) invalidate()
    }

    private fun load(): Set<String> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT e.pubkey, e.tags::text FROM events e
                WHERE e.kind = ? AND EXISTS (SELECT 1 FROM event_tags t WHERE t.event_id = e.id AND t.name = ? AND t.value = ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, Article.KIND)
                statement.setString(2, AgirFormats.TAG_TYPE)
                statement.setString(3, AgirFormats.TYPE_ORGANISATION)
                val rows = statement.executeQuery()
                buildSet {
                    while (rows.next()) {
                        // Only the directory signed by the editorial team counts (a key removed from it no longer verifies anyone).
                        if (!isEditor(rows.getString(1))) continue
                        val tags = TagsJson.decode(rows.getString(2))
                        // The first ppf-type is the one every reader sees.
                        if (tags.firstOrNull { it.size >= 2 && it[0] == AgirFormats.TAG_TYPE }?.get(1) != AgirFormats.TYPE_ORGANISATION) continue
                        tags.filter { it.size >= 2 && it[0] == AgirFormats.TAG_PUBKEY && isHexKey(it[1]) }.forEach { add(it[1]) }
                    }
                }
            }
        }
    }

    private fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        val DEFAULT_MAX_AGE: Duration = 5.minutes
    }
}
