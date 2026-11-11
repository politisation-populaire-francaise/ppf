package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Présence et délégations » (VO-35): solemn ballots only, by group, every member-ballot counted once, never ranked. */
class PresenceAndDelegationsTest {

    @Test
    fun onlyTheSolemnBallotsOfTheMandateAreCountedByGroup() {
        val presence = PresenceAndDelegations.of(ParliamentFixtures.catalog(), Chamber.AN)!!
        // 101 is solemn; 102 is an ordinary ballot and 103 a motion of censure (only the votes in favour are recorded).
        assertEquals(listOf("an:17:101"), presence.ballots.map { it.ballot.id })
        assertEquals(CivilDate(2026, 10, 8), presence.from)
        // Alphabetical order of the groups, never an order of the figures.
        assertEquals(listOf("an:PO900002", "an:PO900001"), presence.groups.map { it.group.id })
        val acacias = presence.groups[0]
        assertEquals(GroupPresence(acacias.group, members = 1, expected = 1, personal = 0, delegated = 1, notVoting = 0, withoutVote = 0), acacias)
        // Bruno Côté's mandate starts after the ballot: only Zoé Vasseur counts for it.
        val tilleuls = presence.groups[1]
        assertEquals(2, tilleuls.members)
        assertEquals(1, tilleuls.expected)
        assertEquals(1, tilleuls.personal)
        assertEquals(100, tilleuls.percent(tilleuls.personal))
    }

    @Test
    fun theFourOutcomesAlwaysAddUpAndNothingIsShownWithoutSolemnBallot() {
        val ballot = Ballot(
            id = "an:17:200", number = 200, date = "2026-11-04", title = "[EXEMPLE] l'ensemble du projet de loi d'exemple", kind = "sps", adopted = true,
            counts = Counts(voters = 1, expressed = 1, inFavour = 1, against = 0, abstention = 0, notVoting = 1),
            positions = Positions(inFavour = listOf("an:PA900003"), notVoting = listOf("an:PA900002"), notVotingCauses = listOf(NotVotingCause("an:PA900002", "PSE"))),
        )
        val catalog = ParliamentCatalog(ParliamentFixtures.members, listOf(BallotsPackage(example = true, chamber = Chamber.AN, month = "2026-11", ballots = listOf(ballot))))
        val presence = PresenceAndDelegations.of(catalog, Chamber.AN)!!
        val tilleuls = presence.groups.single { it.group.id == "an:PO900001" }
        assertEquals(1, tilleuls.personal)
        assertEquals(1, tilleuls.notVoting)
        val acacias = presence.groups.single { it.group.id == "an:PO900002" }
        assertEquals(1, acacias.withoutVote, "no vote recorded: absent, or present without voting")
        presence.groups.forEach { assertEquals(it.expected, it.personal + it.delegated + it.notVoting + it.withoutVote, it.group.id) }

        // No major ballot defined for the other assemblies, and nothing without a solemn ballot or without members.
        assertNull(PresenceAndDelegations.of(catalog, Chamber.SENAT))
        assertNull(PresenceAndDelegations.of(catalog, Chamber.PE))
        assertNull(PresenceAndDelegations.of(ParliamentCatalog(ParliamentFixtures.members, listOf(ParliamentFixtures.novembre)), Chamber.AN))
        assertNull(PresenceAndDelegations.of(ParliamentCatalog(null, listOf(BallotsPackage(example = true, chamber = Chamber.AN, month = "2026-11", ballots = listOf(ballot)))), Chamber.AN))
        assertTrue(PresenceAndDelegations.isMajor(BallotEntry(ballot, Chamber.AN)))
    }
}
