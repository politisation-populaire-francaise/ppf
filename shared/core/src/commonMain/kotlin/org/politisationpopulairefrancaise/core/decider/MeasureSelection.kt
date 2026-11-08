package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.urne.UrneResults

/**
 * Which measures of a deliberation enter the democratic pact (D-115, owner's decision of 7 November
 * 2026, option A): the 5 to 10 measures with the most supports among those with more `pour` than
 * `contre`. The national threshold is not required: the pact itself is how PPF carries them to the
 * candidates. Fewer than 5 such measures: the list is said to be short, never completed by the team.
 */
object MeasureSelection {
    const val MIN: Int = 5
    const val MAX: Int = 10

    /** One measure and its results at the end of the final vote. */
    data class Candidate<T>(val measure: T, val results: UrneResults)

    data class Selection<T>(val measures: List<T>, val tooFew: Boolean)

    /**
     * The measures kept, the most supported first; equal supports: fewer `contre` first, then the
     * order given (the order of the deliberation), so that the rule never depends on anyone's choice.
     */
    fun <T> select(candidates: List<Candidate<T>>): Selection<T> {
        fun count(results: UrneResults, choice: String) = results.citizens.counts[choice] ?: 0L
        val kept = candidates.withIndex()
            .filter { (_, c) -> !c.results.citizens.hidden && count(c.results, "pour") > count(c.results, "contre") }
            .sortedWith(
                compareByDescending<IndexedValue<Candidate<T>>> { count(it.value.results, "pour") }
                    .thenBy { count(it.value.results, "contre") }
                    .thenBy { it.index },
            )
            .take(MAX)
            .map { it.value.measure }
        return Selection(kept, tooFew = kept.size < MIN)
    }
}
