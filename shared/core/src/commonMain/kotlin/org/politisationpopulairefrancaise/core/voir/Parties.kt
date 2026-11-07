package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.links
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/** The assemblies where a party holds seats (`ppf-seats`, first field). */
enum class Chamber(val tagValue: String) {
    ASSEMBLY("assemblee"),
    SENATE("senat"),
    EUROPEAN("parlement-europeen"),
    OTHER("autre"),
    ;

    companion object {
        fun fromTag(value: String?): Chamber? = entries.firstOrNull { it.tagValue == value }
    }
}

/**
 * How a party collects funds (loi n° 88-227 du 11 mars 1988, articles 11 to 11-2): through a
 * financing association approved by the CNCCFP (approval published in the Journal officiel), or
 * through a financial agent, a natural person declared at the prefecture.
 */
enum class FinancingKind(val tagValue: String) {
    ASSOCIATION("association-de-financement"),
    AGENT("mandataire-financier"),
    ;

    companion object {
        fun fromTag(value: String?): FinancingKind? = entries.firstOrNull { it.tagValue == value }
    }
}

/**
 * The financing structure of a party (`ppf-financing`): its kind, the reference of its approval
 * (CNCCFP decision, Journal officiel) and its date. The name of a financial agent, a private person,
 * is never kept: for [FinancingKind.AGENT] the reference field is ignored.
 */
data class Financing(val kind: FinancingKind, val approval: String?, val date: CivilDate?, val source: Source?)

/** The public aid of the State to a party for one year (loi n° 88-227, articles 8 and 9), in cents, with its decree. */
data class PublicFunding(val year: Int, val amountCents: Long, val decree: Source?)

/** A number of seats in one assembly at a date, as one source gives it. */
data class SeatCount(val chamber: Chamber, val count: Int, val date: CivilDate?, val source: Source?)

/**
 * A political party (axis of power; owner's decision of 7 November 2026: the major parties enter
 * "Voir" right after the persons cited in published contents). Every party sheet has the same
 * sections in the same order, whatever the party (règle « Traitement identique »): presentation,
 * seats, leaders and members, programme promises, facts concerning the party itself, sources,
 * corrections. A member's case is never shown on the party's sheet (no guilt by association):
 * only the facts that link the party itself (`a` tag of role `parti`).
 */
data class PoliticalParty(
    val article: Article,
    /** The short name or acronym (`ppf-acronym`). */
    val acronym: String?,
    val founded: CivilDate?,
    /** Mandatory: a party is read only with its financing structure, the same official criterion for all. */
    val financing: Financing,
    /** The public aid of the State, latest year first. */
    val publicFunding: List<PublicFunding>,
    val europeanParty: String?,
    val europeanGroup: String?,
    val seats: List<SeatCount>,
    /** The persons who lead the party (`a` tags of role `responsable`). */
    val leaders: List<ContentAddress>,
    /** The party's own web site, only an http(s) address. */
    val website: String?,
    val sources: List<Source>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
) {
    val address: ContentAddress get() = article.address
    val name: String get() = article.title

    /** The assemblies where the party has at least one count, in their usual order. */
    val chambers: List<Chamber> get() = Chamber.entries.filter { c -> seats.any { it.chamber == c } }

    /** Every count given for [chamber], latest date first; several sources of the same date stay side by side. */
    fun seatsIn(chamber: Chamber): List<SeatCount> = seats.filter { it.chamber == chamber }.sortedByDescending { it.date }

    /**
     * The reference number of seats in [chamber]: among the counts of the latest date, the one from a
     * primary source (parliamentary records, official text) when there is one, otherwise the first.
     */
    fun referenceSeats(chamber: Chamber): SeatCount? {
        val counts = seatsIn(chamber)
        val latest = counts.firstOrNull()?.date
        val ofLatest = counts.filter { it.date == latest }
        return ofLatest.firstOrNull { it.source?.kind in PRIMARY } ?: ofLatest.firstOrNull()
    }

    /** The sum of the reference seats of every assembly (shown on the sheet, never used to rank the parties). */
    val totalSeats: Int get() = chambers.sumOf { referenceSeats(it)?.count ?: 0 }

    companion object {
        private val PRIMARY = setOf(SourceKind.PARLIAMENT, SourceKind.OFFICIAL_TEXT)

        fun from(article: Article): PoliticalParty? {
            if (article.type != ArticleType.PARTI) return null
            val event = article.event
            val financing = event.fields(VoirTags.FINANCING).firstNotNullOfOrNull(::financingOf) ?: return null
            return PoliticalParty(
                article = article,
                acronym = event.value(VoirTags.ACRONYM),
                founded = CivilDate.parseIso(event.value(VoirTags.FOUNDED)),
                financing = financing,
                publicFunding = event.fields(VoirTags.PUBLIC_FUNDING).mapNotNull(::fundingOf)
                    .distinctBy { it.year }.sortedByDescending { it.year },
                europeanParty = event.value(VoirTags.EUROPEAN_PARTY),
                europeanGroup = event.value(VoirTags.EUROPEAN_GROUP),
                seats = event.fields(VoirTags.SEATS).mapNotNull { f ->
                    val chamber = Chamber.fromTag(f.at(0)) ?: return@mapNotNull null
                    val count = f.at(1)?.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                    val source = SourceKind.fromTag(f.at(3))?.let { kind ->
                        f.at(4)?.let { name -> Source(kind, name, f.at(5)?.takeIf(VoirTags::isWebAddress), CivilDate.parseIso(f.at(2))) }
                    }
                    SeatCount(chamber, count, CivilDate.parseIso(f.at(2)), source)
                },
                leaders = event.links(VoirTags.ROLE_LEADER),
                website = event.value(VoirTags.WEBSITE)?.takeIf(VoirTags::isWebAddress),
                sources = event.sources(),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
            )
        }

        /** `kind | approval reference | date | source kind | source name | address`. */
        private fun financingOf(f: List<String?>): Financing? {
            val kind = FinancingKind.fromTag(f.at(0)) ?: return null
            val date = CivilDate.parseIso(f.at(2))
            val source = SourceKind.fromTag(f.at(3))?.let { k -> f.at(4)?.let { Source(k, it, f.at(5)?.takeIf(VoirTags::isWebAddress), date) } }
            return Financing(kind, f.at(1)?.takeIf { kind == FinancingKind.ASSOCIATION }, date, source)
        }

        /** `year | amount in euros ("4 210 345,60") | decree | address`; public aid exists since 1988. */
        private fun fundingOf(f: List<String?>): PublicFunding? {
            val year = f.at(0)?.toIntOrNull()?.takeIf { it in 1988..2100 } ?: return null
            val amount = f.at(1)?.let(Indicator::parseNumber)?.takeIf { it >= 0 } ?: return null
            val decree = f.at(2)?.let { Source(SourceKind.OFFICIAL_TEXT, it, f.at(3)?.takeIf(VoirTags::isWebAddress), null) }
            return PublicFunding(year, kotlin.math.round(amount * 100).toLong(), decree)
        }
    }
}

/** The party a public person belongs to (`a` tag of role `parti`), when the sheet names one. */
val PublicPerson.partyAddress: ContentAddress? get() = article.event.links(VoirTags.ROLE_PARTY).firstOrNull()

/** The parties a fact concerns as such (a programme promise, a conviction of the party as a legal person, a group vote). */
val Fact.parties: List<ContentAddress> get() = article.event.links(VoirTags.ROLE_PARTY)
