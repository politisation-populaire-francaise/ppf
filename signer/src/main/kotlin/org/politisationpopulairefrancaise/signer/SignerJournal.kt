package org.politisationpopulairefrancaise.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * One line of the journal of the signing service: what was done, for which use, on which event (its id,
 * the SHA-256 of its canonical form, and its date), with which key, why it was refused, and which team
 * accounts approved it. Never the content of an event, never a caller address.
 */
@Serializable
data class JournalEntry(
    val seq: Long,
    val at: Long,
    val action: String,
    val use: String? = null,
    val event: String? = null,
    @SerialName("event_created_at") val eventCreatedAt: Long? = null,
    val key: String? = null,
    val code: String? = null,
    val approvers: List<String> = emptyList(),
    @SerialName("previous_hash") val previousHash: String,
    val hash: String,
)

/** The head of the journal: the last line and its hash, to anchor elsewhere (timestamp, back-office journal). */
@Serializable
data class JournalHead(val seq: Long, val hash: String)

/**
 * The append-only journal of the service (D-129), hash-chained like the audit log of the back-office
 * (D-124): each line holds the hash of the previous one and its own, the SHA-256 of a canonical JSON array
 * of all its fields and of the previous hash. One JSON line per entry in `<data>/journal.jsonl`, flushed
 * to disk before the action is answered. The whole chain is checked when the service starts: a line
 * removed, changed or reordered stops it.
 */
class SignerJournal private constructor(private val file: Path, private var head: JournalHead) {
    private val channel: FileChannel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)

    fun head(): JournalHead = head

    /** Appends one line and flushes it to disk; returns it. */
    fun append(
        at: Long,
        action: String,
        use: String? = null,
        event: String? = null,
        eventCreatedAt: Long? = null,
        key: String? = null,
        code: String? = null,
        approvers: List<String> = emptyList(),
    ): JournalEntry {
        val seq = head.seq + 1
        val hash = hashOf(seq, at, action, use, event, eventCreatedAt, key, code, approvers, head.hash)
        val entry = JournalEntry(seq, at, action, use, event, eventCreatedAt, key, code, approvers, head.hash, hash)
        val line = (JSON.encodeToString(JournalEntry.serializer(), entry) + "\n").toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.wrap(line)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(false)
        head = JournalHead(seq, hash)
        return entry
    }

    /** The lines after [after], at most [limit], in order. */
    fun read(after: Long, limit: Int): List<JournalEntry> {
        val result = mutableListOf<JournalEntry>()
        Files.newBufferedReader(file, Charsets.UTF_8).use { reader ->
            while (result.size < limit) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val entry = JSON.decodeFromString(JournalEntry.serializer(), line)
                if (entry.seq > after) result += entry
            }
        }
        return result
    }

    fun close() = channel.close()

    companion object {
        val GENESIS: String = "0".repeat(64)

        private val JSON = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }

        /**
         * Opens the journal of [dataDir], checking its whole chain and passing every line to [replay] (so that
         * the service rebuilds what it needs: events already signed, signatures of the last hour).
         */
        fun open(dataDir: Path, replay: (JournalEntry) -> Unit = {}): SignerJournal {
            Files.createDirectories(dataDir)
            val file = dataDir.resolve("journal.jsonl")
            var head = JournalHead(0, GENESIS)
            if (Files.exists(file)) {
                Files.newBufferedReader(file, Charsets.UTF_8).use { reader ->
                    var number = 0L
                    while (true) {
                        val line = reader.readLine() ?: break
                        number++
                        val entry = try {
                            JSON.decodeFromString(JournalEntry.serializer(), line)
                        } catch (e: Exception) {
                            throw SignerStartupException("Journal line $number is unreadable: the chain is broken")
                        }
                        if (entry.seq != head.seq + 1 || entry.previousHash != head.hash || entry.hash != hashOf(entry)) {
                            throw SignerStartupException("Journal line $number breaks the chain")
                        }
                        head = JournalHead(entry.seq, entry.hash)
                        replay(entry)
                    }
                }
            }
            val journal = SignerJournal(file, head)
            DurableFiles.restrictToOwner(file)
            return journal
        }

        fun hashOf(entry: JournalEntry): String = hashOf(
            entry.seq, entry.at, entry.action, entry.use, entry.event, entry.eventCreatedAt, entry.key, entry.code, entry.approvers, entry.previousHash,
        )

        fun hashOf(
            seq: Long,
            at: Long,
            action: String,
            use: String?,
            event: String?,
            eventCreatedAt: Long?,
            key: String?,
            code: String?,
            approvers: List<String>,
            previousHash: String,
        ): String {
            val canonical = JsonArray(
                listOf(
                    JsonPrimitive(seq),
                    JsonPrimitive(at),
                    JsonPrimitive(action),
                    use?.let(::JsonPrimitive) ?: JsonNull,
                    event?.let(::JsonPrimitive) ?: JsonNull,
                    eventCreatedAt?.let(::JsonPrimitive) ?: JsonNull,
                    key?.let(::JsonPrimitive) ?: JsonNull,
                    code?.let(::JsonPrimitive) ?: JsonNull,
                    JsonArray(approvers.map(::JsonPrimitive)),
                    JsonPrimitive(previousHash),
                ),
            ).toString()
            return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).toHexString()
        }
    }
}

/** The stable actions of the journal. */
object JournalActions {
    const val STARTED: String = "service.started"
    const val SIGNED: String = "sign.signed"
    const val REFUSED: String = "sign.refused"
    const val REFUSALS_DROPPED: String = "sign.refusals_dropped"
    const val KEY_CREATED: String = "key.created"
    const val KEY_RETIRED: String = "key.retired"
    const val CERTIFICATE_STORED: String = "certificate.stored"
    const val TEAM_LIST_STORED: String = "team_list.stored"
}
