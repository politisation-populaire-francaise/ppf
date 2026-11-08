package org.politisationpopulairefrancaise.server.editorial

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.server.backoffice.instant
import org.politisationpopulairefrancaise.server.backoffice.nowSeconds
import org.politisationpopulairefrancaise.server.backoffice.setInstant
import org.politisationpopulairefrancaise.server.backoffice.setText
import org.politisationpopulairefrancaise.server.backoffice.setUuid
import org.politisationpopulairefrancaise.server.backoffice.transaction
import org.politisationpopulairefrancaise.server.backoffice.uuid
import java.sql.Connection
import java.sql.ResultSet
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** Where one submission to the signing service stands (column `editorial_signing.state`). */
enum class SigningState(val db: String) {
    SUBMITTED("submitted"),
    SIGNED("signed"),
    PUBLISHED("published"),
    REFUSED("refused"),
    EXPIRED("expired"),
    WITHDRAWN("withdrawn"),
    ;

    /** A living submission: waiting for its approvals or its signature, or signed and not yet published. */
    val open: Boolean get() = this == SUBMITTED || this == SIGNED

    companion object {
        fun of(db: String): SigningState = entries.first { it.db == db }
    }
}

/** One submission of an approved version (table `editorial_signing`): nothing secret, see the migration. */
data class SigningAttempt(
    val eventId: String,
    val draftId: UUID,
    val version: Int,
    val use: SigningUse,
    val unsigned: UnsignedEvent,
    val approvalUrl: String?,
    val state: SigningState,
    val refusalCode: String?,
    val ticket: String?,
    val signed: SignedEvent?,
    val submittedAt: Instant,
    val expiresAt: Instant,
    val publishedAt: Instant?,
)

/**
 * The submissions of the editorial circuit to the signing service (step 5, `V13__editorial_signing.sql`,
 * provisional number), and the state of the draft that goes with them. Plain JDBC, blocking.
 */
class EditorialSigning(private val dataSource: DataSource, private val clock: Clock = Clock.systemUTC()) {
    /** Records a submission accepted by the signing service; every older living submission of the draft expires. */
    fun recordSubmitted(draftId: UUID, version: Int, use: SigningUse, unsigned: UnsignedEvent, signerDraftId: String, approvalUrl: String): SigningAttempt =
        dataSource.transaction { connection ->
            closeOpen(connection, draftId, SigningState.EXPIRED)
            insert(connection, draftId, version, use, unsigned, signerDraftId, approvalUrl, SigningState.SUBMITTED, null)
            requireNotNull(find(connection, unsigned.idHex()))
        }

    /** Records a submission refused at once (`DraftSubmission.Refused`): the draft goes to `signing_refused`. */
    fun recordRefusedSubmission(draftId: UUID, version: Int, use: SigningUse, unsigned: UnsignedEvent, code: String) = dataSource.transaction { connection ->
        closeOpen(connection, draftId, SigningState.EXPIRED)
        val id = unsigned.idHex()
        if (find(connection, id) == null) insert(connection, draftId, version, use, unsigned, null, null, SigningState.REFUSED, safeCode(code))
        setDraftStatus(connection, draftId, DraftStatus.SIGNING_REFUSED)
    }

    /** The latest submission of [version] of draft [draftId], any state. */
    fun latest(draftId: UUID, version: Int): SigningAttempt? = dataSource.connection.use { connection ->
        connection.prepareStatement("$COLUMNS WHERE draft_id = ? AND version = ? ORDER BY submitted_at DESC, event_created_at DESC LIMIT 1").use { query ->
            query.setUuid(1, draftId)
            query.setInt(2, version)
            query.executeQuery().use { rows -> if (rows.next()) rows.toAttempt() else null }
        }
    }

    /** Every submission of draft [draftId], the newest first. */
    fun attempts(draftId: UUID): List<SigningAttempt> = dataSource.connection.use { connection ->
        connection.prepareStatement("$COLUMNS WHERE draft_id = ? ORDER BY submitted_at DESC, event_created_at DESC").use { query ->
            query.setUuid(1, draftId)
            query.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.toAttempt() else null }.toList() }
        }
    }

    fun find(eventId: String): SigningAttempt? = dataSource.connection.use { find(it, eventId) }

    fun setTicket(eventId: String, ticket: String?) = update(eventId, "UPDATE editorial_signing SET ticket = ? WHERE event_id = ? AND state = 'submitted'", 2) { it.setText(1, ticket) }

    /** The submission expired on the signing service: a retry builds a new event. */
    fun markExpired(eventId: String) = update(eventId, "UPDATE editorial_signing SET state = 'expired', ticket = NULL WHERE event_id = ? AND state = 'submitted'", 1)

    /** Signed by the service and checked; kept until it is published. */
    fun markSigned(eventId: String, signed: SignedEvent) =
        update(eventId, "UPDATE editorial_signing SET state = 'signed', ticket = NULL, signed_event = CAST(? AS jsonb) WHERE event_id = ? AND state = 'submitted'", 2) { it.setString(1, signed.toJson()) }

    /** Refused by the service or by this server: the draft goes to `signing_refused`. Whether the submission was living. */
    fun markRefused(eventId: String, code: String): Boolean = dataSource.transaction { connection ->
        val attempt = find(connection, eventId) ?: return@transaction false
        val changed = connection.prepareStatement("UPDATE editorial_signing SET state = 'refused', refusal_code = ?, ticket = NULL WHERE event_id = ? AND state IN ('submitted', 'signed')").use { update ->
            update.setString(1, safeCode(code))
            update.setString(2, eventId)
            update.executeUpdate()
        } == 1
        setDraftStatus(connection, attempt.draftId, DraftStatus.SIGNING_REFUSED, onlyFrom = setOf(DraftStatus.READY_TO_SIGN))
        changed
    }

    /** Refused by this server before anything was sent: the draft goes to `signing_refused`. Whether it was ready to sign. */
    fun refuseUnsent(draftId: UUID): Boolean = dataSource.transaction { connection ->
        closeOpen(connection, draftId, SigningState.EXPIRED)
        setDraftStatus(connection, draftId, DraftStatus.SIGNING_REFUSED, onlyFrom = setOf(DraftStatus.READY_TO_SIGN)) == 1
    }

    /** Published through the publication port: the draft is `published`, with the event id. */
    fun markPublished(eventId: String) = dataSource.transaction { connection ->
        val attempt = find(connection, eventId) ?: return@transaction
        val now = clock.nowSeconds()
        connection.prepareStatement("UPDATE editorial_signing SET state = 'published', published_at = ? WHERE event_id = ? AND state = 'signed'").use { update ->
            update.setInstant(1, now)
            update.setString(2, eventId)
            update.executeUpdate()
        }
        connection.prepareStatement("UPDATE editorial_draft SET status = ?, published_event_id = ?, updated_at = ? WHERE id = ?").use { update ->
            update.setString(1, DraftStatus.PUBLISHED.db)
            update.setString(2, eventId)
            update.setInstant(3, now)
            update.setUuid(4, attempt.draftId)
            update.executeUpdate()
        }
    }

    /** Before a retry: the living submissions of the draft expire and the draft is ready to sign again. */
    fun reopen(draftId: UUID) = dataSource.transaction { connection ->
        closeOpen(connection, draftId, SigningState.EXPIRED)
        setDraftStatus(connection, draftId, DraftStatus.READY_TO_SIGN, onlyFrom = setOf(DraftStatus.READY_TO_SIGN, DraftStatus.SIGNING_REFUSED))
    }

    private fun closeOpen(connection: Connection, draftId: UUID, state: SigningState) {
        connection.prepareStatement("UPDATE editorial_signing SET state = ?, ticket = NULL WHERE draft_id = ? AND state IN ('submitted', 'signed')").use { update ->
            update.setString(1, state.db)
            update.setUuid(2, draftId)
            update.executeUpdate()
        }
    }

    private fun setDraftStatus(connection: Connection, draftId: UUID, status: DraftStatus, onlyFrom: Set<DraftStatus>? = null): Int {
        val where = onlyFrom?.let { " AND status IN (${DraftStatus.sqlList(it)})" }.orEmpty()
        return connection.prepareStatement("UPDATE editorial_draft SET status = ?, updated_at = ? WHERE id = ?$where").use { update ->
            update.setString(1, status.db)
            update.setInstant(2, clock.nowSeconds())
            update.setUuid(3, draftId)
            update.executeUpdate()
        }
    }

    private fun insert(
        connection: Connection, draftId: UUID, version: Int, use: SigningUse, unsigned: UnsignedEvent,
        signerDraftId: String?, approvalUrl: String?, state: SigningState, code: String?,
    ) {
        val now = clock.nowSeconds()
        connection.prepareStatement(
            "INSERT INTO editorial_signing (event_id, draft_id, version, use_id, pubkey, event_created_at, unsigned_event, signer_draft_id, approval_url, state, refusal_code, submitted_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)",
        ).use { insert ->
            insert.setString(1, unsigned.idHex())
            insert.setUuid(2, draftId)
            insert.setInt(3, version)
            insert.setString(4, use.id)
            insert.setString(5, unsigned.pubkey)
            insert.setLong(6, unsigned.createdAt)
            insert.setString(7, encodeUnsigned(unsigned))
            insert.setText(8, signerDraftId)
            insert.setText(9, approvalUrl?.take(500))
            insert.setString(10, state.db)
            insert.setText(11, code)
            insert.setInstant(12, now)
            insert.setInstant(13, now.plus(DRAFT_LIFETIME))
            insert.executeUpdate()
        }
    }

    private fun update(eventId: String, sql: String, idIndex: Int, bind: (java.sql.PreparedStatement) -> Unit = {}) = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { update ->
            bind(update)
            update.setString(idIndex, eventId)
            update.executeUpdate()
        }
    }

    private fun find(connection: Connection, eventId: String): SigningAttempt? =
        connection.prepareStatement("$COLUMNS WHERE event_id = ?").use { query ->
            query.setString(1, eventId)
            query.executeQuery().use { rows -> if (rows.next()) rows.toAttempt() else null }
        }

    private fun ResultSet.toAttempt(): SigningAttempt = SigningAttempt(
        eventId = getString("event_id"),
        draftId = requireNotNull(uuid("draft_id")),
        version = getInt("version"),
        use = SigningUse.entries.first { it.id == getString("use_id") },
        unsigned = decodeUnsigned(getString("unsigned_event")),
        approvalUrl = getString("approval_url"),
        state = SigningState.of(getString("state")),
        refusalCode = getString("refusal_code"),
        ticket = getString("ticket"),
        signed = getString("signed_event")?.let(SignedEvent::fromJson),
        submittedAt = requireNotNull(instant("submitted_at")),
        expiresAt = requireNotNull(instant("expires_at")),
        publishedAt = instant("published_at"),
    )

    companion object {
        /** A draft expires after 7 days on the signing service (contract of the signing service, section 5). */
        val DRAFT_LIFETIME: Duration = Duration.ofDays(7)

        private const val COLUMNS =
            "SELECT event_id, draft_id, version, use_id, unsigned_event::text AS unsigned_event, approval_url, state, refusal_code, ticket, " +
                "signed_event::text AS signed_event, submitted_at, expires_at, published_at FROM editorial_signing"

        /** A stable code as the table keeps it (lowercase letters, digits, underscores), never a free text. */
        fun safeCode(code: String): String = code.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }.joinToString("").take(64).ifEmpty { "refused" }

        fun encodeUnsigned(event: UnsignedEvent): String = JsonObject(
            mapOf(
                "pubkey" to JsonPrimitive(event.pubkey),
                "created_at" to JsonPrimitive(event.createdAt),
                "kind" to JsonPrimitive(event.kind),
                "tags" to JsonArray(event.tags.map { tag -> JsonArray(tag.map(::JsonPrimitive)) }),
                "content" to JsonPrimitive(event.content),
            ),
        ).toString()

        fun decodeUnsigned(text: String): UnsignedEvent {
            val json = Json.parseToJsonElement(text).jsonObject
            return UnsignedEvent(
                pubkey = json.getValue("pubkey").jsonPrimitive.content,
                createdAt = json.getValue("created_at").jsonPrimitive.long,
                kind = json.getValue("kind").jsonPrimitive.int,
                tags = json.getValue("tags").jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
                content = json.getValue("content").jsonPrimitive.content,
            )
        }
    }
}
