package org.politisationpopulairefrancaise.server.legal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.sql.Connection
import java.sql.SQLException
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

/**
 * The legal register of level 1, the "registre pseudonyme" (decision D-077; legal note
 * docs/integration/lcen-conservation-identification.md, sections 1, 4, 6.8 and 7.1): what a host must keep
 * about the contributions to PUBLIC contents (LCEN, article 6, V, A; décret n° 2021-1362, articles 3, 5, 6
 * and 7; injunction of décret n° 2026-920, in force since 21 October 2026 for one year), and nothing more,
 * following the owner's rule of 7 November 2026: « Conserver le minimum dans le cadre de la loi ».
 *
 * **What is recorded, and only that.** For each creation, replacement (a newer version of a replaceable or
 * addressable event) and deletion (NIP-09 request, kind 5) of a public content: the event id, the nature of
 * the operation, the reception date (set by the database, UTC, to the second), the event's own `created_at`,
 * its kind, the author's public key and a one-byte protocol type ([PROTOCOL_HTTPS_API]); for a replacement
 * or a deletion, the id, date and key of the removed version, never its content. Per account: the key and
 * the successive pseudonyms (`name` and `display_name` of the profile, kind 0, nothing else of it) with the
 * date of each change. The rows are written by [EventRepository] in the same transaction as the event, so
 * that no publication escapes the register and no row exists for an event that was not stored.
 *
 * **Public contents** are the events that `GET /api/v1/events` serves to anyone and that a person writes
 * for the public, of the kinds of [PUBLIC_KINDS]:
 * - 0, profile (pseudonym, presentation, picture), which also feeds the pseudonym history;
 * - 1, short message, reply or comment of the feed;
 * - 5, deletion request (NIP-09), recorded once per deleted public content of the same author;
 * - 7, reaction (NIP-25), public and nominative;
 * - 9, message of a room: every room of the server is open (private and hidden rooms are refused);
 * - 1111, comment (NIP-22);
 * - 30023, long content of a key outside the editorial team: calls of Agir, citizen demands once approved,
 *   answers of candidates;
 * - 31923, mobilization of the agenda; 31925, public answer to a mobilization;
 * - 39000, description of a room (creating a room is open to every account).
 *
 * Content held for review (Agir, citizen demands) is recorded when it is published (stored after approval),
 * never when submitted.
 *
 * **Never recorded:** the events signed by the editorial keys ([isEditorialKey]: PPF is the publisher of
 * those, not their host); reading, searching, downloading; votes, ballots and anonymous supports (they are
 * not events of this API); encrypted private messages and anything only their recipient can read (gift
 * wraps 1059, seals 13, rumors 14, legacy messages 4); reports (NIP-56, 1984); NIP-98 authorisations and
 * every ephemeral kind (never stored); private settings and personal lists (application data 30078, kinds
 * 10000 to 19999); follow lists (3, the social graph, which stays on the devices); group membership,
 * moderation and companions (9000 to 9022, 39001 to 39005: refused or editorial); closed or secret rooms
 * (not supported); and every other kind: a feature that makes a new public kind adds it here in its own
 * pull request (for example media, 1063 and 20 to 22, or reposts, 6 and 16).
 *
 * **Never, anywhere:** an IP address or a port, a browser, an e-mail address, a phone number, a content
 * text. No IP address is ever collected or logged (neither Caddy, nor the server, nor any rate limiting by
 * IP: the limits are per key). No e-mail or phone field may ever be added, even optional: it would make
 * five years of retention of civil identity (décret n° 2021-1362, article 2). Level 2 of the note (IP sealed
 * under an offline key, section 7.2) stays off unless a lawyer requires it.
 *
 * **Where and who.** A separate PostgreSQL schema, `legal_register` (migration V10), never read by any
 * feature of the platform: the restricted role of the server may only INSERT (afterMigrate.sql). Reading
 * happens only through [answerRequisition], the answer to a judicial requisition approved by two people in
 * the back-office; deleting only through [purge].
 *
 * **Retention.** One year from the operation for the content operations. For an account's key and
 * pseudonyms, one year after the account's last recorded operation: a Nostr account has no closure date,
 * so its last operation stands for it until the terms of use define the closure of an account (open
 * question: « définir la clôture du compte dans les conditions d'utilisation », question 11 of the lawyer).
 * A daily task ([LegalRegisterPurge]) deletes what is older.
 */
class LegalRegister(
    /** Whether a key belongs to the editorial team (PPF publishes those contents as their editor). */
    private val isEditorialKey: (String) -> Boolean = { false },
) {
    /** Whether [event], once stored, enters the register. Cheap: no database. */
    fun records(event: SignedEvent): Boolean = event.kind in PUBLIC_KINDS && !isEditorialKey(event.pubkey)

    /**
     * Records that [event] was just stored, inside the transaction of [connection] (the caller commits or rolls
     * back both together). [replaced] is the version it removed, if any. One INSERT per operation; a
     * deletion request inserts one row per public content of its author it deletes, in one statement.
     */
    internal fun recordStored(connection: Connection, event: SignedEvent, replaced: ReplacedVersion?) {
        if (!records(event)) return
        if (event.kind == EventKinds.DELETION_REQUEST) {
            recordDeletion(connection, event)
            return
        }
        connection.prepareStatement(
            "INSERT INTO legal_register.content_operation " +
                "(event_id, operation, created_at, kind, pubkey, protocol, removed_event_id, removed_created_at, removed_pubkey) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ).use { insert ->
            insert.setString(1, event.id)
            insert.setString(2, if (replaced == null) Operation.CREATE.column else Operation.REPLACE.column)
            insert.setLong(3, event.createdAt)
            insert.setInt(4, event.kind)
            insert.setString(5, event.pubkey)
            insert.setInt(6, PROTOCOL_HTTPS_API)
            insert.setString(7, replaced?.id)
            if (replaced == null) insert.setNull(8, Types.BIGINT) else insert.setLong(8, replaced.createdAt)
            insert.setString(9, replaced?.pubkey)
            insert.executeUpdate()
        }
        if (event.kind == EventKinds.METADATA) recordPseudonyms(connection, event, replaced)
    }

    /**
     * A NIP-09 deletion request: one row per stored public content of the same author it names (by `e` id or
     * by `a` address), never a deletion request itself; nothing when it names no such content.
     */
    private fun recordDeletion(connection: Connection, event: SignedEvent) {
        val ids = event.tagValues("e").filter(::isHex64).distinct().take(MAX_DELETION_TARGETS)
        val addresses = event.tagValues("a").flatMap { listOf(it, it.trimEnd(':')) }.filter { it.length <= 1100 }.distinct().take(MAX_DELETION_TARGETS)
        if (ids.isEmpty() && addresses.isEmpty()) return
        val kinds = (PUBLIC_KINDS - EventKinds.DELETION_REQUEST).toTypedArray()
        connection.prepareStatement(
            """
            INSERT INTO legal_register.content_operation
                (event_id, operation, created_at, kind, pubkey, protocol, removed_event_id, removed_created_at, removed_pubkey)
            SELECT ?, 'delete', ?, ?, ?, ?, t.id, t.created_at, t.pubkey
            FROM events t
            WHERE t.pubkey = ? AND t.kind = ANY(?) AND (t.id = ANY(?) OR t.address = ANY(?))
            ORDER BY t.id
            """.trimIndent(),
        ).use { insert ->
            insert.setString(1, event.id)
            insert.setLong(2, event.createdAt)
            insert.setInt(3, event.kind)
            insert.setString(4, event.pubkey)
            insert.setInt(5, PROTOCOL_HTTPS_API)
            insert.setString(6, event.pubkey)
            insert.setArray(7, connection.createArrayOf("integer", kinds))
            insert.setArray(8, connection.createArrayOf("text", ids.toTypedArray()))
            insert.setArray(9, connection.createArrayOf("text", addresses.toTypedArray()))
            insert.executeUpdate()
        }
    }

    /** A pseudonym row when the profile's pair (name, display_name) differs from the version it replaces. */
    private fun recordPseudonyms(connection: Connection, event: SignedEvent, replaced: ReplacedVersion?) {
        val current = Pseudonyms.of(event.content)
        if (replaced == null && current.isEmpty) return
        if (replaced != null && Pseudonyms.of(replaced.profileContent.orEmpty()) == current) return
        connection.prepareStatement(
            "INSERT INTO legal_register.account_pseudonym (pubkey, name, display_name, event_id, event_created_at) VALUES (?, ?, ?, ?, ?)",
        ).use { insert ->
            insert.setString(1, event.pubkey)
            insert.setString(2, current.name)
            insert.setString(3, current.displayName)
            insert.setString(4, event.id)
            insert.setLong(5, event.createdAt)
            insert.executeUpdate()
        }
    }

    /** The version of a replaceable or addressable content that a newer one just removed (never its text, but a profile's). */
    data class ReplacedVersion(
        val id: String,
        val createdAt: Long,
        val pubkey: String,
        /** The content of a replaced profile (kind 0) only, to tell whether the pseudonyms changed; never stored. */
        val profileContent: String? = null,
    )

    enum class Operation(val column: String) {
        CREATE("create"),
        REPLACE("replace"),
        DELETE("delete"),
    }

    /** The two pseudonym fields of a profile (kind 0), bounded and without NUL characters, or null. */
    data class Pseudonyms(val name: String?, val displayName: String?) {
        val isEmpty: Boolean get() = name == null && displayName == null

        companion object {
            private val json = Json { ignoreUnknownKeys = true }

            fun of(content: String): Pseudonyms {
                val profile = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull() ?: return Pseudonyms(null, null)
                return Pseudonyms(field(profile, "name"), field(profile, "display_name"))
            }

            private fun field(profile: JsonObject, key: String): String? {
                val value = (profile[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                return bounded(value.replace("\u0000", "")).takeIf { it.isNotEmpty() }
            }

            /** At most [MAX_PSEUDONYM_LENGTH] characters, never cutting a surrogate pair. */
            private fun bounded(text: String): String {
                if (text.length <= MAX_PSEUDONYM_LENGTH) return text
                val end = if (Character.isHighSurrogate(text[MAX_PSEUDONYM_LENGTH - 1])) MAX_PSEUDONYM_LENGTH - 1 else MAX_PSEUDONYM_LENGTH
                return text.substring(0, end)
            }
        }
    }

    /** A requisition the database refused to answer, with the stable code of the back-office. */
    class RequisitionRefused(val code: String) : Exception(code)

    /** What one pass of [purge] deleted (counts only). */
    data class PurgeReport(val operations: Long, val pseudonyms: Long)

    companion object {
        /** The one-byte protocol type of décret n° 2021-1362, article 5, 2°: the HTTPS API (POST /api/v1/events). */
        const val PROTOCOL_HTTPS_API: Int = 1

        /** The kinds of public contents recorded (see the class documentation, kind by kind). */
        val PUBLIC_KINDS: Set<Int> = setOf(
            EventKinds.METADATA,
            EventKinds.TEXT_NOTE,
            EventKinds.DELETION_REQUEST,
            7,
            EventKinds.GROUP_CHAT,
            1111,
            EventKinds.LONG_FORM_ARTICLE,
            AgirFormats.KIND_MOBILIZATION,
            AgirFormats.KIND_PARTICIPATION,
            EventKinds.GROUP_METADATA,
        )

        /** A pseudonym is kept up to this many characters (a profile is at most 64 KB). */
        const val MAX_PSEUDONYM_LENGTH: Int = 1_000

        /** A deletion request names at most this many contents for the register (the policy allows 1000 tags). */
        const val MAX_DELETION_TARGETS: Int = 1_000

        /** The SQLSTATE codes of answer_requisition (migration V10) and the codes of the back-office. */
        private val REFUSALS: Map<String, String> = mapOf(
            "LR001" to "requisition_not_approved",
            "LR002" to "requisition_answered",
            "LR003" to "requisition_unavailable",
        )

        /**
         * Deletes what is older than the retention, and nothing younger (the database's own date decides, in the
         * SECURITY DEFINER function legal_register.purge).
         */
        fun purge(dataSource: DataSource): PurgeReport = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT deleted_operations, deleted_pseudonyms FROM legal_register.purge()").use { statement ->
                val rows = statement.executeQuery()
                rows.next()
                PurgeReport(rows.getLong(1), rows.getLong(2))
            }
        }

        /**
         * The only reading of the register: the rows answering the requisition approved in [approvalId], at most
         * once, inside the transaction of [connection] (the caller journals the answer in the same transaction).
         * The search is read by the database from the approval itself. Throws [RequisitionRefused] when the
         * database refuses (not approved by a second person, already answered).
         */
        fun answerRequisition(connection: Connection, approvalId: UUID): RequisitionRows {
            val text = try {
                connection.prepareStatement("SELECT legal_register.answer_requisition(?)::text").use { statement ->
                    statement.setObject(1, approvalId)
                    val rows = statement.executeQuery()
                    rows.next()
                    rows.getString(1)
                }
            } catch (e: SQLException) {
                throw REFUSALS[e.sqlState]?.let(::RequisitionRefused) ?: e
            }
            val answer = Json.parseToJsonElement(text).jsonObject
            return RequisitionRows(
                contentOperations = answer["content_operations"] as? JsonArray ?: JsonArray(emptyList()),
                pseudonyms = answer["pseudonyms"] as? JsonArray ?: JsonArray(emptyList()),
            )
        }

        /** The public counter: requisitions answered per month (UTC, `YYYY-MM`), oldest first. */
        fun requisitionsByMonth(dataSource: DataSource): List<Pair<String, Long>> = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT month, answered FROM legal_register.requisitions_by_month()").use { statement ->
                val rows = statement.executeQuery()
                buildList { while (rows.next()) add(rows.getString(1) to rows.getLong(2)) }
            }
        }

        private fun isHex64(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

/** The rows of the register answering one requisition, as the database built them. */
data class RequisitionRows(
    val contentOperations: JsonArray,
    val pseudonyms: JsonArray,
)
