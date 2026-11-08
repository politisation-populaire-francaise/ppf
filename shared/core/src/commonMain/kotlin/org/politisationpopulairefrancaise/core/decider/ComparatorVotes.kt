package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryBallot
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryPackage
import org.politisationpopulairefrancaise.core.voir.parlement.PartyDistribution
import org.politisationpopulairefrancaise.core.voir.parlement.PartyRef

/**
 * The words of one theme (`ppf-theme-words` = `theme | word | word…`, D-117, option A): a ballot whose official
 * title holds one of them, accents and capitals aside, as a whole word or the start of one, belongs to the theme.
 * A ballot may belong to several themes; the same rule for every party, published in the signed sheet.
 */
data class ThemeWords(val theme: String, val words: List<String>) {
    private val folded: List<String> = words.map(FrenchText::fold).filter { it.isNotEmpty() }.distinct()

    fun matches(title: String): Boolean = matchesFolded(FrenchText.fold(title))

    internal fun matchesFolded(foldedTitle: String): Boolean {
        val padded = " $foldedTitle"
        return folded.any { padded.contains(" $it") }
    }

    companion object {
        /** Enough words for a theme, few enough to read under the list. */
        const val MAX_WORDS: Int = 60

        fun parse(fields: List<String?>): ThemeWords? {
            val theme = fields.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val words = fields.drop(1).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            if (words.isEmpty() || words.size > MAX_WORDS || words.any { FrenchText.fold(it).isEmpty() }) return null
            return ThemeWords(theme, words)
        }
    }
}

/**
 * One version of the words (`ppf-words-version` = `version | date | reason`): the public history of the rule,
 * kept in the signed sheet (D-117, safeguard 2). Every change is dated and explained; the sheet shows the current one.
 */
data class WordsVersion(val version: Int, val date: CivilDate, val reason: String) {
    companion object {
        fun parse(fields: List<String?>): WordsVersion? {
            val version = fields.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it >= 1 } ?: return null
            val date = CivilDate.parseIso(fields.getOrNull(1)) ?: return null
            val reason = fields.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return WordsVersion(version, date, reason)
        }
    }
}

/**
 * The party that carries a candidate in the history packages of "Voir", for a period (`ppf-party` = `candidate |
 * party | from | to`, D-117): `an:PO…` (party of the Assemblée), `senat-grp:…` (group of the Sénat, the only thing
 * it publishes), `pe-org:…` (national party of the members of the European Parliament elected in France).
 */
data class CandidateParty(val candidate: String, val party: String, val from: CivilDate, val to: CivilDate?) {
    fun covers(date: CivilDate): Boolean = date >= from && (to == null || date <= to)

    fun concerns(candidate: ContentAddress): Boolean = candidate.identifier == this.candidate

    companion object {
        fun parse(fields: List<String?>): CandidateParty? {
            val candidate = fields.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val party = fields.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() && ':' in it } ?: return null
            val from = CivilDate.parseIso(fields.getOrNull(2)) ?: return null
            val toText = fields.getOrNull(3)?.trim().orEmpty()
            val to = if (toText.isEmpty()) null else CivilDate.parseIso(toText) ?: return null
            if (to != null && to < from) return null
            return CandidateParty(candidate, party, from, to)
        }
    }
}

/** One public ballot with how the members of the candidate's party voted: numbers only, never a computed position. */
data class PartyBallot(
    val chamber: Chamber,
    val ballot: HistoryBallot,
    val date: CivilDate,
    /** The party or group as the package names it; null when the package forgot it. */
    val party: PartyRef?,
    val distribution: PartyDistribution,
) {
    internal val foldedTitle: String = FrenchText.fold(ballot.title)
}

/**
 * Every public ballot of the history packages where the party of a candidate voted (D-117), newest first: the
 * themes help to read, they never filter what can be seen ([all], [search] are always there).
 */
class CandidateVotes(val candidate: ContentAddress, val all: List<PartyBallot>) {
    /** The ballots of [rule]'s theme, newest first. */
    fun inTheme(rule: ThemeWords): List<PartyBallot> = all.filter { rule.matchesFolded(it.foldedTitle) }

    /** The ballots no theme takes, listed apart so that nothing is left out. */
    fun outsideEveryTheme(rules: List<ThemeWords>): List<PartyBallot> = all.filter { b -> rules.none { it.matchesFolded(b.foldedTitle) } }

    /** Free search in the official titles, on the device: every word of [query] starts a word of the title. */
    fun search(query: String): List<PartyBallot> = all.filter { FrenchText.matchesAllWords(it.ballot.title, query) }

    fun inChamber(chamber: Chamber): List<PartyBallot> = all.filter { it.chamber == chamber }

    companion object {
        /**
         * The ballots of [candidate] in [packages], by the parties of [sheet]: for each ballot, the party of the
         * candidate at its date in its assembly, when it voted. The same computation for every candidate.
         */
        fun of(sheet: ComparatorSheet, candidate: ContentAddress, packages: List<HistoryPackage>): CandidateVotes =
            collect(sheet, candidate, packages.asSequence())

        /**
         * The same, reading the packages one at a time ([read], null for a package that is not on the device): a
         * legislature weighs a few megabytes, so only the ballots of the candidate's party are kept, without the
         * distributions of the other parties.
         */
        fun collect(sheet: ComparatorSheet, candidate: ContentAddress, names: List<String>, read: (String) -> HistoryPackage?): CandidateVotes =
            collect(sheet, candidate, names.asSequence().mapNotNull(read))

        private fun collect(sheet: ComparatorSheet, candidate: ContentAddress, packages: Sequence<HistoryPackage>): CandidateVotes {
            val parties = sheet.parties.filter { it.concerns(candidate) }
            if (parties.isEmpty()) return CandidateVotes(candidate, emptyList())
            val ballots = packages.flatMap { pack ->
                val refs = pack.parties.associateBy { it.id }
                pack.ballots.asSequence().mapNotNull { ballot ->
                    val date = CivilDate.parseIso(ballot.date) ?: return@mapNotNull null
                    val distribution = parties.asSequence()
                        .filter { it.covers(date) }
                        .firstNotNullOfOrNull { p -> ballot.parties.firstOrNull { it.party == p.party } }
                        ?: return@mapNotNull null
                    PartyBallot(pack.chamber, ballot.copy(parties = emptyList()), date, refs[distribution.party], distribution)
                }.toList()
            }.toList()
                .distinctBy { it.ballot.id }
                .sortedWith(compareByDescending<PartyBallot> { it.date }.thenByDescending { it.ballot.id })
            return CandidateVotes(candidate, ballots)
        }
    }
}
