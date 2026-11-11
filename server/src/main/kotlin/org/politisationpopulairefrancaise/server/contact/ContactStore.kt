package org.politisationpopulairefrancaise.server.contact

import org.politisationpopulairefrancaise.core.contact.CaseNature
import org.politisationpopulairefrancaise.core.contact.CaseStatus
import org.politisationpopulairefrancaise.core.contact.CaseType
import org.politisationpopulairefrancaise.core.contact.ClaimMotif
import org.politisationpopulairefrancaise.core.contact.ContactCategory
import org.politisationpopulairefrancaise.core.contact.DeadlineKind
import org.politisationpopulairefrancaise.core.contact.TicketSubtype
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.backoffice.instant
import org.politisationpopulairefrancaise.server.backoffice.setInstant
import org.politisationpopulairefrancaise.server.backoffice.setText
import org.politisationpopulairefrancaise.server.backoffice.setUuid
import org.politisationpopulairefrancaise.server.backoffice.transaction
import org.politisationpopulairefrancaise.server.backoffice.uuid
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** The priority the team gives a case (S-107). */
enum class CasePriority(val db: String) {
    NORMAL("normal"),
    HIGH("high"),
    ;

    companion object {
        fun of(db: String?): CasePriority? = entries.firstOrNull { it.db == db }
    }
}

/** Whether an envelope came to the inbox of the team (`in`) or leaves for the key of the case (`out`). */
enum class EnvelopeDirection(val db: String) {
    IN("in"),
    OUT("out"),
}

/** A case as stored (table contact_case): metadata only, never a text. */
/** A content and its open reports (S-38, D-234). */
data class ReportedContent(val address: String, val reports: Int, val reasons: List<String>, val lastAt: Instant)

data class StoredCase(
    val number: String,
    val nature: CaseNature,
    val status: CaseStatus,
    val priority: CasePriority,
    val assignedTo: UUID?,
    val receivedAt: Instant,
    val deadlineAt: Instant?,
    val deadlineKind: DeadlineKind?,
    val extendedAt: Instant?,
    val extensionReason: String?,
    val decisionAt: Instant?,
    val decisionAuthor: UUID?,
    val concerns: UUID?,
    val closedAt: Instant?,
    val legalHoldReason: String?,
    val legalHoldReviewAt: Instant?,
    val legalHoldAlertedAt: Instant?,
    val anonymizedAt: Instant?,
    val accessHash: String?,
    val casePubkey: String?,
    val lastActivityAt: Instant,
    /** A report only (S-38, D-234): the address of the content reported and the code of the reason, in clear. */
    val reportContent: String? = null,
    val reportReason: String? = null,
) {
    val type: CaseType get() = nature.type
    val motif: ClaimMotif? get() = nature.motif
    val isClosed: Boolean get() = status == CaseStatus.CLOSED
    val isAnonymized: Boolean get() = anonymizedAt != null
    val hasLegalHold: Boolean get() = legalHoldReason != null
}

/** A legal hold, or its release, waiting for a second member (table contact_legal_hold_request). */
data class HoldRequest(
    val caseNumber: String,
    val release: Boolean,
    val reason: String,
    val reviewAt: Instant?,
    val requestedBy: UUID,
    val requestedAt: Instant,
)

/** What the inbox of the team lists: which cases, seen by whom. */
data class InboxQuery(
    val view: InboxView = InboxView.OPEN,
    val type: CaseType? = null,
    val subtype: TicketSubtype? = null,
    val category: ContactCategory? = null,
    val motif: ClaimMotif? = null,
    val assigned: AssignedFilter = AssignedFilter.Anyone,
    val priority: CasePriority? = null,
    /** Whether the viewer may see the claims reserved to administrators. */
    val restricted: Boolean = false,
    /** The viewer: the cases that concern them are never listed. */
    val viewer: UUID,
    /** Every case, or the requests of verification only (section `verifications`, CO-18, D-143). */
    val scope: ContactScope = ContactScope.ALL,
    val limit: Int = 200,
)

sealed interface InboxView {
    /** Not closed, or brought back by a legal hold to review or a pending hold request. */
    data object OPEN : InboxView

    data object ALL : InboxView

    /** Under a legal hold, or with a hold request pending. */
    data object HOLDS : InboxView

    data class Status(val status: CaseStatus) : InboxView
}

sealed interface AssignedFilter {
    data object Anyone : AssignedFilter

    data object Nobody : AssignedFilter

    data class To(val account: UUID) : AssignedFilter
}

/**
 * The cases on the server (plain JDBC, as the rest of the server). Never logs anything: a database message
 * could quote a row.
 */
class ContactStore(private val dataSource: DataSource) {

    sealed interface Created {
        data object Stored : Created

        /** The very same creation, sent again (same number, same access hash, same opening envelope). */
        data class Again(val case: StoredCase) : Created

        data object NumberTaken : Created

        data object EnvelopeTaken : Created
    }

    sealed interface Added {
        data class Stored(val case: StoredCase) : Added

        data class Again(val case: StoredCase) : Added

        data object EnvelopeTaken : Added

        data object Closed : Added

        data object Gone : Added
    }

    private class Rollback(val result: Created) : RuntimeException(null, null, false, false)

    /** Stores a new case and its opening envelope, in one transaction. */
    fun create(case: StoredCase, envelope: SignedEvent): Created = try {
        dataSource.transaction { connection ->
            val inserted = connection.prepareStatement(
                """
                INSERT INTO contact_case (number, type, subtype, category, motif, status, priority, received_at, deadline_at, deadline_kind,
                    decision_at, access_hash, case_pubkey, last_activity_at, report_content, report_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (number) DO NOTHING
                """.trimIndent(),
            ).use { insert ->
                insert.setString(1, case.number)
                insert.setString(2, case.type.wire)
                insert.setText(3, case.nature.subtype?.wire)
                insert.setText(4, case.nature.category?.wire)
                insert.setText(5, case.nature.motif?.wire)
                insert.setString(6, case.status.wire)
                insert.setString(7, case.priority.db)
                insert.setInstant(8, case.receivedAt)
                insert.setInstant(9, case.deadlineAt)
                insert.setText(10, case.deadlineKind?.wire)
                insert.setInstant(11, case.decisionAt)
                insert.setText(12, case.accessHash)
                insert.setText(13, case.casePubkey)
                insert.setInstant(14, case.lastActivityAt)
                insert.setText(15, case.reportContent)
                insert.setText(16, case.reportReason)
                insert.executeUpdate() == 1
            }
            if (!inserted) {
                val existing = find(connection, case.number, lock = false)
                val same = existing != null && existing.accessHash == case.accessHash && envelopeCase(connection, envelope.id) == case.number
                return@transaction if (same) Created.Again(existing!!) else Created.NumberTaken
            }
            if (!insertEnvelope(connection, envelope, case.number, EnvelopeDirection.IN, null, case.receivedAt)) throw Rollback(Created.EnvelopeTaken)
            Created.Stored
        }
    } catch (e: Rollback) {
        e.result
    }

    /** Adds an envelope of the person to an open case and updates its status and activity, in one transaction. */
    fun addIncoming(number: String, envelope: SignedEvent, now: Instant, update: (StoredCase) -> StoredCase): Added = dataSource.transaction { connection ->
        val case = find(connection, number, lock = true) ?: return@transaction Added.Gone
        when (envelopeCase(connection, envelope.id)) {
            number -> return@transaction Added.Again(case)
            null -> Unit
            else -> return@transaction Added.EnvelopeTaken
        }
        if (case.isClosed) return@transaction Added.Closed
        if (!insertEnvelope(connection, envelope, number, EnvelopeDirection.IN, null, now)) return@transaction Added.EnvelopeTaken
        val updated = update(case)
        save(connection, updated)
        Added.Stored(updated)
    }

    /** Adds a reply of the team (step 3, the team client; the tests use it now). */
    fun addOutgoing(number: String, envelope: SignedEvent, author: UUID, now: Instant): Boolean = dataSource.transaction { connection ->
        find(connection, number, lock = true) ?: return@transaction false
        insertEnvelope(connection, envelope, number, EnvelopeDirection.OUT, author, now)
    }

    fun find(number: String): StoredCase? = dataSource.connection.use { find(it, number, lock = false) }

    /** Runs [block] on the case [number], locked until the end of the transaction; [block] returns the new state to save, or null to change nothing. */
    fun <T> change(number: String, block: (Connection, StoredCase) -> Pair<StoredCase?, T>): T? = dataSource.transaction { connection ->
        val case = find(connection, number, lock = true) ?: return@transaction null
        val (updated, result) = block(connection, case)
        if (updated != null && updated != case) save(connection, updated)
        result
    }

    /** The envelopes of the team for [number] after the envelope [after] (from the first when unknown), oldest first; and whether more wait. */
    fun outgoing(number: String, after: String?, limit: Int): Pair<List<SignedEvent>, Boolean> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT event FROM contact_envelope
            WHERE case_number = ? AND direction = 'out'
              AND seq > COALESCE((SELECT seq FROM contact_envelope WHERE id = ? AND case_number = ?), 0)
            ORDER BY seq
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, number)
            statement.setText(2, after)
            statement.setString(3, number)
            statement.setInt(4, limit + 1)
            val rows = statement.executeQuery()
            val events = buildList { while (rows.next()) add(SignedEvent.fromJson(rows.getString(1))) }
            events.take(limit) to (events.size > limit)
        }
    }

    /** How many envelopes a case holds, in and out (the team sees counts, never a text). */
    fun envelopeCounts(number: String): Pair<Int, Int> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT direction, count(*) FROM contact_envelope WHERE case_number = ? GROUP BY direction").use { statement ->
            statement.setString(1, number)
            val rows = statement.executeQuery()
            var incoming = 0
            var outgoing = 0
            while (rows.next()) if (rows.getString(1) == EnvelopeDirection.IN.db) incoming = rows.getInt(2) else outgoing = rows.getInt(2)
            incoming to outgoing
        }
    }

    /** The inbox of the team (S-107): by deadline, then by reception. */
    /**
     * The reports still open, grouped by content (S-38, D-234): the most reported first. Nothing is hidden by
     * this list: the team decides, with the tools of moderation.
     */
    fun reportsByContent(limit: Int = 100): List<ReportedContent> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT report_content, count(*) AS reports, string_agg(DISTINCT report_reason, ',') AS reasons, max(received_at) AS last_at
            FROM contact_case
            WHERE report_content IS NOT NULL AND status <> 'closed'
            GROUP BY report_content
            ORDER BY reports DESC, last_at DESC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            ReportedContent(
                                address = rows.getString("report_content"),
                                reports = rows.getInt("reports"),
                                reasons = rows.getString("reasons").orEmpty().split(',').filter { it.isNotEmpty() }.sorted(),
                                lastAt = rows.getTimestamp("last_at").toInstant(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun inbox(query: InboxQuery): List<StoredCase> = dataSource.connection.use { connection ->
        val conditions = mutableListOf<String>()
        val binders = mutableListOf<(PreparedStatement, Int) -> Unit>()
        if (!query.restricted) conditions += "(c.motif IS NULL OR c.motif NOT IN ('funds', 'team_member'))"
        conditions += "(c.concerns IS NULL OR c.concerns <> ?)"
        binders += { s, i -> s.setObject(i, query.viewer) }
        if (query.scope == ContactScope.VERIFICATION) {
            conditions += "c.type = ? AND c.subtype = ?"
            binders += { s, i -> s.setString(i, CaseType.TICKET.wire) }
            binders += { s, i -> s.setString(i, TicketSubtype.VERIFICATION.wire) }
        }
        val pending = "EXISTS (SELECT 1 FROM contact_legal_hold_request r WHERE r.case_number = c.number)"
        when (val view = query.view) {
            InboxView.OPEN -> conditions += "(c.status <> 'closed' OR c.legal_hold_alerted_at IS NOT NULL OR $pending)"
            InboxView.ALL -> Unit
            InboxView.HOLDS -> conditions += "(c.legal_hold_reason IS NOT NULL OR $pending)"
            is InboxView.Status -> {
                conditions += "c.status = ?"
                binders += { s, i -> s.setString(i, view.status.wire) }
            }
        }
        query.type?.let { type ->
            conditions += "c.type = ?"
            binders += { s, i -> s.setString(i, type.wire) }
        }
        listOf("subtype" to query.subtype?.wire, "category" to query.category?.wire, "motif" to query.motif?.wire).forEach { (column, value) ->
            if (value != null) {
                conditions += "c.$column = ?"
                binders += { s, i -> s.setString(i, value) }
            }
        }
        when (val assigned = query.assigned) {
            AssignedFilter.Anyone -> Unit
            AssignedFilter.Nobody -> conditions += "c.assigned_to IS NULL"
            is AssignedFilter.To -> {
                conditions += "c.assigned_to = ?"
                binders += { s, i -> s.setObject(i, assigned.account) }
            }
        }
        query.priority?.let { priority ->
            conditions += "c.priority = ?"
            binders += { s, i -> s.setString(i, priority.db) }
        }
        val sql = "$SELECT c WHERE ${conditions.joinToString(" AND ")} ORDER BY c.deadline_at NULLS LAST, c.received_at, c.number LIMIT ?"
        connection.prepareStatement(sql).use { statement ->
            binders.forEachIndexed { index, bind -> bind(statement, index + 1) }
            statement.setInt(binders.size + 1, query.limit)
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toCase()) }
        }
    }

    // Legal holds --------------------------------------------------------------------------------------

    fun holdRequest(number: String): HoldRequest? = dataSource.connection.use { holdRequest(it, number) }

    /** The numbers of the cases with a hold request pending (a handful at most). */
    fun pendingHoldNumbers(): Set<String> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT case_number FROM contact_legal_hold_request").use { statement ->
            val rows = statement.executeQuery()
            buildSet { while (rows.next()) add(rows.getString(1)) }
        }
    }

    fun holdRequest(connection: Connection, number: String): HoldRequest? =
        connection.prepareStatement("SELECT case_number, action, reason, review_at, requested_by, requested_at FROM contact_legal_hold_request WHERE case_number = ?").use { statement ->
            statement.setString(1, number)
            val rows = statement.executeQuery()
            if (!rows.next()) {
                null
            } else {
                HoldRequest(
                    caseNumber = rows.getString("case_number"),
                    release = rows.getString("action") == "release",
                    reason = rows.getString("reason"),
                    reviewAt = rows.instant("review_at"),
                    requestedBy = rows.uuid("requested_by")!!,
                    requestedAt = rows.instant("requested_at")!!,
                )
            }
        }

    /** Records a request; false when one is already pending for this case. */
    fun insertHoldRequest(connection: Connection, request: HoldRequest): Boolean =
        connection.prepareStatement(
            "INSERT INTO contact_legal_hold_request (case_number, action, reason, review_at, requested_by, requested_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (case_number) DO NOTHING",
        ).use { insert ->
            insert.setString(1, request.caseNumber)
            insert.setString(2, if (request.release) "release" else "hold")
            insert.setString(3, request.reason)
            insert.setInstant(4, request.reviewAt)
            insert.setObject(5, request.requestedBy)
            insert.setInstant(6, request.requestedAt)
            insert.executeUpdate() == 1
        }

    fun deleteHoldRequest(connection: Connection, number: String): Boolean =
        connection.prepareStatement("DELETE FROM contact_legal_hold_request WHERE case_number = ?").use { delete ->
            delete.setString(1, number)
            delete.executeUpdate() == 1
        }

    // Retention (contract, section 6; docs/REGISTRE_DONNEES.md) -------------------------------------------

    /** Closes the cases awaiting the person whose last activity is older than [before]. */
    fun closeSilent(before: Instant, now: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE contact_case SET status = 'closed', closed_at = ?, last_activity_at = ? WHERE status = 'awaiting_you' AND last_activity_at < ?",
        ).use { update ->
            update.setInstant(1, now)
            update.setInstant(2, now)
            update.setInstant(3, before)
            update.executeUpdate()
        }
    }

    /** Deletes the hold requests older than [before] (no second person within the delay). */
    fun expireHoldRequests(before: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM contact_legal_hold_request WHERE requested_at < ?").use { delete ->
            delete.setInstant(1, before)
            delete.executeUpdate()
        }
    }

    /** Marks the holds whose review date has come and that were not signalled yet; returns how many. */
    fun markHoldsDue(now: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE contact_case SET legal_hold_alerted_at = ? WHERE legal_hold_review_at <= ? AND legal_hold_alerted_at IS NULL",
        ).use { update ->
            update.setInstant(1, now)
            update.setInstant(2, now)
            update.executeUpdate()
        }
    }

    /** Lifts the holds whose review date is older than [before] (not renewed in time): the ordinary durations resume. */
    fun liftLapsedHolds(before: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE contact_case SET legal_hold_reason = NULL, legal_hold_review_at = NULL, legal_hold_alerted_at = NULL WHERE legal_hold_review_at < ?",
        ).use { update ->
            update.setInstant(1, before)
            update.executeUpdate()
        }
    }

    /** Deletes the tickets and contacts closed before [before], with their envelopes, unless under a legal hold. */
    fun deleteClosedMessages(before: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "DELETE FROM contact_case WHERE type IN ('ticket', 'contact') AND status = 'closed' AND closed_at < ? AND legal_hold_reason IS NULL",
        ).use { delete ->
            delete.setInstant(1, before)
            delete.executeUpdate()
        }
    }

    /**
     * Anonymises the claims closed before [before], unless under a legal hold: envelopes, access hash, key of
     * the case, members named and reason of an extension removed; nature, status and dates kept.
     */
    fun anonymizeClosedClaims(before: Instant, now: Instant): Int = dataSource.transaction { connection ->
        val numbers = connection.prepareStatement(
            """
            SELECT number FROM contact_case
            WHERE type = 'claim' AND status = 'closed' AND closed_at < ? AND anonymized_at IS NULL AND legal_hold_reason IS NULL
            FOR UPDATE
            """.trimIndent(),
        ).use { select ->
            select.setInstant(1, before)
            val rows = select.executeQuery()
            buildList { while (rows.next()) add(rows.getString(1)) }
        }
        if (numbers.isEmpty()) return@transaction 0
        val array = connection.createArrayOf("text", numbers.toTypedArray())
        connection.prepareStatement("DELETE FROM contact_envelope WHERE case_number = ANY (?)").use { delete ->
            delete.setArray(1, array)
            delete.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM contact_legal_hold_request WHERE case_number = ANY (?)").use { delete ->
            delete.setArray(1, array)
            delete.executeUpdate()
        }
        connection.prepareStatement(
            """
            UPDATE contact_case SET anonymized_at = ?, access_hash = NULL, case_pubkey = NULL, assigned_to = NULL, decision_author = NULL,
                concerns = NULL, extension_reason = NULL
            WHERE number = ANY (?)
            """.trimIndent(),
        ).use { update ->
            update.setInstant(1, now)
            update.setArray(2, array)
            update.executeUpdate()
        }
    }

    // Rows -------------------------------------------------------------------------------------------

    private fun find(connection: Connection, number: String, lock: Boolean): StoredCase? =
        connection.prepareStatement("$SELECT c WHERE c.number = ?" + if (lock) " FOR UPDATE" else "").use { statement ->
            statement.setString(1, number)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toCase() else null
        }

    private fun envelopeCase(connection: Connection, id: String): String? =
        connection.prepareStatement("SELECT case_number FROM contact_envelope WHERE id = ?").use { statement ->
            statement.setString(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.getString(1) else null
        }

    private fun insertEnvelope(connection: Connection, envelope: SignedEvent, number: String, direction: EnvelopeDirection, author: UUID?, now: Instant): Boolean =
        connection.prepareStatement(
            "INSERT INTO contact_envelope (id, case_number, direction, event, received_at, team_author) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
        ).use { insert ->
            insert.setString(1, envelope.id)
            insert.setString(2, number)
            insert.setString(3, direction.db)
            insert.setString(4, envelope.toJson())
            insert.setInstant(5, now)
            insert.setUuid(6, author)
            insert.executeUpdate() == 1
        }

    /** Writes every column the team or the server may change (the nature, the reception and the secrets never change). */
    private fun save(connection: Connection, case: StoredCase) {
        connection.prepareStatement(
            """
            UPDATE contact_case SET status = ?, priority = ?, assigned_to = ?, deadline_at = ?, deadline_kind = ?, extended_at = ?, extension_reason = ?,
                decision_author = ?, concerns = ?, closed_at = ?, legal_hold_reason = ?, legal_hold_review_at = ?, legal_hold_alerted_at = ?, last_activity_at = ?
            WHERE number = ?
            """.trimIndent(),
        ).use { update ->
            update.setString(1, case.status.wire)
            update.setString(2, case.priority.db)
            update.setUuid(3, case.assignedTo)
            update.setInstant(4, case.deadlineAt)
            update.setText(5, case.deadlineKind?.wire)
            update.setInstant(6, case.extendedAt)
            update.setText(7, case.extensionReason)
            update.setUuid(8, case.decisionAuthor)
            update.setUuid(9, case.concerns)
            update.setInstant(10, case.closedAt)
            update.setText(11, case.legalHoldReason)
            update.setInstant(12, case.legalHoldReviewAt)
            update.setInstant(13, case.legalHoldAlertedAt)
            update.setInstant(14, case.lastActivityAt)
            update.setString(15, case.number)
            check(update.executeUpdate() == 1) { "The case is gone" }
        }
    }

    private fun ResultSet.toCase(): StoredCase = StoredCase(
        number = getString("number"),
        nature = requireNotNull(CaseNature.fromWire(getString("type"), getString("subtype"), getString("category"), getString("motif"))) { "A stored case has a valid nature" },
        status = requireNotNull(CaseStatus.fromWire(getString("status"))),
        priority = requireNotNull(CasePriority.of(getString("priority"))),
        assignedTo = uuid("assigned_to"),
        receivedAt = instant("received_at")!!,
        deadlineAt = instant("deadline_at"),
        deadlineKind = DeadlineKind.fromWire(getString("deadline_kind")),
        extendedAt = instant("extended_at"),
        extensionReason = getString("extension_reason"),
        decisionAt = instant("decision_at"),
        decisionAuthor = uuid("decision_author"),
        concerns = uuid("concerns"),
        closedAt = instant("closed_at"),
        legalHoldReason = getString("legal_hold_reason"),
        legalHoldReviewAt = instant("legal_hold_review_at"),
        legalHoldAlertedAt = instant("legal_hold_alerted_at"),
        anonymizedAt = instant("anonymized_at"),
        accessHash = getString("access_hash"),
        casePubkey = getString("case_pubkey"),
        lastActivityAt = instant("last_activity_at")!!,
        reportContent = getString("report_content"),
        reportReason = getString("report_reason"),
    )

    private companion object {
        const val SELECT =
            "SELECT c.number, c.type, c.subtype, c.category, c.motif, c.status, c.priority, c.assigned_to, c.received_at, c.deadline_at, c.deadline_kind, " +
                "c.extended_at, c.extension_reason, c.decision_at, c.decision_author, c.concerns, c.closed_at, c.legal_hold_reason, c.legal_hold_review_at, " +
                "c.legal_hold_alerted_at, c.anonymized_at, c.access_hash, c.case_pubkey, c.last_activity_at, c.report_content, c.report_reason FROM contact_case"
    }
}
