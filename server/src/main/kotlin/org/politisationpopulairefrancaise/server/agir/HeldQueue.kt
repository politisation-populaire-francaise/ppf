package org.politisationpopulairefrancaise.server.agir

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.publication.HeldState
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import org.politisationpopulairefrancaise.server.revendications.PeriodBudget
import org.slf4j.Logger
import java.sql.Connection
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** The value of the status column (V5__agir.sql), the serial name of [HeldStatus]. */
val HeldStatus.column: String get() = name.lowercase()

/** A content as the queue keeps it (table held_content). */
data class HeldContent(
    val id: String,
    val event: SignedEvent,
    val status: HeldStatus,
    /** Seconds since 1970. */
    val receivedAt: Long,
    val decidedAt: Long?,
    val reason: String?,
) {
    /** What its author may read: never the content nor the key. */
    fun state(): HeldState = HeldState(id = id, status = status, receivedAt = receivedAt, decidedAt = decidedAt, reason = reason)
}

sealed interface HoldOutcome {
    /** Stored as pending; [replaced] is the id of the pending version it replaced, if any. */
    data class Queued(val replaced: String?) : HoldOutcome

    /** This very event already waits for review. */
    data object AlreadyPending : HoldOutcome

    /** This very event was approved and published. */
    data object AlreadyApproved : HoldOutcome

    /** This very event was rejected by the team. */
    data object AlreadyRejected : HoldOutcome

    /** A more recent version of this content is already pending or published. */
    data object Superseded : HoldOutcome

    data object TooManyPendingForKey : HoldOutcome

    data object QueueFull : HoldOutcome

    data class OverDailyBudget(val retryAfterSeconds: Long) : HoldOutcome
}

sealed interface HeldDecision {
    /** Approved; [stored] tells what the events table did with the event. */
    data class Approved(val event: SignedEvent, val stored: StoreOutcome) : HeldDecision

    data object Rejected : HeldDecision

    data object NotFound : HeldDecision

    data class AlreadyDecided(val status: HeldStatus) : HeldDecision
}

/**
 * The generic review queue of the Agir space, in PostgreSQL (table held_content of V5__agir.sql):
 * the contents that wait for the decision of a person before publication. It stores the signed
 * event, a few of its tags, its status and the dates of reception and decision, never anything
 * about the request that brought it. One pending version per address: a newer version replaces the
 * pending one. Approving copies the event into the events table, where the replacement rule of the
 * versions applies as for any content; decided contents are deleted [AgirConfig.decidedRetention]
 * after the decision ([deleteDecidedBefore]).
 *
 * Modelled on the queue of citizen demands (server/revendications/ProposalQueue), which keeps its own
 * table because its report of automatic checks is richer.
 */
class HeldQueue(
    private val dataSource: DataSource,
    private val events: EventRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Queues [event] (addressable, verified and checked by the rules) unless the limits refuse it.
     * [budget] is asked for a token only when the content is about to be stored, and refunded if
     * storing fails. Locks (one per author key, one for the whole queue when a new address comes in)
     * keep the counts exact under concurrent requests.
     */
    fun submit(event: SignedEvent, maxPendingPerKey: Int, maxPending: Int, budget: PeriodBudget): HoldOutcome {
        val address = requireNotNull(EventReplacement.replacementKey(event)) { "Only replaceable contents are held" }
        val identifier = requireNotNull(event.firstTagValue("d")) { "A held content has a d tag" }
        var spent = false
        return try {
            transaction { connection -> submitInTransaction(connection, event, address, identifier, maxPendingPerKey, maxPending, budget) { spent = true } }
        } catch (e: Exception) {
            // Nothing was stored (the transaction was rolled back): the token goes back.
            if (spent) budget.refund(event.pubkey)
            throw e
        }
    }

    private fun submitInTransaction(
        connection: Connection,
        event: SignedEvent,
        address: String,
        identifier: String,
        maxPendingPerKey: Int,
        maxPending: Int,
        budget: PeriodBudget,
        onSpent: () -> Unit,
    ): HoldOutcome {
        lock(connection, "held_content:${event.pubkey}")
        connection.prepareStatement("SELECT status FROM held_content WHERE id = ?").use { statement ->
            statement.setString(1, event.id)
            val rows = statement.executeQuery()
            if (rows.next()) {
                return when (statusOf(rows.getString(1))) {
                    HeldStatus.PENDING -> HoldOutcome.AlreadyPending
                    HeldStatus.APPROVED -> HoldOutcome.AlreadyApproved
                    HeldStatus.REJECTED -> HoldOutcome.AlreadyRejected
                }
            }
        }
        // A newer version is already published: this one would be dropped at approval.
        connection.prepareStatement("SELECT id, created_at FROM events WHERE address = ?").use { statement ->
            statement.setString(1, address)
            val rows = statement.executeQuery()
            if (rows.next() && !EventReplacement.supersedes(event.createdAt, event.id, rows.getLong(2), rows.getString(1))) {
                return HoldOutcome.Superseded
            }
        }
        val previous = connection.prepareStatement("SELECT id, created_at FROM held_content WHERE address = ? AND status = 'pending' FOR UPDATE").use { statement ->
            statement.setString(1, address)
            val rows = statement.executeQuery()
            if (rows.next()) rows.getString(1) to rows.getLong(2) else null
        }
        if (previous != null && !EventReplacement.supersedes(event.createdAt, event.id, previous.second, previous.first)) {
            return HoldOutcome.Superseded
        }
        if (previous == null) {
            if (countPending(connection, event.pubkey) >= maxPendingPerKey) return HoldOutcome.TooManyPendingForKey
            // Taken after the lock of the key, always in this order: no deadlock.
            lock(connection, GLOBAL_LOCK)
            if (countPending(connection, null) >= maxPending) return HoldOutcome.QueueFull
        }
        when (val token = budget.tryAcquire(event.pubkey)) {
            is RateLimiter.Outcome.Limited -> return HoldOutcome.OverDailyBudget(token.retryAfterSeconds)
            RateLimiter.Outcome.Allowed -> onSpent()
        }
        if (previous != null) {
            connection.prepareStatement("DELETE FROM held_content WHERE id = ?").use { delete ->
                delete.setString(1, previous.first)
                delete.executeUpdate()
            }
        }
        connection.prepareStatement(
            """
            INSERT INTO held_content (id, pubkey, kind, identifier, address, created_at, event, tags, status, received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'pending', ?)
            """.trimIndent(),
        ).use { insert ->
            insert.setString(1, event.id)
            insert.setString(2, event.pubkey)
            insert.setInt(3, event.kind)
            insert.setString(4, identifier)
            insert.setString(5, address)
            insert.setLong(6, event.createdAt)
            insert.setString(7, event.toJson())
            insert.setString(8, TagsJson.encode(usefulTags(event)))
            insert.setObject(9, now())
            insert.executeUpdate()
        }
        return HoldOutcome.Queued(previous?.first)
    }

    /** A lock held until the end of the transaction. */
    private fun lock(connection: Connection, name: String) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { lock ->
            lock.setString(1, name)
            lock.executeQuery().close()
        }
    }

    private fun countPending(connection: Connection, pubkey: String?): Int {
        val sql = "SELECT count(*) FROM held_content WHERE status = 'pending'" + if (pubkey != null) " AND pubkey = ?" else ""
        return connection.prepareStatement(sql).use { statement ->
            if (pubkey != null) statement.setString(1, pubkey)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }
    }

    fun find(id: String): HeldContent? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE id = ?").use { statement ->
            statement.setString(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toHeld() else null
        }
    }

    /**
     * Where the contents among [ids] stand, in the order asked, duplicates removed; ids the queue does
     * not know (never held, or deleted after the retention) are left out.
     */
    fun states(ids: List<String>): List<HeldState> {
        val wanted = ids.distinct()
        if (wanted.isEmpty()) return emptyList()
        val found = dataSource.connection.use { connection ->
            connection.prepareStatement("$SELECT WHERE id = ANY(?)").use { statement ->
                statement.setArray(1, connection.createArrayOf("text", wanted.toTypedArray()))
                val rows = statement.executeQuery()
                buildMap { while (rows.next()) rows.toHeld().let { put(it.id, it) } }
            }
        }
        return wanted.mapNotNull { found[it]?.state() }
    }

    /** The pending contents, oldest first, at most [limit]: the review order. */
    fun pending(limit: Int = MAX_LISTED): List<HeldContent> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE status = 'pending' ORDER BY received_at ASC, id ASC LIMIT ?").use { statement ->
            statement.setInt(1, limit.coerceIn(1, MAX_LISTED))
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toHeld()) }
        }
    }

    /**
     * Publishes a pending content: the event goes to the events table exactly as its author signed it
     * (a newer version already published wins, as everywhere), the content is marked approved. Only
     * the team may call this (back-office to come with the moderation, M15: two-factor authentication
     * and an audit log).
     */
    fun approve(id: String): HeldDecision {
        val held = find(id) ?: return HeldDecision.NotFound
        if (held.status != HeldStatus.PENDING) return HeldDecision.AlreadyDecided(held.status)
        // Checked again: what the team approves must be exactly what the author signed.
        check(held.event.verify()) { "A held content does not verify" }
        // Marked first, atomically: two approvals cannot both publish.
        if (!decide(id, HeldStatus.APPROVED, null)) {
            return find(id)?.let { HeldDecision.AlreadyDecided(it.status) } ?: HeldDecision.NotFound
        }
        val stored = try {
            events.store(held.event)
        } catch (e: Exception) {
            // Back to the queue, so that the team can try again.
            runCatching { reopen(id) }
            throw e
        }
        return HeldDecision.Approved(held.event, stored)
    }

    private fun reopen(id: String) = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE held_content SET status = 'pending', decided_at = NULL WHERE id = ? AND status = 'approved'").use { statement ->
            statement.setString(1, id)
            statement.executeUpdate()
        }
    }

    /** Refuses a pending content; [reason] (1 to [MAX_REASON_LENGTH] characters) is shown to its author. Nothing is published. */
    fun reject(id: String, reason: String): HeldDecision {
        val text = reason.trim()
        require(text.isNotEmpty() && text.length <= MAX_REASON_LENGTH) { "A reason of 1 to $MAX_REASON_LENGTH characters is required" }
        val held = find(id) ?: return HeldDecision.NotFound
        if (held.status != HeldStatus.PENDING) return HeldDecision.AlreadyDecided(held.status)
        return if (decide(id, HeldStatus.REJECTED, text)) {
            HeldDecision.Rejected
        } else {
            find(id)?.let { HeldDecision.AlreadyDecided(it.status) } ?: HeldDecision.NotFound
        }
    }

    private fun decide(id: String, status: HeldStatus, reason: String?): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("UPDATE held_content SET status = ?, decided_at = ?, decision_reason = ? WHERE id = ? AND status = 'pending'").use { statement ->
            statement.setString(1, status.column)
            statement.setObject(2, now())
            statement.setString(3, reason)
            statement.setString(4, id)
            statement.executeUpdate() == 1
        }
    }

    /** Deletes the contents decided before [cutoff] and returns how many were deleted; pending ones stay. */
    fun deleteDecidedBefore(cutoff: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM held_content WHERE status <> 'pending' AND decided_at < ?").use { statement ->
            statement.setObject(1, cutoff.atOffset(ZoneOffset.UTC))
            statement.executeUpdate()
        }
    }

    /** Seconds are enough for the author and the team; nothing finer is kept. */
    private fun now(): OffsetDateTime = clock.instant().truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC)

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

    private fun ResultSet.toHeld(): HeldContent = HeldContent(
        id = getString("id"),
        event = SignedEvent.fromJson(getString("event")),
        status = statusOf(getString("status")),
        receivedAt = getObject("received_at", OffsetDateTime::class.java).toEpochSecond(),
        decidedAt = getObject("decided_at", OffsetDateTime::class.java)?.toEpochSecond(),
        reason = getString("decision_reason"),
    )

    companion object {
        const val MAX_REASON_LENGTH: Int = 1_000

        /** At most this many pending contents are listed at once. */
        const val MAX_LISTED: Int = 2_000

        /** The tags copied next to the event, to sort the queue (all also in the event). */
        val USEFUL_TAGS: Set<String> = setOf(
            "d", AgirFormats.TAG_TITLE, AgirFormats.TAG_TYPE, AgirFormats.TAG_CALL_TYPE, AgirFormats.TAG_EVENT_TYPE,
            AgirFormats.TAG_TERRITORY, AgirFormats.TAG_START,
        )

        fun usefulTags(event: SignedEvent): List<List<String>> =
            event.tags.filter { it.size >= 2 && it[0] in USEFUL_TAGS }.map { it.take(2) }.distinctBy { it[0] }

        /** The lock taken to count the whole queue; it cannot collide with "held_content:<hex key>". */
        private const val GLOBAL_LOCK = "held_content:*"

        private fun statusOf(column: String): HeldStatus = HeldStatus.entries.first { it.column == column }

        private const val SELECT = "SELECT id, event, status, received_at, decided_at, decision_reason FROM held_content"
    }
}

/**
 * Deletes the decided contents of the queue once they are older than [retention], counted from the
 * decision. Only counts are logged, never contents, keys or ids.
 */
class HeldMaintenance(
    private val queue: HeldQueue,
    private val retention: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun purgeOnce(): Int = queue.deleteDecidedBefore(clock.instant().minus(retention.toJavaDuration()))

    fun start(scope: CoroutineScope, interval: Duration, log: Logger): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val deleted = purgeOnce()
                if (deleted > 0) log.info("Held contents retention: $deleted decided content(s) deleted.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Held contents maintenance failed (${e::class.simpleName}); next attempt in $interval.")
            }
            delay(interval)
        }
    }
}
