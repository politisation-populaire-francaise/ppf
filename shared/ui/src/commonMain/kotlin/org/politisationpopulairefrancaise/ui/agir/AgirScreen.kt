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
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsScreen
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.groups.GroupsLibrary
import org.politisationpopulairefrancaise.ui.groups.rememberGroupsLibrary
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.messages.CollectivesFullScreen
import org.politisationpopulairefrancaise.ui.messages.CollectivesPage
import org.politisationpopulairefrancaise.ui.messages.collectivesTakeTheScreen
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agir_agenda_title
import org.politisationpopulairefrancaise.ui.resources.agir_back
import org.politisationpopulairefrancaise.ui.resources.agir_calls_title
import org.politisationpopulairefrancaise.ui.resources.agir_collectives_title
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
}

/** A screen of another block plugged into "Agir": it fills [modifier] and offers a way back with [onBack]. */
typealias AgirSlot = @Composable (modifier: Modifier, onBack: () -> Unit) -> Unit

/**
 * The space "Agir" (vision, pillar 3): its home, then each rubric with a way back to it. The
 * agenda and the calls are developed by other blocks and plugged in through [agenda] and [calls];
 * while they are null, their rubric shows "En préparation". The directory of organisations lives
 * here ([OrganisationsScreen]); the collectives are the rooms of "Messages", shown here with the
 * same state ([groups], the shell's). "Guides pour s'organiser" leads to "Comprendre".
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
    // The shell's modifier carries its own test tag: the screens below carry theirs on inner boxes.
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
            AgirRoute.COLLECTIVES -> Collectives(dependencies, groups, session, windowClass, onNavigate, onBack = home)
        }
    }
}

/** The rooms of collectives, as in "Messages", with a way back to "Agir" above the list. */
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
    Column(Modifier.fillMaxSize().testTag(AgirTags.COLLECTIVES)) {
        BackTitle(stringResource(Res.string.agir_collectives_title), onBack, Modifier.padding(start = if (compact) 8.dp else 16.dp, top = 8.dp, end = 16.dp))
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
