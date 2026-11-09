package org.politisationpopulairefrancaise.ui.agir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.rememberAgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.calls.CallsLibrary
import org.politisationpopulairefrancaise.ui.agir.calls.rememberCallsLibrary
import org.politisationpopulairefrancaise.ui.agir.collectives.CollectivesScreen
import org.politisationpopulairefrancaise.ui.agir.collectives.rememberCollectivesLibrary
import org.politisationpopulairefrancaise.ui.agir.manifestation.LocalHelpState
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationScreen
import org.politisationpopulairefrancaise.ui.agir.manifestation.rememberHelpState
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsScreen
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityLibrary
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityScreen
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityTab
import org.politisationpopulairefrancaise.ui.agir.solidarity.rememberSolidarityLibrary
import org.politisationpopulairefrancaise.ui.agir.testimonies.ProvideTestify
import org.politisationpopulairefrancaise.ui.agir.testimonies.TestimonyLibrary
import org.politisationpopulairefrancaise.ui.agir.testimonies.TestimonyScreen
import org.politisationpopulairefrancaise.ui.agir.testimonies.rememberTestimonyLibrary
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.groups.GroupsLibrary
import org.politisationpopulairefrancaise.ui.groups.rememberGroupsLibrary
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.messages.CollectivesFullScreen
import org.politisationpopulairefrancaise.ui.messages.CollectivesPage
import org.politisationpopulairefrancaise.ui.messages.collectivesTakeTheScreen
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agir_agenda_title
import org.politisationpopulairefrancaise.ui.resources.agir_back
import org.politisationpopulairefrancaise.ui.resources.agir_calls_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_rooms_title
import org.politisationpopulairefrancaise.ui.resources.agir_pending_text
import org.politisationpopulairefrancaise.ui.resources.agir_pending_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Where the user is inside "Agir". The ids are stable (routes, tests). */
enum class AgirRoute(val id: String) {
    HOME("accueil"),
    AGENDA("agenda"),
    CALLS("appels"),
    ORGANISATIONS("organisations"),
    COLLECTIVES("collectifs"),

    /** The plain rooms of "Messages" (salons), open to all, reached from the directory of the collectifs (M24). */
    ROOMS("salons"),
    TESTIFY("temoigner"),
    MANIFESTATION("mode-manifestation"),

    /** M35: the solidarity funds (AG-10) and the volunteer missions (AG-12), one rubric with tabs. */
    SOLIDARITY("solidarite"),
}

/** A screen of another block plugged into "Agir": it fills [modifier] and offers a way back with [onBack]. */
typealias AgirSlot = @Composable (modifier: Modifier, onBack: () -> Unit) -> Unit

/**
 * The space "Agir" (vision, pillar 3): its home, then each rubric with a way back to it. The
 * agenda and the calls are developed by other blocks and plugged in through [agenda] and [calls];
 * while they are null, their rubric shows "En préparation". The directory of organisations lives
 * here ([OrganisationsScreen]); the collectifs (M24, D-087) are rooms of "Messages" carrying a
 * marker, with their own directory, page and creation ([CollectivesScreen]) on the same rooms
 * ([groups], the shell's); the plain rooms stay one tap away ([AgirRoute.ROOMS]). "Guides pour
 * s'organiser" leads to "Comprendre".
 *
 * [organisations] is the directory shared with the other blocks (its [OrganisationsLibrary.verified]
 * keys mark the organisations' publications); null builds one for as long as "Agir" is shown.
 * Calling [OrganisationsLibrary.openKey] from a plugged screen opens the organisation's page; its
 * back button then returns to that screen. [organisationAgenda] fills the agenda of an
 * organisation's page with its key.
 */
@Composable
fun AgirScreen(
    dependencies: AppDependencies,
    library: ContentLibrary,
    session: AccountSession,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onNavigate: (Space) -> Unit = {},
    organisations: OrganisationsLibrary? = null,
    groups: GroupsLibrary? = null,
    agenda: AgirSlot? = null,
    calls: AgirSlot? = null,
    organisationAgenda: (@Composable (pubkeyHex: String, modifier: Modifier) -> Unit)? = null,
    /** "Témoigner" (AG-07); null builds one for as long as "Agir" is shown. */
    testimonies: TestimonyLibrary? = null,
    /** Solidarity and "Je veux aider" (M35); null builds one for as long as "Agir" is shown. */
    solidarity: SolidarityLibrary? = null,
    /** The agenda and the calls of the shell, whose events and calls may be a collectif's (M24); null builds them. */
    agendaLibrary: AgendaLibrary? = null,
    callsLibrary: CallsLibrary? = null,
) {
    var route by rememberSaveable { mutableStateOf(AgirRoute.HOME) }
    // Where the back button of the directory leads when a plugged screen opened an organisation's page.
    var returnTo by rememberSaveable { mutableStateOf<AgirRoute?>(null) }
    val directory = organisations ?: rememberOrganisationsLibrary(dependencies)
    val selectedOrganisation = directory.selected
    LaunchedEffect(selectedOrganisation?.address) {
        if (selectedOrganisation != null && route != AgirRoute.ORGANISATIONS) {
            returnTo = route
            route = AgirRoute.ORGANISATIONS
        }
    }
    val home: () -> Unit = { route = AgirRoute.HOME }
    val testimonyLibrary = testimonies ?: rememberTestimonyLibrary(dependencies, start = false)
    // The collectifs (M24) live on the rooms of the shell; their events and calls on its agenda and its calls.
    val rooms = groups ?: rememberGroupsLibrary(dependencies)
    val collectives = rememberCollectivesLibrary(dependencies, rooms)
    val agendaOfCollectives = agendaLibrary ?: rememberAgendaLibrary(dependencies, start = false)
    val callsOfCollectives = callsLibrary ?: rememberCallsLibrary(dependencies, start = false)
    val solidarityLibrary = solidarity ?: rememberSolidarityLibrary(dependencies)
    // The protest mode shares the AIDE button's state (rally point, legal teams); alone (tests), its own.
    val help = LocalHelpState.current ?: rememberHelpState(dependencies, directory, library)
    // "Je témoigne" of a call for witnesses opens the form linked to the call; back returns to the calls.
    val testify: (String, String) -> Unit = { address, title ->
        testimonyLibrary.answer(address, title)
        returnTo = route.takeIf { it != AgirRoute.TESTIFY }
        route = AgirRoute.TESTIFY
    }
    // The system's back first closes the open page of an organisation, then returns to the home of Agir.
    SystemBack(enabled = route != AgirRoute.HOME) {
        if (route == AgirRoute.ORGANISATIONS && directory.selected != null) {
            directory.close()
            returnTo?.let { route = it }
            returnTo = null
        } else if (route == AgirRoute.TESTIFY && returnTo != null) {
            route = returnTo ?: AgirRoute.HOME
            returnTo = null
        } else if (route == AgirRoute.ROOMS) {
            // The plain rooms were opened from the directory of the collectifs: back to it.
            route = AgirRoute.COLLECTIVES
        } else if (route == AgirRoute.SOLIDARITY && solidarityLibrary.hasSelection) {
            // The page of a fund or a mission closes first, back to the list.
            solidarityLibrary.closeSelection()
        } else {
            route = AgirRoute.HOME
            returnTo = null
        }
    }
    // The shell's modifier carries its own test tag: the screens below carry theirs on inner boxes.
    // "Je témoigne" of the calls reads LocalTestify, provided here for every screen of Agir.
    ProvideTestify(testify) {
        Box(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
            when (route) {
                AgirRoute.HOME -> AgirHome(
                    windowClass = windowClass,
                    onOpen = { rubric ->
                        when (rubric.id) {
                            AgirRubrics.AGENDA.id -> route = AgirRoute.AGENDA
                            AgirRubrics.CALLS.id -> route = AgirRoute.CALLS
                            AgirRubrics.ORGANISATIONS.id -> route = AgirRoute.ORGANISATIONS
                            AgirRubrics.COLLECTIVES.id -> route = AgirRoute.COLLECTIVES
                            AgirRubrics.TESTIFY.id -> route = AgirRoute.TESTIFY
                            AgirRubrics.MANIFESTATION.id -> route = AgirRoute.MANIFESTATION
                            AgirRubrics.SOLIDARITY.id -> {
                                solidarityLibrary.selectTab(SolidarityTab.FUNDS)
                                route = AgirRoute.SOLIDARITY
                            }
                            AgirRubrics.HELP.id -> {
                                solidarityLibrary.selectTab(SolidarityTab.MISSIONS)
                                route = AgirRoute.SOLIDARITY
                            }
                            AgirRubrics.GUIDES.id -> onNavigate(Space.COMPRENDRE)
                        }
                    },
                )
                AgirRoute.AGENDA -> if (agenda != null) agenda(Modifier.fillMaxSize(), home) else PendingRubric(Res.string.agir_agenda_title, windowClass, home)
                AgirRoute.CALLS -> if (calls != null) calls(Modifier.fillMaxSize(), home) else PendingRubric(Res.string.agir_calls_title, windowClass, home)
                AgirRoute.ORGANISATIONS -> OrganisationsScreen(
                    library = directory,
                    windowClass = windowClass,
                    isFollowed = library::isFollowed,
                    onToggleFollow = library::toggleFollow,
                    onBack = {
                        directory.close()
                        route = returnTo ?: AgirRoute.HOME
                        returnTo = null
                    },
                    notesKey = library.posts,
                    organisationAgenda = organisationAgenda,
                )
                AgirRoute.COLLECTIVES -> CollectivesScreen(
                    library = collectives,
                    session = session,
                    windowClass = windowClass,
                    agenda = agendaOfCollectives,
                    calls = callsOfCollectives,
                    onBack = home,
                    onNavigate = onNavigate,
                    onOpenRooms = { route = AgirRoute.ROOMS },
                    onCreateRoom = {
                        rooms.beginCreation("")
                        route = AgirRoute.ROOMS
                    },
                    onOpenMobilization = { mobilization ->
                        agendaOfCollectives.open(mobilization)
                        route = AgirRoute.AGENDA
                    },
                    onOpenCall = { call ->
                        callsOfCollectives.open(call)
                        route = AgirRoute.CALLS
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                AgirRoute.ROOMS -> Collectives(dependencies, rooms, session, windowClass, onNavigate, onBack = { route = AgirRoute.COLLECTIVES })
                AgirRoute.MANIFESTATION -> ManifestationScreen(help, windowClass, onBack = home)
                AgirRoute.SOLIDARITY -> SolidarityScreen(
                    library = solidarityLibrary,
                    directory = directory,
                    windowClass = windowClass,
                    onBack = {
                        solidarityLibrary.closeSelection()
                        route = AgirRoute.HOME
                    },
                    // The page of the organisation opens in the directory; its way back returns here.
                    onOpenOrganisation = directory::open,
                    onOpenCalls = { route = AgirRoute.CALLS },
                )
                AgirRoute.TESTIFY -> TestimonyScreen(
                    library = testimonyLibrary,
                    session = session,
                    windowClass = windowClass,
                    onBack = {
                        route = returnTo ?: AgirRoute.HOME
                        returnTo = null
                    },
                    onCreateAccount = { onNavigate(Space.MON_ESPACE) },
                )
            }
        }
    }
}

/** The plain rooms (salons), as in "Messages", with a way back to the directory of the collectifs above the list. */
@Composable
private fun Collectives(
    dependencies: AppDependencies,
    groups: GroupsLibrary?,
    session: AccountSession,
    windowClass: WindowClass,
    onNavigate: (Space) -> Unit,
    onBack: () -> Unit,
) {
    val rooms = groups ?: rememberGroupsLibrary(dependencies)
    // On phones and tablets an open room, or the creation of one, takes the whole screen with its own way back.
    if (collectivesTakeTheScreen(rooms, windowClass)) {
        CollectivesFullScreen(rooms, session, windowClass, onNavigate, Modifier.fillMaxSize())
        return
    }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    Column(Modifier.fillMaxSize().testTag(AgirTags.ROOMS)) {
        BackTitle(stringResource(Res.string.collectifs_rooms_title), onBack, Modifier.padding(start = if (compact) 8.dp else 16.dp, top = 8.dp, end = 16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CollectivesPage(rooms, session, windowClass, onNavigate)
        }
    }
}

@Composable
private fun BackTitle(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.agir_back), onBack, Modifier.testTag(AgirTags.BACK), tint = PpfTheme.palette.ink)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    }
}

/**
 * Frames a screen of another block plugged into "Agir" ([AgirSlot]): its [title] with the way back
 * to the home of the space, then the screen itself, which fills the rest.
 */
@Composable
fun AgirPlugged(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    Column(modifier) {
        BackTitle(title, onBack, Modifier.padding(start = 8.dp, top = 8.dp, end = 16.dp))
        content(Modifier.fillMaxWidth().weight(1f))
    }
}

/** A rubric whose screen is not plugged in yet: its name, "En préparation" and the way back. */
@Composable
private fun PendingRubric(title: StringResource, windowClass: WindowClass, onBack: () -> Unit) {
    val compact = windowClass.width == WindowWidthClass.COMPACT
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(AgirTags.PENDING)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.agir_back), onBack, Modifier.testTag(AgirTags.BACK), tint = PpfTheme.palette.ink)
            Spacer(Modifier.height(8.dp))
            PageHeader(stringResource(title))
            Spacer(Modifier.height(16.dp))
            InfoBox(Lucide.Info) {
                Text(stringResource(Res.string.agir_pending_title), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.agir_pending_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
