package org.politisationpopulairefrancaise.core.urne

import kotlin.test.Test
import kotlin.test.assertEquals

/** The two ways of counting (ballot by ballot, from a database's sums) give the same results (D-112). */
class UrneTallyTest {
    private val ballots = listOf(
        VoteLevel.VERIFIE to "pour",
        VoteLevel.VERIFIE to "pour",
        VoteLevel.VERIFIE to "contre",
        VoteLevel.NON_VERIFIE to "contre",
        VoteLevel.NON_VERIFIE to UrneInfo.WITHDRAWN,
        VoteLevel.ELU to "pour",
    )

    @Test
    fun countsGiveTheSameResultsAsBallots() {
        val sums = ballots.groupingBy { it }.eachCount().mapValues { it.value.toLong() }
        val results = UrneTally.fromCounts(sums)
        assertEquals(UrneTally.results(ballots), results)
        assertEquals(ChoiceCounts(mapOf("contre" to 2L, "pour" to 2L), 4), results.citizens)
        // Elected officials apart, never in the citizens' total; a withdrawn ballot counts nowhere.
        assertEquals(ChoiceCounts(mapOf("pour" to 1L), 1), results.electedOfficials)
        assertEquals(ChoiceCounts(mapOf("contre" to 1L), 1), results.notVerified)
    }

    @Test
    fun aFewBallotsOfOneLevelAreNotShownWhileTheBoxIsOpen() {
        val many = (1..20).map { VoteLevel.NON_VERIFIE to "pour" }
        val two = listOf(VoteLevel.VERIFIE to "contre", VoteLevel.VERIFIE to "pour")
        val shown = UrneTally.hidingSmall(UrneTally.results(many + two + listOf(VoteLevel.ELU to "pour")), minimum = 5)
        assertEquals(true, shown.verified.hidden)
        // The total minus the other column would give the hidden one back: hidden too.
        assertEquals(true, shown.citizens.hidden)
        assertEquals(20L, shown.notVerified.ballots)
        assertEquals(true, shown.electedOfficials.hidden)
        assertEquals(5, shown.minimum)
        // Enough everywhere, or nobody at a level: nothing hidden.
        val enough = UrneTally.results(many + (1..5).map { VoteLevel.VERIFIE to "contre" })
        assertEquals(enough, UrneTally.hidingSmall(enough, minimum = 5))
        assertEquals(false, UrneTally.hidingSmall(UrneTally.results(many), minimum = 5).verified.hidden)
    }

    @Test
    fun noBallotGivesEmptyColumns() {
        val empty = ChoiceCounts(emptyMap(), 0)
        assertEquals(UrneResults(empty, empty, empty, empty), UrneTally.fromCounts(emptyMap()))
    }
}
