package org.politisationpopulairefrancaise.core.agir.engagement

import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « S'engager en politique » (D-196): registering comes first, then the two rounds, each until its day. */
class EngagementTest {

    @Test
    fun theNextMilestoneFollowsTheCalendar() {
        assertEquals(Engagement.REGISTRATION_DEADLINE, Engagement.nextMilestone(CivilDate(2026, 11, 11)))
        assertEquals(Engagement.REGISTRATION_DEADLINE, Engagement.nextMilestone(CivilDate(2027, 3, 12)))
        assertEquals(Engagement.PRESIDENTIAL_FIRST_ROUND, Engagement.nextMilestone(CivilDate(2027, 3, 13)))
        assertEquals(Engagement.PRESIDENTIAL_SECOND_ROUND, Engagement.nextMilestone(CivilDate(2027, 4, 19)))
        assertNull(Engagement.nextMilestone(CivilDate(2027, 5, 3)))
    }

    @Test
    fun daysAreCountedInWholeDays() {
        assertEquals(121, Engagement.daysUntil(Engagement.REGISTRATION_DEADLINE, CivilDate(2026, 11, 11)))
        assertEquals(0, Engagement.daysUntil(Engagement.REGISTRATION_DEADLINE, CivilDate(2027, 3, 12)))
    }

    @Test
    fun theDatesAreInOrderAnnouncedUntilTheDecreeAndTheRegistrationEndsOnAFriday() {
        val dates = Engagement.milestones.map { it.date }
        assertEquals(dates.sorted(), dates)
        assertTrue(Engagement.milestones.none { it.confirmed })
        // Code électoral, L17: the sixth Friday before the first round.
        assertEquals(5, ParisTime.dayOfWeek(ParisTime.epochDayOf(Engagement.REGISTRATION_DEADLINE.date)))
        assertEquals(42, Engagement.daysUntil(Engagement.PRESIDENTIAL_FIRST_ROUND, Engagement.REGISTRATION_DEADLINE.date) + 5)
        assertFalse(Engagement.milestones.any { !it.source.startsWith("https://") })
    }
}
