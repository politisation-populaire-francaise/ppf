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
 * The creation of a new room also passes the [guardrails] of D-132: daily limit per key, rooms of
 * close name on the same territory or a parent one (`409 group_similar`, unless confirmed with a
 * difference), names close to a verified organisation's or to a very active room elsewhere (held
 * for the team, `202 held`; refused to a key seen for less than 7 days when an organisation is concerned).
 *
 * Listing and search go through the table of [GroupIndex], which holds every description.
 */
class GroupDirectory(
    private val events: EventRepository,
    private val index: GroupIndex = GroupIndex(events.dataSource),
    /** Roles, exclusions, edits and removals of the rooms (D-131). */
    val moderation: GroupModeration = GroupModeration(events),
    /** Guardrails of the creation (D-132) and the queue of the creations held for the team. */
    val guardrails: GroupGuardrails = GroupGuardrails(events.dataSource),
) {

    /** What [admitAndStore] did with an event. */
    sealed interface Admission {
        data class Refused(val refusal: Decision.Refused) : Admission

        data class Stored(val outcome: StoreOutcome) : Admission

        /** A new room held for the team (`202 held`); [id] is the event id its author asks about. */
        data class Held(val id: String) : Admission
    }

    /** What the team's decision on a held creation did. */
    sealed interface CreationDecision {
        data object Done : CreationDecision

        data object NotFound : CreationDecision

        data object AlreadyDecided : CreationDecision

        /** The room cannot be published as it is (a room of the same name appeared, the id was taken). */
        data class Refused(val error: String) : CreationDecision
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

    /** Told of every event the server stored: an entry of the organisations' directory renews their names. */
    fun stored(event: SignedEvent) {
        if (event.kind == ORGANISATION_KIND) guardrails.organisationsChanged()
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
                if (described.normalizedName != existing.normalizedName) organisationNameRefusal(described.normalizedName, described.normalizedTerritory, event.pubkey)?.let { return it }
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
            if (existing == null) creationRefusal(described, event)?.let { return it }
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

    /**
     * Why [group] may not take [name] in an edit of its administrators, or null: another room of its
     * territory bears it, or it resembles a verified organisation's name.
     */
    private fun renameRefusal(group: Group, name: String): Decision.Refused? {
        val nameKey = GroupNames.normalize(name)
        val territory = group.normalizedTerritory
        if (nameKey.isEmpty() || territory.isEmpty()) return null
        val duplicate = index.duplicateOf(group.id, nameKey, territory)
        if (duplicate != null) {
            return Decision.Refused(
                409,
                "group_exists",
                "A group with this name already exists on this territory; join it instead of creating another one.",
                existing = duplicate,
            )
        }
        return organisationNameRefusal(nameKey, territory, null)
    }

    /**
     * A room renamed after a verified organisation would bypass the review of its creation: refused,
     * unless [pubkey] is trusted or one of the organisation's keys (null: the rename of an edit, whose
     * author is checked by [GroupModeration] as administrator, not as an organisation).
     */
    private fun organisationNameRefusal(nameKey: String, territoryKey: String, pubkey: String?): Decision.Refused? {
        val resembling = guardrails.organisationsResembling(nameKey, territoryKey, pubkey ?: NO_KEY)
        if (resembling.isEmpty()) return null
        return Decision.Refused(
            403,
            "organisation_name",
            "This name resembles a verified organisation's (${resembling.joinToString()}); to use it, create a new room, which the team will review.",
        )
    }

    /**
     * The guardrails of a new room that refuse it at once (D-132): the daily limit, the rooms of close
     * name on the same territory or a parent one (unless confirmed with a difference), and a name of
     * an organisation from a key seen for less than 7 days.
     */
    private fun creationRefusal(described: Group, event: SignedEvent): Decision.Refused? {
        if (guardrails.createdToday(event.pubkey) >= guardrails.maxPerDay) {
            return Decision.Refused(429, "too_many_groups", "An account creates at most ${guardrails.maxPerDay} rooms a day; try again tomorrow.")
        }
        val confirmed = event.firstTagValue(GroupKinds.SIMILAR_TAG) == GroupKinds.SIMILAR_CONFIRMED
        if (!confirmed) {
            val similar = similarTo(described)
            if (similar.isNotEmpty()) {
                return Decision.Refused(
                    409,
                    "group_similar",
                    "Rooms of a close name already exist on this territory or a parent one; join one of them, or confirm and say how yours differs.",
                    similar = similar,
                )
            }
        }
        if (guardrails.organisationsResembling(described.normalizedName, described.normalizedTerritory, event.pubkey).isNotEmpty() && guardrails.isYoung(event.pubkey)) {
            return Decision.Refused(
                403,
                "account_too_recent",
                "This name resembles a verified organisation's: an account seen for less than ${guardrails.youngAccountDays} days cannot create such a room.",
            )
        }
        return null
    }

    /** The rooms of a name close to [described]'s, on its territory or a parent one (at most [GroupGuardrails.MAX_SIMILAR]). */
    fun similarTo(described: Group): List<SimilarGroup> {
        val territory = described.normalizedTerritory
        if (described.normalizedName.isEmpty() || territory.isEmpty()) return emptyList()
        val words = GroupSimilarity.significantWords(described.normalizedName, territory)
        return index.names(setOf(territory) + guardrails.parents.parentsOf(territory))
            .filter { it.groupId != described.id && GroupSimilarity.similar(words, GroupSimilarity.significantWords(it.nameKey, it.territoryKey)) }
            .sortedWith(compareBy<GroupIndex.NameRow> { if (it.territoryKey == territory) 0 else 1 }.thenBy { it.nameKey }.thenBy { it.groupId })
            .take(GroupGuardrails.MAX_SIMILAR)
            .mapNotNull { row -> find(row.groupId)?.let { SimilarGroup(it.id, it.name, it.territory) } }
    }

    /** Why a new room, allowed by [admit], waits for the team; null when it is published at once. */
    private fun holdCause(described: Group, event: SignedEvent): Pair<HoldCause, List<String>>? {
        val organisations = guardrails.organisationsResembling(described.normalizedName, described.normalizedTerritory, event.pubkey)
        if (organisations.isNotEmpty()) return HoldCause.ORGANISATION to organisations
        val territory = described.normalizedTerritory
        val words = GroupSimilarity.significantWords(described.normalizedName, territory)
        val elsewhere = index.names(setOf(territory) + guardrails.parents.parentsOf(territory), except = true)
            .filter { it.groupId != described.id && GroupSimilarity.similar(words, GroupSimilarity.significantWords(it.nameKey, it.territoryKey)) }
            .take(MAX_ACTIVE_CANDIDATES)
            .filter { index.messageCount(it.groupId, guardrails.activeRoomMessages) >= guardrails.activeRoomMessages }
        if (elsewhere.isEmpty()) return null
        return HoldCause.ACTIVE_ROOM to elsewhere.mapNotNull { row -> find(row.groupId)?.let { "${it.name} (${it.territory})" } }
    }

    /**
     * The team approves a held creation: the description is published exactly as signed, unless its
     * id was taken or a room of the same name appeared on its territory meanwhile.
     */
    fun approveCreation(id: String): CreationDecision = index.exclusively {
        val held = guardrails.creations.find(id) ?: return@exclusively CreationDecision.NotFound
        if (!held.pending) return@exclusively CreationDecision.AlreadyDecided
        val described = Group.from(held.event) ?: return@exclusively CreationDecision.Refused("bad_group_id")
        val existing = findDescription(described.id)
        if (existing != null && existing.event.pubkey != held.event.pubkey) return@exclusively CreationDecision.Refused("group_taken")
        if (findDuplicate(described) != null) return@exclusively CreationDecision.Refused("group_exists")
        events.store(held.event)
        if (guardrails.creations.decide(id, approved = true, reason = null)) CreationDecision.Done else CreationDecision.AlreadyDecided
    }

    /** The team refuses a held creation, with a reason shown to its creator. */
    fun rejectCreation(id: String, reason: String): CreationDecision {
        val held = guardrails.creations.find(id) ?: return CreationDecision.NotFound
        if (!held.pending) return CreationDecision.AlreadyDecided
        return if (guardrails.creations.decide(id, approved = false, reason = reason.trim())) CreationDecision.Done else CreationDecision.AlreadyDecided
    }

    private fun admitThenStore(event: SignedEvent): Admission = when (val decision = admit(event)) {
        is Decision.Refused -> Admission.Refused(decision)
        Decision.Allowed -> {
            val described = if (event.kind == GroupKinds.METADATA) Group.from(event) else null
            val hold = described?.takeIf { findDescription(it.id) == null }?.let { holdCause(it, event) }
            if (hold != null) {
                guardrails.creations.hold(event, hold.first, hold.second)
                Admission.Held(event.id)
            } else {
                Admission.Stored(events.store(event))
            }
        }
    }

    companion object {
        /** Groups answered when the caller gives no limit. */
        const val DEFAULT_LIMIT: Int = 100

        /** Most groups in one answer. */
        const val MAX_LIMIT: Int = 500

        /** The kind of the entries of the organisations' directory (Agir, kind 30023). */
        private const val ORGANISATION_KIND: Int = 30023

        /** The rooms of close name elsewhere whose activity is counted, at most. */
        private const val MAX_ACTIVE_CANDIDATES: Int = 20

        /** No key: the organisation's own keys and the trusted keys are not exempted. */
        private const val NO_KEY: String = ""
    }
}
