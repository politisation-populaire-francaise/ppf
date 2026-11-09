package org.politisationpopulairefrancaise.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.politisationpopulairefrancaise.core.platform.RootRequests
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * One line of the journal of the signing service: what was done, for which use, on which event (its id
 * and its date), with which key, why it was refused, which team accounts approved it (and, for one
 * approval, with which passkey), whether the draft asked for the double validation and who wrote it, and
 * for a closing statement the `d` of its ballot box. Never the content of an event, never a caller address.
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
    @SerialName("double_validation") val doubleValidation: Boolean? = null,
    @SerialName("author_account") val authorAccount: String? = null,
    @SerialName("author_pubkey") val authorPubkey: String? = null,
    /** The passkey of a recorded approval (its credential id, base64url). */
    val credential: String? = null,
    /** The `d` of a closing statement of a ballot box (a second one for the same `d` is never signed). */
    val d: String? = null,
    @SerialName("previous_hash") val previousHash: String,
    val hash: String,
)

/** The head of the journal: the last line and its hash, to anchor elsewhere (timestamp, back-office journal). */
@Serializable
data class JournalHead(val seq: Long, val hash: String)

/** The fields of a line besides its number, date and chaining. */
data class JournalFields(
    val use: String? = null,
    val event: String? = null,
    val eventCreatedAt: Long? = null,
    val key: String? = null,
    val code: String? = null,
    val approvers: List<String> = emptyList(),
    val doubleValidation: Boolean? = null,
    val authorAccount: String? = null,
    val authorPubkey: String? = null,
    val credential: String? = null,
    val d: String? = null,
)

/**
 * The append-only journal of the service (D-129), hash-chained like the audit log of the back-office
 * (D-124): each line holds the hash of the previous one and its own, the SHA-256 of a canonical JSON array
 * of all its fields and of the previous hash. One JSON line per entry in `<data>/journal.jsonl` (private
 * file), flushed to disk before the action is answered.
 *
 * The whole chain is checked when the service starts: a line removed, changed or reordered stops it. One
 * case only is repaired: a last line torn by a crash during its write (no final line end, unreadable) is
 * cut, and a line [JournalActions.TORN_LINE_CUT] records how many bytes were cut. A last line complete but
 * for its line end gets its line end back. Anything else broken stops the service (contract, section 5).
 */
class SignerJournal private constructor(private val file: Path, private var head: JournalHead) {
    private val channel: FileChannel = DurableFiles.openPrivate(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)

    fun head(): JournalHead = head

    /** Appends one line and flushes it to disk; returns it. */
    fun append(at: Long, action: String, fields: JournalFields = JournalFields()): JournalEntry {
        val seq = head.seq + 1
        val unhashed = entry(seq, at, action, fields, head.hash, hash = "")
        val entry = unhashed.copy(hash = hashOf(unhashed))
        val line = (JSON.encodeToString(JournalEntry.serializer(), entry) + "\n").toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.wrap(line)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(false)
        head = JournalHead(seq, entry.hash)
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
         * the service rebuilds what it needs: events already signed, drafts and their approvals, closed
         * ballot boxes, signatures of the last hour). [now] dates the line written when a torn last line is cut.
         */
        fun open(dataDir: Path, now: Long, replay: (JournalEntry) -> Unit = {}): SignerJournal {
            DurableFiles.privateDirectory(dataDir)
            val file = dataDir.resolve("journal.jsonl")
            var head = JournalHead(0, GENESIS)
            var cut: Long? = null
            var missingLineEnd = false
            if (Files.exists(file)) {
                var number = 0L
                var offset = 0L
                fun accept(entry: JournalEntry) {
                    if (entry.seq != head.seq + 1 || entry.previousHash != head.hash || entry.hash != hashOf(entry)) {
                        throw SignerStartupException("Journal line $number breaks the chain")
                    }
                    head = JournalHead(entry.seq, entry.hash)
                    replay(entry)
                }
                Files.newInputStream(file).buffered().use { input ->
                    val line = ByteArrayOutputStream()
                    while (true) {
                        val byte = input.read()
                        if (byte == -1) break
                        if (byte != '\n'.code) {
                            line.write(byte)
                            continue
                        }
                        number++
                        val entry = decode(line.toByteArray()) ?: throw SignerStartupException("Journal line $number is unreadable: the chain is broken")
                        accept(entry)
                        offset += line.size() + 1
                        line.reset()
                    }
                    if (line.size() > 0) {
                        number++
                        val entry = decode(line.toByteArray())
                        if (entry == null) {
                            cut = line.size().toLong()
                        } else {
                            accept(entry)
                            missingLineEnd = true
                        }
                    }
                }
                if (cut != null) {
                    FileChannel.open(file, StandardOpenOption.WRITE).use { channel ->
                        channel.truncate(offset)
                        channel.force(true)
                    }
                }
            }
            val journal = SignerJournal(file, head)
            if (missingLineEnd) {
                val buffer = ByteBuffer.wrap("\n".toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) journal.channel.write(buffer)
                journal.channel.force(false)
            }
            cut?.let { journal.append(now, JournalActions.TORN_LINE_CUT, JournalFields(code = it.toString())) }
            return journal
        }

        private fun decode(bytes: ByteArray): JournalEntry? = try {
            JSON.decodeFromString(JournalEntry.serializer(), String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }

        private fun entry(seq: Long, at: Long, action: String, f: JournalFields, previousHash: String, hash: String) = JournalEntry(
            seq, at, action, f.use, f.event, f.eventCreatedAt, f.key, f.code, f.approvers, f.doubleValidation,
            f.authorAccount, f.authorPubkey, f.credential, f.d, previousHash, hash,
        )

        /**
         * SHA-256 of the canonical JSON array of every field of [entry] but its own hash, the previous hash
         * last. The fields added in version 1.2 of the contract (double validation, author, passkey, `d`)
         * enter the array when one of them is set; a line without any of them keeps the array of version
         * 1.1 (eleven elements), so that a journal begun before stays valid. The two lengths never collide.
         */
        fun hashOf(entry: JournalEntry): String {
            fun text(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull
            val added = listOf(
                entry.doubleValidation?.let(::JsonPrimitive) ?: JsonNull,
                text(entry.authorAccount),
                text(entry.authorPubkey),
                text(entry.credential),
                text(entry.d),
            )
            val canonical = JsonArray(
                listOf(
                    JsonPrimitive(entry.seq),
                    JsonPrimitive(entry.at),
                    JsonPrimitive(entry.action),
                    text(entry.use),
                    text(entry.event),
                    entry.eventCreatedAt?.let(::JsonPrimitive) ?: JsonNull,
                    text(entry.key),
                    text(entry.code),
                    JsonArray(entry.approvers.map(::JsonPrimitive)),
                ) + (if (added.all { it == JsonNull }) emptyList() else added) + JsonPrimitive(entry.previousHash),
            ).toString()
            return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).toHexString()
        }
    }
}

/** The stable actions of the journal. */
object JournalActions {
    const val STARTED: String = "service.started"
    const val TORN_LINE_CUT: String = "journal.torn_line_cut"
    const val DRAFT_SUBMITTED: String = "draft.submitted"
    const val DRAFT_REFUSED: String = "draft.refused"
    const val APPROVAL_RECORDED: String = "approval.recorded"
    const val APPROVAL_REFUSED: String = "approval.refused"
    const val SIGNED: String = "sign.signed"
    const val REFUSED: String = "sign.refused"
    const val REFUSALS_DROPPED: String = "sign.refusals_dropped"
    const val KEY_CREATED: String = "key.created"
    const val KEY_RETIRED: String = "key.retired"
    const val CERTIFICATE_STORED: String = "certificate.stored"
    const val TEAM_LIST_STORED: String = "team_list.stored"

    /**
     * A root request kept for the owner's phone (D-141): `event` holds its hash ([RootRequests.sha256]), `d`
     * its id and `code` the number of its events; never the events themselves.
     */
    const val ROOT_REQUEST_CREATED: String = "root_request.created"

    /** A root request refused (the code says why). */
    const val ROOT_REQUEST_REFUSED: String = "root_request.refused"
}
