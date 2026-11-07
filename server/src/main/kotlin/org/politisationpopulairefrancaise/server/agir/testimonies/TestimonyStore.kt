package org.politisationpopulairefrancaise.server.agir.testimonies

import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyState
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

/** A testimony as the server keeps it: never anything readable but its status. */
data class StoredTestimony(
    val id: String,
    val envelope: SignedEvent,
    val status: HeldStatus,
    val receivedAt: Long,
    val decidedAt: Long?,
    val reason: String?,
    val publishedId: String?,
    /** The encrypted files the envelope names (ids). */
    val files: List<String>,
    /** The envelope for the evidence key sent with it, if the witness gave the original. */
    val evidenceId: String?,
) {
    fun state(): TestimonyState = TestimonyState(id, status, receivedAt, decidedAt, reason, publishedId)
}

/** A file in the folder of the testimonies, named by the SHA-256 of its bytes. */
private fun Path.fileOf(kind: String, id: String): Path {
    require(id.length == 64 && id.all { it in '0'..'9' || it in 'a'..'f' }) { "A file is named by its SHA-256" }
    return resolve(kind).resolve(id)
}

/**
 * The tables of V9__agir_temoignages.sql and the folder PPF_TESTIMONY_DIR: `files/` for the
 * encrypted files, `public/` for the photos of published testimonies. Every method is blocking
 * (JDBC, disk): call from Dispatchers.IO.
 */
class TestimonyStore(private val dataSource: DataSource, private val directory: Path) {
    init {
        Files.createDirectories(directory.resolve(FILES))
        Files.createDirectories(directory.resolve(PUBLIC))
    }

    // --- Encrypted files -------------------------------------------------------------------------

    /** Total size of the encrypted files kept, in bytes. */
    fun storedBytes(): Long = query("SELECT COALESCE(SUM(size), 0) FROM testimony_file") { it.getLong(1) }.single()

    fun hasFile(id: String): Boolean = query("SELECT 1 FROM testimony_file WHERE id = ?", id) { true }.isNotEmpty()

    /** Writes [bytes] under [id] (their SHA-256), atomically; false when the file was already there. */
    fun putFile(id: String, bytes: ByteArray, receivedAt: Long): Boolean {
        if (hasFile(id)) return false
        val target = directory.fileOf(FILES, id)
        val temporary = Files.createTempFile(directory.resolve(FILES), "upload-", ".part")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO testimony_file (id, size, received_at) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING").use { statement ->
                statement.setString(1, id)
                statement.setLong(2, bytes.size.toLong())
                statement.setLong(3, receivedAt)
                statement.executeUpdate() == 1
            }
        }
    }

    fun readFile(id: String): ByteArray? {
        if (!hasFile(id)) return null
        val path = directory.fileOf(FILES, id)
        return if (Files.exists(path)) Files.readAllBytes(path) else null
    }

    /** The owner of each of [ids] (null when unclaimed); absent ids are not on the server. */
    fun owners(ids: Collection<String>): Map<String, String?> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        return query("SELECT id, owner FROM testimony_file WHERE id IN ($placeholders)", *ids.toTypedArray()) { it.getString(1) to it.getString(2) }.toMap()
    }

    // --- Testimonies -----------------------------------------------------------------------------

    sealed interface Insert {
        data object Stored : Insert
        data object Duplicate : Insert
        /** A named file belongs to another testimony. */
        data object FileTaken : Insert
    }

    /** Stores a testimony and claims its files, in one transaction. */
    fun insert(envelope: SignedEvent, files: List<String>, evidence: SignedEvent?, evidenceFiles: List<String>, receivedAt: Long, evidenceExpiresAt: Long): Insert =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val existing = connection.prepareStatement("SELECT envelope FROM testimony_submission WHERE id = ? FOR UPDATE").use { statement ->
                    statement.setString(1, envelope.id)
                    statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                }
                if (existing != null) {
                    connection.rollback()
                    return@use Insert.Duplicate
                }
                val claims = files.map { it to envelope.id } + evidenceFiles.map { it to evidence!!.id }
                for ((file, owner) in claims) {
                    val claimed = connection.prepareStatement(
                        "UPDATE testimony_file SET owner = ?, evidence = ? WHERE id = ? AND (owner IS NULL OR owner = ?)",
                    ).use { statement ->
                        statement.setString(1, owner)
                        statement.setBoolean(2, owner != envelope.id)
                        statement.setString(3, file)
                        statement.setString(4, owner)
                        statement.executeUpdate()
                    }
                    if (claimed != 1) {
                        connection.rollback()
                        return@use Insert.FileTaken
                    }
                }
                connection.prepareStatement("INSERT INTO testimony_submission (id, envelope, status, received_at, evidence_id) VALUES (?, ?, 'pending', ?, ?)").use { statement ->
                    statement.setString(1, envelope.id)
                    statement.setString(2, envelope.toJson())
                    statement.setLong(3, receivedAt)
                    if (evidence == null) statement.setNull(4, Types.VARCHAR) else statement.setString(4, evidence.id)
                    statement.executeUpdate()
                }
                if (evidence != null) {
                    connection.prepareStatement("INSERT INTO testimony_evidence (id, envelope, received_at, expires_at) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING").use { statement ->
                        statement.setString(1, evidence.id)
                        statement.setString(2, evidence.toJson())
                        statement.setLong(3, receivedAt)
                        statement.setLong(4, evidenceExpiresAt)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
                Insert.Stored
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }

    fun find(id: String): StoredTestimony? = dataSource.connection.use { connection ->
        query(connection, "$SELECT_TESTIMONY WHERE id = ?", id) { rows -> rows.toTestimony(connection) }.firstOrNull()
    }

    fun states(ids: List<String>): List<TestimonyState> {
        val known = ids.distinct().mapNotNull { id ->
            query("SELECT status, received_at, decided_at, reason, published_id FROM testimony_submission WHERE id = ?", id) { rows ->
                TestimonyState(id, statusOf(rows.getString(1)), rows.getLong(2), rows.nullableLong(3), rows.getString(4), rows.getString(5))
            }.firstOrNull()
        }
        return known
    }

    /** The testimonies waiting for the team, oldest first. */
    fun pending(): List<StoredTestimony> = dataSource.connection.use { connection ->
        query(connection, "$SELECT_TESTIMONY WHERE status = 'pending' ORDER BY received_at, id") { rows ->
            rows.toTestimony(connection)
        }
    }

    fun evidenceEnvelope(id: String): SignedEvent? =
        query("SELECT envelope FROM testimony_evidence WHERE id = ?", id) { SignedEvent.fromJson(it.getString(1)) }.firstOrNull()

    /** Records the decision on a pending testimony; false when it is not (or no longer) pending. */
    fun decide(id: String, status: HeldStatus, decidedAt: Long, reason: String?, publishedId: String?): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE testimony_submission SET status = ?, decided_at = ?, reason = ?, published_id = ? WHERE id = ? AND status = 'pending'",
        ).use { statement ->
            statement.setString(1, status.name.lowercase())
            statement.setLong(2, decidedAt)
            if (reason == null) statement.setNull(3, Types.VARCHAR) else statement.setString(3, reason)
            if (publishedId == null) statement.setNull(4, Types.VARCHAR) else statement.setString(4, publishedId)
            statement.setString(5, id)
            statement.executeUpdate() == 1
        }
    }

    // --- Published photos ------------------------------------------------------------------------

    fun putPublic(sha256: String, mime: String, bytes: ByteArray, eventId: String) {
        val target = directory.fileOf(PUBLIC, sha256)
        if (!Files.exists(target)) {
            val temporary = Files.createTempFile(directory.resolve(PUBLIC), "public-", ".part")
            try {
                Files.write(temporary, bytes)
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO published_media (sha256, mime, size, event_id) VALUES (?, ?, ?, ?) ON CONFLICT (sha256) DO NOTHING").use { statement ->
                statement.setString(1, sha256)
                statement.setString(2, mime)
                statement.setLong(3, bytes.size.toLong())
                statement.setString(4, eventId)
                statement.executeUpdate()
            }
        }
    }

    /** A published photo and its media type, or null. */
    fun readPublic(sha256: String): Pair<String, ByteArray>? {
        val mime = query("SELECT mime FROM published_media WHERE sha256 = ?", sha256) { it.getString(1) }.firstOrNull() ?: return null
        val path = directory.fileOf(PUBLIC, sha256)
        return if (Files.exists(path)) mime to Files.readAllBytes(path) else null
    }

    // --- Retention -------------------------------------------------------------------------------

    /**
     * Deletes what has expired at [now]: files no envelope claimed since [orphansBefore], testimonies
     * decided before [decidedBefore] with their files, originals for the lawyers past their date.
     * Returns how many rows went, for the log (counts only).
     */
    fun purge(now: Long, orphansBefore: Long, decidedBefore: Long): Int {
        var deleted = 0
        val expiredFiles = mutableListOf<String>()
        dataSource.connection.use { connection ->
            expiredFiles += query(connection, "SELECT id FROM testimony_file WHERE owner IS NULL AND received_at < ?", orphansBefore) { it.getString(1) }
            val decided = query(connection, "SELECT id FROM testimony_submission WHERE status <> 'pending' AND decided_at < ?", decidedBefore) { it.getString(1) }
            val evidence = query(connection, "SELECT id FROM testimony_evidence WHERE expires_at < ?", now) { it.getString(1) }
            for (owner in decided + evidence) expiredFiles += query(connection, "SELECT id FROM testimony_file WHERE owner = ?", owner) { it.getString(1) }
            for (id in decided) deleted += update(connection, "DELETE FROM testimony_submission WHERE id = ?", id)
            for (id in evidence) deleted += update(connection, "DELETE FROM testimony_evidence WHERE id = ?", id)
            for (id in expiredFiles.distinct()) deleted += update(connection, "DELETE FROM testimony_file WHERE id = ?", id)
        }
        for (id in expiredFiles.distinct()) Files.deleteIfExists(directory.fileOf(FILES, id))
        return deleted
    }

    // --- JDBC helpers ----------------------------------------------------------------------------

    private fun ResultSet.toTestimony(connection: Connection): StoredTestimony {
        val id = getString(1)
        val files = query(connection, "SELECT id FROM testimony_file WHERE owner = ? ORDER BY id", id) { it.getString(1) }
        return StoredTestimony(id, SignedEvent.fromJson(getString(2)), statusOf(getString(3)), getLong(4), nullableLong(5), getString(6), getString(7), files, getString(8))
    }

    private fun <T> query(sql: String, vararg parameters: Any, map: (ResultSet) -> T): List<T> =
        dataSource.connection.use { connection -> query(connection, sql, *parameters, map = map) }

    private fun <T> query(connection: Connection, sql: String, vararg parameters: Any, map: (ResultSet) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value ->
                when (value) {
                    is Long -> statement.setLong(index + 1, value)
                    else -> statement.setString(index + 1, value.toString())
                }
            }
            statement.executeQuery().use { rows ->
                val out = mutableListOf<T>()
                while (rows.next()) out += map(rows)
                out
            }
        }

    private fun update(connection: Connection, sql: String, id: String): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, id)
            statement.executeUpdate()
        }

    private fun ResultSet.nullableLong(index: Int): Long? = getLong(index).takeIf { !wasNull() }

    private fun statusOf(column: String): HeldStatus = HeldStatus.entries.first { it.name.lowercase() == column }

    companion object {
        private const val SELECT_TESTIMONY = "SELECT id, envelope, status, received_at, decided_at, reason, published_id, evidence_id FROM testimony_submission"
        const val FILES: String = "files"
        const val PUBLIC: String = "public"
    }
}
