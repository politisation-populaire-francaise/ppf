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
 * withdraws the verification, and so does a version with `ppf-org-status` = `revoquee` (contract,
 * version 2). As on the device, when several entries name a key, the most recent one describes it
 * (on equal dates, the lowest id): the key is verified only when that entry is not revoked.
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
                SELECT e.id, e.pubkey, e.created_at, e.tags::text FROM events e
                WHERE e.kind = ? AND EXISTS (SELECT 1 FROM event_tags t WHERE t.event_id = e.id AND t.name = ? AND t.value = ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, Article.KIND)
                statement.setString(2, AgirFormats.TAG_TYPE)
                statement.setString(3, AgirFormats.TYPE_ORGANISATION)
                val rows = statement.executeQuery()
                // For each named key, the entry that describes it: (created_at, id, revoked).
                val byKey = HashMap<String, Triple<Long, String, Boolean>>()
                while (rows.next()) {
                    // Only the directory signed by the editorial team counts (a key removed from it no longer verifies anyone).
                    if (!isEditor(rows.getString(2))) continue
                    val tags = TagsJson.decode(rows.getString(4))
                    // The first ppf-type (and ppf-org-status) is the one every reader sees.
                    if (firstValue(tags, AgirFormats.TAG_TYPE) != AgirFormats.TYPE_ORGANISATION) continue
                    val entry = Triple(rows.getLong(3), rows.getString(1), isRevoked(firstValue(tags, AgirFormats.TAG_ORG_STATUS)))
                    tags.filter { it.size >= 2 && it[0] == AgirFormats.TAG_PUBKEY && isHexKey(it[1]) }.forEach { tag ->
                        val current = byKey[tag[1]]
                        if (current == null || entry.first > current.first || (entry.first == current.first && entry.second < current.second)) byKey[tag[1]] = entry
                    }
                }
                byKey.filterValues { !it.third }.keys
            }
        }
    }

    private fun firstValue(tags: List<List<String>>, name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

    /** Absent or `verifiee` keeps the verification; any other value withdraws it, as on the device. */
    private fun isRevoked(status: String?): Boolean = status != null && status.trim() != AgirFormats.ORG_STATUS_VERIFIED

    private fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        val DEFAULT_MAX_AGE: Duration = 5.minutes
    }
}
