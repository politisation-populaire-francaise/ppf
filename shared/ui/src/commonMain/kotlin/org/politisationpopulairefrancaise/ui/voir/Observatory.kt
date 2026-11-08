package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.Indicator
import org.politisationpopulairefrancaise.core.voir.Measure
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.core.voir.Reading
import org.politisationpopulairefrancaise.core.voir.Series
import org.politisationpopulairefrancaise.core.voir.SourceKind
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/*
 * The observatory of the figures (M2, VO-01, VO-02, VO-05): key figure cards, the page of an
 * indicator, its chart and the same data as a table. Sources are always side by side, never merged.
 */

/** The fixed chart colour of each kind of source (colour follows the source, never its rank). */
private val SourceKind.chartSlot: Int get() = when (this) {
    SourceKind.AUTHORITIES, SourceKind.PARLIAMENT, SourceKind.OFFICIAL_TEXT -> 0
    SourceKind.ORGANISERS -> 1
    SourceKind.INDEPENDENT -> 2
    SourceKind.REPORT, SourceKind.INVESTIGATION -> 3
    SourceKind.CITIZENS -> 4
    SourceKind.STATEMENT -> 5
    SourceKind.MEDIA -> 6
    SourceKind.JUSTICE -> 7
}

/** The colour of each kind present in one chart; two kinds sharing a slot get the next free one. */
@Composable
internal fun chartColors(kinds: List<SourceKind>): Map<SourceKind, Color> {
    val palette = PpfTheme.palette.chart
    val used = mutableSetOf<Int>()
    return kinds.associateWith { kind ->
        var slot = kind.chartSlot
        while (slot in used && used.size < palette.size) slot = (slot + 1) % palette.size
        used += slot
        palette[slot]
    }
}

internal fun valueText(value: Double, unit: String): String = if (unit == "%") "${FrenchNumbers.format(value)}${FrenchNumbers.GROUP_SEPARATOR}%" else FrenchNumbers.format(value)

/** A key figure: its latest values, one per source, side by side, with their date. */
@Composable
internal fun KeyFigureCard(indicator: Indicator, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val latest = indicator.latest
    PpfCard(onClick = onOpen, modifier = modifier.testTag(VoirTags.card(indicator.article.slug))) {
        VoirTitle(indicator.title, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        if (latest == null) {
            Text(stringResource(Res.string.voir_no_measure), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            return@PpfCard
        }
        val colors = chartColors(latest.measures.map { it.source.kind }.distinct())
        if (latest.measures.size == 1) {
            val measure = latest.measures.single()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(valueText(measure.value, indicator.unit), style = MaterialTheme.typography.headlineMedium, color = palette.ink)
                if (indicator.unit != "%") {
                    Spacer(Modifier.width(6.dp))
                    Text(indicator.unit, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Text(stringResource(measure.source.kind.label) + " · " + measure.source.name.withoutExampleSuffix(), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            indicator.breakdown?.let { label -> measure.part?.let { Text(stringResource(Res.string.voir_breakdown, label, FrenchNumbers.format(it)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) } }
        } else {
            latest.measures.forEach { measure ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors.getValue(measure.source.kind), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(measure.source.kind.label), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(valueText(measure.value, indicator.unit), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(indicator.unit.takeIf { it != "%" }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            // A measure without an occasion is named by its date, written the French way.
            if (latest.occasion == latest.date.toIso()) latest.date.toFrenchShort() else "${latest.occasion} · ${latest.date.toFrenchShort()}",
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val trend = trendOf(indicator)
        val badges = latest.diverges || indicator.declarative || trend != null
        if (badges) {
            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (latest.diverges) PpfBadge(stringResource(Res.string.voir_sources_diverge), BadgeStyle.WARNING)
                if (indicator.declarative) PpfBadge(stringResource(Res.string.voir_declarative), BadgeStyle.NEUTRAL)
                trend?.let { TrendLabel(it) }
            }
        }
    }
}

/** The removal of "(exemple)" from source names on small cards, where the [EXEMPLE] badge already says it. */
private fun String.withoutExampleSuffix(): String = removeSuffix(" (exemple)")

/** Up, down or stable since the previous occasion, for the first source present in both. */
internal data class Trend(val direction: Int, val since: CivilDate)

internal fun trendOf(indicator: Indicator): Trend? {
    val latest = indicator.latest ?: return null
    val previous = indicator.previous ?: return null
    val kind = latest.measures.map { it.source.kind }.firstOrNull { k -> previous.measures.any { it.source.kind == k } } ?: return null
    val now = latest.measures.first { it.source.kind == kind }.value
    val before = previous.measures.first { it.source.kind == kind }.value
    val direction = when {
        before == 0.0 && now == 0.0 -> 0
        abs(now - before) / maxOf(abs(before), 1.0) < 0.02 -> 0
        now > before -> 1
        else -> -1
    }
    return Trend(direction, previous.date)
}

@Composable
private fun TrendLabel(trend: Trend) {
    val palette = PpfTheme.palette
    val text = stringResource(
        when {
            trend.direction > 0 -> Res.string.voir_trend_up
            trend.direction < 0 -> Res.string.voir_trend_down
            else -> Res.string.voir_trend_same
        },
        trend.since.toFrenchShort(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                trend.direction > 0 -> Lucide.ChevronUp
                trend.direction < 0 -> Lucide.ChevronDown
                else -> Lucide.ChevronRight
            },
            contentDescription = null,
            tint = palette.inkMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
}

/** Key figures laid out in [columns] columns of equal width. */
@Composable
internal fun KeyFigureGrid(indicators: List<Indicator>, columns: Int, onOpen: (Indicator) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        indicators.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { indicator -> KeyFigureCard(indicator, onOpen = { onOpen(indicator) }, modifier = Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The page of an indicator: latest values side by side, chart or table, every measure, method, next steps. */
@Composable
internal fun IndicatorPage(
    indicator: Indicator,
    state: VoirState,
    wide: Boolean,
    onNext: (NextTarget) -> Unit,
    onExplain: () -> Unit,
) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(VoirTags.detail(indicator.article.slug))) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PpfBadge(stringResource(indicator.axis.label), BadgeStyle.BRAND)
            if (indicator.declarative) PpfBadge(stringResource(Res.string.voir_declarative), BadgeStyle.NEUTRAL)
        }
        Spacer(Modifier.height(8.dp))
        VoirTitle(indicator.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        indicator.definition?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(12.dp))
        ActionsRow(indicator, state)
        Spacer(Modifier.height(16.dp))
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1.5f)) { IndicatorMain(indicator, onExplain) }
                Column(Modifier.weight(1f)) { IndicatorAside(indicator, onNext) }
            }
        } else {
            IndicatorMain(indicator, onExplain)
            Spacer(Modifier.height(16.dp))
            IndicatorAside(indicator, onNext)
        }
    }
}

@Composable
private fun ActionsRow(indicator: Indicator, state: VoirState) {
    val palette = PpfTheme.palette
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember(indicator.address) { mutableStateOf(false) }
    val copyText = copyTextOf(indicator)
    LaunchedEffect(copied) {
        if (copied) {
            delay(4_000)
            copied = false
        }
    }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        FollowButton(state.isFollowed(indicator.address), indicator.title, onToggle = { state.toggleFollow(indicator.address) })
        if (indicator.latest != null) {
            PpfSecondaryButton(
                onClick = {
                    clipboard.setText(AnnotatedString(copyText))
                    copied = true
                },
                text = stringResource(Res.string.voir_copy_figure),
                small = true,
                icon = Lucide.Copy,
                modifier = Modifier.testTag(VoirTags.COPY),
            )
        }
        if (copied) Text(stringResource(Res.string.voir_copied), style = MaterialTheme.typography.bodySmall, color = palette.success)
    }
}

/**
 * The text copied with "Copier avec la source": every source of the latest occasion with its own
 * figure, its name and its address, and the [EXEMPLE] marker kept, so that a figure never travels
 * without where it comes from.
 */
@Composable
private fun copyTextOf(indicator: Indicator): String {
    val latest = indicator.latest ?: return indicator.title
    val labels = latest.measures.associate { it.id to stringResource(it.source.kind.label) }
    return buildString {
        append(indicator.title)
        append(" (")
        append(latest.occasion.takeIf { it != latest.date.toIso() }?.let { "$it, " }.orEmpty())
        append(latest.date.toFrench())
        append(")\n")
        latest.measures.forEach { measure ->
            append("- ")
            append(labels.getValue(measure.id))
            append(" : ")
            append(valueText(measure.value, indicator.unit))
            if (indicator.unit != "%") append(" ").append(indicator.unit)
            append(" (").append(measure.source.name)
            measure.source.url?.let { append(", ").append(it) }
            append(")\n")
        }
        append("Politisation Populaire Française")
    }
}

@Composable
private fun IndicatorMain(indicator: Indicator, onExplain: () -> Unit) {
    val latest = indicator.latest
    if (latest == null) {
        Text(stringResource(Res.string.voir_no_measure), style = MaterialTheme.typography.bodyLarge)
        return
    }
    LatestReading(indicator, latest, onExplain)
    Spacer(Modifier.height(16.dp))
    ChartCard(indicator)
    Spacer(Modifier.height(16.dp))
    AllMeasures(indicator)
}

/** The latest occasion, one tile per source, never merged into one figure. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LatestReading(indicator: Indicator, reading: Reading, onExplain: () -> Unit) {
    val palette = PpfTheme.palette
    val colors = chartColors(reading.measures.map { it.source.kind }.distinct())
    PpfCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(Res.string.voir_latest_reading))
        Text(
            listOfNotNull(reading.occasion.takeIf { it != reading.date.toIso() }, reading.date.toFrench(), reading.place).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkSubtle,
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = ((maxWidth + 12.dp) / (136.dp + 12.dp)).toInt().coerceIn(1, reading.measures.size)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                reading.measures.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { measure -> MeasureTile(indicator, measure, colors.getValue(measure.source.kind), Modifier.weight(1f).fillMaxHeight()) }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (reading.measures.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Lucide.Info, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.voir_side_by_side_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.weight(1f))
            }
        }
        if (indicator.declarative) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.voir_declarative_note), style = MaterialTheme.typography.bodySmall, color = palette.warning)
        }
        PpfTextButton(onClick = onExplain, text = stringResource(Res.string.voir_method_open), modifier = Modifier.testTag(VoirTags.GLOSSARY_OPEN))
    }
}

@Composable
private fun MeasureTile(indicator: Indicator, measure: Measure, color: Color, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val open = rememberOpenUrl()
    val url = measure.source.url
    Inset(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(measure.source.kind.label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(4.dp))
        Text(valueText(measure.value, indicator.unit), style = MaterialTheme.typography.headlineSmall)
        if (indicator.unit != "%") Text(indicator.unit, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        indicator.breakdown?.let { label -> measure.part?.let { Text(stringResource(Res.string.voir_breakdown, label, FrenchNumbers.format(it)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) } }
        Spacer(Modifier.height(4.dp))
        Text(measure.source.name, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        ReliabilityLabel(measure.reliability)
        if (url != null) {
            PpfTextButton(onClick = { open(url) }, text = stringResource(Res.string.voir_open_source))
        }
    }
}

/** The chart of the indicator, or the same values as a table (for screen readers, for print). */
@Composable
private fun ChartCard(indicator: Indicator) {
    var asTable by remember(indicator.address) { mutableStateOf(false) }
    val series = indicator.series
    PpfCard(Modifier.fillMaxWidth()) {
        SectionHeader(
            stringResource(Res.string.voir_chart_title),
            action = stringResource(if (asTable) Res.string.voir_chart_as_graph else Res.string.voir_chart_as_table),
            actionTag = VoirTags.CHART_TOGGLE,
            onAction = { asTable = !asTable },
        )
        if (asTable) MeasuresTable(indicator) else LineChart(indicator, series)
    }
}

/** A line per kind of source: 2 dp lines, 8 dp markers with a surface ring, hairline grid, legend and end labels. */
@Composable
private fun LineChart(indicator: Indicator, series: List<Series>) {
    val palette = PpfTheme.palette
    val density = LocalDensity.current
    val colors = chartColors(series.map { it.kind })
    val dates = series.flatMap { s -> s.points.map { it.first } }.distinct().sorted()
    if (dates.isEmpty()) return
    val maxValue = series.maxOf { s -> s.points.maxOf { it.second } }
    val top = niceCeiling(maxValue)
    val ticks = 4
    var selected by remember(indicator.address) { mutableStateOf<Int?>(null) }
    val description = stringResource(Res.string.voir_chart_description, indicator.title.withoutExample())

    if (series.size >= 2) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(16.dp).height(3.dp).background(colors.getValue(s.kind), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(s.kind.label), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).testTag(VoirTags.CHART).semantics { contentDescription = description }) {
        val axisWidth = if (top >= 100_000) 64.dp else 44.dp
        val endLabels = 64.dp
        val plotWidth = maxWidth - axisWidth - endLabels
        val plotHeight = 190.dp
        val firstDay = dates.first().toEpochDays()
        val lastDay = dates.last().toEpochDays()
        val span = (lastDay - firstDay).coerceAtLeast(1)
        fun xFor(date: CivilDate): Float = with(density) {
            if (dates.size == 1) (axisWidth + plotWidth / 2).toPx() else (axisWidth + plotWidth * ((date.toEpochDays() - firstDay).toFloat() / span)).toPx()
        }
        fun yFor(value: Double): Float = with(density) { (plotHeight * (1f - (value / top).toFloat())).toPx() + 8.dp.toPx() }
        val surface = palette.surface2
        val grid = palette.border

        Canvas(
            Modifier.fillMaxWidth().height(220.dp).pointerInput(dates, series) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: continue
                        selected = when (event.type) {
                            PointerEventType.Exit -> null
                            else -> dates.indices.minByOrNull { abs(xFor(dates[it]) - position.x) }
                        }
                    }
                }
            },
        ) {
            // Hairline grid: one step off the surface, solid, behind the marks.
            for (i in 0..ticks) {
                val y = yFor(top * i / ticks)
                drawLine(grid, Offset(axisWidth.toPx(), y), Offset((axisWidth + plotWidth).toPx(), y), strokeWidth = 1f)
            }
            selected?.let { index ->
                val x = xFor(dates[index])
                drawLine(palette.borderStrong, Offset(x, 8.dp.toPx()), Offset(x, yFor(0.0)), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            }
            series.forEach { s ->
                val color = colors.getValue(s.kind)
                if (s.points.size >= 2) {
                    val path = Path()
                    s.points.forEachIndexed { i, (date, value) -> if (i == 0) path.moveTo(xFor(date), yFor(value)) else path.lineTo(xFor(date), yFor(value)) }
                    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                s.points.forEach { (date, value) ->
                    val center = Offset(xFor(date), yFor(value))
                    drawCircle(surface, radius = 6.dp.toPx(), center = center)
                    drawCircle(color, radius = 4.dp.toPx(), center = center)
                }
            }
        }
        // Axis labels, in text colours (never the series colour).
        for (i in 0..ticks) {
            val value = top * i / ticks
            val y = with(density) { yFor(value).toDp() } - 8.dp
            Text(
                FrenchNumbers.short(value),
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle,
                maxLines = 1,
                modifier = Modifier.offset(y = y).width(axisWidth - 6.dp),
            )
        }
        Text(dates.first().toFrenchShort(), style = MaterialTheme.typography.labelSmall, color = palette.inkSubtle, modifier = Modifier.offset(x = axisWidth, y = 200.dp))
        if (dates.size > 1) {
            Text(
                dates.last().toFrenchShort(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkSubtle,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = -endLabels, y = 200.dp),
            )
        }
        // Direct labels at the end of each line, spread apart when they would collide.
        val ends = series.map { s -> s to with(density) { yFor(s.points.last().second).toDp() } }.sortedBy { it.second }
        var lastY = (-100).dp
        ends.forEach { (s, y) ->
            val placed = maxOf(y - 8.dp, lastY + 16.dp)
            lastY = placed
            val lastPoint = s.points.last()
            Text(
                FrenchNumbers.short(lastPoint.second),
                style = MaterialTheme.typography.labelSmall,
                color = palette.ink,
                maxLines = 1,
                modifier = Modifier.offset(x = maxWidth - endLabels + 6.dp, y = placed),
            )
        }
        selected?.let { index -> ChartTooltip(dates[index], series, colors, indicator, xDp = with(density) { xFor(dates[index]).toDp() }, maxWidth = maxWidth) }
    }
}

@Composable
private fun ChartTooltip(date: CivilDate, series: List<Series>, colors: Map<SourceKind, Color>, indicator: Indicator, xDp: androidx.compose.ui.unit.Dp, maxWidth: androidx.compose.ui.unit.Dp) {
    val palette = PpfTheme.palette
    val values = series.mapNotNull { s -> s.points.firstOrNull { it.first == date }?.let { s.kind to it.second } }
    if (values.isEmpty()) return
    val width = 220.dp
    val x = (xDp + 8.dp).coerceAtMost(maxWidth - width).coerceAtLeast(0.dp)
    // A plain box, not a Surface: a Surface catches the pointer, the chart would lose it and hide the
    // tooltip, which would give it back, endlessly.
    Box(
        Modifier.offset(x = x, y = 4.dp).width(width)
            .shadow(4.dp, MaterialTheme.shapes.small)
            .background(palette.surface2, MaterialTheme.shapes.small)
            .border(1.dp, palette.border, MaterialTheme.shapes.small),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(date.toFrench(), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle)
            values.forEach { (kind, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colors.getValue(kind), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(kind.label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(valueText(value, indicator.unit), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** A round top for the axis: 1, 2, 2.5 or 5 times a power of ten, above the largest value. */
internal fun niceCeiling(value: Double): Double {
    if (value <= 0) return 1.0
    val magnitude = 10.0.pow(kotlin.math.floor(log10(value)))
    val steps = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
    val ratio = value / magnitude
    val step = steps.first { it >= ratio - 1e-9 }
    return ceil(step * magnitude)
}

/** The data of the chart as a table: one row per occasion, one column per kind of source. */
@Composable
private fun MeasuresTable(indicator: Indicator) {
    val palette = PpfTheme.palette
    val kinds = indicator.series.map { it.kind }
    Column(Modifier.fillMaxWidth().testTag(VoirTags.TABLE)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(Res.string.voir_table_date), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle, modifier = Modifier.weight(1.4f))
            kinds.forEach { Text(stringResource(it.label), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle, modifier = Modifier.weight(1f)) }
        }
        HorizontalDivider(color = palette.border)
        indicator.readings.asReversed().forEach { reading ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.4f)) {
                    Text(reading.occasion.takeIf { it != reading.date.toIso() } ?: reading.date.toFrenchShort(), style = MaterialTheme.typography.bodyMedium)
                    if (reading.occasion != reading.date.toIso()) Text(reading.date.toFrenchShort(), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
                kinds.forEach { kind ->
                    val value = reading.measures.firstOrNull { it.source.kind == kind }?.value
                    Text(value?.let { valueText(it, indicator.unit) } ?: "–", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = palette.border)
        }
    }
}

/** Every occasion, newest first, each value with its source, its reliability and its link. */
@Composable
private fun AllMeasures(indicator: Indicator) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(Res.string.voir_all_measures))
        indicator.readings.asReversed().forEachIndexed { index, reading ->
            if (index > 0) HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                listOfNotNull(reading.occasion.takeIf { it != reading.date.toIso() }, reading.date.toFrenchShort(), reading.place).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
            )
            reading.measures.forEach { measure ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) { SourceLine(measure.source, reliability = measure.reliability) }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 8.dp)) {
                        Text(valueText(measure.value, indicator.unit), style = MaterialTheme.typography.titleMedium)
                        indicator.breakdown?.let { label -> measure.part?.let { Text(stringResource(Res.string.voir_breakdown, label, FrenchNumbers.format(it)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) } }
                    }
                }
            }
        }
    }
}

/** The method, the accepted sources, the corrections, the next steps, the origin. */
@Composable
private fun IndicatorAside(indicator: Indicator, onNext: (NextTarget) -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(Res.string.voir_method))
        if (indicator.acceptedSources.isNotEmpty()) {
            val names = indicator.acceptedSources.map { stringResource(it.label) }.joinToString(", ")
            Text(stringResource(Res.string.voir_accepted_sources, names), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            Spacer(Modifier.height(8.dp))
        }
        MarkdownText(indicator.method)
        CorrectionsSection(indicator.corrections)
        SourcesSection(indicator.sources)
        Spacer(Modifier.height(4.dp))
        OriginLine(indicator.article, cite = VoirItem.OfIndicator(indicator))
    }
    if (indicator.nextSteps.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        NextStepsSection(indicator.nextSteps, onNext)
    }
}

