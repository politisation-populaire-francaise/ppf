package org.politisationpopulairefrancaise.ui.voir

import org.politisationpopulairefrancaise.ui.voir.parlement.BillOfficialVotes
import org.politisationpopulairefrancaise.ui.voir.parlement.PersonOfficialVotesLink

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfTabs
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentLinks
import org.politisationpopulairefrancaise.ui.voir.parlement.OfficialParliamentCard
import org.politisationpopulairefrancaise.ui.voir.parlement.ParliamentScreen
import org.politisationpopulairefrancaise.ui.voir.parlement.ParliamentState

/**
 * "Voir" (vision, pillar 1): what is happening and what the power does, in figures and sourced
 * facts, on three axes that the reader swipes between like the views of the feed: the movement,
 * the power, freedoms and counter-powers. Every list opens a page; the back button (or the
 * system's) closes it. One column on phones, two on tablets, three on wide windows; long pages put
 * their method and their next steps beside the content on wide windows.
 */
@Composable
fun VoirScreen(
    library: ContentLibrary,
    state: VoirState,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onNavigate: (Space) -> Unit = {},
    requests: VoirRequestSession? = null,
    parliament: ParliamentState? = null,
) {
    val catalog = library.voir
    val compact = windowClass.width == WindowWidthClass.COMPACT
    var glossary by remember { mutableStateOf(false) }
    val ownScope = rememberCoroutineScope()
    val session = requests ?: remember { VoirRequestSession(messaging = null, remote = null, scope = ownScope) }
    // Anonymous error reports written offline leave as soon as "Voir" is shown again with a server.
    LaunchedEffect(session) { session.retryPending() }
    val callbacks = RequestCallbacks(
        createAccount = { onNavigate(Space.MON_ESPACE) },
        openMessages = {
            session.openTeamConversation()
            onNavigate(Space.MESSAGES)
        },
        back = { state.back() },
    )
    val actions = VoirActions(
        open = state::open,
        next = { target ->
            when (target) {
                is NextTarget.ToSpace -> onNavigate(target.space)
                is NextTarget.ToContent -> when {
                    catalog.item(target.address) != null -> state.open(target.address)
                    else -> library.guides.firstOrNull { it.address == target.address }?.let { guide ->
                        library.open(guide)
                        onNavigate(Space.COMPRENDRE)
                    }
                }
            }
        },
        explain = { glossary = true },
        reply = { address -> state.open(VoirDestination.Reply(address)) },
    )
    // The system's back (Android button or gesture, Escape on a computer) closes the open page first.
    SystemBack(enabled = state.stack.isNotEmpty()) { state.back() }

    BoxWithConstraints(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        // Cards of about 300 dp at least: one column on phones, two on tablets and small computer
        // windows, three on wide ones (the side bar and the rail already took their share).
        val columns = when {
            maxWidth < 600.dp -> 1
            maxWidth < 960.dp -> 2
            else -> 3
        }
        // Long pages put their method and their next steps beside the content when there is room.
        val wide = maxWidth >= 900.dp
        Box(Modifier.fillMaxSize().testTag(VoirTags.SCREEN)) {
            val current = state.current
            if (parliament != null && parliament.isOpen) {
                // The parliamentary open data (D-065), with their own stack of pages.
                ParliamentScreen(parliament, voirSheetFor = { id ->
                    catalog.persons(null).firstOrNull { ParliamentLinks.memberId(it.article.event) == id }?.let { person ->
                        {
                            parliament.close()
                            state.open(person.address)
                        }
                    }
                })
            } else if (current == null) {
                VoirHome(catalog, library.status, state, actions, compact, columns, parliament)
            } else {
                key(current) { VoirDetail(current, catalog, state, actions, compact, wide, columns, session, callbacks, parliament) }
            }
        }
    }
    if (glossary) GlossaryDialog(onDismiss = { glossary = false })
}

@Composable
private fun VoirHome(catalog: VoirCatalog, status: LibraryStatus, state: VoirState, actions: VoirActions, compact: Boolean, columns: Int, parliament: ParliamentState?) {
    val axes = VoirAxis.entries
    val pagerState = rememberPagerState(initialPage = state.axis.ordinal) { axes.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { state.axis = axes[pagerState.currentPage] }
    val padding = if (compact) 16.dp else 24.dp
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = padding, end = padding, top = padding)) {
            PageHeader(stringResource(Res.string.space_voir), if (compact) null else stringResource(Res.string.desc_voir))
            Spacer(Modifier.height(12.dp))
            SearchField(state.query, onChange = { state.query = it }, modifier = Modifier.widthIn(max = 720.dp))
            FollowedStrip(catalog, state)
            if (state.query.isBlank()) {
                PpfTabs(
                    labels = axes.map { stringResource(it.tab) },
                    selected = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                    tagFor = VoirTags::tab,
                )
            }
        }
        HorizontalDivider(color = PpfTheme.palette.border)
        when {
            state.query.isNotBlank() -> SearchResults(catalog, state, actions, padding)
            catalog.isEmpty && status != LibraryStatus.Loading -> Text(
                stringResource(Res.string.voir_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = PpfTheme.palette.inkMuted,
                modifier = Modifier.padding(padding),
            )
            else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { axes[it].tagValue }) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(VoirTags.page(page))) {
                    Text(stringResource(axes[page].intro), style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.inkMuted)
                    Spacer(Modifier.height(16.dp))
                    when (axes[page]) {
                        VoirAxis.MOUVEMENT -> MovementAxis(catalog, state, actions, compact, columns)
                        VoirAxis.POUVOIR -> PowerAxis(catalog, state, actions, columns, parliament)
                        VoirAxis.LIBERTES -> FreedomsAxis(catalog, state, actions, compact, columns)
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementAxis(catalog: VoirCatalog, state: VoirState, actions: VoirActions, compact: Boolean, columns: Int) {
    val figures = catalog.keyFigures(VoirAxis.MOUVEMENT)
    val shown = if (compact) 3 else columns * 2
    SectionHeader(
        stringResource(Res.string.voir_key_figures),
        action = stringResource(Res.string.voir_see_all_count, figures.size).takeIf { figures.size > shown },
        actionTag = VoirTags.seeAll("figures"),
        onAction = { state.open(VoirDestination.KeyFigures(VoirAxis.MOUVEMENT)) },
    )
    KeyFigureGrid(figures.take(shown), columns, onOpen = { actions.open(it.address) })
    Spacer(Modifier.height(24.dp))
    val chronology = catalog.chronology()
    SectionHeader(stringResource(Res.string.voir_chronology), action = stringResource(Res.string.voir_see_all_count, chronology.size), actionTag = VoirTags.seeAll("chronology"), onAction = { state.open(VoirDestination.Chronology) })
    Grid(chronology.take(if (compact) 3 else columns), columns) { fact, modifier -> FactCard(fact, onOpen = { actions.open(fact.address) }, modifier = modifier) }
    if (catalog.movements.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionHeader(stringResource(Res.string.voir_world), action = stringResource(Res.string.voir_see_all_count, catalog.movements.size), actionTag = VoirTags.seeAll("world"), onAction = { state.open(VoirDestination.World) })
        Grid(catalog.movements.take(if (compact) 2 else columns), columns) { movement, modifier -> MovementCard(movement, onOpen = { actions.open(movement.address) }, modifier = modifier) }
    }
    Spacer(Modifier.height(24.dp))
    MethodCard(actions)
}

@Composable
private fun PowerAxis(catalog: VoirCatalog, state: VoirState, actions: VoirActions, columns: Int, parliament: ParliamentState?) {
    if (catalog.parties.isNotEmpty()) {
        SectionHeader(stringResource(Res.string.voir_parties), action = stringResource(Res.string.voir_see_all_count, catalog.parties.size), actionTag = VoirTags.seeAll("parties"), onAction = { state.open(VoirDestination.Parties) })
        Grid(catalog.parties.take(maxOf(columns, 2)), columns) { party, modifier -> PartyCard(party, catalog, onOpen = { actions.open(party.address) }, modifier = modifier) }
        Spacer(Modifier.height(24.dp))
    }
    val persons = catalog.persons(null)
    SectionHeader(stringResource(Res.string.voir_people), action = stringResource(Res.string.voir_see_all_count, persons.size), actionTag = VoirTags.seeAll("people"), onAction = { state.open(VoirDestination.People) })
    Grid(persons.take(maxOf(columns * 2, 4)), columns) { person, modifier -> PersonCard(person, catalog, onOpen = { actions.open(person.address) }, modifier = modifier) }
    if (parliament != null) {
        Spacer(Modifier.height(12.dp))
        OfficialParliamentCard(onOpen = parliament::open)
    }
    Spacer(Modifier.height(24.dp))
    val bills = catalog.bills()
    SectionHeader(stringResource(Res.string.voir_bills), action = stringResource(Res.string.voir_see_all_count, bills.size), actionTag = VoirTags.seeAll("bills"), onAction = { state.open(VoirDestination.Bills) })
    Grid(bills.take(maxOf(columns, 3)), columns) { text, modifier -> BillCard(text, onOpen = { actions.open(text.address) }, modifier = modifier) }
    Spacer(Modifier.height(24.dp))
    SectionHeader(stringResource(Res.string.voir_latest_power))
    Grid(catalog.latestAboutPower(if (columns == 1) 4 else columns * 2), columns) { fact, modifier -> FactCard(fact, onOpen = { actions.open(fact.address) }, catalog = catalog, modifier = modifier) }
    Spacer(Modifier.height(24.dp))
    CorrectionsLink(state)
    Spacer(Modifier.height(12.dp))
    MethodCard(actions)
}

@Composable
private fun FreedomsAxis(catalog: VoirCatalog, state: VoirState, actions: VoirActions, compact: Boolean, columns: Int) {
    val figures = catalog.keyFigures(VoirAxis.LIBERTES)
    val shown = if (compact) 4 else columns * 2
    SectionHeader(
        stringResource(Res.string.voir_counter_powers),
        action = stringResource(Res.string.voir_see_all_count, figures.size).takeIf { figures.size > shown },
        actionTag = VoirTags.seeAll("counter-powers"),
        onAction = { state.open(VoirDestination.KeyFigures(VoirAxis.LIBERTES)) },
    )
    KeyFigureGrid(figures.take(shown), columns, onOpen = { actions.open(it.address) })
    Spacer(Modifier.height(24.dp))
    val liberties = catalog.liberties()
    SectionHeader(stringResource(Res.string.voir_liberties), action = stringResource(Res.string.voir_see_all_count, liberties.size), actionTag = VoirTags.seeAll("liberties"), onAction = { state.open(VoirDestination.Liberties) })
    Grid(liberties.take(if (compact) 3 else columns), columns) { fact, modifier -> FactCard(fact, onOpen = { actions.open(fact.address) }, modifier = modifier) }
    if (catalog.dossiers.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionHeader(stringResource(Res.string.voir_dossiers))
        Grid(catalog.dossiers.filter { it.axis == VoirAxis.LIBERTES }, columns) { dossier, modifier -> DossierCard(dossier, onOpen = { actions.open(dossier.address) }, modifier = modifier) }
    }
    Spacer(Modifier.height(24.dp))
    CorrectionsLink(state)
    Spacer(Modifier.height(12.dp))
    MethodCard(actions)
}

/** How to read the labels: always one touch away. */
@Composable
private fun MethodCard(actions: VoirActions) {
    InfoBox(Lucide.ShieldCheck) {
        Text(stringResource(Res.string.voir_method_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.voir_method_text), style = MaterialTheme.typography.bodyMedium)
        PpfTextButton(onClick = actions.explain, text = stringResource(Res.string.voir_method_open), modifier = Modifier.testTag(VoirTags.GLOSSARY_OPEN))
    }
}

@Composable
private fun CorrectionsLink(state: VoirState) {
    LinkRow(stringResource(Res.string.voir_corrections), Lucide.Pencil, onClick = { state.open(VoirDestination.Corrections) }, modifier = Modifier.testTag(VoirTags.CORRECTIONS_OPEN))
}

/** The search field of the space: every word must match, accents and case ignored. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val label = stringResource(Res.string.voir_search_label)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.ink),
        cursorBrush = SolidColor(palette.brand),
        modifier = modifier.fillMaxWidth().testTag(VoirTags.SEARCH).semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(palette.surface2, MaterialTheme.shapes.small).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Search, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(stringResource(Res.string.voir_search_hint), style = MaterialTheme.typography.bodyLarge, color = palette.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                if (value.isNotEmpty()) {
                    PpfIconButton(Lucide.X, stringResource(Res.string.voir_search_clear), onClick = { onChange("") }, modifier = Modifier.testTag(VoirTags.SEARCH_CLEAR))
                }
            }
        },
    )
}

@Composable
private fun SearchResults(catalog: VoirCatalog, state: VoirState, actions: VoirActions, padding: androidx.compose.ui.unit.Dp) {
    val results = catalog.search(state.query)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(VoirTags.SEARCH_RESULTS)) {
        Text(stringResource(Res.string.voir_search_results, state.query.trim()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        if (results.isEmpty()) Text(stringResource(Res.string.voir_search_none), style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.inkMuted)
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            results.forEach { item -> ItemRow(item, onClick = { actions.open(item.address) }) }
        }
    }
}

/** One content of "Voir" on one line: its icon, its title and what it is. */
@Composable
private fun ItemRow(item: VoirItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val (icon, subtitle) = describe(item)
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(VoirTags.card(item.address.identifier)), shape = MaterialTheme.shapes.small, color = palette.surface2, contentColor = palette.ink) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                VoirTitle(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun describe(item: VoirItem): Pair<ImageVector, String> = when (item) {
    is VoirItem.OfIndicator -> Lucide.ListChecks to stringResource(Res.string.voir_key_figures)
    is VoirItem.OfFact -> Lucide.Flag to "${stringResource(item.fact.kind.label)} · ${item.fact.date.toFrenchShort()}"
    is VoirItem.OfPerson -> Lucide.User to (item.person.function ?: stringResource(Res.string.voir_people))
    is VoirItem.OfParty -> Lucide.Landmark to listOfNotNull(item.party.acronym, stringResource(Res.string.voir_party_kind)).joinToString(" · ")
    is VoirItem.OfText -> Lucide.ScrollText to stringResource(item.text.stage.label)
    is VoirItem.OfMovement -> Lucide.Globe to item.movement.country
    is VoirItem.OfDossier -> Lucide.BookOpen to stringResource(Res.string.voir_dossiers)
}

/** What the user follows, one touch away, above the tabs; the list never leaves the device. */
@Composable
private fun FollowedStrip(catalog: VoirCatalog, state: VoirState) {
    val items = state.followed.mapNotNull(catalog::item)
    if (items.isEmpty()) return
    val palette = PpfTheme.palette
    Spacer(Modifier.height(12.dp))
    Text(stringResource(Res.string.voir_followed), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(VoirTags.section("followed")), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val (icon, _) = describe(item)
            Surface(
                onClick = { state.open(item.address) },
                modifier = Modifier.heightIn(min = 44.dp).widthIn(max = 260.dp),
                shape = MaterialTheme.shapes.small,
                color = palette.brandTint,
                contentColor = palette.ink,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(item.title.withoutExample(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Text(stringResource(Res.string.voir_followed_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun VoirDetail(
    current: VoirDestination,
    catalog: VoirCatalog,
    state: VoirState,
    actions: VoirActions,
    compact: Boolean,
    wide: Boolean,
    columns: Int,
    session: VoirRequestSession,
    callbacks: RequestCallbacks,
    parliament: ParliamentState? = null,
) {
    val palette = PpfTheme.palette
    val padding = if (compact) 16.dp else 24.dp
    Column(Modifier.fillMaxSize()) {
        // The way back stays in sight however long the page: the back button, then a breadcrumb to the
        // home of the space and its axis.
        Row(Modifier.fillMaxWidth().background(palette.surface1).padding(horizontal = padding - 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.content_back), onClick = { state.back() }, modifier = Modifier.testTag(VoirTags.BACK), tint = palette.ink)
            Spacer(Modifier.width(4.dp))
            PpfTextButton(onClick = { state.home() }, text = stringResource(Res.string.space_voir) + " · " + stringResource(axisOf(current, catalog, state).label), color = palette.inkMuted)
        }
        HorizontalDivider(color = palette.border)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(padding)) {
        Column(Modifier.widthIn(max = if (wide) 1100.dp else 760.dp)) {
            when (current) {
                is VoirDestination.Item -> {
                    val item = catalog.item(current.address)
                    when (item) {
                        is VoirItem.OfIndicator -> IndicatorPage(item.indicator, state, wide, actions.next, actions.explain)
                        is VoirItem.OfFact -> FactPage(item.fact, catalog, state, actions)
                        is VoirItem.OfPerson -> {
                            // Their votes in the parliamentary open data, when the sheet carries an official identifier (D-065).
                            parliament?.let { PersonOfficialVotesLink(item.person, it, Modifier.padding(bottom = 12.dp)) }
                            PersonPage(item.person, catalog, state, actions, wide)
                        }
                        is VoirItem.OfParty -> PartyPage(item.party, catalog, state, actions, wide, columns)
                        is VoirItem.OfText -> {
                            BillPage(item.text, catalog, state, actions, wide)
                            // Its public ballots, when the bill carries the identifier of its legislative file (D-065).
                            parliament?.let { BillOfficialVotes(item.text, it, Modifier.padding(top = 16.dp)) }
                        }
                        is VoirItem.OfMovement -> MovementPage(item.movement)
                        is VoirItem.OfDossier -> DossierPage(item.dossier)
                        null -> Text(stringResource(Res.string.voir_empty), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                    }
                    // Every content ends with a discreet way to report an error (VO-22).
                    if (item != null) ReportErrorLink(onClick = { state.open(VoirDestination.Report(item.address)) })
                }
                is VoirDestination.Reply -> when (val item = catalog.item(current.address)) {
                    null -> Text(stringResource(Res.string.voir_empty), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                    else -> ReplyFormPage(item, catalog, session, callbacks)
                }
                is VoirDestination.Report -> when (val item = catalog.item(current.address)) {
                    null -> Text(stringResource(Res.string.voir_empty), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                    else -> ErrorReportPage(item, session, callbacks)
                }
                VoirDestination.Chronology -> ChronologyPage(catalog, state, actions)
                VoirDestination.Liberties -> LibertiesPage(catalog, state, actions)
                VoirDestination.People -> PeoplePage(catalog, state, actions, columns)
                VoirDestination.Parties -> PartiesPage(catalog, actions, columns)
                VoirDestination.Bills -> BillsPage(catalog, actions, columns)
                VoirDestination.World -> WorldPage(catalog, actions, columns)
                VoirDestination.Corrections -> CorrectionsPage(catalog, actions)
                is VoirDestination.KeyFigures -> KeyFiguresPage(catalog, current.axis, actions, columns)
            }
        }
        }
    }
}

private fun axisOf(destination: VoirDestination, catalog: VoirCatalog, state: VoirState): VoirAxis = when (destination) {
    is VoirDestination.Item -> catalog.item(destination.address)?.axis ?: state.axis
    VoirDestination.Chronology, VoirDestination.World -> VoirAxis.MOUVEMENT
    VoirDestination.People, VoirDestination.Parties, VoirDestination.Bills -> VoirAxis.POUVOIR
    VoirDestination.Liberties -> VoirAxis.LIBERTES
    VoirDestination.Corrections -> state.axis
    is VoirDestination.KeyFigures -> destination.axis
    is VoirDestination.Reply -> catalog.item(destination.address)?.axis ?: state.axis
    is VoirDestination.Report -> catalog.item(destination.address)?.axis ?: state.axis
}
