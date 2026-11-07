package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaPeriod
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaQuery
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRemovableChip
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTabs
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfToolbarButton
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_chip_remove
import org.politisationpopulairefrancaise.ui.resources.agenda_chip_text
import org.politisationpopulairefrancaise.ui.resources.agenda_clear_all
import org.politisationpopulairefrancaise.ui.resources.agenda_clear_filters
import org.politisationpopulairefrancaise.ui.resources.agenda_count
import org.politisationpopulairefrancaise.ui.resources.agenda_create_account
import org.politisationpopulairefrancaise.ui.resources.agenda_editorial_team
import org.politisationpopulairefrancaise.ui.resources.agenda_empty_filters
import org.politisationpopulairefrancaise.ui.resources.agenda_empty_past
import org.politisationpopulairefrancaise.ui.resources.agenda_empty_upcoming
import org.politisationpopulairefrancaise.ui.resources.agenda_filters
import org.politisationpopulairefrancaise.ui.resources.agenda_filters_active
import org.politisationpopulairefrancaise.ui.resources.agenda_forget
import org.politisationpopulairefrancaise.ui.resources.agenda_intro
import org.politisationpopulairefrancaise.ui.resources.agenda_map_soon_text
import org.politisationpopulairefrancaise.ui.resources.agenda_map_soon_title
import org.politisationpopulairefrancaise.ui.resources.agenda_mine_empty
import org.politisationpopulairefrancaise.ui.resources.agenda_mine_intro
import org.politisationpopulairefrancaise.ui.resources.agenda_need_account_mine
import org.politisationpopulairefrancaise.ui.resources.agenda_ongoing
import org.politisationpopulairefrancaise.ui.resources.agenda_organized_by
import org.politisationpopulairefrancaise.ui.resources.agenda_period_month
import org.politisationpopulairefrancaise.ui.resources.agenda_period_today
import org.politisationpopulairefrancaise.ui.resources.agenda_period_week
import org.politisationpopulairefrancaise.ui.resources.agenda_propose
import org.politisationpopulairefrancaise.ui.resources.agenda_range_both
import org.politisationpopulairefrancaise.ui.resources.agenda_range_since
import org.politisationpopulairefrancaise.ui.resources.agenda_range_until
import org.politisationpopulairefrancaise.ui.resources.agenda_refresh
import org.politisationpopulairefrancaise.ui.resources.agenda_select_hint
import org.politisationpopulairefrancaise.ui.resources.agenda_state_reason
import org.politisationpopulairefrancaise.ui.resources.agenda_state_waiting_text
import org.politisationpopulairefrancaise.ui.resources.agenda_tab_mine
import org.politisationpopulairefrancaise.ui.resources.agenda_tab_past
import org.politisationpopulairefrancaise.ui.resources.agenda_tab_upcoming
import org.politisationpopulairefrancaise.ui.resources.agenda_title
import org.politisationpopulairefrancaise.ui.resources.content_status_offline
import org.politisationpopulairefrancaise.ui.resources.content_status_unreachable
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the agenda, also useful for accessibility tooling. */
object AgendaTags {
    const val SCREEN = "agenda-screen"
    const val LIST = "agenda-list"
    const val TITLE = "agenda-title"
    const val MAP_SOON = "agenda-map-soon"
    const val TAB = "agenda-tab-" // followed by upcoming, past or mine
    const val REFRESH = "agenda-refresh"
    const val FILTERS = "agenda-filters"
    const val PROPOSE = "agenda-propose"
    const val COUNT = "agenda-count"
    const val DAY = "agenda-day-" // followed by the local day number, or "ongoing"
    const val CARD = "agenda-card-" // followed by the identifier of the mobilization
    const val STATUS = "agenda-status-" // followed by "annule" or "reporte"
    const val EMPTY = "agenda-empty"
    const val EMPTY_CLEAR = "agenda-empty-clear"
    const val CHIP = "agenda-chip-" // followed by "period", "territory:<key>", "type:<tag>", "organizer:<key>" or "text"
    const val CLEAR_ALL = "agenda-clear-all"
    const val STATUS_TEXT = "agenda-status-text"
    const val SELECT_HINT = "agenda-select-hint"

    const val DETAIL = "agenda-detail"
    const val BACK = "agenda-back"
    const val WHEN = "agenda-when"
    const val WHERE = "agenda-where"
    const val ORGANIZERS = "agenda-organizers"
    const val DESCRIPTION = "agenda-description"
    const val NOTICE = "agenda-notice"
    const val LINK = "agenda-link"
    const val EXPORT = "agenda-export"
    const val EXPORT_DIALOG = "agenda-export-dialog"
    const val EXPORT_CANCEL = "agenda-export-cancel"
    const val EXPORT_CONFIRM = "agenda-export-confirm"
    const val EXPORT_MESSAGE = "agenda-export-message"
    const val LEAVE_DIALOG = "agenda-leave-dialog"
    const val LEAVE_STAY = "agenda-leave-stay"
    const val LEAVE_OPEN = "agenda-leave-open"
    const val RESPONSE = "agenda-response-" // followed by "going" or "interested"
    const val RESPONSE_NOTE = "agenda-response-note"
    const val CANCEL_EVENT = "agenda-cancel-event"
    const val CANCEL_DIALOG = "agenda-cancel-dialog"
    const val CANCEL_KEEP = "agenda-cancel-keep"
    const val CANCEL_CONFIRM = "agenda-cancel-confirm"
    const val CANCEL_MESSAGE = "agenda-cancel-message"

    const val MINE = "agenda-mine"
    const val MINE_ITEM = "agenda-mine-" // followed by the identifier
    const val OWN_STATE = "agenda-own-state-" // followed by the state in lowercase
    const val FORGET = "agenda-forget"
    const val CREATE_ACCOUNT = "agenda-create-account"
}

/**
 * The "Carte" entry of the bottom bar: "Carte et agenda" (M1, AG-01). For now the agenda of the
 * mobilizations, with an honest box saying that the map view comes next (the map library and
 * self-hosted tiles are a later step). Three tabs: "À venir", "Passés" and, with an account, "Mes
 * propositions"; the list is grouped by day (Aujourd’hui, Demain, then the dates), narrowed by
 * filters computed on the device, and every mobilization opens its detail: description, place,
 * organisers, link (after the "Vous quittez PPF" warning), export to a calendar, and the private
 * answers "J'y serai" / "Intéressé" that never leave the device.
 *
 * Two panes on wide windows (list and detail), one pane with a back button otherwise.
 *
 * @param agenda the state of the agenda; the shell may hoist it to keep it for the whole run
 * @param organisationName the name of an organisation from its key, given by the directory of
 *   organisations; null keeps the short form of the key
 * @param exporter hands the calendar file to the platform (share sheet, save dialog)
 * @param onOpenLink opens a web link once the user confirmed; the platform's browser by default
 */
@Composable
fun AgendaScreen(
    dependencies: AppDependencies,
    session: AccountSession,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onNavigate: (Space) -> Unit = {},
    agenda: AgendaLibrary = rememberAgendaLibrary(dependencies),
    organisationName: (String) -> String? = { null },
    exporter: CalendarExporter = rememberPlatformCalendarExporter(),
    onOpenLink: ((String) -> Unit)? = null,
) {
    val account = session.state as? AccountUiState.Ready
    val accountKey = account?.publicKey?.hex
    LaunchedEffect(agenda, accountKey) { agenda.setAccount(accountKey) }
    // Where the user's proposals stand is asked again each time the agenda is shown.
    LaunchedEffect(agenda) { agenda.refreshReviews() }
    val names = remember(agenda, organisationName) { AgendaNames(organisationName) { agenda.isEditorial(it) } }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val composer = agenda.composer

    // The caller's modifier may carry its own test tag (the first one wins): the screen's goes on an inner box.
    Box(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        Box(Modifier.fillMaxSize().testTag(AgendaTags.SCREEN)) {
            if (composer.open) {
                ProposeMobilizationScreen(
                    agenda = agenda,
                    signer = session.signer,
                    windowClass = windowClass,
                    names = names,
                    onCreateAccount = { onNavigate(Space.MON_ESPACE) },
                    modifier = Modifier.fillMaxSize(),
                )
                return@Box
            }
            var panel by remember { mutableStateOf(false) }
            val list: @Composable (Modifier) -> Unit = { listModifier ->
                AgendaList(
                    agenda = agenda,
                    names = names,
                    compact = compact,
                    onOpenFilters = { panel = true },
                    onPropose = { composer.open = true },
                    onCreateAccount = { onNavigate(Space.MON_ESPACE) },
                    modifier = listModifier,
                )
            }
            val selected = agenda.selected
            val detail: @Composable (Mobilization, (() -> Unit)?, Modifier) -> Unit = { mobilization, onBack, detailModifier ->
                MobilizationDetail(
                    mobilization = mobilization,
                    agenda = agenda,
                    names = names,
                    compact = compact,
                    exporter = exporter,
                    onOpenLink = onOpenLink,
                    onBack = onBack,
                    modifier = detailModifier,
                    signer = session.signer,
                )
            }
            when {
                windowClass.width == WindowWidthClass.EXPANDED -> Row(Modifier.fillMaxSize()) {
                    list(Modifier.width(420.dp).fillMaxHeight())
                    VerticalDivider(color = PpfTheme.palette.border)
                    if (selected != null) {
                        detail(selected, null, Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp)) {
                            Column(Modifier.widthIn(max = 720.dp)) {
                                PageHeader(stringResource(Res.string.agenda_title), stringResource(Res.string.agenda_intro))
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(Res.string.agenda_select_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PpfTheme.palette.inkSubtle,
                                    modifier = Modifier.testTag(AgendaTags.SELECT_HINT),
                                )
                            }
                        }
                    }
                }
                selected != null -> detail(selected, agenda::close, Modifier.fillMaxSize())
                else -> list(Modifier.fillMaxSize())
            }
            if (panel) {
                AgendaFilterPanel(
                    agenda = agenda,
                    names = names,
                    compact = compact,
                    onDismiss = { panel = false },
                    onApply = {
                        agenda.filters = it
                        panel = false
                    },
                )
            }
        }
    }
}

/** How organisers and authors are named: the directory of organisations, the editorial team, or the short key. */
internal class AgendaNames(
    private val organisationName: (String) -> String?,
    private val isEditorial: (String) -> Boolean,
) {
    /** An organisation named as organiser. */
    fun organisation(key: String): String = organisationName(key)?.takeIf { it.isNotBlank() } ?: shortNpub(key)

    /** The author of a publication; [editorialTeam] is the label of the editorial team. */
    fun author(key: String, editorialTeam: String): String =
        organisationName(key)?.takeIf { it.isNotBlank() } ?: if (isEditorial(key)) editorialTeam else shortNpub(key)
}

/** The list pane: the header with the tabs, then the tab's page. */
@Composable
private fun AgendaList(
    agenda: AgendaLibrary,
    names: AgendaNames,
    compact: Boolean,
    onOpenFilters: () -> Unit,
    onPropose: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier,
) {
    val palette = PpfTheme.palette
    val tabs = listOfNotNull(AgendaTab.UPCOMING, AgendaTab.PAST, AgendaTab.MINE.takeIf { agenda.hasAccount })
    val horizontal = if (compact) 16.dp else 24.dp
    Column(modifier.testTag(AgendaTags.LIST)) {
        Surface(color = palette.surface0, contentColor = palette.ink) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = horizontal, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.agenda_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() }.testTag(AgendaTags.TITLE),
                    )
                    if (agenda.status != LibraryStatus.Offline && agenda.status != LibraryStatus.Unavailable) {
                        PpfIconButton(Lucide.RefreshCw, stringResource(Res.string.agenda_refresh), agenda::refresh, Modifier.testTag(AgendaTags.REFRESH))
                    }
                }
                PpfTabs(
                    labels = tabs.map { stringResource(tabLabel(it)) },
                    selected = tabs.indexOf(agenda.tab).coerceAtLeast(0),
                    onSelect = { agenda.tab = tabs[it] },
                    modifier = Modifier.padding(horizontal = horizontal),
                    tagFor = { AgendaTags.TAB + tabs[it].name.lowercase() },
                )
                HorizontalDivider(color = palette.border)
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = horizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (agenda.tab == AgendaTab.MINE) {
                    MinePage(agenda, onPropose, onCreateAccount)
                } else {
                    ListPage(agenda, names, onOpenFilters, onPropose)
                }
            }
        }
    }
}

private fun tabLabel(tab: AgendaTab) = when (tab) {
    AgendaTab.UPCOMING -> Res.string.agenda_tab_upcoming
    AgendaTab.PAST -> Res.string.agenda_tab_past
    AgendaTab.MINE -> Res.string.agenda_tab_mine
}

/** "À venir" or "Passés": tool bar, number, active filters, the map box, then the days. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListPage(agenda: AgendaLibrary, names: AgendaNames, onOpenFilters: () -> Unit, onPropose: () -> Unit) {
    val palette = PpfTheme.palette
    val filters = agenda.filters
    val now = agenda.now
    val shown = remember(agenda.mobilizations, agenda.query, now) { agenda.shown() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val count = filters.constraintCount
        PpfToolbarButton(
            icon = Lucide.SlidersHorizontal,
            text = stringResource(Res.string.agenda_filters),
            onClick = onOpenFilters,
            count = count,
            contentDescription = if (count > 0) pluralStringResource(Res.plurals.agenda_filters_active, count, count) else null,
            modifier = Modifier.testTag(AgendaTags.FILTERS),
        )
        PpfPrimaryButton(onClick = onPropose, text = stringResource(Res.string.agenda_propose), icon = Lucide.Plus, modifier = Modifier.padding(vertical = 2.dp).testTag(AgendaTags.PROPOSE))
    }
    Text(pluralStringResource(Res.plurals.agenda_count, shown.size, shown.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(AgendaTags.COUNT))
    if (filters.constraintCount > 0) ActiveFilters(agenda, names)
    if (agenda.tab == AgendaTab.UPCOMING) {
        InfoBox(Lucide.Map, Modifier.testTag(AgendaTags.MAP_SOON)) {
            Text(stringResource(Res.string.agenda_map_soon_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.agenda_map_soon_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
    statusText(agenda.status)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(AgendaTags.STATUS_TEXT))
    }
    if (shown.isEmpty()) {
        Column(Modifier.fillMaxWidth().testTag(AgendaTags.EMPTY), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                agenda.status == LibraryStatus.Loading && agenda.mobilizations.isEmpty() -> Unit
                filters.constraintCount > 0 -> {
                    Text(stringResource(Res.string.agenda_empty_filters), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                    PpfSecondaryButton(onClick = { agenda.filters = filters.cleared() }, text = stringResource(Res.string.agenda_clear_filters), modifier = Modifier.testTag(AgendaTags.EMPTY_CLEAR))
                }
                agenda.tab == AgendaTab.PAST -> Text(stringResource(Res.string.agenda_empty_past), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                else -> Text(stringResource(Res.string.agenda_empty_upcoming), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
        }
        return
    }
    val groups = remember(shown, now, agenda.tab) {
        shown.groupBy { m -> if (agenda.tab == AgendaTab.UPCOMING && m.start < now) null else ParisTime.epochDay(m.start) }.toList()
    }
    groups.forEach { (day, mobilizations) ->
        Text(
            text = if (day == null) stringResource(Res.string.agenda_ongoing) else dayHeader(day, now),
            style = MaterialTheme.typography.titleMedium,
            color = palette.brand,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() }.testTag(AgendaTags.DAY + (day?.toString() ?: "ongoing")),
        )
        mobilizations.forEach { m ->
            MobilizationCard(
                mobilization = m,
                names = names,
                selected = agenda.selected?.address == m.address,
                response = agenda.responseTo(m),
                onOpen = { agenda.open(m) },
            )
        }
    }
}

@Composable
private fun statusText(status: LibraryStatus): String? = when (status) {
    LibraryStatus.Offline -> stringResource(Res.string.content_status_offline)
    LibraryStatus.Unreachable -> stringResource(Res.string.content_status_unreachable)
    else -> null
}

/** The filters that apply, one removable chip each, and "Tout effacer". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(agenda: AgendaLibrary, names: AgendaNames) {
    val filters = agenda.filters
    val territories = remember(agenda.mobilizations) { territoryLabels(agenda.mobilizations) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
        if (filters.period != AgendaPeriod.Anytime) {
            Chip(periodLabel(filters.period), "period") { agenda.filters = filters.copy(period = AgendaPeriod.Anytime) }
        }
        filters.types.sortedBy { it.ordinal }.forEach { type ->
            Chip(typeLabel(type), "type:${type.tagValue}") { agenda.filters = filters.copy(types = filters.types - type) }
        }
        filters.territories.forEach { key ->
            Chip(territories[key] ?: key, "territory:$key") { agenda.filters = filters.copy(territories = filters.territories - key) }
        }
        filters.organizers.forEach { key ->
            Chip(names.organisation(key), "organizer:$key") { agenda.filters = filters.copy(organizers = filters.organizers - key) }
        }
        if (filters.text.isNotBlank()) {
            Chip(stringResource(Res.string.agenda_chip_text, filters.text.trim()), "text") { agenda.filters = filters.copy(text = "") }
        }
        PpfTextButton(onClick = { agenda.filters = filters.cleared() }, text = stringResource(Res.string.agenda_clear_all), modifier = Modifier.testTag(AgendaTags.CLEAR_ALL))
    }
}

@Composable
private fun Chip(label: String, key: String, onRemove: () -> Unit) {
    PpfRemovableChip(
        label = label,
        onRemove = onRemove,
        removeDescription = stringResource(Res.string.agenda_chip_remove, label),
        modifier = Modifier.testTag(AgendaTags.CHIP + key),
    )
}

/** The label of a period, for its chip. */
@Composable
internal fun periodLabel(period: AgendaPeriod): String = when (period) {
    AgendaPeriod.Anytime -> ""
    AgendaPeriod.Today -> stringResource(Res.string.agenda_period_today)
    AgendaPeriod.ThisWeek -> stringResource(Res.string.agenda_period_week)
    AgendaPeriod.ThisMonth -> stringResource(Res.string.agenda_period_month)
    is AgendaPeriod.Range -> {
        fun text(date: org.politisationpopulairefrancaise.core.time.CivilDate) =
            AgendaFormat.numeric(ParisTime.moment(ParisTime.startOfDay(ParisTime.epochDayOf(date)) + 12 * 3_600))
        val from = period.from
        val to = period.to
        when {
            from != null && to != null -> stringResource(Res.string.agenda_range_both, text(from), text(to))
            from != null -> stringResource(Res.string.agenda_range_since, text(from))
            to != null -> stringResource(Res.string.agenda_range_until, text(to))
            else -> ""
        }
    }
}

/** The territories of the agenda by folded key, with the spelling of the first mobilization that uses it. */
internal fun territoryLabels(mobilizations: List<Mobilization>): Map<String, String> {
    val labels = LinkedHashMap<String, String>()
    mobilizations.sortedBy { AgendaQuery.territoryKey(it.territory) }.forEach { m ->
        val key = AgendaQuery.territoryKey(m.territory)
        if (key !in labels) labels[key] = m.territory
    }
    return labels
}

/**
 * The card of a mobilization: the icon and label of its type, its title, its hours, its place
 * and territory, its organisers, its status (cancelled or postponed, very visible) and the
 * user's own answer (which stays on the device).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MobilizationCard(
    mobilization: Mobilization,
    names: AgendaNames,
    selected: Boolean,
    response: org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = modifier.fillMaxWidth().testTag(AgendaTags.CARD + mobilization.identifier)) {
        Row(verticalAlignment = Alignment.Top) {
            TypeMark(mobilization)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(typeLabel(mobilization.type), style = MaterialTheme.typography.labelMedium, color = palette.inkMuted, modifier = Modifier.weight(1f, fill = false))
                    if (mobilization.example) ExampleBadge()
                }
                Spacer(Modifier.height(4.dp))
                Text(mobilization.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                MetaLine(Lucide.Clock, whenText(mobilization, full = false))
                MetaLine(Lucide.MapPin, "${mobilization.location} · ${mobilization.territory}")
                if (mobilization.organizers.isNotEmpty()) {
                    MetaLine(Lucide.Users, stringResource(Res.string.agenda_organized_by, mobilization.organizers.joinToString(", ") { names.organisation(it) }))
                }
                if (mobilization.status != org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus.PREVU || response != null) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusPill(mobilization.status)
                        if (response != null) ResponsePill(response)
                    }
                }
            }
        }
    }
}

/** The icon of the type in a tinted square: a hint, the label next to it says the type. */
@Composable
internal fun TypeMark(mobilization: Mobilization, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val cancelled = mobilization.cancelled
    Box(
        modifier = modifier.size(40.dp).background(if (cancelled) palette.surface1 else palette.brandTint, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(typeIcon(mobilization.type), contentDescription = null, tint = if (cancelled) palette.inkSubtle else palette.brand, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun MetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    }
}

/** "Mes propositions": the user's own events and where they stand. */
@Composable
private fun MinePage(agenda: AgendaLibrary, onPropose: () -> Unit, onCreateAccount: () -> Unit) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(AgendaTags.MINE), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!agenda.hasAccount) {
            InfoBox(Lucide.KeyRound) {
                Text(stringResource(Res.string.agenda_need_account_mine), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                PpfPrimaryButton(onCreateAccount, stringResource(Res.string.agenda_create_account), Modifier.testTag(AgendaTags.CREATE_ACCOUNT))
            }
            return@Column
        }
        Text(stringResource(Res.string.agenda_mine_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        PpfPrimaryButton(onClick = onPropose, text = stringResource(Res.string.agenda_propose), icon = Lucide.Plus, modifier = Modifier.width(IntrinsicSize.Max).testTag(AgendaTags.PROPOSE))
        if (agenda.mine.isEmpty()) {
            Text(stringResource(Res.string.agenda_mine_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(AgendaTags.EMPTY))
        }
        agenda.mine.forEach { own -> OwnProposalCard(own, onOpen = { agenda.open(own.mobilization) }, onForget = { agenda.forget(own) }) }
    }
}

@Composable
private fun OwnProposalCard(own: OwnProposal, onOpen: () -> Unit, onForget: () -> Unit) {
    val palette = PpfTheme.palette
    val m = own.mobilization
    PpfCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag(AgendaTags.MINE_ITEM + m.identifier)) {
        Row(verticalAlignment = Alignment.Top) {
            TypeMark(m)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                OwnStatePill(own.state)
                Spacer(Modifier.height(6.dp))
                Text(m.displayTitle, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                MetaLine(Lucide.Clock, whenText(m, full = true))
                MetaLine(Lucide.MapPin, "${m.location} · ${m.territory}")
                when (own.state) {
                    OwnProposalState.WAITING -> Text(stringResource(Res.string.agenda_state_waiting_text), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    OwnProposalState.NOT_PUBLISHED -> {
                        own.reason?.let { Text(stringResource(Res.string.agenda_state_reason, it), style = MaterialTheme.typography.bodyMedium) }
                        PpfTextButton(onClick = onForget, text = stringResource(Res.string.agenda_forget), color = palette.inkMuted, modifier = Modifier.testTag(AgendaTags.FORGET))
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** The editorial team, as author of the examples bundled with the application. */
@Composable
internal fun editorialTeamLabel(): String = stringResource(Res.string.agenda_editorial_team)
