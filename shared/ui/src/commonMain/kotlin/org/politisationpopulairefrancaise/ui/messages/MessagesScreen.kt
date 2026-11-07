package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.groups.GroupsLibrary
import org.politisationpopulairefrancaise.ui.groups.rememberGroupsLibrary
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTabs
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.desc_messages
import org.politisationpopulairefrancaise.ui.resources.messages_collectives_text
import org.politisationpopulairefrancaise.ui.resources.messages_collectives_title
import org.politisationpopulairefrancaise.ui.resources.messages_flows_text
import org.politisationpopulairefrancaise.ui.resources.messages_flows_title
import org.politisationpopulairefrancaise.ui.resources.messages_new
import org.politisationpopulairefrancaise.ui.resources.messages_private_text
import org.politisationpopulairefrancaise.ui.resources.messages_private_title
import org.politisationpopulairefrancaise.ui.resources.messages_tab_collectives
import org.politisationpopulairefrancaise.ui.resources.messages_tab_flows
import org.politisationpopulairefrancaise.ui.resources.messages_tab_private
import org.politisationpopulairefrancaise.ui.resources.space_messages
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object MessagesTags {
    const val TAB = "messages-tab-" // followed by the view id
    const val NEW = "messages-new"
}

/** The three views of the messages, swiped like the feed (decision of the owner, 2026-11-06). */
enum class MessagesView(val id: String, val tab: StringResource, val title: StringResource, val text: StringResource, val icon: ImageVector) {
    PRIVATE("prive", Res.string.messages_tab_private, Res.string.messages_private_title, Res.string.messages_private_text, Lucide.Lock),
    COLLECTIVES("collectifs", Res.string.messages_tab_collectives, Res.string.messages_collectives_title, Res.string.messages_collectives_text, Lucide.Users),
    FLOWS("flux", Res.string.messages_tab_flows, Res.string.messages_flows_title, Res.string.messages_flows_text, Lucide.Rss),
}

/**
 * "Messages" (M14 and the community block, S-124 to S-128): private and group conversations
 * (NIP-17, decision D-027), the rooms of collectives, unions and associations, and the flows of
 * followed media and elected officials. While the screen is shown it asks the server for the
 * private messages (the pending deliveries first, then the wraps addressed to the user) when it
 * opens and every [MessagingSession.REFRESH_PERIOD_SECONDS]. On phones an open conversation or
 * flow takes the whole screen, with a back button. [onNavigate] lets the invitations lead to
 * "Mon espace".
 */
@Composable
fun MessagesScreen(
    dependencies: AppDependencies,
    session: AccountSession,
    library: ContentLibrary,
    messaging: MessagingSession,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onNavigate: (Space) -> Unit = {},
    /** The rooms of collectives, kept by the shell for the whole run; a local one otherwise. */
    groups: GroupsLibrary? = null,
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val views = MessagesView.entries
    val pagerState = rememberPagerState(initialPage = if (messaging.openFlow != null) views.indexOf(MessagesView.FLOWS) else 0, pageCount = { views.size })
    val scope = rememberCoroutineScope()
    val trust = dependencies.editorialTrust
    var composing by remember { mutableStateOf(false) }

    LaunchedEffect(messaging, messaging.myKey) {
        messaging.refresh()
        while (true) {
            delay(MessagingSession.REFRESH_PERIOD_SECONDS * 1_000L)
            messaging.refresh()
        }
    }
    if (composing) {
        NewConversationDialog(
            messaging = messaging,
            followed = library.followed,
            trust = trust,
            onStart = { recipients, subject ->
                composing = false
                messaging.openWith(recipients, subject)
                scope.launch { pagerState.scrollToPage(views.indexOf(MessagesView.PRIVATE)) }
            },
            onDismiss = { composing = false },
        )
    }
    val rooms = groups ?: rememberGroupsLibrary(dependencies)
    SystemBack(enabled = messaging.open != null) { messaging.close() }
    SystemBack(enabled = messaging.openFlow != null) { messaging.closeFlow() }
    SystemBack(enabled = rooms.selected != null || rooms.creating) { if (rooms.creating) rooms.cancelCreation() else rooms.close() }
    // On phones and tablets an open room, or the creation of one, takes the whole screen.
    if (collectivesTakeTheScreen(rooms, windowClass)) {
        CollectivesFullScreen(rooms, session, windowClass, onNavigate, modifier)
        return
    }
    // On phones the open conversation or flow replaces the header, the tabs and the list.
    if (compact && (messaging.open != null || messaging.openFlow != null)) {
        Box(modifier.fillMaxSize().background(palette.surface1)) {
            if (messaging.open != null) {
                ConversationScreen(messaging, trust, onBack = messaging::close)
            } else {
                FlowScreen(library, messaging, trust, onBack = messaging::closeFlow)
            }
        }
        return
    }
    val tabs: @Composable (Modifier) -> Unit = { tabsModifier ->
        PpfTabs(
            labels = views.map { stringResource(it.tab) },
            selected = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = tabsModifier,
            tagFor = { MessagesTags.TAB + views[it].id },
        )
    }
    Column(modifier.fillMaxSize().background(palette.surface1)) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp)) {
                PageHeader(stringResource(Res.string.space_messages), stringResource(Res.string.desc_messages))
                Spacer(Modifier.size(12.dp))
                PpfPrimaryButton(
                    onClick = { composing = true },
                    text = stringResource(Res.string.messages_new),
                    enabled = messaging.canWrite,
                    small = true,
                    icon = Lucide.Plus,
                    modifier = Modifier.testTag(MessagesTags.NEW),
                )
            }
            Surface(color = palette.surface0, contentColor = palette.ink, modifier = Modifier.fillMaxWidth()) {
                Column {
                    tabs(Modifier.padding(horizontal = if (compact) 16.dp else 24.dp))
                    HorizontalDivider(color = palette.border)
                }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { views[it].id }) { page ->
            when (views[page]) {
                MessagesView.PRIVATE -> PrivatePage(session, messaging, trust, compact, onNavigate)
                MessagesView.COLLECTIVES -> CollectivesPage(rooms, session, windowClass, onNavigate)
                MessagesView.FLOWS -> FlowsPage(library, messaging, trust, compact)
            }
        }
    }
}
