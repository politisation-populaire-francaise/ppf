package org.politisationpopulairefrancaise.server.groups

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import java.sql.Connection
import java.sql.ResultSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The table group_directory (V4__group_directory.sql): one row per stored group description, with
 * its name, territory and `about` normalized by [GroupNames]. It lets the directory refuse
 * duplicates and search the rooms in SQL, whatever their number.
 *
 * Rows are written by EventRepository through [write], in the transaction that stores the
 * description, and disappear with it (ON DELETE CASCADE): a new version of a description replaces
 * the row of the old one. Descriptions stored before the table existed are indexed by [indexMissing].
 *
 * When two keys described the same id (only possible through a race the lock of [exclusively] now
 * prevents, or through a direct store), the description that counts is the earliest one, then the
 * one with the lowest event id, as in [GroupDirectory.find].
 */
class GroupIndex(
    private val dataSource: DataSource,
    /** How long a description waits for its turn in [exclusively] before the request fails. */
    private val lockWait: Duration = DEFAULT_LOCK_WAIT,
) {
    init {
        require(lockWait >= 1.seconds) { "The lock wait of group descriptions must be at least one second" }
    }


    /**
     * The descriptions that count, by normalized name then id, at most [limit]: those whose name,
     * territory or `about` contains [text] (already normalized; null for no text filter) and whose
     * territory is [territory] (already normalized; null for every territory).
     */
    fun search(text: String?, territory: String?, limit: Int): List<SignedEvent> = dataSource.connection.use { connection ->
        val conditions = mutableListOf(CANONICAL)
        val values = mutableListOf<String>()
        if (text != null) {
            conditions += "(strpos(d.name_key, ?) > 0 OR strpos(d.territory_key, ?) > 0 OR strpos(d.about_key, ?) > 0)"
            values += listOf(text, text, text)
        }
        if (territory != null) {
            conditions += "d.territory_key = ?"
            values += territory
        }
        connection.prepareStatement(
            "SELECT $EVENT_COLUMNS FROM group_directory d JOIN events e ON e.id = d.event_id " +
                "WHERE ${conditions.joinToString(" AND ")} ORDER BY d.name_key COLLATE \"C\", d.group_id LIMIT ?",
        ).use { statement ->
            values.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.setInt(values.size + 1, limit)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toEvent()) }
        }
    }

    /**
     * The id of a room other than [groupId] whose description that counts bears [nameKey] on
     * [territoryKey] (both normalized), or null. Among several (only possible through a direct
     * store), the lowest id.
     */
    fun duplicateOf(groupId: String, nameKey: String, territoryKey: String): String? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT d.group_id FROM group_directory d " +
                "WHERE d.territory_key = ? AND d.name_key = ? AND d.group_id <> ? AND $CANONICAL ORDER BY d.group_id LIMIT 1",
        ).use { statement ->
            statement.setString(1, territoryKey)
            statement.setString(2, nameKey)
            statement.setString(3, groupId)
            val rows = statement.executeQuery()
            if (rows.next()) rows.getString(1) else null
        }
    }

    /** One room of the directory as the guardrails compare it: its id, normalized name and territory. */
    data class NameRow(val groupId: String, val nameKey: String, val territoryKey: String)

    /**
     * The rooms that count (their name in force, edits included) on [territoryKeys] (normalized), or
     * on every territory but those when [except] is true. Used by the guardrails of the creation (D-132).
     */
    fun names(territoryKeys: Collection<String>, except: Boolean = false): List<NameRow> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT d.group_id, d.name_key, d.territory_key FROM group_directory d " +
                "WHERE ${if (except) "NOT " else ""}(d.territory_key = ANY(?)) AND $CANONICAL",
        ).use { statement ->
            statement.setArray(1, connection.createArrayOf("text", territoryKeys.distinct().toTypedArray()))
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(NameRow(rows.getString(1), rows.getString(2), rows.getString(3))) }
        }
    }

    /** The number of messages (kind 9) of [groupId], counted up to [cap] at most. */
    fun messageCount(groupId: String, cap: Int): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT count(*) FROM (SELECT 1 FROM event_tags t JOIN events e ON e.id = t.event_id " +
                "WHERE t.name = 'h' AND t.value = ? AND e.kind = ? LIMIT ?) AS m",
        ).use { statement ->
            statement.setString(1, groupId)
            statement.setInt(2, GroupKinds.CHAT_MESSAGE)
            statement.setInt(3, cap)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }
    }

    /**
     * Runs [block] while no other caller runs it, in this process or in any other server sharing the
     * database: the admission and the storage of a group description happen one at a time, so that
     * two rooms with the same name on the same territory (or two keys claiming the same id) cannot
     * both pass the checks at the same instant.
     *
     * Two locks: a lock of this process first, so that waiting requests do not each hold a connection
     * of the small pool; then a PostgreSQL advisory lock, held by a transaction of its own and released
     * when it ends, whatever happens. [block] uses other connections; what it commits is visible to the
     * next holder, which only starts once this transaction is over.
     */
    fun <T> exclusively(block: () -> T): T {
        if (!processLock.tryLock(lockWait.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            throw IllegalStateException("Timed out waiting for the lock of group descriptions")
        }
        try {
            return dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    connection.createStatement().use { it.execute("SET LOCAL lock_timeout = '${lockWait.inWholeMilliseconds}ms'") }
                    connection.prepareStatement("SELECT pg_advisory_xact_lock(?)").use { statement ->
                        statement.setLong(1, ADVISORY_LOCK_KEY)
                        statement.execute()
                    }
                    val result = block()
                    connection.commit()
                    result
                } catch (e: Exception) {
                    runCatching { connection.rollback() }
                    throw e
                } finally {
                    runCatching { connection.autoCommit = true }
                }
            }
        } finally {
            processLock.unlock()
        }
    }

    /**
     * Writes the rows of the group descriptions stored without one (before V4, or restored from a
     * backup taken before it); returns how many. Cheap when there is none: run at start-up.
     */
    fun indexMissing(): Int = dataSource.connection.use { connection ->
        val missing = connection.prepareStatement(
            "SELECT $EVENT_COLUMNS FROM events e WHERE e.kind = ? AND NOT EXISTS (SELECT 1 FROM group_directory d WHERE d.event_id = e.id)",
        ).use { statement ->
            statement.setInt(1, GroupKinds.METADATA)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toEvent()) }
        }
        missing.count { write(connection, it) }
    }

    companion object {
        /** The description that counts for its id: no earlier one (then no lower event id) for the same id. */
        private const val CANONICAL =
            "NOT EXISTS (SELECT 1 FROM group_directory o WHERE o.group_id = d.group_id AND (o.created_at, o.event_id) < (d.created_at, d.event_id))"

        private const val EVENT_COLUMNS = "e.id, e.pubkey, e.created_at, e.kind, e.tags::text AS tags, e.content, e.sig"

        /** Any fixed number shared by every server process; "PPFGROUP" in ASCII. */
        private const val ADVISORY_LOCK_KEY: Long = 0x5050_4647_524F_5550L

        /** In production: a busy server answers an error after 10 seconds rather than piling up requests. */
        val DEFAULT_LOCK_WAIT: Duration = 10.seconds

        /** One lock for the whole process, whatever the number of directories built on the same database. */
        private val processLock = ReentrantLock(true)

        /**
         * Writes the row of [event] when it is a well-formed group description (otherwise does nothing);
         * returns whether a row was written. Called within the transaction that stores the event.
         */
        fun write(connection: Connection, event: SignedEvent): Boolean {
            val group = Group.from(event) ?: return false
            return connection.prepareStatement(
                "INSERT INTO group_directory (event_id, group_id, created_at, name_key, territory_key, about_key) " +
                    "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
            ).use { insert ->
                insert.setString(1, event.id)
                insert.setString(2, group.id)
                insert.setLong(3, event.createdAt)
                insert.setString(4, group.normalizedName)
                insert.setString(5, group.normalizedTerritory)
                insert.setString(6, group.normalizedAbout)
                insert.executeUpdate() == 1
            }
        }

        private fun ResultSet.toEvent(): SignedEvent = SignedEvent(
            id = getString("id"),
            pubkey = getString("pubkey"),
            createdAt = getLong("created_at"),
            kind = getInt("kind"),
            tags = TagsJson.decode(getString("tags")),
            content = getString("content"),
            sig = getString("sig"),
        )
    }
}
