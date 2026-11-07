package org.politisationpopulairefrancaise.core.agir.agenda

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AG-21: the events that look like a new one, the same day. */
class MobilizationDuplicatesTest {
    private val fixtures = AgendaFixtures()
    private val day = AgendaTimes.DAY

    private suspend fun existing() = listOf(
        fixtures.mobilization("marche", "[EXEMPLE] Marche pour l'hôpital public", location = "Place de la Gare, Villefictive", territory = "Villefictive"),
        fixtures.mobilization("assemblee", "[EXEMPLE] Assemblée des parents d'élèves", location = "Salle des fêtes, Villefictive", territory = "Villefictive"),
        fixtures.mobilization("lendemain", "[EXEMPLE] Marche pour l'hôpital public", start = AgendaTimes.SATURDAY_14H + day, end = null, territory = "Villefictive"),
        fixtures.mobilization("annulee", "[EXEMPLE] Marche pour l'hôpital public", status = MobilizationStatus.ANNULE, territory = "Villefictive"),
    )

    @Test
    fun aCloseTitleTheSameDayIsFoundAndOtherDaysAreNot() = runTest {
        val found = MobilizationDuplicates.find(
            title = "Marche pour sauver l'hôpital public",
            location = "Devant l'hôpital",
            territory = "Villefictive",
            start = AgendaTimes.SATURDAY_14H + 3_600,
            candidates = existing(),
        )
        assertEquals(listOf("marche"), found.map { it.mobilization.identifier }, "not the next day, not the cancelled one")
        assertTrue(found.single().closeTitle)
    }

    @Test
    fun aClosePlaceTheSameDayIsFoundToo() = runTest {
        val found = MobilizationDuplicates.find(
            title = "Rencontre des familles",
            location = "salle des fetes, Villefictive",
            territory = "Villefictive",
            start = AgendaTimes.SATURDAY_14H,
            candidates = existing(),
        )
        assertEquals(listOf("assemblee"), found.map { it.mobilization.identifier })
        assertTrue(found.single().closePlace)
        assertEquals(false, found.single().closeTitle)
    }

    @Test
    fun bothCloseComesFirstAndTheOwnEarlierVersionIsIgnored() = runTest {
        val candidates = existing()
        val found = MobilizationDuplicates.find(
            title = "[EXEMPLE] Marche pour l'hôpital public",
            location = "Place de la Gare, Villefictive",
            territory = "Villefictive",
            start = AgendaTimes.SATURDAY_14H,
            candidates = candidates,
        )
        assertEquals("marche", found.first().mobilization.identifier)
        assertTrue(found.first().closeTitle && found.first().closePlace)
        val own = candidates.first().address.toString()
        assertTrue(MobilizationDuplicates.find("[EXEMPLE] Marche pour l'hôpital public", "Place de la Gare, Villefictive", "Villefictive", AgendaTimes.SATURDAY_14H, candidates, ownAddress = own).none { it.mobilization.identifier == "marche" })
    }

    @Test
    fun aDifferentEventTheSameDayIsNotADuplicate() = runTest {
        val found = MobilizationDuplicates.find(
            title = "Projection d'un documentaire sur le logement",
            location = "Cinéma du centre, Bourg-Fictif",
            territory = "Bourg-Fictif",
            start = AgendaTimes.SATURDAY_14H,
            candidates = existing(),
        )
        assertEquals(emptyList(), found)
        // A place that only shares the name of the town is not close.
        assertEquals(emptyList(), MobilizationDuplicates.find("Projection", "Villefictive", "Villefictive", AgendaTimes.SATURDAY_14H, existing()))
    }
}
