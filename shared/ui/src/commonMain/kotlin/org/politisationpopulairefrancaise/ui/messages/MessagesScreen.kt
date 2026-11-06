package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
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
import org.politisationpopulairefrancaise.ui.resources.messages_coming
import org.politisationpopulairefrancaise.ui.resources.messages_encrypted_note
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
 * "Messages" (M14 and the community block, S-124 to S-128): private and group conversations,
 * the rooms of collectives, unions and associations, and the flows of followed media and elected
 * officials. The screen shows the planned organisation; no conversation exists until the
 * encryption protocol is chosen (brainstorming of the cahier des charges).
 */
@Composable
fun MessagesScreen(
    dependencies: AppDependencies,
    session: AccountSession,
    library: ContentLibrary,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    /** The rooms of collectives, kept by the shell for the whole run; a local one otherwise. */
    groups: GroupsLibrary? = null,
    onNavigate: (Space) -> Unit = {},
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val views = MessagesView.entries
    val pagerState = rememberPagerState(pageCount = { views.size })
    val scope = rememberCoroutineScope()
    val rooms = groups ?: rememberGroupsLibrary(dependencies)
    // On phones and tablets an open room, or the creation of one, takes the whole screen.
    if (collectivesTakeTheScreen(rooms, windowClass)) {
        CollectivesFullScreen(rooms, session, windowClass, onNavigate, modifier)
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
                PpfPrimaryButton(onClick = { }, text = stringResource(Res.string.messages_new), enabled = false, small = true, icon = Lucide.Plus, modifier = Modifier.testTag(MessagesTags.NEW))
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
                MessagesView.PRIVATE -> PrivatePage(dependencies, session, compact)
                MessagesView.COLLECTIVES -> CollectivesPage(rooms, session, windowClass, onNavigate)
                MessagesView.FLOWS -> FlowsPage(dependencies, session, library, compact)
            }
        }
    }
}

/** What a view will hold, shown until it exists: its icon, its title, its description. */
@Composable
internal fun PlannedView(view: MessagesView, compact: Boolean) {
    val palette = PpfTheme.palette
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.widthIn(max = 720.dp)) {
            PpfCard(Modifier.fillMaxWidth()) {
                Icon(view.icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(view.title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(view.text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.messages_coming), style = MaterialTheme.typography.bodyMedium, color = palette.warning)
            }
            Spacer(Modifier.height(12.dp))
            InfoBox(Lucide.ShieldCheck) {
                Text(stringResource(Res.string.messages_encrypted_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
