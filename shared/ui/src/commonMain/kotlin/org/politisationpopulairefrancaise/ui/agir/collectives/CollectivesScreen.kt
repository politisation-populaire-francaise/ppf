package org.politisationpopulairefrancaise.ui.agir.collectives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.collectives.Collective
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveQuery
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveScope
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveType
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaNames
import org.politisationpopulairefrancaise.ui.agir.agenda.ProposeMobilizationScreen
import org.politisationpopulairefrancaise.ui.agir.calls.CallComposerScreen
import org.politisationpopulairefrancaise.ui.agir.calls.CallsLibrary
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.groups.GroupScreen
import org.politisationpopulairefrancaise.ui.groups.GroupsStatus
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.collectifs_back
import org.politisationpopulairefrancaise.ui.resources.collectifs_card_joined
import org.politisationpopulairefrancaise.ui.resources.collectifs_card_near
import org.politisationpopulairefrancaise.ui.resources.collectifs_composer_note
import org.politisationpopulairefrancaise.ui.resources.collectifs_create
import org.politisationpopulairefrancaise.ui.resources.collectifs_empty
import org.politisationpopulairefrancaise.ui.resources.collectifs_empty_filtered
import org.politisationpopulairefrancaise.ui.resources.collectifs_filter_clear
import org.politisationpopulairefrancaise.ui.resources.collectifs_filter_near
import org.politisationpopulairefrancaise.ui.resources.collectifs_filter_scope
import org.politisationpopulairefrancaise.ui.resources.collectifs_filter_theme
import org.politisationpopulairefrancaise.ui.resources.collectifs_filter_type
import org.politisationpopulairefrancaise.ui.resources.collectifs_intro
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_ar
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_br
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_de
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_en
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_es
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_eu
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_fr
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_it
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_oc
import org.politisationpopulairefrancaise.ui.resources.collectifs_language_pt
import org.politisationpopulairefrancaise.ui.resources.collectifs_missing_text
import org.politisationpopulairefrancaise.ui.resources.collectifs_rooms_link
import org.politisationpopulairefrancaise.ui.resources.collectifs_rooms_text
import org.politisationpopulairefrancaise.ui.resources.collectifs_scope_departemental
import org.politisationpopulairefrancaise.ui.resources.collectifs_scope_etablissement
import org.politisationpopulairefrancaise.ui.resources.collectifs_scope_local
import org.politisationpopulairefrancaise.ui.resources.collectifs_scope_national
import org.politisationpopulairefrancaise.ui.resources.collectifs_scope_quartier
import org.politisationpopulairefrancaise.ui.resources.collectifs_scope_regional
import org.politisationpopulairefrancaise.ui.resources.collectifs_scope_unknown
import org.politisationpopulairefrancaise.ui.resources.collectifs_search_hint
import org.politisationpopulairefrancaise.ui.resources.collectifs_status_loading
import org.politisationpopulairefrancaise.ui.resources.collectifs_status_offline
import org.politisationpopulairefrancaise.ui.resources.collectifs_status_unavailable
import org.politisationpopulairefrancaise.ui.resources.collectifs_status_unreachable
import org.politisationpopulairefrancaise.ui.resources.collectifs_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_type_assemblee
import org.politisationpopulairefrancaise.ui.resources.collectifs_type_autre
import org.politisationpopulairefrancaise.ui.resources.collectifs_type_club
import org.politisationpopulairefrancaise.ui.resources.collectifs_type_collectif
import org.politisationpopulairefrancaise.ui.resources.collectifs_type_comite
import org.politisationpopulairefrancaise.ui.resources.collectifs_type_reseau
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.urne.LocalVoting

/** Test tags of the collectifs of "Agir" (M24). */
object CollectivesTags {
    // Directory (AG-47)
    const val DIRECTORY = "collectifs-directory"
    const val LIST = "collectifs-list"
    const val SEARCH = "collectifs-search"
    const val CREATE = "collectifs-create"
    const val ROOMS = "collectifs-rooms"
    const val CARD = "collectifs-card-" // followed by the id
    const val THEME = "collectifs-theme-" // followed by the theme
    const val TYPE = "collectifs-type-" // followed by the type tag
    const val SCOPE = "collectifs-scope-" // followed by the scope tag
    const val NEAR = "collectifs-near"
    const val CLEAR = "collectifs-clear"
    const val EMPTY = "collectifs-empty"
    const val STATUS = "collectifs-status"

    // Page (AG-48)
    const val PAGE = "collectifs-page"
    const val PAGE_BACK = "collectifs-page-back"
    const val MISSING = "collectifs-missing"
    const val JOIN = "collectifs-join"
    const val LEAVE = "collectifs-leave"
    const val OPEN_ROOM = "collectifs-open-room"
    const val CHARTER_TOGGLE = "collectifs-charter-toggle"
    const val CHARTER_TEXT = "collectifs-charter-text"
    const val ROLES = "collectifs-roles"
    const val EVENTS = "collectifs-events"
    const val EVENT = "collectifs-event-" // followed by the identifier
    const val CALLS = "collectifs-calls"
    const val CALL = "collectifs-call-" // followed by the slug
    const val PROPOSE_EVENT = "collectifs-propose-event"
    const val LAUNCH_CALL = "collectifs-launch-call"
    const val COMPOSER_NOTE = "collectifs-composer-note"

    // Creation (S-129)
    const val WIZARD = "collectifs-wizard"
    const val WIZARD_CLOSE = "collectifs-wizard-close"
    const val NEED_ACCOUNT = "collectifs-need-account"
    const val CHECKING = "collectifs-checking"
    const val NEEDS_VERIFIED = "collectifs-needs-verified"
    const val OPEN_MY_SPACE = "collectifs-open-my-space"
    const val CREATE_ROOM = "collectifs-create-room"
    const val STEP = "collectifs-step"
    const val NAME = "collectifs-name"
    const val TERRITORY = "collectifs-territory"
    const val TERRITORY_CHOICE = "collectifs-territory-choice-" // followed by the code
    const val TERRITORY_CODE = "collectifs-territory-code"
    const val TERRITORY_CODE_CLEAR = "collectifs-territory-code-clear"
    const val SCOPE_CHOICE = "collectifs-scope-choice-" // followed by the scope tag
    const val ABOUT = "collectifs-about"
    const val TYPE_CHOICE = "collectifs-type-choice-" // followed by the type tag
    const val THEMES = "collectifs-themes"
    const val LANGUAGE = "collectifs-language-" // followed by the code
    const val INTERNAL_CHARTER = "collectifs-internal-charter"
    const val NEXT = "collectifs-next"
    const val PREVIOUS = "collectifs-previous"
    const val BLOCKED = "collectifs-blocked"
    const val SOON = "collectifs-soon"
    const val CHARTER = "collectifs-charter"
    const val WARNING = "collectifs-warning"
    const val CANCEL = "collectifs-cancel"
    const val SUBMIT = "collectifs-submit"
    const val REFUSED = "collectifs-refused"
}

/**
 * The collectifs of "Agir" (M24, step 1, D-087): the directory (AG-47), the page of a collectif
 * (AG-48), its salon, its composers of events and calls (AG-49, AG-50) and the creation wizard
 * (S-129). One screen at a time, centred and at most 720 dp wide, on phones, tablets and computers
 * alike. [agenda] and [calls] are the libraries of the agenda and of the calls (the shell's), whose
 * events and calls carry the collectif's tag; [onBack] leads back to the home of "Agir".
 */
@Composable
fun CollectivesScreen(
    library: CollectivesLibrary,
    session: AccountSession,
    windowClass: WindowClass,
    agenda: AgendaLibrary,
    calls: CallsLibrary,
    onBack: () -> Unit,
    onNavigate: (Space) -> Unit,
    /** The plain rooms (salons) of "Messages", open to all. */
    onOpenRooms: () -> Unit,
    /** The creation of a plain room, open to all. */
    onCreateRoom: () -> Unit,
    onOpenMobilization: (Mobilization) -> Unit,
    onOpenCall: (Call) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(library) { library.start() }
    // Inside the collectifs, the system's back first returns to the page, then to the directory.
    SystemBack(enabled = library.view != CollectivesView.Directory) {
        if (library.view == CollectivesView.Create) library.cancelCreation() else library.back()
    }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    Box(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        when (val view = library.view) {
            CollectivesView.Directory -> CollectivesDirectory(library, compact, onBack, onOpenRooms) { library.beginCreation(session.signer, it) }
            is CollectivesView.Page -> {
                val collective = library.find(view.id)
                if (collective == null) {
                    MissingCollective(compact, library::toDirectory)
                } else {
                    CollectivePage(library, collective, session, agenda, calls, compact, onOpenMobilization, onOpenCall)
                }
            }
            CollectivesView.Create -> CollectiveCreationWizard(library, session, compact, onNavigate, onCreateRoom)
            is CollectivesView.Room -> {
                // Back from another space: the salon is opened again, with its messages.
                LaunchedEffect(view.id) {
                    if (library.groups.selected?.id != view.id) library.groups.groups.firstOrNull { it.id == view.id }?.let { library.groups.open(it) }
                }
                val room = library.groups.selected ?: library.groups.groups.firstOrNull { it.id == view.id }
                if (room == null) {
                    MissingCollective(compact, library::toDirectory)
                } else {
                    GroupScreen(library.groups, room, session, onBack = library::back, onNavigate = onNavigate, compact = compact)
                }
            }
            is CollectivesView.ProposeEvent -> {
                val composer = agenda.composer
                // Its cross, or the way back of its result, closes the form: back to the page of the collectif.
                LaunchedEffect(composer.open) {
                    if (!composer.open) {
                        composer.collective = null
                        library.back()
                    }
                }
                DisposableEffect(view.id) {
                    // Leaving "Agir" with the form open: it never stays tied to the collectif.
                    onDispose {
                        if (composer.collective?.id == view.id) {
                            composer.collective = null
                            composer.open = false
                        }
                    }
                }
                val names = remember(agenda) { AgendaNames({ null }, { agenda.isEditorial(it) }) }
                ProposeMobilizationScreen(agenda, session.signer, windowClass, names, onCreateAccount = { onNavigate(Space.MON_ESPACE) }, modifier = Modifier.fillMaxSize())
            }
            is CollectivesView.LaunchCall -> {
                val composer = calls.composer
                LaunchedEffect(composer.open) {
                    if (!composer.open) {
                        composer.collective = null
                        library.back()
                    }
                }
                DisposableEffect(view.id) {
                    // Leaving "Agir" with the form open: it never stays tied to the collectif.
                    onDispose {
                        if (composer.collective?.id == view.id) {
                            composer.collective = null
                            composer.open = false
                        }
                    }
                }
                CallComposerScreen(
                    composer = composer,
                    calls = calls.calls,
                    signer = session.signer,
                    // A collectif is not a verified organisation: no call to raise funds in its name (AP-08).
                    canFundraise = false,
                    windowClass = windowClass,
                    onClose = { composer.open = false },
                    onOpenCall = { call ->
                        composer.open = false
                        onOpenCall(call)
                    },
                    onCreateAccount = { onNavigate(Space.MON_ESPACE) },
                    onSent = calls::onCallSent,
                )
            }
        }
    }
}

/**
 * Under the title of the composers of "Agir" opened from a collectif's page: in whose name the event
 * or the call is published. Nothing when the composer publishes in the author's name alone.
 */
@Composable
fun CollectiveComposerNote(collective: CollectiveLink?, modifier: Modifier = Modifier) {
    if (collective == null) return
    Spacer(Modifier.height(12.dp))
    InfoBox(Lucide.Users, modifier.testTag(CollectivesTags.COMPOSER_NOTE)) {
        Text(stringResource(Res.string.collectifs_composer_note, collective.name), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The directory: search, filters, the cards, the creation and the way to the plain rooms. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectivesDirectory(
    library: CollectivesLibrary,
    compact: Boolean,
    onBack: () -> Unit,
    onOpenRooms: () -> Unit,
    onCreate: (VoterStatus?) -> Unit,
) {
    val palette = PpfTheme.palette
    val voting = LocalVoting.current
    val query = library.query
    val shown = library.shown
    val themes = library.themes
    val home = library.home
    val padding = if (compact) 16.dp else 24.dp
    Box(Modifier.fillMaxSize().testTag(AgirTags.COLLECTIVES)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(CollectivesTags.LIST),
            contentPadding = PaddingValues(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag(CollectivesTags.DIRECTORY)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.collectifs_back), onBack, Modifier.testTag(AgirTags.BACK), tint = palette.ink)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.collectifs_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(Res.string.collectifs_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                    Spacer(Modifier.height(12.dp))
                    PpfTextField(
                        value = query.text,
                        onValueChange = library::search,
                        placeholder = stringResource(Res.string.collectifs_search_hint),
                        modifier = Modifier.fillMaxWidth().testTag(CollectivesTags.SEARCH),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PpfPrimaryButton(
                            onClick = { onCreate(voting?.status) },
                            text = stringResource(Res.string.collectifs_create),
                            icon = Lucide.Plus,
                            modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.CREATE),
                        )
                        PpfTextButton(onClick = onOpenRooms, text = stringResource(Res.string.collectifs_rooms_link), modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.ROOMS))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.collectifs_rooms_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    statusText(library.groups.status)?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(CollectivesTags.STATUS))
                    }
                }
            }
            item(key = "filters") {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    if (home.isNotEmpty()) {
                        PpfSelectableChip(
                            label = stringResource(Res.string.collectifs_filter_near),
                            selected = query.nearMe,
                            onClick = library::toggleNearMe,
                            leadingIcon = if (query.nearMe) null else Lucide.MapPin,
                            modifier = Modifier.testTag(CollectivesTags.NEAR),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (themes.isNotEmpty()) {
                        FilterTitle(Res.string.collectifs_filter_theme)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            themes.forEach { theme ->
                                PpfSelectableChip(theme, query.theme == theme, { library.toggleTheme(theme) }, Modifier.testTag(CollectivesTags.THEME + theme))
                            }
                        }
                    }
                    FilterTitle(Res.string.collectifs_filter_type)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CollectiveType.entries.forEach { type ->
                            PpfSelectableChip(stringResource(typeLabel(type)), query.type == type, { library.toggleType(type) }, Modifier.testTag(CollectivesTags.TYPE + type.tagValue))
                        }
                    }
                    FilterTitle(Res.string.collectifs_filter_scope)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CollectiveScope.entries.forEach { scope ->
                            PpfSelectableChip(stringResource(scopeLabel(scope)), query.scope == scope, { library.toggleScope(scope) }, Modifier.testTag(CollectivesTags.SCOPE + scope.tagValue))
                        }
                    }
                    if (query.isFiltered) {
                        Spacer(Modifier.height(4.dp))
                        PpfTextButton(onClick = library::clearFilters, text = stringResource(Res.string.collectifs_filter_clear), modifier = Modifier.testTag(CollectivesTags.CLEAR))
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag(CollectivesTags.EMPTY)) {
                        Spacer(Modifier.height(8.dp))
                        InfoBox(Lucide.Search) {
                            Text(
                                stringResource(if (query.isFiltered) Res.string.collectifs_empty_filtered else Res.string.collectifs_empty),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { collective ->
                CollectiveCard(
                    collective = collective,
                    joined = library.isMember(collective),
                    near = CollectiveQuery.nearRank(collective, home) != null,
                    onOpen = { library.open(collective) },
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FilterTitle(title: StringResource) {
    Spacer(Modifier.height(12.dp))
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}

/** One collectif in the directory: name, [EXEMPLE], type, scope, territory, themes; the whole card opens its page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectiveCard(collective: Collective, joined: Boolean, near: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier.heightIn(min = 48.dp).testTag(CollectivesTags.CARD + collective.id), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(collective.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (collective.example) ExampleBadge()
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PpfBadge(stringResource(typeLabel(collective.type)), BadgeStyle.BRAND)
            PpfBadge(stringResource(scopeLabel(collective.scope)))
            collective.territory?.let { PpfBadge(it) }
            if (near) PpfBadge(stringResource(Res.string.collectifs_card_near), BadgeStyle.SUCCESS)
            if (joined) PpfBadge(stringResource(Res.string.collectifs_card_joined), BadgeStyle.SUCCESS)
        }
        collective.about?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3)
        }
        if (collective.themes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(collective.themes.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.bodySmall, color = palette.brand)
        }
    }
}

/** A page whose collectif the device does not know (any more): a sentence and the way back. */
@Composable
private fun MissingCollective(compact: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(if (compact) 16.dp else 24.dp).testTag(CollectivesTags.MISSING), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.collectifs_back), onBack, Modifier.testTag(CollectivesTags.PAGE_BACK), tint = PpfTheme.palette.ink)
            Spacer(Modifier.height(8.dp))
            InfoBox(Lucide.Info) { Text(stringResource(Res.string.collectifs_missing_text), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun statusText(status: GroupsStatus): String? = when (status) {
    GroupsStatus.Idle, GroupsStatus.Synced -> null
    GroupsStatus.Loading -> stringResource(Res.string.collectifs_status_loading)
    GroupsStatus.Unavailable -> stringResource(Res.string.collectifs_status_unavailable)
    GroupsStatus.Offline -> stringResource(Res.string.collectifs_status_offline)
    GroupsStatus.Unreachable -> stringResource(Res.string.collectifs_status_unreachable)
}

internal fun scopeLabel(scope: CollectiveScope?): StringResource = when (scope) {
    CollectiveScope.NATIONAL -> Res.string.collectifs_scope_national
    CollectiveScope.REGIONAL -> Res.string.collectifs_scope_regional
    CollectiveScope.DEPARTEMENTAL -> Res.string.collectifs_scope_departemental
    CollectiveScope.LOCAL -> Res.string.collectifs_scope_local
    CollectiveScope.QUARTIER -> Res.string.collectifs_scope_quartier
    CollectiveScope.ETABLISSEMENT -> Res.string.collectifs_scope_etablissement
    null -> Res.string.collectifs_scope_unknown
}

internal fun typeLabel(type: CollectiveType): StringResource = when (type) {
    CollectiveType.COLLECTIF -> Res.string.collectifs_type_collectif
    CollectiveType.CLUB -> Res.string.collectifs_type_club
    CollectiveType.COMITE -> Res.string.collectifs_type_comite
    CollectiveType.ASSEMBLEE -> Res.string.collectifs_type_assemblee
    CollectiveType.RESEAU -> Res.string.collectifs_type_reseau
    CollectiveType.AUTRE -> Res.string.collectifs_type_autre
}

/** The languages offered by the wizard (ISO 639-1); a collectif may carry another code, shown as it is. */
internal val LANGUAGES: List<Pair<String, StringResource>> get() = listOf(
    "fr" to Res.string.collectifs_language_fr,
    "en" to Res.string.collectifs_language_en,
    "es" to Res.string.collectifs_language_es,
    "de" to Res.string.collectifs_language_de,
    "it" to Res.string.collectifs_language_it,
    "pt" to Res.string.collectifs_language_pt,
    "ar" to Res.string.collectifs_language_ar,
    "br" to Res.string.collectifs_language_br,
    "oc" to Res.string.collectifs_language_oc,
    "eu" to Res.string.collectifs_language_eu,
)

@Composable
internal fun languageLabel(code: String): String = LANGUAGES.firstOrNull { it.first == code }?.let { stringResource(it.second) } ?: code
