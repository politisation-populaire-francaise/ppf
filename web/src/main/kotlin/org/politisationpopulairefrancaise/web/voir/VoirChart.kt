package org.politisationpopulairefrancaise.web.voir

import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.Indicator
import org.politisationpopulairefrancaise.core.voir.Series
import org.politisationpopulairefrancaise.core.voir.SourceKind
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The chart of an indicator, drawn on the server as inline SVG: one line per kind of source, a
 * legend, a label at the end of each line, a hairline grid, an accessible title and description.
 * No script, no inline style (the CSP forbids it): every colour comes from a class of site.css
 * (`chart-s0` to `chart-s7`), which follows the light or dark mode of the reader. The same data is
 * always given as an HTML table next to the chart (see VoirPages).
 */
object VoirChart {

    /**
     * The fixed colour slot of each kind of source in `PpfPalette.chart` (same mapping as the
     * application, shared/ui Observatory.kt `chartSlot`): the colour follows the source, never its rank.
     */
    fun slotOf(kind: SourceKind): Int = when (kind) {
        SourceKind.AUTHORITIES, SourceKind.PARLIAMENT, SourceKind.OFFICIAL_TEXT -> 0
        SourceKind.ORGANISERS -> 1
        SourceKind.INDEPENDENT -> 2
        SourceKind.REPORT, SourceKind.INVESTIGATION -> 3
        SourceKind.CITIZENS -> 4
        SourceKind.STATEMENT -> 5
        SourceKind.MEDIA -> 6
        SourceKind.JUSTICE -> 7
    }

    const val SLOTS: Int = 8

    /** The slot of each kind present in one chart; two kinds sharing a slot get the next free one (as in the app). */
    fun slots(kinds: List<SourceKind>): Map<SourceKind, Int> {
        val used = mutableSetOf<Int>()
        return kinds.associateWith { kind ->
            var slot = slotOf(kind)
            while (slot in used && used.size < SLOTS) slot = (slot + 1) % SLOTS
            used += slot
            slot
        }
    }

    // Geometry of the drawing, in SVG units (the drawing scales to the width of the page).
    private const val WIDTH = 640.0
    private const val HEIGHT = 300.0
    private const val LEFT = 60.0
    private const val RIGHT = 168.0
    private const val TOP = 16.0
    private const val BOTTOM = 40.0
    private const val LABEL_GAP = 30.0

    /** The SVG of [indicator], or null when it has no measure. [id] prefixes the ids of the title and description. */
    fun svg(indicator: Indicator, id: String = "graphique"): String? {
        val series = indicator.series.filter { it.points.isNotEmpty() }
        if (series.isEmpty()) return null
        val slots = slots(series.map { it.kind })
        val dates = series.flatMap { s -> s.points.map { it.first } }.distinct().sorted()
        val firstDay = dates.first().toEpochDays()
        val lastDay = dates.last().toEpochDays()
        val plotWidth = WIDTH - LEFT - RIGHT
        val plotHeight = HEIGHT - TOP - BOTTOM
        val (top, step) = scale(series.maxOf { s -> s.points.maxOf { it.second } })

        fun x(date: CivilDate): Double =
            if (lastDay == firstDay) LEFT + plotWidth / 2 else LEFT + (date.toEpochDays() - firstDay) * plotWidth / (lastDay - firstDay)
        fun y(value: Double): Double = TOP + plotHeight - value / top * plotHeight

        val out = StringBuilder()
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"chart\" viewBox=\"0 0 ${n(WIDTH)} ${n(HEIGHT)}\" role=\"img\" aria-labelledby=\"$id-titre $id-description\">")
        out.append("<title id=\"$id-titre\">").append(xml("Évolution de « ${indicator.title} »")).append("</title>")
        out.append("<desc id=\"$id-description\">").append(xml(description(indicator, series))).append("</desc>")

        // Hairline grid and values of the vertical axis.
        val ticks = (0..Math.round(top / step).toInt()).map { it * step }
        out.append("<g class=\"chart-grid\">")
        ticks.forEach { tick ->
            val ty = y(tick)
            out.append("<line x1=\"${n(LEFT)}\" y1=\"${n(ty)}\" x2=\"${n(LEFT + plotWidth)}\" y2=\"${n(ty)}\"/>")
        }
        out.append("</g><g class=\"chart-axis\">")
        ticks.forEach { tick ->
            out.append("<text x=\"${n(LEFT - 8)}\" y=\"${n(y(tick) + 4)}\" text-anchor=\"end\">").append(xml(FrenchNumbers.short(tick))).append("</text>")
        }
        // Dates of the horizontal axis: at most four, the first and the last always.
        axisDates(dates, maxLabels = 4).forEach { date ->
            val anchor = when {
                dates.size == 1 -> "middle"
                date == dates.first() -> "start"
                date == dates.last() -> "end"
                else -> "middle"
            }
            out.append("<text x=\"${n(x(date))}\" y=\"${n(HEIGHT - BOTTOM + 22)}\" text-anchor=\"$anchor\">").append(xml(shortDate(date))).append("</text>")
        }
        out.append("</g>")

        // One line per kind of source, with a dot on each value.
        series.forEach { s ->
            out.append("<g class=\"chart-series chart-s${slots.getValue(s.kind)}\">")
            if (s.points.size > 1) {
                out.append("<polyline fill=\"none\" points=\"")
                out.append(s.points.joinToString(" ") { (date, value) -> "${n(x(date))},${n(y(value))}" })
                out.append("\"/>")
            }
            s.points.forEach { (date, value) -> out.append("<circle cx=\"${n(x(date))}\" cy=\"${n(y(value))}\" r=\"3.5\"/>") }
            out.append("</g>")
        }

        // A label at the end of each line (kind, then latest value), moved apart so that none overlaps.
        val ends = series.map { s -> s to y(s.points.last().second) }.sortedBy { it.second }
        val placed = spread(ends.map { it.second }, TOP + 10, HEIGHT - BOTTOM - 4)
        ends.forEachIndexed { index, (s, _) ->
            val last = s.points.last()
            val lx = x(last.first) + 10
            val ly = placed[index]
            out.append("<text class=\"chart-label chart-s${slots.getValue(s.kind)}\" x=\"${n(lx)}\" y=\"${n(ly - 3)}\">")
            out.append("<tspan class=\"chart-label-kind\">").append(xml(s.kind.label)).append("</tspan>")
            out.append("<tspan x=\"${n(lx)}\" dy=\"14\">").append(xml(valueText(last.second, indicator.unit))).append("</tspan>")
            out.append("</text>")
        }
        out.append("</svg>")
        return out.toString()
    }

    /** What the chart shows, in words: for each source, its first and latest values. */
    private fun description(indicator: Indicator, series: List<Series>): String {
        val lines = series.map { s ->
            val first = s.points.first()
            val last = s.points.last()
            if (s.points.size == 1) {
                "${s.kind.label} : ${valueText(last.second, indicator.unit)} le ${last.first.toFrench()}"
            } else {
                "${s.kind.label} : de ${valueText(first.second, indicator.unit)} le ${first.first.toFrench()} à ${valueText(last.second, indicator.unit)} le ${last.first.toFrench()}"
            }
        }
        return "Une courbe par source. " + lines.joinToString(" ; ") + ". Les données sont aussi données dans le tableau qui suit."
    }

    /** The top of the vertical axis and its step: 1, 2, 2,5 or 5 times a power of ten, four steps or so. */
    internal fun scale(maxValue: Double): Pair<Double, Double> {
        if (maxValue <= 0) return 1.0 to 0.25
        val rough = maxValue / 4
        val power = 10.0.pow(floor(log10(rough)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * power }.first { it >= rough }
        return ceil(maxValue / step - 1e-9) * step to step
    }

    /** At most [maxLabels] dates, evenly chosen, the first and the last included. */
    internal fun axisDates(dates: List<CivilDate>, maxLabels: Int): List<CivilDate> {
        if (dates.size <= maxLabels) return dates
        return (0 until maxLabels).map { i -> dates[(i * (dates.size - 1).toDouble() / (maxLabels - 1)).toInt()] }.distinct()
    }

    /** Positions at least [LABEL_GAP] apart, kept between [low] and [high], as close as possible to [wanted] (sorted). */
    internal fun spread(wanted: List<Double>, low: Double, high: Double): List<Double> {
        val y = wanted.map { it.coerceIn(low, high) }.toMutableList()
        for (i in 1 until y.size) y[i] = max(y[i], y[i - 1] + LABEL_GAP)
        if (y.isNotEmpty() && y.last() > high) {
            y[y.size - 1] = high
            for (i in y.size - 2 downTo 0) y[i] = min(y[i], y[i + 1] - LABEL_GAP)
        }
        return y
    }

    private fun shortDate(date: CivilDate): String = date.toFrenchShort()

    /** A number for an SVG attribute: one decimal at most, a dot as separator. */
    private fun n(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }
}

/** Escapes text for XML (inside an SVG written by hand). */
internal fun xml(text: String): String = buildString(text.length) {
    text.forEach { c ->
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> if (c == '\t' || c == '\n' || c == '\r' || c >= ' ') append(c)
        }
    }
}

/** A value with its unit when it is a percentage ("12,5 %"); other units are written next to it by the page. */
internal fun valueText(value: Double, unit: String): String =
    if (unit == "%") "${FrenchNumbers.format(value)}${FrenchNumbers.GROUP_SEPARATOR}%" else FrenchNumbers.format(value)
