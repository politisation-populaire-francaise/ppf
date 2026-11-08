package org.politisationpopulairefrancaise.server.archive

import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import javax.sql.DataSource

/** Where a proof stands (column `state` of V13__archives.sql). */
enum class ProofState(val code: String) {
    QUEUED("queued"),
    PENDING("pending"),
    ANCHORED("anchored"),
    VERIFIED("verified"),
    ;

    companion object {
        fun of(code: String): ProofState = entries.first { it.code == code }
    }
}

/** The Bitcoin block of a verified proof: height, hash as explorers write it, block time in seconds. */
class BitcoinAnchor(val height: Long, val blockHash: String, val time: Long)

/** A row of `archive_proof`. */
class StoredProof(
    val digest: ByteArray,
    val state: ProofState,
    val requestedOn: LocalDate,
    val batchRoot: ByteArray?,
    val ots: ByteArray?,
    val bitcoin: BitcoinAnchor?,
    val rfc3161: Rfc3161Token?,
)

/**
 * The table of V13__archives.sql (plain JDBC, blocking: call it from Dispatchers.IO). Every update
 * names the state it expects, so that a slow round never overwrites the work of a newer one.
 */
class ArchiveStore(private val dataSource: DataSource) {

    /** Queues [digest]; false when it is already known (queued, stamped or verified). */
    fun queue(digest: ByteArray, day: LocalDate): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "INSERT INTO archive_proof (digest, state, requested_on) VALUES (?, 'queued', ?) ON CONFLICT (digest) DO NOTHING",
        ).use { statement ->
            statement.setBytes(1, digest)
            statement.setDate(2, Date.valueOf(day))
            statement.executeUpdate() == 1
        }
    }

    /** The digests waiting for a batch, oldest first. */
    fun queued(limit: Int): List<ByteArray> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT digest FROM archive_proof WHERE state = 'queued' ORDER BY requested_on, digest LIMIT ?",
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getBytes(1)) } }
        }
    }

    /** Stores the proofs of a stamped batch ([proofs]: digest to `.ots` bytes); returns how many rows changed. */
    fun saveStamped(root: ByteArray, proofs: List<Pair<ByteArray, ByteArray>>): Int = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val changed = connection.prepareStatement(
                "UPDATE archive_proof SET state = 'pending', batch_root = ?, ots = ? WHERE digest = ? AND state = 'queued'",
            ).use { statement ->
                for ((digest, ots) in proofs) {
                    statement.setBytes(1, root)
                    statement.setBytes(2, ots)
                    statement.setBytes(3, digest)
                    statement.addBatch()
                }
                statement.executeBatch().count { it == 1 }
            }
            connection.commit()
            changed
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    /** Proofs in [state] (pending or anchored), oldest request first. */
    fun withState(state: ProofState, limit: Int): List<StoredProof> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE state = ? ORDER BY requested_on, digest LIMIT ?").use { statement ->
            statement.setString(1, state.code)
            statement.setInt(2, limit)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(read(rows)) } }
        }
    }

    /** Replaces the proof of [digest], still in state [from], and moves it to [to] (pending or anchored). */
    fun updateOts(digest: ByteArray, from: ProofState, ots: ByteArray, to: ProofState): Boolean = dataSource.connection.use { connection ->
        require(to == ProofState.PENDING || to == ProofState.ANCHORED) { "verified only through markVerified" }
        connection.prepareStatement("UPDATE archive_proof SET ots = ?, state = ? WHERE digest = ? AND state = ?").use { statement ->
            statement.setBytes(1, ots)
            statement.setString(2, to.code)
            statement.setBytes(3, digest)
            statement.setString(4, from.code)
            statement.executeUpdate() == 1
        }
    }

    /** An anchored proof whose block was checked against two explorers. */
    fun markVerified(digest: ByteArray, anchor: BitcoinAnchor): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE archive_proof SET state = 'verified', bitcoin_height = ?, block_hash = ?, block_time = ? WHERE digest = ? AND state = 'anchored'",
        ).use { statement ->
            statement.setLong(1, anchor.height)
            statement.setString(2, anchor.blockHash)
            statement.setLong(3, anchor.time)
            statement.setBytes(4, digest)
            statement.executeUpdate() == 1
        }
    }

    /** Roots of stamped batches still without an RFC 3161 token, requested on [since] or later, newest first. */
    fun rootsWithoutToken(since: LocalDate, limit: Int): List<ByteArray> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT batch_root FROM archive_proof WHERE batch_root IS NOT NULL AND rfc3161_token IS NULL AND requested_on >= ? " +
                "GROUP BY batch_root ORDER BY max(requested_on) DESC, batch_root LIMIT ?",
        ).use { statement ->
            statement.setDate(1, Date.valueOf(since))
            statement.setInt(2, limit)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getBytes(1)) } }
        }
    }

    /** The token of a batch root, written for every row of the batch; returns how many rows changed. */
    fun saveToken(root: ByteArray, token: Rfc3161Token): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE archive_proof SET rfc3161_token = ?, tsa_time = ?, tsa_cert_sha256 = ? WHERE batch_root = ? AND rfc3161_token IS NULL",
        ).use { statement ->
            statement.setBytes(1, token.token)
            statement.setTimestamp(2, Timestamp.from(token.time))
            statement.setBytes(3, token.tsaCertSha256)
            statement.setBytes(4, root)
            statement.executeUpdate()
        }
    }

    fun find(digest: ByteArray): StoredProof? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE digest = ?").use { statement ->
            statement.setBytes(1, digest)
            statement.executeQuery().use { rows -> if (rows.next()) read(rows) else null }
        }
    }

    private fun read(rows: ResultSet): StoredProof {
        val height = rows.getLong("bitcoin_height").takeUnless { rows.wasNull() }
        val token = rows.getBytes("rfc3161_token")
        return StoredProof(
            digest = rows.getBytes("digest"),
            state = ProofState.of(rows.getString("state")),
            requestedOn = rows.getDate("requested_on").toLocalDate(),
            batchRoot = rows.getBytes("batch_root"),
            ots = rows.getBytes("ots"),
            bitcoin = height?.let { BitcoinAnchor(it, rows.getString("block_hash"), rows.getLong("block_time")) },
            rfc3161 = token?.let {
                Rfc3161Token(it, rows.getTimestamp("tsa_time").toInstant(), rows.getBytes("tsa_cert_sha256"))
            },
        )
    }

    private companion object {
        const val SELECT = "SELECT digest, state, requested_on, batch_root, ots, bitcoin_height, block_hash, block_time, " +
            "rfc3161_token, tsa_time, tsa_cert_sha256 FROM archive_proof"
    }
}
