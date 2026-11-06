package org.politisationpopulairefrancaise.core.agir.calls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParisTimeTest {

    @Test
    fun winterAndSummerOffsetsFollowTheEuropeanRule() {
        // 2026: summer time from Sunday 29 March 01:00 UTC to Sunday 25 October 01:00 UTC.
        val springChange = 1_774_746_000L // 2026-03-29T01:00:00Z
        val autumnChange = 1_792_890_000L // 2026-10-25T01:00:00Z
        assertEquals(3_600, ParisTime.offsetSeconds(springChange - 1))
        assertEquals(7_200, ParisTime.offsetSeconds(springChange))
        assertEquals(7_200, ParisTime.offsetSeconds(autumnChange - 1))
        assertEquals(3_600, ParisTime.offsetSeconds(autumnChange))
    }

    @Test
    fun aParisMomentRoundTripsInWinterAndInSummer() {
        val winter = ParisDateTime(2026, 11, 14, 18, 30)
        val winterSeconds = ParisTime.toEpochSeconds(winter)
        assertEquals(1_794_677_400L, winterSeconds) // 2026-11-14T17:30:00Z
        assertEquals(winter, ParisTime.of(winterSeconds))

        val summer = ParisDateTime(2027, 6, 21, 9, 5)
        val summerSeconds = ParisTime.toEpochSeconds(summer)
        assertEquals(1_813_561_500L, summerSeconds) // 2027-06-21T07:05:00Z
        assertEquals(summer, ParisTime.of(summerSeconds))
    }

    @Test
    fun theSkippedHourOfMarchIsReadInWinterTime() {
        val skipped = ParisTime.toEpochSeconds(ParisDateTime(2026, 3, 29, 2, 30))
        assertEquals(ParisDateTime(2026, 3, 29, 3, 30), ParisTime.of(skipped))
    }

    @Test
    fun typedDatesAndHoursAreReadInTheUsualFrenchForms() {
        assertEquals(Triple(2026, 11, 14), ParisTime.parseDate("14/11/2026"))
        assertEquals(Triple(2027, 2, 1), ParisTime.parseDate(" 1-2-2027 "))
        assertEquals(Triple(2026, 11, 14), ParisTime.parseDate("14.11.2026"))
        assertEquals(Triple(2026, 11, 14), ParisTime.parseDate("2026-11-14"))
        assertNull(ParisTime.parseDate("31/02/2027"))
        assertNull(ParisTime.parseDate("demain"))
        assertNull(ParisTime.parseDate("14/11/26"))
        assertEquals(Triple(2028, 2, 29), ParisTime.parseDate("29/02/2028"))

        assertEquals(18 to 30, ParisTime.parseTime("18:30"))
        assertEquals(18 to 30, ParisTime.parseTime("18h30"))
        assertEquals(18 to 30, ParisTime.parseTime("18 h 30"))
        assertEquals(9 to 0, ParisTime.parseTime("9h"))
        assertEquals(9 to 5, ParisTime.parseTime("9:05"))
        assertNull(ParisTime.parseTime("25:00"))
        assertNull(ParisTime.parseTime("18:75"))
        assertNull(ParisTime.parseTime("midi"))
    }

    @Test
    fun theWeekEndsOnSundayNightAndTheMonthOnItsLastDay() {
        // Friday 6 November 2026, 10:00 in Paris.
        val friday = ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 6, 10, 0))
        assertEquals(5, ParisTime.of(friday).dayOfWeek)
        assertEquals(ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 9, 0, 0)), ParisTime.endOfWeek(friday))
        assertEquals(ParisTime.toEpochSeconds(ParisDateTime(2026, 12, 1, 0, 0)), ParisTime.endOfMonth(friday))
        // In December, the next month is in the next year.
        val december = ParisTime.toEpochSeconds(ParisDateTime(2026, 12, 20, 12, 0))
        assertEquals(ParisTime.toEpochSeconds(ParisDateTime(2027, 1, 1, 0, 0)), ParisTime.endOfMonth(december))
        // A Sunday belongs to its own week.
        val sunday = ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 8, 23, 0))
        assertEquals(7, ParisTime.of(sunday).dayOfWeek)
        assertEquals(ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 9, 0, 0)), ParisTime.endOfWeek(sunday))
    }

    @Test
    fun calendarDaysCountParisDaysNotPeriodsOf24Hours() {
        val evening = ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 6, 23, 30))
        val nextMorning = ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 7, 0, 30))
        assertEquals(1, ParisTime.calendarDaysBetween(evening, nextMorning))
        assertEquals(0, ParisTime.calendarDaysBetween(evening, evening + 600))
    }

    @Test
    fun aTypedDateAndHourBecomeAnInstant() {
        assertEquals(1_794_677_400L, DateTimeText("14/11/2026", "18h30").toEpochSeconds())
        assertNull(DateTimeText("14/11/2026", "").toEpochSeconds())
        assertEquals(
            ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 14, 23, 59)),
            DateTimeText("14/11/2026", "").toEpochSeconds(defaultTime = 23 to 59),
        )
        assertEquals(DateTimeText("14/11/2026", "18:30"), DateTimeText.of(1_794_677_400L))
    }
}
