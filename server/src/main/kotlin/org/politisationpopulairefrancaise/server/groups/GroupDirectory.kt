package org.politisationpopulairefrancaise.server.groups

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy.Decision
import org.politisationpopulairefrancaise.server.events.StoreOutcome

/**
 * The groups known to this relay, read from the stored metadata events (kind 39000), and the
 * admission rules that need the database (the pure rules live in PublicationPolicy).
 *
 * A group id belongs to the key that first described it: another key may not redefine it. Should
 * two keys ever have described the same id (through a direct store), the earliest event (then the
 * lowest id) wins. One name per territory (D-028): a description whose normalized name and
 * territory are those of another group is refused, with the id of that group to join instead.
 * Both checks and the storage of a description happen one at a time ([admitAndStore]).
 *
 * Listing and search go through the table of [GroupIndex], which holds every description.
 */
class GroupDirectory(
    private val events: EventRepository,
    private val index: GroupIndex = GroupIndex(events.dataSource),
    /** Roles, exclusions, edits and removals of the rooms (D-131). */
    val moderation: GroupModeration = GroupModeration(events),
) {

    /** What [admitAndStore] did with an event. */
    sealed interface Admission {
        data class Refused(val refusal: Decision.Refused) : Admission

        data class Stored(val outcome: StoreOutcome) : Admission
    }

    /** The first groups by name (accents and case ignored) then id; see [search]. */
    fun list(limit: Int = DEFAULT_LIMIT): List<Group> = search(null, null, limit)

    /**
     * The groups whose name, territory or description contains [query] and whose territory is
     * [territory], both compared once normalized ([GroupNames]: accents, case and punctuation
     * ignored); a null, blank or punctuation-only value does not filter. Sorted by normalized name
     * then id, at most [limit] (bounded to 1..[MAX_LIMIT]).
     */
    fun search(query: String?, territory: String?, limit: Int = DEFAULT_LIMIT): List<Group> {
        val text = query?.let(GroupNames::normalize)?.takeIf { it.isNotEmpty() }
        val place = territory?.let(GroupNames::normalize)?.takeIf { it.isNotEmpty() }
        return withEdits(index.search(text, place, limit.coerceIn(1, MAX_LIMIT)).mapNotNull(Group::from))
    }

    /** [groups] with the name and presentation in force after the edits of their administrators. */
    private fun withEdits(groups: List<Group>): List<Group> {
        val edits = moderation.edits(groups.map { it.id })
        return if (edits.isEmpty()) groups else groups.map { group -> edits[group.id]?.let(group::withEdit) ?: group }
    }

    fun find(id: String): Group? {
        if (!GroupKinds.isValidId(id)) return null
        return events.query(EventFilter(kinds = listOf(GroupKinds.METADATA), tags = mapOf("d" to listOf(id)), limit = 10))
            .mapNotNull(Group::from)
            .filter { it.id == id }
            .takeIf { it.isNotEmpty() }
            ?.let(::canonical)
            ?.let { group -> moderation.edits(listOf(group.id))[group.id]?.let(group::withEdit) ?: group }
    }

    /** The description that counts for [id], without the edits of its administrators. */
    private fun findDescription(id: String): Group? {
        if (!GroupKinds.isValidId(id)) return null
        return events.query(EventFilter(kinds = listOf(GroupKinds.METADATA), tags = mapOf("d" to listOf(id)), limit = 10))
            .mapNotNull(Group::from)
            .filter { it.id == id }
            .takeIf { it.isNotEmpty() }
            ?.let(::canonical)
    }

    /**
     * Another group (another id) with the same normalized name and the same normalized territory
     * as [group]; null when there is none, or when [group] has no usable name or territory.
     */
    fun findDuplicate(group: Group): Group? {
        val name = group.normalizedName
        val territory = group.normalizedTerritory
        if (name.isEmpty() || territory.isEmpty()) return null
        return index.duplicateOf(group.id, name, territory)?.let(::find)
    }

    /** Descriptions stored before the directory table existed enter it; returns how many. */
    fun indexMissing(): Int = index.indexMissing()

    private fun canonical(candidates: Collection<Group>): Group =
        candidates.minWith(compareBy<Group>({ it.event.createdAt }, { it.event.id }))

    /**
     * Whether a verified, policy-approved event may enter the relay with respect to groups:
     * - group metadata (39000): the id must be free or already managed by the same key, and no other
     *   group may bear the same name on the same territory (`group_exists`, with the id to join);
     * - companions of the metadata (39001, 39003...): the group must exist and be managed by the same key;
     * - any event carrying `h` tags: every named group must exist and be open.
     * Other events are not about groups and pass. The checks do not rely on the policy having run:
     * every `d` and `h` value is examined. To store the event as well, use [admitAndStore].
     */
    fun admit(event: SignedEvent): Decision {
        if (event.kind == GroupKinds.METADATA) {
            val described = Group.from(event)
                ?: return Decision.Refused(400, "bad_group_id", "A group description carries exactly one valid d tag.")
            val existing = findDescription(described.id)
            if (existing != null && existing.event.pubkey != event.pubkey) {
                return Decision.Refused(409, "group_taken", "Another key already manages a group with this id.")
            }
            if (existing != null) {
                // A new version by the creator (D-131): only while they are an administrator, and the
                // territory never changes (otherwise the refusal of duplicates would be bypassed).
                if (moderation.roleOf(described.id, event.pubkey) != GroupRole.ADMIN) {
                    return Decision.Refused(403, "not_allowed", "Only an administrator describes the room; the creator who gave up the role no longer does.")
                }
                if (described.normalizedTerritory != existing.normalizedTerritory) {
                    return Decision.Refused(400, "territory_fixed", "The territory of a room never changes; create another room for another territory.")
                }
            }
            val duplicate = findDuplicate(described)
            if (duplicate != null) {
                return Decision.Refused(
                    409,
                    "group_exists",
                    "A group with this name already exists on this territory; join it instead of creating another one.",
                    existing = duplicate.id,
                )
            }
            return Decision.Allowed
        }
        if (GroupKinds.isGroupMetadata(event.kind)) {
            val group = event.tagValues("d").singleOrNull()?.let(::find)
                ?: return Decision.Refused(404, "group_not_found", "No group with this id on this relay.")
            return if (group.event.pubkey == event.pubkey) {
                Decision.Allowed
            } else {
                Decision.Refused(403, "not_allowed", "Only the key managing the group describes it.")
            }
        }
        for (groupId in event.tagValues(GroupKinds.GROUP_TAG)) {
            val group = find(groupId) ?: return Decision.Refused(404, "group_not_found", "No group with this id on this relay.")
            if (!group.isOpen) {
                return Decision.Refused(403, "group_closed", "This group only accepts messages from its members; membership is not supported yet.")
            }
            if (event.kind == GroupKinds.CHAT_MESSAGE && moderation.isBanned(group.id, event.pubkey)) {
                return Decision.Refused(403, "banned", "This account was excluded from this room by its moderators; the reason is in the room's journal.")
            }
        }
        return Decision.Allowed
    }

    /**
     * [admit], then the storage of the event if it is allowed. A group description is checked and
     * stored under the lock of [GroupIndex.exclusively]: two rooms of the same name on the same
     * territory, published at the same instant, cannot both pass; the second one sees the first.
     */
    fun admitAndStore(event: SignedEvent): Admission = when {
        event.kind == GroupKinds.METADATA -> index.exclusively { admitThenStore(event) }
        GroupKinds.isAcceptedModeration(event.kind) -> when (val decision = admit(event)) {
            is Decision.Refused -> Admission.Refused(decision)
            Decision.Allowed -> {
                val group = event.tagValues(GroupKinds.GROUP_TAG).single().let(::find)
                    ?: return Admission.Refused(Decision.Refused(404, "group_not_found", "No group with this id on this relay."))
                moderation.admitAndStore(event, group) { _, name -> renameRefusal(group, name) }
            }
        }
        else -> admitThenStore(event)
    }

    /** Why [group] may not take [name] in an edit of its administrators, or null: another room of its territory bears it. */
    private fun renameRefusal(group: Group, name: String): Decision.Refused? {
        val nameKey = GroupNames.normalize(name)
        val territory = group.normalizedTerritory
        if (nameKey.isEmpty() || territory.isEmpty()) return null
        val duplicate = index.duplicateOf(group.id, nameKey, territory) ?: return null
        return Decision.Refused(
            409,
            "group_exists",
            "A group with this name already exists on this territory; join it instead of creating another one.",
            existing = duplicate,
        )
    }

    private fun admitThenStore(event: SignedEvent): Admission = when (val decision = admit(event)) {
        is Decision.Refused -> Admission.Refused(decision)
        Decision.Allowed -> Admission.Stored(events.store(event))
    }

    companion object {
        /** Groups answered when the caller gives no limit. */
        const val DEFAULT_LIMIT: Int = 100

        /** Most groups in one answer. */
        const val MAX_LIMIT: Int = 500
    }
}
