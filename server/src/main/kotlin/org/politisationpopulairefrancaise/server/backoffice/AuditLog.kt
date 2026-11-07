package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** Who did what is recorded: a member, the server itself, or an unknown person (failed sign-in). */
sealed interface AuditActor {
    val id: UUID?
    val label: String

    data class Member(override val id: UUID, override val label: String) : AuditActor

    data object System : AuditActor {
        override val id: UUID? = null
        override val label: String = BackOfficeTexts.ACTOR_SYSTEM
    }

    data object Unknown : AuditActor {
        override val id: UUID? = null
        override val label: String = BackOfficeTexts.ACTOR_UNKNOWN
    }

    companion object {
        fun of(actor: TeamActor): AuditActor = Member(actor.accountId, actor.pseudonym)
    }
}

/**
 * One action to record (contract, section 6). [details] holds only what the decision needs: identifiers,
 * outcomes, roles, dates. Never the full text of a citizen's content, never a client address.
 */
data class AuditEntry(
    val actor: AuditActor,
    val action: String,
    val targetType: String? = null,
    val targetId: String? = null,
    val reason: String? = null,
    val details: Map<String, String> = emptyMap(),
) {
    init {
        require(Permissions.isValidId(action)) { "An audit action is a stable dotted identifier" }
        require(reason == null || reason.length <= MAX_REASON_LENGTH) { "A reason has at most $MAX_REASON_LENGTH characters" }
        require(details.size <= 32 && details.all { (key, value) -> key.length <= 64 && value.length <= 2_000 }) { "Details are short" }
    }

    companion object {
        const val MAX_REASON_LENGTH: Int = 1_000
    }
}

/** A line of the journal as stored, with its place in the hash chain. */
@Serializable
data class AuditRecord(
    val seq: Long,
    @SerialName("recorded_at") val recordedAt: Long,
    @SerialName("actor_id") val actorId: String?,
    @SerialName("actor") val actorLabel: String,
    val action: String,
    @SerialName("target_type") val targetType: String?,
    @SerialName("target_id") val targetId: String?,
    val reason: String?,
    val details: Map<String, String>,
    @SerialName("previous_hash") val previousHash: String,
    val hash: String,
)

/**
 * The hash chain of the journal: each line stores the hash of the previous one and its own, the SHA-256
 * of a canonical JSON array of all its fields and of the previous hash. Removing, changing or reordering
 * a line by a direct access to the database breaks the chain from that line on.
 */
object AuditChain {
    /** The previous hash of the first line. */
    val GENESIS: String = "0".repeat(64)

    fun hashOf(
        seq: Long,
        recordedAt: Long,
        actorId: String?,
        actorLabel: String,
        action: String,
        targetType: String?,
        targetId: String?,
        reason: String?,
        details: String,
        previousHash: String,
    ): String {
        val canonical = JsonArray(
            listOf(
                JsonPrimitive(seq),
                JsonPrimitive(recordedAt),
                actorId?.let(::JsonPrimitive) ?: JsonNull,
                JsonPrimitive(actorLabel),
                JsonPrimitive(action),
                targetType?.let(::JsonPrimitive) ?: JsonNull,
                targetId?.let(::JsonPrimitive) ?: JsonNull,
                reason?.let(::JsonPrimitive) ?: JsonNull,
                JsonPrimitive(details),
                JsonPrimitive(previousHash),
            ),
        ).toString()
        return Tokens.sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    fun hashOf(record: AuditRecord): String = hashOf(
        record.seq, record.recordedAt, record.actorId, record.actorLabel, record.action, record.targetType,
        record.targetId, record.reason, encodeDetails(record.details), record.previousHash,
    )

    /** The details as stored and hashed: a JSON object of strings with sorted keys. */
    fun encodeDetails(details: Map<String, String>): String =
        JsonObject(details.toSortedMap().mapValues { JsonPrimitive(it.value) }).toString()

    fun decodeDetails(text: String): Map<String, String> =
        Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.content }

    /** Where the chain of [records] (in order, from the first line) breaks, if it does. */
    sealed interface Check {
        data class Intact(val lines: Long, val head: String) : Check

        /** The first line [seq] whose number, previous hash or own hash is wrong. */
        data class Broken(val seq: Long, val problem: String) : Check

        /** The last line is [found], lower than [expected], a line this process saw: the end of the journal was removed. */
        data class Truncated(val expected: Long, val found: Long) : Check
    }

    /**
     * Checks [records], which follow the line [afterSeq] whose hash is [previousHash] (the first line by
     * default): numbers in a row, each previous hash the hash of the line before, each hash right.
     */
    fun verify(records: Sequence<AuditRecord>, afterSeq: Long = 0, previousHash: String = GENESIS): Check {
        var expectedSeq = afterSeq + 1
        var previous = previousHash
        for (record in records) {
            if (record.seq != expectedSeq) return Check.Broken(expectedSeq, "missing_line")
            if (record.previousHash != previous) return Check.Broken(record.seq, "previous_hash")
            if (hashOf(record) != record.hash) return Check.Broken(record.seq, "hash")
            previous = record.hash
            expectedSeq++
        }
        return Check.Intact(expectedSeq - 1, previous)
    }
}

/** Filters of the journal page (contract, section 6): period, account, action, object. */
data class AuditFilter(
    val from: Instant? = null,
    val until: Instant? = null,
    val actorId: UUID? = null,
    val action: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    /** Lines strictly before this number (newest first, page by page). */
    val beforeSeq: Long? = null,
)

/** The last line of the journal: its number and hash are what an external anchor keeps (D-111). */
@Serializable
data class AuditHead(val seq: Long, val hash: String, @SerialName("recorded_at") val recordedAt: Long)

/**
 * The journal of the team (table team_audit_log of V8__back_office.sql): append only, a PostgreSQL
 * trigger refuses any change or deletion, the restricted role of the server may only read and append
 * (`afterMigrate.sql`, D-127), and the [AuditChain] reveals a line changed behind its back.
 *
 * Written only by the back-office base (contract, section 7: a pole never writes it); [record] is its
 * single entry point. Lines are numbered and chained under a lock, inside the transaction of the action
 * when the base performs it itself.
 */
class AuditLog(private val dataSource: DataSource, private val clock: Clock = Clock.systemUTC()) {
    /** The last line verified by this process: later checks start from it instead of the first line. */
    @Volatile
    private var checkpoint: AuditHead? = null

    /** The highest line number this process wrote or verified: the journal can never end below it. */
    private val highestSeen = java.util.concurrent.atomic.AtomicLong(0)

    /** Records [entry] in its own transaction. */
    fun record(entry: AuditEntry): AuditRecord = dataSource.transaction { record(it, entry) }

    /** Records [entry] inside the open transaction of [connection] (the action and its line commit together). */
    fun record(connection: Connection, entry: AuditEntry): AuditRecord {
        check(!connection.autoCommit) { "The journal is written inside a transaction" }
        connection.advisoryLock(LOCK)
        val (lastSeq, lastHash) = connection.prepareStatement("SELECT seq, hash FROM team_audit_log ORDER BY seq DESC LIMIT 1").use { statement ->
            val rows = statement.executeQuery()
            if (rows.next()) rows.getLong(1) to rows.getString(2) else 0L to AuditChain.GENESIS
        }
        val seq = lastSeq + 1
        val recordedAt = clock.nowSeconds()
        val details = AuditChain.encodeDetails(entry.details)
        val actorId = entry.actor.id?.toString()
        val hash = AuditChain.hashOf(
            seq, recordedAt.epochSecond, actorId, entry.actor.label, entry.action, entry.targetType, entry.targetId,
            entry.reason, details, lastHash,
        )
        connection.prepareStatement(
            """
            INSERT INTO team_audit_log (seq, recorded_at, actor_id, actor_label, action, target_type, target_id, reason, details, previous_hash, hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { insert ->
            insert.setLong(1, seq)
            insert.setInstant(2, recordedAt)
            insert.setUuid(3, entry.actor.id)
            insert.setString(4, entry.actor.label)
            insert.setString(5, entry.action)
            insert.setText(6, entry.targetType)
            insert.setText(7, entry.targetId)
            insert.setText(8, entry.reason)
            insert.setString(9, details)
            insert.setString(10, lastHash)
            insert.setString(11, hash)
            insert.executeUpdate()
        }
        highestSeen.accumulateAndGet(seq, ::maxOf)
        return AuditRecord(seq, recordedAt.epochSecond, actorId, entry.actor.label, entry.action, entry.targetType, entry.targetId, entry.reason, entry.details, lastHash, hash)
    }

    /** The lines matching [filter], newest first, at most [limit]. */
    fun query(filter: AuditFilter = AuditFilter(), limit: Int = 100): List<AuditRecord> = dataSource.connection.use { connection ->
        val conditions = mutableListOf<String>()
        val binders = mutableListOf<(java.sql.PreparedStatement, Int) -> Unit>()
        filter.from?.let { value -> conditions += "recorded_at >= ?"; binders += { s, i -> s.setInstant(i, value) } }
        filter.until?.let { value -> conditions += "recorded_at < ?"; binders += { s, i -> s.setInstant(i, value) } }
        filter.actorId?.let { value -> conditions += "actor_id = ?"; binders += { s, i -> s.setUuid(i, value) } }
        filter.action?.let { value -> conditions += "(action = ? OR action LIKE ?)"; binders += { s, i -> s.setString(i, value) }; binders += { s, i -> s.setString(i, escapeLike(value) + ".%") } }
        filter.targetType?.let { value -> conditions += "target_type = ?"; binders += { s, i -> s.setString(i, value) } }
        filter.targetId?.let { value -> conditions += "target_id = ?"; binders += { s, i -> s.setString(i, value) } }
        filter.beforeSeq?.let { value -> conditions += "seq < ?"; binders += { s, i -> s.setLong(i, value) } }
        val where = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        connection.prepareStatement("$SELECT $where ORDER BY seq DESC LIMIT ?").use { statement ->
            binders.forEachIndexed { index, bind -> bind(statement, index + 1) }
            statement.setInt(binders.size + 1, limit.coerceIn(1, MAX_PAGE))
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toRecord()) }
        }
    }

    /**
     * Checks the chain from the last verified line (the whole chain the first time): that line must still
     * be there with the same hash, the journal must not end below a line this process saw, and every new
     * line must follow. Removing the end of the journal before this process saw it is only revealed by an
     * external anchor of the head ([head], D-111).
     */
    fun verifyChain(): AuditChain.Check = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            connection.transactionIsolation = java.sql.Connection.TRANSACTION_REPEATABLE_READ
            val last = connection.prepareStatement("SELECT max(seq) FROM team_audit_log").use { statement ->
                val rows = statement.executeQuery()
                rows.next()
                rows.getLong(1)
            }
            val known = maxOf(highestSeen.get(), checkpoint?.seq ?: 0)
            if (last < known) return@use AuditChain.Check.Truncated(known, last)
            val from = checkpoint
            if (from != null) {
                val stored = connection.prepareStatement("$SELECT WHERE seq = ?").use { statement ->
                    statement.setLong(1, from.seq)
                    val rows = statement.executeQuery()
                    if (rows.next()) rows.toRecord() else null
                } ?: return@use AuditChain.Check.Broken(from.seq, "missing_line")
                if (stored.hash != from.hash || AuditChain.hashOf(stored) != stored.hash) return@use AuditChain.Check.Broken(from.seq, "hash")
            }
            val check = connection.prepareStatement("$SELECT WHERE seq > ? ORDER BY seq ASC").use { statement ->
                statement.fetchSize = 500
                statement.setLong(1, from?.seq ?: 0)
                val rows = statement.executeQuery()
                var lastRecord: AuditRecord? = null
                val result = AuditChain.verify(
                    generateSequence { if (rows.next()) rows.toRecord().also { lastRecord = it } else null },
                    afterSeq = from?.seq ?: 0,
                    previousHash = from?.hash ?: AuditChain.GENESIS,
                )
                if (result is AuditChain.Check.Intact) {
                    lastRecord?.let { checkpoint = AuditHead(it.seq, it.hash, it.recordedAt) }
                    highestSeen.accumulateAndGet(result.lines, ::maxOf)
                }
                result
            }
            check
        } finally {
            connection.rollback()
            connection.transactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
            connection.autoCommit = true
        }
    }

    /** The last line: its number and hash are what to note or timestamp outside (archive of Décider, D-111). */
    fun head(): AuditHead? = query(limit = 1).firstOrNull()?.let { AuditHead(it.seq, it.hash, it.recordedAt) }

    private fun ResultSet.toRecord(): AuditRecord = AuditRecord(
        seq = getLong("seq"),
        recordedAt = instant("recorded_at")!!.epochSecond,
        actorId = uuid("actor_id")?.toString(),
        actorLabel = getString("actor_label"),
        action = getString("action"),
        targetType = getString("target_type"),
        targetId = getString("target_id"),
        reason = getString("reason"),
        details = AuditChain.decodeDetails(getString("details")),
        previousHash = getString("previous_hash"),
        hash = getString("hash"),
    )

    private fun escapeLike(value: String): String = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val MAX_PAGE: Int = 1_000
        private const val LOCK = "team_audit_log"
        private const val SELECT = "SELECT seq, recorded_at, actor_id, actor_label, action, target_type, target_id, reason, details, previous_hash, hash FROM team_audit_log"
    }
}
