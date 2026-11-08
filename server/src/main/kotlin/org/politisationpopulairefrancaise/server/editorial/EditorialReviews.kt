package org.politisationpopulairefrancaise.server.editorial

import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.server.backoffice.instant
import org.politisationpopulairefrancaise.server.backoffice.nowSeconds
import org.politisationpopulairefrancaise.server.backoffice.setInstant
import org.politisationpopulairefrancaise.server.backoffice.setText
import org.politisationpopulairefrancaise.server.backoffice.setUuid
import org.politisationpopulairefrancaise.server.backoffice.transaction
import org.politisationpopulairefrancaise.server.backoffice.uuid
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** A decision on a submitted version (table `editorial_review`). */
enum class ReviewDecision(val db: String, val status: DraftStatus, val needsReason: Boolean) {
    /** Back to its writers, editable again, with a message. */
    CHANGES_REQUESTED("changes_requested", DraftStatus.CHANGES_REQUESTED, true),

    /** Closed, with a reason. */
    REJECTED("rejected", DraftStatus.REJECTED, true),

    /** Approved for publication: ready to be signed on the pages of the signing service (D-129 revision). */
    READY_TO_SIGN("ready_to_sign", DraftStatus.READY_TO_SIGN, false),
    ;

    companion object {
        fun of(db: String): ReviewDecision = entries.first { it.db == db }
    }
}

/** One decision, never modified. [reason] is the free text for the writers; it never enters the journal. */
data class ReviewRecord(val draftId: UUID, val version: Int, val reviewer: UUID, val decision: ReviewDecision, val reason: String?, val decidedAt: Instant)

/** A draft of the queue, with what its deadline and level depend on. */
data class QueuedDraft(val draft: EditorialDraft, val version: Int, val submittedAt: Instant, val correction: Boolean) {
    /** `<draft uuid>:v<version>`: a new submission is a new item (section 5 of the design). */
    val itemId: String get() = "${draft.id}:v$version"

    /** Seconds since 1970: 48 hours for a correction of a published content, 7 days otherwise (S-115). */
    val deadline: Long get() = submittedAt.plus(if (correction) CORRECTION_DELAY else NEW_DELAY).epochSecond

    /** Importance from 1 to 5: 4 for a correction, 2 otherwise. */
    val level: Int get() = if (correction) CORRECTION_LEVEL else NEW_LEVEL

    companion object {
        val CORRECTION_DELAY: Duration = Duration.ofHours(48)
        val NEW_DELAY: Duration = Duration.ofDays(7)
        const val CORRECTION_LEVEL: Int = 4
        const val NEW_LEVEL: Int = 2

        /** The draft id and the version of an item id, or null when it is not one. */
        fun parseItemId(itemId: String): Pair<UUID, Int>? {
            val draft = itemId.substringBefore(":v", "")
            val version = itemId.substringAfter(":v", "").toIntOrNull()?.takeIf { it >= 1 } ?: return null
            return runCatching { UUID.fromString(draft) }.getOrNull()?.takeIf { it.toString() == draft }?.let { it to version }
        }
    }
}

/** What a submission or a decision led to; codes only (the pages turn them into French). */
sealed interface ReviewResult {
    val code: String

    data class Done(val draft: EditorialDraft) : ReviewResult {
        override val code: String get() = "done"
    }

    data object NotFound : ReviewResult {
        override val code: String = "not_found"
    }

    /** The draft is not in the state the action needs (already decided, in review, finished). */
    data class WrongState(val status: DraftStatus) : ReviewResult {
        override val code: String = "wrong_state"
    }

    /** The version given is not the latest one (submission) or not the submitted one (decision). */
    data object StaleVersion : ReviewResult {
        override val code: String = "stale_version"
    }

    /** This version was already reviewed: the writers save a new version before submitting again. */
    data object AlreadyReviewed : ReviewResult {
        override val code: String = "already_reviewed"
    }

    /** The reviewer wrote the draft (its account, a version, or the same Nostr key): another editor must review it. */
    data object Writer : ReviewResult {
        override val code: String = "writer"
    }

    /** Only a writer of the draft submits it. */
    data object NotWriter : ReviewResult {
        override val code: String = "not_writer"
    }

    data object ReasonRequired : ReviewResult {
        override val code: String = "reason_required"
    }
}

/**
 * The review of the editorial circuit (step 4): submission of a version by one of its writers, and the
 * decision of another editor (request changes, reject, approve for publication). A version under review is
 * never edited: the draft leaves an editable state when it is submitted, and comes back to one only when
 * changes are requested. The rule « two people » is checked here, inside the transaction that decides:
 * the reviewer is neither the author of the draft, nor a writer of one of its versions, nor an account
 * linked to the same Nostr key as one of them ([nostrKeyOf], the optional key of a team account).
 * Plain JDBC, blocking.
 */
class EditorialReviews(
    private val dataSource: DataSource,
    private val drafts: EditorialDrafts,
    private val clock: Clock = Clock.systemUTC(),
    /** The optional Nostr key of a team account (`team_account.nostr_pubkey`). */
    val nostrKeyOf: (UUID) -> String? = { null },
) {
    /** Sends version [version] of draft [id] to review; [actor] must be one of its writers. The caller checked it has no problem. */
    fun submit(id: UUID, version: Int, actor: UUID): ReviewResult = dataSource.transaction { connection ->
        val draft = drafts.find(connection, id, lock = true) ?: return@transaction ReviewResult.NotFound
        if (!draft.status.editable) return@transaction ReviewResult.WrongState(draft.status)
        if (draft.latestVersion != version) return@transaction ReviewResult.StaleVersion
        if (actor !in writers(connection, draft)) return@transaction ReviewResult.NotWriter
        if (decision(connection, id, version) != null) return@transaction ReviewResult.AlreadyReviewed
        val now = clock.nowSeconds()
        connection.prepareStatement(
            "UPDATE editorial_draft SET status = ?, submitted_version = ?, submitted_at = ?, updated_at = ? WHERE id = ?",
        ).use { update ->
            update.setString(1, DraftStatus.IN_REVIEW.db)
            update.setInt(2, version)
            update.setInstant(3, now)
            update.setInstant(4, now)
            update.setUuid(5, id)
            update.executeUpdate()
        }
        ReviewResult.Done(requireNotNull(drafts.find(connection, id)))
    }

    /**
     * The decision of [reviewer] on the submitted version [version] of draft [id]. [reason] is required for a
     * request for changes and a refusal (at most [MAX_REASON] characters), ignored for an approval.
     */
    fun decide(id: UUID, version: Int, decision: ReviewDecision, reviewer: UUID, reason: String?): ReviewResult {
        val text = reason?.trim()?.takeIf { it.isNotEmpty() }
        if (decision.needsReason && (text == null || text.length > MAX_REASON)) return ReviewResult.ReasonRequired
        return dataSource.transaction { connection ->
            val draft = drafts.find(connection, id, lock = true) ?: return@transaction ReviewResult.NotFound
            if (draft.status != DraftStatus.IN_REVIEW) return@transaction ReviewResult.WrongState(draft.status)
            if (draft.submittedVersion != version) return@transaction ReviewResult.StaleVersion
            if (isWriter(reviewer, writers(connection, draft))) return@transaction ReviewResult.Writer
            val now = clock.nowSeconds()
            connection.prepareStatement("INSERT INTO editorial_review (draft_id, version, reviewer, decision, reason, decided_at) VALUES (?, ?, ?, ?, ?, ?)").use { insert ->
                insert.setUuid(1, id)
                insert.setInt(2, version)
                insert.setUuid(3, reviewer)
                insert.setString(4, decision.db)
                insert.setText(5, if (decision.needsReason) text else null)
                insert.setInstant(6, now)
                insert.executeUpdate()
            }
            connection.prepareStatement("UPDATE editorial_draft SET status = ?, updated_at = ? WHERE id = ?").use { update ->
                update.setString(1, decision.status.db)
                update.setInstant(2, now)
                update.setUuid(3, id)
                update.executeUpdate()
            }
            ReviewResult.Done(requireNotNull(drafts.find(connection, id)))
        }
    }

    /** Whether [account] may not review draft [id]: it wrote it (see the class). */
    fun isWriterOf(id: UUID, account: UUID): Boolean = dataSource.connection.use { connection ->
        val draft = drafts.find(connection, id) ?: return@use false
        isWriter(account, writers(connection, draft))
    }

    /** Every decision on draft [id], the newest first. */
    fun decisions(id: UUID): List<ReviewRecord> = dataSource.connection.use { connection ->
        connection.prepareStatement("$REVIEW_COLUMNS WHERE draft_id = ? ORDER BY version DESC").use { query ->
            query.setUuid(1, id)
            query.executeQuery().use { rows ->
                generateSequence {
                    if (!rows.next()) {
                        null
                    } else {
                        ReviewRecord(
                            draftId = requireNotNull(rows.uuid("draft_id")),
                            version = rows.getInt("version"),
                            reviewer = requireNotNull(rows.uuid("reviewer")),
                            decision = ReviewDecision.of(rows.getString("decision")),
                            reason = rows.getString("reason"),
                            decidedAt = requireNotNull(rows.instant("decided_at")),
                        )
                    }
                }.toList()
            }
        }
    }

    /** The drafts of the queue of [space] (in review, then ready to sign), by deadline. */
    fun queued(space: EditorialSpace): List<QueuedDraft> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT d.id, v.change_kind FROM editorial_draft d JOIN editorial_draft_version v ON v.draft_id = d.id AND v.version = d.submitted_version " +
                "WHERE d.space = ? AND d.status IN (${DraftStatus.sqlList(setOf(DraftStatus.IN_REVIEW, DraftStatus.READY_TO_SIGN))}) ORDER BY d.submitted_at, d.id LIMIT ?",
        ).use { query ->
            query.setString(1, space.id)
            query.setInt(2, MAX_QUEUE)
            val rows = query.executeQuery().use { rows ->
                generateSequence { if (rows.next()) requireNotNull(rows.uuid("id")) to (rows.getString("change_kind") == ChangeKind.CORRECTION.db) else null }.toList()
            }
            rows.mapNotNull { (id, correctionKind) ->
                val draft = drafts.find(connection, id) ?: return@mapNotNull null
                QueuedDraft(draft, requireNotNull(draft.submittedVersion), requireNotNull(draft.submittedAt), correction = correctionKind && draft.baseEventId != null)
            }.sortedWith(compareBy<QueuedDraft> { it.draft.status != DraftStatus.IN_REVIEW }.thenBy { it.deadline })
        }
    }

    /** How many drafts of [space] wait for a review. */
    fun inReview(space: EditorialSpace): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT count(*) FROM editorial_draft WHERE space = ? AND status = ?").use { query ->
            query.setString(1, space.id)
            query.setString(2, DraftStatus.IN_REVIEW.db)
            query.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }
    }

    /** The author of the draft and every account that wrote one of its versions. */
    private fun writers(connection: Connection, draft: EditorialDraft): Set<UUID> =
        connection.prepareStatement("SELECT DISTINCT written_by FROM editorial_draft_version WHERE draft_id = ?").use { query ->
            query.setUuid(1, draft.id)
            query.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.uuid("written_by") else null }.toSet() } + draft.author
        }

    private fun isWriter(account: UUID, writers: Set<UUID>): Boolean {
        if (account in writers) return true
        val key = nostrKeyOf(account) ?: return false
        return writers.any { nostrKeyOf(it) == key }
    }

    private fun decision(connection: Connection, id: UUID, version: Int): String? =
        connection.prepareStatement("SELECT decision FROM editorial_review WHERE draft_id = ? AND version = ?").use { query ->
            query.setUuid(1, id)
            query.setInt(2, version)
            query.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    companion object {
        /** The longest message or reason a reviewer writes for the writers. */
        const val MAX_REASON: Int = 2_000
        private const val MAX_QUEUE = 500
        private const val REVIEW_COLUMNS = "SELECT draft_id, version, reviewer, decision, reason, decided_at FROM editorial_review"
    }
}
