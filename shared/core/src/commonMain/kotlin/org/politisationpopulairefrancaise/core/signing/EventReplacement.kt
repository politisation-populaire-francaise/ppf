package org.politisationpopulairefrancaise.core.signing

/**
 * How a newer event supersedes an older one (NIP-01), shared by the local database and the server
 * so that both keep exactly the same version of a content.
 */
object EventReplacement {
    /**
     * The key under which this event replaces previous ones: `kind:pubkey` for replaceable kinds,
     * `kind:pubkey:d` for addressable kinds, null for regular events (which are never replaced).
     */
    fun replacementKey(event: SignedEvent): String? = when {
        EventKinds.isReplaceable(event.kind) -> "${event.kind}:${event.pubkey}"
        EventKinds.isAddressable(event.kind) -> "${event.kind}:${event.pubkey}:${event.firstTagValue("d") ?: ""}"
        else -> null
    }

    /**
     * Whether an incoming event replaces the stored one sharing its replacement key:
     * the most recent wins, and on equal dates the lowest id wins, so every store converges.
     */
    fun supersedes(incomingCreatedAt: Long, incomingId: String, existingCreatedAt: Long, existingId: String): Boolean =
        incomingCreatedAt > existingCreatedAt || (incomingCreatedAt == existingCreatedAt && incomingId < existingId)

    fun supersedes(incoming: SignedEvent, existing: SignedEvent): Boolean =
        supersedes(incoming.createdAt, incoming.id, existing.createdAt, existing.id)
}
