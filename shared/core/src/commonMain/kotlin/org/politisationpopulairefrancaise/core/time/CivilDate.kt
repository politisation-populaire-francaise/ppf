package org.politisationpopulairefrancaise.core.time

/** A calendar date (proleptic Gregorian, UTC), enough to show when a content was updated. */
data class CivilDate(val year: Int, val month: Int, val day: Int) {

    /** French long form: "1er janvier 2027", "5 novembre 2026". */
    fun toFrench(): String {
        val dayText = if (day == 1) "1er" else day.toString()
        return "$dayText ${FRENCH_MONTHS[month - 1]} $year"
    }

    companion object {
        private val FRENCH_MONTHS = listOf(
            "janvier", "février", "mars", "avril", "mai", "juin",
            "juillet", "août", "septembre", "octobre", "novembre", "décembre",
        )

        /** Converts seconds since 1970-01-01 UTC (days-from-civil algorithm of Howard Hinnant). */
        fun fromEpochSeconds(seconds: Long): CivilDate {
            val days = seconds.floorDiv(86_400L)
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
    }
}
