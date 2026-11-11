package org.politisationpopulairefrancaise.core.agir.agenda

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-197: what is happening right now, from the dates of the agenda; blockades announced by their organisers only. */
class OngoingMobilizationsTest {
    private val fixtures = AgendaFixtures()
    private val now = AgendaTimes.SATURDAY_14H + AgendaTimes.HOUR

    @Test
    fun onlyTheEventsBetweenTheirStartAndTheirEndAndNotCancelled() = runTest {
        val rally = fixtures.mobilization("rassemblement")
        val blockade = fixtures.mobilization("blocage", type = MobilizationType.BLOCAGE, end = AgendaTimes.SATURDAY_14H + 90 * 60)
        val cancelled = fixtures.mobilization("annule", status = MobilizationStatus.ANNULE)
        val later = fixtures.mobilization("plus-tard", start = AgendaTimes.SATURDAY_17H, end = null)
        val over = fixtures.mobilization("fini", start = AgendaTimes.SATURDAY_14H - 4 * AgendaTimes.HOUR, end = AgendaTimes.SATURDAY_14H - AgendaTimes.HOUR)
        // Without an end, an event stays « en cours » for its default duration.
        val noEnd = fixtures.mobilization("sans-fin", start = AgendaTimes.SATURDAY_14H, end = null)

        val ongoing = OngoingMobilizations.of(listOf(rally, blockade, cancelled, later, over, noEnd), now)
        assertEquals(listOf("blocage", "rassemblement", "sans-fin").sorted(), ongoing.all.map { it.identifier }.sorted())
        // The one that ends soonest first: the blockade ends at 15 h 30.
        assertEquals("blocage", ongoing.all.first().identifier)
        assertEquals(3, ongoing.count)
        assertEquals(1, ongoing.blockades)
    }

    @Test
    fun nothingHappeningIsEmpty() = runTest {
        val later = fixtures.mobilization("plus-tard", start = AgendaTimes.SATURDAY_17H, end = null)
        assertTrue(OngoingMobilizations.of(listOf(later), AgendaTimes.NOW).isEmpty)
    }
}
