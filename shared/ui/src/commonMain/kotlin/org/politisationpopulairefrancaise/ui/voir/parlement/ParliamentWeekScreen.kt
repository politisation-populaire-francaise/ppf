package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.parlement.AgendaStatus
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.DataOrigin
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentCatalog
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeek
import org.politisationpopulairefrancaise.core.voir.parlement.SittingKind
import org.politisationpopulairefrancaise.core.voir.parlement.WeekItem
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.VoirTitle

/** Test tags of « Cette semaine au Parlement ». */
object WeekTags {
    const val ENTRY = "week-entry"
    const val PAGE = "week-page"
    const val SLOT = "week-slot"
    const val SLOT_ALL = "week-slot-all"
    const val EXAMPLE = "week-example"
    const val DATA_DATE = "week-data-date"
    const val STALE = "week-stale"
    const val DOWNLOAD = "week-download"
    const val NOT_OFFERED = "week-not-offered"
    const val NOTHING = "week-nothing"
    const val LOADING = "week-loading"
    const val ATTRIBUTION = "week-attribution"
    fun day(iso: String) = "week-day-$iso"
    fun item(id: String) = "week-item-$id"
    fun slotItem(id: String) = "week-slot-item-$id"
    fun chamber(id: String) = "week-chamber-$id"
    fun link(kind: String, id: String) = "week-link-$kind-$id"
}

/** How many items the block of « En direct » shows. */
private const val SLOT_ITEMS = 4

// ---------------------------------------------------------------------------------------------
// Entry on the parliamentary screen

/** The card of the week on the list of « Parlementaires ». */
@Composable
fun ParliamentWeekEntry(week: ParliamentWeekState, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier.fillMaxWidth().testTag(WeekTags.ENTRY), onClick = { week.openPage() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Calendar, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.week_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.week_card_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.week_card_open), style = MaterialTheme.typography.labelLarge, color = palette.brand)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The page

/**
 * « Cette semaine au Parlement » (VO-31), a page of the parliamentary screen: the coming days, the
 * sittings of each assembly and their points, the bill of "Voir" when its `ppf-official-id` is the
 * legislative file of a point, the official pages (after « Vous quittez PPF »), the source and the
 * date of the data, and the [EXEMPLE] banner for fictional data. A day header on the left of its
 * items on wide windows, above them on phones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParliamentWeekPage(parliament: ParliamentState, padding: Dp) {
    val week = parliament.week
    val palette = PpfTheme.palette
    if (week == null) {
        Text(stringResource(Res.string.week_not_offered), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, modifier = Modifier.padding(padding))
        return
    }
    val snapshot = week.snapshot
    LaunchedEffect(snapshot) { week.refresh(autoUpdate = true) }
    val leave = rememberLeave()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(WeekTags.PAGE)) {
            Column(Modifier.widthIn(max = 980.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.week_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(stringResource(Res.string.week_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                WeekProvenance(week, chambers = week.holding?.week?.chambers())
                WeekDownload(week)
                val held = week.holding?.week
                if (held != null) {
                    val chambers = held.chambers()
                    if (chambers.size > 1) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PpfSelectableChip(stringResource(Res.string.week_filter_all), selected = week.chamberFilter == null, onClick = { week.chamberFilter = null }, modifier = Modifier.testTag(WeekTags.chamber("all")))
                            chambers.forEach { chamber ->
                                PpfSelectableChip(stringResource(chamber.label), selected = week.chamberFilter == chamber, onClick = { week.chamberFilter = chamber }, modifier = Modifier.testTag(WeekTags.chamber(ParliamentCatalog.sourceId(chamber))))
                            }
                        }
                    }
                    val days = held.days(week.today, week.chamberFilter?.takeIf { it in chambers })
                    if (days.isEmpty()) {
                        Text(stringResource(Res.string.week_nothing), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, modifier = Modifier.testTag(WeekTags.NOTHING))
                    }
                    days.forEach { day ->
                        val header: @Composable (Modifier) -> Unit = { m ->
                            Text(dayLabel(day.date, week.today), style = MaterialTheme.typography.titleMedium, modifier = m.semantics { heading() })
                        }
                        val items: @Composable (Modifier) -> Unit = { m ->
                            Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                day.items.forEach { item -> WeekItemCard(item, week, leave) }
                            }
                        }
                        if (wide) {
                            Row(Modifier.fillMaxWidth().testTag(WeekTags.day(day.date.toIso())), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                header(Modifier.width(200.dp).padding(top = 8.dp))
                                items(Modifier.weight(1f))
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().testTag(WeekTags.day(day.date.toIso())), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                header(Modifier)
                                items(Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (Chamber.PE in chambers) Text(stringResource(Res.string.week_pe_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
            }
        }
    }
}

/** One point of the agenda: when, where, what, its status, then where it leads. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekItemCard(item: WeekItem, week: ParliamentWeekState, leave: (String) -> Unit) {
    val palette = PpfTheme.palette
    val bill = item.dossier?.let { week.links.billFor(it) }
    Surface(Modifier.fillMaxWidth().testTag(WeekTags.item(item.id)), shape = MaterialTheme.shapes.small, color = palette.surface2, contentColor = palette.ink) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(whereAndWhen(item), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
            VoirTitle(item.title ?: stringResource(Res.string.week_no_title), style = MaterialTheme.typography.titleSmall)
            val badges = listOfNotNull(item.pointKind, item.status?.let { stringResource(it.label) })
            if (badges.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    badges.forEach { PpfBadge(it, BadgeStyle.NEUTRAL) }
                }
            }
            item.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 8, overflow = TextOverflow.Ellipsis) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bill != null) WeekLink(stringResource(Res.string.week_voir_bill), Lucide.ScrollText, WeekTags.link("bill", item.id)) { week.links.openBill(bill) }
                item.dossierUrl?.let { url -> WeekLink(stringResource(Res.string.week_dossier), Lucide.ExternalLink, WeekTags.link("dossier", item.id)) { leave(url) } }
                item.url?.let { url -> WeekLink(stringResource(Res.string.week_official), Lucide.ExternalLink, WeekTags.link("official", item.id)) { leave(url) } }
                WeekLink(stringResource(Res.string.week_who_decides), Lucide.Landmark, WeekTags.link("who", item.id)) { week.links.navigate(Space.COMPRENDRE) }
                WeekLink(stringResource(Res.string.week_interpellate), Lucide.Megaphone, WeekTags.link("interpellate", item.id)) { week.links.navigate(Space.DECIDER) }
            }
        }
    }
}

/** A small tappable link of an item, 44 dp high at least. */
@Composable
private fun WeekLink(text: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    val palette = PpfTheme.palette
    Surface(onClick = onClick, modifier = Modifier.heightIn(min = 44.dp).testTag(tag).semantics { role = Role.Button }, shape = MaterialTheme.shapes.small, color = palette.surface1, contentColor = palette.ink) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Where the data come from: the [EXEMPLE] banner, the day of the data and the warning after two
 * weeks, the copy of the device or of the application, and the attribution of each assembly shown,
 * never presented as official.
 */
@Composable
private fun WeekProvenance(week: ParliamentWeekState, chambers: List<Chamber>?, compact: Boolean = false) {
    val snapshot = week.snapshot ?: return
    val palette = PpfTheme.palette
    val example = snapshot.example || week.holding?.week?.example == true
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        if (example) {
            Surface(Modifier.fillMaxWidth().testTag(WeekTags.EXAMPLE), shape = MaterialTheme.shapes.small, color = palette.warningTint, contentColor = palette.ink) {
                Row(Modifier.padding(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(Res.string.week_example_banner), style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val date = snapshot.importDate?.toFrench() ?: snapshot.index.importedOn
        val held = week.holding?.week?.pkg
        val period = held?.let { p -> CivilDate.parseIso(p.from)?.let { from -> CivilDate.parseIso(p.to)?.let { to -> stringResource(Res.string.week_period, from.toFrench(), to.toFrench()) } } }
        Text(listOfNotNull(stringResource(Res.string.parl_data_of, date), period).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted, modifier = Modifier.testTag(WeekTags.DATA_DATE))
        if (snapshot.isStale(week.today)) {
            InfoBox(Lucide.Clock, Modifier.testTag(WeekTags.STALE)) { Text(stringResource(Res.string.parl_stale, date), style = MaterialTheme.typography.bodyMedium) }
        }
        if (compact) return@Column
        when (snapshot.origin) {
            DataOrigin.DEVICE -> Text(stringResource(Res.string.parl_offline), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            DataOrigin.BUNDLED -> Text(stringResource(Res.string.parl_bundled), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            DataOrigin.SERVER -> Unit
        }
        val ids = chambers?.map(ParliamentCatalog::sourceId)
        Column(Modifier.fillMaxWidth().testTag(WeekTags.ATTRIBUTION), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            snapshot.index.sources.filter { ids == null || it.id in ids }.forEach { source -> Text(attribution(source), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
            if (ids == null || "senat" in ids) Text(stringResource(Res.string.week_senat_source), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}

/** The download of the package when the device does not hold it (or holds an older one), its size shown first. */
@Composable
private fun WeekDownload(week: ParliamentWeekState, compact: Boolean = false) {
    val palette = PpfTheme.palette
    val snapshot = week.snapshot
    val holding = week.holding
    val ref = week.ref
    when {
        snapshot == null || !week.checked -> Text(stringResource(Res.string.week_loading), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(WeekTags.LOADING))
        ref == null && holding == null -> Text(stringResource(Res.string.week_not_offered), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(WeekTags.NOT_OFFERED))
        ref != null && (holding == null || holding.outdated) -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (holding == null) Text(stringResource(Res.string.week_needed), style = MaterialTheme.typography.bodyMedium)
            PpfPrimaryButton(
                onClick = { week.download() },
                text = if (week.downloading) stringResource(Res.string.week_downloading) else stringResource(if (holding == null) Res.string.week_download else Res.string.week_update, fileSize(ref.bytes)),
                enabled = !week.downloading,
                small = compact,
                icon = Lucide.RefreshCw,
                modifier = Modifier.testTag(WeekTags.DOWNLOAD),
            )
            if (!compact) Text(stringResource(Res.string.week_download_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            val problem = when (week.lastOutcome) {
                is PackageOutcome.Refused -> stringResource(Res.string.parl_download_refused)
                is PackageOutcome.Unavailable -> stringResource(Res.string.parl_download_unavailable)
                is PackageOutcome.Unknown -> stringResource(Res.string.parl_download_unknown)
                PackageOutcome.Done, null -> null
            }
            problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------------------------------------
// The block of « En direct »

/**
 * The block « Cette semaine au Parlement » of « En direct » (M19): the next points of the agenda,
 * one touch to the whole week (in "Voir"). Opening « En direct » asks the signed index when it is
 * not known yet, and updates the package only when a version is already on the device and the
 * data saver is off; otherwise its size is shown and the download waits for a gesture.
 */
@Composable
fun ParliamentWeekSlot(parliament: ParliamentState, modifier: Modifier = Modifier) {
    val week = parliament.week ?: return
    val palette = PpfTheme.palette
    val snapshot = week.snapshot
    LaunchedEffect(snapshot) { week.onLiveOpened() }
    val openWeek: () -> Unit = {
        week.openPage()
        week.links.navigate(Space.VOIR)
    }
    Column(modifier.fillMaxWidth().testTag(WeekTags.SLOT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.week_live_title), action = stringResource(Res.string.week_see_all), actionTag = WeekTags.SLOT_ALL, onAction = openWeek)
        WeekDownload(week, compact = true)
        val held = week.holding?.week
        if (held != null) {
            WeekProvenance(week, chambers = null, compact = true)
            val upcoming = held.upcoming(week.today)
            if (upcoming.isEmpty()) Text(stringResource(Res.string.week_nothing), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(WeekTags.NOTHING))
            upcoming.take(SLOT_ITEMS).forEach { item ->
                Surface(
                    onClick = openWeek,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(WeekTags.slotItem(item.id)),
                    shape = MaterialTheme.shapes.small,
                    color = palette.surface2,
                    contentColor = palette.ink,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(shortWhen(item, week.today), style = MaterialTheme.typography.labelMedium, color = palette.inkMuted)
                            VoirTitle(item.title ?: stringResource(Res.string.week_no_title), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        }
                        Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (upcoming.size > SLOT_ITEMS) Text(stringResource(Res.string.week_more, count(upcoming.size - SLOT_ITEMS)), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Words

/** `15:00` to « 15 h », `09:30` to « 9 h 30 », with non-breaking spaces; null for an item without hour (00:00). */
internal fun frenchTime(hhmm: String?): String? {
    val value = hhmm?.takeIf { it.length == 5 && it != "00:00" } ?: return null
    val hours = value.take(2).toIntOrNull() ?: return null
    val minutes = value.substring(3, 5)
    return if (minutes == "00") "$hours h" else "$hours h $minutes"
}

@Composable
private fun timeRange(item: WeekItem): String? {
    val start = frenchTime(item.startTime) ?: return null
    val end = frenchTime(item.endTime)
    return if (end != null) stringResource(Res.string.week_time_range, start, end) else start
}

@Composable
private fun whereAndWhen(item: WeekItem): String {
    val kind = stringResource(item.kind.label)
    return listOfNotNull(timeRange(item), stringResource(item.chamber.label), item.body?.removePrefix(ParliamentCatalog.EXAMPLE_MARKER)?.trim()?.let { "$kind, $it" } ?: kind).joinToString(" · ")
}

@Composable
private fun shortWhen(item: WeekItem, today: CivilDate): String {
    val day = item.day?.let { d -> if (d == today) stringResource(Res.string.week_today) else stringResource(Res.string.week_day_label, stringResource(weekdays[ParliamentWeek.weekday(d) - 1]), d.toFrenchShort()) }
    return listOfNotNull(day, frenchTime(item.startTime), stringResource(item.chamber.label)).joinToString(" · ")
}

@Composable
private fun dayLabel(date: CivilDate, today: CivilDate): String {
    val label = stringResource(Res.string.week_day_label, stringResource(weekdays[ParliamentWeek.weekday(date) - 1]), date.toFrench())
    return if (date == today) "${stringResource(Res.string.week_today)} · $label" else label
}

private val weekdays: List<StringResource> = listOf(
    Res.string.week_day_1, Res.string.week_day_2, Res.string.week_day_3, Res.string.week_day_4,
    Res.string.week_day_5, Res.string.week_day_6, Res.string.week_day_7,
)

private val SittingKind.label: StringResource get() = when (this) {
    SittingKind.PLENARY -> Res.string.week_kind_plenary
    SittingKind.COMMITTEE -> Res.string.week_kind_committee
    SittingKind.OTHER -> Res.string.week_kind_other
}

private val AgendaStatus.label: StringResource get() = when (this) {
    AgendaStatus.CONFIRMED -> Res.string.week_status_confirmed
    AgendaStatus.PROVISIONAL -> Res.string.week_status_provisional
    AgendaStatus.CANCELLED -> Res.string.week_status_cancelled
}
