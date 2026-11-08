package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.urne.ChoiceCounts
import org.politisationpopulairefrancaise.core.urne.UrneResults
import kotlin.test.Test
import kotlin.test.assertEquals

/** The thresholds of the owner's decision (D-115): proportional to the population, floors 25 and 100. */
class SupportThresholdsTest {
    private val france = 68_094_280L

    @Test
    fun thresholdsFollowThePetitionsOfTheAssemblyAtEveryScale() {
        assertEquals(SupportThresholds.Thresholds(100_000, 500_000), SupportThresholds.of(france, france))
        assertEquals(SupportThresholds.Thresholds(73, 367), SupportThresholds.of(50_000, france))
        assertEquals(SupportThresholds.Thresholds(112, 562), SupportThresholds.of(76_486, france), "Lozère")
        assertEquals(SupportThresholds.Thresholds(12_050, 60_251), SupportThresholds.of(8_205_557, france), "Auvergne-Rhône-Alpes")
        // A village: the floors.
        assertEquals(SupportThresholds.Thresholds(25, 100), SupportThresholds.of(500, france))
    }

    private fun results(pour: Long, contre: Long, hidden: Boolean = false): UrneResults {
        val citizens = if (hidden) ChoiceCounts(emptyMap(), 0, hidden = true) else ChoiceCounts(mapOf("contre" to contre, "pour" to pour), pour + contre)
        val none = ChoiceCounts(emptyMap(), 0)
        return UrneResults(none, none, citizens, none)
    }

    @Test
    fun aDemandIsCarriedWhenItsSupportsPassTheThresholdAndTheAgainst() {
        val village = SupportThresholds.of(500, france)
        assertEquals(SupportThresholds.Stage.BELOW, SupportThresholds.stage(results(25, 0), village), "« plus de » 25")
        assertEquals(SupportThresholds.Stage.CARRY, SupportThresholds.stage(results(26, 3), village))
        assertEquals(SupportThresholds.Stage.BELOW, SupportThresholds.stage(results(60, 60), village), "not more than the contre")
        assertEquals(SupportThresholds.Stage.PETITION, SupportThresholds.stage(results(101, 40), village))
        assertEquals(SupportThresholds.Stage.BELOW, SupportThresholds.stage(results(0, 0, hidden = true), village), "hidden: unknown")
    }
}

/** The pact keeps the 5 to 10 most supported measures with more « pour » than « contre » (D-115). */
class MeasureSelectionTest {
    private fun results(pour: Long, contre: Long): UrneResults {
        val none = ChoiceCounts(emptyMap(), 0)
        return UrneResults(none, none, ChoiceCounts(mapOf("contre" to contre, "pour" to pour), pour + contre), none)
    }

    @Test
    fun theMostSupportedMeasuresWithMorePourThanContre() {
        val candidates = (1..12).map { MeasureSelection.Candidate("m$it", results(pour = 100L + it, contre = 10)) } +
            MeasureSelection.Candidate("rejetee", results(pour = 500, contre = 600))
        val selection = MeasureSelection.select(candidates)
        assertEquals(listOf("m12", "m11", "m10", "m9", "m8", "m7", "m6", "m5", "m4", "m3"), selection.measures)
        assertEquals(false, selection.tooFew)
        val short = MeasureSelection.select(listOf(MeasureSelection.Candidate("a", results(3, 1)), MeasureSelection.Candidate("b", results(1, 3))))
        assertEquals(listOf("a"), short.measures)
        assertEquals(true, short.tooFew)
    }
}
