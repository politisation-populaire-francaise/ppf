package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** Where a comment of the debate stands (DE-20): for, against, or mixed. */
enum class DebateStance(val tagValue: String) {
    POUR("pour"),
    CONTRE("contre"),
    MITIGE("mitige"),
    ;

    companion object {
        fun fromTag(value: String?): DebateStance? = entries.firstOrNull { it.tagValue == value?.trim() }
    }
}

/** One comment of the debate of a demand, signed by its pseudonymous author. */
data class DebateComment(val event: SignedEvent, val demand: ContentAddress, val stance: DebateStance, val text: String) {
    val id: String get() = event.id
    val author: String get() = event.pubkey
    val createdAt: Long get() = event.createdAt
}

/**
 * The debate of a demand (DE-20, MVP 1, simple version without a summary by an AI): short comments, each one
 * labelled « Pour », « Contre » or « Mitigé », published under the author's pseudonym like any post (moderation
 * after the fact: anyone can report, a person of the team decides). A comment is a NIP-22 comment (kind 1111)
 * whose root is the demand: `A`/`a` = its address, `K`/`k` = 30023, `P`/`p` = its author, plus
 * `["ppf-stance","pour|contre|mitige"]`. Fetched by the tag `A` of the demand.
 */
object DemandDebate {
    const val KIND: Int = 1111
    const val TAG_STANCE: String = "ppf-stance"
    const val TAG_ROOT: String = "A"
    const val MAX_TEXT: Int = 1_000

    fun build(author: PublicKey, demand: ContentAddress, stance: DebateStance, text: String, now: Long): UnsignedEvent {
        val clean = text.trim()
        require(clean.isNotEmpty() && clean.length <= MAX_TEXT) { "a comment holds in 1 to $MAX_TEXT characters" }
        val address = demand.toString()
        val kind = demand.kind.toString()
        val tags = listOf(
            listOf(TAG_ROOT, address), listOf("K", kind), listOf("P", demand.pubkey),
            listOf("a", address), listOf("k", kind), listOf("p", demand.pubkey),
            listOf(TAG_STANCE, stance.tagValue),
        )
        return UnsignedEvent.create(author, KIND, now, tags, clean)
    }

    /** The comment of [event], or null when it is not a well-formed comment of a demand. */
    fun parse(event: SignedEvent): DebateComment? {
        if (event.kind != KIND) return null
        val root = event.firstTagValue(TAG_ROOT) ?: return null
        val demand = runCatching { ContentAddress.parse(root) }.getOrNull()?.takeIf { it.kind == EventKinds.LONG_FORM_ARTICLE } ?: return null
        val stance = DebateStance.fromTag(event.firstTagValue(TAG_STANCE)) ?: return null
        val text = event.content.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT } ?: return null
        return DebateComment(event, demand, stance, text)
    }

    /** The comments of [demand] among [events], each once, the newest first. */
    fun of(events: List<SignedEvent>, demand: ContentAddress): List<DebateComment> =
        events.mapNotNull(::parse).filter { it.demand == demand }.distinctBy { it.id }.sortedByDescending { it.createdAt }

    /** How many comments of each stance, every stance present (0 when none). */
    fun counts(comments: List<DebateComment>): Map<DebateStance, Int> =
        DebateStance.entries.associateWith { stance -> comments.count { it.stance == stance } }
}
