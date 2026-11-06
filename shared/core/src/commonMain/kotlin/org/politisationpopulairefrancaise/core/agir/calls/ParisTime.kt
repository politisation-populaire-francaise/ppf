package org.politisationpopulairefrancaise.core.agir.calls

/** A wall-clock moment in Paris: what the author typed and what the screens show. */
data class ParisDateTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
    /** ISO day of the week: 1 for Monday, 7 for Sunday. */
    val dayOfWeek: Int get() = ParisTime.dayOfWeek(ParisTime.daysFromCivil(year, month, day))
}

/**
 * The time of the calls, in the zone named by the contract of the Agir space (`start_tzid` =
 * Europe/Paris): the author types a date and an hour in Paris time, the event carries seconds
 * since 1970 (UTC), and every screen shows Paris time again, whatever the zone of the device.
 *
 * Pure Kotlin and deterministic (no time-zone database, so it runs the same on Android, desktop
 * and iOS): Paris is UTC+1 in winter and UTC+2 in summer, summer time running from the last Sunday
 * of March to the last Sunday of October at 01:00 UTC (directive 2000/84/EC). Should the European
 * Union stop changing the clocks, this is the one place to change.
 */
object ParisTime {
    private const val HOUR: Long = 3_600
    private const val DAY: Long = 86_400

    /** The offset of Paris from UTC at [epochSeconds]: 3600 in winter, 7200 in summer. */
    fun offsetSeconds(epochSeconds: Long): Int {
        val year = civilFromDays(epochSeconds.floorDiv(DAY)).first
        val summerStart = lastSundayAt1Utc(year, 3)
        val summerEnd = lastSundayAt1Utc(year, 10)
        return if (epochSeconds in summerStart until summerEnd) 7_200 else 3_600
    }

    /** The Paris wall-clock moment of [epochSeconds]. */
    fun of(epochSeconds: Long): ParisDateTime {
        val local = epochSeconds + offsetSeconds(epochSeconds)
        val days = local.floorDiv(DAY)
        val secondOfDay = local - days * DAY
        val (year, month, day) = civilFromDays(days)
        return ParisDateTime(year, month, day, (secondOfDay / HOUR).toInt(), (secondOfDay % HOUR / 60).toInt())
    }

    /**
     * The instant of a Paris wall-clock moment. In the hour skipped in March the moment is read in
     * winter time (02:30 becomes 03:30 summer time); in the hour repeated in October the first one
     * (summer time) is chosen.
     */
    fun toEpochSeconds(moment: ParisDateTime): Long {
        val local = daysFromCivil(moment.year, moment.month, moment.day) * DAY + moment.hour * HOUR + moment.minute * 60L
        val summer = local - 2 * HOUR
        return if (offsetSeconds(summer) == 7_200) summer else local - HOUR
    }

    /** A valid calendar date and hour, or null ("31/02/2027", "25:00"). */
    fun moment(year: Int, month: Int, day: Int, hour: Int, minute: Int): ParisDateTime? {
        if (year !in 1970..2999 || month !in 1..12 || hour !in 0..23 || minute !in 0..59) return null
        if (day !in 1..daysInMonth(year, month)) return null
        return ParisDateTime(year, month, day, hour, minute)
    }

    /**
     * Reads a date typed by a person: "14/11/2026", "14-11-2026", "14.11.2026", "1/2/2027" or the
     * ISO form "2026-11-14". Returns year, month and day, or null when it is not a real date.
     */
    fun parseDate(text: String): Triple<Int, Int, Int>? {
        val value = text.trim()
        ISO_DATE.matchEntire(value)?.let { match ->
            val (y, m, d) = match.destructured
            return checkedDate(y.toInt(), m.toInt(), d.toInt())
        }
        FRENCH_DATE.matchEntire(value)?.let { match ->
            val (d, m, y) = match.destructured
            return checkedDate(y.toInt(), m.toInt(), d.toInt())
        }
        return null
    }

    /** Reads an hour typed by a person: "18:30", "18h30", "18 h 30", "18h", "9:05". Returns hour and minute, or null. */
    fun parseTime(text: String): Pair<Int, Int>? {
        val match = TIME.matchEntire(text.trim().lowercase()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    /** "14/11/2026", the form the date fields of the form show back. */
    fun formatDate(moment: ParisDateTime): String =
        "${moment.day.toString().padStart(2, '0')}/${moment.month.toString().padStart(2, '0')}/${moment.year}"

    /** "18:30", the form the hour fields of the form show back. */
    fun formatTime(moment: ParisDateTime): String =
        "${moment.hour.toString().padStart(2, '0')}:${moment.minute.toString().padStart(2, '0')}"

    /** The first instant of the next Monday in Paris after [epochSeconds]: the end of "cette semaine". */
    fun endOfWeek(epochSeconds: Long): Long {
        val now = of(epochSeconds)
        val today = daysFromCivil(now.year, now.month, now.day)
        val nextMonday = today + (8 - now.dayOfWeek)
        return startOfDay(nextMonday)
    }

    /** The first instant of the first day of the next month in Paris: the end of "ce mois-ci". */
    fun endOfMonth(epochSeconds: Long): Long {
        val now = of(epochSeconds)
        val (year, month) = if (now.month == 12) now.year + 1 to 1 else now.year to now.month + 1
        return startOfDay(daysFromCivil(year, month, 1))
    }

    /** Whole Paris calendar days from the day of [from] to the day of [to] (0 the same day, 1 the next day). */
    fun calendarDaysBetween(from: Long, to: Long): Long {
        val a = of(from)
        val b = of(to)
        return daysFromCivil(b.year, b.month, b.day) - daysFromCivil(a.year, a.month, a.day)
    }

    /** ISO day of the week of a day count since 1970-01-01 (a Thursday). */
    fun dayOfWeek(days: Long): Int = ((days + 3).mod(7L) + 1).toInt()

    /** Days since 1970-01-01 of a calendar date (algorithm of Howard Hinnant). */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = y.floorDiv(400L)
        val yearOfEra = y - era * 400
        val monthIndex = if (month > 2) month - 3 else month + 9
        val dayOfYear = (153 * monthIndex + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }

    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719_468
        val era = z.floorDiv(146_097L)
        val dayOfEra = z - era * 146_097
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val monthIndex = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * monthIndex + 2) / 5 + 1
        val month = if (monthIndex < 10) monthIndex + 3 else monthIndex - 9
        val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
        return Triple(year.toInt(), month.toInt(), day.toInt())
    }

    /** Midnight in Paris of a day count since 1970. */
    private fun startOfDay(days: Long): Long {
        val (year, month, day) = civilFromDays(days)
        return toEpochSeconds(ParisDateTime(year, month, day, 0, 0))
    }

    private fun lastSundayAt1Utc(year: Int, month: Int): Long {
        val lastDay = daysFromCivil(year, month, daysInMonth(year, month))
        val sunday = lastDay - (dayOfWeek(lastDay) % 7)
        return sunday * DAY + HOUR
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun checkedDate(year: Int, month: Int, day: Int): Triple<Int, Int, Int>? =
        if (moment(year, month, day, 0, 0) != null) Triple(year, month, day) else null

    private val ISO_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val FRENCH_DATE = Regex("""(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4})""")
    private val TIME = Regex("""(\d{1,2})\s*(?:[:h]\s*(\d{2})?)?""")
}
