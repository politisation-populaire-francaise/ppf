package org.politisationpopulairefrancaise.core.time

/** A calendar date (proleptic Gregorian, UTC), enough to show when a content was updated. */
data class CivilDate(val year: Int, val month: Int, val day: Int) : Comparable<CivilDate> {

    /** French long form: "1er janvier 2027", "5 novembre 2026". */
    fun toFrench(): String {
        val dayText = if (day == 1) "1er" else day.toString()
        return "$dayText ${FRENCH_MONTHS[month - 1]} $year"
    }

    /** French short form for lists and charts: "1er janv. 2027", "5 nov. 2026". */
    fun toFrenchShort(): String {
        val dayText = if (day == 1) "1er" else day.toString()
        return "$dayText ${FRENCH_MONTHS_SHORT[month - 1]} $year"
    }

    /** The month and the year, to group a chronology: "novembre 2026". */
    fun monthYearFrench(): String = "${FRENCH_MONTHS[month - 1]} $year"

    /** ISO 8601 form, "2026-11-05", as written in the tags of the contents. */
    fun toIso(): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    /** Days since 1970-01-01 (Howard Hinnant's days-from-civil), to place dates on a chart. */
    fun toEpochDays(): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = y.floorDiv(400L)
        val yearOfEra = y - era * 400
        val monthIndex = if (month > 2) month - 3 else month + 9
        val dayOfYear = (153 * monthIndex + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }

    override fun compareTo(other: CivilDate): Int = compareValuesBy(this, other, CivilDate::year, CivilDate::month, CivilDate::day)

    companion object {
        private val FRENCH_MONTHS = listOf(
            "janvier", "février", "mars", "avril", "mai", "juin",
            "juillet", "août", "septembre", "octobre", "novembre", "décembre",
        )
        private val FRENCH_MONTHS_SHORT = listOf(
            "janv.", "févr.", "mars", "avr.", "mai", "juin",
            "juil.", "août", "sept.", "oct.", "nov.", "déc.",
        )

        /** Converts seconds since 1970-01-01 UTC (days-from-civil algorithm of Howard Hinnant). */
        fun fromEpochSeconds(seconds: Long): CivilDate = fromEpochDays(seconds.floorDiv(86_400L))

        /** The date [days] days after 1970-01-01. */
        fun fromEpochDays(days: Long): CivilDate {
            val z = days + 719_468
            val era = z.floorDiv(146_097L)
            val dayOfEra = z - era * 146_097
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val monthIndex = (5 * dayOfYear + 2) / 153
            val day = dayOfYear - (153 * monthIndex + 2) / 5 + 1
            val month = if (monthIndex < 10) monthIndex + 3 else monthIndex - 9
            val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
            return CivilDate(year.toInt(), month.toInt(), day.toInt())
        }

        /** Reads "2026-11-05"; null for anything else, including impossible dates such as 2026-02-30. */
        fun parseIso(text: String?): CivilDate? {
            val value = text?.trim() ?: return null
            if (value.length != 10 || value[4] != '-' || value[7] != '-') return null
            val year = value.substring(0, 4).toIntOrNull() ?: return null
            val month = value.substring(5, 7).toIntOrNull() ?: return null
            val day = value.substring(8, 10).toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            val date = CivilDate(year, month, day)
            // An impossible day (30 February) does not survive the round trip.
            return date.takeIf { fromEpochDays(it.toEpochDays()) == it }
        }
    }
}
