package org.politisationpopulairefrancaise.server.events

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.groups.GroupKinds

/**
 * Who may publish what, without looking at the database (the rules that need it live in
 * GroupDirectory). Phase 1 rules:
 *
 * - ephemeral events are never stored;
 * - editorial contents (kind 30023) and group descriptions (kinds 39000 to 39005, NIP-29) are
 *   accepted only from the keys listed in the environment variable PPF_EDITOR_KEYS (hex or
 *   "npub1…" keys separated by commas);
 * - member lists (39002) are refused: the server never holds nor publishes one (D-027);
 * - group membership and moderation events (9000 to 9022) are not accepted yet; they are the
 *   next step of the groups (see GroupKinds);
 * - a chat message (kind 9) must name its group; a group description must carry a valid id;
 * - encrypted envelopes (kind 1059) and ordinary notes are accepted from any key.
 *
 * Anti-spam: the per-key rate limit is applied by the API before this policy; the proof of work
 * is still to come.
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
        event.kind == Article.KIND && !isEditor(event.pubkey) ->
            Decision.Refused(403, "not_allowed", "Only the editorial team publishes editorial contents.")
        event.kind == GroupKinds.MEMBERS ->
            Decision.Refused(403, "members_list_refused", "Member lists are never published nor stored on this relay.")
        GroupKinds.isMembershipOrModeration(event.kind) ->
            Decision.Refused(400, "unsupported_kind", "Group membership and moderation events are not accepted yet.")
        GroupKinds.isGroupMetadata(event.kind) && !isEditor(event.pubkey) ->
            Decision.Refused(403, "not_allowed", "Only authorized keys describe groups.")
        GroupKinds.isGroupMetadata(event.kind) && !GroupKinds.isValidId(event.firstTagValue("d")) ->
            Decision.Refused(400, "bad_group_id", "A group id is 1 to 64 letters, digits, dots, dashes, underscores or tildes, in the d tag.")
        event.kind == GroupKinds.CHAT_MESSAGE && event.firstTagValue(GroupKinds.GROUP_TAG) == null ->
            Decision.Refused(400, "missing_group", "A chat message names its group in an h tag.")
        else -> Decision.Allowed
    }

    companion object {
        const val EDITOR_KEYS_VARIABLE: String = "PPF_EDITOR_KEYS"

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
