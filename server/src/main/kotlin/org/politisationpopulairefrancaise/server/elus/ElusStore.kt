package org.politisationpopulairefrancaise.server.elus

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.postgresql.PGConnection
import org.postgresql.copy.PGCopyOutputStream
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** The import being served: its index and what is needed to answer for a package without the database. */
data class ServedImport(
    val id: Long,
    val source: String,
    val finishedAt: Instant,
    val index: ByteArray,
    val indexSha256: String,
    /** Package code to its SHA-256 (the ETag). */
    val packages: Map<String, String>,
)

/** A package as stored: [gzip] holds the exact JSON body ([bytes] bytes of SHA-256 [sha256]) compressed. */
class StoredPackage(
    val code: String,
    val name: String,
    val sha256: String,
    val bytes: Int,
    val postalCodes: List<String>,
    val gzip: ByteArray,
)

/** Where the last import attempt stands, for the schedule of the next one. */
data class LastAttempt(val source: String, val status: String, val at: Instant)

/**
 * The tables of V6__elus.sql. One import at a time across processes (an advisory lock held by
 * [ImportLock]); the staging is written with COPY, in a stream.
 */
class ElusStore(val dataSource: DataSource) {

    /** The advisory lock of the imports, on a connection kept for the whole import. */
    class ImportLock internal constructor(val connection: Connection) : AutoCloseable {
        override fun close() {
            connection.use { c ->
                try {
                    c.prepareStatement("SELECT pg_advisory_unlock(?)").use { it.setLong(1, LOCK_KEY); it.executeQuery().close() }
                } catch (e: Exception) {
                    // Closing the connection releases the lock anyway.
                }
            }
        }
    }

    /** The lock, or null when another import holds it. */
    fun tryLock(): ImportLock? {
        val connection = dataSource.connection
        try {
            connection.autoCommit = true
            val locked = connection.prepareStatement("SELECT pg_try_advisory_lock(?)").use { statement ->
                statement.setLong(1, LOCK_KEY)
                statement.executeQuery().use { it.next() && it.getBoolean(1) }
            }
            if (locked) return ImportLock(connection)
            connection.close()
            return null
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }

    /** Starts an import of [source]: removes what an interrupted import left, returns the new id. */
    fun begin(lock: ImportLock, source: String, now: Instant): Long {
        val c = lock.connection
        // DELETE, not TRUNCATE: the restricted role of the server (afterMigrate.sql, D-127) may delete rows but
        // has no TRUNCATE right; with TRUNCATE, every import failed in preproduction with « permission denied ».
        c.createStatement().use { it.executeUpdate("DELETE FROM elus_staging") }
        c.createStatement().use { it.executeUpdate("DELETE FROM elus_import WHERE status = 'building'") }
        return c.prepareStatement("INSERT INTO elus_import (source, status, started_at) VALUES (?, 'building', ?) RETURNING id").use { statement ->
            statement.setString(1, source)
            statement.setTimestamp(2, Timestamp.from(now))
            statement.executeQuery().use { it.next(); it.getLong(1) }
        }
    }

    /**
     * Writes officials into the staging of [importId] with COPY: [block] is called with a function
     * that stages one official. Nothing is kept in memory.
     */
    fun <T> stage(lock: ImportLock, importId: Long, block: ((StagedOfficial) -> Unit) -> T): T {
        val pg = lock.connection.unwrap(PGConnection::class.java)
        val copy = PGCopyOutputStream(
            pg,
            "COPY elus_staging (import_id, area, mandate, territory, commune, name, function, rank, since, function_since) FROM STDIN WITH (FORMAT csv)",
            1 shl 16,
        )
        val writer = BufferedWriter(OutputStreamWriter(copy, Charsets.UTF_8), 1 shl 16)
        try {
            val result = block { official ->
                writer.write(importId.toString())
                writer.write(',')
                writer.csv(official.area).write(',')
                writer.write(official.mandate.ordinal.toString())
                writer.write(',')
                writer.csv(official.territory).write(',')
                writer.csvOrNull(official.commune).write(',')
                writer.csv(official.name).write(',')
                writer.csvOrNull(official.function).write(',')
                writer.write(official.rank.toString())
                writer.write(',')
                writer.csv(official.since).write(',')
                writer.csvOrNull(official.functionSince).write('\n')
            }
            writer.close()
            return result
        } catch (e: Exception) {
            try {
                copy.cancelCopy()
            } catch (ignored: Exception) {
                // The original failure matters more.
            }
            throw e
        }
    }

    /**
     * The staged officials of one package: those filed under one of [areas] (intercommunalities
     * aside), the councillors of the intercommunalities [sirens] wherever they are filed, and the
     * president of regional council [region].
     */
    fun staged(lock: ImportLock, importId: Long, areas: Collection<String>, sirens: Collection<String>, region: String?): List<StagedOfficial> {
        val c = lock.connection
        val sql = """
            SELECT area, mandate, territory, commune, name, function, rank, since, function_since FROM elus_staging
            WHERE import_id = ? AND (
                (area = ANY(?) AND mandate <> ${Mandate.COMMUNAUTAIRE.ordinal})
                OR (mandate = ${Mandate.COMMUNAUTAIRE.ordinal} AND territory = ANY(?))
                OR (mandate = ${Mandate.REGIONAL.ordinal} AND territory = ? AND rank = 0)
            )
        """.trimIndent()
        return c.prepareStatement(sql).use { statement ->
            statement.setLong(1, importId)
            statement.setArray(2, c.createArrayOf("text", areas.toTypedArray()))
            statement.setArray(3, c.createArrayOf("text", sirens.toTypedArray()))
            statement.setString(4, region ?: "")
            statement.fetchSize = 5_000
            statement.executeQuery().use { rows ->
                val list = ArrayList<StagedOfficial>()
                while (rows.next()) {
                    list += StagedOfficial(
                        area = rows.getString(1),
                        mandate = Mandate.entries[rows.getInt(2)],
                        territory = rows.getString(3),
                        commune = rows.getString(4),
                        name = rows.getString(5),
                        function = rows.getString(6),
                        rank = rows.getInt(7),
                        since = rows.getString(8),
                        functionSince = rows.getString(9),
                    )
                }
                list
            }
        }
    }

    /** The areas present in the staging of [importId]. */
    fun stagedAreas(lock: ImportLock, importId: Long): Set<String> =
        lock.connection.prepareStatement("SELECT DISTINCT area FROM elus_staging WHERE import_id = ?").use { statement ->
            statement.setLong(1, importId)
            statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
        }

    fun clearStaging(lock: ImportLock, importId: Long) {
        lock.connection.prepareStatement("DELETE FROM elus_staging WHERE import_id = ?").use { it.setLong(1, importId); it.executeUpdate() }
    }

    fun savePackage(lock: ImportLock, importId: Long, stored: StoredPackage) {
        val c = lock.connection
        c.prepareStatement("INSERT INTO elus_package (import_id, code, name, sha256, bytes, postal_codes, body_gzip) VALUES (?, ?, ?, ?, ?, ?, ?)").use { statement ->
            statement.setLong(1, importId)
            statement.setString(2, stored.code)
            statement.setString(3, stored.name)
            statement.setString(4, stored.sha256)
            statement.setInt(5, stored.bytes)
            statement.setArray(6, c.createArrayOf("text", stored.postalCodes.toTypedArray()))
            statement.setBytes(7, stored.gzip)
            statement.executeUpdate()
        }
    }

    /** Counts per mandate of the import served for [source], or null when none was ever served. */
    fun servedCounts(lock: ImportLock, source: String): Map<Mandate, Int>? =
        lock.connection.prepareStatement("SELECT counts::text FROM elus_import WHERE status = 'ready' AND source = ?").use { statement ->
            statement.setString(1, source)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                val text = rows.getString(1) ?: return emptyMap()
                val json = ElusJson.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
                Mandate.entries.mapNotNull { mandate -> json[mandate.code]?.jsonPrimitive?.intOrNull?.let { mandate to it } }.toMap()
            }
        }

    /**
     * Serves import [importId] from now on, in one transaction: its index is written, it becomes
     * `ready`, and every other import is deleted (with its packages).
     */
    fun activate(lock: ImportLock, importId: Long, index: ByteArray, sha256: String, counts: Map<Mandate, Int>, updatedOn: LocalDate, now: Instant) {
        val c = lock.connection
        c.autoCommit = false
        try {
            c.prepareStatement("DELETE FROM elus_import WHERE id <> ?").use { it.setLong(1, importId); it.executeUpdate() }
            c.prepareStatement(
                "UPDATE elus_import SET status = 'ready', finished_at = ?, source_updated_on = ?, counts = ?::jsonb, index_body = ?, index_sha256 = ? WHERE id = ?",
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(now))
                statement.setDate(2, Date.valueOf(updatedOn))
                statement.setString(3, countsJson(counts))
                statement.setBytes(4, index)
                statement.setString(5, sha256)
                statement.setLong(6, importId)
                statement.executeUpdate()
            }
            c.commit()
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    /** Records that import [importId] failed for [reason]; its packages go, the served import stays. */
    fun fail(lock: ImportLock, importId: Long, reason: String, counts: Map<Mandate, Int>?, now: Instant) {
        val c = lock.connection
        c.prepareStatement("DELETE FROM elus_package WHERE import_id = ?").use { it.setLong(1, importId); it.executeUpdate() }
        c.prepareStatement("DELETE FROM elus_import WHERE status = 'failed' AND id <> ?").use { it.setLong(1, importId); it.executeUpdate() }
        c.prepareStatement("UPDATE elus_import SET status = 'failed', finished_at = ?, failure = ?, counts = ?::jsonb WHERE id = ?").use { statement ->
            statement.setTimestamp(1, Timestamp.from(now))
            statement.setString(2, reason.take(200))
            statement.setString(3, counts?.let(::countsJson))
            statement.setLong(4, importId)
            statement.executeUpdate()
        }
    }

    /** The import served now, or null. */
    fun served(): ServedImport? = dataSource.connection.use { c ->
        val head = c.prepareStatement("SELECT id, source, finished_at, index_body, index_sha256 FROM elus_import WHERE status = 'ready'").use { statement ->
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                ServedImport(rows.getLong(1), rows.getString(2), rows.getTimestamp(3).toInstant(), rows.getBytes(4), rows.getString(5), emptyMap())
            }
        }
        val packages = c.prepareStatement("SELECT code, sha256 FROM elus_package WHERE import_id = ?").use { statement ->
            statement.setLong(1, head.id)
            statement.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) } }
        }
        head.copy(packages = packages)
    }

    /** The compressed body of package [code] of import [importId], or null. */
    fun packageGzip(importId: Long, code: String): ByteArray? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT body_gzip FROM elus_package WHERE import_id = ? AND code = ?").use { statement ->
            statement.setLong(1, importId)
            statement.setString(2, code)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getBytes(1) else null }
        }
    }

    /** The last attempt (served, failed or interrupted), for the schedule of the next one. */
    fun lastAttempt(): LastAttempt? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT source, status, COALESCE(finished_at, started_at) FROM elus_import ORDER BY id DESC LIMIT 1").use { statement ->
            statement.executeQuery().use { rows -> if (rows.next()) LastAttempt(rows.getString(1), rows.getString(2), rows.getTimestamp(3).toInstant()) else null }
        }
    }

    /** The reason of the last failed attempt, if any (a code written by the server). */
    fun lastFailure(): String? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT failure FROM elus_import WHERE status = 'failed' ORDER BY id DESC LIMIT 1").use { statement ->
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    private fun countsJson(counts: Map<Mandate, Int>): String =
        JsonObject(Mandate.entries.filter { it in counts }.associate { it.code to JsonPrimitive(counts.getValue(it)) }).toString()

    private fun BufferedWriter.csv(value: String): BufferedWriter {
        write('"'.code)
        for (ch in value) {
            if (ch == '"') write('"'.code)
            write(ch.code)
        }
        write('"'.code)
        return this
    }

    /** An unquoted empty field is NULL for COPY in CSV format; a quoted one is an empty string. */
    private fun BufferedWriter.csvOrNull(value: String?): BufferedWriter = if (value == null) this else csv(value)

    private fun BufferedWriter.write(ch: Char): BufferedWriter {
        write(ch.code)
        return this
    }

    companion object {
        /** Key of the advisory lock of the imports (any constant unique to this server). */
        const val LOCK_KEY: Long = 0x5050_4645_4C55_53L
    }
}
