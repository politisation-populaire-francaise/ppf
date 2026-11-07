package org.politisationpopulairefrancaise.server.backoffice

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

enum class ApprovalStatus(val column: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REFUSED("refused"),
    EXPIRED("expired"),
    CANCELLED("cancelled"),

    /** Approved, but the action could no longer be done (account gone, item already decided). */
    FAILED("failed"),
    ;

    companion object {
        fun of(column: String): ApprovalStatus = entries.first { it.column == column }
    }
}

/**
 * An action waiting for a second person (S-61, contract section 5): asked by [requestedBy], approved by
 * another member holding [permission], within 7 days.
 */
data class PendingApproval(
    val id: UUID,
    val action: String,
    val permission: String,
    val targetType: String,
    val targetId: String,
    val payload: Map<String, String>,
    val reason: String?,
    val requestedBy: UUID,
    val requestedAt: Instant,
    val expiresAt: Instant,
    val status: ApprovalStatus,
    val decidedBy: UUID?,
    val decidedAt: Instant?,
)

/** The requests of the two-person rule (table team_pending_approval). */
class PendingApprovals(private val dataSource: DataSource) {

    fun create(
        connection: Connection,
        action: String,
        permission: String,
        targetType: String,
        targetId: String,
        payload: Map<String, String>,
        reason: String?,
        requestedBy: UUID,
        now: Instant,
    ): PendingApproval {
        val approval = PendingApproval(
            id = UUID.randomUUID(),
            action = action,
            permission = permission,
            targetType = targetType,
            targetId = targetId,
            payload = payload,
            reason = reason,
            requestedBy = requestedBy,
            requestedAt = now,
            expiresAt = now.plusSeconds(BackOfficeConfig.APPROVAL_LIFETIME.inWholeSeconds),
            status = ApprovalStatus.PENDING,
            decidedBy = null,
            decidedAt = null,
        )
        connection.prepareStatement(
            """
            INSERT INTO team_pending_approval (id, action, permission, target_type, target_id, payload, reason, requested_by, requested_at, expires_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')
            """.trimIndent(),
        ).use { insert ->
            insert.setObject(1, approval.id)
            insert.setString(2, action)
            insert.setString(3, permission)
            insert.setString(4, targetType)
            insert.setString(5, targetId)
            insert.setString(6, AuditChain.encodeDetails(payload))
            insert.setText(7, reason)
            insert.setObject(8, requestedBy)
            insert.setInstant(9, now)
            insert.setInstant(10, approval.expiresAt)
            insert.executeUpdate()
        }
        return approval
    }

    fun find(id: UUID): PendingApproval? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE id = ?").use { statement ->
            statement.setObject(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toApproval() else null
        }
    }

    /** The requests still open (pending and not past their 7 days), oldest first. */
    fun open(now: Instant): List<PendingApproval> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE status = 'pending' AND expires_at > ? ORDER BY requested_at, id").use { statement ->
            statement.setInstant(1, now)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toApproval()) }
        }
    }

    /** Whether an open request for the same action on the same target already exists. */
    fun hasOpen(connection: Connection, action: String, targetType: String, targetId: String, key: String?, now: Instant): Boolean =
        connection.prepareStatement(
            "SELECT payload FROM team_pending_approval WHERE status = 'pending' AND expires_at > ? AND action = ? AND target_type = ? AND target_id = ?",
        ).use { statement ->
            statement.setInstant(1, now)
            statement.setString(2, action)
            statement.setString(3, targetType)
            statement.setString(4, targetId)
            val rows = statement.executeQuery()
            var found = false
            while (rows.next() && !found) {
                found = key == null || AuditChain.decodeDetails(rows.getString(1))[KEY] == key
            }
            found
        }

    /**
     * Locks the open request [id] for a decision and returns it, or null when it is no longer pending or
     * past its 7 days. The row stays locked until the end of the transaction of [connection].
     */
    fun lockOpen(connection: Connection, id: UUID, now: Instant): PendingApproval? =
        connection.prepareStatement("$SELECT WHERE id = ? AND status = 'pending' AND expires_at > ? FOR UPDATE").use { statement ->
            statement.setObject(1, id)
            statement.setInstant(2, now)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toApproval() else null
        }

    fun decide(connection: Connection, id: UUID, status: ApprovalStatus, decidedBy: UUID?, now: Instant): Boolean {
        require(status != ApprovalStatus.PENDING)
        return connection.prepareStatement("UPDATE team_pending_approval SET status = ?, decided_by = ?, decided_at = ? WHERE id = ? AND status = 'pending'").use { update ->
            update.setString(1, status.column)
            update.setUuid(2, decidedBy)
            update.setInstant(3, now)
            update.setObject(4, id)
            update.executeUpdate() == 1
        }
    }

    /** The pending requests past their 7 days, to expire with a journal line each. */
    fun due(now: Instant): List<PendingApproval> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE status = 'pending' AND expires_at <= ?").use { statement ->
            statement.setInstant(1, now)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toApproval()) }
        }
    }

    /** Cancels the open requests concerning [accountId] as target (emergency cut, removal); returns them. */
    fun cancelForTarget(connection: Connection, targetType: String, targetId: String, now: Instant): Int =
        connection.prepareStatement(
            "UPDATE team_pending_approval SET status = 'failed', decided_at = ? WHERE status = 'pending' AND target_type = ? AND target_id = ?",
        ).use { update ->
            update.setInstant(1, now)
            update.setString(2, targetType)
            update.setString(3, targetId)
            update.executeUpdate()
        }

    /** Deletes the requests decided before [cutoff] (their trace stays in the journal). */
    fun deleteDecidedBefore(cutoff: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM team_pending_approval WHERE status <> 'pending' AND decided_at < ?").use { delete ->
            delete.setInstant(1, cutoff)
            delete.executeUpdate()
        }
    }

    private fun ResultSet.toApproval(): PendingApproval = PendingApproval(
        id = uuid("id")!!,
        action = getString("action"),
        permission = getString("permission"),
        targetType = getString("target_type"),
        targetId = getString("target_id"),
        payload = AuditChain.decodeDetails(getString("payload")),
        reason = getString("reason"),
        requestedBy = uuid("requested_by")!!,
        requestedAt = instant("requested_at")!!,
        expiresAt = instant("expires_at")!!,
        status = ApprovalStatus.of(getString("status")),
        decidedBy = uuid("decided_by"),
        decidedAt = instant("decided_at"),
    )

    companion object {
        /** The payload entry that tells two requests on the same target apart (for example the role). */
        const val KEY: String = "key"

        private const val SELECT =
            "SELECT id, action, permission, target_type, target_id, payload, reason, requested_by, requested_at, expires_at, status, decided_by, decided_at FROM team_pending_approval"
    }
}
