package org.politisationpopulairefrancaise.server.groups

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy.Decision
import java.text.Collator
import java.util.Locale

/**
 * The groups known to this relay, read from the stored metadata events (kind 39000), and the
 * admission rules that need the database (the pure rules live in PublicationPolicy).
 *
 * A group id belongs to the key that first described it: another key may not redefine it. Should
 * two keys ever claim the same id at the same instant, the earliest event (then the lowest id) wins.
 * One name per territory (D-028): a description whose normalized name and territory are those of
 * another group is refused, with the id of that group to join instead.
 *
 * Ceiling of this version: the directory works on the first [EventFilter.MAX_LIMIT] descriptions
 * by date, loaded and compared in Kotlin; pagination and indexed search will come with the second
 * version of the groups.
 */
class GroupDirectory(private val events: EventRepository) {

    private val collator: Collator = Collator.getInstance(Locale.FRENCH)

    /**
     * Every group, by name (French collation) then id. Ceiling: the first [EventFilter.MAX_LIMIT]
     * descriptions by date; pagination will come with the second version of the groups.
     */
    fun list(): List<Group> = events.query(EventFilter(kinds = listOf(GroupKinds.METADATA), limit = EventFilter.MAX_LIMIT))
        .mapNotNull(Group::from)
        .groupBy { it.id }
        .values
        .map(::canonical)
        .sortedWith(compareBy<Group, String>(collator) { it.name ?: it.id }.thenBy { it.id })

    fun find(id: String): Group? {
        if (!GroupKinds.isValidId(id)) return null
        return events.query(EventFilter(kinds = listOf(GroupKinds.METADATA), tags = mapOf("d" to listOf(id)), limit = 10))
            .mapNotNull(Group::from)
            .filter { it.id == id }
            .takeIf { it.isNotEmpty() }
            ?.let(::canonical)
    }

    /**
     * Another group (another id) with the same normalized name and the same normalized territory
     * as [group]; null when there is none, or when [group] has no usable name or territory. Among
     * several (only possible through a race), the first by name then id.
     */
    fun findDuplicate(group: Group): Group? {
        val name = group.normalizedName
        val territory = group.normalizedTerritory
        if (name.isEmpty() || territory.isEmpty()) return null
        return list().firstOrNull { it.id != group.id && it.normalizedName == name && it.normalizedTerritory == territory }
    }

    private fun canonical(candidates: Collection<Group>): Group =
        candidates.minWith(compareBy<Group>({ it.event.createdAt }, { it.event.id }))

    /**
     * Whether a verified, policy-approved event may enter the relay with respect to groups:
     * - group metadata (39000): the id must be free or already managed by the same key, and no other
     *   group may bear the same name on the same territory (`group_exists`, with the id to join);
     * - companions of the metadata (39001, 39003...): the group must exist and be managed by the same key;
     * - any event carrying `h` tags: every named group must exist and be open.
     * Other events are not about groups and pass. The checks do not rely on the policy having run:
     * every `d` and `h` value is examined.
     */
    fun admit(event: SignedEvent): Decision {
        if (event.kind == GroupKinds.METADATA) {
            val described = Group.from(event)
                ?: return Decision.Refused(400, "bad_group_id", "A group description carries exactly one valid d tag.")
            val existing = find(described.id)
            if (existing != null && existing.event.pubkey != event.pubkey) {
                return Decision.Refused(409, "group_taken", "Another key already manages a group with this id.")
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
        }
        return Decision.Allowed
    }
}
