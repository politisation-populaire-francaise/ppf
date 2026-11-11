package org.politisationpopulairefrancaise.core.agir.strike

/**
 * Reading what the person types in the strike simulator (amounts in euros, hours) and writing the
 * results the French way ("2 400,00 €", "7 h 30", "13,33 %"), with no-break spaces. Pure functions:
 * nothing typed is kept here.
 */
object StrikeNumbers {
    /** The no-break space of French typography, between thousands and before a unit. */
    const val NBSP: Char = '\u00A0'

    /**
     * An amount in euros ("2400", "2 400,50", "2400.5 €") as cents; null when it is empty, not a
     * number, has more than two decimals, is zero or exceeds [StrikeSimulator.MAX_PAY_CENTS].
     * Both the comma and the point are read as the decimal separator; spaces are ignored.
     */
    fun parseEuros(text: String): Long? {
        val cleaned = text.filterNot { it == ' ' || it == NBSP || it == '\u202F' }.removeSuffix("€").replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        val whole = cleaned.substringBefore('.')
        val decimals = if ('.' in cleaned) cleaned.substringAfter('.') else ""
        if (whole.isEmpty() || whole.length > 9 || !whole.all(Char::isDigit)) return null
        if (decimals.length > 2 || !decimals.all(Char::isDigit)) return null
        val cents = whole.toLong() * 100 + decimals.padEnd(2, '0').toLong()
        return cents.takeIf { it in 1..StrikeSimulator.MAX_PAY_CENTS }
    }

    /**
     * A duration ("7", "7,5", "7.25", "7h30", "7 h 30", "45 min" is not read) as minutes, 1 to 24 hours;
     * null otherwise. Decimal hours are rounded to the nearest minute.
     */
    fun parseHours(text: String): Int? = parseDuration(text)?.takeIf { it in 1..StrikeSimulator.MAX_DAY_MINUTES }

    /** The working time of a week ("35", "37,5", "39 h"), as minutes, 1 to 60 hours; null otherwise. */
    fun parseWeeklyHours(text: String): Int? =
        parseDuration(text)?.takeIf { it in StrikeSimulator.MIN_WEEKLY_MINUTES..StrikeSimulator.MAX_WEEKLY_MINUTES }

    /** A duration in decimal hours or with an "h", as minutes (up to 99 h 59); null when unreadable. */
    private fun parseDuration(text: String): Int? {
        val cleaned = text.filterNot { it == ' ' || it == NBSP || it == '\u202F' }.lowercase()
        if (cleaned.isEmpty()) return null
        val minutes = if ('h' in cleaned) {
            val hours = cleaned.substringBefore('h')
            val rest = cleaned.substringAfter('h')
            if (hours.isEmpty() || hours.length > 2 || !hours.all(Char::isDigit)) return null
            if (rest.length > 2 || !rest.all(Char::isDigit)) return null
            val extra = if (rest.isEmpty()) 0 else rest.toInt()
            if (extra >= 60) return null
            hours.toInt() * 60 + extra
        } else {
            val normalized = cleaned.replace(',', '.')
            if (normalized.count { it == '.' } > 1) return null
            val whole = normalized.substringBefore('.')
            val decimals = if ('.' in normalized) normalized.substringAfter('.') else ""
            if (whole.isEmpty() || whole.length > 2 || !whole.all(Char::isDigit)) return null
            if (decimals.length > 2 || !decimals.all(Char::isDigit)) return null
            val hundredths = whole.toInt() * 100 + decimals.padEnd(2, '0').toInt()
            // hundredths of an hour × 60 ÷ 100, to the nearest minute
            ((hundredths * 60 * 2 + 100) / 200)
        }
        return minutes
    }

    /** "2 400,00 €": cents written with two decimals, thousands grouped by no-break spaces. */
    fun formatEuros(cents: Long): String {
        val negative = cents < 0
        val absolute = if (negative) -cents else cents
        val euros = group(absolute / 100)
        val rest = (absolute % 100).toString().padStart(2, '0')
        return (if (negative) "-" else "") + euros + "," + rest + NBSP + "€"
    }

    /** "13,33 %" from hundredths of a percent. */
    fun formatPercent(basisPoints: Long): String =
        (basisPoints / 100).toString() + "," + (basisPoints % 100).toString().padStart(2, '0') + NBSP + "%"

    /** "7 h", "7 h 30", "0 h 45". */
    fun formatHours(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "$hours${NBSP}h" else "$hours${NBSP}h$NBSP${rest.toString().padStart(2, '0')}"
    }

    /** "151,67" or "169" from hundredths of an hour. */
    fun formatHundredths(hundredths: Long): String {
        val whole = hundredths / 100
        val rest = hundredths % 100
        return if (rest == 0L) whole.toString() else whole.toString() + "," + rest.toString().padStart(2, '0')
    }

    /** The minutes as the person would type them again ("7", "7,5", "7h20" becomes "7 h 20"). */
    fun hoursInput(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when (rest) {
            0 -> hours.toString()
            30 -> "$hours,5"
            else -> "${hours}h${rest.toString().padStart(2, '0')}"
        }
    }

    private fun group(value: Long): String {
        val digits = value.toString()
        val builder = StringBuilder()
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) builder.append(NBSP)
            builder.append(digit)
        }
        return builder.toString()
    }
}
