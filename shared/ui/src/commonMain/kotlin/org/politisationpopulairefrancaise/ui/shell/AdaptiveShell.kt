package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.core.comprendre.ComprendreDeviceData
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.ui.endirect.EnDirectScreen
import org.politisationpopulairefrancaise.ui.voir.VoirRequestSession
import org.politisationpopulairefrancaise.ui.voir.VoirScreen
import org.politisationpopulairefrancaise.ui.voir.parlement.ParliamentState
import org.politisationpopulairefrancaise.ui.voir.parlement.ParliamentWeekSlot
import org.politisationpopulairefrancaise.ui.voir.parlement.rememberParliamentState
import org.politisationpopulairefrancaise.ui.voir.parlement.rememberParliamentWeekState
import org.politisationpopulairefrancaise.ui.voir.rememberVoirRequestSession
import org.politisationpopulairefrancaise.ui.voir.VoirState
import org.politisationpopulairefrancaise.ui.voir.rememberVoirState
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.account.MailboxSetting
import org.politisationpopulairefrancaise.ui.account.MySpaceScreen
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.account.rememberMailboxSetting
import org.politisationpopulairefrancaise.ui.agir.AgirScreen
import org.politisationpopulairefrancaise.ui.agir.AgirPlugged
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaScreen
import org.politisationpopulairefrancaise.ui.agir.agenda.OrganisationAgenda
import org.politisationpopulairefrancaise.ui.agir.agenda.UpcomingMobilizations
import org.politisationpopulairefrancaise.ui.agir.agenda.rememberAgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.testimonies.TestimonyLibrary
import org.politisationpopulairefrancaise.ui.agir.testimonies.rememberTestimonyLibrary
import org.politisationpopulairefrancaise.ui.agir.calls.CallsLibrary
import org.politisationpopulairefrancaise.ui.account.ProvidePublishing
import org.politisationpopulairefrancaise.ui.agir.manifestation.HelpButton
import org.politisationpopulairefrancaise.ui.agir.manifestation.HelpSheetHost
import org.politisationpopulairefrancaise.ui.agir.manifestation.ProvideHelp
import org.politisationpopulairefrancaise.ui.agir.manifestation.rememberHelpState
import org.politisationpopulairefrancaise.ui.agir.security.DeviceSecurityGate
import org.politisationpopulairefrancaise.ui.agir.security.LocalPreparePhoneNotifications
import org.politisationpopulairefrancaise.ui.agir.security.ProvideDeviceSecurity
import org.politisationpopulairefrancaise.ui.agir.security.RegisterErasable
import org.politisationpopulairefrancaise.ui.agir.timer.rememberTimerState
import org.politisationpopulairefrancaise.ui.agir.camera.rememberProtectedCameraState
import org.politisationpopulairefrancaise.core.voir.VoirDeviceData
import org.politisationpopulairefrancaise.ui.agir.security.rememberDeviceSecurity
import org.politisationpopulairefrancaise.ui.agir.calls.CallsScreen
import org.politisationpopulairefrancaise.ui.agir.calls.rememberCallsLibrary
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.resources.agenda_in_agir_title
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.NavRow
import org.politisationpopulairefrancaise.ui.components.NavSubRow
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.Wordmark
import org.politisationpopulairefrancaise.ui.components.avatarLetters
import org.politisationpopulairefrancaise.ui.comprendre.rememberComprendreLibrary
import org.politisationpopulairefrancaise.ui.content.ComprendreScreen
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.feed.FeedScreen
import org.politisationpopulairefrancaise.ui.glossaire.ProvideGlossary
import org.politisationpopulairefrancaise.ui.glossaire.rememberGlossaryState
import org.politisationpopulairefrancaise.ui.groups.GroupsLibrary
import org.politisationpopulairefrancaise.ui.groups.rememberGroupsLibrary
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.messages.MessagesScreen
import org.politisationpopulairefrancaise.ui.messages.MessagingSession
import org.politisationpopulairefrancaise.ui.messages.rememberMessagingSession
import org.politisationpopulairefrancaise.ui.revendications.RevendicationViewsState
import org.politisationpopulairefrancaise.ui.revendications.RevendicationsScreen
import org.politisationpopulairefrancaise.ui.revendications.rememberRevendicationViewsState
import org.politisationpopulairefrancaise.ui.revendications.propose.ProposalComposer
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.action_my_space
import org.politisationpopulairefrancaise.ui.resources.action_notifications_soon
import org.politisationpopulairefrancaise.ui.resources.action_open_menu
import org.politisationpopulairefrancaise.ui.resources.action_search_soon
import org.politisationpopulairefrancaise.ui.resources.guides_title
import org.politisationpopulairefrancaise.ui.resources.nav_disclaimer
import org.politisationpopulairefrancaise.ui.resources.nav_search_soon
import org.politisationpopulairefrancaise.ui.resources.space_mon_espace
import org.politisationpopulairefrancaise.ui.decider.DeciderScreen
import org.politisationpopulairefrancaise.ui.decider.DeciderState
import org.politisationpopulairefrancaise.ui.decider.rememberDeciderState
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.core.notifications.AccountAlerts
import org.politisationpopulairefrancaise.core.notifications.NotificationEntry
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.notifications.NotificationCenterScreen
import org.politisationpopulairefrancaise.ui.notifications.NotificationsSettingsState
import org.politisationpopulairefrancaise.ui.notifications.rememberNotificationCenter
import org.politisationpopulairefrancaise.ui.notifications.rememberNotificationsSettings
import org.politisationpopulairefrancaise.ui.resources.action_notifications
import org.politisationpopulairefrancaise.ui.resources.notif_bell_unread_many
import org.politisationpopulairefrancaise.ui.resources.notif_bell_unread_one
import org.politisationpopulairefrancaise.ui.urne.LocalVoting
import org.politisationpopulairefrancaise.ui.urne.RunVoting
import org.politisationpopulairefrancaise.ui.urne.VotingState
import org.politisationpopulairefrancaise.ui.urne.rememberVotingState

/** Test tags, also useful for accessibility tooling. */
object ShellTags {
    const val SHELL_COMPACT = "shell-compact"
    const val SHELL_MEDIUM = "shell-medium"
    const val SHELL_EXPANDED = "shell-expanded"
    const val MENU_BUTTON = "nav-menu-button"
    const val BOTTOM_BAR = "nav-bottom-bar"
    const val NAV_RAIL = "nav-rail"
    const val NAV_DRAWER = "nav-side-bar"
    const val CONTENT = "content"

    /** The bell of the top bar, which opens the notification centre. */
    const val NOTIFICATIONS = "nav-notifications"

    /** The navigation entry of a space, wherever it is shown. */
    fun navItem(space: Space): String = "nav-${space.id}"

    /** A secondary entry under a pillar (for now: the practical guides under "Comprendre"). */
    fun subItem(id: String): String = "nav-sub-$id"
}

/**
 * The application shell, adapted to the window size (cahier fonctionnel, S-05 and M18), after
 * the owner's mock-ups of 2026-11-06:
 *
 * - compact width (phone): a 56 dp top bar (menu, wordmark, search, notifications, avatar of the
 *   account that opens "Mon espace"), a fixed bottom bar with the daily entries (feed, live, map,
 *   messages, demands), the four pillars in a menu opened over the screen;
 * - medium width (tablet, phone in landscape): the same top bar without the menu button and a
 *   rail on the left with every entry;
 * - expanded width (desktop): a 64 dp top bar with the search field and the "Mon espace" button,
 *   a 280 dp side bar with the daily entries, then the pillars, then the disclaimer.
 */
@Composable
fun AdaptiveShell(dependencies: AppDependencies) {
    var selected by rememberSaveable { mutableStateOf(Space.default) }
    val contentLibrary = rememberContentLibrary(dependencies)
    // The private conversations, kept for the whole run like the library (the open one survives a visit elsewhere).
    val messaging = rememberMessagingSession(dependencies)
    // The rooms of collectives, kept for the whole run (nothing is fetched before the view is shown).
    val groupsLibrary = rememberGroupsLibrary(dependencies)
    // "Agir": the directory of organisations and the agenda, kept for the whole run; the agenda is
    // shown behind "Carte" and inside "Agir", the directory names the organisers of its events.
    val organisationsLibrary = rememberOrganisationsLibrary(dependencies)
    // Started when their screens are first shown, as the rooms are: nothing is loaded before.
    val agendaLibrary = rememberAgendaLibrary(dependencies, start = false)
    val callsLibrary = rememberCallsLibrary(dependencies, start = false)
    // The notification centre (M31), filled while the application is open: a round at start, then every minute.
    val notificationCenter = rememberNotificationCenter(dependencies)
    var centerOpen by rememberSaveable { mutableStateOf(false) }
    val shellScope = rememberCoroutineScope()
    // Anonymous voting (D-110): the boxes, the tokens and the choices of the device, kept for the whole run.
    val voting = rememberVotingState(dependencies)
    val testimonyLibrary = rememberTestimonyLibrary(dependencies, start = false)
    // The AIDE button (M25, AG-27): its emergency sheet over every screen, its rally point on the device.
    val help = rememberHelpState(dependencies, organisationsLibrary, contentLibrary)
    // Erasing the keys also forgets the followed accounts, the conversations and the joined rooms (protection by default).
    val accountSession = rememberAccountSession(dependencies.identityStore, remote = dependencies.remote, onSignOut = {
        voting.forget()
        contentLibrary.clearFollowed()
        messaging.forgetAll()
        groupsLibrary.clearMemberships()
        agendaLibrary.forgetUser()
        callsLibrary.clearResponses()
        // The notifications go back to the most protective settings and the server forgets the wake-ups.
        dependencies.notifications?.forget()
        notificationCenter?.reload()
        dependencies.push?.let { push -> shellScope.launch { runCatching { push.clear() } } }
        testimonyLibrary.forgetAll()
    })
    // M33: the lock of the application, the content hidden from the system and the quick erase (S-43 to S-45).
    val security = rememberDeviceSecurity(dependencies)
    // The quick erase (S-44): the keys, with what « Effacer mes clés » already forgets (D-032, D-039), and the rally point.
    RegisterErasable(dependencies, "core-identity") {
        dependencies.identityStore.clear()
        accountSession.signOut()
    }
    RegisterErasable(dependencies, "agir-rally-point") { help.clearRallyPoint() }
    // AG-55 « Sans nouvelles de moi » (D-088): the timer armed here and the deposits received; erasing never cancels a deposit.
    val timer = rememberTimerState(dependencies, help, accountSession, messaging)
    RegisterErasable(dependencies, "agir-minuteur") { timer.eraseFromDevice() }
    // AG-58 « Caméra protégée » (D-089): the photos of the vault; erasing destroys the Keystore key, then the files.
    val protectedCamera = rememberProtectedCameraState()
    help.camera = protectedCamera
    RegisterErasable(dependencies, "agir-camera") { protectedCamera.eraseFromDevice() }
    // « Comprendre » (S-44): progress of the courses, chosen commune and directory packages, department packages of the territories.
    dependencies.database?.let { database -> remember(database) { ComprendreDeviceData.erasables(database) }.forEach { RegisterErasable(dependencies, it) } }
    // Décider: the voting tokens, pending requests and choices of the device (S-44).
    RegisterErasable(dependencies, VotingState.ERASABLE_ID) { voting.eraseFromDevice() }
    // The views of the demands wall, kept for the whole run so that their filters survive a visit elsewhere.
    val revendicationViews = rememberRevendicationViewsState(dependencies.database, accountSession)
    // "Voir": the open page, the axis and the follows survive a visit to another space.
    val voirState = rememberVoirState(dependencies)
    // Its parliamentary open data (D-065): the open page and the filters survive a visit elsewhere too.
    val parliamentState = rememberParliamentState(dependencies)
    // « Cette semaine au Parlement » (VO-31), attached to it: shown in its screen and in « En direct ».
    rememberParliamentWeekState(dependencies, parliamentState, contentLibrary, voirState) { selected = it }
    // Its requests to the editorial team: drafts and anonymous reports waiting for a server.
    val voirRequests = rememberVoirRequestSession(dependencies, messaging)
    // The quick erase (S-44): what « Voir » keeps on the device (follows, views of the demands wall, parliamentary
    // open data) and in memory (drafts, anonymous reports waiting with their single-use keys).
    RegisterErasable(dependencies, VoirDeviceData.ID) {
        dependencies.database?.let { VoirDeviceData(it).eraseFromDevice() }
        voirState.forgetFollows()
        revendicationViews.reset()
        voirRequests.forget()
    }
    // "Décider": the open page and the follows survive a visit to another space.
    val deciderState = rememberDeciderState(dependencies)
    // The form to propose a demand: its draft survives a visit to another space (to create an account, for example).
    val proposalComposer = remember(dependencies) { ProposalComposer(dependencies.contentStore, dependencies.remote) }
    val accountKey = (accountSession.state as? AccountUiState.Ready)?.publicKey?.hex
    // Its two tasks, apart from each other: tokens with the account, ballots without it.
    RunVoting(voting, accountSession, dependencies.dataSaver)
    LaunchedEffect(accountKey) {
        contentLibrary.setAccount(accountKey)
        callsLibrary.setAccount(accountKey)
        messaging.setAccount(accountSession.messagingSigner)
    }
    // A compromised account no longer writes (S-146): the conversations say why, and the alert joins
    // the notification centre even while the category « Mon compte » is off.
    LaunchedEffect(accountSession.compromise) {
        val compromised = accountSession.compromise
        messaging.markOwnCompromised(compromised != null)
        val key = accountSession.accountKey
        if (compromised != null && key != null) notificationCenter?.accountCompromised(key, currentEpochSeconds())
    }
    // How long the server keeps the account's private messages (Mon espace, D-028).
    val mailboxSetting = rememberMailboxSetting(dependencies, accountKey)
    // The notification settings (Mon espace, M31); null without notifications.
    val notificationsSettings = rememberNotificationsSettings(dependencies, accountKey)
    // A change of settings (details shown, a category enabled) is reflected at once in the centre.
    LaunchedEffect(notificationsSettings?.settings) { notificationCenter?.reload() }
    // The glossary (D-048): read once the bundled contents are imported and after each synchronization,
    // provided to every screen so that the terms of every content open their definition.
    val glossary = rememberGlossaryState(dependencies)
    LaunchedEffect(contentLibrary.status) { glossary.reload() }
    // « Ouvrir le glossaire complet » and the related contents of an entry live in "Comprendre".
    LaunchedEffect(glossary.wantsComprendre) {
        if (glossary.wantsComprendre) {
            selected = Space.COMPRENDRE
            glossary.navigationDone()
        }
    }
    val palette = PpfTheme.palette
    // The system's back leads from any space to the feed; the screens close what they opened first.
    SystemBack(enabled = selected != Space.default) { selected = Space.default }

    ProvideGlossary(glossary) { ProvideHelp(help) { ProvidePublishing(accountSession) { CompositionLocalProvider(LocalVoting provides voting, LocalPreparePhoneNotifications provides notificationsSettings) { ProvideDeviceSecurity(security) { DeviceSecurityGate(security) {
        BoxWithConstraints(Modifier.fillMaxSize().background(palette.surface1)) {
            val windowClass = WindowClass.of(widthDp = maxWidth.value, heightDp = maxHeight.value)
            val account = accountSession.state as? AccountUiState.Ready
            val navigate: (Space) -> Unit = {
                selected = it
                centerOpen = false
            }
            // An entry of the centre opens what it points to when the shell knows the way; otherwise it is only marked read.
            val openEntry: (NotificationEntry) -> Unit = { entry ->
                notificationCenter?.markRead(entry.id)
                val conversation = entry.target?.takeIf { it.startsWith(MESSAGES_TARGET) }?.removePrefix(MESSAGES_TARGET)
                if (conversation != null) {
                    messaging.conversations.firstOrNull { it.id == conversation }?.conversation?.let(messaging::open)
                    navigate(Space.MESSAGES)
                } else if (entry.target == AccountAlerts.MY_SPACE_TARGET) {
                    navigate(Space.MON_ESPACE)
                }
            }
            val bell = notificationCenter?.let { center -> Bell(center.unreadCount) { centerOpen = true } }
            val center = notificationCenter
            val centerScreen: (@Composable (Modifier) -> Unit)? = if (center != null && centerOpen) {
                { modifier ->
                    NotificationCenterScreen(
                        center = center,
                        windowClass = windowClass,
                        onBack = { centerOpen = false },
                        onOpen = openEntry,
                        onSettings = { navigate(Space.MON_ESPACE) },
                        modifier = modifier.testTag(ShellTags.CONTENT),
                    )
                }
            } else {
                null
            }
            val content: @Composable (Modifier) -> Unit = { modifier ->
                SpaceContent(
                    dependencies = dependencies,
                    selected = selected,
                    onNavigate = navigate,
                    accountSession = accountSession,
                    contentLibrary = contentLibrary,
                    messaging = messaging,
                    revendicationViews = revendicationViews,
                    proposalComposer = proposalComposer,
                    voirState = voirState,
                    parliamentState = parliamentState,
                    voirRequests = voirRequests,
                    deciderState = deciderState,
                    groupsLibrary = groupsLibrary,
                    organisationsLibrary = organisationsLibrary,
                    agendaLibrary = agendaLibrary,
                    callsLibrary = callsLibrary,
                    testimonyLibrary = testimonyLibrary,
                    mailboxSetting = mailboxSetting,
                    notificationsSettings = notificationsSettings,
                    windowClass = windowClass,
                    modifier = modifier.testTag(ShellTags.CONTENT),
                )
            }
            when {
                // On phones the centre takes the whole screen, with its back button.
                windowClass.width == WindowWidthClass.COMPACT && centerScreen != null -> centerScreen(Modifier)
                windowClass.width == WindowWidthClass.COMPACT -> CompactShell(selected, navigate, account, bell, content)
                windowClass.width == WindowWidthClass.MEDIUM -> MediumShell(selected, navigate, account, bell, centerScreen ?: content)
                else -> ExpandedShell(selected, navigate, account, bell, centerScreen ?: content)
            }
            HelpSheetHost(help)
        }
    } } } } } }
}

@Composable
private fun SpaceContent(
    dependencies: AppDependencies,
    selected: Space,
    onNavigate: (Space) -> Unit,
    accountSession: AccountSession,
    contentLibrary: ContentLibrary,
    messaging: MessagingSession,
    revendicationViews: RevendicationViewsState,
    proposalComposer: ProposalComposer,
    voirState: VoirState,
    parliamentState: ParliamentState,
    voirRequests: VoirRequestSession,
    deciderState: DeciderState,
    groupsLibrary: GroupsLibrary,
    organisationsLibrary: OrganisationsLibrary,
    agendaLibrary: AgendaLibrary,
    callsLibrary: CallsLibrary,
    testimonyLibrary: TestimonyLibrary,
    mailboxSetting: MailboxSetting,
    notificationsSettings: NotificationsSettingsState?,
    windowClass: WindowClass,
    modifier: Modifier,
) {
    // The organisers of the agenda are named after the directory when they are verified organisations.
    val organisationName: (String) -> String? = { key -> organisationsLibrary.verified.organisationFor(key)?.name }
    when (selected) {
        Space.FIL -> FeedScreen(contentLibrary, accountSession, windowClass, onNavigate, modifier)
        // The coming mobilisations will plug the agenda of "Agir" into EnDirectScreen(upcoming = ...).
        Space.EN_DIRECT -> EnDirectScreen(
            contentLibrary, voirState, modifier, onNavigate,
            upcoming = { slotModifier ->
                UpcomingMobilizations(agendaLibrary, slotModifier) { mobilization ->
                    agendaLibrary.open(mobilization)
                    onNavigate(Space.CARTE)
                }
            },
            parliamentWeek = { slotModifier -> ParliamentWeekSlot(parliamentState, slotModifier) },
        )
        Space.VOIR -> VoirScreen(contentLibrary, voirState, windowClass, modifier, onNavigate, requests = voirRequests, parliament = parliamentState)
        Space.COMPRENDRE -> ComprendreScreen(contentLibrary, rememberComprendreLibrary(dependencies), windowClass, modifier, onNavigate)
        Space.AGIR -> {
            // The verified organisations name the organisers of the agenda and may call for donations.
            LaunchedEffect(organisationsLibrary) { organisationsLibrary.ensureStarted() }
            AgirScreen(
                dependencies, contentLibrary, accountSession, windowClass, modifier, onNavigate,
                organisations = organisationsLibrary,
                groups = groupsLibrary,
                agenda = { slotModifier, onBack ->
                    AgirPlugged(stringResource(Res.string.agenda_in_agir_title), onBack, slotModifier) { inner ->
                        AgendaScreen(dependencies, accountSession, windowClass, inner, onNavigate, agenda = agendaLibrary, organisationName = organisationName)
                    }
                },
                calls = { slotModifier, onBack ->
                    CallsScreen(
                        callsLibrary, accountSession, windowClass, onBack, slotModifier,
                        // Verified organisations and PPF itself may call for donations (decision of the owner, 7 November 2026).
                        isVerifiedOrganisation = { key -> organisationsLibrary.verified.isVerified(key) || dependencies.editorialTrust.isTrusted(key) },
                        onCreateAccount = { onNavigate(Space.MON_ESPACE) },
                    )
                },
                testimonies = testimonyLibrary,
                // The events and calls of the collectifs (M24) are those of the agenda and of the calls.
                agendaLibrary = agendaLibrary,
                callsLibrary = callsLibrary,
                organisationAgenda = { key, slotModifier ->
                    OrganisationAgenda(agendaLibrary, key, slotModifier) { mobilization ->
                        agendaLibrary.open(mobilization)
                        onNavigate(Space.CARTE)
                    }
                },
            )
        }
        Space.DECIDER -> DeciderScreen(
            contentLibrary, deciderState, windowClass, modifier, onNavigate,
            // A candidate, a bill or a fact opens in "Voir", where each has one sheet.
            onOpenVoir = { address ->
                voirState.open(address)
                onNavigate(Space.VOIR)
            },
            dependencies = dependencies,
        )
        Space.MON_ESPACE -> MySpaceScreen(accountSession, windowClass, modifier, mailbox = mailboxSetting, notifications = notificationsSettings)
        Space.MESSAGES -> MessagesScreen(dependencies, accountSession, contentLibrary, messaging, windowClass, modifier, onNavigate, groups = groupsLibrary)
        Space.REVENDICATIONS -> RevendicationsScreen(
            contentLibrary,
            accountSession,
            windowClass,
            modifier,
            views = revendicationViews,
            proposal = proposalComposer,
            onNavigate = onNavigate,
        )
        Space.CARTE -> {
            // The directory names the verified organisers of the agenda.
            LaunchedEffect(organisationsLibrary) { organisationsLibrary.ensureStarted() }
            AgendaScreen(dependencies, accountSession, windowClass, modifier, onNavigate, agenda = agendaLibrary, organisationName = organisationName)
        }
    }
}

@Composable
private fun CompactShell(
    selected: Space,
    onSelect: (Space) -> Unit,
    account: AccountUiState.Ready?,
    bell: Bell?,
    content: @Composable (Modifier) -> Unit,
) {
    val palette = PpfTheme.palette
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = Modifier.testTag(ShellTags.SHELL_COMPACT),
        scrimColor = palette.scrim,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = palette.surface0, drawerContentColor = palette.ink) {
                PillarsMenu(selected = selected, onSelect = {
                    onSelect(it)
                    scope.launch { drawerState.close() }
                })
            }
        },
    ) {
        Scaffold(
            containerColor = palette.surface1,
            topBar = {
                TopBar(
                    onMenu = { scope.launch { drawerState.open() } },
                    account = account,
                    bell = bell,
                    mySpaceSelected = selected == Space.MON_ESPACE,
                    onMySpace = { onSelect(Space.MON_ESPACE) },
                )
            },
            bottomBar = { BottomBar(selected, onSelect) },
        ) { padding ->
            content(Modifier.padding(padding))
        }
    }
}

@Composable
private fun BottomBar(selected: Space, onSelect: (Space) -> Unit) {
    val palette = PpfTheme.palette
    Column(Modifier.background(palette.surface0)) {
        HorizontalDivider(color = palette.border)
        Row(Modifier.fillMaxWidth().height(76.dp).selectableGroup().testTag(ShellTags.BOTTOM_BAR)) {
            quickAccessSections.forEach { section ->
                BottomBarItem(section, section.space == selected, { onSelect(section.space) }, Modifier.weight(1f))
            }
        }
    }
}

/** One entry of the bottom bar: a pill behind the icon when selected, a two-line label at most. */
@Composable
private fun BottomBarItem(section: SectionSpec, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = PpfTheme.palette
    val color = if (active) palette.brand else palette.inkMuted
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .testTag(ShellTags.navItem(section.space))
            .padding(top = 10.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(width = 56.dp, height = 30.dp).clip(CircleShape).background(if (active) palette.brandTint else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(section.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(section.barLabel),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp),
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun MediumShell(
    selected: Space,
    onSelect: (Space) -> Unit,
    account: AccountUiState.Ready?,
    bell: Bell?,
    content: @Composable (Modifier) -> Unit,
) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxSize().testTag(ShellTags.SHELL_MEDIUM)) {
        TopBar(onMenu = null, account = account, bell = bell, mySpaceSelected = selected == Space.MON_ESPACE, onMySpace = { onSelect(Space.MON_ESPACE) })
        Row(Modifier.weight(1f).fillMaxWidth()) {
            NavigationRail(
                containerColor = palette.surface0,
                contentColor = palette.inkMuted,
                modifier = Modifier.testTag(ShellTags.NAV_RAIL).verticalScroll(rememberScrollState()),
                header = { HelpButton(Modifier.padding(top = 8.dp).width(68.dp), stacked = true) },
            ) {
                Spacer(Modifier.height(8.dp))
                quickAccessSections.forEach { section -> RailItem(section, selected, onSelect) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(40.dp), color = palette.border)
                pillarSections.forEach { section -> RailItem(section, selected, onSelect) }
            }
            VerticalDivider(color = palette.border)
            content(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun RailItem(section: SectionSpec, selected: Space, onSelect: (Space) -> Unit) {
    val palette = PpfTheme.palette
    NavigationRailItem(
        selected = section.space == selected,
        onClick = { onSelect(section.space) },
        icon = { Icon(section.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
        label = { Text(stringResource(section.barLabel), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = palette.brand,
            selectedTextColor = palette.brand,
            indicatorColor = palette.brandTint,
            unselectedIconColor = palette.inkMuted,
            unselectedTextColor = palette.inkMuted,
        ),
        modifier = Modifier.testTag(ShellTags.navItem(section.space)),
    )
}

@Composable
private fun ExpandedShell(
    selected: Space,
    onSelect: (Space) -> Unit,
    account: AccountUiState.Ready?,
    bell: Bell?,
    content: @Composable (Modifier) -> Unit,
) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxSize().testTag(ShellTags.SHELL_EXPANDED)) {
        DesktopTopBar(account = account, bell = bell, mySpaceSelected = selected == Space.MON_ESPACE, onMySpace = { onSelect(Space.MON_ESPACE) })
        Row(Modifier.weight(1f).fillMaxWidth()) {
            SideBar(selected, onSelect, Modifier.width(280.dp).fillMaxHeight().testTag(ShellTags.NAV_DRAWER))
            VerticalDivider(color = palette.border)
            content(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** The side bar of wide windows: daily entries, pillars, then the disclaimer at the bottom. */
@Composable
private fun SideBar(selected: Space, onSelect: (Space) -> Unit, modifier: Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.background(palette.surface0)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp)) {
            HelpButton(Modifier.padding(bottom = 12.dp), fullWidth = true)
            quickAccessSections.forEach { section ->
                NavRow(section.icon, stringResource(section.title), section.space == selected, { onSelect(section.space) }, Modifier.testTag(ShellTags.navItem(section.space)))
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            PillarRows(selected, onSelect)
        }
        Disclaimer()
    }
}

/** The menu of phones: wordmark, the four pillars, the disclaimer. */
@Composable
private fun PillarsMenu(selected: Space, onSelect: (Space) -> Unit) {
    Column(Modifier.fillMaxHeight()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp)) {
            Wordmark(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), large = true)
            Spacer(Modifier.height(16.dp))
            PillarRows(selected, onSelect)
        }
        Disclaimer()
    }
}

@Composable
private fun PillarRows(selected: Space, onSelect: (Space) -> Unit) {
    pillarSections.forEach { section ->
        val isSelected = section.space == selected
        NavRow(section.icon, stringResource(section.title), isSelected, { onSelect(section.space) }, Modifier.testTag(ShellTags.navItem(section.space)))
        if (isSelected && section.space == Space.COMPRENDRE) {
            Spacer(Modifier.height(4.dp))
            NavSubRow(
                label = stringResource(Res.string.guides_title),
                current = true,
                onClick = { onSelect(Space.COMPRENDRE) },
                modifier = Modifier.testTag(ShellTags.subItem("fiches")),
            )
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Disclaimer() {
    Text(
        text = stringResource(Res.string.nav_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = PpfTheme.palette.inkSubtle,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** The target of a notification that opens a private conversation: `ppf:messages/<conversation id>`. */
private const val MESSAGES_TARGET = "ppf:messages/"

/** The bell of the top bars: how many entries of the centre are unread, and how to open it. */
private class Bell(val unread: Long, val onOpen: () -> Unit)

/**
 * The bell: it opens the notification centre, with a small dot when something is unread and the
 * number in words for screen readers. Disabled without notifications (tests, previews).
 */
@Composable
private fun BellButton(bell: Bell?) {
    if (bell == null) {
        PpfIconButton(Lucide.Bell, stringResource(Res.string.action_notifications_soon), onClick = { }, modifier = Modifier.testTag(ShellTags.NOTIFICATIONS), enabled = false)
        return
    }
    val label = when {
        bell.unread <= 0 -> stringResource(Res.string.action_notifications)
        bell.unread == 1L -> stringResource(Res.string.notif_bell_unread_one)
        else -> stringResource(Res.string.notif_bell_unread_many, bell.unread.toInt())
    }
    PpfIconButton(Lucide.Bell, label, onClick = bell.onOpen, modifier = Modifier.testTag(ShellTags.NOTIFICATIONS), badge = bell.unread > 0)
}

/** The 56 dp bar of phones and tablets. Search waits for its module. */
@Composable
private fun TopBar(
    onMenu: (() -> Unit)?,
    account: AccountUiState.Ready?,
    bell: Bell?,
    mySpaceSelected: Boolean,
    onMySpace: () -> Unit,
) {
    val palette = PpfTheme.palette
    Column(Modifier.background(palette.surface0)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMenu != null) {
                PpfIconButton(Lucide.Menu, stringResource(Res.string.action_open_menu), onMenu, Modifier.testTag(ShellTags.MENU_BUTTON), tint = palette.ink)
            }
            Wordmark(Modifier.weight(1f).padding(start = if (onMenu != null) 4.dp else 8.dp))
            // Phones (the bar with the menu): the AIDE button sits in the top bar, where it never
            // covers a list, a composer or its send button; tablets and computers have it in the rail
            // and the side bar.
            if (onMenu != null) HelpButton(Modifier.padding(horizontal = 4.dp), compact = true)
            PpfIconButton(Lucide.Search, stringResource(Res.string.action_search_soon), onClick = { }, enabled = false)
            BellButton(bell)
            AvatarButton(account, mySpaceSelected, onMySpace)
        }
        HorizontalDivider(color = palette.border)
    }
}

/** The avatar of the top bar: it opens "Mon espace" (a generic silhouette without an account). */
@Composable
private fun AvatarButton(account: AccountUiState.Ready?, selected: Boolean, onClick: () -> Unit) {
    val palette = PpfTheme.palette
    IconButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.iconButtonColors(contentColor = if (selected) palette.brand else palette.inkMuted),
        modifier = Modifier.size(44.dp).testTag(ShellTags.navItem(Space.MON_ESPACE)),
    ) {
        if (account != null) {
            Avatar(
                seed = account.publicKey.hex,
                letters = avatarLetters(account.publicKey.hex),
                size = 32.dp,
                modifier = if (selected) Modifier.border(2.dp, palette.brand, CircleShape) else Modifier,
            )
        } else {
            Icon(Lucide.CircleUserRound, contentDescription = stringResource(Res.string.action_my_space), modifier = Modifier.size(24.dp))
        }
    }
}

/** The 64 dp bar of wide windows: wordmark, search field, notifications, "Mon espace". */
@Composable
private fun DesktopTopBar(account: AccountUiState.Ready?, bell: Bell?, mySpaceSelected: Boolean, onMySpace: () -> Unit) {
    val palette = PpfTheme.palette
    Column(Modifier.background(palette.surface0)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark(Modifier.width(248.dp), large = true)
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier.weight(1f).widthIn(max = 560.dp).heightIn(min = 44.dp)
                    .background(palette.surface1, CircleShape)
                    .border(1.dp, palette.border, CircleShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Search, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.nav_search_soon), style = MaterialTheme.typography.bodyLarge, color = palette.inkSubtle, maxLines = 1)
            }
            Spacer(Modifier.width(16.dp))
            BellButton(bell)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onMySpace,
                shape = CircleShape,
                border = BorderStroke(1.dp, if (mySpaceSelected) palette.brand else palette.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (mySpaceSelected) palette.brandTint else palette.surface2, contentColor = palette.ink),
                contentPadding = PaddingValues(start = 6.dp, end = 16.dp),
                modifier = Modifier.heightIn(min = 44.dp).testTag(ShellTags.navItem(Space.MON_ESPACE)),
            ) {
                if (account != null) {
                    Avatar(seed = account.publicKey.hex, letters = avatarLetters(account.publicKey.hex), size = 32.dp)
                } else {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(Lucide.CircleUserRound, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.space_mon_espace), style = MaterialTheme.typography.labelLarge)
            }
        }
        HorizontalDivider(color = palette.border)
    }
}
