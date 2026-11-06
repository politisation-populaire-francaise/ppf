package org.politisationpopulairefrancaise.core.publication

import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupMessage
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.EventReplacement
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
    /**
     * The event is accepted and stored (also returned when it was already there). [heldForReview]
     * is true when the destination keeps it for a review by the team before publishing it (citizen
     * demands, decision of the owner of 2026-11-05): it is safe on the server, not yet public.
     */
    data class Accepted(val id: String, val heldForReview: Boolean = false) : PublicationResult

    /**
     * The event is refused for a definitive reason; retrying the same event is useless.
     * [existing] names what the refusal points at when the destination says so: the room to join
     * for [RejectionReason.GROUP_EXISTS], the taken id for [RejectionReason.GROUP_TAKEN].
     */
    data class Rejected(val id: String, val reason: RejectionReason, val existing: String? = null) : PublicationResult

    /** The destination cannot be reached right now; the event should be kept and retried. */
    data class Unavailable(val cause: Throwable? = null) : PublicationResult
}

enum class RejectionReason {
    /** The id or the signature does not match the content: forged or corrupted event. */
    INVALID_SIGNATURE,

    /** The destination applies a rule (moderation, size, kind) that this event breaks. */
    REFUSED,

    // Rooms of collectives (NIP-29, decision D-028).
    /** A room with the same name already exists on the same territory; `existing` is its id, to join. */
    GROUP_EXISTS,

    /** The room id is already described by another key; `existing` is that id. */
    GROUP_TAKEN,

    /** The room refuses messages (closed or restricted). */
    GROUP_CLOSED,

    /** The room named by the `h` tag does not exist. */
    GROUP_NOT_FOUND,

    /** A room description without the required `ppf-territory` tag. */
    MISSING_TERRITORY,

    /** A room description without the required `ppf-charter` tag. */
    MISSING_CHARTER,

    /** A room description without a `name`. */
    MISSING_NAME,
}

/**
 * Keeps accepted events in memory and serves them back like the server would (newest first, same
 * filters, same rules for the rooms of collectives). Used by tests and as the local, offline-first
 * store until the database arrives; it refuses anything whose signature does not check out, like
 * every adapter must.
 */
class InMemoryPublicationPort : RemoteEvents {
    private val stored = LinkedHashMap<String, SignedEvent>()

    val events: List<SignedEvent> get() = stored.values.toList()

    override suspend fun publish(event: SignedEvent): PublicationResult {
        if (!event.verify()) return PublicationResult.Rejected(event.id, RejectionReason.INVALID_SIGNATURE)
        if (event.id in stored) return PublicationResult.Accepted(event.id)
        checkRoomRules(event)?.let { return it }
        if (event.kind == EventKinds.GROUP_METADATA) {
            // Addressable: the author's newer description replaces the older one, as on the server;
            // an older version is acknowledged ("superseded") without being stored.
            val address = EventReplacement.replacementKey(event)
            stored.values.firstOrNull { EventReplacement.replacementKey(it) == address }?.let { existing ->
                if (!EventReplacement.supersedes(event, existing)) return PublicationResult.Accepted(event.id)
                stored.remove(existing.id)
            }
        }
        stored[event.id] = event
        return PublicationResult.Accepted(event.id)
    }

    /**
     * The server's rules for rooms (docs/FORMATS.md, section 5): a description carries exactly one
     * valid `d`, a territory and the charter, no unsupported flag, and is refused when its id belongs
     * to another key or when a room of the same name exists on the same territory; any event
     * carrying an `h` tag names exactly one existing, open room.
     */
    private fun checkRoomRules(event: SignedEvent): PublicationResult? {
        fun reject(reason: RejectionReason, existing: String? = null) = PublicationResult.Rejected(event.id, reason, existing)
        if (event.kind == EventKinds.GROUP_METADATA) {
            val ids = event.tagValues(Group.TAG_ID)
            if (ids.size != 1 || !Group.isValidId(ids[0])) return reject(RejectionReason.REFUSED)
            val flags = event.tags.filter { it.size == 1 }.map { it[0] }
            if ("private" in flags || "hidden" in flags) return reject(RejectionReason.REFUSED)
            val group = Group.from(event) ?: return reject(RejectionReason.REFUSED)
            if (event.firstTagValue(Group.TAG_NAME).isNullOrBlank()) return reject(RejectionReason.MISSING_NAME)
            if (group.territory == null) return reject(RejectionReason.MISSING_TERRITORY)
            if (group.charter == null) return reject(RejectionReason.MISSING_CHARTER)
            val rooms = rooms()
            rooms.firstOrNull { it.id == group.id && it.event.pubkey != event.pubkey }?.let { return reject(RejectionReason.GROUP_TAKEN, it.id) }
            rooms.firstOrNull { it.id != group.id && Group.sameNameAndTerritory(it, group) }?.let { return reject(RejectionReason.GROUP_EXISTS, it.id) }
            return null
        }
        val named = event.tagValues(GroupMessage.TAG_GROUP)
        if (named.isEmpty()) return if (event.kind == EventKinds.GROUP_CHAT) reject(RejectionReason.REFUSED) else null
        if (named.size != 1) return reject(RejectionReason.REFUSED)
        val room = rooms().firstOrNull { it.id == named[0] } ?: return reject(RejectionReason.GROUP_NOT_FOUND)
        if (!room.open) return reject(RejectionReason.GROUP_CLOSED, room.id)
        return null
    }

    private fun rooms(): List<Group> = stored.values.filter { it.kind == EventKinds.GROUP_METADATA }.mapNotNull(Group::from)

    override suspend fun fetch(filter: RemoteFilter): FetchResult = fetchByTags(emptyList(), filter)

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult {
        val wanted = tags.groupBy({ it.name }, { it.value })
        val matching = stored.values.asSequence()
            .filter { filter.kinds.isEmpty() || it.kind in filter.kinds }
            .filter { filter.authors.isEmpty() || it.pubkey in filter.authors }
            .filter { filter.identifiers.isEmpty() || it.firstTagValue("d") in filter.identifiers }
            .filter { event -> filter.since?.let { event.createdAt >= it } ?: true }
            .filter { event -> filter.until?.let { event.createdAt <= it } ?: true }
            .filter { event -> wanted.all { (name, values) -> event.tagValues(name).any { it in values } } }
            .sortedWith(compareByDescending<SignedEvent> { it.createdAt }.thenBy { it.id })
            .take(filter.limit.coerceAtLeast(0))
            .toList()
        return FetchResult.Events(matching)
    }

    /** The rooms described so far, filtered and sorted like `GET /api/v1/groups`. */
    override suspend fun fetchGroups(query: String?, territory: String?, limit: Int): GroupsResult {
        val text = query?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        val place = territory?.trim()?.takeIf { it.isNotEmpty() }?.let(Group::slugify)
        val groups = rooms()
            .filter { room ->
                text == null ||
                    room.name.lowercase().contains(text) ||
                    room.about?.lowercase()?.contains(text) == true ||
                    room.territory?.lowercase()?.contains(text) == true
            }
            .filter { room -> place == null || room.territory?.let(Group::slugify) == place }
            .sortedWith(compareBy<Group> { it.displayName.lowercase() }.thenBy { it.id })
            .take(limit.coerceIn(0, MAX_GROUPS))
        return GroupsResult.Groups(groups)
    }

    companion object {
        /** The server never lists more rooms than this in one answer. */
        const val MAX_GROUPS: Int = 500
    }
}
