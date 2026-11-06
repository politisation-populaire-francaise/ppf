package org.politisationpopulairefrancaise.core.local

import org.politisationpopulairefrancaise.core.db.Event
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

enum class LocalStoreOutcome { STORED, DUPLICATE, SUPERSEDED }

/**
 * The device's copy of signed events: contents received from the server (readable offline) and the
 * user's own publications until the server accepts them (`pending`). Replacement of older versions
 * follows [EventReplacement], exactly like the server, so both sides converge.
 */
class LocalEventStore(private val database: PpfDatabase, private val clock: () -> Long = ::currentEpochSeconds) {

    private val queries get() = database.eventQueries

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

    fun markPublished(id: String) = queries.markPublished(id)

    fun delete(id: String) = database.transaction {
        queries.deleteTagsOf(id)
        queries.deleteById(id)
    }

    /** Date of the most recent server-accepted event of these kinds: the cursor of the next pull. */
    fun latestCreatedAt(kinds: List<Int>): Long? =
        queries.latestCreatedAt(kinds.map(Int::toLong)).executeAsOneOrNull()?.MAX

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
}
