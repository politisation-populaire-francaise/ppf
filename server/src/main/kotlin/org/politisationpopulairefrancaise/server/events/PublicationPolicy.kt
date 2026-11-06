package org.politisationpopulairefrancaise.server.events

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.relay.RelayKinds

/**
 * Who may publish what, without looking at the database (the rules that need it live in
 * GroupDirectory). Phase 1 rules:
 *
 * - ephemeral events are never stored; events with more than [MAX_TAGS] tags are refused;
 * - editorial contents (kind 30023) and group descriptions (kinds 39000 to 39005, NIP-29) are
 *   accepted only from the keys listed in the environment variable PPF_EDITOR_KEYS (hex or
 *   "npub1…" keys separated by commas);
 * - member lists (39002) are refused: the server never holds nor publishes one (D-027);
 * - group membership and moderation events (9000 to 9022) are not accepted yet; they are the
 *   next step of the groups (see GroupKinds);
 * - a group description carries exactly one valid `d` id and none of the flags the relay cannot
 *   honour yet (`private`, `hidden`: reading would have to be restricted to members);
 * - an event sent to a group names exactly one group (`h`); a chat message (kind 9) must name one;
 * - an encrypted envelope (kind 1059) names exactly one recipient (`p`), otherwise it could never
 *   be fetched and would only take space;
 * - everything else (notes, envelopes...) is accepted from any key.
 *
 * Anti-spam: the rate limits are applied by the API before this policy; the proof of work is
 * still to come.
 */
class PublicationPolicy(private val editors: Set<String>) {

    sealed interface Decision {
        data object Allowed : Decision

        data class Refused(val status: Int, val error: String, val message: String) : Decision
    }

    fun isEditor(pubkeyHex: String): Boolean = pubkeyHex in editors

    fun check(event: SignedEvent): Decision = when {
        EventKinds.isEphemeral(event.kind) ->
            Decision.Refused(400, "ephemeral_kind", "Ephemeral events are not stored.")
        event.tags.size > MAX_TAGS ->
            Decision.Refused(400, "too_many_tags", "An event carries at most $MAX_TAGS tags.")
        event.kind == Article.KIND && !isEditor(event.pubkey) ->
            Decision.Refused(403, "not_allowed", "Only the editorial team publishes editorial contents.")
        event.kind == GroupKinds.MEMBERS ->
            Decision.Refused(403, "members_list_refused", "Member lists are never published nor stored on this relay.")
        GroupKinds.isMembershipOrModeration(event.kind) ->
            Decision.Refused(400, "unsupported_kind", "Group membership and moderation events are not accepted yet.")
        GroupKinds.isGroupMetadata(event.kind) && !isEditor(event.pubkey) ->
            Decision.Refused(403, "not_allowed", "Only authorized keys describe groups.")
        GroupKinds.isGroupMetadata(event.kind) && !hasExactlyOneValidGroupId(event, "d") ->
            Decision.Refused(400, "bad_group_id", "A group description carries exactly one d tag: 1 to 64 lowercase letters, digits, dots, dashes, underscores or tildes.")
        event.kind == GroupKinds.METADATA && event.tags.any { it.firstOrNull() in GroupKinds.UNSUPPORTED_FLAGS } ->
            Decision.Refused(400, "unsupported_group_flag", "Private and hidden groups are not supported yet: this relay cannot restrict who reads them.")
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
