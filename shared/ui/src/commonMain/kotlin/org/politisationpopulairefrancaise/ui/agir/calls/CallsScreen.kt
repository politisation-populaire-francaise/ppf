package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallDue
import org.politisationpopulairefrancaise.core.agir.calls.CallQuery
import org.politisationpopulairefrancaise.core.agir.calls.CallSort
import org.politisationpopulairefrancaise.core.agir.calls.CallStatusFilter
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.poster.PosterContent
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.agir.poster.PosterExporter
import org.politisationpopulairefrancaise.ui.agir.poster.PosterScreen
import org.politisationpopulairefrancaise.ui.agir.poster.rememberPlatformPosterExporter
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfRemovableChip
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTabs
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.PpfToolbarButton
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.content.OwnDemandState
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags, also useful for accessibility tooling. */
object CallTags {
    const val SCREEN = "calls-screen"
    const val BACK = "calls-back"
    const val WALL = "calls-wall"
    const val TAB = "calls-tab-" // followed by the tab name in lowercase
    const val FILTERS = "calls-filters"
    const val FILTER_PANEL = "calls-filter-panel"
    const val FILTER_TYPE = "calls-filter-type-" // followed by the type tag value
    const val FILTER_STATUS = "calls-filter-status-" // followed by open, closed, all
    const val FILTER_DUE = "calls-filter-due-" // followed by any, this_week, this_month, none
    const val FILTER_TERRITORY = "calls-filter-territory-" // followed by the folded territory
    const val FILTER_WORDS = "calls-filter-words"
    const val CHIP = "calls-chip-" // followed by type:<tag>, territory:<key>, due, status, words
    const val CLEAR_ALL = "calls-clear-all"
    const val SORT = "calls-sort"
    const val SORT_OPTION = "calls-sort-" // followed by newest, deadline
    const val SORT_NOTE = "calls-sort-note"
    const val LAUNCH = "calls-launch"
    const val COUNT = "calls-count"
    const val CARD = "call-" // followed by the slug
    const val EMPTY = "calls-empty"
    const val EMPTY_CLEAR = "calls-empty-clear"
    const val CLEAR_ANSWERS = "calls-clear-answers"
    const val OWN_STATE = "call-own-state-" // followed by the state in lowercase
    const val ANSWERED_BADGE = "call-answered"
    const val DETAIL = "call-detail"
    const val DETAIL_BACK = "call-detail-back"
    const val ANSWER = "call-answer"
    const val ANSWER_DONE = "call-answer-done"
    const val ANSWER_WITHDRAW = "call-answer-withdraw"
    const val ANSWER_NOTE = "call-answer-note"
    const val GOAL = "call-goal"
    const val COUNTDOWN = "call-countdown"
    const val REPORT = "call-report"
    const val LINK = "call-link-" // followed by the index of the link
    const val SOURCE_LINK = "call-source-link"
    const val SHARE_TEXT = "call-share-text"
    const val SHARE_COPY = "call-share-copy"
    const val MAKE_EVENT = "call-make-event"
    const val MAKE_DEMAND = "call-make-demand"
    const val DISCUSS = "call-discuss"
    const val CLOSE = "call-close"
    const val CLOSE_DIALOG = "call-close-dialog"
    const val CLOSE_REPORT = "call-close-report"
    const val CLOSE_KEEP = "call-close-keep"
    const val CLOSE_CONFIRM = "call-close-confirm"
    const val CLOSE_OUTCOME = "call-close-outcome"
    const val LEAVE_DIALOG = "call-leave-dialog"
    const val LEAVE_STAY = "call-leave-stay"
    const val LEAVE_OPEN = "call-leave-open"
    const val KEEP = "calls-keep"
    const val CONFIRM = "calls-confirm"
}

/** A link waiting for the "Vous quittez PPF" warning. */
internal data class LeavingLink(val url: String, val fundraising: Boolean)

/**
 * The calls of the Agir space (AP-01 to AP-10), as one standalone screen: the wall of calls with
 * its tabs (all calls, the user's own, the ones the user answered), filters and sort (AP-05), the
 * detail of a call with the answer kept on the device (AP-03), its goal and countdown (AP-04), its
 * report once closed (AP-09) and what makes it grow (AP-07), and the form to launch one (AP-01,
 * AP-10). Two panes on wide windows (list and detail), one pane with a back button otherwise.
 * Every external link goes through the "Vous quittez PPF" warning, the safe choice first.
 *
 * @param onBack shows a back arrow above the wall when not null (to the home of the Agir space)
 * @param isVerifiedOrganisation whether a key belongs to a verified organisation (contract, section
 *   4): only those may launch a fundraising; false for everyone until the directory is plugged in
 * @param onCreateEvent "Transformer en événement" (AP-07), shown only when given
 * @param onCreateDemand "En faire une revendication" (AP-07), shown only when given
 * @param onCreateAccount the button of the form for people without an account, shown only when given
 */
@Composable
fun CallsScreen(
    library: CallsLibrary,
    session: AccountSession,
    windowClass: WindowClass,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isVerifiedOrganisation: (pubkeyHex: String) -> Boolean = { false },
    onCreateEvent: ((Call) -> Unit)? = null,
    onCreateDemand: ((Call) -> Unit)? = null,
    onCreateAccount: (() -> Unit)? = null,
    posterExporter: PosterExporter = rememberPlatformPosterExporter(),
) {
    // The shell keeps the library for the whole run and starts it here, the first time the calls are shown.
    LaunchedEffect(library) { library.start() }
    // AG-61: the call whose poster is being made (memory only).
    var posterOf by remember { mutableStateOf<Call?>(null) }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val twoPanes = windowClass.width == WindowWidthClass.EXPANDED
    val account = session.state as? AccountUiState.Ready
    val composer = library.composer
    var leaving by remember { mutableStateOf<LeavingLink?>(null) }
    var closing by remember { mutableStateOf<Call?>(null) }
    var closeOutcome by remember { mutableStateOf<Pair<String, CallSendOutcome>?>(null) }
    var clearAnswers by remember { mutableStateOf(false) }

    // The caller's modifier may carry its own test tag (the first one wins): the screen's goes on an inner box.
    // The surface gives every text of the screen the ink of the theme (light and dark).
    Surface(modifier.fillMaxSize(), color = PpfTheme.palette.surface1, contentColor = PpfTheme.palette.ink) {
        Box(Modifier.fillMaxSize().testTag(CallTags.SCREEN)) {
            if (composer.open) {
                CallComposerScreen(
                    composer = composer,
                    calls = library.calls,
                    signer = session.signer,
                    canFundraise = account?.let { isVerifiedOrganisation(it.publicKey.hex) } == true,
                    windowClass = windowClass,
                    onClose = { composer.open = false },
                    onOpenCall = { call ->
                        composer.open = false
                        library.open(call)
                    },
                    onCreateAccount = onCreateAccount,
                    onSent = library::onCallSent,
                )
                return@Box
            }
            val poster = posterOf
            if (poster != null) {
                val content = remember(poster.event.id) { PosterContent.of(poster) }
                // The author as the detail names them ("Lancé par").
                PosterScreen(content, listOf(shortNpub(poster.author)), compact, posterExporter, onBack = { posterOf = null }, modifier = Modifier.fillMaxSize())
                return@Box
            }
            val detail: @Composable (Call, (() -> Unit)?) -> Unit = { call, back ->
                CallDetail(
                    call = call,
                    library = library,
                    accountKey = account?.publicKey?.hex,
                    compact = compact,
                    onBack = back,
                    verified = isVerifiedOrganisation(call.author),
                    onCreateEvent = onCreateEvent,
                    onCreateDemand = onCreateDemand,
                    onOpenLink = { url, fundraising -> leaving = LeavingLink(url, fundraising) },
                    onClose = { closing = it },
                    closeOutcome = closeOutcome?.takeIf { it.first == call.address.toString() }?.second,
                    modifier = Modifier.fillMaxSize(),
                    onMakePoster = { posterOf = it },
                )
            }
            val selected = library.selected
            when {
                twoPanes -> Row(Modifier.fillMaxSize()) {
                    Wall(library, account != null, compact = false, onBack, onClearAnswers = { clearAnswers = true }, Modifier.width(400.dp).fillMaxHeight())
                    VerticalDivider(color = PpfTheme.palette.border)
                    if (selected != null) {
                        detail(selected, null)
                    } else {
                        Column(Modifier.fillMaxSize().padding(24.dp)) {
                            PageHeader(stringResource(Res.string.appels_title), stringResource(Res.string.appels_detail_placeholder))
                        }
                    }
                }
                selected != null -> detail(selected, library::close)
                else -> Wall(library, account != null, compact, onBack, onClearAnswers = { clearAnswers = true }, Modifier.fillMaxSize())
            }
        }
    }

    leaving?.let { link ->
        LeaveDialog(link, onStay = { leaving = null }, onDone = { leaving = null })
    }
    closing?.let { call ->
        CloseDialog(
            call = call,
            signer = session.signer,
            library = library,
            onKeep = { closing = null },
            onClosed = { outcome ->
                closeOutcome = call.address.toString() to outcome
                closing = null
            },
        )
    }
    if (clearAnswers) {
        SafeDialog(
            title = stringResource(Res.string.appels_clear_answers_title),
            text = stringResource(Res.string.appels_clear_answers_text),
            keepLabel = stringResource(Res.string.appels_clear_answers_keep),
            confirmLabel = stringResource(Res.string.appels_clear_answers_confirm),
            onKeep = { clearAnswers = false },
            onConfirm = {
                library.clearResponses()
                clearAnswers = false
            },
        )
    }
}

/** The tabs, the tool bar, the filters and the cards. */
@Composable
private fun Wall(
    library: CallsLibrary,
    hasAccount: Boolean,
    compact: Boolean,
    onBack: (() -> Unit)?,
    onClearAnswers: () -> Unit,
    modifier: Modifier,
) {
    val palette = PpfTheme.palette
    val now = library.now()
    val tab = library.tab
    val query = library.query
    var filtersShown by remember { mutableStateOf(false) }
    val results: List<Call> = remember(library.calls, library.myCalls, library.responded, query, tab) {
        when (tab) {
            CallsTab.ALL -> query.apply(library.calls, now)
            CallsTab.MINE -> library.myCalls.map { it.call }
            CallsTab.ANSWERED -> (library.calls + library.myCalls.map { it.call })
                .distinctBy { it.address }
                .filter { it.address.toString() in library.responded }
                .let { CallQuery(status = CallStatusFilter.ALL).sorted(it, now) }
        }
    }
    val padding = if (compact) 16.dp else 24.dp

    Column(modifier.testTag(CallTags.WALL)) {
        Surface(color = palette.surface0, contentColor = palette.ink) {
            Column {
                Row(Modifier.padding(start = if (onBack != null) 8.dp else padding, end = padding, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.appels_back), onBack, Modifier.testTag(CallTags.BACK), tint = palette.ink)
                        Spacer(Modifier.width(4.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.appels_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text(stringResource(Res.string.appels_description), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                    }
                }
                val tabs = CallsTab.entries
                PpfTabs(
                    labels = tabs.map { stringResource(tabLabel(it)) },
                    selected = tabs.indexOf(tab),
                    onSelect = { library.tab = tabs[it] },
                    // On a phone the three tabs may be wider than the screen: they scroll.
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = padding),
                    tagFor = { CallTags.TAB + tabs[it].name.lowercase() },
                )
                HorizontalDivider(color = palette.border)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Toolbar(
                    showFilters = tab == CallsTab.ALL,
                    activeCount = query.constraintCount,
                    filtersShown = filtersShown,
                    sort = query.sort,
                    onFilters = { filtersShown = !filtersShown },
                    onSort = { library.query = query.copy(sort = it) },
                    onLaunch = { library.composer.open = true },
                )
                if (tab == CallsTab.ALL) {
                    if (filtersShown) FilterPanel(library, onHide = { filtersShown = false })
                    if (query.constraintCount > 0) ActiveFilters(library)
                }
                Text(
                    text = pluralStringResource(Res.plurals.appels_count, results.size, results.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag(CallTags.COUNT),
                )
                if (tab == CallsTab.ANSWERED) {
                    InfoBox(Lucide.Lock) {
                        Text(stringResource(Res.string.appels_answered_intro), style = MaterialTheme.typography.bodyMedium)
                        if (library.responded.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            PpfTextButton(onClearAnswers, stringResource(Res.string.appels_clear_answers), Modifier.testTag(CallTags.CLEAR_ANSWERS), color = palette.accentInk)
                        }
                    }
                }
                if (results.isEmpty()) EmptyState(library, tab, hasAccount)
                results.forEach { call ->
                    CallCard(
                        call = call,
                        now = now,
                        onOpen = { library.open(call) },
                        selected = library.selected?.address == call.address,
                        own = library.ownCall(call),
                        responded = library.isResponded(call),
                    )
                }
            }
        }
    }
}

private fun tabLabel(tab: CallsTab) = when (tab) {
    CallsTab.ALL -> Res.string.appels_tab_all
    CallsTab.MINE -> Res.string.appels_tab_mine
    CallsTab.ANSWERED -> Res.string.appels_tab_answered
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(
    showFilters: Boolean,
    activeCount: Int,
    filtersShown: Boolean,
    sort: CallSort,
    onFilters: () -> Unit,
    onSort: (CallSort) -> Unit,
    onLaunch: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (showFilters) {
            PpfToolbarButton(
                icon = Lucide.SlidersHorizontal,
                text = stringResource(if (filtersShown) Res.string.appels_filters_hide else Res.string.appels_filters),
                onClick = onFilters,
                count = activeCount,
                contentDescription = if (activeCount > 0) pluralStringResource(Res.plurals.appels_filters_active, activeCount, activeCount) else null,
                modifier = Modifier.testTag(CallTags.FILTERS),
            )
            Box {
                PpfToolbarButton(
                    icon = Lucide.ArrowUpDown,
                    text = stringResource(Res.string.appels_sort_button, stringResource(sortLabel(sort))),
                    onClick = { sortMenu = true },
                    modifier = Modifier.testTag(CallTags.SORT),
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = PpfTheme.palette.surface2, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.width(300.dp).padding(horizontal = 8.dp)) {
                        Column(Modifier.selectableGroup()) {
                            CallSort.entries.forEach { option ->
                                PpfRadioRow(
                                    label = stringResource(sortLabel(option)),
                                    selected = option == sort,
                                    onClick = {
                                        onSort(option)
                                        sortMenu = false
                                    },
                                    modifier = Modifier.testTag(CallTags.SORT_OPTION + option.name.lowercase()),
                                )
                            }
                        }
                        Text(
                            stringResource(Res.string.appels_sort_no_popularity),
                            style = MaterialTheme.typography.bodySmall,
                            color = PpfTheme.palette.inkSubtle,
                            modifier = Modifier.padding(8.dp).testTag(CallTags.SORT_NOTE),
                        )
                    }
                }
            }
        }
        PpfPrimaryButton(onClick = onLaunch, text = stringResource(Res.string.appels_launch), icon = Lucide.Plus, modifier = Modifier.padding(vertical = 2.dp).testTag(CallTags.LAUNCH))
    }
}

private fun sortLabel(sort: CallSort) = when (sort) {
    CallSort.NEWEST -> Res.string.appels_sort_newest
    CallSort.DEADLINE -> Res.string.appels_sort_deadline
}

private fun statusLabel(status: CallStatusFilter) = when (status) {
    CallStatusFilter.OPEN -> Res.string.appels_status_filter_open
    CallStatusFilter.CLOSED -> Res.string.appels_status_filter_closed
    CallStatusFilter.ALL -> Res.string.appels_status_filter_all
}

private fun dueFilterLabel(due: CallDue) = when (due) {
    CallDue.ANY -> Res.string.appels_due_any
    CallDue.THIS_WEEK -> Res.string.appels_due_week
    CallDue.THIS_MONTH -> Res.string.appels_due_month
    CallDue.NONE -> Res.string.appels_due_none
}

/** The filters, shown in the page under the tool bar (no gesture, no sheet to dismiss). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(library: CallsLibrary, onHide: () -> Unit) {
    val query = library.query
    val territories = remember(library.calls) { CallQuery.territoriesOf(library.calls) }
    PpfCard(Modifier.fillMaxWidth().testTag(CallTags.FILTER_PANEL)) {
        FilterHeading(stringResource(Res.string.appels_filter_status))
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallStatusFilter.entries.forEach { status ->
                ChoiceChip(stringResource(statusLabel(status)), status == query.status, { library.query = query.copy(status = status) }, Modifier.testTag(CallTags.FILTER_STATUS + status.name.lowercase()))
            }
        }
        FilterHeading(stringResource(Res.string.appels_filter_types))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallType.entries.forEach { type ->
                PpfSelectableChip(
                    label = stringResource(typeLabelResource(type)),
                    selected = type in query.types,
                    onClick = { library.query = query.copy(types = if (type in query.types) query.types - type else query.types + type) },
                    modifier = Modifier.testTag(CallTags.FILTER_TYPE + type.tagValue),
                )
            }
        }
        FilterHeading(stringResource(Res.string.appels_filter_due))
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallDue.entries.forEach { due ->
                ChoiceChip(stringResource(dueFilterLabel(due)), due == query.due, { library.query = query.copy(due = due) }, Modifier.testTag(CallTags.FILTER_DUE + due.name.lowercase()))
            }
        }
        if (territories.isNotEmpty()) {
            FilterHeading(stringResource(Res.string.appels_filter_territory))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                territories.forEach { (key, label) ->
                    PpfSelectableChip(
                        label = label,
                        selected = key in query.territories,
                        onClick = { library.query = query.copy(territories = if (key in query.territories) query.territories - key else query.territories + key) },
                        modifier = Modifier.testTag(CallTags.FILTER_TERRITORY + key),
                    )
                }
            }
        }
        FilterHeading(stringResource(Res.string.appels_filter_words))
        PpfTextField(
            value = query.text,
            onValueChange = { library.query = library.query.copy(text = it) },
            placeholder = stringResource(Res.string.appels_filter_words_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(CallTags.FILTER_WORDS),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSecondaryButton(onHide, stringResource(Res.string.appels_filters_hide))
            if (query.constraintCount > 0) PpfTextButton({ library.query = query.cleared() }, stringResource(Res.string.appels_clear_all))
        }
    }
}

@Composable
private fun FilterHeading(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp).semantics { heading() })
}

/** One choice among several, shaped like a chip, announced as a radio button. */
@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val shape = MaterialTheme.shapes.extraSmall
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) palette.brandTint else palette.surface2, shape)
            .border(1.dp, if (selected) palette.brand else palette.borderStrong, shape)
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(14.dp).border(2.dp, if (selected) palette.brand else palette.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(6.dp).background(if (selected) palette.brand else Color.Transparent, CircleShape))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) palette.brand else palette.ink, maxLines = 1)
    }
}

/** The filters that apply, one removable chip each, and "Tout effacer". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(library: CallsLibrary) {
    val query = library.query
    val territories = remember(library.calls) { CallQuery.territoriesOf(library.calls) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
        query.types.sortedBy { it.ordinal }.forEach { type ->
            Chip(stringResource(typeLabelResource(type)), "type:${type.tagValue}") { library.query = query.copy(types = query.types - type) }
        }
        query.territories.forEach { key ->
            Chip(territories[key] ?: key, "territory:$key") { library.query = query.copy(territories = query.territories - key) }
        }
        if (query.due != CallDue.ANY) Chip(stringResource(dueFilterLabel(query.due)), "due") { library.query = query.copy(due = CallDue.ANY) }
        if (query.status != CallStatusFilter.OPEN) Chip(stringResource(statusLabel(query.status)), "status") { library.query = query.copy(status = CallStatusFilter.OPEN) }
        if (query.text.isNotBlank()) Chip(stringResource(Res.string.appels_chip_words, query.text.trim()), "words") { library.query = query.copy(text = "") }
        PpfTextButton(onClick = { library.query = query.cleared() }, text = stringResource(Res.string.appels_clear_all), modifier = Modifier.testTag(CallTags.CLEAR_ALL))
    }
}

@Composable
private fun Chip(label: String, key: String, onRemove: () -> Unit) {
    PpfRemovableChip(label = label, onRemove = onRemove, removeDescription = stringResource(Res.string.appels_chip_remove, label), modifier = Modifier.testTag(CallTags.CHIP + key))
}

@Composable
private fun EmptyState(library: CallsLibrary, tab: CallsTab, hasAccount: Boolean) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(CallTags.EMPTY), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (tab) {
            CallsTab.ALL -> when {
                library.calls.isEmpty() -> if (library.status != LibraryStatus.Loading) {
                    Text(stringResource(Res.string.appels_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                else -> {
                    Text(stringResource(Res.string.appels_empty_filters), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                    if (library.query.constraintCount > 0) {
                        PpfSecondaryButton({ library.query = library.query.cleared() }, stringResource(Res.string.appels_clear_filters), Modifier.testTag(CallTags.EMPTY_CLEAR))
                    }
                }
            }
            CallsTab.MINE -> Text(
                stringResource(if (hasAccount) Res.string.appels_empty_mine else Res.string.appels_empty_mine_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
            )
            CallsTab.ANSWERED -> Text(stringResource(Res.string.appels_empty_answered), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
    }
}

/** The card of a call: type, state, title, summary, territory and date, and whether the user answered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CallCard(
    call: Call,
    now: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    own: OwnCall? = null,
    responded: Boolean = false,
) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = modifier.fillMaxWidth().testTag(CallTags.CARD + call.slug)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TypeBadge(call.type)
            if (!call.isOpen) PpfBadge(stringResource(Res.string.appels_status_closed), BadgeStyle.NEUTRAL)
            if (own != null && own.state != OwnDemandState.PUBLISHED) OwnStateBadge(own.state)
            if (call.isExample) ExampleBadge()
        }
        Spacer(Modifier.height(10.dp))
        Text(call.displayTitle, style = MaterialTheme.typography.titleSmall)
        call.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetaItem(Lucide.MapPin, call.territory)
            dueLabel(call.deadline, call.start)?.let { label ->
                MetaItem(Lucide.Calendar, label)
            }
            if (call.isOpen) call.dueAt?.let { MetaItem(Lucide.Clock, countdown(it, now)) }
        }
        if (responded) {
            Spacer(Modifier.height(8.dp))
            PpfBadge(stringResource(Res.string.appels_answered_badge), BadgeStyle.SUCCESS, Modifier.testTag(CallTags.ANSWERED_BADGE))
        }
    }
}

@Composable
internal fun MetaItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
    }
}

/** The type of a call, with its icon. */
@Composable
internal fun TypeBadge(type: CallType, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraSmall, color = palette.brandTint, contentColor = palette.brand) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(typeIcon(type), contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(typeLabelResource(type)), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Where one of the user's own calls stands, while it is not on the wall. */
@Composable
internal fun OwnStateBadge(state: OwnDemandState, modifier: Modifier = Modifier) {
    val (label, style) = when (state) {
        OwnDemandState.WAITING -> Res.string.appels_own_waiting to BadgeStyle.NEUTRAL
        OwnDemandState.IN_REVIEW -> Res.string.appels_own_review to BadgeStyle.EXAMPLE
        OwnDemandState.PUBLISHED -> Res.string.appels_own_published to BadgeStyle.SUCCESS
        OwnDemandState.NOT_PUBLISHED -> Res.string.appels_own_rejected to BadgeStyle.NEUTRAL
    }
    PpfBadge(stringResource(label), style, modifier.testTag(CallTags.OWN_STATE + state.name.lowercase()))
}
