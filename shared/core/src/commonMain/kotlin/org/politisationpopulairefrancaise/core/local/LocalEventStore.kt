package org.politisationpopulairefrancaise.core.local

import org.politisationpopulairefrancaise.core.db.Event
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class LocalStoreOutcome { STORED, DUPLICATE, SUPERSEDED }

/**
 * The device's copy of signed events: contents received from the server (readable offline) and the
 * user's own publications until the server accepts them (`pending`). Replacement of older versions
 * follows [EventReplacement], exactly like the server, so both sides converge.
 */
@OptIn(ExperimentalAtomicApi::class)
class LocalEventStore(
    private val database: PpfDatabase,
    private val sendingTime: TimeSource = TimeSource.Monotonic,
    // Last, so that `LocalEventStore(database) { now }` keeps setting the clock.
    private val clock: () -> Long = ::currentEpochSeconds,
) {

    private val queries get() = database.eventQueries

    /** The publications a screen stored to send them itself, with when it did (memory only). */
    private val claims = AtomicReference<Map<String, TimeMark>>(emptyMap())

    /**
     * Stores [event] as pending for the screen that sends it right after: for [SENDING_GRACE], the
     * synchronizations leave it alone ([pendingToSend]), so that it does not leave twice (the
     * synchronization used to send it again while the screen was sending it, 11 November 2026). If
     * the screen could not send it (no network), a later synchronization takes it after that delay.
     */
    fun storeSending(event: SignedEvent): LocalStoreOutcome {
        val now = sendingTime.markNow()
        while (true) {
            val current = claims.load()
            if (claims.compareAndSet(current, current + (event.id to now))) break
        }
        return store(event, pending = true)
    }

    /**
     * The screen's own attempt at sending [event], stored by [storeSending]: when it fails (no network, refusal,
     * error), the event is handed back to the synchronizations at once; when it succeeds, it stays the screen's
     * until [markPublished].
     */
    suspend fun sending(event: SignedEvent, publish: suspend () -> PublicationResult): PublicationResult {
        val result = try {
            publish()
        } catch (failure: Throwable) {
            release(event.id)
            throw failure
        }
        if (result !is PublicationResult.Accepted) release(event.id)
        return result
    }

    private fun release(id: String) {
        while (true) {
            val current = claims.load()
            if (id !in current || claims.compareAndSet(current, current - id)) return
        }
    }

    /** The pending publications that a synchronization may send: those no screen is sending now, oldest first. */
    fun pendingToSend(): List<SignedEvent> {
        val current = claims.load()
        val fresh = current.filterValues { it.elapsedNow() < SENDING_GRACE }
        if (fresh.size != current.size) claims.compareAndSet(current, fresh)
        return pending().filter { it.id !in fresh }
    }

    /** Stores a verified event; the caller checks the signature first. */
    fun store(event: SignedEvent, pending: Boolean = false): LocalStoreOutcome = database.transactionWithResult {
        if (queries.findById(event.id).executeAsOneOrNull() != null) return@transactionWithResult LocalStoreOutcome.DUPLICATE
        val address = EventReplacement.replacementKey(event)
        if (address != null) {
            queries.findByAddress(address).executeAsOneOrNull()?.let { existing ->
                if (!EventReplacement.supersedes(event.createdAt, event.id, existing.created_at, existing.id)) {
                    return@transactionWithResult LocalStoreOutcome.SUPERSEDED
                }
                queries.deleteTagsOf(existing.id)
                queries.deleteById(existing.id)
            }
        }
        queries.insert(
            id = event.id,
            pubkey = event.pubkey,
            created_at = event.createdAt,
            kind = event.kind.toLong(),
            tags = TagsJson.encode(event.tags),
            content = event.content,
            sig = event.sig,
            address = address,
            received_at = clock(),
            pending = pending,
        )
        event.tags.filter { it.size >= 2 }.forEach { tag -> queries.insertTag(event.id, tag[0], tag[1]) }
        LocalStoreOutcome.STORED
    }

    fun find(id: String): SignedEvent? = queries.findById(id).executeAsOneOrNull()?.toSignedEvent()

    /** Newest first; all kinds when [kinds] is empty. */
    fun recent(kinds: List<Int> = emptyList(), limit: Int = 50): List<SignedEvent> =
        if (kinds.isEmpty()) {
            queries.selectRecent(limit.toLong()).executeAsList().map { it.toSignedEvent() }
        } else {
            queries.selectByKinds(kinds.map(Int::toLong), limit.toLong()).executeAsList().map { it.toSignedEvent() }
        }

    /** Events of the given kinds carrying the tag [name] = [value] (for example `t` = "droits"). */
    fun byTag(kinds: List<Int>, name: String, value: String, limit: Int = 50): List<SignedEvent> =
        queries.selectByKindsAndTag(kinds.map(Int::toLong), name, value, limit.toLong()).executeAsList().map { it.toSignedEvent() }

    /** The user's publications not yet accepted by the server, oldest first. */
    fun pending(): List<SignedEvent> = queries.selectPending().executeAsList().map { it.toSignedEvent() }

    fun markPublished(id: String) {
        queries.markPublished(id)
        release(id)
    }

    fun delete(id: String) = database.transaction {
        queries.deleteTagsOf(id)
        queries.deleteById(id)
    }

    /** Date of the most recent server-accepted event of these kinds: the cursor of the next pull. */
    fun latestCreatedAt(kinds: List<Int>): Long? =
        queries.latestCreatedAt(kinds.map(Int::toLong)).executeAsOneOrNull()?.MAX

    /** Where the synchronization pass named [name] stopped, or null if it never ran. */
    fun cursor(name: String): Long? = queries.selectCursor(name).executeAsOneOrNull()

    fun setCursor(name: String, value: Long) = queries.upsertCursor(name, value)

    /** The current time of the store's clock, seconds since 1970. */
    fun now(): Long = clock()

    fun count(): Long = queries.countAll().executeAsOne()

    private fun Event.toSignedEvent(): SignedEvent = SignedEvent(
        id = id,
        pubkey = pubkey,
        createdAt = created_at,
        kind = kind.toInt(),
        tags = TagsJson.decode(tags),
        content = content,
        sig = sig,
    )

    companion object {
        /** How long a publication stored by its screen is left to it before a synchronization may send it. */
        val SENDING_GRACE: Duration = 60.seconds
    }
}
