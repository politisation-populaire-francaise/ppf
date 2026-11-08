package org.politisationpopulairefrancaise.core.voir.parlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParliamentHistoryTest {

    private val pkg = HistoryPackage(
        example = true,
        chamber = Chamber.AN,
        period = 14,
        coverage = HistoryCoverage("2012-07-10", "2016-11-24"),
        parties = listOf(
            PartyRef("an:PO1", "[EXEMPLE] Parti", "P", PartyBasis.PARTY),
            PartyRef("an:PO2", "[EXEMPLE] Groupe", "G", PartyBasis.GROUP, ParliamentHistory.GROUP_BASIS_LABEL),
        ),
        ballots = listOf(
            HistoryBallot(
                id = "an:14:1",
                date = "2012-07-10",
                title = "[EXEMPLE] titre",
                counts = Counts(inFavour = 3, against = 1, abstention = 0),
                parties = listOf(
                    PartyDistribution("an:PO1", PartyBasis.PARTY, inFavour = 3, against = 0, abstention = 0, notVoting = 0, members = 4),
                    PartyDistribution("an:PO2", PartyBasis.GROUP, inFavour = 0, against = 1, abstention = 0, notVoting = 0, members = 1),
                ),
            ),
        ),
    )

    @Test
    fun namesOfTheHistoryPackages() {
        assertEquals("historique-an-14", ParliamentFormat.historyPackage(Chamber.AN, 14))
        assertEquals("historique-senat-2025", ParliamentFormat.historyPackage(Chamber.SENAT, 2025))
        assertEquals("historique-pe-9", ParliamentFormat.historyPackage(Chamber.PE, 9))
        listOf("historique-an-14", "historique-senat-2006", "historique-pe-10").forEach { assertTrue(ParliamentFormat.isPackageName(it) && ParliamentHistory.isHistoryPackage(it), it) }
        assertEquals(Chamber.SENAT to 2006, ParliamentHistory.parse("historique-senat-2006"))
        listOf("historique-xx-1", "historique-an-", "historique-an-014", "historique-an-14-2", "scrutins-an-2026-09", "membres").forEach { assertNull(ParliamentHistory.parse(it), it) }
        assertFailsWith<IllegalArgumentException> { ParliamentFormat.historyPackage(Chamber.AN, 0) }
    }

    @Test
    fun aPackageIsReadAsTheOneItsNameAnnounces() {
        val json = ParliamentFormat.json.encodeToString(HistoryPackage.serializer(), pkg)
        assertEquals(pkg, ParliamentHistory.read("historique-an-14", json))
        assertNull(ParliamentHistory.read("historique-an-15", json))
        assertNull(ParliamentHistory.read("historique-pe-14", json))
        assertNull(ParliamentHistory.read("historique-an-14", "{\"not\": 1}"))
        // The basis of every line is always written; French field names as in the other packages.
        assertTrue("\"basis\":\"parti\"" in json && "\"basis\":\"groupe\"" in json, json)
        assertTrue("\"pour\":3" in json && "\"nonVotant\":0" in json && "\"members\":4" in json, json)
        assertTrue("\"coverage\":{\"first\":\"2012-07-10\",\"last\":\"2016-11-24\"}" in json, json)
        // Unknown fields of a newer writer are ignored.
        assertEquals(pkg, ParliamentHistory.read("historique-an-14", json.replaceFirst("{", "{\"later\":true,")))
        assertFalse(ParliamentHistory.isHistoryPackage("historique-AN-14"))
    }
}
