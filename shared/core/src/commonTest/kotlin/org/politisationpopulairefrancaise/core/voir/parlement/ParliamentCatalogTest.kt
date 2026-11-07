package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParliamentCatalogTest {

    private val catalog = ParliamentFixtures.catalog()

    @Test
    fun membersAreSortedByNameWithoutTheExampleMarkerNorAccents() {
        assertEquals(
            listOf("an:PA900003", "senat:90001A", "pe:990001", "an:PA900001", "an:PA900002"),
            catalog.members().map { it.id },
        )
    }

    @Test
    fun theSearchIgnoresAccentsAndCaseAndLooksAtGroupAndTerritory() {
        assertEquals(listOf("an:PA900001"), catalog.members("elise").map { it.id })
        assertEquals(listOf("an:PA900003"), catalog.members("COTE paris").map { it.id })
        assertEquals(listOf("an:PA900003", "an:PA900002"), catalog.members("tilleuls").map { it.id })
        assertEquals(listOf("an:PA900002"), catalog.members("rhone").map { it.id })
        assertTrue(catalog.members("personne inconnue").isEmpty())
    }

    @Test
    fun filtersByChamberAndGroup() {
        assertEquals(listOf("senat:90001A"), catalog.members(chamber = Chamber.SENAT).map { it.id })
        assertEquals(listOf("an:PA900001"), catalog.members(chamber = Chamber.AN, group = "an:PO900002").map { it.id })
        assertEquals(listOf("an:PO900002", "an:PO900001"), catalog.groups(Chamber.AN).map { it.id })
        assertEquals(listOf(Chamber.AN, Chamber.SENAT, Chamber.PE), catalog.chambers())
    }

    @Test
    fun ballotsComeMostRecentFirstAndByDossier() {
        assertEquals(listOf("an:17:103", "an:17:102", "an:17:101"), catalog.ballots(Chamber.AN).map { it.ballot.id })
        assertTrue(catalog.ballots(Chamber.SENAT).isEmpty())
        assertEquals(listOf("an:17:103", "an:17:101"), catalog.ballotsOfDossier("an-dossier:DLR5L17N90001").map { it.ballot.id })
    }

    @Test
    fun theVotesOfAMemberKeepDelegationCorrectionAndCause() {
        val arnaud = catalog.votesOf("an:PA900001")
        assertEquals(listOf("an:17:102", "an:17:101"), arnaud.map { it.ballot.id })
        assertEquals(Position.NOT_VOTING, arnaud[0].position)
        assertEquals("PSE", arnaud[0].notVotingCause)
        assertEquals(Position.AGAINST, arnaud[1].position)
        assertTrue(arnaud[1].delegated)

        val vasseur = catalog.votesOf("an:PA900002")
        // The recorded vote stays; the correction is beside it.
        assertEquals(Position.AGAINST, vasseur[0].position)
        assertEquals(Position.FOR, vasseur[0].correction?.intended)
    }

    @Test
    fun participationIsTheShareOfTheBallotsOfTheMandateWhereTheMemberVoted() {
        // Arnaud: three ballots of the Assemblée since 2024, voted in one (against), did not vote in one, absent from one.
        val arnaud = catalog.participation("an:PA900001")!!
        assertEquals(1, arnaud.voted)
        assertEquals(3, arnaud.total)
        assertEquals(33, arnaud.percent)
        assertEquals(CivilDate(2026, 10, 8), arnaud.from)
        assertEquals(CivilDate(2026, 11, 3), arnaud.to)
        // Côté's mandate starts on 2026-10-15: only the two later ballots count.
        val cote = catalog.participation("an:PA900003")!!
        assertEquals(1, cote.voted)
        assertEquals(2, cote.total)
        assertEquals(50, cote.percent)
        // No ballot of the Sénat on the device: no figure rather than a false zero.
        assertNull(catalog.participation("senat:90001A"))
    }

    @Test
    fun membersOfABallotAreGroupedByPositionAndByName() {
        val ballot = catalog.ballot("an:17:101")!!.ballot
        val byPosition = catalog.membersByPosition(ballot)
        assertEquals(listOf(Position.FOR, Position.AGAINST, Position.ABSTENTION, Position.NOT_VOTING), byPosition.keys.toList())
        assertEquals("[EXEMPLE] Zoé Vasseur", byPosition.getValue(Position.FOR).single().member?.name)
        assertTrue(byPosition.getValue(Position.ABSTENTION).isEmpty())
    }

    @Test
    fun theMonthsOfTheIndexAreReadFromThePackageNames() {
        val index = ParliamentFixtures.index(ParliamentFixtures.packages() + ("scrutins-an-2026-13" to ByteArray(1)))
        assertEquals(listOf("2026-11", "2026-10"), ParliamentCatalog.months(index, Chamber.AN).map { it.month })
        assertTrue(ParliamentCatalog.months(index, Chamber.PE).isEmpty())
    }

    @Test
    fun theOfficialIdentifierIsReadFromTheTagOnly() {
        fun event(vararg tags: List<String>) = SignedEvent("a".repeat(64), "b".repeat(64), 1, 30023, tags.toList(), "", "c".repeat(128))
        assertEquals("an:PA900001", ParliamentLinks.memberId(event(listOf("ppf-official-id", "an:PA900001"))))
        assertEquals("an-dossier:DLR5L17N90001", ParliamentLinks.dossierId(event(listOf("ppf-official-id", "an-dossier:DLR5L17N90001"))))
        assertNull(ParliamentLinks.memberId(event(listOf("ppf-official-id", "an-dossier:DLR5L17N90001"))))
        assertNull(ParliamentLinks.memberId(event(listOf("ppf-official-id", "javascript:alert(1)"))))
        assertNull(ParliamentLinks.memberId(event()))
    }
}
