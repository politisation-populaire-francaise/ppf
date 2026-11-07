package org.politisationpopulairefrancaise.server.backoffice

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

enum class AccountStatus(val column: String) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    REMOVED("removed"),
    ;

    companion object {
        fun of(column: String): AccountStatus = entries.first { it.column == column }
    }
}

/** A member of the team (table team_account): a pseudonym, never an e-mail address. */
data class TeamAccount(
    val id: UUID,
    val pseudonym: String,
    val nostrPubkey: String?,
    val userHandle: ByteArray,
    val status: AccountStatus,
    val createdAt: Instant,
    val invitedBy: UUID?,
    val closedAt: Instant?,
) {
    override fun equals(other: Any?): Boolean = other is TeamAccount && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/** A passkey or security key of a member (table team_credential): only public data of the authenticator. */
class StoredCredential(
    val id: UUID,
    val accountId: UUID,
    val credentialId: ByteArray,
    val attestedData: ByteArray,
    val signCount: Long,
    val uvInitialized: Boolean,
    val backupEligible: Boolean,
    val backupState: Boolean,
    val name: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)

/** What a registration ceremony produced, ready to store. */
class NewCredential(
    val credentialId: ByteArray,
    val attestedData: ByteArray,
    val signCount: Long,
    val uvInitialized: Boolean,
    val backupEligible: Boolean,
    val backupState: Boolean,
    val name: String,
)

/** Pseudonyms of the team: 2 to 40 characters, letters, digits, spaces and `-_.'`, never an e-mail address. */
object Pseudonyms {
    const val MIN_LENGTH: Int = 2
    const val MAX_LENGTH: Int = 40

    fun normalize(value: String): String? {
        val text = value.trim().replace(Regex("\\s+"), " ")
        if (text.length !in MIN_LENGTH..MAX_LENGTH) return null
        if (!text.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' || it == '.' || it == '\'' }) return null
        return text
    }
}

/** Names of the keys: 1 to 64 printable characters. */
object KeyNames {
    const val MAX_LENGTH: Int = 64

    fun normalize(value: String?): String? {
        val text = value?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (text.isEmpty() || text.length > MAX_LENGTH || text.any { it.isISOControl() }) return null
        return text
    }
}

/** The accounts of the team and their keys (tables team_account and team_credential). */
class TeamAccounts(private val dataSource: DataSource) {

    fun find(id: UUID): TeamAccount? = dataSource.connection.use { find(it, id) }

    fun find(connection: Connection, id: UUID): TeamAccount? =
        connection.prepareStatement("$SELECT_ACCOUNT WHERE id = ?").use { statement ->
            statement.setObject(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toAccount() else null
        }

    /** Every account, active ones first, then by pseudonym. */
    fun all(): List<TeamAccount> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT_ACCOUNT ORDER BY (status = 'active') DESC, lower(pseudonym), id").use { statement ->
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toAccount()) }
        }
    }

    /** Whether an active account already uses [pseudonym] (case ignored). */
    fun pseudonymTaken(connection: Connection, pseudonym: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM team_account WHERE status = 'active' AND lower(pseudonym) = lower(?)").use { statement ->
            statement.setString(1, pseudonym)
            statement.executeQuery().next()
        }

    fun create(connection: Connection, pseudonym: String, userHandle: ByteArray, invitedBy: UUID?, now: Instant): TeamAccount {
        val id = UUID.randomUUID()
        connection.prepareStatement(
            "INSERT INTO team_account (id, pseudonym, user_handle, status, created_at, invited_by) VALUES (?, ?, ?, 'active', ?, ?)",
        ).use { insert ->
            insert.setObject(1, id)
            insert.setString(2, pseudonym)
            insert.setBytes(3, userHandle)
            insert.setInstant(4, now)
            insert.setUuid(5, invitedBy)
            insert.executeUpdate()
        }
        return TeamAccount(id, pseudonym, null, userHandle.copyOf(), AccountStatus.ACTIVE, now, invitedBy, null)
    }

    /** Sets the account to [status] (suspended or removed); a removed account also loses its Nostr key. */
    fun close(connection: Connection, id: UUID, status: AccountStatus, now: Instant): Boolean {
        require(status != AccountStatus.ACTIVE)
        val sql = if (status == AccountStatus.REMOVED) {
            "UPDATE team_account SET status = ?, closed_at = COALESCE(closed_at, ?), nostr_pubkey = NULL WHERE id = ? AND status <> 'removed'"
        } else {
            "UPDATE team_account SET status = ?, closed_at = ? WHERE id = ? AND status = 'active'"
        }
        return connection.prepareStatement(sql).use { update ->
            update.setString(1, status.column)
            update.setInstant(2, now)
            update.setObject(3, id)
            update.executeUpdate() == 1
        }
    }

    fun setNostrKey(id: UUID, pubkey: String?): Boolean = dataSource.connection.use { connection ->
        require(pubkey == null || isHexKey(pubkey))
        connection.prepareStatement("UPDATE team_account SET nostr_pubkey = ? WHERE id = ? AND status = 'active'").use { update ->
            update.setText(1, pubkey)
            update.setObject(2, id)
            update.executeUpdate() == 1
        }
    }

    // Keys ------------------------------------------------------------------------------------------

    fun addCredential(connection: Connection, accountId: UUID, credential: NewCredential, now: Instant): StoredCredential {
        val id = UUID.randomUUID()
        connection.prepareStatement(
            """
            INSERT INTO team_credential (id, account_id, credential_id, attested_data, sign_count, uv_initialized, backup_eligible, backup_state, name, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { insert ->
            insert.setObject(1, id)
            insert.setObject(2, accountId)
            insert.setBytes(3, credential.credentialId)
            insert.setBytes(4, credential.attestedData)
            insert.setLong(5, credential.signCount)
            insert.setBoolean(6, credential.uvInitialized)
            insert.setBoolean(7, credential.backupEligible)
            insert.setBoolean(8, credential.backupState)
            insert.setString(9, credential.name)
            insert.setInstant(10, now)
            insert.executeUpdate()
        }
        return StoredCredential(
            id, accountId, credential.credentialId, credential.attestedData, credential.signCount, credential.uvInitialized,
            credential.backupEligible, credential.backupState, credential.name, now, null,
        )
    }

    fun credentialExists(credentialId: ByteArray): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT 1 FROM team_credential WHERE credential_id = ?").use { statement ->
            statement.setBytes(1, credentialId)
            statement.executeQuery().next()
        }
    }

    fun credentialByCredentialId(credentialId: ByteArray): StoredCredential? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT_CREDENTIAL WHERE credential_id = ?").use { statement ->
            statement.setBytes(1, credentialId)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toCredential() else null
        }
    }

    fun credentials(accountId: UUID): List<StoredCredential> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT_CREDENTIAL WHERE account_id = ? ORDER BY created_at, id").use { statement ->
            statement.setObject(1, accountId)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toCredential()) }
        }
    }

    fun credentialCount(connection: Connection, accountId: UUID): Int =
        connection.prepareStatement("SELECT count(*) FROM team_credential WHERE account_id = ?").use { statement ->
            statement.setObject(1, accountId)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }

    /** Records a successful use: the new signature counter and the date. */
    fun markUsed(id: UUID, signCount: Long, backupState: Boolean, now: Instant) = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE team_credential SET sign_count = ?, backup_state = ?, last_used_at = ? WHERE id = ?").use { update ->
            update.setLong(1, signCount)
            update.setBoolean(2, backupState)
            update.setInstant(3, now)
            update.setObject(4, id)
            update.executeUpdate()
        }
    }

    /** Deletes one key of [accountId]; returns its name, or null when it was not theirs. */
    fun deleteCredential(connection: Connection, accountId: UUID, id: UUID): String? =
        connection.prepareStatement("DELETE FROM team_credential WHERE id = ? AND account_id = ? RETURNING name").use { delete ->
            delete.setObject(1, id)
            delete.setObject(2, accountId)
            val rows = delete.executeQuery()
            if (rows.next()) rows.getString(1) else null
        }

    /** Deletes every key of [accountId] (emergency cut, removal); returns how many. */
    fun deleteCredentials(connection: Connection, accountId: UUID): Int =
        connection.prepareStatement("DELETE FROM team_credential WHERE account_id = ?").use { delete ->
            delete.setObject(1, accountId)
            delete.executeUpdate()
        }

    private fun ResultSet.toAccount(): TeamAccount = TeamAccount(
        id = uuid("id")!!,
        pseudonym = getString("pseudonym"),
        nostrPubkey = getString("nostr_pubkey"),
        userHandle = getBytes("user_handle"),
        status = AccountStatus.of(getString("status")),
        createdAt = instant("created_at")!!,
        invitedBy = uuid("invited_by"),
        closedAt = instant("closed_at"),
    )

    private fun ResultSet.toCredential(): StoredCredential = StoredCredential(
        id = uuid("id")!!,
        accountId = uuid("account_id")!!,
        credentialId = getBytes("credential_id"),
        attestedData = getBytes("attested_data"),
        signCount = getLong("sign_count"),
        uvInitialized = getBoolean("uv_initialized"),
        backupEligible = getBoolean("backup_eligible"),
        backupState = getBoolean("backup_state"),
        name = getString("name"),
        createdAt = instant("created_at")!!,
        lastUsedAt = instant("last_used_at"),
    )

    private companion object {
        const val SELECT_ACCOUNT = "SELECT id, pseudonym, nostr_pubkey, user_handle, status, created_at, invited_by, closed_at FROM team_account"
        const val SELECT_CREDENTIAL =
            "SELECT id, account_id, credential_id, attested_data, sign_count, uv_initialized, backup_eligible, backup_state, name, created_at, last_used_at FROM team_credential"
    }
}
