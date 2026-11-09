package org.politisationpopulairefrancaise.ui.agir.poster

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.poster.PosterColor
import org.politisationpopulairefrancaise.core.agir.poster.PosterContent
import org.politisationpopulairefrancaise.core.agir.poster.PosterFact
import org.politisationpopulairefrancaise.core.agir.poster.PosterFormat
import org.politisationpopulairefrancaise.core.agir.poster.PosterKind
import org.politisationpopulairefrancaise.core.agir.poster.PosterLayouts
import org.politisationpopulairefrancaise.core.agir.poster.PosterPayload
import org.politisationpopulairefrancaise.core.agir.poster.PosterText
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaFormat
import org.politisationpopulairefrancaise.ui.agir.manifestation.GuideReader
import org.politisationpopulairefrancaise.ui.agir.manifestation.LocalHelpState
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.components.qr.QrCode
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.affiche_back
import org.politisationpopulairefrancaise.ui.resources.affiche_export_busy
import org.politisationpopulairefrancaise.ui.resources.affiche_export_done
import org.politisationpopulairefrancaise.ui.resources.affiche_export_failed
import org.politisationpopulairefrancaise.ui.resources.affiche_export_note_pdf
import org.politisationpopulairefrancaise.ui.resources.affiche_export_note_png
import org.politisationpopulairefrancaise.ui.resources.affiche_export_pdf
import org.politisationpopulairefrancaise.ui.resources.affiche_export_png
import org.politisationpopulairefrancaise.ui.resources.affiche_export_unavailable
import org.politisationpopulairefrancaise.ui.resources.affiche_export_unsupported
import org.politisationpopulairefrancaise.ui.resources.affiche_format_a3
import org.politisationpopulairefrancaise.ui.resources.affiche_format_a3_help
import org.politisationpopulairefrancaise.ui.resources.affiche_format_a4
import org.politisationpopulairefrancaise.ui.resources.affiche_format_a4_help
import org.politisationpopulairefrancaise.ui.resources.affiche_format_caption
import org.politisationpopulairefrancaise.ui.resources.affiche_format_title
import org.politisationpopulairefrancaise.ui.resources.affiche_guide_open
import org.politisationpopulairefrancaise.ui.resources.affiche_guide_text
import org.politisationpopulairefrancaise.ui.resources.affiche_guide_title
import org.politisationpopulairefrancaise.ui.resources.affiche_intro
import org.politisationpopulairefrancaise.ui.resources.affiche_preview_note
import org.politisationpopulairefrancaise.ui.resources.affiche_preview_title
import org.politisationpopulairefrancaise.ui.resources.affiche_print_deadline
import org.politisationpopulairefrancaise.ui.resources.affiche_print_footer
import org.politisationpopulairefrancaise.ui.resources.affiche_print_help_address
import org.politisationpopulairefrancaise.ui.resources.affiche_print_help_event
import org.politisationpopulairefrancaise.ui.resources.affiche_print_hosts
import org.politisationpopulairefrancaise.ui.resources.affiche_print_kind_call
import org.politisationpopulairefrancaise.ui.resources.affiche_print_launched
import org.politisationpopulairefrancaise.ui.resources.affiche_print_marker
import org.politisationpopulairefrancaise.ui.resources.affiche_print_scan_address_call
import org.politisationpopulairefrancaise.ui.resources.affiche_print_scan_address_event
import org.politisationpopulairefrancaise.ui.resources.affiche_print_scan_call
import org.politisationpopulairefrancaise.ui.resources.affiche_print_scan_event
import org.politisationpopulairefrancaise.ui.resources.affiche_print_territory
import org.politisationpopulairefrancaise.ui.resources.affiche_print_when
import org.politisationpopulairefrancaise.ui.resources.affiche_print_where
import org.politisationpopulairefrancaise.ui.resources.affiche_printer_help
import org.politisationpopulairefrancaise.ui.resources.affiche_printer_placeholder
import org.politisationpopulairefrancaise.ui.resources.affiche_printer_title
import org.politisationpopulairefrancaise.ui.resources.affiche_qr_address
import org.politisationpopulairefrancaise.ui.resources.affiche_qr_call
import org.politisationpopulairefrancaise.ui.resources.affiche_qr_description
import org.politisationpopulairefrancaise.ui.resources.affiche_qr_event
import org.politisationpopulairefrancaise.ui.resources.affiche_qr_private
import org.politisationpopulairefrancaise.ui.resources.affiche_title
import org.politisationpopulairefrancaise.ui.resources.agenda_date_range
import org.politisationpopulairefrancaise.ui.resources.agenda_date_time
import org.politisationpopulairefrancaise.ui.resources.agenda_time_range
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.agir.agenda.typeLabel as mobilizationTypeLabel
import org.politisationpopulairefrancaise.ui.agir.calls.typeLabelResource as callTypeLabelResource

/** Test tags of the posters (AG-61), also useful for accessibility tooling. */
object PosterTags {
    /** « Faire une affiche » on the detail of a mobilization or of a call. */
    const val MAKE = "poster-make"
    const val SCREEN = "poster-screen"
    const val BACK = "poster-back"
    const val FORMAT = "poster-format-" // followed by "a4" or "a3"
    const val FORMAT_CAPTION = "poster-format-caption"
    const val PREVIEW = "poster-preview"
    const val QR_NOTE = "poster-qr-note"
    const val PRINTER = "poster-printer"
    const val EXPORT = "poster-export"
    const val EXPORT_NOTE = "poster-export-note"
    const val EXPORT_MESSAGE = "poster-export-message"
    const val GUIDE = "poster-guide"
    const val GUIDE_OPEN = "poster-guide-open"

    /** « Scanner une affiche » in the agenda (Android only). */
    const val SCAN = "poster-scan"
    const val SCAN_SCREEN = "poster-scan-screen"
    const val SCAN_BACK = "poster-scan-back"
    const val SCAN_RESULT = "poster-scan-result"
    const val SCAN_SIGNER = "poster-scan-signer"
    const val SCAN_ADD = "poster-scan-add"
    const val SCAN_OPEN = "poster-scan-open"
    const val SCAN_AGAIN = "poster-scan-again"
    const val SCAN_MESSAGE = "poster-scan-message"
}

/** The practical guide the poster screen points to (written with its sources in `content/fiches/`). */
const val POSTER_GUIDE: String = "coller-des-affiches-et-distribuer-des-tracts"

/** Longest printer's mention, in characters: two short lines at the bottom of the poster. */
private const val MAX_PRINTER: Int = 160

/**
 * « Faire une affiche » (AG-61, step 1): an A4 or A3 poster of a mobilization or of a call, drawn
 * on the device, with the QR code of the signed content itself (or of its stable address when the
 * content is too long). The person picks the format, may add the printer's mention, sees a preview,
 * saves the file where they choose, and reads the practical guide of free posting. Nothing is
 * published, sent or stored by the application: making a poster publishes nothing.
 *
 * [hostNames] are the organisers as the application names them. [fileKind] is what the platform
 * saves (null: not available, the screen says so).
 */
@Composable
internal fun PosterScreen(
    content: PosterContent,
    hostNames: List<String>,
    compact: Boolean,
    exporter: PosterExporter,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fileKind: PosterFileKind? = posterFileKind,
) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var format by remember(content.address) { mutableStateOf(PosterFormat.A4) }
    var printer by remember(content.address) { mutableStateOf("") }
    var busy by remember(content.address) { mutableStateOf(false) }
    var result by remember(content.address) { mutableStateOf<PosterExportResult?>(null) }
    var guideOpen by remember(content.address) { mutableStateOf(false) }
    val help = LocalHelpState.current
    val padding = if (compact) 16.dp else 24.dp

    // The system's back closes the guide first, then the poster screen.
    SystemBack(enabled = true) { if (guideOpen) guideOpen = false else onBack() }
    if (guideOpen) {
        Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(padding)) {
            GuideReader(help?.guide(POSTER_GUIDE), onBack = { guideOpen = false })
        }
        return
    }

    val text = posterText(content, hostNames, printer)
    Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(padding).testTag(PosterTags.SCREEN)) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.affiche_back), onBack, Modifier.testTag(PosterTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.affiche_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(6.dp))
                Text(stringResource(Res.string.affiche_intro), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            }

            Column {
                Text(stringResource(Res.string.affiche_format_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Column(Modifier.selectableGroup()) {
                    PosterFormat.entries.forEach { option ->
                        PpfRadioRow(
                            label = stringResource(if (option == PosterFormat.A3) Res.string.affiche_format_a3 else Res.string.affiche_format_a4),
                            selected = format == option,
                            onClick = {
                                format = option
                                result = null
                            },
                            supporting = stringResource(if (option == PosterFormat.A3) Res.string.affiche_format_a3_help else Res.string.affiche_format_a4_help),
                            modifier = Modifier.testTag(PosterTags.FORMAT + option.name.lowercase()),
                        )
                    }
                }
            }

            Column {
                Text(stringResource(Res.string.affiche_preview_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                PosterPreview(text, content.payload.text, format, Modifier.widthIn(max = if (compact) 340.dp else 420.dp).fillMaxWidth().testTag(PosterTags.PREVIEW))
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        Res.string.affiche_format_caption,
                        stringResource(if (format == PosterFormat.A3) Res.string.affiche_format_a3 else Res.string.affiche_format_a4),
                        stringResource(if (format == PosterFormat.A3) Res.string.affiche_format_a3_help else Res.string.affiche_format_a4_help),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(PosterTags.FORMAT_CAPTION),
                )
                Text(stringResource(Res.string.affiche_preview_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }

            InfoBox(Lucide.ShieldCheck, Modifier.testTag(PosterTags.QR_NOTE)) {
                val note = when {
                    content.payload is PosterPayload.Address -> Res.string.affiche_qr_address
                    content.kind == PosterKind.CALL -> Res.string.affiche_qr_call
                    else -> Res.string.affiche_qr_event
                }
                Text(stringResource(note), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.affiche_qr_private), style = MaterialTheme.typography.bodyMedium)
            }

            Column {
                Text(stringResource(Res.string.affiche_printer_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(6.dp))
                PpfTextField(
                    value = printer,
                    onValueChange = { printer = it.replace('\n', ' ').take(MAX_PRINTER) },
                    placeholder = stringResource(Res.string.affiche_printer_placeholder),
                    modifier = Modifier.fillMaxWidth().testTag(PosterTags.PRINTER),
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.affiche_printer_help), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }

            Column {
                if (fileKind == null) {
                    Text(stringResource(Res.string.affiche_export_unavailable), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(PosterTags.EXPORT_NOTE))
                } else {
                    val document = PosterDocument(format, text, content.payload.text, content.fileName(format))
                    PpfPrimaryButton(
                        onClick = {
                            busy = true
                            result = null
                            scope.launch {
                                try {
                                    result = exporter.export(document)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        text = stringResource(if (fileKind == PosterFileKind.PDF) Res.string.affiche_export_pdf else Res.string.affiche_export_png),
                        enabled = !busy,
                        icon = Lucide.Printer,
                        modifier = Modifier.testTag(PosterTags.EXPORT),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(if (fileKind == PosterFileKind.PDF) Res.string.affiche_export_note_pdf else Res.string.affiche_export_note_png),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle,
                        modifier = Modifier.testTag(PosterTags.EXPORT_NOTE),
                    )
                }
                val message = when {
                    busy -> Res.string.affiche_export_busy
                    else -> when (result) {
                        PosterExportResult.DONE -> Res.string.affiche_export_done
                        PosterExportResult.FAILED -> Res.string.affiche_export_failed
                        PosterExportResult.UNSUPPORTED -> Res.string.affiche_export_unsupported
                        PosterExportResult.CANCELLED, null -> null
                    }
                }
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(message),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(PosterTags.EXPORT_MESSAGE),
                    )
                }
            }

            PpfCard(Modifier.fillMaxWidth().testTag(PosterTags.GUIDE)) {
                Text(stringResource(Res.string.affiche_guide_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(6.dp))
                Text(stringResource(Res.string.affiche_guide_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton(
                    onClick = { guideOpen = true },
                    text = stringResource(Res.string.affiche_guide_open),
                    icon = Lucide.BookOpen,
                    modifier = Modifier.fillMaxWidth().testTag(PosterTags.GUIDE_OPEN),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The words of the poster of [content], in French, from the string resources: the kind, the title,
 * when, where, who (as the application names them), the short text, what scanning the code does,
 * the footer and the printer's mention typed by the person.
 */
@Composable
internal fun posterText(content: PosterContent, hostNames: List<String>, printer: String): PosterText {
    val call = content.kind == PosterKind.CALL
    val kind = if (call) {
        stringResource(Res.string.affiche_print_kind_call, stringResource(callTypeLabelResource(content.callType ?: CallType.AUTRE)).uppercase())
    } else {
        mobilizationTypeLabel(content.mobilizationType ?: MobilizationType.AUTRE).uppercase()
    }
    val facts = buildList {
        content.start?.let { start -> add(PosterFact(stringResource(Res.string.affiche_print_when), whenOf(start, content.end), emphasis = true)) }
        content.deadline?.let { deadline -> add(PosterFact(stringResource(Res.string.affiche_print_deadline), deadlineOf(deadline), emphasis = content.start == null)) }
        val place = content.place
        if (place != null) {
            add(PosterFact(stringResource(Res.string.affiche_print_where), place))
        } else {
            add(PosterFact(stringResource(Res.string.affiche_print_territory), content.territory))
        }
        if (hostNames.isNotEmpty()) {
            add(PosterFact(stringResource(if (call) Res.string.affiche_print_launched else Res.string.affiche_print_hosts), hostNames.joinToString(", ")))
        }
    }
    val address = content.payload is PosterPayload.Address
    val scanTitle = stringResource(
        when {
            address && call -> Res.string.affiche_print_scan_address_call
            address -> Res.string.affiche_print_scan_address_event
            call -> Res.string.affiche_print_scan_call
            else -> Res.string.affiche_print_scan_event
        },
    )
    return PosterText(
        marker = if (content.example) stringResource(Res.string.affiche_print_marker) else null,
        kind = kind,
        title = content.title,
        facts = facts,
        body = content.text,
        scanTitle = scanTitle,
        scanHelp = stringResource(if (address) Res.string.affiche_print_help_address else Res.string.affiche_print_help_event),
        footer = stringResource(Res.string.affiche_print_footer),
        printer = printer.trim().takeIf { it.isNotEmpty() },
    )
}

/** "Samedi 14 novembre 2026, 14 h à 17 h", or both dates for an event over several days (as the detail of the agenda writes it). */
@Composable
private fun whenOf(start: Long, end: Long?): String {
    val from = ParisTime.moment(start)
    val to = end?.takeIf { it > start }?.let(ParisTime::moment)
    if (to != null && to.epochDay != from.epochDay) {
        return stringResource(
            Res.string.agenda_date_range,
            stringResource(Res.string.agenda_date_time, AgendaFormat.date(from, withYear = true), AgendaFormat.hour(from)),
            stringResource(Res.string.agenda_date_time, AgendaFormat.date(to, withYear = true), AgendaFormat.hour(to)),
        )
    }
    val hours = if (to != null) stringResource(Res.string.agenda_time_range, AgendaFormat.hour(from), AgendaFormat.hour(to)) else AgendaFormat.hour(from)
    return stringResource(Res.string.agenda_date_time, AgendaFormat.capitalized(AgendaFormat.date(from, withYear = true)), hours)
}

/** "Samedi 14 novembre 2026", with the hour unless the deadline lasts the whole day (23 h 59). */
@Composable
private fun deadlineOf(deadline: Long): String {
    val moment = ParisTime.moment(deadline)
    val day = AgendaFormat.capitalized(AgendaFormat.date(moment, withYear = true))
    return if (moment.hour == 23 && moment.minute == 59) day else stringResource(Res.string.agenda_date_time, day, AgendaFormat.hour(moment))
}

/** A colour of the printed poster ([PosterColor]), for the preview. */
private fun printColor(color: PosterColor): Color = Color(OPAQUE or color.rgb)

private const val OPAQUE: Int = -0x1000000

/**
 * A preview of the poster in the proportions of the page: the same words and the same code, black
 * on white whatever the theme (it shows paper). The exact layout is that of the saved file, drawn
 * by the platform from the common layout.
 */
@Composable
private fun PosterPreview(text: PosterText, code: String, format: PosterFormat, modifier: Modifier) {
    val ink = printColor(PosterColor.INK)
    val brand = printColor(PosterColor.BRAND)
    Surface(
        modifier = modifier.aspectRatio(format.widthPt.toFloat() / format.heightPt),
        shape = RectangleShape,
        color = printColor(PosterColor.PAPER),
        contentColor = ink,
        border = BorderStroke(1.dp, PpfTheme.palette.borderStrong),
        shadowElevation = 2.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            // The sizes of the layout are written for A4 (595 points wide); an A3 is the same page enlarged.
            val scale = maxWidth.value / PosterFormat.A4.widthPt
            Column(Modifier.fillMaxSize().padding((PosterLayouts.MARGIN * scale).dp).semantics(mergeDescendants = true) { }) {
                text.marker?.let { PreviewLine(it, PosterLayouts.MARKER_SIZE, true, brand, 1, scale) }
                PreviewLine(text.kind, PosterLayouts.KIND_SIZE, true, brand, 2, scale)
                Spacer(Modifier.height((6 * scale).dp))
                PreviewLine(text.title, PosterLayouts.TITLE_SIZES[2], true, ink, PosterLayouts.TITLE_MAX_LINES, scale)
                Spacer(Modifier.height((12 * scale).dp))
                Spacer(Modifier.fillMaxWidth().height((3 * scale).dp).background(brand))
                Spacer(Modifier.height((12 * scale).dp))
                text.facts.forEach { fact ->
                    PreviewLine(fact.label, PosterLayouts.LABEL_SIZE, true, brand, 1, scale)
                    PreviewLine(fact.value, if (fact.emphasis) PosterLayouts.EMPHASIS_SIZE else PosterLayouts.VALUE_SIZE, fact.emphasis, ink, 2, scale)
                    Spacer(Modifier.height((8 * scale).dp))
                }
                text.body?.let { PreviewLine(it, PosterLayouts.BODY_SIZE, false, ink, 4, scale) }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Top) {
                    QrCode(code, contentDescription = stringResource(Res.string.affiche_qr_description), modifier = Modifier.size((PosterLayouts.CODE_SIDE * scale).dp))
                    Spacer(Modifier.width((PosterLayouts.CODE_GAP * scale).dp))
                    Column(Modifier.weight(1f)) {
                        PreviewLine(text.scanTitle, PosterLayouts.SCAN_TITLE_SIZE, true, ink, 5, scale)
                        text.scanHelp?.let {
                            Spacer(Modifier.height((8 * scale).dp))
                            PreviewLine(it, PosterLayouts.SCAN_HELP_SIZE, false, ink, 6, scale)
                        }
                    }
                }
                Spacer(Modifier.height((10 * scale).dp))
                PreviewLine(text.footer, PosterLayouts.FOOTER_SIZE, false, ink, 2, scale)
                text.printer?.let { PreviewLine(it, PosterLayouts.PRINTER_SIZE, false, ink, 2, scale) }
            }
        }
    }
}

/** One text of the preview at [size] points of the page, in the typeface of the identity. */
@Composable
private fun PreviewLine(text: String, size: Float, bold: Boolean, color: Color, maxLines: Int, scale: Float) {
    val fontSize = with(LocalDensity.current) { (size * scale).dp.toSp() }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize, lineHeight = fontSize * 1.2f, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
