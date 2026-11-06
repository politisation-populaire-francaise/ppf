package org.politisationpopulairefrancaise.server.events

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * Who may publish what. Phase 1 rules: ephemeral events are never stored, and editorial contents
 * (kind 30023) are accepted only from the editorial team's keys, listed in the environment variable
 * PPF_EDITOR_KEYS (hex or "npub1…" keys separated by commas). Anti-spam rules will come here too.
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
