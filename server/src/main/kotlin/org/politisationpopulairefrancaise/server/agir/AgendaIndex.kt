package org.politisationpopulairefrancaise.server.agir

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import org.politisationpopulairefrancaise.server.groups.GroupNames
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * What GET /api/v1/agenda asks for: the mobilizations that overlap [from]..[to] (seconds since
 * 1970, both included: they start at or before [to] and end, or start when they have no end, at or
 * after [from]), on the territory [territory] (already normalized by [GroupNames]; null for every
 * territory), of one of [types] (empty for every type), organised by one of [organizers] (author or
 * key named organiser; empty for anyone), at most [limit].
 */
data class AgendaFilter(
    val from: Long,
    val to: Long,
    val territory: String? = null,
    val types: List<String> = emptyList(),
    val organizers: List<String> = emptyList(),
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT: Int = 100
        const val MAX_LIMIT: Int = 500
    }
}

/**
 * The table agenda_entry (V5__agir.sql): one row per stored mobilization (kind 31923), with its
 * dates, normalized territory, type, status and organisers, so that the agenda is read by date in
 * SQL whatever the number of events.
 *
 * Rows are written by EventRepository through [write], in the transaction that stores the event, and
 * disappear with it (ON DELETE CASCADE): a corrected or cancelled version replaces the row of the old
 * one. Mobilizations stored before the table existed are indexed by [indexMissing].
 */
class AgendaIndex(private val dataSource: DataSource) {

    /** The signed events of the mobilizations matching [filter], by start then event id. */
    fun query(filter: AgendaFilter): List<SignedEvent> = dataSource.connection.use { connection ->
        val conditions = mutableListOf("a.starts_at <= ?", "COALESCE(a.ends_at, a.starts_at) >= ?")
        val binders = mutableListOf<(java.sql.PreparedStatement, Int) -> Unit>(
            { s, i -> s.setLong(i, filter.to) },
            { s, i -> s.setLong(i, filter.from) },
        )
        if (filter.territory != null) {
            conditions += "a.territory_key = ?"
            binders += { s, i -> s.setString(i, filter.territory) }
        }
        if (filter.types.isNotEmpty()) {
            conditions += "a.event_type = ANY(?)"
            binders += { s, i -> s.setArray(i, connection.createArrayOf("text", filter.types.toTypedArray())) }
        }
        if (filter.organizers.isNotEmpty()) {
            conditions += "a.organizers && ?"
            binders += { s, i -> s.setArray(i, connection.createArrayOf("text", filter.organizers.toTypedArray())) }
        }
        connection.prepareStatement(
            "SELECT $EVENT_COLUMNS FROM agenda_entry a JOIN events e ON e.id = a.event_id " +
                "WHERE ${conditions.joinToString(" AND ")} ORDER BY a.starts_at ASC, a.event_id ASC LIMIT ?",
        ).use { statement ->
            binders.forEachIndexed { index, bind -> bind(statement, index + 1) }
            statement.setInt(binders.size + 1, filter.limit.coerceIn(1, AgendaFilter.MAX_LIMIT))
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toEvent()) }
        }
    }

    /**
     * Writes the rows of the mobilizations stored without one (before V5, or restored from a backup
     * taken before it); returns how many. Cheap when there is none: run at start-up.
     */
    fun indexMissing(): Int = dataSource.connection.use { connection ->
        val missing = connection.prepareStatement(
            "SELECT $EVENT_COLUMNS FROM events e WHERE e.kind = ? AND NOT EXISTS (SELECT 1 FROM agenda_entry a WHERE a.event_id = e.id)",
        ).use { statement ->
            statement.setInt(1, AgirFormats.KIND_MOBILIZATION)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toEvent()) }
        }
        missing.count { write(connection, it) }
    }

    /** One row of the index, as [entryOf] reads it from a mobilization. */
    data class Entry(
        val address: String,
        val startsAt: Long,
        val endsAt: Long?,
        val territoryKey: String,
        val eventType: String,
        val status: String,
        val organizers: List<String>,
    )

    companion object {
        private const val EVENT_COLUMNS = "e.id, e.pubkey, e.created_at, e.kind, e.tags::text AS tags, e.content, e.sig"

        private val SECONDS = Regex("[0-9]{1,12}")

        /** The status of an event without `ppf-event-status` (or with an unknown one). */
        const val DEFAULT_STATUS: String = "prevu"

        /** The type of an event whose `ppf-event-type` is unknown (only possible through a direct store). */
        const val DEFAULT_TYPE: String = "autre"

        /**
         * The row of [event], or null when it is not a mobilization with a readable start. Lenient on
         * purpose: the API refuses malformed mobilizations, but an event stored directly (development
         * server, restored backup) must not make the store fail.
         */
        fun entryOf(event: SignedEvent): Entry? {
            if (event.kind != AgirFormats.KIND_MOBILIZATION) return null
            val address = EventReplacement.replacementKey(event) ?: return null
            val start = event.firstTagValue(AgirFormats.TAG_START)?.takeIf(SECONDS::matches)?.toLong() ?: return null
            val end = event.firstTagValue(AgirFormats.TAG_END)?.takeIf(SECONDS::matches)?.toLong()?.takeIf { it > start }
            val organizers = buildList {
                add(event.pubkey)
                event.tags.filter { it.size >= 4 && it[0] == AgirFormats.TAG_PUBKEY && it[3] == AgirFormats.ROLE_ORGANIZER && isHexKey(it[1]) }
                    .forEach { add(it[1]) }
            }.distinct()
            return Entry(
                address = address,
                startsAt = start,
                endsAt = end,
                territoryKey = event.firstTagValue(AgirFormats.TAG_TERRITORY)?.let(GroupNames::normalize).orEmpty(),
                eventType = event.firstTagValue(AgirFormats.TAG_EVENT_TYPE)?.takeIf { it in AgirFormats.EVENT_TYPES } ?: DEFAULT_TYPE,
                status = event.firstTagValue(AgirFormats.TAG_EVENT_STATUS)?.takeIf { it in AgirFormats.EVENT_STATUSES } ?: DEFAULT_STATUS,
                organizers = organizers,
            )
        }

        /**
         * Writes the row of [event] when it is a mobilization with a readable start (otherwise does
         * nothing); returns whether a row was written. Called within the transaction that stores the event.
         */
        fun write(connection: Connection, event: SignedEvent): Boolean {
            val entry = entryOf(event) ?: return false
            return connection.prepareStatement(
                "INSERT INTO agenda_entry (event_id, address, starts_at, ends_at, territory_key, event_type, status, organizers) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
            ).use { insert ->
                insert.setString(1, event.id)
                insert.setString(2, entry.address)
                insert.setLong(3, entry.startsAt)
                if (entry.endsAt != null) insert.setLong(4, entry.endsAt) else insert.setNull(4, java.sql.Types.BIGINT)
                insert.setString(5, entry.territoryKey)
                insert.setString(6, entry.eventType)
                insert.setString(7, entry.status)
                insert.setArray(8, connection.createArrayOf("text", entry.organizers.toTypedArray()))
                insert.executeUpdate() == 1
            }
        }

        private fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

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
