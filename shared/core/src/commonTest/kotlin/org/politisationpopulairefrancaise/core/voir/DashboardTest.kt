package org.politisationpopulairefrancaise.core.voir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Tableau de bord fédérateur » (PP-07): every figure in its order, « non mesuré » rather than zero, any period. */
class DashboardTest {
    private val dashboard = DashboardFormat.of(
        "2026-11-09",
        mapOf(
            DashboardMetric.DEMANDS to mapOf("2026-10" to 3L, "2026-09" to 2L, "pas-un-mois" to 9L),
            DashboardMetric.CALLS to emptyMap(),
        ),
    )

    @Test
    fun everyFigureIsThereInItsOrderAndANotMeasuredOneIsNeverZero() {
        assertEquals(DashboardMetric.entries.map { it.code }, dashboard.metrics.map { it.code })
        val demands = dashboard.series(DashboardMetric.DEMANDS)
        assertEquals(listOf("2026-09", "2026-10"), demands.months.map { it.month }, "months sorted, a wrong key left out")
        assertEquals(5L, demands.total())
        assertEquals(3L, demands.total(from = "2026-10"))
        assertEquals(2L, demands.total(to = "2026-09"))
        assertEquals(0L, dashboard.series(DashboardMetric.CALLS).total(), "measured and nothing yet: zero is true")
        assertNull(dashboard.series(DashboardMetric.MEMBERS).total(), "not measured: never zero")
        assertFalse(dashboard.series(DashboardMetric.VOTES).measured)
        assertEquals(listOf("2026-09", "2026-10"), dashboard.months)
    }

    @Test
    fun theJsonIsReadBackAndTheMonthsAreChecked() {
        assertEquals(dashboard, DashboardFormat.read(DashboardFormat.write(dashboard)))
        assertTrue(DashboardFormat.isMonth("2026-11"))
        assertFalse(DashboardFormat.isMonth("2026-13"))
        assertFalse(DashboardFormat.isMonth("novembre"))
        assertEquals(DashboardUnit.EUROS, DashboardMetric.MONEY_COLLECTED.unit)
    }
}
