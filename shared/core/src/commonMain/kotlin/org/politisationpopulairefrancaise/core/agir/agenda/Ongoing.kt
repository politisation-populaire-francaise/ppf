package org.politisationpopulairefrancaise.core.agir.agenda

/**
 * The mobilizations happening right now, put forward on the map and in the agenda (D-197, tutorial of the first
 * launch: « la carte qui indique les manifestations en cours »). Only what the agenda says: an event between its
 * start and its end (or its default duration), not cancelled. The blockades counted are those announced by their
 * organisers in the agenda (type « blocage »), never a street closed by the forces of order (points of attention
 * n° 14 and 24, D-190).
 */
data class OngoingMobilizations(val all: List<Mobilization>) {
    val isEmpty: Boolean get() = all.isEmpty()
    val count: Int get() = all.size

    /** The announced blockades among them. */
    val blockades: Int get() = all.count { it.type == MobilizationType.BLOCAGE }

    companion object {
        /** The ongoing mobilizations of [mobilizations] at [now], those that end soonest first. */
        fun of(mobilizations: List<Mobilization>, now: Long): OngoingMobilizations =
            OngoingMobilizations(
                mobilizations.filter { it.isOngoing(now) && !it.cancelled }
                    .sortedWith(compareBy<Mobilization> { it.endsAt }.thenBy { it.start }.thenBy { it.title }),
            )
    }
}
