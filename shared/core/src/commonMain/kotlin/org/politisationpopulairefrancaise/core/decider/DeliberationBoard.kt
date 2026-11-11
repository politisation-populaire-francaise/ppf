package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** A proposition of a deliberation with the amendments put to the vote, in order of creation. */
data class PropositionEntry(val proposition: Proposition, val amendments: List<Amendment>)

/**
 * One deliberation as a device shows it (DE-25, MVP 1: the measures of the Pacte démocratique): the
 * deliberation published by the team, its admissible propositions (the starting ones of PPF first) and,
 * for each, the amendments put to the vote. Built by the rules of [DeliberationRules], the same as the
 * server's, from the contents of the local copy.
 */
data class DeliberationBoard(val deliberation: Deliberation, val entries: List<PropositionEntry>) {
    val address: ContentAddress get() = deliberation.address

    /** The phase at [now], null before the propositions and after the vote. */
    fun phaseAt(now: Long): DeliberationPhase? = deliberation.phaseAt(now)

    fun entry(address: ContentAddress): PropositionEntry? = entries.firstOrNull { it.proposition.address == address }

    companion object {
        /** The `ppf-type` values to ask the local copy for. */
        val TYPES: List<String> = listOf(DeliberationFormats.TYPE_DELIBERATION, DeliberationFormats.TYPE_PROPOSITION, DeliberationFormats.TYPE_AMENDMENT)

        /**
         * The deliberations of [events], newest first. A deliberation counts only when an editorial key
         * ([isEditor]) signed it; propositions and amendments come from anyone, since the server serves a
         * citizen's contribution only after the review of a person; a starting proposal counts only when
         * the deliberation's author signed it. Only the latest version of each content is read.
         */
        fun from(events: List<SignedEvent>, isEditor: (String) -> Boolean): List<DeliberationBoard> {
            val latest = events.filter { it.kind == EventKinds.LONG_FORM_ARTICLE }
                .groupBy { ContentAddress.of(it) }
                .filterKeys { it != null }
                .values.map { versions -> versions.maxBy { it.createdAt } }
            val deliberations = latest.filter { isEditor(it.pubkey) }.mapNotNull(Deliberation::parse)
            val propositions = latest.mapNotNull(Proposition::parse)
            val amendments = latest.mapNotNull(Amendment::parse)
            return deliberations.map { deliberation ->
                val admissible = DeliberationRules.admissiblePropositions(deliberation, propositions)
                    .filter { !it.isStart || it.event.pubkey == deliberation.event.pubkey }
                val ordered = admissible.filter { it.isStart } + admissible.filterNot { it.isStart }
                DeliberationBoard(deliberation, ordered.map { PropositionEntry(it, DeliberationRules.votedAmendments(it, amendments)) })
            }.sortedByDescending { it.deliberation.event.createdAt }
        }
    }
}

/**
 * The contributions a citizen writes on the device (docs/integration/deliberation.md, section 2): signed by
 * their key, sent through the single publication port, held by the server for the review of a person.
 */
object DeliberationDrafts {
    /** A proposition of measure during the phase of propositions. */
    fun proposition(author: PublicKey, deliberation: ContentAddress, title: String, text: String, justification: String?, now: Long, identifier: String): UnsignedEvent {
        val cleanTitle = title.trim()
        val cleanText = text.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= DeliberationFormats.MAX_TITLE) { "the title holds in ${DeliberationFormats.MAX_TITLE} characters" }
        require(cleanText.isNotEmpty() && cleanText.length <= DeliberationFormats.MAX_TEXT) { "the text holds in ${DeliberationFormats.MAX_TEXT} characters" }
        return UnsignedEvent.create(author, EventKinds.LONG_FORM_ARTICLE, now, common(identifier, DeliberationFormats.TYPE_PROPOSITION, deliberation, justification) + listOf(listOf("title", cleanTitle)), cleanText)
    }

    /** An amendment: the complete new wording of [proposition], during the phase of amendments. */
    fun amendment(author: PublicKey, proposition: ContentAddress, text: String, justification: String?, now: Long, identifier: String): UnsignedEvent {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty() && cleanText.length <= DeliberationFormats.MAX_TEXT) { "the text holds in ${DeliberationFormats.MAX_TEXT} characters" }
        return UnsignedEvent.create(author, EventKinds.LONG_FORM_ARTICLE, now, common(identifier, DeliberationFormats.TYPE_AMENDMENT, proposition, justification), cleanText)
    }

    private fun common(identifier: String, type: String, link: ContentAddress, justification: String?): List<List<String>> {
        require(identifier.isNotBlank() && identifier.none { it.isWhitespace() }) { "the identifier is a single word" }
        val cleanJustification = justification?.trim()?.takeIf { it.isNotEmpty() }
        require(cleanJustification == null || cleanJustification.length <= DeliberationFormats.MAX_JUSTIFICATION) { "the justification holds in ${DeliberationFormats.MAX_JUSTIFICATION} characters" }
        return buildList {
            add(listOf("d", identifier))
            add(listOf(DeliberationFormats.TAG_TYPE, type))
            add(listOf("a", link.toString()))
            cleanJustification?.let { add(listOf(DeliberationFormats.TAG_JUSTIFICATION, it)) }
        }
    }
}
