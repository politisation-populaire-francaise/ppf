package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.ReportContentButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.revendications.AuthorScope
import org.politisationpopulairefrancaise.core.revendications.RevendicationContext
import org.politisationpopulairefrancaise.core.revendications.RevendicationQuery
import org.politisationpopulairefrancaise.core.revendications.RevendicationSort
import org.politisationpopulairefrancaise.core.revendications.RevendicationThemes
import org.politisationpopulairefrancaise.core.revendications.RevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
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
import org.politisationpopulairefrancaise.ui.components.PpfTabs
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfToolbarButton
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.FeedPost
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.content_updated
import org.politisationpopulairefrancaise.ui.resources.desc_revendications
import org.politisationpopulairefrancaise.ui.resources.revendication_argument
import org.politisationpopulairefrancaise.ui.resources.revendication_back
import org.politisationpopulairefrancaise.ui.resources.revendications_intro
import org.politisationpopulairefrancaise.ui.resources.rv_how_it_works
import org.politisationpopulairefrancaise.ui.resources.rv_movement_badge
import org.politisationpopulairefrancaise.ui.resources.rv_supported_by
import org.politisationpopulairefrancaise.ui.resources.rv_movement_carried_by
import org.politisationpopulairefrancaise.ui.resources.rv_movement_gathered
import org.politisationpopulairefrancaise.ui.resources.rv_propose_short
import org.politisationpopulairefrancaise.ui.resources.rv_how_it_works_closed
import org.politisationpopulairefrancaise.ui.resources.rv_how_it_works_open
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.stateDescription
import org.politisationpopulairefrancaise.ui.resources.revendications_none
import org.politisationpopulairefrancaise.ui.resources.rv_chip_remove
import org.politisationpopulairefrancaise.ui.resources.rv_chip_text
import org.politisationpopulairefrancaise.ui.resources.rv_clear_all
import org.politisationpopulairefrancaise.ui.resources.rv_clear_filters
import org.politisationpopulairefrancaise.ui.resources.rv_count
import org.politisationpopulairefrancaise.ui.resources.rv_customize
import org.politisationpopulairefrancaise.ui.resources.rv_empty_filters
import org.politisationpopulairefrancaise.ui.resources.rv_empty_followed
import org.politisationpopulairefrancaise.ui.resources.rv_empty_followed_none
import org.politisationpopulairefrancaise.ui.resources.rv_empty_mine
import org.politisationpopulairefrancaise.ui.resources.rv_empty_view
import org.politisationpopulairefrancaise.ui.resources.rv_filters
import org.politisationpopulairefrancaise.ui.resources.rv_filters_active
import org.politisationpopulairefrancaise.ui.resources.rv_propose
import org.politisationpopulairefrancaise.ui.resources.rv_sort_button
import org.politisationpopulairefrancaise.ui.resources.rv_sort_popular_note
import org.politisationpopulairefrancaise.ui.resources.rv_view_followed
import org.politisationpopulairefrancaise.ui.resources.rv_view_mine
import org.politisationpopulairefrancaise.ui.resources.space_revendications
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.core.decider.Petition
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.voir.hostOf
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl
import org.politisationpopulairefrancaise.ui.resources.revendication_petition_open
import org.politisationpopulairefrancaise.ui.urne.DemandVoteRow
import org.politisationpopulairefrancaise.ui.urne.DemandVoteTags
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.ui.content.OwnDemand
import org.politisationpopulairefrancaise.ui.content.OwnDemandState
import org.politisationpopulairefrancaise.ui.resources.proposal_reason
import org.politisationpopulairefrancaise.ui.resources.proposal_report_title
import org.politisationpopulairefrancaise.ui.resources.proposal_status_approved
import org.politisationpopulairefrancaise.ui.resources.proposal_status_pending
import org.politisationpopulairefrancaise.ui.resources.proposal_status_rejected
import org.politisationpopulairefrancaise.ui.resources.proposal_status_waiting
import org.politisationpopulairefrancaise.ui.revendications.propose.IssueList
import org.politisationpopulairefrancaise.ui.revendications.propose.ProposalComposer
import org.politisationpopulairefrancaise.ui.revendications.propose.ProposalScreen

object RevendicationTags {
    /** Followed by the state in lowercase ("in_review", "waiting"...): the badge of one of the user's own demands. */
    const val OWN_STATE = "revendication-own-state-"
    const val OWN_REVIEW = "revendication-own-review"
    const val CARD = "revendication-" // followed by the slug
    const val LIST = "revendication-list"
    const val DETAIL = "revendication-detail"
    const val BACK = "revendication-back"
    const val VOTE_FOR = DemandVoteTags.FOR
    /** The official code of a demand's territory (D-090), beside its free label. */
    const val TERRITORY_CODE = "ppf-territory-code"
    /** The link of the official petition that carries a demand (D-115). */
    const val PETITION = "revendication-petition"
    const val TAB = "revendication-tab-" // followed by the view id
    const val PAGE = "revendication-page-" // followed by the view id
    const val FILTERS = "revendication-filters"
    const val SORT = "revendication-sort"
    const val SORT_OPTION = "revendication-sort-" // followed by the sort name in lowercase
    const val CUSTOMIZE = "revendication-customize"
    const val PROPOSE = "revendication-propose"
    const val COUNT = "revendication-count"
    /** « Du mouvement » and who carries it, on a demand gathered by the team (MVP 1). */
    const val MOVEMENT = "revendication-movement"
    /** « Également portée par », on any demand of the team that names public supporters (D-152). */
    const val SUPPORTERS = "revendication-supporters"
    /** The one-line "Comment ça marche ?" that unfolds the explanation of the wall (MVP 1, lighter header). */
    const val HOW_IT_WORKS = "revendication-how-it-works"
    const val HOW_IT_WORKS_TEXT = "revendication-how-it-works-text"
    const val CHIP = "revendication-chip-" // followed by "theme:<id>", "territory:<key>", "status:<tag>", "authors" or "text"
    const val CLEAR_ALL = "revendication-clear-all"
    const val EMPTY = "revendication-empty"
    const val EMPTY_CLEAR = "revendication-empty-clear"
}

/** What the screen has open over the wall. */
private sealed interface Overlay {
    data class Panel(val mode: PanelMode) : Overlay
    data object Manage : Overlay
}

/**
 * The demands wall (M11, DE-01): every demand is a card read at a glance, grouped in VIEWS shown
 * as tabs and swiped like the feed (owner's request of 2026-11-06), and narrowed by temporary
 * FILTERS (themes, territory, status, authors, words) and a sort. A view is a saved query: the
 * built-in ones (Toutes, Comptes suivis, Mes revendications, Nationales, one per theme) and the
 * user's own, chosen and ordered in "Personnaliser". Views stay on the device; filters live for
 * the session. Voting and debating open once the "one person, one vote" method is validated
 * (decision pending); until then the buttons are shown disabled and popularity is the status.
 *
 * Two panes on wide windows (list and detail), one pane with a back button otherwise.
 *
 * @param views the views and filters; the shell keeps one for the whole run
 * @param onPropose shows "Proposer une revendication" when not null
 * @param proposal the proposal form (DE-18); when given, "Proposer une revendication" opens it
 * @param onNavigate leaves for another space (the form sends to Mon espace to create an account)
 */
@Composable
fun RevendicationsScreen(
    library: ContentLibrary,
    session: AccountSession,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    database: PpfDatabase? = null,
    views: RevendicationViewsState = rememberRevendicationViewsState(database, session),
    onPropose: (() -> Unit)? = null,
    proposal: ProposalComposer? = null,
    onNavigate: (Space) -> Unit = {},
) {
    SystemBack(enabled = proposal?.open == true) { proposal?.open = false }
    SystemBack(enabled = library.selectedRevendication != null) { library.closeRevendication() }
    if (proposal != null && proposal.open) {
        val existing = remember(library.revendications, library.myRevendications) {
            (library.revendications + library.myRevendications.map { it.article }).distinctBy { it.address }.map(DemandText::of)
        }
        ProposalScreen(
            composer = proposal,
            existing = existing,
            signer = session.signer,
            windowClass = windowClass,
            onClose = { proposal.open = false },
            onOpenDemand = { address ->
                val all = library.revendications + library.myRevendications.map { it.article }
                all.firstOrNull { it.address.toString() == address }?.let(library::openRevendication)
                proposal.open = false
            },
            onCreateAccount = { onNavigate(Space.MON_ESPACE) },
            onSent = library::onDemandSent,
            modifier = modifier,
        )
        return
    }
    @Suppress("NAME_SHADOWING")
    val onPropose: (() -> Unit)? = onPropose ?: proposal?.let { form -> { form.open = true } }
    val twoPanes = windowClass.width == WindowWidthClass.EXPANDED
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val selected = library.selectedRevendication
    val account = session.state as? AccountUiState.Ready
    val context = RevendicationContext(followed = library.followed, myKey = account?.publicKey?.hex)
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    val wall: @Composable (Modifier) -> Unit = { listModifier ->
        Wall(
            library = library,
            views = views,
            context = context,
            compact = compact,
            withTitle = windowClass.width == WindowWidthClass.MEDIUM,
            onOpenPanel = { overlay = Overlay.Panel(it) },
            onManage = { overlay = Overlay.Manage },
            onPropose = onPropose,
            modifier = listModifier,
        )
    }

    Box(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        if (!views.loaded) return@Box
        when {
            twoPanes -> Row(Modifier.fillMaxSize()) {
                wall(Modifier.width(380.dp).fillMaxHeight())
                VerticalDivider(color = PpfTheme.palette.border)
                if (selected != null) {
                    RevendicationDetail(selected, onBack = null, compact = false, Modifier.fillMaxSize(), own = library.ownDemand(selected), onOpenDemand = { library.openAddress(it) }, movement = library.movementCarriers(selected), supporters = library.demandSupporters(selected))
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        PageHeader(stringResource(Res.string.space_revendications), stringResource(Res.string.desc_revendications))
                    }
                }
            }
            selected != null -> RevendicationDetail(
                selected,
                onBack = library::closeRevendication,
                compact = compact,
                Modifier.fillMaxSize(),
                own = library.ownDemand(selected),
                onOpenDemand = { library.openAddress(it) },
                movement = library.movementCarriers(selected),
                supporters = library.demandSupporters(selected),
            )
            else -> wall(Modifier.fillMaxSize())
        }
    }

    when (val shown = overlay) {
        is Overlay.Panel -> {
            val mode = shown.mode
            val initial = when (mode) {
                is PanelMode.Filters -> views.filters(mode.view.id)
                is PanelMode.EditView -> mode.view.query
                PanelMode.CreateView -> RevendicationQuery()
            }
            RevendicationFilterPanel(
                mode = mode,
                initial = initial,
                demands = library.revendications,
                context = context,
                hasAccount = account != null,
                canCreateView = views.canCreateCustom,
                compact = compact,
                onDismiss = { overlay = null },
                onApply = { filters ->
                    if (mode is PanelMode.Filters) views.setFilters(mode.view.id, filters)
                    overlay = null
                },
                onSaveAsView = { name, query ->
                    if (mode is PanelMode.Filters && views.createView(name, query, afterId = mode.view.id) != null) {
                        views.clearFilters(mode.view.id)
                        overlay = null
                    }
                },
                onSaveView = { name, query ->
                    when (mode) {
                        is PanelMode.EditView -> {
                            views.updateQuery(mode.view.id, query)
                            views.select(mode.view.id)
                            overlay = null
                        }
                        PanelMode.CreateView -> if (name != null && views.createView(name, query) != null) overlay = null
                        is PanelMode.Filters -> Unit
                    }
                },
            )
        }
        Overlay.Manage -> ManageViewsDialog(
            state = views,
            compact = compact,
            onDismiss = { overlay = null },
            onEditCriteria = { overlay = Overlay.Panel(PanelMode.EditView(it)) },
            onCreate = { overlay = Overlay.Panel(PanelMode.CreateView) },
        )
        null -> Unit
    }
}

/** The tabs of the views and, under them, the swiped pages of the wall. */
@Composable
private fun Wall(
    library: ContentLibrary,
    views: RevendicationViewsState,
    context: RevendicationContext,
    compact: Boolean,
    withTitle: Boolean,
    onOpenPanel: (PanelMode) -> Unit,
    onManage: () -> Unit,
    onPropose: (() -> Unit)?,
    modifier: Modifier,
) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    val list = views.views
    val currentIndex = list.indexOfFirst { it.id == views.currentViewId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = currentIndex) { list.size }
    // A view chosen elsewhere (created, removed, moved, edited): bring its page.
    LaunchedEffect(currentIndex, list.size) {
        if (pagerState.currentPage != currentIndex) pagerState.scrollToPage(currentIndex)
    }
    // A page reached by a swipe or a tab becomes the current view (the first value is the page already shown).
    LaunchedEffect(pagerState, list) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page -> list.getOrNull(page)?.let { views.select(it.id) } }
    }
    val tabsScroll = rememberScrollState()
    // The row of tabs is often wider than the window: it follows the view shown.
    LaunchedEffect(pagerState.currentPage, list.size, tabsScroll.maxValue) {
        val target = if (list.size <= 1) 0 else tabsScroll.maxValue * pagerState.currentPage / (list.size - 1)
        tabsScroll.animateScrollTo(target)
    }

    Column(modifier.testTag(RevendicationTags.LIST)) {
        Surface(color = palette.surface0, contentColor = palette.ink) {
            Column {
                if (withTitle) {
                    Text(
                        text = stringResource(Res.string.space_revendications),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp).semantics { heading() },
                    )
                }
                PpfTabs(
                    labels = list.map { viewLabel(it) },
                    selected = pagerState.currentPage.coerceIn(0, list.lastIndex),
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                    modifier = Modifier.horizontalScroll(tabsScroll).padding(horizontal = if (compact) 16.dp else 24.dp),
                    tagFor = { RevendicationTags.TAB + list[it].id },
                )
                HorizontalDivider(color = palette.border)
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), key = { list.getOrNull(it)?.id ?: it }) { page ->
            val view = list.getOrNull(page) ?: return@HorizontalPager
            WallPage(
                view = view,
                library = library,
                views = views,
                context = context,
                compact = compact,
                onOpenFilters = { onOpenPanel(PanelMode.Filters(view)) },
                onManage = onManage,
                onPropose = onPropose,
            )
        }
    }
}

/** One view: the tool bar, the number of demands, the active filters, then the cards or what to do when there is none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallPage(
    view: RevendicationView,
    library: ContentLibrary,
    views: RevendicationViewsState,
    context: RevendicationContext,
    compact: Boolean,
    onOpenFilters: () -> Unit,
    onManage: () -> Unit,
    onPropose: (() -> Unit)?,
) {
    val palette = PpfTheme.palette
    val filters = views.filters(view.id)
    // The criteria of the view win; the filters that still apply are the others.
    val active = filters.without(view.fixedCriteria)
    val effective = view.query.overlay(filters)
    // The user's own demands that are not published yet only appear in the views of their own.
    val demands = if (effective.authors == AuthorScope.MINE) {
        (library.revendications + library.myRevendications.map { it.article }).distinctBy { it.event.id }
    } else {
        library.revendications
    }
    val today = CivilDate.fromEpochSeconds(currentEpochSeconds())
    // A demand carried by an open official petition is pinned at the top of the wall (D-115), in the
    // order of the view among themselves; then the others, in the order of the view.
    val results = remember(demands, effective, context, library.decider) {
        val ordered = effective.apply(demands, context)
        val (pinned, others) = ordered.partition { library.decider.openPetitionFor(it.address, today) != null }
        pinned + others
    }
    val territories = remember(demands) { territoriesOf(demands) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(RevendicationTags.PAGE + view.id),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // MVP 1 (Galaxy A34, 360 dp): the first card must show high on the screen. Filters, sort and
            // "Personnaliser" share one row of chips, the number of demands sits beside "Proposer", and the
            // explanation folds into one line "Comment ça marche ?".
            Toolbar(
                activeCount = active.constraintCount,
                sort = effective.sort ?: RevendicationSort.NEWEST,
                onFilters = onOpenFilters,
                onSort = { views.setFilters(view.id, filters.copy(sort = it)) },
                onManage = onManage,
            )
            // The number of demands and what the view shows, with "Proposer" when it is offered.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(Res.plurals.rv_count, results.size, results.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.testTag(RevendicationTags.COUNT),
                    )
                    Text(viewDescription(view), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, maxLines = 2)
                }
                if (onPropose != null) {
                    Spacer(Modifier.width(8.dp))
                    PpfPrimaryButton(onClick = onPropose, text = stringResource(if (compact) Res.string.rv_propose_short else Res.string.rv_propose), icon = Lucide.Plus, modifier = Modifier.testTag(RevendicationTags.PROPOSE))
                }
            }
            if (active.constraintCount > 0) {
                ActiveFilters(active, territories, onChange = { views.setFilters(view.id, it) }, onClearAll = { views.clearFilters(view.id) })
            }
            if (view.id == RevendicationViews.ALL_ID) {
                HowItWorks()
            }
            if (results.isEmpty()) {
                EmptyState(
                    library = library,
                    effective = effective,
                    filtered = active.constraintCount > 0,
                    onClearFilters = { views.clearFilters(view.id) },
                    onPropose = onPropose,
                )
            }
            results.forEach { revendication ->
                RevendicationCard(
                    revendication = revendication,
                    onOpen = { library.openRevendication(revendication) },
                    selected = library.selectedRevendication?.address == revendication.address,
                    own = library.ownDemand(revendication),
                    petition = library.decider.openPetitionFor(revendication.address, today),
                    movement = library.movementCarriers(revendication),
                    supporters = library.demandSupporters(revendication),
                )
            }
        }
    }
}

/**
 * The explanation of the wall folded into one line (MVP 1): a touch unfolds it, a second folds it again.
 * Folded by default, so that the first card shows high on a phone; the state survives a rotation.
 */
@Composable
private fun HowItWorks() {
    val palette = PpfTheme.palette
    var open by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (open) Res.string.rv_how_it_works_open else Res.string.rv_how_it_works_closed)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = { open = !open },
            shape = MaterialTheme.shapes.small,
            color = palette.surface0,
            contentColor = palette.brand,
            modifier = Modifier.semantics { stateDescription = state }.testTag(RevendicationTags.HOW_IT_WORKS),
        ) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.CircleQuestionMark, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.rv_how_it_works), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(if (open) Lucide.ChevronUp else Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        if (open) {
            InfoBox(Lucide.ScrollText, Modifier.testTag(RevendicationTags.HOW_IT_WORKS_TEXT)) {
                Text(stringResource(Res.string.revendications_intro), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Filters (with the number of active ones), sort and "Personnaliser", on one row of chips that scrolls when narrow. */
@Composable
private fun Toolbar(
    activeCount: Int,
    sort: RevendicationSort,
    onFilters: () -> Unit,
    onSort: (RevendicationSort) -> Unit,
    onManage: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val filtersLabel = stringResource(Res.string.rv_filters)
        PpfToolbarButton(
            icon = Lucide.SlidersHorizontal,
            text = filtersLabel,
            onClick = onFilters,
            count = activeCount,
            contentDescription = if (activeCount > 0) pluralStringResource(Res.plurals.rv_filters_active, activeCount, activeCount) else null,
            modifier = Modifier.testTag(RevendicationTags.FILTERS),
        )
        Box {
            PpfToolbarButton(
                icon = Lucide.ArrowUpDown,
                text = stringResource(Res.string.rv_sort_button, stringResource(sortLabelResource(sort))),
                onClick = { sortMenu = true },
                modifier = Modifier.testTag(RevendicationTags.SORT),
            )
            DropdownMenu(
                expanded = sortMenu,
                onDismissRequest = { sortMenu = false },
                containerColor = PpfTheme.palette.surface2,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(Modifier.width(300.dp).padding(horizontal = 8.dp).selectableGroup()) {
                    RevendicationSort.entries.forEach { option ->
                        PpfRadioRow(
                            label = stringResource(sortLabelResource(option)),
                            selected = option == sort,
                            onClick = {
                                onSort(option)
                                sortMenu = false
                            },
                            supporting = if (option == RevendicationSort.POPULAR) stringResource(Res.string.rv_sort_popular_note) else null,
                            modifier = Modifier.testTag(RevendicationTags.SORT_OPTION + option.name.lowercase()),
                        )
                    }
                }
            }
        }
        PpfToolbarButton(icon = Lucide.Settings, text = stringResource(Res.string.rv_customize), onClick = onManage, modifier = Modifier.testTag(RevendicationTags.CUSTOMIZE))
    }
}

/** The filters that apply, one removable chip each, and "Tout effacer". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(active: RevendicationQuery, territories: Map<String, String>, onChange: (RevendicationQuery) -> Unit, onClearAll: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
        active.themes.forEach { id ->
            Chip(themeLabel(id), "theme:$id") { onChange(active.copy(themes = active.themes - id)) }
        }
        active.territories.forEach { key ->
            Chip(territoryLabel(key, territories), "territory:$key") { onChange(active.copy(territories = active.territories - key)) }
        }
        active.statuses.sortedBy { it.ordinal }.forEach { status ->
            Chip(stringResource(statusLabelResource(status)), "status:${status.tagValue}") { onChange(active.copy(statuses = active.statuses - status)) }
        }
        if (active.authors != AuthorScope.ANYONE) {
            val label = stringResource(if (active.authors == AuthorScope.FOLLOWED) Res.string.rv_view_followed else Res.string.rv_view_mine)
            Chip(label, "authors") { onChange(active.copy(authors = AuthorScope.ANYONE)) }
        }
        if (active.text.isNotBlank()) {
            Chip(stringResource(Res.string.rv_chip_text, active.text.trim()), "text") { onChange(active.copy(text = "")) }
        }
        PpfTextButton(onClick = onClearAll, text = stringResource(Res.string.rv_clear_all), modifier = Modifier.testTag(RevendicationTags.CLEAR_ALL))
    }
}

@Composable
private fun Chip(label: String, key: String, onRemove: () -> Unit) {
    PpfRemovableChip(
        label = label,
        onRemove = onRemove,
        removeDescription = stringResource(Res.string.rv_chip_remove, label),
        modifier = Modifier.testTag(RevendicationTags.CHIP + key),
    )
}

/** Why a view is empty, and what to do about it. */
@Composable
private fun EmptyState(
    library: ContentLibrary,
    effective: RevendicationQuery,
    filtered: Boolean,
    onClearFilters: () -> Unit,
    onPropose: (() -> Unit)?,
) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(RevendicationTags.EMPTY), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            library.revendications.isEmpty() -> if (library.status != LibraryStatus.Loading) {
                Text(stringResource(Res.string.revendications_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            effective.authors == AuthorScope.FOLLOWED && library.followed.isEmpty() -> InfoBox(Lucide.UsersRound) {
                Text(stringResource(Res.string.rv_empty_followed_none), style = MaterialTheme.typography.bodyMedium)
            }
            filtered -> {
                Text(stringResource(Res.string.rv_empty_filters), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                PpfSecondaryButton(onClick = onClearFilters, text = stringResource(Res.string.rv_clear_filters), modifier = Modifier.testTag(RevendicationTags.EMPTY_CLEAR))
            }
            effective.authors == AuthorScope.MINE -> {
                Text(stringResource(Res.string.rv_empty_mine), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                if (onPropose != null) PpfPrimaryButton(onClick = onPropose, text = stringResource(Res.string.rv_propose), icon = Lucide.Plus)
            }
            effective.authors == AuthorScope.FOLLOWED -> {
                Text(stringResource(Res.string.rv_empty_followed), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            else -> Text(stringResource(Res.string.rv_empty_view), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
    }
}

/** The card of a demand, used on the wall and in the feed: status, the demand, its territory and themes, the vote row. */
@Composable
fun RevendicationCard(
    revendication: Article,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    own: OwnDemand? = null,
    /** The open official petition that carries it (D-115): pinned, its link on the card. */
    petition: Petition? = null,
    /** Who carries it, when it is a demand of the movement gathered by the team (ContentLibrary.movementCarriers). */
    movement: List<String>? = null,
    /** Who else publicly supports it (ContentLibrary.demandSupporters), the same line for every demand. */
    supporters: List<String> = emptyList(),
) {
    val palette = PpfTheme.palette
    val example = revendication.title.startsWith(FeedPost.EXAMPLE_MARKER)
    PpfCard(onClick = onOpen, selected = selected, modifier = modifier.fillMaxWidth().testTag(RevendicationTags.CARD + revendication.slug)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Not yet on the wall: its review state replaces the status the team will give it.
            if (own != null && own.state != OwnDemandState.PUBLISHED) OwnStateBadge(own.state) else revendication.status?.let { StatusBadge(it) }
            Spacer(Modifier.weight(1f))
            if (example) ExampleBadge()
        }
        if (petition != null) {
            Spacer(Modifier.height(8.dp))
            PetitionLink(petition)
        }
        if (movement != null) {
            Spacer(Modifier.height(8.dp))
            MovementLine(movement)
        }
        if (supporters.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SupportersLine(supporters)
        }
        Spacer(Modifier.height(10.dp))
        Text(revendication.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(), style = MaterialTheme.typography.titleSmall)
        revendication.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        MetaRow(revendication)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = palette.border)
        Spacer(Modifier.height(4.dp))
        VoteRow(revendication)
    }
}

/**
 * The official petition filed by the team for this demand (D-115): pinned at the top of the wall,
 * signed only on the official site, after the "Vous quittez PPF" warning.
 */
@Composable
private fun PetitionLink(petition: Petition) {
    val open = rememberOpenUrl()
    val palette = PpfTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(RevendicationTags.PETITION)) {
        Icon(Lucide.ScrollText, contentDescription = null, tint = palette.brand, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        PpfTextButton(onClick = { open(petition.officialUrl) }, text = stringResource(Res.string.revendication_petition_open, hostOf(petition.officialUrl)))
    }
}

/** The territory and the themes of a demand (localized labels; its raw topics when it has no known theme). */
/**
 * A demand already carried by the movement (MVP 1): « Du mouvement », who carries it, and that PPF only
 * gathered it, with the help of an AI. The same for every such demand, in the order of the sources.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovementLine(carriers: List<String>) {
    val palette = PpfTheme.palette
    Column(Modifier.testTag(RevendicationTags.MOVEMENT), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PpfBadge(stringResource(Res.string.rv_movement_badge), BadgeStyle.BRAND, Modifier.align(Alignment.CenterVertically))
            Text(stringResource(Res.string.rv_movement_gathered), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.align(Alignment.CenterVertically))
        }
        if (carriers.isNotEmpty()) {
            Text(stringResource(Res.string.rv_movement_carried_by, carriers.joinToString(" ; ")), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}

/** « Également portée par » : the organisations that publicly support a demand, sourced in its text (D-152). */
@Composable
private fun SupportersLine(supporters: List<String>) {
    Text(
        stringResource(Res.string.rv_supported_by, supporters.joinToString(" ; ")),
        style = MaterialTheme.typography.bodySmall,
        color = PpfTheme.palette.inkMuted,
        modifier = Modifier.testTag(RevendicationTags.SUPPORTERS),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(revendication: Article) {
    val palette = PpfTheme.palette
    val themes = RevendicationThemes.of(revendication)
    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        revendication.territory?.let { territory ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterVertically)) {
                Icon(Lucide.MapPin, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(territory, style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            }
        }
        if (themes.isNotEmpty()) {
            themes.forEach { theme ->
                Text(themeLabel(theme.id), style = MaterialTheme.typography.labelLarge, color = palette.brand)
            }
        } else if (revendication.topics.isNotEmpty()) {
            Text(revendication.topics.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelLarge, color = palette.brand)
        }
    }
}

/**
 * Pour, Contre and Débat on a demand: the anonymous vote (D-110) once the team put it in debate and
 * its ballot box is open; the debate itself comes later.
 */
@Composable
private fun VoteRow(revendication: Article, detailed: Boolean = false) {
    // D-155: every published demand is open to votes, until it is retained or becomes a campaign.
    val inDebate = revendication.status != RevendicationStatus.RETENUE && revendication.status != RevendicationStatus.CAMPAGNE
    DemandVoteRow(
        revendication.address.toString(),
        inDebate = inDebate,
        detailed = detailed,
        territoryCode = revendication.event.firstTagValue(RevendicationTags.TERRITORY_CODE),
    )
}

@Composable
fun StatusBadge(status: RevendicationStatus, modifier: Modifier = Modifier) {
    val style = when (status) {
        RevendicationStatus.NOUVELLE -> BadgeStyle.NEUTRAL
        RevendicationStatus.EN_DEBAT -> BadgeStyle.BRAND
        RevendicationStatus.TRES_SOUTENUE -> BadgeStyle.SUCCESS
        RevendicationStatus.RETENUE -> BadgeStyle.SUCCESS
        RevendicationStatus.CAMPAGNE -> BadgeStyle.BRAND
    }
    PpfBadge(stringResource(statusLabelResource(status)), style, modifier)
}

@Composable
private fun RevendicationDetail(
    revendication: Article,
    onBack: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier,
    own: OwnDemand? = null,
    onOpenDemand: (String) -> Unit = {},
    movement: List<String>? = null,
    supporters: List<String> = emptyList(),
) {
    val palette = PpfTheme.palette
    val example = revendication.title.startsWith(FeedPost.EXAMPLE_MARKER)
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(RevendicationTags.DETAIL)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.revendication_back), onBack, Modifier.testTag(RevendicationTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (own != null && own.state != OwnDemandState.PUBLISHED) OwnStateBadge(own.state) else revendication.status?.let { StatusBadge(it) }
                if (example) ExampleBadge()
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = revendication.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            revendication.summary?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            MetaRow(revendication)
            if (movement != null) {
                Spacer(Modifier.height(8.dp))
                MovementLine(movement)
            }
            if (supporters.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SupportersLine(supporters)
            }
            Text(
                text = stringResource(Res.string.content_updated, CivilDate.fromEpochSeconds(revendication.updatedAt).toFrench()),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkSubtle,
            )
            if (own != null) OwnDemandReview(own, onOpenDemand)
            Spacer(Modifier.height(12.dp))
            PpfCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { VoteRow(revendication, detailed = true) }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(Res.string.revendication_argument), style = MaterialTheme.typography.titleMedium, color = palette.brand, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                MarkdownText(revendication.body)
            }
            // S-38, D-234: any user may report a demand to the team (not one's own while it waits).
            if (own == null) ReportContentButton(revendication.address.toString())
        }
    }
}

/** Where one of the user's own demands stands, instead of the team's status while it is not published. */
@Composable
fun OwnStateBadge(state: OwnDemandState, modifier: Modifier = Modifier) {
    val (label, style) = when (state) {
        OwnDemandState.WAITING -> Res.string.proposal_status_waiting to BadgeStyle.NEUTRAL
        OwnDemandState.IN_REVIEW -> Res.string.proposal_status_pending to BadgeStyle.EXAMPLE
        OwnDemandState.PUBLISHED -> Res.string.proposal_status_approved to BadgeStyle.SUCCESS
        OwnDemandState.NOT_PUBLISHED -> Res.string.proposal_status_rejected to BadgeStyle.NEUTRAL
    }
    PpfBadge(stringResource(label), style, modifier.testTag(RevendicationTags.OWN_STATE + state.name.lowercase()))
}

/** The review of one of the user's own demands: the team's reason, then what the automatic checks found on the server. */
@Composable
private fun OwnDemandReview(own: OwnDemand, onOpenDemand: (String) -> Unit) {
    val palette = PpfTheme.palette
    val reason = own.reason
    val issues = own.report?.issues.orEmpty()
    if (reason == null && issues.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    PpfCard(Modifier.fillMaxWidth().testTag(RevendicationTags.OWN_REVIEW)) {
        if (reason != null) {
            Text(stringResource(Res.string.proposal_reason, reason), style = MaterialTheme.typography.bodyMedium)
            if (issues.isNotEmpty()) Spacer(Modifier.height(12.dp))
        }
        if (issues.isNotEmpty()) {
            Text(stringResource(Res.string.proposal_report_title), style = MaterialTheme.typography.titleSmall, color = palette.brand, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            IssueList(issues, onOpenDemand)
        }
    }
}
