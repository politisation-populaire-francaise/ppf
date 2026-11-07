package org.politisationpopulairefrancaise.server.backoffice

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

enum class GrantStatus(val column: String) {
    ACTIVE("active"),

    /** Its end date passed (temporary access, S-60). */
    EXPIRED("expired"),

    /** Withdrawn by a member, by an emergency cut or a removal. */
    REVOKED("revoked"),

    /** A validator whose designating organisation lost its verification (D-112). */
    SUSPENDED("suspended"),
    ;

    companion object {
        fun of(column: String): GrantStatus = entries.first { it.column == column }
    }
}

/** A role given to a member (table team_role_grant). */
data class RoleGrant(
    val id: UUID,
    val accountId: UUID,
    val role: TeamRole,
    val scope: String,
    val organisationPubkey: String?,
    val grantedAt: Instant,
    val endsAt: Instant?,
    val grantedBy: UUID?,
    val approvedBy: UUID?,
    val status: GrantStatus,
    val closedAt: Instant?,
) {
    /** Active and not past its end date (the periodic task closes it shortly after). */
    fun isLive(now: Instant): Boolean = status == GrantStatus.ACTIVE && (endsAt == null || now.isBefore(endsAt))
}

/** Roles given to the members, with optional end dates (S-60) and the two people behind each grant (S-61). */
class TeamGrants(private val dataSource: DataSource) {

    fun insert(
        connection: Connection,
        accountId: UUID,
        role: TeamRole,
        organisationPubkey: String?,
        endsAt: Instant?,
        grantedBy: UUID?,
        approvedBy: UUID?,
        now: Instant,
    ): RoleGrant {
        val grant = RoleGrant(UUID.randomUUID(), accountId, role, SCOPE_ALL, organisationPubkey, now, endsAt, grantedBy, approvedBy, GrantStatus.ACTIVE, null)
        connection.prepareStatement(
            """
            INSERT INTO team_role_grant (id, account_id, role, scope, organisation_pubkey, granted_at, ends_at, granted_by, approved_by, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')
            """.trimIndent(),
        ).use { insert ->
            insert.setObject(1, grant.id)
            insert.setObject(2, accountId)
            insert.setString(3, role.id)
            insert.setString(4, SCOPE_ALL)
            insert.setText(5, organisationPubkey)
            insert.setInstant(6, now)
            insert.setInstant(7, endsAt)
            insert.setUuid(8, grantedBy)
            insert.setUuid(9, approvedBy)
            insert.executeUpdate()
        }
        return grant
    }

    fun find(id: UUID): RoleGrant? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE id = ?").use { statement ->
            statement.setObject(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toGrant() else null
        }
    }

    /** The live grants of [accountId] (active and not past their end). */
    fun live(accountId: UUID, now: Instant): List<RoleGrant> = dataSource.connection.use { live(it, accountId, now) }

    fun live(connection: Connection, accountId: UUID, now: Instant): List<RoleGrant> =
        connection.prepareStatement("$SELECT WHERE account_id = ? AND status = 'active' AND (ends_at IS NULL OR ends_at > ?) ORDER BY granted_at").use { statement ->
            statement.setObject(1, accountId)
            statement.setInstant(2, now)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toGrant()) }
        }

    /** Every grant of [accountId], newest first (the member page shows the history). */
    fun history(accountId: UUID): List<RoleGrant> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE account_id = ? ORDER BY granted_at DESC, id").use { statement ->
            statement.setObject(1, accountId)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toGrant()) }
        }
    }

    /** Whether [accountId] already holds [role] (an active grant, even past its end date not yet closed). */
    fun hasActive(connection: Connection, accountId: UUID, role: TeamRole): Boolean =
        connection.prepareStatement("SELECT 1 FROM team_role_grant WHERE account_id = ? AND role = ? AND status = 'active'").use { statement ->
            statement.setObject(1, accountId)
            statement.setString(2, role.id)
            statement.executeQuery().next()
        }

    /** How many active members hold a live grant of [role]. */
    fun countLiveHolders(connection: Connection, role: TeamRole, now: Instant): Int =
        connection.prepareStatement(
            """
            SELECT count(DISTINCT g.account_id) FROM team_role_grant g JOIN team_account a ON a.id = g.account_id
            WHERE g.role = ? AND g.status = 'active' AND (g.ends_at IS NULL OR g.ends_at > ?) AND a.status = 'active'
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, role.id)
            statement.setInstant(2, now)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }

    /** Closes the active grant [id] with [status]; returns it, or null when it was not active. */
    fun close(connection: Connection, id: UUID, status: GrantStatus, now: Instant): RoleGrant? {
        require(status != GrantStatus.ACTIVE)
        return connection.prepareStatement(
            "UPDATE team_role_grant SET status = ?, closed_at = ? WHERE id = ? AND status = 'active' RETURNING $COLUMNS",
        ).use { update ->
            update.setString(1, status.column)
            update.setInstant(2, now)
            update.setObject(3, id)
            val rows = update.executeQuery()
            if (rows.next()) rows.toGrant() else null
        }
    }

    /** Revokes every active grant of [accountId] (emergency cut, removal); returns them. */
    fun revokeAll(connection: Connection, accountId: UUID, now: Instant): List<RoleGrant> =
        connection.prepareStatement(
            "UPDATE team_role_grant SET status = 'revoked', closed_at = ? WHERE account_id = ? AND status = 'active' RETURNING $COLUMNS",
        ).use { update ->
            update.setInstant(1, now)
            update.setObject(2, accountId)
            val rows = update.executeQuery()
            buildList { while (rows.next()) add(rows.toGrant()) }
        }

    /** The active grants whose end date has passed (the periodic task expires them one by one, with a journal line each). */
    fun due(now: Instant): List<RoleGrant> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE status = 'active' AND ends_at IS NOT NULL AND ends_at <= ? ORDER BY ends_at").use { statement ->
            statement.setInstant(1, now)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toGrant()) }
        }
    }

    /** The active validator grants, with their designating organisation. */
    fun activeValidators(): List<RoleGrant> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE status = 'active' AND role = 'validator'").use { statement ->
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toGrant()) }
        }
    }

    /** The active validator grants designated by [organisation]. */
    fun activeValidatorsOf(connection: Connection, organisation: String): List<RoleGrant> =
        connection.prepareStatement("$SELECT WHERE status = 'active' AND role = 'validator' AND organisation_pubkey = ? FOR UPDATE").use { statement ->
            statement.setString(1, organisation)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toGrant()) }
        }

    private fun ResultSet.toGrant(): RoleGrant = RoleGrant(
        id = uuid("id")!!,
        accountId = uuid("account_id")!!,
        role = requireNotNull(TeamRole.fromId(getString("role"))),
        scope = getString("scope"),
        organisationPubkey = getString("organisation_pubkey"),
        grantedAt = instant("granted_at")!!,
        endsAt = instant("ends_at"),
        grantedBy = uuid("granted_by"),
        approvedBy = uuid("approved_by"),
        status = GrantStatus.of(getString("status")),
        closedAt = instant("closed_at"),
    )

    companion object {
        /** The only scope of version 1: every space, every territory (contract, section 5). */
        const val SCOPE_ALL: String = "all"

        private const val COLUMNS = "id, account_id, role, scope, organisation_pubkey, granted_at, ends_at, granted_by, approved_by, status, closed_at"
        private const val SELECT = "SELECT $COLUMNS FROM team_role_grant"
    }
}
