package org.politisationpopulairefrancaise.server.revendications

import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.revendications.ProposalStatus
import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

/** The value of the status column (V3__revendication_proposals.sql), the serial name of [ProposalStatus]. */
val ProposalStatus.column: String get() = name.lowercase()

/** A proposal as the queue keeps it (table revendication_proposal). */
data class Proposal(
    val id: String,
    val event: SignedEvent,
    val status: ProposalStatus,
    /** Null until the automatic checks ran. */
    val report: ProposalReport?,
    /** Seconds since 1970. */
    val receivedAt: Long,
    val decidedAt: Long?,
    val reason: String?,
)

sealed interface SubmitOutcome {
    /** Stored as pending; [replaced] is the id of the pending version it replaced, if any. */
    data class Queued(val replaced: String?) : SubmitOutcome

    /** This very event already waits for review. */
    data object AlreadyPending : SubmitOutcome

    /** This very event was approved and published. */
    data object AlreadyApproved : SubmitOutcome

    /** This very event was rejected by the team. */
    data object AlreadyRejected : SubmitOutcome

    /** A more recent version of this demand is already pending or published. */
    data object Superseded : SubmitOutcome

    data object TooManyPendingForKey : SubmitOutcome

    data object QueueFull : SubmitOutcome

    data class OverDailyBudget(val retryAfterSeconds: Long) : SubmitOutcome
}

sealed interface DecisionOutcome {
    /** Approved; [stored] tells what the events table did with the event. */
    data class Approved(val stored: StoreOutcome) : DecisionOutcome

    data object Rejected : DecisionOutcome

    data object NotFound : DecisionOutcome

    data class AlreadyDecided(val status: ProposalStatus) : DecisionOutcome
}

/**
 * The queue of demands proposed by citizens, waiting for the review of a person before publication
 * (decision of the owner, 2026-11-05), in PostgreSQL (V3__revendication_proposals.sql). It stores
 * the signed event, its status and the report of the automatic checks, never anything about the
 * request that brought it. Approving copies the event into the events table, where it is
 * published like any content; the queue keeps decided proposals [RevendicationsConfig.decidedRetention].
 */
class ProposalQueue(
    private val dataSource: DataSource,
    private val events: EventRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Queues [event], already verified and checked by the rules, unless the limits refuse it.
     * [budget] is asked for a token only when the proposal is about to be stored, and refunded if
     * storing fails. Locks (one per author key, one for the whole queue when a new address comes
     * in) make the counts exact under concurrent requests.
     */
    fun submit(event: SignedEvent, identifier: String, maxPendingPerKey: Int, maxPending: Int, budget: PeriodBudget): SubmitOutcome {
        var spent = false
        return try {
            transaction { connection -> submitInTransaction(connection, event, identifier, maxPendingPerKey, maxPending, budget) { spent = true } }
        } catch (e: Exception) {
            // Nothing was stored (the transaction was rolled back): the token goes back.
            if (spent) budget.refund(event.pubkey)
            throw e
        }
    }

    private fun submitInTransaction(
        connection: Connection,
        event: SignedEvent,
        identifier: String,
        maxPendingPerKey: Int,
        maxPending: Int,
        budget: PeriodBudget,
        onSpent: () -> Unit,
    ): SubmitOutcome {
        lock(connection, "revendication_proposal:${event.pubkey}")
        connection.prepareStatement("SELECT status FROM revendication_proposal WHERE id = ?").use { statement ->
            statement.setString(1, event.id)
            val rows = statement.executeQuery()
            if (rows.next()) {
                return when (statusOf(rows.getString(1))) {
                    ProposalStatus.PENDING -> SubmitOutcome.AlreadyPending
                    ProposalStatus.APPROVED -> SubmitOutcome.AlreadyApproved
                    ProposalStatus.REJECTED -> SubmitOutcome.AlreadyRejected
                }
            }
        }
        // A newer version of this demand is already published: this one would be dropped at approval.
        connection.prepareStatement("SELECT id, created_at FROM events WHERE address = ?").use { statement ->
            statement.setString(1, EventReplacement.replacementKey(event))
            val rows = statement.executeQuery()
            if (rows.next() && !EventReplacement.supersedes(event.createdAt, event.id, rows.getLong(2), rows.getString(1))) {
                return SubmitOutcome.Superseded
            }
        }
        val previous = connection.prepareStatement(
            "SELECT id, created_at FROM revendication_proposal WHERE pubkey = ? AND identifier = ? AND status = 'pending' FOR UPDATE",
        ).use { statement ->
            statement.setString(1, event.pubkey)
            statement.setString(2, identifier)
            val rows = statement.executeQuery()
            if (rows.next()) rows.getString(1) to rows.getLong(2) else null
        }
        if (previous != null && !EventReplacement.supersedes(event.createdAt, event.id, previous.second, previous.first)) {
            return SubmitOutcome.Superseded
        }
        if (previous == null) {
            if (countPending(connection, event.pubkey) >= maxPendingPerKey) return SubmitOutcome.TooManyPendingForKey
            // Taken after the lock of the key, always in this order: no deadlock.
            lock(connection, GLOBAL_LOCK)
            if (countPending(connection, null) >= maxPending) return SubmitOutcome.QueueFull
        }
        when (val token = budget.tryAcquire(event.pubkey)) {
            is RateLimiter.Outcome.Limited -> return SubmitOutcome.OverDailyBudget(token.retryAfterSeconds)
            RateLimiter.Outcome.Allowed -> onSpent()
        }
        if (previous != null) {
            connection.prepareStatement("DELETE FROM revendication_proposal WHERE id = ?").use { delete ->
                delete.setString(1, previous.first)
                delete.executeUpdate()
            }
        }
        connection.prepareStatement(
            """
            INSERT INTO revendication_proposal (id, pubkey, identifier, created_at, event, tags, status, received_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, 'pending', ?)
            """.trimIndent(),
        ).use { insert ->
            insert.setString(1, event.id)
            insert.setString(2, event.pubkey)
            insert.setString(3, identifier)
            insert.setLong(4, event.createdAt)
            insert.setString(5, event.toJson())
            insert.setString(6, TagsJson.encode(event.tags))
            insert.setObject(7, now())
            insert.executeUpdate()
        }
        return SubmitOutcome.Queued(previous?.first)
    }

    /** A lock held until the end of the transaction. */
    private fun lock(connection: Connection, name: String) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { lock ->
            lock.setString(1, name)
            lock.executeQuery().close()
        }
    }

    private fun countPending(connection: Connection, pubkey: String?): Int {
        val sql = "SELECT count(*) FROM revendication_proposal WHERE status = 'pending'" + if (pubkey != null) " AND pubkey = ?" else ""
        return connection.prepareStatement(sql).use { statement ->
            if (pubkey != null) statement.setString(1, pubkey)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }
    }

    fun find(id: String): Proposal? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE id = ?").use { statement ->
            statement.setString(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toProposal() else null
        }
    }

    /** The proposals among [ids] that exist, in no particular order. */
    fun findAll(ids: Collection<String>): List<Proposal> {
        if (ids.isEmpty()) return emptyList()
        return dataSource.connection.use { connection ->
            connection.prepareStatement("$SELECT WHERE id = ANY(?)").use { statement ->
                statement.setArray(1, connection.createArrayOf("text", ids.distinct().toTypedArray()))
                val rows = statement.executeQuery()
                buildList { while (rows.next()) add(rows.toProposal()) }
            }
        }
    }

    /** The pending proposals, oldest first, at most [limit]: the review order. */
    fun pending(limit: Int = MAX_LISTED): List<Proposal> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE status = 'pending' ORDER BY received_at ASC, id ASC LIMIT ?").use { statement ->
            statement.setInt(1, limit.coerceIn(1, MAX_LISTED))
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toProposal()) }
        }
    }

    /** Ids of the pending proposals whose checks have not run yet, oldest first. */
    fun uncheckedPending(limit: Int): List<String> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id FROM revendication_proposal WHERE status = 'pending' AND report IS NULL ORDER BY received_at ASC, id ASC LIMIT ?",
        ).use { statement ->
            statement.setInt(1, limit.coerceAtLeast(1))
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.getString(1)) }
        }
    }

    /** Stores the report of the automatic checks; false when the proposal no longer exists. */
    fun saveReport(id: String, report: ProposalReport): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE revendication_proposal SET report = ?::jsonb WHERE id = ?").use { statement ->
            statement.setString(1, reportJson.encodeToString(ProposalReport.serializer(), report))
            statement.setString(2, id)
            statement.executeUpdate() == 1
        }
    }

    /**
     * Publishes a pending proposal: the event goes to the events table (and is then served by
     * GET /api/v1/events like any content), the proposal is marked approved. Only the team may call
     * this (back-office to come, with two-factor authentication and an audit log).
     */
    fun approve(id: String): DecisionOutcome {
        val proposal = find(id) ?: return DecisionOutcome.NotFound
        if (proposal.status != ProposalStatus.PENDING) return DecisionOutcome.AlreadyDecided(proposal.status)
        // Checked again: what the team approves must be exactly what the author signed.
        check(proposal.event.verify()) { "A stored proposal does not verify" }
        // Marked first, atomically: two approvals, or an approval racing a new version, cannot both win.
        if (!decide(id, ProposalStatus.APPROVED, null)) {
            return find(id)?.let { DecisionOutcome.AlreadyDecided(it.status) } ?: DecisionOutcome.NotFound
        }
        val stored = try {
            events.store(proposal.event)
        } catch (e: Exception) {
            // Back to the queue, so that the team can try again (unless a new version took its place meanwhile).
            runCatching { reopen(id) }
            throw e
        }
        return DecisionOutcome.Approved(stored)
    }

    private fun reopen(id: String) = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE revendication_proposal SET status = 'pending', decided_at = NULL WHERE id = ? AND status = 'approved'",
        ).use { statement ->
            statement.setString(1, id)
            statement.executeUpdate()
        }
    }

    /** Refuses a pending proposal; [reason] (1 to 1000 characters) is shown to its author. Nothing is published. */
    fun reject(id: String, reason: String): DecisionOutcome {
        val text = reason.trim()
        require(text.isNotEmpty() && text.length <= MAX_REASON_LENGTH) { "A reason of 1 to $MAX_REASON_LENGTH characters is required" }
        val proposal = find(id) ?: return DecisionOutcome.NotFound
        if (proposal.status != ProposalStatus.PENDING) return DecisionOutcome.AlreadyDecided(proposal.status)
        return if (decide(id, ProposalStatus.REJECTED, text)) {
            DecisionOutcome.Rejected
        } else {
            find(id)?.let { DecisionOutcome.AlreadyDecided(it.status) } ?: DecisionOutcome.NotFound
        }
    }

    private fun decide(id: String, status: ProposalStatus, reason: String?): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "UPDATE revendication_proposal SET status = ?, decided_at = ?, decision_reason = ? WHERE id = ? AND status = 'pending'",
        ).use { statement ->
            statement.setString(1, status.column)
            statement.setObject(2, now())
            statement.setString(3, reason)
            statement.setString(4, id)
            statement.executeUpdate() == 1
        }
    }

    /** Deletes the proposals decided before [cutoff] and returns how many were deleted; pending ones stay. */
    fun deleteDecidedBefore(cutoff: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM revendication_proposal WHERE status <> 'pending' AND decided_at < ?").use { statement ->
            statement.setObject(1, cutoff.atOffset(ZoneOffset.UTC))
            statement.executeUpdate()
        }
    }

    /**
     * The published demands (kind 30023, `ppf-type` = revendication), newest first, at most
     * [limit], without their body: the duplicate check reads titles, summaries and territories only.
     */
    fun publishedDemands(limit: Int = MAX_PUBLISHED_COMPARED): List<SignedEvent> =
        publishedDemandEvents("ORDER BY created_at DESC, id ASC LIMIT ?") { _, statement, index -> statement.setInt(index, limit.coerceAtLeast(1)) }

    /** The published demands at [addresses] (`kind:pubkey:d`), as they read now, by address. */
    fun publishedDemandsAt(addresses: Collection<String>): Map<String, DemandText> {
        if (addresses.isEmpty()) return emptyMap()
        val wanted = addresses.distinct().toTypedArray()
        return publishedDemandEvents("AND address = ANY(?)") { connection, statement, index -> statement.setArray(index, connection.createArrayOf("text", wanted)) }
            .mapNotNull(Article::from).map(DemandText::of).associateBy { it.address }
    }

    /**
     * Seconds since 1970 at which the published demands among [ids] reached the events table, by
     * id: once its proposal is deleted, a published demand still answers "approved" to its author.
     */
    fun publishedDemandReceptions(ids: Collection<String>): Map<String, Long> {
        if (ids.isEmpty()) return emptyMap()
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, received_at FROM events e
                WHERE id = ANY(?) AND kind = ? AND EXISTS (SELECT 1 FROM event_tags t WHERE t.event_id = e.id AND t.name = ? AND t.value = ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setArray(1, connection.createArrayOf("text", ids.distinct().toTypedArray()))
                statement.setInt(2, Article.KIND)
                statement.setString(3, Article.TAG_TYPE)
                statement.setString(4, ArticleType.REVENDICATION.tagValue)
                val rows = statement.executeQuery()
                buildMap { while (rows.next()) put(rows.getString(1), rows.getObject(2, OffsetDateTime::class.java).toEpochSecond()) }
            }
        }
    }

    private fun publishedDemandEvents(clause: String, bind: (Connection, PreparedStatement, Int) -> Unit): List<SignedEvent> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, pubkey, created_at, kind, tags::text AS tags, sig FROM events e
                WHERE kind = ? AND EXISTS (SELECT 1 FROM event_tags t WHERE t.event_id = e.id AND t.name = ? AND t.value = ?)
                $clause
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, Article.KIND)
                statement.setString(2, Article.TAG_TYPE)
                statement.setString(3, ArticleType.REVENDICATION.tagValue)
                bind(connection, statement, 4)
                val rows = statement.executeQuery()
                buildList { while (rows.next()) add(rows.toBodilessEvent(rows.getInt("kind"), rows.getString("sig"))) }
            }
        }

    /**
     * The other pending proposals ([excluding] left out), oldest first, at most [limit], without
     * their body, like [publishedDemands].
     */
    fun pendingDemands(excluding: String, limit: Int = MAX_LISTED): List<SignedEvent> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, pubkey, created_at, tags::text AS tags FROM revendication_proposal
            WHERE status = 'pending' AND id <> ? ORDER BY received_at ASC, id ASC LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, excluding)
            statement.setInt(2, limit.coerceIn(1, MAX_LISTED))
            val rows = statement.executeQuery()
            // Only read for their tags: no signature needed.
            buildList { while (rows.next()) add(rows.toBodilessEvent(Article.KIND, sig = "")) }
        }
    }

    private fun ResultSet.toBodilessEvent(kind: Int, sig: String): SignedEvent = SignedEvent(
        id = getString("id"),
        pubkey = getString("pubkey"),
        createdAt = getLong("created_at"),
        kind = kind,
        tags = TagsJson.decode(getString("tags")),
        content = "",
        sig = sig,
    )

    private fun now(): OffsetDateTime = clock.instant().atOffset(ZoneOffset.UTC)

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    private fun ResultSet.toProposal(): Proposal = Proposal(
        id = getString("id"),
        event = SignedEvent.fromJson(getString("event")),
        status = statusOf(getString("status")),
        report = getString("report")?.let { reportJson.decodeFromString(ProposalReport.serializer(), it) },
        receivedAt = getObject("received_at", OffsetDateTime::class.java).toEpochSecond(),
        decidedAt = getObject("decided_at", OffsetDateTime::class.java)?.toEpochSecond(),
        reason = getString("decision_reason"),
    )

    companion object {
        const val MAX_REASON_LENGTH: Int = 1_000

        /** At most this many pending proposals are listed or compared at once. */
        const val MAX_LISTED: Int = 2_000

        /** At most this many published demands are compared with a proposal, the newest. */
        const val MAX_PUBLISHED_COMPARED: Int = 5_000

        /** Reports as stored: compact, defaults left out. */
        val reportJson: Json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

        /** The lock taken to count the whole queue; it cannot collide with "revendication_proposal:<hex key>". */
        private const val GLOBAL_LOCK = "revendication_proposal:*"

        private fun statusOf(column: String): ProposalStatus = ProposalStatus.entries.first { it.column == column }

        private const val SELECT =
            "SELECT id, event, status, report::text AS report, received_at, decided_at, decision_reason FROM revendication_proposal"
    }
}
