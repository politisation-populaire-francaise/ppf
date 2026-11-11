package org.politisationpopulairefrancaise.core.agir.agenda

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.AgirHighlights
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** « En ce moment » of « Agir »: the mobilizations of the coming week, under way first in time, never the cancelled ones. */
class AgirHighlightsTest {
    private val fixtures = AgendaFixtures()

    @Test
    fun theComingWeekInOrderWithoutCancelledOrPastOrTooFar() = runTest {
        val now = AgendaTimes.NOW
        val saturday = fixtures.mobilization("samedi")
        val underWay = fixtures.mobilization("en-cours", start = now - AgendaTimes.HOUR, end = now + AgendaTimes.HOUR)
        val cancelled = fixtures.mobilization("annule", status = MobilizationStatus.ANNULE)
        val past = fixtures.mobilization("passe", start = now - AgendaTimes.DAY, end = now - AgendaTimes.DAY + AgendaTimes.HOUR)
        val far = fixtures.mobilization("loin", start = now + 10 * AgendaTimes.DAY, end = null)
        val highlights = AgirHighlights.of(listOf(saturday, underWay, cancelled, past, far), emptyList(), now)
        assertEquals(listOf("en-cours", "samedi"), highlights.mobilizations.map { it.identifier })
        assertTrue(AgirHighlights.isUnderWay(underWay, now))
        assertTrue(AgirHighlights.of(listOf(far, past), emptyList(), now).isEmpty)
    }
}
