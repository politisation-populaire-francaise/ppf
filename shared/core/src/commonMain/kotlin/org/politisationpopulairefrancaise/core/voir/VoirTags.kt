package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * The tags of the "Voir" building blocks (D-034, docs/FORMATS.md section 4 quinquies). A block
 * is a NIP-23 event (kind 30023) whose `ppf-type` says what it is; its Markdown body stays
 * readable by any Nostr client, its structured data lives in these tags. Multi-field tags are
 * positional; an empty field means "unknown".
 */
object VoirTags {
    const val AXIS = "ppf-axis"
    const val SOURCE = "ppf-source"
    const val ARCHIVE = "ppf-archive"
    const val CORRECTION = "ppf-correction"
    const val NEXT = "ppf-next"

    /** A link to another block: `["a", "30023:<pubkey>:<identifier>", "", <role>]`. */
    const val LINK = "a"
    const val ROLE_PERSON = "personne"
    const val ROLE_TEXT = "texte"
    const val ROLE_INDICATOR = "indicateur"
    const val ROLE_TARGET = "cible"
    /** From a person or a fact to the party it belongs to or concerns. */
    const val ROLE_PARTY = "parti"
    /** From a party to the persons who lead it. */
    const val ROLE_LEADER = "responsable"

    // Indicators.
    const val UNIT = "ppf-unit"
    const val RANK = "ppf-rank"
    const val DECLARATIVE = "ppf-declarative"
    const val BREAKDOWN = "ppf-breakdown"
    const val ACCEPTED_SOURCE = "ppf-accepted-source"
    const val MEASURE = "ppf-measure"

    // Facts.
    const val FACT = "ppf-fact"
    const val DATE = "ppf-date"
    const val PLACE = "ppf-place"
    const val FACT_STATUS = "ppf-fact-status"
    const val PROCEDURE = "ppf-procedure"
    const val SENTENCE = "ppf-sentence"
    const val SENTENCE_SERVED = "ppf-sentence-served"
    const val PROMISE = "ppf-promise"
    const val VOTE = "ppf-vote"
    const val QUOTE = "ppf-quote"
    const val LIBERTY = "ppf-liberty"
    const val RECOURSE = "ppf-recourse"

    // Public persons.
    const val ROLE = "ppf-role"
    const val GROUP = "ppf-group"
    const val MANDATE = "ppf-mandate"

    // Bills.
    const val TEXT_KIND = "ppf-text-kind"
    const val STAGE = "ppf-stage"
    const val STAGE_HISTORY = "ppf-stage-history"
    const val NEXT_VOTE = "ppf-next-vote"
    const val ARTICLE_49_3 = "ppf-493"
    const val IMPACT = "ppf-impact"

    // Movements of the world.
    const val COUNTRY = "ppf-country"
    const val PERIOD = "ppf-period"
    const val CLAIM = "ppf-claim"
    const val RESULT = "ppf-result"

    // Political parties.
    const val ACRONYM = "ppf-acronym"
    const val FOUNDED = "ppf-founded"
    const val SEATS = "ppf-seats"
    const val WEBSITE = "ppf-website"
    const val FINANCING = "ppf-financing"
    const val PUBLIC_FUNDING = "ppf-public-funding"
    const val EUROPEAN_PARTY = "ppf-european-party"
    const val EUROPEAN_GROUP = "ppf-european-group"

    // Rights of reply.
    const val RESPONDENT = "ppf-respondent"

    // « Transparence de PPF » (PP-01, D-104).
    const val RUBRIC = "ppf-rubric"
    const val INSTANCE = "ppf-instance"
    const val SEAT = "ppf-seat"
    const val TALLY = "ppf-tally"
    const val YEAR = "ppf-year"
    const val ACCOUNT_LINE = "ppf-account-line"
    const val APPROVED = "ppf-approved"
    const val FIGURE = "ppf-figure"

    // « Nos victoires » (PP-03).
    const val VICTORY = "ppf-victory"
    /** From a victory to the blocks of Voir it concerns (a promise kept, a law changed). */
    const val ROLE_RELATED = "lie"

    /** Every tag named [name], as its fields after the name, blank fields read as null. */
    fun SignedEvent.fields(name: String): List<List<String?>> =
        tags.filter { it.size >= 2 && it[0] == name }.map { tag -> tag.drop(1).map { field -> field.trim().takeIf { it.isNotEmpty() } } }

    /** The first value of [name], trimmed, null when absent or blank. */
    fun SignedEvent.value(name: String): String? = firstTagValue(name)?.trim()?.takeIf { it.isNotEmpty() }

    fun List<String?>.at(index: Int): String? = getOrNull(index)

    fun SignedEvent.sources(): List<Source> = fields(SOURCE).mapNotNull { f ->
        val kind = SourceKind.fromTag(f.at(0)) ?: return@mapNotNull null
        val name = f.at(1) ?: return@mapNotNull null
        Source(kind, name, f.at(2)?.takeIf(::isWebAddress), CivilDate.parseIso(f.at(3)))
    }

    fun SignedEvent.archives(): List<ArchivedCopy> = fields(ARCHIVE).mapNotNull { f ->
        val hash = f.at(0)?.lowercase()?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } } ?: return@mapNotNull null
        ArchivedCopy(hash, f.at(1), f.at(2)?.takeIf(::isWebAddress), CivilDate.parseIso(f.at(3)))
    }

    fun SignedEvent.corrections(): List<Correction> = fields(CORRECTION).mapNotNull { f ->
        val date = CivilDate.parseIso(f.at(0)) ?: return@mapNotNull null
        val reason = f.at(1) ?: return@mapNotNull null
        Correction(date, reason)
    }.sortedByDescending { it.date }

    fun SignedEvent.nextSteps(): List<NextStep> = fields(NEXT).mapNotNull { f ->
        val label = f.at(0) ?: return@mapNotNull null
        val target = f.at(1)?.let(NextTarget::parse) ?: return@mapNotNull null
        NextStep(label, target)
    }

    /** The addresses this block links to with the given [role]. */
    fun SignedEvent.links(role: String): List<ContentAddress> = tags
        .filter { it.size >= 4 && it[0] == LINK && it[3] == role }
        .mapNotNull { runCatching { ContentAddress.parse(it[1]) }.getOrNull() }
        .distinct()

    /** Only web addresses are followed from a source; anything else (javascript:, file:) is ignored. */
    fun isWebAddress(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")
}
