package org.politisationpopulairefrancaise.core.agir

import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call

/**
 * « En ce moment » in « Agir » (and the blocks of « En direct »): the mobilizations under way or starting within
 * [AgirHighlights.AHEAD_SECONDS], and the open calls due within [AgirHighlights.URGENT_SECONDS]. Computed on the
 * device from the contents it already holds: nothing is asked of the server, nothing about the person is used.
 */
data class AgirHighlights(val mobilizations: List<Mobilization>, val urgentCalls: List<Call>) {
    val isEmpty: Boolean get() = mobilizations.isEmpty() && urgentCalls.isEmpty()

    companion object {
        const val AHEAD_SECONDS: Long = 7 * 24 * 3_600
        const val URGENT_SECONDS: Long = 3 * 24 * 3_600
        const val MAX_ITEMS: Int = 3

        val EMPTY = AgirHighlights(emptyList(), emptyList())

        /**
         * [near] (the person's territory, chosen on the device) puts what happens near them first; the order is kept
         * within each group.
         */
        fun of(mobilizations: List<Mobilization>, calls: List<Call>, now: Long, max: Int = MAX_ITEMS, near: ((String) -> Boolean)? = null): AgirHighlights {
            val rank: (String) -> Int = { territory -> if (near?.invoke(territory) == true) 0 else 1 }
            return AgirHighlights(
                mobilizations = mobilizations
                    .filter { !it.cancelled && !it.isPast(now) && it.start <= now + AHEAD_SECONDS }
                    .sortedWith(compareBy<Mobilization> { rank(it.territory) }.thenBy { it.start }.thenBy { it.displayTitle })
                    .take(max),
                urgentCalls = calls
                    .filter { call -> call.isOpen && call.deadline?.let { it in now..(now + URGENT_SECONDS) } == true }
                    .sortedWith(compareBy<Call> { rank(it.territory) }.thenBy { it.deadline })
                    .take(max),
            )
        }

        /** Whether [mobilization] is under way at [now]. */
        fun isUnderWay(mobilization: Mobilization, now: Long): Boolean = mobilization.start <= now && !mobilization.isPast(now)
    }
}
