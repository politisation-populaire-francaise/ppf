package org.politisationpopulairefrancaise.server.decider

import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** PP-07: what each month added to the official readings of a petition. */
class DeciderDashboardFiguresTest {

    @Test
    fun aMonthAddsWhatItsLastReadingGoesBeyondTheHighestBefore() {
        val readings = listOf(
            CivilDate(2026, 10, 2) to 10_000L,
            CivilDate(2026, 10, 30) to 25_000L,
            CivilDate(2026, 11, 15) to 24_000L,
            CivilDate(2026, 12, 1) to 40_000L,
        )
        // October: 25 000 (its last reading); November: a correction of the official site, nothing added; December: 15 000.
        assertEquals(mapOf("2026-10" to 25_000L, "2026-12" to 15_000L), DeciderDashboardFigures.monthlyIncrements(readings.shuffled()))
        assertEquals(40_000L, DeciderDashboardFigures.monthlyIncrements(readings).values.sum(), "the months add up to the highest reading")
        assertEquals(emptyMap<String, Long>(), DeciderDashboardFigures.monthlyIncrements(emptyList()))
    }
}
