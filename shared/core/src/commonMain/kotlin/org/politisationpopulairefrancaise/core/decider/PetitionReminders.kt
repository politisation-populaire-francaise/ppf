package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime

/**
 * The reminders of an official petition (MVP 1, jalon 3): a week before its closing and the day before, at 10 h, Paris
 * time, so that no one misses the time to sign it on the official site. Off by default (principle 10), asked on the page
 * of the petition, programmed and kept on the device only, like those of the consultations. A petition without a closing
 * date has none.
 */
object PetitionReminders {
    const val OWNER_PREFIX: String = "decider:petition:"

    const val WEEK_BEFORE: String = "semaine"
    const val EVE: String = "veille"

    const val WEEK_DAYS: Int = 7

    fun owner(petition: Petition): String = OWNER_PREFIX + petition.article.slug

    fun reminderId(petition: Petition, key: String): String = owner(petition) + "/" + key

    /** `key` to due time, those after [now] only, the nearest first; nothing without a closing date. */
    fun plan(petition: Petition, now: Long): List<Pair<String, Long>> {
        val closes = petition.closesOn ?: return emptyList()
        return listOf(
            WEEK_BEFORE to CivilDate.fromEpochDays(closes.toEpochDays() - WEEK_DAYS),
            EVE to CivilDate.fromEpochDays(closes.toEpochDays() - 1),
        ).map { (key, day) -> key to ParisTime.toEpochSeconds(day, ElectionReminders.HOUR, 0) }
            .filter { it.second > now }
            .sortedBy { it.second }
    }
}
