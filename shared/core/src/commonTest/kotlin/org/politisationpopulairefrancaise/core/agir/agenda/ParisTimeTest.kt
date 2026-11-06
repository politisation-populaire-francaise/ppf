package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParisTimeTest {

    @Test
    fun winterIsUtcPlusOneAndSummerUtcPlusTwo() {
        assertEquals(3_600, ParisTime.offsetSeconds(AgendaTimes.SATURDAY_14H))
        assertEquals(7_200, ParisTime.offsetSeconds(1_784_030_400L)) // 14 July 2026, 12:00 UTC
        // 2026: summer time from 29 March 01:00 UTC to 25 October 01:00 UTC.
        assertEquals(3_600, ParisTime.offsetSeconds(1_774_746_000L - 1))
        assertEquals(7_200, ParisTime.offsetSeconds(1_774_746_000L))
        assertEquals(7_200, ParisTime.offsetSeconds(1_792_890_000L - 1))
        assertEquals(3_600, ParisTime.offsetSeconds(1_792_890_000L))
    }

    @Test
    fun aMomentIsReadInLocalTime() {
        val moment = ParisTime.moment(AgendaTimes.SATURDAY_14H)
        assertEquals(CivilDate(2026, 11, 14), moment.date)
        assertEquals(14, moment.hour)
        assertEquals(0, moment.minute)
        assertEquals(6, moment.dayOfWeek, "a Saturday")
        // Just before midnight UTC is already the next day in Paris.
        val lateEvening = ParisTime.moment(1_794_700_800L - 1) // 14 Nov 2026 23:59:59 UTC
        assertEquals(CivilDate(2026, 11, 15), lateEvening.date)
        assertEquals(0, lateEvening.hour)
        assertEquals(59, lateEvening.minute)
        assertEquals(7, lateEvening.dayOfWeek)
        assertEquals(ParisTime.epochDay(AgendaTimes.SATURDAY_14H) + 1, lateEvening.epochDay)
    }

    @Test
    fun localTimesBecomeInstants() {
        assertEquals(AgendaTimes.SATURDAY_14H, ParisTime.toEpochSeconds(CivilDate(2026, 11, 14), 14, 0))
        assertEquals(1_784_030_400L, ParisTime.toEpochSeconds(CivilDate(2026, 7, 14), 14, 0))
        // The hour skipped on 29 March 2026 lands one hour later; the hour repeated on 25 October is the first one.
        assertEquals(1_774_746_000L + 1_800, ParisTime.toEpochSeconds(CivilDate(2026, 3, 29), 2, 30))
        assertEquals(1_792_890_000L - 1_800, ParisTime.toEpochSeconds(CivilDate(2026, 10, 25), 2, 30))
        assertEquals(ParisTime.toEpochSeconds(CivilDate(2026, 11, 14), 0, 0), ParisTime.startOfDay(ParisTime.epochDay(AgendaTimes.SATURDAY_14H)))
    }

    @Test
    fun calendarArithmetic() {
        assertEquals(0L, ParisTime.epochDayOf(CivilDate(1970, 1, 1)))
        assertEquals(CivilDate(2028, 2, 29), ParisTime.dateOf(ParisTime.epochDayOf(CivilDate(2028, 2, 29))))
        assertEquals(4, ParisTime.dayOfWeek(0), "1 January 1970 was a Thursday")
        assertEquals(29, ParisTime.daysInMonth(2028, 2))
        assertEquals(28, ParisTime.daysInMonth(2100, 2))
        assertEquals(29, ParisTime.daysInMonth(2000, 2))
        assertTrue(ParisTime.isValidDate(2026, 11, 30))
        assertFalse(ParisTime.isValidDate(2026, 11, 31))
        assertFalse(ParisTime.isValidDate(2026, 13, 1))
    }
}
