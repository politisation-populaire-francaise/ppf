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
    fun noBallotGivesEmptyColumns() {
        val empty = ChoiceCounts(emptyMap(), 0)
        assertEquals(UrneResults(empty, empty, empty, empty), UrneTally.fromCounts(emptyMap()))
    }
}
