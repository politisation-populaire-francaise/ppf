package org.politisationpopulairefrancaise.server.agir.collectives

import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveFormat
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveProblem
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.accounts.AccountTypes
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
import org.politisationpopulairefrancaise.server.agir.AgirCategory
import org.politisationpopulairefrancaise.server.agir.AgirRules
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy.Decision
import org.politisationpopulairefrancaise.server.groups.Group
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.GroupModeration
import org.politisationpopulairefrancaise.server.groups.GroupRole
import org.politisationpopulairefrancaise.server.urne.UrneStore

/**
 * The rules of the collectifs that need the database (M24, decision D-087,
 * docs/integration/agir-collectifs.md). A collectif is a room whose description (kind 39000) carries
 * `["ppf-collective", "1"]`; the pure rules of its tags live in [CollectiveFormat], shared with the
 * application. Called by the group directory for every room description ([descriptionRefusal]),
 * after it checked that the id is free or managed by the same key.
 *
 * - **Creation** of a collectif: the tags are well formed (`400 collective_…`), and the founder is a
 *   person (not a collective account, D-133), not declared compromised for that event (S-146), and
 *   **validated in person** (D-112: level `verifie` or `elu` of the ballot boxes); otherwise
 *   `403 collective_needs_verified` (or `403 account_compromised`). Without the store of the ballot
 *   boxes ([urne] null: no database, no seed, tests), the creation of a collectif is always refused,
 *   never allowed by default.
 * - **New version** of a description (by its founder only: the directory refuses another key with
 *   `409 group_taken`): what makes the collectif never changes (`409 collective_immutable`), a plain
 *   room never becomes a collectif and a collectif never becomes a plain room; the other tags are
 *   checked again.
 */
class CollectiveRules(
    /** The levels of the voters (D-112); null refuses every creation of a collectif. */
    private val urne: UrneStore? = null,
    /** The collective accounts (D-133), which never found a collectif. */
    private val accountTypes: AccountTypes? = null,
    /** The compromised accounts (S-146). */
    private val compromised: CompromisedAccounts? = null,
) {

    /**
     * Why the room description [event] may not enter as a collectif, or null. [existing] is the
     * description stored for the same id (by the same key), null for a new room. A plain room that
     * stays a plain room is never refused here.
     */
    fun descriptionRefusal(existing: SignedEvent?, event: SignedEvent): Decision.Refused? {
        if (event.kind != GroupKinds.METADATA) return null
        val collective = CollectiveFormat.isCollective(event.tags)
        if (existing != null) {
            CollectiveFormat.immutableChange(existing.tags, event.tags)?.let { return immutable(it) }
            return if (collective) shapeRefusal(event) else null
        }
        if (!collective) return null
        return shapeRefusal(event) ?: founderRefusal(event)
    }

    private fun shapeRefusal(event: SignedEvent): Decision.Refused? =
        CollectiveFormat.problem(event.tags)?.let { Decision.Refused(400, it.code, messageOf(it)) }

    /** The founder of a new collectif: a person, not compromised for this event, validated in person. */
    private fun founderRefusal(event: SignedEvent): Decision.Refused? {
        if (compromised?.heldVerdict(event.pubkey, event.createdAt) is CompromisedAccounts.HeldVerdict.CreatedAfter) {
            return CompromisedAccounts.COMPROMISED
        }
        if (accountTypes?.isCollective(event.pubkey) == true) return NOT_A_PERSON
        val level = urne?.account(event.pubkey)?.level
        return if (level != null && level in FOUNDER_LEVELS) null else NEEDS_VERIFIED
    }

    private fun immutable(tag: String) = Decision.Refused(
        409,
        "collective_immutable",
        "A collectif keeps its marker, scope, territory, charter, visibility and way of joining ($tag changed); a plain room never becomes a collectif.",
    )

    private fun messageOf(problem: CollectiveProblem): String = when (problem) {
        CollectiveProblem.BAD_MARKER -> "A collectif carries exactly one [\"${CollectiveFormat.TAG_COLLECTIVE}\", \"${CollectiveFormat.VERSION}\"]."
        CollectiveProblem.BAD_SCOPE -> "A collectif carries one ${CollectiveFormat.TAG_SCOPE}: national, regional, departemental, local, quartier or etablissement."
        CollectiveProblem.BAD_TYPE -> "A collectif carries one ${CollectiveFormat.TAG_TYPE}: collectif, club, comite, assemblee, reseau or autre."
        CollectiveProblem.TOO_MANY_THEMES -> "A collectif has at most ${CollectiveFormat.MAX_THEMES} themes."
        CollectiveProblem.BAD_THEME -> "A theme is lowercase, 1 to ${CollectiveFormat.MAX_THEME_LENGTH} characters, given once."
        CollectiveProblem.BAD_LANGUAGE -> "The language is one l tag with a two-letter ISO 639-1 code."
        CollectiveProblem.BAD_TERRITORY_CODE -> "The territory code is one code of the referential of territories, written as it publishes it."
        CollectiveProblem.BAD_INTERNAL_CHARTER -> "The internal charter is one tag with some text."
        CollectiveProblem.CHARTER_TOO_LONG -> "The internal charter has at most ${CollectiveFormat.MAX_INTERNAL_CHARTER_LENGTH} characters."
        CollectiveProblem.VISIBILITY_UNSUPPORTED -> "Only public collectifs are accepted for now: private and secret ones need end-to-end encrypted rooms."
        CollectiveProblem.JOIN_UNSUPPORTED -> "Only collectifs open to all are accepted for now: joining on request or by invitation is not supported yet."
    }

    companion object {
        /** The levels of an account validated in person (D-112): a certified elected official was too. */
        val FOUNDER_LEVELS: Set<VoteLevel> = setOf(VoteLevel.VERIFIE, VoteLevel.ELU)

        val NEEDS_VERIFIED = Decision.Refused(
            403,
            "collective_needs_verified",
            "Founding a collectif needs an account validated in person; anyone may create a room instead.",
        )

        val NOT_A_PERSON = Decision.Refused(
            403,
            "collective_needs_verified",
            "A collective account does not found a collectif: its founder is a person validated in person.",
        )
    }
}

/**
 * Who may publish a mobilization (AG-49) or a call (AG-50) **in the name of** a collectif, that is
 * carrying `["ppf-collective", <room id>]`: its founder, or a key that holds the role `admin` or
 * `moderator` in its room when the server receives the event (D-131, [GroupModeration]); otherwise
 * `403 collective_role_required`. A tag that names no collectif of this relay is `404
 * collective_not_found`. The usual rules of the Agir space apply afterwards (review, held contents).
 * The shape of the tag is checked before, by [AgirRules].
 */
class CollectiveRoles(
    private val events: EventRepository,
    private val moderation: GroupModeration = GroupModeration(events),
) {
    fun refusal(event: SignedEvent): Decision.Refused? {
        val category = AgirRules.categoryOf(event)
        if (category != AgirCategory.MOBILIZATION && category != AgirCategory.CALL) return null
        if (event.tagValues(CollectiveFormat.TAG_COLLECTIVE).isEmpty()) return null
        val id = CollectiveFormat.collectiveOf(event) ?: return BAD_REFERENCE
        val founder = founderOf(id) ?: return NOT_FOUND
        if (event.pubkey == founder) return null
        val role = moderation.roleOf(id, event.pubkey)
        return if (role == GroupRole.ADMIN || role == GroupRole.MODERATOR) null else ROLE_REQUIRED
    }

    /**
     * The founder of the collectif [id]: the author of the description that counts for this id (the
     * earliest, then the lowest id, like the directory), when it carries the marker; null otherwise.
     */
    fun founderOf(id: String): String? =
        events.query(EventFilter(kinds = listOf(GroupKinds.METADATA), tags = mapOf("d" to listOf(id)), limit = 10))
            .filter { Group.from(it)?.id == id }
            .minWithOrNull(compareBy<SignedEvent>({ it.createdAt }, { it.id }))
            ?.takeIf { CollectiveFormat.isCollective(it.tags) }
            ?.pubkey

    companion object {
        val ROLE_REQUIRED = Decision.Refused(
            403,
            "collective_role_required",
            "Only the founder, an administrator or a moderator of the collectif publishes in its name.",
        )

        val NOT_FOUND = Decision.Refused(404, "collective_not_found", "No collectif with this id on this relay.")

        val BAD_REFERENCE = Decision.Refused(400, "missing_field", CollectiveFormat.TAG_COLLECTIVE)
    }
}
