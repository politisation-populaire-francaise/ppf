package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * A moment as it is read on a poster in mainland France: its local date, hour and minute, and the
 * day of the week. Mobilizations are dated in the time zone of their place (`start_tzid` =
 * `Europe/Paris`, contract of the Agir space), so the agenda shows them in that time zone whatever
 * the device's own setting: "14 h" on the poster is "14 h" on every phone.
 */
data class ParisMoment(
    val date: CivilDate,
    val hour: Int,
    val minute: Int,
    /** ISO day of the week: 1 = Monday ... 7 = Sunday. */
    val dayOfWeek: Int,
    /** Days since 1970-01-01 in local time: two moments of the same local day share it. */
    val epochDay: Long,
)

/**
 * The legal time of mainland France (`Europe/Paris`) in pure Kotlin, for every platform: UTC+1 in
 * winter, UTC+2 in summer, the change happening at 01:00 UTC on the last Sunday of March and of
 * October (EU directive 2000/84/EC, in force since 1996, which is enough for an agenda of
 * mobilizations). No time zone database is needed, so the result never depends on the device.
 */
object ParisTime {
    const val ZONE: String = "Europe/Paris"

    private const val HOUR: Long = 3_600
    private const val DAY: Long = 86_400

    /** The offset from UTC at [epochSeconds], in seconds: 3600 or 7200. */
    fun offsetSeconds(epochSeconds: Long): Int {
        val year = CivilDate.fromEpochSeconds(epochSeconds).year
        val summerStart = lastSundayEpochDay(year, 3) * DAY + HOUR
        val summerEnd = lastSundayEpochDay(year, 10) * DAY + HOUR
        return if (epochSeconds in summerStart until summerEnd) 7_200 else 3_600
    }

    /** The local date and time of [epochSeconds]. */
    fun moment(epochSeconds: Long): ParisMoment {
        val local = epochSeconds + offsetSeconds(epochSeconds)
        val day = local.floorDiv(DAY)
        val secondOfDay = local - day * DAY
        return ParisMoment(
            date = CivilDate.fromEpochSeconds(day * DAY),
            hour = (secondOfDay / HOUR).toInt(),
            minute = ((secondOfDay % HOUR) / 60).toInt(),
            dayOfWeek = dayOfWeek(day),
            epochDay = day,
        )
    }

    /** The local day (days since 1970-01-01) of [epochSeconds]. */
    fun epochDay(epochSeconds: Long): Long = (epochSeconds + offsetSeconds(epochSeconds)).floorDiv(DAY)

    /**
     * The instant of a local date and time. A time that does not exist (the hour skipped in March)
     * is read in winter time, so it lands one hour later; a time that happens twice (the hour
     * repeated in October) is the first one, in summer time.
     */
    fun toEpochSeconds(date: CivilDate, hour: Int, minute: Int): Long {
        require(hour in 0..23 && minute in 0..59) { "Invalid time" }
        val local = epochDayOf(date) * DAY + hour * HOUR + minute * 60L
        val summer = local - 7_200
        if (offsetSeconds(summer) == 7_200) return summer
        return local - 3_600
    }

    /** The first instant of a local day. */
    fun startOfDay(epochDay: Long): Long = toEpochSeconds(dateOf(epochDay), 0, 0)

    /** Days since 1970-01-01 of a calendar date (inverse of [CivilDate.fromEpochSeconds], Howard Hinnant's algorithm). */
    fun epochDayOf(date: CivilDate): Long {
        val y = (if (date.month <= 2) date.year - 1 else date.year).toLong()
        val era = y.floorDiv(400L)
        val yearOfEra = y - era * 400
        val m = date.month.toLong()
        val dayOfYear = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + date.day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }

    fun dateOf(epochDay: Long): CivilDate = CivilDate.fromEpochSeconds(epochDay * DAY)

    /** ISO day of the week of a day number: 1970-01-01 was a Thursday (4). */
    fun dayOfWeek(epochDay: Long): Int = (epochDay + 3).mod(7L).toInt() + 1

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    }

    /** True when the three numbers form a real calendar date. */
    fun isValidDate(year: Int, month: Int, day: Int): Boolean =
        year in 1970..9999 && month in 1..12 && day in 1..daysInMonth(year, month)

    private fun lastSundayEpochDay(year: Int, month: Int): Long {
        val last = epochDayOf(CivilDate(year, month, daysInMonth(year, month)))
        // Days to go back from the last day of the month to reach a Sunday (ISO 7).
        return last - (dayOfWeek(last) % 7)
    }
}
