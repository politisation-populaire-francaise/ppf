package org.politisationpopulairefrancaise.server.events

import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/** What callers may ask for; every list is a disjunction, the lists are conjoined. */
data class EventFilter(
    val kinds: List<Int> = emptyList(),
    val authors: List<String> = emptyList(),
    /** Values of the `d` tag (stable identifiers of addressable contents). */
    val identifiers: List<String> = emptyList(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT: Int = 50
        const val MAX_LIMIT: Int = 500
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
 */
class EventRepository(private val dataSource: DataSource) {

    fun store(event: SignedEvent): StoreOutcome = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val outcome = storeInTransaction(connection, event)
            connection.commit()
            outcome
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    private fun storeInTransaction(connection: Connection, event: SignedEvent): StoreOutcome {
        connection.prepareStatement("SELECT 1 FROM events WHERE id = ?").use { statement ->
            statement.setString(1, event.id)
            if (statement.executeQuery().next()) return StoreOutcome.DUPLICATE
        }
        val address = EventReplacement.replacementKey(event)
        if (address != null) {
            connection.prepareStatement("SELECT id, created_at FROM events WHERE address = ? FOR UPDATE").use { statement ->
                statement.setString(1, address)
                val existing = statement.executeQuery()
                if (existing.next()) {
                    val existingId = existing.getString(1)
                    val existingCreatedAt = existing.getLong(2)
                    if (!EventReplacement.supersedes(event.createdAt, event.id, existingCreatedAt, existingId)) {
                        return StoreOutcome.SUPERSEDED
                    }
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
        return StoreOutcome.STORED
    }

    fun find(id: String): SignedEvent? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE id = ?").use { statement ->
            statement.setString(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toEvent() else null
        }
    }

    /** Newest first; on equal dates, by id, so pages are stable. */
    fun query(filter: EventFilter): List<SignedEvent> = dataSource.connection.use { connection ->
        val conditions = mutableListOf<String>()
        val binders = mutableListOf<(java.sql.PreparedStatement, Int) -> Unit>()
        if (filter.kinds.isNotEmpty()) {
            conditions += "kind = ANY(?)"
            binders += { s, i -> s.setArray(i, connection.createArrayOf("integer", filter.kinds.toTypedArray())) }
        }
        if (filter.authors.isNotEmpty()) {
            conditions += "pubkey = ANY(?)"
            binders += { s, i -> s.setArray(i, connection.createArrayOf("text", filter.authors.toTypedArray())) }
        }
        if (filter.identifiers.isNotEmpty()) {
            conditions += "EXISTS (SELECT 1 FROM jsonb_array_elements(tags) t WHERE t->>0 = 'd' AND t->>1 = ANY(?))"
            binders += { s, i -> s.setArray(i, connection.createArrayOf("text", filter.identifiers.toTypedArray())) }
        }
        filter.since?.let { since ->
            conditions += "created_at >= ?"
            binders += { s, i -> s.setLong(i, since) }
        }
        filter.until?.let { until ->
            conditions += "created_at <= ?"
            binders += { s, i -> s.setLong(i, until) }
        }
        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        val limit = filter.limit.coerceIn(1, EventFilter.MAX_LIMIT)
        connection.prepareStatement("$SELECT$where ORDER BY created_at DESC, id ASC LIMIT ?").use { statement ->
            binders.forEachIndexed { index, bind -> bind(statement, index + 1) }
            statement.setInt(binders.size + 1, limit)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toEvent()) }
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
    }
}
