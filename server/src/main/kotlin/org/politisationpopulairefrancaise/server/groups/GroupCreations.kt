package org.politisationpopulairefrancaise.server.groups

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

/** Why a room creation waits for the team (D-132). */
enum class HoldCause(val id: String) {
    /** Its name resembles a verified organisation's. */
    ORGANISATION("organisation"),

    /** Its name resembles a very active room of another territory. */
    ACTIVE_ROOM("active_room"),
    ;

    companion object {
        fun fromId(id: String): HoldCause = entries.first { it.id == id }
    }
}

/** A room creation held for review (table group_held_creation of V10). */
data class HeldCreation(
    val id: String,
    val event: SignedEvent,
    val cause: HoldCause,
    /** The names it resembles, for the team. */
    val resembles: List<String>,
    val status: String,
    /** Seconds since 1970. */
    val receivedAt: Long,
    val decidedAt: Long?,
    val reason: String?,
) {
    val pending: Boolean get() = status == PENDING

    companion object {
        const val PENDING = "pending"
        const val APPROVED = "approved"
        const val REJECTED = "rejected"
    }
}

/** Where a held creation stands, for its author (`GET /api/v1/group-creations?id=…`); the shape of HeldState. */
@Serializable
data class CreationState(
    val id: String,
    val status: String,
    @SerialName("received_at") val receivedAt: Long,
    @SerialName("decided_at") val decidedAt: Long? = null,
    val reason: String? = null,
)

/**
 * The room creations held for a review by the team (queue `groups.creations` of the back-office,
 * D-132). Only the signed description is kept, with why it waits; nothing about the request. A
 * decided creation is deleted [RETENTION_DAYS] days after the decision (at the next hold).
 */
class GroupCreations(private val dataSource: DataSource, private val clock: () -> Instant = Instant::now) {

    /** Keeps [event] for review; holding it again changes nothing. */
    fun hold(event: SignedEvent, cause: HoldCause, resembles: List<String>) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM group_held_creation WHERE status <> 'pending' AND decided_at < ?").use { statement ->
                statement.setObject(1, now().minusDays(RETENTION_DAYS))
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO group_held_creation (id, group_id, pubkey, event, cause, resembles, status, received_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'pending', ?) ON CONFLICT (id) DO NOTHING",
            ).use { statement ->
                statement.setString(1, event.id)
                statement.setString(2, event.tagValues("d").single())
                statement.setString(3, event.pubkey)
                statement.setString(4, event.toJson())
                statement.setString(5, cause.id)
                statement.setString(6, resembles.joinToString(SEPARATOR).take(MAX_RESEMBLES))
                statement.setObject(7, now())
                statement.executeUpdate()
            }
        }
    }

    fun find(id: String): HeldCreation? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE id = ?").use { statement ->
            statement.setString(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toHeld() else null
        }
    }

    /** The pending creations, oldest first. */
    fun pending(): List<HeldCreation> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE status = 'pending' ORDER BY received_at, id").use { statement ->
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toHeld()) }
        }
    }

    fun pendingCount(): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT count(*) FROM group_held_creation WHERE status = 'pending'").use { statement ->
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }
    }

    /** The pending creations of [pubkey] received in the last 24 hours (they count in the daily limit). */
    fun pendingToday(connection: Connection, pubkey: String): Int =
        connection.prepareStatement(
            "SELECT count(*) FROM group_held_creation WHERE pubkey = ? AND status = 'pending' AND received_at > ?",
        ).use { statement ->
            statement.setString(1, pubkey)
            statement.setObject(2, now().minusDays(1))
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }

    fun states(ids: List<String>): List<CreationState> {
        if (ids.isEmpty()) return emptyList()
        return dataSource.connection.use { connection ->
            connection.prepareStatement("$SELECT WHERE id = ANY(?)").use { statement ->
                statement.setArray(1, connection.createArrayOf("text", ids.distinct().toTypedArray()))
                val rows = statement.executeQuery()
                buildList {
                    while (rows.next()) {
                        val held = rows.toHeld()
                        add(CreationState(held.id, held.status, held.receivedAt, held.decidedAt, held.reason))
                    }
                }
            }
        }
    }

    /** Marks a pending creation decided; false when it is not pending any more. */
    fun decide(id: String, approved: Boolean, reason: String?): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE group_held_creation SET status = ?, decided_at = ?, reason = ? WHERE id = ? AND status = 'pending'",
        ).use { statement ->
            statement.setString(1, if (approved) HeldCreation.APPROVED else HeldCreation.REJECTED)
            statement.setObject(2, now())
            statement.setString(3, reason)
            statement.setString(4, id)
            statement.executeUpdate() == 1
        }
    }

    private fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock(), ZoneOffset.UTC)

    private fun ResultSet.toHeld(): HeldCreation = HeldCreation(
        id = getString("id"),
        event = SignedEvent.fromJson(getString("event")),
        cause = HoldCause.fromId(getString("cause")),
        resembles = getString("resembles").split(SEPARATOR).filter { it.isNotBlank() },
        status = getString("status"),
        receivedAt = getObject("received_at", OffsetDateTime::class.java).toEpochSecond(),
        decidedAt = getObject("decided_at", OffsetDateTime::class.java)?.toEpochSecond(),
        reason = getString("reason"),
    )

    companion object {
        const val RETENTION_DAYS: Long = 30
        const val MAX_REASON_LENGTH: Int = 1000
        private const val MAX_RESEMBLES: Int = 2000
        private const val SEPARATOR = "\n"
        private const val SELECT = "SELECT id, event, cause, resembles, status, received_at, decided_at, reason FROM group_held_creation"
    }
}
