package org.politisationpopulairefrancaise.server.events

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.agir.AgirRules
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.GroupNames
import org.politisationpopulairefrancaise.server.relay.RelayKinds

/**
 * Who may publish what, without looking at the database (the rules that need it live in
 * GroupDirectory). Phase 1 rules:
 *
 * - ephemeral events are never stored; events with more than [MAX_TAGS] tags are refused;
 * - editorial contents (kind 30023) are accepted only from the keys listed in the environment
 *   variable PPF_EDITOR_KEYS (hex or "npub1…" keys separated by commas); a demand from another key
 *   ([isCitizenDemand]) is refused here too, and the API holds it for review by a person instead
 *   (server/revendications); a call of the Agir space (`ppf-type` = appel) is open to every key;
 * - mobilizations (kind 31923), calls and directory entries of the Agir space must have the shape of
 *   the contract (server/agir/AgirRules); who publishes them directly is decided by AgirSpace;
 * - a group description (kind 39000, NIP-29) is accepted from any key (D-028: creating a group is
 *   open to every account) provided it carries exactly one valid `d` id, a non-empty `name`, a
 *   non-empty territory (`ppf-territory`), the accepted charter version (`ppf-charter` = 1.0) and
 *   none of the flags the relay cannot honour yet (`private`, `hidden`: reading would have to be
 *   restricted to members); `about` and `picture` are optional; it stays small (at most
 *   [GroupKinds.MAX_DESCRIPTION_BYTES] serialized, name and territory of at most 100 characters);
 * - the companions of a description (39001 admins, 39003 roles, 39004, 39005) stay reserved to the
 *   editorial keys until roles exist: an admins list published by anyone would tie keys to a group;
 * - member lists (39002) are refused: the server never holds nor publishes one (D-027);
 * - group membership and moderation events (9000 to 9022) are not accepted yet; they are the
 *   next step of the groups (see GroupKinds);
 * - an event sent to a group names exactly one group (`h`); a chat message (kind 9) must name one;
 * - an encrypted envelope (kind 1059) names exactly one recipient (`p`), otherwise it could never
 *   be fetched and would only take space;
 * - everything else (notes, application data such as the mailbox settings of kind 30078...) is
 *   accepted from any key.
 *
 * Anti-spam: the rate limits are applied by the API before this policy; the proof of work is
 * still to come.
 */
class PublicationPolicy(private val editors: Set<String>) {

    sealed interface Decision {
        data object Allowed : Decision

        /**
         * [existing] is set with the error `group_exists` only: the id of the group the client should
         * join instead of creating a duplicate.
         */
        data class Refused(val status: Int, val error: String, val message: String, val existing: String? = null) : Decision
    }

    fun isEditor(pubkeyHex: String): Boolean = pubkeyHex in editors

    /** A demand of the wall (kind 30023, `ppf-type` = revendication) signed by a key outside the editorial team. */
    fun isCitizenDemand(event: SignedEvent): Boolean =
        event.kind == Article.KIND && !isEditor(event.pubkey) && event.firstTagValue(Article.TAG_TYPE) == ArticleType.REVENDICATION.tagValue

    fun check(event: SignedEvent): Decision = when {
        EventKinds.isEphemeral(event.kind) ->
            Decision.Refused(400, "ephemeral_kind", "Ephemeral events are not stored.")
        event.tags.size > MAX_TAGS ->
            Decision.Refused(400, "too_many_tags", "An event carries at most $MAX_TAGS tags.")
        // Calls of the Agir space are open to every key (AgirSpace then publishes or holds them).
        event.kind == Article.KIND && !isEditor(event.pubkey) && !AgirRules.isCall(event) ->
            Decision.Refused(403, "not_allowed", "Only the editorial team publishes editorial contents.")
        // The shape of mobilizations, calls and directory entries (docs/integration/agir-contrat.md).
        AgirRules.check(event) != null -> AgirRules.check(event)!!
        event.kind == GroupKinds.MEMBERS ->
            Decision.Refused(403, "members_list_refused", "Member lists are never published nor stored on this relay.")
        GroupKinds.isMembershipOrModeration(event.kind) ->
            Decision.Refused(400, "unsupported_kind", "Group membership and moderation events are not accepted yet.")
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
        event.kind == GroupKinds.CHAT_MESSAGE && event.tagValues(GroupKinds.GROUP_TAG).isEmpty() ->
            Decision.Refused(400, "missing_group", "A chat message names its group in an h tag.")
        event.tagValues(GroupKinds.GROUP_TAG).size > 1 ->
            Decision.Refused(400, "multiple_groups", "An event is sent to one group at a time (one h tag).")
        event.kind == RelayKinds.GIFT_WRAP && !hasExactlyOneRecipient(event) ->
            Decision.Refused(400, "bad_recipient", "An envelope names exactly one recipient: a p tag with a 64-character hex key.")
        else -> Decision.Allowed
    }

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
