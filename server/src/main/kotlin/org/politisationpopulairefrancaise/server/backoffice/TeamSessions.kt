package org.politisationpopulairefrancaise.server.backoffice

import java.sql.Connection
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** A signed-in browser (table team_session): only the hash of its token is stored. */
data class TeamSession(
    val tokenHash: String,
    val accountId: UUID,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val expiresAt: Instant,
    val verifiedAt: Instant,
)

/** A session just opened, with the token of its cookie (given to the browser once). */
class OpenedSession(val session: TeamSession, val token: String)

/**
 * Sessions of the team: 30 minutes without a request or 8 hours after the sign-in at the most
 * (contract, section 4), then deleted. A session is never extended beyond its 8 hours: the person signs
 * in again with their key.
 */
class TeamSessions(private val dataSource: DataSource) {

    fun open(connection: Connection, accountId: UUID, now: Instant): OpenedSession {
        val token = Tokens.generate()
        val session = TeamSession(
            tokenHash = Tokens.hash(token),
            accountId = accountId,
            createdAt = now,
            lastSeenAt = now,
            expiresAt = now.plusSeconds(BackOfficeConfig.SESSION_MAX.inWholeSeconds),
            verifiedAt = now,
        )
        connection.prepareStatement(
            "INSERT INTO team_session (token_hash, account_id, created_at, last_seen_at, expires_at, verified_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).use { insert ->
            insert.setString(1, session.tokenHash)
            insert.setObject(2, accountId)
            insert.setInstant(3, now)
            insert.setInstant(4, now)
            insert.setInstant(5, session.expiresAt)
            insert.setInstant(6, now)
            insert.executeUpdate()
        }
        return OpenedSession(session, token)
    }

    /**
     * The live session of [token], touched (its idle time starts again); null when unknown, idle for more
     * than 30 minutes, past its 8 hours or when the account is no longer active. A dead session is deleted.
     */
    fun touch(token: String, now: Instant): TeamSession? {
        if (!Tokens.looksValid(token)) return null
        val hash = Tokens.hash(token)
        return dataSource.connection.use { connection ->
            val session = connection.prepareStatement(
                """
                SELECT s.token_hash, s.account_id, s.created_at, s.last_seen_at, s.expires_at, s.verified_at, a.status
                FROM team_session s JOIN team_account a ON a.id = s.account_id WHERE s.token_hash = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, hash)
                val rows = statement.executeQuery()
                if (!rows.next()) return@use null
                val found = TeamSession(
                    tokenHash = rows.getString("token_hash"),
                    accountId = rows.uuid("account_id")!!,
                    createdAt = rows.instant("created_at")!!,
                    lastSeenAt = rows.instant("last_seen_at")!!,
                    expiresAt = rows.instant("expires_at")!!,
                    verifiedAt = rows.instant("verified_at")!!,
                )
                found to rows.getString("status")
            } ?: return@use null
            val (found, status) = session
            val idleLimit = found.lastSeenAt.plusSeconds(BackOfficeConfig.SESSION_IDLE.inWholeSeconds)
            if (status != AccountStatus.ACTIVE.column || !now.isBefore(found.expiresAt) || !now.isBefore(idleLimit)) {
                delete(connection, hash)
                return@use null
            }
            connection.prepareStatement("UPDATE team_session SET last_seen_at = ? WHERE token_hash = ?").use { update ->
                update.setInstant(1, now)
                update.setString(2, hash)
                update.executeUpdate()
            }
            found.copy(lastSeenAt = now)
        }
    }

    /** A passkey was just verified again in this session (before a sensitive action). */
    fun markVerified(tokenHash: String, now: Instant): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE team_session SET verified_at = ?, last_seen_at = ? WHERE token_hash = ? AND expires_at > ?").use { update ->
            update.setInstant(1, now)
            update.setInstant(2, now)
            update.setString(3, tokenHash)
            update.setInstant(4, now)
            update.executeUpdate() == 1
        }
    }

    fun close(tokenHash: String): Boolean = dataSource.connection.use { delete(it, tokenHash) }

    /** Closes every session of [accountId] (emergency cut, removal); returns how many. */
    fun closeAll(connection: Connection, accountId: UUID): Int =
        connection.prepareStatement("DELETE FROM team_session WHERE account_id = ?").use { delete ->
            delete.setObject(1, accountId)
            delete.executeUpdate()
        }

    /** Deletes the sessions past their 8 hours or idle for more than 30 minutes; returns how many. */
    fun deleteExpired(now: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM team_session WHERE expires_at <= ? OR last_seen_at <= ?").use { delete ->
            delete.setInstant(1, now)
            delete.setInstant(2, now.minusSeconds(BackOfficeConfig.SESSION_IDLE.inWholeSeconds))
            delete.executeUpdate()
        }
    }

    private fun delete(connection: Connection, hash: String): Boolean =
        connection.prepareStatement("DELETE FROM team_session WHERE token_hash = ?").use { delete ->
            delete.setString(1, hash)
            delete.executeUpdate() == 1
        }
}
