package org.politisationpopulairefrancaise.server.groups

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.TagsJson
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How close two room names are (D-132, contract salons-roles.md, section 3 bis), on names already
 * normalized by [GroupNames]. A name is reduced to its significant words: the words of the territory
 * and the empty words ("collectif", "de", "du", the example marker...) are dropped, unless nothing
 * would remain. Two names are close when their significant words are the same in another order, or
 * when they differ by a small edit distance (one letter up to 11 letters, two beyond, none under 5).
 */
object GroupSimilarity {
    /** Words that do not tell two rooms apart. */
    val EMPTY_WORDS: Set<String> = setOf(
        "exemple", "collectif", "collectifs", "collective", "collectives", "salon", "salons", "groupe", "groupes",
        "association", "asso", "comite", "coordination", "reseau", "citoyen", "citoyens", "citoyenne", "citoyennes",
        "de", "du", "des", "d", "la", "le", "les", "l", "et", "a", "au", "aux", "en", "pour", "sur", "par", "avec",
    )

    fun significantWords(nameKey: String, territoryKey: String = ""): List<String> {
        val words = nameKey.split(' ').filter { it.isNotEmpty() }
        val place = territoryKey.split(' ').filter { it.isNotEmpty() }.toSet()
        val meaningful = words.filter { it !in EMPTY_WORDS }
        return meaningful.filter { it !in place }.ifEmpty { meaningful.ifEmpty { words } }
    }

    fun similar(a: List<String>, b: List<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val sortedA = a.sorted().joinToString(" ")
        val sortedB = b.sorted().joinToString(" ")
        if (sortedA == sortedB) return true
        val joinedA = a.joinToString(" ")
        val joinedB = b.joinToString(" ")
        val limit = tolerance(min(joinedA.length, joinedB.length))
        if (limit == 0) return false
        return distance(joinedA, joinedB, limit) <= limit || distance(sortedA, sortedB, limit) <= limit
    }

    /**
     * A room name resembles an organisation's when they are [similar], or when the room's significant
     * words contain all of the organisation's ("Comité CGT santé" contains "CGT").
     */
    fun resemblesOrganisation(room: List<String>, organisation: List<String>): Boolean =
        similar(room, organisation) || (organisation.isNotEmpty() && organisation.sumOf { it.length } >= MIN_ORGANISATION_LENGTH && room.containsAll(organisation))

    fun tolerance(length: Int): Int = when {
        length < 5 -> 0
        length < 12 -> 1
        else -> 2
    }

    /** Levenshtein distance of [a] and [b], or a value above [limit] as soon as it is certain to exceed it. */
    fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var best = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                best = min(best, current[j])
            }
            if (best > limit) return limit + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** "CGT" counts; a one- or two-letter name would hold back too many rooms. */
    private const val MIN_ORGANISATION_LENGTH: Int = 3
}

/**
 * The territories that contain a territory, normalized, for the comparison of names (D-132): a room
 * of Lyon is compared with the rooms of Lyon and of its parents. The rooms name their territory in
 * free text; until it is tied to the referential of territories (D-090), the only parent known is
 * the whole country.
 */
fun interface TerritoryParents {
    fun parentsOf(territoryKey: String): Set<String>

    companion object {
        val NATIONAL_ONLY: TerritoryParents = TerritoryParents { key -> if (key == NATIONAL) emptySet() else setOf(NATIONAL) }

        /** "France", normalized. */
        const val NATIONAL: String = "france"
    }
}

/** A verified organisation of the Agir directory as the guardrails compare it. */
data class KnownOrganisation(val name: String, val words: List<String>, val keys: Set<String>)

/**
 * The names of the verified organisations (fiches of the Agir directory, contract agir-contrat.md,
 * section 4): the `title` of every entry (kind 30023, `ppf-type` = organisation) signed by a key for
 * which [isEditor] holds, with the keys it verifies (`p` tags). Read from the events table and kept
 * in memory at most [maxAge].
 */
class OrganisationNames(
    private val dataSource: DataSource,
    private val isEditor: (String) -> Boolean,
    private val maxAge: Duration = 5.minutes,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private class Snapshot(val generation: Long, val loadedAt: Long, val organisations: List<KnownOrganisation>)

    private val generation = AtomicLong()

    @Volatile
    private var snapshot: Snapshot? = null

    fun all(): List<KnownOrganisation> {
        val now = nanoTime()
        val current = snapshot
        val wanted = generation.get()
        if (current != null && current.generation == wanted && now - current.loadedAt < maxAge.inWholeNanoseconds) return current.organisations
        val loaded = load()
        if (generation.get() == wanted) snapshot = Snapshot(wanted, now, loaded)
        return loaded
    }

    /** Forgets the names: an entry of the directory was just stored. */
    fun invalidate() {
        generation.incrementAndGet()
    }

    private fun load(): List<KnownOrganisation> = dataSource.connection.use { connection ->
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
            buildList {
                while (rows.next()) {
                    if (!isEditor(rows.getString(1))) continue
                    val tags = TagsJson.decode(rows.getString(2))
                    if (tags.firstOrNull { it.size >= 2 && it[0] == AgirFormats.TAG_TYPE }?.get(1) != AgirFormats.TYPE_ORGANISATION) continue
                    val title = tags.firstOrNull { it.size >= 2 && it[0] == AgirFormats.TAG_TITLE }?.get(1)?.trim() ?: continue
                    val words = GroupSimilarity.significantWords(GroupNames.normalize(title))
                    if (words.isEmpty()) continue
                    val keys = tags.filter { it.size >= 2 && it[0] == AgirFormats.TAG_PUBKEY }.map { it[1] }.toSet()
                    add(KnownOrganisation(title, words, keys))
                }
            }
        }
    }
}
