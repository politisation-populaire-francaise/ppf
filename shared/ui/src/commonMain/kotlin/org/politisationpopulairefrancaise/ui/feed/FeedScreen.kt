package org.politisationpopulairefrancaise.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.components.ActionButton
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.LiveBanner
import org.politisationpopulairefrancaise.ui.components.LiveCard
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTabs
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.avatarLetters
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.FeedPost
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.app_name
import org.politisationpopulairefrancaise.ui.resources.content_status_offline
import org.politisationpopulairefrancaise.ui.resources.content_status_synced
import org.politisationpopulairefrancaise.ui.resources.content_status_unreachable
import org.politisationpopulairefrancaise.ui.resources.desc_fil
import org.politisationpopulairefrancaise.ui.resources.feed_badge_you
import org.politisationpopulairefrancaise.ui.resources.feed_comment
import org.politisationpopulairefrancaise.ui.resources.feed_compose_hint
import org.politisationpopulairefrancaise.ui.resources.feed_create_account
import org.politisationpopulairefrancaise.ui.resources.feed_empty
import org.politisationpopulairefrancaise.ui.resources.feed_empty_filter
import org.politisationpopulairefrancaise.ui.resources.feed_like
import org.politisationpopulairefrancaise.ui.resources.feed_need_account
import org.politisationpopulairefrancaise.ui.resources.feed_pending
import org.politisationpopulairefrancaise.ui.resources.feed_prototype_notice
import org.politisationpopulairefrancaise.ui.resources.feed_public
import org.politisationpopulairefrancaise.ui.resources.feed_publish
import org.politisationpopulairefrancaise.ui.resources.feed_relay
import org.politisationpopulairefrancaise.ui.resources.feed_tab_all
import org.politisationpopulairefrancaise.ui.resources.feed_tab_mine
import org.politisationpopulairefrancaise.ui.resources.feed_tab_ppf
import org.politisationpopulairefrancaise.ui.resources.feed_tab_revendications
import org.politisationpopulairefrancaise.ui.resources.feed_title
import org.politisationpopulairefrancaise.ui.resources.feed_verified
import org.politisationpopulairefrancaise.ui.resources.feed_visibility_public
import org.politisationpopulairefrancaise.ui.resources.revendications_intro
import org.politisationpopulairefrancaise.ui.resources.revendications_none
import org.politisationpopulairefrancaise.ui.revendications.RevendicationCard
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object FeedTags {
    const val LIST = "feed-list-" // followed by the view id
    const val COMPOSER = "feed-composer"
    const val PUBLISH = "feed-publish"
    const val CREATE_ACCOUNT = "feed-create-account"
    const val LIVE = "feed-live"
    const val TAB = "feed-tab-" // followed by the view id
    const val POST = "post-" // followed by the first 8 characters of the event id
    const val LIKE = "like-" // same suffix
}

/** The views of the feed, swiped from one to the next. More will come with circles, collectives and the map. */
enum class FeedFilter(val id: String) { ALL("tout"), PPF("ppf"), REVENDICATIONS("revendications"), MINE("moi") }

/** Topic of the publications that feed the live banner. */
const val LIVE_TOPIC = "en-direct"

/**
 * The feed, default screen of the application (M18): short publications of trusted accounts and
 * of the user, newest first, a composer for people who have an account, the live banner when a
 * publication is tagged "en-direct", and a view of the demands wall. The views sit under a row of
 * tabs and are swiped with a finger (decision of the owner, 2026-11-06). Reactions stay on the
 * device and comments are not open yet: their privacy design is being settled.
 */
@Composable
fun FeedScreen(
    library: ContentLibrary,
    session: AccountSession,
    windowClass: WindowClass,
    onNavigate: (Space) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    val account = session.state as? AccountUiState.Ready
    val myKey = account?.publicKey?.hex
    val now = currentEpochSeconds()
    val compact = windowClass.width == WindowWidthClass.COMPACT

    val filters = if (account != null) FeedFilter.entries else FeedFilter.entries.filter { it != FeedFilter.MINE }
    val pagerState = rememberPagerState(pageCount = { filters.size })
    val live = library.posts.firstOrNull { LIVE_TOPIC in it.topics }
    val onPublish: () -> Unit = {
        val signer = session.signer
        val text = draft
        if (signer != null) scope.launch { if (library.publishNote(signer, text)) draft = "" }
    }

    val tabs: @Composable (Modifier) -> Unit = { tabsModifier ->
        PpfTabs(
            labels = filters.map { stringResource(it.label()) },
            selected = pagerState.currentPage.coerceIn(0, filters.lastIndex),
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = tabsModifier,
            tagFor = { FeedTags.TAB + filters[it].id },
        )
    }

    BoxWithConstraints(modifier.fillMaxSize().background(palette.surface1)) {
        val withAside = maxWidth >= 980.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight()) {
                if (compact) {
                    // On phones the tabs sit right under the top bar, full width, as in the mock-up.
                    Surface(color = palette.surface0, contentColor = palette.ink) {
                        Column {
                            tabs(Modifier.padding(horizontal = 16.dp))
                            HorizontalDivider(color = palette.border)
                        }
                    }
                } else {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(Res.string.feed_title),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            ExampleBadge()
                        }
                        Spacer(Modifier.height(12.dp))
                        tabs(Modifier)
                        HorizontalDivider(color = palette.border)
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { filters[it].id }) { page ->
                    FeedPage(
                        filter = filters[page],
                        library = library,
                        myKey = myKey,
                        live = live?.takeIf { !withAside },
                        draft = draft,
                        onDraftChange = { draft = it },
                        onPublish = onPublish,
                        showInvitation = account == null && !withAside,
                        onNavigate = onNavigate,
                        compact = compact,
                        now = now,
                    )
                }
            }
            if (withAside) {
                Spacer(Modifier.width(24.dp))
                Column(
                    modifier = Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 24.dp, top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (live != null) LiveCard(live.text, onClick = { onNavigate(Space.EN_DIRECT) }, Modifier.testTag(FeedTags.LIVE))
                    if (account == null) {
                        Invitation(onCreate = { onNavigate(Space.MON_ESPACE) })
                    } else {
                        PpfCard { Text(stringResource(Res.string.desc_fil), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted) }
                    }
                }
            }
        }
    }
}

private fun FeedFilter.label() = when (this) {
    FeedFilter.ALL -> Res.string.feed_tab_all
    FeedFilter.PPF -> Res.string.feed_tab_ppf
    FeedFilter.REVENDICATIONS -> Res.string.feed_tab_revendications
    FeedFilter.MINE -> Res.string.feed_tab_mine
}

/** One view of the feed: a scrolling list with the banner, the composer and the publications it shows. */
@Composable
private fun FeedPage(
    filter: FeedFilter,
    library: ContentLibrary,
    myKey: String?,
    live: FeedPost?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPublish: () -> Unit,
    showInvitation: Boolean,
    onNavigate: (Space) -> Unit,
    compact: Boolean,
    now: Long,
) {
    val palette = PpfTheme.palette
    val posts = when (filter) {
        FeedFilter.ALL -> library.posts
        FeedFilter.PPF -> library.posts.filter { it.editorial }
        FeedFilter.MINE -> library.posts.filter { it.event.pubkey == myKey }
        FeedFilter.REVENDICATIONS -> emptyList()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(FeedTags.LIST + filter.id),
        contentPadding = if (compact) PaddingValues(12.dp) else PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
    ) {
        if (filter == FeedFilter.REVENDICATIONS) {
            item {
                InfoBox(Lucide.ScrollText) {
                    Text(stringResource(Res.string.revendications_intro), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (library.revendications.isEmpty() && library.status != LibraryStatus.Loading) {
                item { Text(stringResource(Res.string.revendications_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted) }
            }
            items(library.revendications, key = { it.address.toString() }) { revendication ->
                RevendicationCard(
                    revendication = revendication,
                    onOpen = {
                        library.openRevendication(revendication)
                        onNavigate(Space.REVENDICATIONS)
                    },
                )
            }
            return@LazyColumn
        }
        if (live != null) {
            item { LiveBanner(live.text, onClick = { onNavigate(Space.EN_DIRECT) }, Modifier.testTag(FeedTags.LIVE)) }
        }
        if (myKey != null) {
            item { Composer(myKey, draft, onDraftChange, onPublish) }
        } else if (showInvitation) {
            item { Invitation(onCreate = { onNavigate(Space.MON_ESPACE) }) }
        }
        if (filter == FeedFilter.ALL) {
            item {
                Column {
                    Text(stringResource(Res.string.feed_prototype_notice), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    statusText(library.status)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    }
                }
            }
        }
        if (posts.isEmpty() && library.status != LibraryStatus.Loading) {
            item {
                Text(
                    text = stringResource(if (filter == FeedFilter.ALL) Res.string.feed_empty else Res.string.feed_empty_filter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                )
            }
        }
        items(posts, key = { it.id }) { post ->
            PostCard(
                post = post,
                mine = myKey == post.event.pubkey,
                liked = post.id in library.liked,
                onLike = { library.toggleLike(post.id) },
                now = now,
            )
        }
    }
}

@Composable
private fun statusText(status: LibraryStatus): String? = when (status) {
    LibraryStatus.Offline -> stringResource(Res.string.content_status_offline)
    is LibraryStatus.Synced -> stringResource(Res.string.content_status_synced, status.pulled)
    LibraryStatus.Unreachable -> stringResource(Res.string.content_status_unreachable)
    LibraryStatus.Loading, LibraryStatus.Unavailable -> null
}

/** The invitation shown instead of the composer when there is no account on the device. */
@Composable
private fun Invitation(onCreate: () -> Unit) {
    InfoBox(Lucide.KeyRound) {
        Text(stringResource(Res.string.feed_need_account), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        PpfPrimaryButton(onClick = onCreate, text = stringResource(Res.string.feed_create_account), modifier = Modifier.testTag(FeedTags.CREATE_ACCOUNT))
    }
}

@Composable
private fun Composer(myKey: String, draft: String, onDraftChange: (String) -> Unit, onPublish: () -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(seed = myKey, letters = avatarLetters(myKey), modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            PpfTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = stringResource(Res.string.feed_compose_hint),
                modifier = Modifier.weight(1f).testTag(FeedTags.COMPOSER),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.extraSmall, color = palette.surface2, contentColor = palette.ink, border = BorderStroke(1.dp, palette.border)) {
                Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Globe, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.feed_visibility_public), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted, maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            PpfPrimaryButton(
                onClick = onPublish,
                text = stringResource(Res.string.feed_publish),
                enabled = draft.isNotBlank(),
                small = true,
                modifier = Modifier.testTag(FeedTags.PUBLISH),
            )
        }
    }
}

@Composable
private fun PostCard(post: FeedPost, mine: Boolean, liked: Boolean, onLike: () -> Unit, now: Long) {
    val palette = PpfTheme.palette
    val suffix = post.id.take(8)
    PpfCard(Modifier.fillMaxWidth().testTag(FeedTags.POST + suffix)) {
        Row(verticalAlignment = Alignment.Top) {
            if (post.editorial) {
                Avatar(seed = post.event.pubkey, letters = "PPF", rounded = true, color = palette.brand)
            } else {
                Avatar(seed = post.event.pubkey, letters = avatarLetters(post.event.pubkey))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            post.editorial -> stringResource(Res.string.app_name)
                            mine -> stringResource(Res.string.feed_badge_you)
                            else -> shortNpub(post.event.pubkey)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (post.editorial) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Lucide.BadgeCheck, contentDescription = stringResource(Res.string.feed_verified), tint = palette.brand, modifier = Modifier.size(18.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(RelativeTime.between(post.event.createdAt, now).label(), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                    Text(" · ", style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                    Icon(Lucide.Globe, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.feed_public), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                }
            }
            if (post.example) {
                Spacer(Modifier.width(8.dp))
                ExampleBadge()
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(post.text, style = MaterialTheme.typography.bodyLarge)
        if (post.topics.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = post.topics.joinToString("  ") { "#$it" },
                style = MaterialTheme.typography.labelLarge,
                color = palette.brand,
            )
        }
        if (post.pending) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Clock, contentDescription = null, tint = palette.warning, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.feed_pending), style = MaterialTheme.typography.bodyMedium, color = palette.warning)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = palette.border)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(
                icon = Lucide.Heart,
                label = stringResource(Res.string.feed_like),
                onClick = onLike,
                active = liked,
                modifier = Modifier.testTag(FeedTags.LIKE + suffix),
            )
            ActionButton(icon = Lucide.MessageCircle, label = stringResource(Res.string.feed_comment), onClick = { }, enabled = false)
            ActionButton(icon = Lucide.Repeat2, label = stringResource(Res.string.feed_relay), onClick = { }, enabled = false)
        }
    }
}
