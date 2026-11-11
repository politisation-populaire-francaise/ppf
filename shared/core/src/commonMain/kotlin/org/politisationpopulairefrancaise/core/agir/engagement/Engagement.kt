package org.politisationpopulairefrancaise.core.agir.engagement

import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * A dated milestone of the electoral calendar shown by « S'engager en politique » (D-196), with the official page
 * it was read on. [confirmed] is false while the date is only announced (the decree of convocation not published).
 */
data class ElectoralMilestone(
    val id: String,
    val date: CivilDate,
    val confirmed: Boolean,
    val source: String,
)

/**
 * The four steps of « S'engager en politique », in the order of the electoral calendar decided by the owner (D-213):
 * register first, then the presidential election, then stand and build a list, then create or join a party.
 * The same tools for every list and every party, with no support of PPF for any of them (code électoral, L52-8).
 */
enum class EngagementStep(val id: String) {
    REGISTER("s-inscrire"),
    PRESIDENTIAL("presidentielle"),
    STAND("se-presenter"),
    PARTY("parti"),
}

/**
 * The dates and stable identifiers the rubric relies on. Every date was read on its official source on
 * [CHECKED_ON]; review them at each new decree (the decree convening the voters for the presidential election
 * is expected by 7 February 2027 at the latest, loi n° 62-1292, article 1 bis).
 */
object Engagement {
    /** The day the dates and links below were read on their sources. */
    val CHECKED_ON: CivilDate = CivilDate(2026, 11, 11)

    /** Last day to register for the presidential election: the sixth Friday before the first round (code électoral, L17). */
    val REGISTRATION_DEADLINE = ElectoralMilestone(
        id = "inscription-presidentielle-2027",
        date = CivilDate(2027, 3, 12),
        confirmed = false,
        source = "https://www.info.gouv.fr/actualite/comment-voter-a-la-presidentielle-2027",
    )

    /** First and second rounds of the presidential election, as announced. */
    val PRESIDENTIAL_FIRST_ROUND = ElectoralMilestone("presidentielle-2027-1", CivilDate(2027, 4, 18), confirmed = false, source = CALENDAR_SOURCE)
    val PRESIDENTIAL_SECOND_ROUND = ElectoralMilestone("presidentielle-2027-2", CivilDate(2027, 5, 2), confirmed = false, source = CALENDAR_SOURCE)

    /** The official calendar of the next elections (service-public.gouv.fr, F1939). */
    const val CALENDAR_SOURCE: String = "https://www.service-public.gouv.fr/particuliers/vosdroits/F1939"

    /** Registering on the electoral rolls, conditions and online service (service-public.gouv.fr, F1367). */
    const val REGISTER_PAGE: String = "https://www.service-public.gouv.fr/particuliers/vosdroits/F1367"

    /** Checking one's registration and polling station: the official online service « ISE » (service-public.gouv.fr, R51788). */
    const val CHECK_REGISTRATION: String = "https://www.service-public.gouv.fr/particuliers/vosdroits/R51788"

    /**
     * Code électoral on Légifrance, its version in force (article L52-8, chapter V bis: why PPF supports no list and
     * no party). The whole code rather than one version of the article, whose identifier changes with each amendment.
     */
    const val L52_8: String = "https://www.legifrance.gouv.fr/codes/texte_lc/LEGITEXT000006070239/"

    // Stable identifiers of the contents the steps open (practical guides and courses of « Comprendre »).
    const val GUIDE_PROXY: String = "voter-par-procuration"
    const val COURSE_REGISTER: String = "s-inscrire-et-voter"
    const val COURSE_STAND: String = "se-presenter-a-une-election"
    const val GUIDE_CITIZEN_LIST: String = "constituer-une-liste-citoyenne"
    const val GUIDE_2028: String = "se-presenter-departementales-regionales-2028"
    const val GUIDE_PARTY: String = "creer-ou-rejoindre-un-parti"

    /** The dated milestones, in calendar order. */
    val milestones: List<ElectoralMilestone> = listOf(REGISTRATION_DEADLINE, PRESIDENTIAL_FIRST_ROUND, PRESIDENTIAL_SECOND_ROUND)

    /** The next milestone from [today] (included), or null once they are all past. */
    fun nextMilestone(today: CivilDate): ElectoralMilestone? = milestones.firstOrNull { it.date >= today }

    /** Whole days from [today] to [milestone] (0 on the day itself). */
    fun daysUntil(milestone: ElectoralMilestone, today: CivilDate): Long =
        ParisTime.epochDayOf(milestone.date) - ParisTime.epochDayOf(today)
}
