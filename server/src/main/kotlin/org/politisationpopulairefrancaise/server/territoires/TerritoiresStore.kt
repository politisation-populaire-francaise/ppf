package org.politisationpopulairefrancaise.server.territoires

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.politisationpopulairefrancaise.server.elus.gzip
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** The import being served: the national level and the ETag of each package. */
data class ServedTerritoires(
    val id: Long,
    val source: String,
    val finishedAt: Instant,
    val national: ByteArray,
    val nationalSha256: String,
    /** Package code to its SHA-256 (the ETag). */
    val packages: Map<String, String>,
) {
    val nationalGzip: ByteArray by lazy { gzip(national) }
}

/** Where the last import attempt stands, for the schedule of the next one. */
data class TerritoiresAttempt(val source: String, val status: String, val at: Instant)

/** The tables of V11__territoires.sql. One import at a time across processes (an advisory lock). */
class TerritoiresStore(private val dataSource: DataSource) {

    /** Stores a successful import and serves it, in one transaction; false when another import holds the lock. */
    fun save(source: String, startedAt: Instant, finishedAt: Instant, built: BuiltTerritoires): Boolean = dataSource.connection.use { c ->
        c.autoCommit = false
        try {
            val locked = c.prepareStatement("SELECT pg_try_advisory_xact_lock(?)").use { s ->
                s.setLong(1, LOCK_KEY)
                s.executeQuery().use { it.next() && it.getBoolean(1) }
            }
            if (!locked) {
                c.rollback()
                return false
            }
            val id = c.prepareStatement(
                "INSERT INTO territoires_import (source, status, started_at, finished_at, counts, national_body, national_sha256) " +
                    "VALUES (?, 'building', ?, ?, ?::jsonb, ?, ?) RETURNING id",
            ).use { s ->
                s.setString(1, source)
                s.setTimestamp(2, Timestamp.from(startedAt))
                s.setTimestamp(3, Timestamp.from(finishedAt))
                s.setString(4, countsJson(built.counts))
                s.setBytes(5, built.national)
                s.setString(6, built.nationalSha256)
                s.executeQuery().use { it.next(); it.getLong(1) }
            }
            c.prepareStatement("INSERT INTO territoires_package (import_id, code, name, sha256, bytes, body_gzip) VALUES (?, ?, ?, ?, ?, ?)").use { s ->
                for (document in built.packages) {
                    s.setLong(1, id)
                    s.setString(2, document.code)
                    s.setString(3, document.name)
                    s.setString(4, document.sha256)
                    s.setInt(5, document.body.size)
                    s.setBytes(6, gzip(document.body))
                    s.addBatch()
                }
                s.executeBatch()
            }
            // The previous import stops being served; only the new one and the last failure remain.
            c.createStatement().use { it.executeUpdate("DELETE FROM territoires_import WHERE status = 'ready' OR (status = 'failed' AND id < (SELECT max(id) FROM territoires_import WHERE status = 'failed'))") }
            c.prepareStatement("UPDATE territoires_import SET status = 'ready' WHERE id = ?").use { it.setLong(1, id); it.executeUpdate() }
            c.commit()
            true
        } catch (e: Exception) {
            c.rollback()
            throw e
        }
    }

    /** Records a failed attempt with a short [reason] (a code, never a value read from a file). */
    fun fail(source: String, startedAt: Instant, finishedAt: Instant, reason: String) {
        dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM territoires_import WHERE status = 'failed'").use { it.executeUpdate() }
            c.prepareStatement("INSERT INTO territoires_import (source, status, started_at, finished_at, failure) VALUES (?, 'failed', ?, ?, ?)").use { s ->
                s.setString(1, source)
                s.setTimestamp(2, Timestamp.from(startedAt))
                s.setTimestamp(3, Timestamp.from(finishedAt))
                s.setString(4, reason.take(200))
                s.executeUpdate()
            }
        }
    }

    /** The import served, or null. */
    fun served(): ServedTerritoires? = dataSource.connection.use { c ->
        val head = c.prepareStatement("SELECT id, source, finished_at, national_body, national_sha256 FROM territoires_import WHERE status = 'ready'").use { s ->
            s.executeQuery().use { r ->
                if (!r.next()) return null
                listOf(r.getLong(1), r.getString(2), r.getTimestamp(3).toInstant(), r.getBytes(4), r.getString(5))
            }
        }
        val id = head[0] as Long
        val packages = c.prepareStatement("SELECT code, sha256 FROM territoires_package WHERE import_id = ? ORDER BY code").use { s ->
            s.setLong(1, id)
            s.executeQuery().use { r ->
                val map = LinkedHashMap<String, String>()
                while (r.next()) map[r.getString(1)] = r.getString(2)
                map
            }
        }
        ServedTerritoires(id, head[1] as String, head[2] as Instant, head[3] as ByteArray, head[4] as String, packages)
    }

    /** The counts of the import served, for the guard. */
    fun servedCounts(): Map<String, Long> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT counts::text FROM territoires_import WHERE status = 'ready'").use { s ->
            s.executeQuery().use { r ->
                if (!r.next()) return emptyMap()
                val text = r.getString(1) ?: return emptyMap()
                val json = TerritoiresJson.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
                json.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.longOrNull?.let { key to it } }.toMap()
            }
        }
    }

    /** The compressed body of package [code] of import [importId], or null. */
    fun packageGzip(importId: Long, code: String): ByteArray? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT body_gzip FROM territoires_package WHERE import_id = ? AND code = ?").use { s ->
            s.setLong(1, importId)
            s.setString(2, code)
            s.executeQuery().use { r -> if (r.next()) r.getBytes(1) else null }
        }
    }

    fun lastAttempt(): TerritoiresAttempt? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT source, status, coalesce(finished_at, started_at) FROM territoires_import ORDER BY id DESC LIMIT 1").use { s ->
            s.executeQuery().use { r -> if (r.next()) TerritoiresAttempt(r.getString(1), r.getString(2), r.getTimestamp(3).toInstant()) else null }
        }
    }

    private fun countsJson(counts: Map<String, Long>): String =
        counts.toSortedMap().entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":$value" }

    private companion object {
        /** Distinct from the lock of the directory of elected officials. */
        const val LOCK_KEY: Long = 0x7065_7274_6572_7269L
    }
}
