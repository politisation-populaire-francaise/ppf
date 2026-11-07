package org.politisationpopulairefrancaise.core.voir

import kotlin.math.abs
import kotlin.math.roundToLong

/** French typography for figures: "45 000", "2,5", "1,2 million" (narrow no-break spaces between groups). */
object FrenchNumbers {
    /** The narrow no-break space French uses between groups of three digits. */
    const val GROUP_SEPARATOR: Char = ' '

    /** A value in full: whole numbers grouped by three, otherwise one decimal at most ("2,5"). */
    fun format(value: Double): String {
        val negative = value < 0
        val rounded = (abs(value) * 10).roundToLong()
        val whole = rounded / 10
        val tenth = rounded % 10
        val grouped = group(whole)
        val text = if (tenth == 0L) grouped else "$grouped,$tenth"
        return if (negative) "-$text" else text
    }

    /** A short form for small cards and chart axes: "45 000", "1,2 M", "250 k" is never used (ambiguous). */
    fun short(value: Double): String {
        val size = abs(value)
        return when {
            size >= 1_000_000_000 -> format(value / 1_000_000_000) + "${GROUP_SEPARATOR}Md"
            size >= 1_000_000 -> format(value / 1_000_000) + "${GROUP_SEPARATOR}M"
            else -> format(value)
        }
    }

    /** A range of sources that disagree: "12 000 à 45 000". */
    fun range(low: Double, high: Double): String = if (low == high) format(low) else "${format(low)} à ${format(high)}"

    private fun group(value: Long): String {
        val digits = value.toString()
        val out = StringBuilder()
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) out.append(GROUP_SEPARATOR)
            out.append(digit)
        }
        return out.toString()
    }
}
