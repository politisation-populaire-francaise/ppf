package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.components.DaySeparator
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.FollowButton
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.MessageBubble
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.ThreadHeader
import org.politisationpopulairefrancaise.ui.components.ThreadRow
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.FeedPost
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.feed_followed_private
import org.politisationpopulairefrancaise.ui.resources.feed_public
import org.politisationpopulairefrancaise.ui.resources.flows_back
import org.politisationpopulairefrancaise.ui.resources.flows_empty
import org.politisationpopulairefrancaise.ui.resources.flows_heading
import org.politisationpopulairefrancaise.ui.resources.flows_no_post
import org.politisationpopulairefrancaise.ui.resources.flows_none_yet
import org.politisationpopulairefrancaise.ui.resources.flows_open
import org.politisationpopulairefrancaise.ui.resources.flows_public
import org.politisationpopulairefrancaise.ui.resources.flows_refresh
import org.politisationpopulairefrancaise.ui.resources.flows_select_hint
import org.politisationpopulairefrancaise.ui.resources.messages_flows_text
import org.politisationpopulairefrancaise.ui.resources.messages_flows_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object FlowsTags {
    const val LIST = "flows-list"
    const val EMPTY = "flows-empty"
    const val REFRESH = "flows-refresh"
    const val SELECT_HINT = "flows-select-hint"
    const val SCREEN = "flow-screen"
    const val THREAD = "flow-thread"
    const val NONE = "flow-none"
    const val PUBLIC = "flow-public"
    const val UNFOLLOW = "flow-unfollow"
    const val POST = "flow-post-" // followed by the first 8 characters of the event id

    /** The row of a followed account in the list. */
    fun row(pubkeyHex: String): String = "flow-" + pubkeyHex.take(8)
}

/** One followed account read as a one-way conversation: its recent notes, oldest first. */
internal data class Flow(val pubkey: String, val posts: List<FeedPost>) {
    val last: FeedPost? get() = posts.lastOrNull()
}

/**
 * The flows of the followed accounts, assembled on the device: one per followed account, most
 * recent publication first, the accounts without any publication on the device last. [posts] are
 * the publications of the followed accounts the device holds ([ContentLibrary.followedPosts]).
 */
internal fun flowsOf(followed: Set<String>, posts: List<FeedPost>): List<Flow> {
    val byAuthor = posts.groupBy { it.event.pubkey }
    return followed
        .map { key -> Flow(key, byAuthor[key].orEmpty().sortedWith(compareBy<FeedPost> { it.event.createdAt }.thenBy { it.id })) }
        .sortedWith(compareByDescending<Flow> { it.last?.event?.createdAt ?: Long.MIN_VALUE }.thenBy { it.pubkey })
}

/**
 * "Flux": the publications of the media and elected officials the user follows (S-120), presented
 * as one-way conversations: one entry per followed account, its recent notes as a thread, no
 * private reply. Everything is assembled on the device from the library's local copy and the list
 * of followed accounts, which never leaves the device. Reading needs no account. On wide windows
 * the list and the open flow sit side by side; on phones the open flow takes the whole screen
 * (see [MessagesScreen]).
 */
@Composable
fun FlowsPage(library: ContentLibrary, messaging: MessagingSession, trust: EditorialTrust, compact: Boolean) {
    val palette = PpfTheme.palette
    val flows = remember(library.followed, library.posts) { flowsOf(library.followed, library.followedPosts) }
    // A flow whose account is no longer followed closes.
    val openKey = messaging.openFlow
    LaunchedEffect(openKey, library.followed) {
        if (openKey != null && openKey !in library.followed) messaging.closeFlow()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = listPaneWidth(maxWidth)
        if (maxWidth >= TWO_PANES_MIN_WIDTH) {
            Row(Modifier.fillMaxSize()) {
                FlowList(flows, library, messaging, trust, compact, Modifier.width(listWidth).fillMaxHeight())
                VerticalDivider(color = palette.border)
                if (openKey != null && openKey in library.followed) {
                    FlowScreen(library, messaging, trust, onBack = null, modifier = Modifier.weight(1f).fillMaxHeight())
                } else {
                    Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.flows_select_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkMuted,
                            modifier = Modifier.widthIn(max = 360.dp).testTag(FlowsTags.SELECT_HINT),
                        )
                    }
                }
            }
        } else if (openKey != null && openKey in library.followed) {
            FlowScreen(library, messaging, trust, onBack = messaging::closeFlow, modifier = Modifier.fillMaxSize())
        } else {
            FlowList(flows, library, messaging, trust, compact, Modifier.fillMaxSize())
        }
    }
}

/** The list of flows: what the view is, that the list stays on the device, then one row per followed account. */
@Composable
private fun FlowList(
    flows: List<Flow>,
    library: ContentLibrary,
    messaging: MessagingSession,
    trust: EditorialTrust,
    compact: Boolean,
    modifier: Modifier,
) {
    val palette = PpfTheme.palette
    val now = currentEpochSeconds()
    val openLabel = stringResource(Res.string.flows_open)
    val noPost = stringResource(Res.string.flows_no_post)
    LazyColumn(modifier.background(palette.surface0).testTag(FlowsTags.LIST)) {
        item(key = "intro") {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.messages_flows_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (messaging.hasServer) {
                        PpfIconButton(
                            icon = Lucide.RefreshCw,
                            contentDescription = stringResource(Res.string.flows_refresh),
                            onClick = library::refresh,
                            enabled = library.status != LibraryStatus.Loading,
                            modifier = Modifier.testTag(FlowsTags.REFRESH),
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.messages_flows_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.feed_followed_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
            }
        }
        if (flows.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag(FlowsTags.EMPTY)) {
                    InfoBox(Lucide.Rss) {
                        Text(stringResource(Res.string.flows_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            item(key = "heading") {
                Text(
                    text = stringResource(Res.string.flows_heading),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.inkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                )
            }
            items(flows, key = { it.pubkey }) { flow ->
                val last = flow.last
                ThreadRow(
                    title = accountName(flow.pubkey, trust, messaging.myKey),
                    preview = last?.text ?: noPost,
                    time = last?.let { listTimeLabel(it.event.createdAt, now) },
                    onClick = { messaging.showFlow(flow.pubkey) },
                    onClickLabel = openLabel,
                    selected = messaging.openFlow == flow.pubkey,
                    modifier = Modifier.testTag(FlowsTags.row(flow.pubkey)),
                    avatar = { AccountAvatar(flow.pubkey, trust) },
                )
                HorizontalDivider(color = palette.border, modifier = Modifier.padding(start = 68.dp))
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(if (compact) 16.dp else 24.dp)) }
    }
}

/**
 * One open flow: the header (back on phones, avatar, name, "Public", the follow button to stop
 * following), the notes as bubbles of a one-way thread grouped by day with the local hour, and
 * at the bottom, in place of a composer, the reminder that a public flow is not answered here.
 */
@Composable
fun FlowScreen(library: ContentLibrary, messaging: MessagingSession, trust: EditorialTrust, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val key = messaging.openFlow ?: return
    val posts = remember(key, library.posts) { library.followedPosts.filter { it.event.pubkey == key }.sortedWith(compareBy<FeedPost> { it.event.createdAt }.thenBy { it.id }) }
    val items = remember(posts) { threadItems(posts, key = { it.id }, date = { it.event.createdAt }) }
    val today = LocalMoment.of(currentEpochSeconds())
    Column(modifier.fillMaxSize().background(palette.surface1).testTag(FlowsTags.SCREEN)) {
        ThreadHeader(
            title = accountName(key, trust, messaging.myKey),
            onBack = onBack,
            backDescription = stringResource(Res.string.flows_back),
            avatar = { AccountAvatar(key, trust) },
            subtitle = {
                Icon(Lucide.Globe, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.feed_public), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            actions = {
                FollowButton(
                    followed = true,
                    onToggle = {
                        library.unfollow(key)
                        messaging.closeFlow()
                    },
                    modifier = Modifier.testTag(FlowsTags.UNFOLLOW),
                )
                Spacer(Modifier.width(8.dp))
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (posts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    InfoBox(Lucide.Rss, Modifier.widthIn(max = 480.dp).testTag(FlowsTags.NONE)) {
                        Text(stringResource(Res.string.flows_none_yet), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(FlowsTags.THREAD),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is ThreadItem.Day -> DaySeparator(dayLabel(item.moment, today))
                            is ThreadItem.Entry -> {
                                val post = item.value
                                MessageBubble(
                                    text = post.text,
                                    mine = false,
                                    time = item.moment.hourLabel,
                                    authorSeed = post.event.pubkey,
                                    label = if (post.example) ({ ExampleBadge() }) else null,
                                    modifier = Modifier.testTag(FlowsTags.POST + post.id.take(8)),
                                )
                            }
                        }
                    }
                }
            }
        }
        Surface(color = palette.surface0, contentColor = palette.ink, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) { }.testTag(FlowsTags.PUBLIC),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Globe, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.flows_public), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
        }
    }
}
