package org.politisationpopulairefrancaise.core.publication

import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The single door through which every publication leaves the application (cahier des charges,
 * section 3.2). Phase 1 plugs the server behind it; phase 3 adds Nostr relays and static mirrors
 * without touching the code that publishes.
 */
interface PublicationPort {
    suspend fun publish(event: SignedEvent): PublicationResult
}

sealed interface PublicationResult {
    /** The event is accepted and stored (also returned when it was already there). */
    data class Accepted(val id: String) : PublicationResult

    /** The event is refused for a definitive reason; retrying the same event is useless. */
    data class Rejected(val id: String, val reason: RejectionReason) : PublicationResult

    /** The destination cannot be reached right now; the event should be kept and retried. */
    data class Unavailable(val cause: Throwable? = null) : PublicationResult
}

enum class RejectionReason {
    /** The id or the signature does not match the content: forged or corrupted event. */
    INVALID_SIGNATURE,

    /** The destination applies a rule (moderation, size, kind) that this event breaks. */
    REFUSED,
}

/**
 * Keeps accepted events in memory. Used by tests and as the local, offline-first store until the
 * database arrives; it refuses anything whose signature does not check out, like every adapter must.
 */
class InMemoryPublicationPort : PublicationPort {
    private val stored = LinkedHashMap<String, SignedEvent>()

    val events: List<SignedEvent> get() = stored.values.toList()

    override suspend fun publish(event: SignedEvent): PublicationResult {
        if (!event.verify()) return PublicationResult.Rejected(event.id, RejectionReason.INVALID_SIGNATURE)
        stored.getOrPut(event.id) { event }
        return PublicationResult.Accepted(event.id)
    }
}
