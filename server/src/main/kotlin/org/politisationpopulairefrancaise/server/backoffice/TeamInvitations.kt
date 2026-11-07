package org.politisationpopulairefrancaise.server.backoffice

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** An invitation to join the team (table team_invitation); the token of its link is never stored. */
data class TeamInvitation(
    val id: UUID,
    val pseudonym: String,
    val proposedRoles: List<TeamRole>,
    val organisationPubkey: String?,
    val rolesEndAt: Instant?,
    val bootstrap: Boolean,
    val createdBy: UUID?,
    val createdAt: Instant,
    val expiresAt: Instant,
)

/** An invitation just created, with the token of its link: shown once, then forgotten. */
class IssuedInvitation(val invitation: TeamInvitation, val token: String)

/** Invitations (S-57): one-time links valid 48 hours, deleted when used or expired. */
class TeamInvitations(private val dataSource: DataSource) {

    fun create(
        connection: Connection,
        pseudonym: String,
        roles: List<TeamRole>,
        organisationPubkey: String?,
        rolesEndAt: Instant?,
        createdBy: UUID?,
        now: Instant,
    ): IssuedInvitation {
        val token = Tokens.generate()
        val invitation = TeamInvitation(
            id = UUID.randomUUID(),
            pseudonym = pseudonym,
            proposedRoles = roles.distinct(),
            organisationPubkey = organisationPubkey,
            rolesEndAt = rolesEndAt,
            bootstrap = createdBy == null,
            createdBy = createdBy,
            createdAt = now,
            expiresAt = now.plusSeconds(BackOfficeConfig.INVITATION_LIFETIME.inWholeSeconds),
        )
        connection.prepareStatement(
            """
            INSERT INTO team_invitation (id, token_hash, pseudonym, proposed_roles, organisation_pubkey, roles_end_at, bootstrap, created_by, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { insert ->
            insert.setObject(1, invitation.id)
            insert.setString(2, Tokens.hash(token))
            insert.setString(3, invitation.pseudonym)
            insert.setArray(4, connection.createArrayOf("text", invitation.proposedRoles.map { it.id }.toTypedArray()))
            insert.setText(5, organisationPubkey)
            insert.setInstant(6, rolesEndAt)
            insert.setBoolean(7, invitation.bootstrap)
            insert.setUuid(8, createdBy)
            insert.setInstant(9, invitation.createdAt)
            insert.setInstant(10, invitation.expiresAt)
            insert.executeUpdate()
        }
        return IssuedInvitation(invitation, token)
    }

    /** The valid invitation of [token], if any (unknown, used and expired ones are all just absent). */
    fun findValid(token: String, now: Instant): TeamInvitation? {
        if (!Tokens.looksValid(token)) return null
        return dataSource.connection.use { connection ->
            connection.prepareStatement("$SELECT WHERE token_hash = ? AND expires_at > ?").use { statement ->
                statement.setString(1, Tokens.hash(token))
                statement.setInstant(2, now)
                val rows = statement.executeQuery()
                if (rows.next()) rows.toInvitation() else null
            }
        }
    }

    /** Uses the invitation [id] once: deletes it and returns it, or null when it is gone or expired. */
    fun consume(connection: Connection, id: UUID, now: Instant): TeamInvitation? =
        connection.prepareStatement(
            "DELETE FROM team_invitation WHERE id = ? AND expires_at > ? RETURNING id, pseudonym, proposed_roles, organisation_pubkey, roles_end_at, bootstrap, created_by, created_at, expires_at",
        ).use { delete ->
            delete.setObject(1, id)
            delete.setInstant(2, now)
            val rows = delete.executeQuery()
            if (rows.next()) rows.toInvitation() else null
        }

    /** The invitations still valid, newest first (the team page lists them, never their links). */
    fun pending(now: Instant): List<TeamInvitation> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE expires_at > ? ORDER BY created_at DESC").use { statement ->
            statement.setInstant(1, now)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toInvitation()) }
        }
    }

    /** Withdraws an invitation before it is used. */
    fun delete(connection: Connection, id: UUID): Boolean =
        connection.prepareStatement("DELETE FROM team_invitation WHERE id = ?").use { delete ->
            delete.setObject(1, id)
            delete.executeUpdate() == 1
        }

    /** Valid bootstrap invitations (counted with the super administrators by the bootstrap command). */
    fun pendingBootstrap(connection: Connection, now: Instant): Int =
        connection.prepareStatement("SELECT count(*) FROM team_invitation WHERE bootstrap AND expires_at > ?").use { statement ->
            statement.setInstant(1, now)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }

    fun deleteExpired(now: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM team_invitation WHERE expires_at <= ?").use { delete ->
            delete.setInstant(1, now)
            delete.executeUpdate()
        }
    }

    private fun ResultSet.toInvitation(): TeamInvitation = TeamInvitation(
        id = uuid("id")!!,
        pseudonym = getString("pseudonym"),
        proposedRoles = (getArray("proposed_roles").array as Array<*>).mapNotNull { TeamRole.fromId(it as String) },
        organisationPubkey = getString("organisation_pubkey"),
        rolesEndAt = instant("roles_end_at"),
        bootstrap = getBoolean("bootstrap"),
        createdBy = uuid("created_by"),
        createdAt = instant("created_at")!!,
        expiresAt = instant("expires_at")!!,
    )

    private companion object {
        const val SELECT = "SELECT id, pseudonym, proposed_roles, organisation_pubkey, roles_end_at, bootstrap, created_by, created_at, expires_at FROM team_invitation"
    }
}
