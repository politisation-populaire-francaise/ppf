package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * An alert of "En direct" (MA-04) drawn from "Voir": a vote coming soon in Parliament, or a recent
 * ban on demonstrating. Petitions and urgent calls will join them when their modules exist.
 */
sealed interface VoirAlert {
    val date: CivilDate

    /** A vote planned on a bill within the horizon. */
    data class UpcomingVote(val text: LegislativeText, val vote: PlannedVote, val daysLeft: Int) : VoirAlert {
        override val date: CivilDate get() = vote.date
    }

    /** A ban on demonstrating published within the horizon, whatever its appeals. */
    data class ProtestBan(val fact: Fact, val daysAgo: Int) : VoirAlert {
        override val date: CivilDate get() = fact.date
    }

    companion object {
        /** How far ahead (votes) and back (bans) an alert reaches. */
        const val HORIZON_DAYS: Int = 14

        /**
         * The alerts of [catalog] on [today]: the coming votes, nearest first, then the recent
         * bans, newest first. Nothing in the past for votes, nothing older than the horizon for bans.
         */
        fun of(catalog: VoirCatalog, today: CivilDate, horizonDays: Int = HORIZON_DAYS): List<VoirAlert> {
            val day = today.toEpochDays()
            val votes = catalog.texts.mapNotNull { text ->
                val vote = text.nextVote ?: return@mapNotNull null
                val left = (vote.date.toEpochDays() - day).toInt()
                if (left in 0..horizonDays && !text.stage.closed) UpcomingVote(text, vote, left) else null
            }.sortedWith(compareBy<UpcomingVote> { it.daysLeft }.thenBy { it.text.title })
            val bans = catalog.liberties(LibertyCategory.PROTEST_BAN).mapNotNull { fact ->
                val ago = (day - fact.date.toEpochDays()).toInt()
                if (ago in 0..horizonDays) ProtestBan(fact, ago) else null
            }
            return votes + bans
        }
    }
}
