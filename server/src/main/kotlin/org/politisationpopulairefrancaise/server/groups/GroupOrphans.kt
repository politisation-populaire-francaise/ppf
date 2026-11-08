package org.politisationpopulairefrancaise.server.groups

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** A room left without administrator when its last one was declared compromised (S-146). */
data class GroupOrphan(
    val groupId: String,
    /** The administrator's key declared compromised, lowercase hex. */
    val compromisedPubkey: String,
    /** The notice (kind 1777) that declared it. */
    val noticeId: String,
    /** Seconds since 1970. */
    val orphanedAt: Long,
    /** `leave` or `admin` once the team decided; null while it waits. */
    val decision: String?,
    val decidedAt: Long?,
) {
    val pending: Boolean get() = decision == null
}

/**
 * The rooms without administrator (table `group_orphan`, filled by `CompromisedAccounts` when it
 * removes the roles of a compromised key), for the queue groups.orphans of the back-office
 * (docs/integration/salons-reprise.md). Public identifiers only; never a list of members.
 */
class GroupOrphans(private val dataSource: DataSource, private val clock: () -> Instant = Instant::now) {

    fun pendingCount(): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT count(*) FROM group_orphan WHERE decided_at IS NULL").use { statement ->
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }
    }

    /** The rooms waiting for the team, oldest first. */
    fun pending(): List<GroupOrphan> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE decided_at IS NULL ORDER BY orphaned_at, group_id LIMIT $MAX_PENDING").use { statement ->
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toOrphan()) }
        }
    }

    fun find(groupId: String): GroupOrphan? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE group_id = ?").use { statement ->
            statement.setString(1, groupId)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toOrphan() else null
        }
    }

    /**
     * Records the decision of the team ([decision] `leave` or `admin`) for a room still waiting, and
     * still without administrator; false when it is no longer waiting or has an administrator again.
     */
    fun decide(groupId: String, decision: String): Boolean {
        require(decision == LEAVE || decision == ADMIN) { "Unknown decision" }
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE group_orphan SET decision = ?, decided_at = ? WHERE group_id = ? AND decided_at IS NULL " +
                    "AND (? = 'admin' OR NOT EXISTS (SELECT 1 FROM group_role WHERE group_id = ? AND role = 'admin'))",
            ).use { statement ->
                statement.setString(1, decision)
                statement.setTimestamp(2, Timestamp.from(clock()))
                statement.setString(3, groupId)
                statement.setString(4, decision)
                statement.setString(5, groupId)
                statement.executeUpdate() == 1
            }
        }
    }

    /** Whether the room has an administrator again (a takeover published meanwhile). */
    fun hasAdministrator(groupId: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT 1 FROM group_role WHERE group_id = ? AND role = 'admin' LIMIT 1").use { statement ->
            statement.setString(1, groupId)
            statement.executeQuery().next()
        }
    }

    /** Messages of the room dated after [since] (seconds), counted up to [cap]: how alive the room is. */
    fun recentMessages(groupId: String, since: Long, cap: Int = ACTIVITY_CAP): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT count(*) FROM (SELECT 1 FROM event_tags t JOIN events e ON e.id = t.event_id " +
                "WHERE t.name = 'h' AND t.value = ? AND e.kind = ? AND e.created_at > ? LIMIT ?) AS m",
        ).use { statement ->
            statement.setString(1, groupId)
            statement.setInt(2, GroupKinds.CHAT_MESSAGE)
            statement.setLong(3, since)
            statement.setInt(4, cap)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }
    }

    private fun ResultSet.toOrphan(): GroupOrphan = GroupOrphan(
        groupId = getString("group_id"),
        compromisedPubkey = getString("compromised_pubkey"),
        noticeId = getString("notice_id"),
        orphanedAt = getTimestamp("orphaned_at").toInstant().epochSecond,
        decision = getString("decision"),
        decidedAt = getTimestamp("decided_at")?.toInstant()?.epochSecond,
    )

    companion object {
        const val LEAVE: String = "leave"
        const val ADMIN: String = "admin"

        /** Shown as « 1000 et plus » beyond. */
        const val ACTIVITY_CAP: Int = 1000

        private const val MAX_PENDING = 500
        private const val SELECT = "SELECT group_id, compromised_pubkey, notice_id, orphaned_at, decision, decided_at FROM group_orphan"
    }
}
