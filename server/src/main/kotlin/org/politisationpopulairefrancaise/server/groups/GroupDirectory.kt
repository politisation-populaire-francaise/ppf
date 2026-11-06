package org.politisationpopulairefrancaise.server.groups

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy.Decision

/**
 * The groups known to this relay, read from the stored metadata events (kind 39000), and the
 * admission rules that need the database (the pure rules live in PublicationPolicy).
 *
 * A group id belongs to the key that first described it: another key may not redefine it. Should
 * two keys ever claim the same id at the same instant, the earliest event (then the lowest id) wins.
 */
class GroupDirectory(private val events: EventRepository) {

    /** Every group, by name then id. */
    fun list(): List<Group> = events.query(EventFilter(kinds = listOf(GroupKinds.METADATA), limit = EventFilter.MAX_LIMIT))
        .mapNotNull(Group::from)
        .groupBy { it.id }
        .values
        .map(::canonical)
        .sortedWith(compareBy<Group>({ it.name?.lowercase() ?: it.id }, { it.id }))

    fun find(id: String): Group? {
        if (!GroupKinds.isValidId(id)) return null
        return events.query(EventFilter(kinds = listOf(GroupKinds.METADATA), tags = mapOf("d" to listOf(id)), limit = 10))
            .mapNotNull(Group::from)
            .takeIf { it.isNotEmpty() }
            ?.let(::canonical)
    }

    private fun canonical(candidates: Collection<Group>): Group =
        candidates.minWith(compareBy<Group>({ it.event.createdAt }, { it.event.id }))

    /**
     * Whether a verified, policy-approved event may enter the relay with respect to groups:
     * - group metadata (39000): the id must be free or already managed by the same key;
     * - companions of the metadata (39001, 39003...): the group must exist and be managed by the same key;
     * - any event carrying an `h` tag: the group must exist and be open.
     * Other events are not about groups and pass.
     */
    fun admit(event: SignedEvent): Decision {
        if (event.kind == GroupKinds.METADATA) {
            val id = event.firstTagValue("d") ?: return Decision.Allowed
            val existing = find(id) ?: return Decision.Allowed
            return if (existing.event.pubkey == event.pubkey) {
                Decision.Allowed
            } else {
                Decision.Refused(409, "group_taken", "Another key already manages a group with this id.")
            }
        }
        if (GroupKinds.isGroupMetadata(event.kind)) {
            val group = event.firstTagValue("d")?.let(::find)
                ?: return Decision.Refused(404, "group_not_found", "No group with this id on this relay.")
            return if (group.event.pubkey == event.pubkey) {
                Decision.Allowed
            } else {
                Decision.Refused(403, "not_allowed", "Only the key managing the group describes it.")
            }
        }
        val groupId = event.firstTagValue(GroupKinds.GROUP_TAG) ?: return Decision.Allowed
        val group = find(groupId) ?: return Decision.Refused(404, "group_not_found", "No group with this id on this relay.")
        if (!group.isOpen) {
            return Decision.Refused(403, "group_closed", "This group only accepts messages from its members; membership is not supported yet.")
        }
        return Decision.Allowed
    }
}
