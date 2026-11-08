package org.politisationpopulairefrancaise.server.events

import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.server.signing.PlatformKeys
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.agir.AgirRules
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.GroupNames
import org.politisationpopulairefrancaise.server.groups.SimilarGroup
import org.politisationpopulairefrancaise.server.relay.RelayKinds

/**
 * Who may publish what, without looking at the database (the rules that need it live in
 * GroupDirectory). Phase 1 rules:
 *
 * - ephemeral events are never stored; events with more than [MAX_TAGS] tags are refused;
 * - editorial contents (kind 30023) are accepted only from the keys listed in the environment
 *   variable PPF_EDITOR_KEYS (hex or "npub1…" keys separated by commas), or when signed by a working key
 *   of the platform certified by the root for a use whose form the content has ([platformKeys], D-129,
 *   revocations included, at the content's own date); a demand from another key
 *   ([isCitizenDemand]) is refused here too, and the API holds it for review by a person instead
 *   (server/revendications); a call of the Agir space (`ppf-type` = appel) is open to every key;
 * - mobilizations (kind 31923), calls and directory entries of the Agir space must have the shape of
 *   the contract (server/agir/AgirRules); who publishes them directly is decided by AgirSpace; so
 *   must the responses to a mobilization (kind 31925, NIP-52, contract version 2), published from any key;
 * - a group description (kind 39000, NIP-29) is accepted from any key (D-028: creating a group is
 *   open to every account) provided it carries exactly one valid `d` id, a non-empty `name`, a
 *   non-empty territory (`ppf-territory`), the accepted charter version (`ppf-charter` = 1.0) and
 *   none of the flags the relay cannot honour yet (`private`, `hidden`: reading would have to be
 *   restricted to members); `about` and `picture` are optional; it stays small (at most
 *   [GroupKinds.MAX_DESCRIPTION_BYTES] serialized, name and territory of at most 100 characters);
 * - the companions of a description (39001 admins, 39003 roles, 39004, 39005) stay reserved to the
 *   editorial keys until roles exist: an admins list published by anyone would tie keys to a group;
 * - member lists (39002) are refused: the server never holds nor publishes one (D-027);
 * - of the group membership and moderation events (9000 to 9022), only put-user (9000),
 *   remove-user (9001), edit-metadata (9002) and delete-event (9005) are accepted (D-131), with the
 *   shape of [moderationShape]; who may send them is decided by GroupModeration, from the state of
 *   the room; the others (invitations, join and leave requests, deletion of a room) stay refused;
 * - an event sent to a group names exactly one group (`h`); a chat message (kind 9) must name one;
 * - an encrypted envelope (kind 1059) names exactly one recipient (`p`), otherwise it could never
 *   be fetched and would only take space;
 * - everything else (notes, application data such as the mailbox settings of kind 30078...) is
 *   accepted from any key.
 *
 * Anti-spam: the rate limits are applied by the API before this policy; the proof of work is
 * still to come.
 */
class PublicationPolicy(private val editors: Set<String>, private val platformKeys: PlatformKeys = PlatformKeys.NONE) {

    sealed interface Decision {
        data object Allowed : Decision

        /**
         * [existing] is set with the error `group_exists` only: the id of the group the client should
         * join instead of creating a duplicate; [similar] with the error `group_similar` only: the
         * rooms whose name is close, on the same territory or a parent one (D-132).
         */
        data class Refused(
            val status: Int,
            val error: String,
            val message: String,
            val existing: String? = null,
            val similar: List<SimilarGroup> = emptyList(),
        ) : Decision
    }

    /**
     * A key of the team: an editorial key (PPF_EDITOR_KEYS) or a working key of the platform certified by the
     * root, whatever its use and even revoked since (its contents stay the association's own: out of the
     * legal register, never a citizen's demand).
     */
    fun isEditor(pubkeyHex: String): Boolean = pubkeyHex in editors || platformKeys.trust().certificate(pubkeyHex) != null

    /** Whether [event] is a content of the platform: signed by a key certified for a use whose form it has, valid at its date. */
    fun isPlatformContent(event: SignedEvent): Boolean {
        val trust = platformKeys.trust()
        return trust.certificate(event.pubkey) != null && SigningUse.entries.any { trust.isPlatformContent(event, it) }
    }

    /** The same policy, with the working keys of the platform known by [keys]. */
    fun withPlatformKeys(keys: PlatformKeys): PublicationPolicy = PublicationPolicy(editors, keys)

    /** The public keys of the editorial team (public by nature: they sign every published content), for the open export. */
    fun editorKeys(): Set<String> = editors

    /** A demand of the wall (kind 30023, `ppf-type` = revendication) signed by a key outside the editorial team. */
    fun isCitizenDemand(event: SignedEvent): Boolean =
        event.kind == Article.KIND && !isEditor(event.pubkey) && event.firstTagValue(Article.TAG_TYPE) == ArticleType.REVENDICATION.tagValue

    fun check(event: SignedEvent): Decision = when {
        EventKinds.isEphemeral(event.kind) ->
            Decision.Refused(400, "ephemeral_kind", "Ephemeral events are not stored.")
        event.tags.size > MAX_TAGS ->
            Decision.Refused(400, "too_many_tags", "An event carries at most $MAX_TAGS tags.")
        // Calls of the Agir space are open to every key (AgirSpace then publishes or holds them).
        event.kind == Article.KIND && event.pubkey !in editors && !isPlatformContent(event) && !AgirRules.isCall(event) ->
            Decision.Refused(403, "not_allowed", "Only the editorial team publishes editorial contents.")
        // The shape of mobilizations, calls and directory entries (docs/integration/agir-contrat.md).
        AgirRules.check(event) != null -> AgirRules.check(event)!!
        event.kind == GroupKinds.MEMBERS ->
            Decision.Refused(403, "members_list_refused", "Member lists are never published nor stored on this relay.")
        GroupKinds.isMembershipOrModeration(event.kind) && !GroupKinds.isAcceptedModeration(event.kind) ->
            Decision.Refused(400, "unsupported_kind", "Of the group membership and moderation events, only put-user, remove-user, edit-metadata and delete-event are accepted.")
        GroupKinds.isAcceptedModeration(event.kind) && moderationShape(event) != null -> moderationShape(event)!!
        GroupKinds.isGroupMetadata(event.kind) && event.kind != GroupKinds.METADATA && !isEditor(event.pubkey) ->
            Decision.Refused(403, "not_allowed", "Only authorized keys publish the companions of a group description (admins, roles) for now.")
        GroupKinds.isGroupMetadata(event.kind) && !hasExactlyOneValidGroupId(event, "d") ->
            Decision.Refused(400, "bad_group_id", "A group description carries exactly one d tag: 1 to 64 lowercase letters, digits, dots, dashes, underscores or tildes.")
        event.kind == GroupKinds.METADATA && event.tags.any { it.firstOrNull() in GroupKinds.UNSUPPORTED_FLAGS } ->
            Decision.Refused(400, "unsupported_group_flag", "Private and hidden groups are not supported yet: this relay cannot restrict who reads them.")
        event.kind == GroupKinds.METADATA && !hasUsableText(event, GroupKinds.NAME_TAG) ->
            Decision.Refused(400, "missing_name", "A group description carries a name tag with at least one letter or digit.")
        event.kind == GroupKinds.METADATA && !hasUsableText(event, GroupKinds.TERRITORY_TAG) ->
            Decision.Refused(400, "missing_territory", "A group description names its territory in a ${GroupKinds.TERRITORY_TAG} tag (a town, a department, \"France\").")
        event.kind == GroupKinds.METADATA && event.firstTagValue(GroupKinds.CHARTER_TAG) != GroupKinds.CHARTER_VERSION ->
            Decision.Refused(400, "missing_charter", "Creating a group means accepting the charter of values: a ${GroupKinds.CHARTER_TAG} tag with the value ${GroupKinds.CHARTER_VERSION}.")
        event.kind == GroupKinds.METADATA && !fitsDescriptionBounds(event) ->
            Decision.Refused(
                400,
                "description_too_long",
                "A group description is at most ${GroupKinds.MAX_DESCRIPTION_BYTES} bytes, its name at most ${GroupKinds.MAX_NAME_LENGTH} characters and its territory at most ${GroupKinds.MAX_TERRITORY_LENGTH}.",
            )
        event.kind == GroupKinds.METADATA && !hasValidDifference(event) ->
            Decision.Refused(
                400,
                "bad_difference",
                "A creation confirmed despite similar rooms (${GroupKinds.SIMILAR_TAG} = ${GroupKinds.SIMILAR_CONFIRMED}) says how it differs in one ${GroupKinds.DIFFERENCE_TAG} tag of 1 to ${GroupKinds.MAX_DIFFERENCE_LENGTH} characters.",
            )
        event.kind == GroupKinds.CHAT_MESSAGE && event.tagValues(GroupKinds.GROUP_TAG).isEmpty() ->
            Decision.Refused(400, "missing_group", "A chat message names its group in an h tag.")
        event.tagValues(GroupKinds.GROUP_TAG).size > 1 ->
            Decision.Refused(400, "multiple_groups", "An event is sent to one group at a time (one h tag).")
        event.kind == RelayKinds.GIFT_WRAP && !hasExactlyOneRecipient(event) ->
            Decision.Refused(400, "bad_recipient", "An envelope names exactly one recipient: a p tag with a 64-character hex key.")
        else -> Decision.Allowed
    }

    /**
     * The shape of an accepted moderation event (contract salons-roles.md, section 2), or null when it
     * is fine: exactly one valid `h`; put-user (9000) and remove-user (9001) name exactly one key in a
     * `p` tag, put-user with at most one role (`admin` or `moderator`) as its third value; remove-user
     * and delete-event (9005) give their reason as content; delete-event names exactly one event in
     * an `e` tag; edit-metadata (9002) changes a `name` and/or an `about`, nothing else.
     */
    fun moderationShape(event: SignedEvent): Decision.Refused? {
        val groups = event.tagValues(GroupKinds.GROUP_TAG)
        if (groups.isEmpty()) return Decision.Refused(400, "missing_group", "A moderation event names its group in an h tag.")
        if (groups.size > 1) return Decision.Refused(400, "multiple_groups", "An event is sent to one group at a time (one h tag).")
        if (!GroupKinds.isValidId(groups[0])) return Decision.Refused(400, "bad_group_id", "The h tag is not a valid group id.")
        val reasonRequired = Decision.Refused(400, "reason_required", "An exclusion and a removal give their reason as content (1 to ${GroupKinds.MAX_REASON_LENGTH} characters).")
        val oneKey = Decision.Refused(400, "bad_target", "A put-user or remove-user names exactly one key: a p tag with a 64-character hex key.")
        return when (event.kind) {
            GroupKinds.PUT_USER -> {
                val tag = event.tags.filter { it.firstOrNull() == "p" }.singleOrNull() ?: return oneKey
                if (tag.size < 2 || !isHexKey(tag[1])) return oneKey
                if (tag.size > 3 || (tag.size == 3 && tag[2] !in GroupKinds.ROLES_GIVEN)) {
                    return Decision.Refused(400, "bad_role", "A put-user gives at most one role: ${GroupKinds.ROLE_ADMIN} or ${GroupKinds.ROLE_MODERATOR}.")
                }
                null
            }
            GroupKinds.REMOVE_USER -> {
                val keys = event.tagValues("p")
                if (keys.size != 1 || !isHexKey(keys[0])) return oneKey
                if (!hasReason(event)) reasonRequired else null
            }
            GroupKinds.DELETE_EVENT -> {
                val targets = event.tagValues("e")
                if (targets.size != 1 || !isHexKey(targets[0])) {
                    return Decision.Refused(400, "bad_target", "A delete-event names exactly one event: an e tag with a 64-character hex id.")
                }
                if (!hasReason(event)) reasonRequired else null
            }
            GroupKinds.EDIT_METADATA -> {
                val allowed = setOf(GroupKinds.GROUP_TAG, GroupKinds.NAME_TAG, GroupKinds.ABOUT_TAG)
                if (event.tags.any { it.firstOrNull() !in allowed }) {
                    return Decision.Refused(400, "edit_not_allowed", "An edit changes the name and the presentation only: never the territory, the charter, the flags or the id.")
                }
                val names = event.tagValues(GroupKinds.NAME_TAG)
                val abouts = event.tagValues(GroupKinds.ABOUT_TAG)
                if (names.size > 1 || abouts.size > 1 || (names.isEmpty() && abouts.isEmpty())) {
                    return Decision.Refused(400, "edit_not_allowed", "An edit carries one name tag, one about tag, or both.")
                }
                if (names.isNotEmpty() && !hasUsableText(event, GroupKinds.NAME_TAG)) {
                    return Decision.Refused(400, "missing_name", "A group name has at least one letter or digit.")
                }
                if (names.singleOrNull().orEmpty().length > GroupKinds.MAX_NAME_LENGTH || event.toJson().encodeToByteArray().size > GroupKinds.MAX_DESCRIPTION_BYTES) {
                    return Decision.Refused(400, "description_too_long", "A name is at most ${GroupKinds.MAX_NAME_LENGTH} characters and an edit at most ${GroupKinds.MAX_DESCRIPTION_BYTES} bytes.")
                }
                null
            }
            else -> null
        }
    }

    private fun hasReason(event: SignedEvent): Boolean = event.content.trim().length in 1..GroupKinds.MAX_REASON_LENGTH

    /** A confirmation despite similar rooms comes with its difference, and a difference stays short. */
    private fun hasValidDifference(event: SignedEvent): Boolean {
        val differences = event.tagValues(GroupKinds.DIFFERENCE_TAG)
        if (differences.size > 1) return false
        val difference = differences.singleOrNull()
        if (difference != null && (difference.isBlank() || difference.trim().length > GroupKinds.MAX_DIFFERENCE_LENGTH)) return false
        val confirmed = event.tagValues(GroupKinds.SIMILAR_TAG)
        if (confirmed.isEmpty()) return true
        return confirmed.size == 1 && confirmed[0] == GroupKinds.SIMILAR_CONFIRMED && difference != null
    }

    private fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun hasExactlyOneValidGroupId(event: SignedEvent, tag: String): Boolean {
        val ids = event.tagValues(tag)
        return ids.size == 1 && GroupKinds.isValidId(ids[0])
    }

    /** The first value of [tag] exists and keeps at least one letter or digit once normalized (so that it can be compared). */
    private fun hasUsableText(event: SignedEvent, tag: String): Boolean =
        event.firstTagValue(tag)?.let { GroupNames.normalize(it).isNotEmpty() } == true

    /** A room description stays small: every account may create one and the directory lists them all. */
    private fun fitsDescriptionBounds(event: SignedEvent): Boolean =
        event.firstTagValue(GroupKinds.NAME_TAG).orEmpty().length <= GroupKinds.MAX_NAME_LENGTH &&
            event.firstTagValue(GroupKinds.TERRITORY_TAG).orEmpty().length <= GroupKinds.MAX_TERRITORY_LENGTH &&
            event.toJson().encodeToByteArray().size <= GroupKinds.MAX_DESCRIPTION_BYTES

    private fun hasExactlyOneRecipient(event: SignedEvent): Boolean {
        val recipients = event.tagValues("p")
        return recipients.size == 1 && recipients[0].length == 64 && recipients[0].all { it in '0'..'9' || it in 'a'..'f' }
    }

    companion object {
        const val EDITOR_KEYS_VARIABLE: String = "PPF_EDITOR_KEYS"

        /** Enough for any follow list that fits in 64 KB with hex keys; refuses tag floods. */
        const val MAX_TAGS: Int = 1_000

        fun fromEnvironment(env: (String) -> String? = System::getenv): PublicationPolicy =
            PublicationPolicy(parseKeys(env(EDITOR_KEYS_VARIABLE)))

        /** Accepts hex and "npub1…" keys, separated by commas, semicolons or whitespace. */
        fun parseKeys(value: String?): Set<String> = value.orEmpty()
            .split(',', ';', ' ', '\n', '\t', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { key ->
                if (key.startsWith("npub1", ignoreCase = true)) {
                    PublicKey.fromNpub(key.lowercase()).hex
                } else {
                    PublicKey.fromHex(key).hex
                }
            }
            .toSet()
    }
}
