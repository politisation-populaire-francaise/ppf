package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryBallot
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryCoverage
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryPackage
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentHistory
import org.politisationpopulairefrancaise.core.voir.parlement.PartyBasis
import org.politisationpopulairefrancaise.core.voir.parlement.PartyDistribution
import org.politisationpopulairefrancaise.core.voir.parlement.PartyRef
import org.politisationpopulairefrancaise.core.voir.parlement.Position

/*
 * Common parts of the import of the history of the ballots (docs/integration/voir-historique-import.md):
 * the three history sources, dated memberships, and the counting of the votes per party.
 */

/** The history sources, by their identifier in the index and in `--only`. Run apart from the daily import. */
enum class HistorySourceId(val id: String, val chamber: Chamber, val sourceName: String, val url: String, val licence: String) {
    AN("an-history", Chamber.AN, "Assemblée nationale, données ouvertes (historique des scrutins)", "https://data.assemblee-nationale.fr/", "Licence Ouverte"),
    SENAT("senat-history", Chamber.SENAT, "Sénat, données ouvertes (base Dosleg et historique des groupes)", "https://data.senat.fr/", "Licence Ouverte 2.0"),
    PE("pe-history", Chamber.PE, "Parlement européen, données ouvertes (votes par appel nominal)", "https://data.europarl.europa.eu/", "CC BY 4.0"),
    ;

    /** Every package of this source starts with it: `historique-an-`. */
    val packagePrefix: String get() = ParliamentHistory.PREFIX + ParliamentHistory.chamberCode(chamber) + "-"

    companion object {
        fun of(id: String): HistorySourceId? = entries.firstOrNull { it.id == id }
    }
}

/** A dated membership: of an assembly, a group or a party ([organ]). Dates AAAA-MM-JJ, open when null. */
data class Span(val organ: String, val start: String?, val end: String?) {
    fun covers(day: String): Boolean = (start == null || start <= day) && (end == null || end >= day)
}

/** The membership among [spans] covering [day]; the latest started when several do. */
internal fun List<Span>.at(day: String): Span? = filter { it.covers(day) }.maxWithOrNull(compareBy<Span>({ it.start ?: "" }, { it.organ }))

/** A line of the distribution: a party, or a group standing for it. */
data class Line(val id: String, val basis: PartyBasis)

/** The votes of one ballot, counted per [Line] while the positions are read (never kept). */
internal class Distribution {
    private val counts = HashMap<Line, IntArray>()

    fun add(line: Line, position: Position, n: Int = 1) {
        val slot = when (position) {
            Position.FOR -> 0
            Position.AGAINST -> 1
            Position.ABSTENTION -> 2
            Position.NOT_VOTING -> 3
        }
        counts.getOrPut(line) { IntArray(4) }[slot] += n
    }

    val lines: Set<Line> get() = counts.keys

    /**
     * The distributions, sorted by party; [members] gives the members of a line at the date of the
     * ballot (never fewer than the votes counted, should the membership data lack someone).
     */
    fun build(members: (Line) -> Int): List<PartyDistribution> = counts.entries.sortedBy { it.key.id }.map { (line, c) ->
        PartyDistribution(
            party = line.id,
            basis = line.basis,
            inFavour = c[0],
            against = c[1],
            abstention = c[2],
            notVoting = c[3],
            members = maxOf(members(line), c.sum()),
        )
    }
}

/** A party named by a package: its label as the source writes it. */
internal fun partyRef(line: Line, label: String, abbreviation: String?): PartyRef = PartyRef(
    id = line.id,
    label = label,
    abbreviation = abbreviation?.takeIf { it != label },
    basis = line.basis,
    basisLabel = if (line.basis == PartyBasis.GROUP) ParliamentHistory.GROUP_BASIS_LABEL else null,
)

/** The package of one period: ballots sorted by date then identifier, parties by identifier, coverage. */
internal fun historyPackage(example: Boolean, chamber: Chamber, period: Int, ballots: List<HistoryBallot>, parties: Collection<PartyRef>): HistoryPackage {
    val sorted = ballots.sortedWith(compareBy({ it.date }, { ballotOrder(it.id) }, { it.id }))
    val used = sorted.flatMap { b -> b.parties.map { it.party } }.toSet()
    return HistoryPackage(
        example = example,
        chamber = chamber,
        period = period,
        coverage = if (sorted.isEmpty()) null else HistoryCoverage(sorted.first().date, sorted.last().date),
        parties = parties.filter { it.id in used }.distinctBy { it.id }.sortedBy { it.id },
        ballots = sorted,
    )
}

/** The number at the end of an identifier (`an:14:123` gives 123), to sort ballots of a day in their order. */
private fun ballotOrder(id: String): Int = id.substringAfterLast(':').toIntOrNull() ?: Int.MAX_VALUE
