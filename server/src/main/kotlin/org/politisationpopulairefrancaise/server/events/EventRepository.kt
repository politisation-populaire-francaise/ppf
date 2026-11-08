package org.politisationpopulairefrancaise.server.events

import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import org.politisationpopulairefrancaise.server.agir.AgendaIndex
import org.politisationpopulairefrancaise.server.groups.GroupIndex
import org.politisationpopulairefrancaise.server.groups.GroupModeration
import org.politisationpopulairefrancaise.server.legal.LegalRegister
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * What callers may ask for; every list is a disjunction, the lists are conjoined.
 *
 * [tags] maps a tag name to the accepted values: an event matches when, for every name, one of
 * its tags with that name carries one of the values (`p` = recipient of an envelope, `h` = group
 * of a message, `d` = identifier of a content...). [identifiers] is kept as a shorthand for the `d` tag.
 */
data class EventFilter(
    val kinds: List<Int> = emptyList(),
    val authors: List<String> = emptyList(),
    /** Values of the `d` tag (stable identifiers of addressable contents). */
    val identifiers: List<String> = emptyList(),
    val tags: Map<String, List<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    /** [tags] with [identifiers] folded into the `d` entry. */
    fun allTags(): Map<String, List<String>> =
        if (identifiers.isEmpty()) tags else tags + ("d" to (tags["d"].orEmpty() + identifiers))

    companion object {
        const val DEFAULT_LIMIT: Int = 50
        const val MAX_LIMIT: Int = 500
    }
}

/** Which tags are written to the lookup table (see V2__event_tags.sql) and may be filtered on. */
object TagIndex {
    const val MAX_NAME_LENGTH: Int = 64
    const val MAX_VALUE_BYTES: Int = 1024

    fun isIndexable(name: String, value: String): Boolean =
        name.length in 1..MAX_NAME_LENGTH && value.encodeToByteArray().size <= MAX_VALUE_BYTES

    /** The distinct (name, value) pairs of an event that go to the lookup table. */
    fun rowsOf(event: SignedEvent): List<Pair<String, String>> = event.tags
        .filter { it.size >= 2 && isIndexable(it[0], it[1]) }
        .map { it[0] to it[1] }
        .distinct()
}

/**
 * A retention in days that each recipient may set for the events addressed to them, through an
 * addressable event of theirs: the event `preferenceKind:<recipient>:preferenceIdentifier`, signed
 * by the recipient, whose first tag named [daysTag] carries the number of days. The server bounds
 * the value between [minDays] and [maxDays]; without a readable setting, [defaultDays] applies.
 */
data class RecipientRetention(
    val defaultDays: Int,
    val minDays: Int,
    val maxDays: Int,
    val preferenceKind: Int,
    val preferenceIdentifier: String,
    val daysTag: String,
) {
    init {
        require(minDays >= 1) { "The minimum retention is at least one day" }
        require(maxDays >= minDays) { "The maximum retention cannot be shorter than the minimum" }
        require(defaultDays in minDays..maxDays) { "The default retention lies between the minimum and the maximum" }
    }
}

enum class StoreOutcome {
    /** New event, now stored (possibly replacing an older version). */
    STORED,

    /** Exactly this event was already stored. */
    DUPLICATE,

    /** A more recent version of this replaceable content is already stored; the event is dropped. */
    SUPERSEDED,
}

/**
 * Stores and reads signed events in PostgreSQL. Signature verification is the API's job, before
 * anything reaches this class. Replacement follows [EventReplacement], like the local database.
 *
 * Every stored public content also enters the legal [register] (D-077), in the same transaction as the
 * event: no publication escapes it and no register row exists for an event that was not stored.
 */
class EventRepository(internal val dataSource: DataSource, private val register: LegalRegister = LegalRegister()) {

    fun store(event: SignedEvent): StoreOutcome = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val outcome = storeInTransaction(connection, event)
            connection.commit()
            outcome
        } catch (e: SQLException) {
            connection.rollback()
            // Two versions of the same content (or the same event twice) arriving at the same
            // instant: the second INSERT hits a unique index. Nothing to store, nothing to report.
            if (e.sqlState == UNIQUE_VIOLATION) {
                if (EventReplacement.replacementKey(event) != null) StoreOutcome.SUPERSEDED else StoreOutcome.DUPLICATE
            } else {
                throw e
            }
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    /**
     * Runs [block] in one transaction of its own connection, committed when it returns and rolled
     * back when it throws: what the moderation of the rooms reads, decides and stores (with
     * [storeIn]) happens all at once or not at all.
     */
    fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Exception) {
            runCatching { connection.rollback() }
            throw e
        } finally {
            runCatching { connection.autoCommit = true }
        }
    }

    /** [store] within the transaction of [connection] (see [transaction]); the caller commits. */
    fun storeIn(connection: Connection, event: SignedEvent): StoreOutcome = storeInTransaction(connection, event)

    private fun storeInTransaction(connection: Connection, event: SignedEvent): StoreOutcome {
        connection.prepareStatement("SELECT 1 FROM events WHERE id = ?").use { statement ->
            statement.setString(1, event.id)
            if (statement.executeQuery().next()) return StoreOutcome.DUPLICATE
        }
        val address = EventReplacement.replacementKey(event)
        var replaced: LegalRegister.ReplacedVersion? = null
        if (address != null) {
            // The content of an old version is read only for a profile, whose pseudonyms the register compares.
            connection.prepareStatement("SELECT id, created_at, CASE WHEN kind = 0 THEN content END FROM events WHERE address = ? FOR UPDATE").use { statement ->
                statement.setString(1, address)
                val existing = statement.executeQuery()
                if (existing.next()) {
                    val existingId = existing.getString(1)
                    val existingCreatedAt = existing.getLong(2)
                    if (!EventReplacement.supersedes(event.createdAt, event.id, existingCreatedAt, existingId)) {
                        return StoreOutcome.SUPERSEDED
                    }
                    // The address holds the key: the old version has the same author.
                    replaced = LegalRegister.ReplacedVersion(existingId, existingCreatedAt, event.pubkey, existing.getString(3))
                    // The tag rows of the old version go with it (ON DELETE CASCADE).
                    connection.prepareStatement("DELETE FROM events WHERE id = ?").use { delete ->
                        delete.setString(1, existingId)
                        delete.executeUpdate()
                    }
                }
            }
        }
        connection.prepareStatement(
            "INSERT INTO events (id, pubkey, created_at, kind, tags, content, sig, address) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
        ).use { insert ->
            insert.setString(1, event.id)
            insert.setString(2, event.pubkey)
            insert.setLong(3, event.createdAt)
            insert.setInt(4, event.kind)
            insert.setString(5, TagsJson.encode(event.tags))
            insert.setString(6, event.content)
            insert.setString(7, event.sig)
            insert.setString(8, address)
            insert.executeUpdate()
        }
        // The legal register, in this transaction: a failure of anything after it undoes it with the event.
        register.recordStored(connection, event, replaced)
        insertTags(connection, event)
        // A group description also enters the directory of rooms (and leaves it with the event);
        // the creator of a new room becomes its first administrator (D-131).
        GroupIndex.write(connection, event)
        GroupModeration.described(connection, event)
        // A mobilization also enters the agenda (and leaves it with the event).
        AgendaIndex.write(connection, event)
        return StoreOutcome.STORED
    }

    private fun insertTags(connection: Connection, event: SignedEvent) {
        val rows = TagIndex.rowsOf(event)
        if (rows.isEmpty()) return
        connection.prepareStatement("INSERT INTO event_tags (event_id, name, value) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").use { insert ->
            rows.forEach { (name, value) ->
                insert.setString(1, event.id)
                insert.setString(2, name)
                insert.setString(3, value)
                insert.addBatch()
            }
            insert.executeBatch()
        }
    }

    /** The event with this id, unless a moderator removed it from its room (D-131). */
    fun find(id: String): SignedEvent? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE id = ? AND $NOT_REMOVED").use { statement ->
            statement.setString(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toEvent() else null
        }
    }

    /**
     * Newest first; on equal dates, by id, so pages are stable. A message removed from its room by a
     * moderator (D-131) is never served; the removal (9005) is, with its reason.
     */
    fun query(filter: EventFilter): List<SignedEvent> = dataSource.connection.use { connection ->
        val conditions = mutableListOf(NOT_REMOVED)
        val binders = mutableListOf<(PreparedStatement, Int) -> Unit>()
        if (filter.kinds.isNotEmpty()) {
            conditions += "kind = ANY(?)"
            binders += { s, i -> s.setArray(i, connection.createArrayOf("integer", filter.kinds.toTypedArray())) }
        }
        if (filter.authors.isNotEmpty()) {
            conditions += "pubkey = ANY(?)"
            binders += { s, i -> s.setArray(i, connection.createArrayOf("text", filter.authors.toTypedArray())) }
        }
        // One EXISTS per tag name (conjunction); the values of a name form a disjunction.
        for ((name, values) in filter.allTags()) {
            if (values.isEmpty()) continue
            conditions += "EXISTS (SELECT 1 FROM event_tags t WHERE t.event_id = events.id AND t.name = ? AND t.value = ANY(?))"
            binders += { s, i -> s.setString(i, name) }
            binders += { s, i -> s.setArray(i, connection.createArrayOf("text", values.toTypedArray())) }
        }
        filter.since?.let { since ->
            conditions += "created_at >= ?"
            binders += { s, i -> s.setLong(i, since) }
        }
        filter.until?.let { until ->
            conditions += "created_at <= ?"
            binders += { s, i -> s.setLong(i, until) }
        }
        val where = " WHERE " + conditions.joinToString(" AND ")
        val limit = filter.limit.coerceIn(1, EventFilter.MAX_LIMIT)
        connection.prepareStatement("$SELECT$where ORDER BY created_at DESC, id ASC LIMIT ?").use { statement ->
            binders.forEachIndexed { index, bind -> bind(statement, index + 1) }
            statement.setInt(binders.size + 1, limit)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toEvent()) }
        }
    }

    /**
     * Deletes the events of [kind] that have been on the server (by reception date, which only the
     * server knows) longer than the retention of their recipient, named by the tag [recipientTag];
     * their tag rows go with them. Returns how many were deleted.
     *
     * The retention of a recipient is read, in SQL, from their setting event described by [retention]
     * (the first tag named `daysTag` of the addressable event `preferenceKind:<recipient>:preferenceIdentifier`),
     * bounded to `minDays..maxDays`; a missing, unreadable (not 1 to 9 digits) or unparsable setting
     * means `defaultDays`, as does an event without recipient tag. Nothing younger than `minDays` is
     * ever deleted, which lets the partial index on `received_at` do the first cut.
     */
    fun deleteExpiredByRecipient(kind: Int, recipientTag: String, retention: RecipientRetention, now: Instant): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                DELETE FROM events w
                WHERE w.kind = ?
                  AND w.received_at < ?
                  AND w.received_at < ? - make_interval(days => LEAST(?, GREATEST(?, COALESCE((
                        SELECT CASE WHEN t ->> 1 ~ '^[0-9]{1,9}$' THEN (t ->> 1)::int END
                        FROM event_tags r
                        JOIN events pref ON pref.address = ? || r.value || ?
                        CROSS JOIN LATERAL jsonb_array_elements(pref.tags) WITH ORDINALITY AS x(t, n)
                        WHERE r.event_id = w.id AND r.name = ? AND jsonb_typeof(t) = 'array' AND t ->> 0 = ?
                        ORDER BY n LIMIT 1
                      ), ?))))
                """.trimIndent(),
            ).use { statement ->
                val nowUtc = now.atOffset(ZoneOffset.UTC)
                statement.setInt(1, kind)
                statement.setObject(2, nowUtc.minusDays(retention.minDays.toLong()))
                statement.setObject(3, nowUtc)
                statement.setInt(4, retention.maxDays)
                statement.setInt(5, retention.minDays)
                statement.setString(6, "${retention.preferenceKind}:")
                statement.setString(7, ":${retention.preferenceIdentifier}")
                statement.setString(8, recipientTag)
                statement.setString(9, retention.daysTag)
                statement.setInt(10, retention.defaultDays)
                statement.executeUpdate()
            }
        }

    /**
     * Keeps only the [keep] most recently received events of [kind] carrying the tag [name] = [value]
     * and deletes the older ones; returns how many were deleted. Bounds the mailbox of one recipient.
     */
    fun keepNewestByTag(kind: Int, name: String, value: String, keep: Int): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            DELETE FROM events WHERE id IN (
                SELECT e.id FROM events e JOIN event_tags t ON t.event_id = e.id
                WHERE e.kind = ? AND t.name = ? AND t.value = ?
                ORDER BY e.received_at DESC, e.id ASC OFFSET ?
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, kind)
            statement.setString(2, name)
            statement.setString(3, value)
            statement.setInt(4, keep.coerceAtLeast(0))
            statement.executeUpdate()
        }
    }

    fun count(): Long = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery("SELECT count(*) FROM events")
            rows.next()
            rows.getLong(1)
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

    private companion object {
        const val SELECT = "SELECT id, pubkey, created_at, kind, tags::text AS tags, content, sig FROM events"

        /** Not removed from its room by a moderator (group_removed_event, V10). */
        const val NOT_REMOVED = "NOT EXISTS (SELECT 1 FROM group_removed_event r WHERE r.event_id = events.id)"

        /** PostgreSQL SQLSTATE of a unique index violation. */
        const val UNIQUE_VIOLATION = "23505"
    }
}
