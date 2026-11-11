package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.text.FrenchText

/** A ballot not seen yet where at least one of my parliamentarians is listed, with how each of them voted. */
data class VoteAlert(val entry: BallotEntry, val votes: List<MyVote>)

/** How one of my parliamentarians voted in one ballot: the recorded position, the delegation, the correction. */
data class MyVote(val member: Member, val vote: MemberVote)

/** A ballot not seen yet whose title contains a followed word (or every word of a followed phrase). */
data class WordAlert(val entry: BallotEntry, val word: String)

/**
 * « Mes élus ont voté » and the followed words (VO-34; docs/integration/voir-alertes-elus.md, sections 3 and 4),
 * computed on the device only from the packages it holds, the same for everyone: the server never learns who
 * the person's officials are nor which words they follow.
 *
 * [seen] is the set of ballot ids already shown to the person (`parliament_ballot_seen`, dated by reception on the
 * device); the first time, everything already on the device is marked seen without any alert.
 */
object VoteAlerts {
    /** At most this many followed words or phrases, kept on the device. */
    const val MAX_FOLLOWED_WORDS: Int = 50

    /** The ballots of the assemblies of [members] not in [seen] where one of them is listed, most recent first. */
    fun newVotes(members: List<Member>, catalog: ParliamentCatalog, seen: Set<String>): List<VoteAlert> {
        if (members.isEmpty()) return emptyList()
        val chambers = members.map { it.chamber }.toSet()
        return catalog.ballots()
            .filter { it.chamber in chambers && it.ballot.id !in seen }
            .mapNotNull { entry ->
                val votes = members.filter { it.chamber == entry.chamber }.mapNotNull { member -> catalog.voteOf(entry, member.id)?.let { MyVote(member, it) } }
                votes.takeIf { it.isNotEmpty() }?.let { VoteAlert(entry, it) }
            }
    }

    /**
     * The ballots not in [seen] whose official title contains every word of a followed entry (accents and case
     * ignored, the beginning of a word is enough: « retraite » finds « retraites »), most recent first; one alert
     * per ballot, for the first entry it matches.
     */
    fun newWordMatches(words: List<String>, catalog: ParliamentCatalog, seen: Set<String>): List<WordAlert> {
        val wanted = words.map { it.trim() }.filter { FrenchText.words(it).isNotEmpty() }.distinctBy { FrenchText.fold(it) }.take(MAX_FOLLOWED_WORDS)
        if (wanted.isEmpty()) return emptyList()
        return catalog.ballots()
            .filter { it.ballot.id !in seen }
            .mapNotNull { entry -> wanted.firstOrNull { FrenchText.matchesAllWords(entry.ballot.title, it) }?.let { WordAlert(entry, it) } }
    }

    /** The ids to mark seen after the person was told, or the first time: every ballot on the device. */
    fun allBallotIds(catalog: ParliamentCatalog): Set<String> = catalog.ballots().map { it.ballot.id }.toSet()
}
