package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.time.CivilDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ParisTime] computes the legal time of mainland France without a time zone database; on the JVM
 * it is checked against the one of the platform, every 20 minutes from 2024 to 2031, and on every
 * local quarter of an hour of the days of change.
 */
class ParisTimeAgainstJavaTimeTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun offsetsAndLocalTimesMatchJavaTime() {
        var instant = Instant.parse("2024-01-01T00:00:00Z").epochSecond
        val last = Instant.parse("2031-12-31T23:00:00Z").epochSecond
        while (instant <= last) {
            val expected = LocalDateTime.ofInstant(Instant.ofEpochSecond(instant), paris)
            val moment = ParisTime.moment(instant)
            assertEquals(paris.rules.getOffset(Instant.ofEpochSecond(instant)).totalSeconds, ParisTime.offsetSeconds(instant), "offset at $instant")
            assertEquals(CivilDate(expected.year, expected.monthValue, expected.dayOfMonth), moment.date, "date at $instant")
            assertEquals(expected.hour, moment.hour)
            assertEquals(expected.minute, moment.minute)
            assertEquals(expected.dayOfWeek.value, moment.dayOfWeek)
            instant += 20 * 60
        }
    }

    @Test
    fun localTimesOfTheDaysOfChangeMatchJavaTime() {
        for (year in 2024..2031) {
            for (month in listOf(3, 10)) {
                val lastSunday = (31 downTo 25).first { java.time.LocalDate.of(year, month, it).dayOfWeek == java.time.DayOfWeek.SUNDAY }
                for (quarter in 0 until 24 * 4) {
                    val local = LocalDateTime.of(year, month, lastSunday, quarter / 4, (quarter % 4) * 15)
                    // java.time: a skipped time is moved forward by the gap, a repeated time takes the earlier offset.
                    val expected = local.atZone(paris).toEpochSecond()
                    assertEquals(expected, ParisTime.toEpochSeconds(CivilDate(year, month, lastSunday), local.hour, local.minute), "at $local")
                }
            }
        }
    }
}
