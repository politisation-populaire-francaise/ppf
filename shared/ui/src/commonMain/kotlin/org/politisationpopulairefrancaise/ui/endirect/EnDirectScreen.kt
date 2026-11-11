package org.politisationpopulairefrancaise.ui.endirect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.voir.VoirAlert
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.LiveLabel
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.FeedPost
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.feed.LIVE_TOPIC
import org.politisationpopulairefrancaise.ui.feed.RelativeTime
import org.politisationpopulairefrancaise.ui.feed.label
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.revendications.statusLabelResource
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.FactCard
import org.politisationpopulairefrancaise.ui.voir.Grid
import org.politisationpopulairefrancaise.ui.voir.KeyFigureGrid
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.VoirState
import org.politisationpopulairefrancaise.ui.voir.VoirTitle

/** Test tags of "En direct". */
object EnDirectTags {
    const val SCREEN = "en-direct-screen"
    const val LIVE = "en-direct-live"
    const val ALERTS = "en-direct-alerts"
    const val FIGURES = "en-direct-figures"
    const val FIGURES_MORE = "en-direct-figures-more"
    const val UPCOMING = "en-direct-upcoming"
    const val HEADLINE = "en-direct-headline"
    const val NEWS = "en-direct-news"
    const val CHRONOLOGY = "en-direct-chronology"
    const val CHRONOLOGY_MORE = "en-direct-chronology-more"
    fun alert(index: Int) = "en-direct-alert-$index"
    fun news(id: String) = "en-direct-news-$id"
}

/**
 * "En direct" (cahier fonctionnel, parcours « Découvrir en 30 secondes » ; MA-01 to MA-06): what
 * one must know in under thirty seconds. The live posts of a day of mobilisation, the alerts (a
 * vote soon in Parliament, a recent ban on demonstrating), the key figures of "Voir", the coming
 * mobilisations, the demand of the moment, the latest posts of PPF and the latest facts of the
 * chronology. Everything comes from contents already on the device and opens where it lives
 * (Voir, Revendications, the feed). One column on phones, two side by side on wide windows.
 *
 * [upcoming] is the place of the coming mobilisations, filled by the agenda of "Agir" once it is
 * merged (`ui.agir.agenda.AgendaLibrary`); until then a line says it is coming.
 */
@Composable
fun EnDirectScreen(
    library: ContentLibrary,
    voir: VoirState,
    modifier: Modifier = Modifier,
    onNavigate: (Space) -> Unit = {},
    upcoming: (@Composable (Modifier) -> Unit)? = null,
    /** « Cette semaine au Parlement » (VO-31), filled by `ParliamentWeekSlot`; nothing when null. */
    parliamentWeek: (@Composable (Modifier) -> Unit)? = null,
    /** « Décider ensemble » (MVP 1, jalon 3): demands, official petitions, Pacte, filled by `DeciderHighlights`. */
    decider: (@Composable (Modifier) -> Unit)? = null,
    now: () -> Long = ::currentEpochSeconds,
) {
    val catalog = library.voir
    val nowSeconds = now()
    val today = CivilDate.fromEpochSeconds(nowSeconds)
    fun openInVoir(address: ContentAddress, axis: VoirAxis) {
        voir.home(axis)
        voir.open(address)
        onNavigate(Space.VOIR)
    }
    val live = library.posts.filter { LIVE_TOPIC in it.topics }.take(3)
    val news = library.posts.filter { it.editorial && LIVE_TOPIC !in it.topics }.take(3)
    val alerts = VoirAlert.of(catalog, today)
    val figures = catalog.keyFigures(VoirAxis.MOUVEMENT).take(4)
    val chronology = catalog.chronology().take(3)
    val headline = headlineOf(library.revendications)

    BoxWithConstraints(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        val compact = maxWidth < 600.dp
        val twoColumns = maxWidth >= 900.dp
        val padding = if (compact) 16.dp else 24.dp

        val alertsBlock: @Composable ColumnScope.() -> Unit = {
            Section(stringResource(Res.string.live_alerts), EnDirectTags.ALERTS) {
                if (alerts.isEmpty()) Text(stringResource(Res.string.live_alerts_none), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkSubtle)
                alerts.forEachIndexed { index, alert ->
                    AlertRow(alert, Modifier.testTag(EnDirectTags.alert(index))) {
                        when (alert) {
                            is VoirAlert.UpcomingVote -> openInVoir(alert.text.address, VoirAxis.POUVOIR)
                            is VoirAlert.ProtestBan -> openInVoir(alert.fact.address, VoirAxis.LIBERTES)
                        }
                    }
                    if (index < alerts.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
        val figuresBlock: @Composable ColumnScope.() -> Unit = {
            if (figures.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().testTag(EnDirectTags.FIGURES)) {
                    SectionHeader(
                        stringResource(Res.string.live_key_figures),
                        action = stringResource(Res.string.live_see_voir),
                        actionTag = EnDirectTags.FIGURES_MORE,
                        onAction = {
                            voir.home(VoirAxis.MOUVEMENT)
                            onNavigate(Space.VOIR)
                        },
                    )
                    KeyFigureGrid(figures, if (compact) 1 else 2, onOpen = { openInVoir(it.address, VoirAxis.MOUVEMENT) })
                }
            }
        }
        val upcomingBlock: @Composable ColumnScope.() -> Unit = {
            Section(stringResource(Res.string.live_upcoming), EnDirectTags.UPCOMING) {
                if (upcoming != null) {
                    upcoming(Modifier.fillMaxWidth())
                } else {
                    InfoBox(Lucide.Calendar) { Text(stringResource(Res.string.live_upcoming_soon), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        val headlineBlock: @Composable ColumnScope.() -> Unit = {
            if (headline != null) {
                Section(stringResource(Res.string.live_headline), EnDirectTags.HEADLINE) {
                    HeadlineCard(headline) {
                        library.openRevendication(headline)
                        onNavigate(Space.REVENDICATIONS)
                    }
                }
            }
        }
        val liveBlock: @Composable ColumnScope.() -> Unit = {
            if (live.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().testTag(EnDirectTags.LIVE)) {
                    LiveLabel()
                    Spacer(Modifier.height(8.dp))
                    live.forEachIndexed { index, post ->
                        PostCard(post, nowSeconds, live = true, onOpen = { onNavigate(Space.FIL) })
                        if (index < live.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        val newsBlock: @Composable ColumnScope.() -> Unit = {
            if (news.isNotEmpty()) {
                Section(stringResource(Res.string.live_news), EnDirectTags.NEWS) {
                    news.forEachIndexed { index, post ->
                        PostCard(post, nowSeconds, live = false, onOpen = { onNavigate(Space.FIL) }, modifier = Modifier.testTag(EnDirectTags.news(post.id.take(8))))
                        if (index < news.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        val chronologyBlock: @Composable ColumnScope.() -> Unit = {
            if (chronology.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().testTag(EnDirectTags.CHRONOLOGY)) {
                    SectionHeader(
                        stringResource(Res.string.live_chronology),
                        action = stringResource(Res.string.live_chronology_open),
                        actionTag = EnDirectTags.CHRONOLOGY_MORE,
                        onAction = {
                            voir.home(VoirAxis.MOUVEMENT)
                            voir.open(org.politisationpopulairefrancaise.ui.voir.VoirDestination.Chronology)
                            onNavigate(Space.VOIR)
                        },
                    )
                    Grid(chronology, 1) { fact, itemModifier -> FactCard(fact, onOpen = { openInVoir(fact.address, VoirAxis.MOUVEMENT) }, modifier = itemModifier) }
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(EnDirectTags.SCREEN)) {
            PageHeader(stringResource(Res.string.space_en_direct), stringResource(Res.string.desc_en_direct))
            Spacer(Modifier.height(16.dp))
            if (catalog.isEmpty && library.posts.isEmpty() && library.status != LibraryStatus.Loading) {
                Text(stringResource(Res.string.live_empty), style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.inkMuted)
                return@Column
            }
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        alertsBlock()
                        figuresBlock()
                        chronologyBlock()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        liveBlock()
                        decider?.invoke(Modifier.fillMaxWidth())
                        headlineBlock()
                        upcomingBlock()
                        parliamentWeek?.invoke(Modifier.fillMaxWidth())
                        newsBlock()
                    }
                }
            } else {
                // Phones: what changes by the minute first, then the figures, then the rest.
                Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    liveBlock()
                    alertsBlock()
                    decider?.invoke(Modifier.fillMaxWidth())
                    figuresBlock()
                    upcomingBlock()
                    parliamentWeek?.invoke(Modifier.fillMaxWidth())
                    headlineBlock()
                    newsBlock()
                    chronologyBlock()
                }
            }
        }
    }
}

/** The demand of the moment: a campaign first, then a demand kept, then a very supported one (MA-05). */
internal fun headlineOf(demands: List<Article>): Article? {
    val order = listOf(RevendicationStatus.CAMPAGNE, RevendicationStatus.RETENUE, RevendicationStatus.TRES_SOUTENUE)
    return order.firstNotNullOfOrNull { status -> demands.firstOrNull { it.status == status } }
}

@Composable
private fun Section(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        SectionHeader(title)
        content()
    }
}

/** One alert: an accent mark, what it is, when, and what it concerns; touching it opens the content. */
@Composable
private fun AlertRow(alert: VoirAlert, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    val (icon, kind, detail) = when (alert) {
        is VoirAlert.UpcomingVote -> Triple(
            Lucide.Vote,
            when (alert.daysLeft) {
                0 -> stringResource(Res.string.live_alert_vote_today)
                1 -> stringResource(Res.string.live_alert_vote_tomorrow)
                else -> stringResource(Res.string.live_alert_vote, alert.daysLeft)
            },
            stringResource(Res.string.live_alert_vote_text, alert.vote.label, alert.vote.date.toFrenchShort()),
        )
        is VoirAlert.ProtestBan -> Triple(
            Lucide.Ban,
            stringResource(Res.string.live_alert_ban),
            stringResource(Res.string.live_alert_ban_text, alert.fact.date.toFrenchShort(), alert.fact.place ?: ""),
        )
    }
    val title = when (alert) {
        is VoirAlert.UpcomingVote -> alert.text.title
        is VoirAlert.ProtestBan -> alert.fact.title
    }
    Surface(onClick = onOpen, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium, color = palette.accentTint, contentColor = palette.ink) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = palette.accentInk, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(kind, style = MaterialTheme.typography.labelLarge, color = palette.accentInk)
                VoirTitle(title, style = MaterialTheme.typography.titleSmall, maxLines = 3)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/** The demand of the moment, with its status. */
@Composable
private fun HeadlineCard(demand: Article, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.live_headline_demand), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle, modifier = Modifier.weight(1f))
            demand.status?.let { PpfBadge(stringResource(statusLabelResource(it)), BadgeStyle.BRAND) }
        }
        Spacer(Modifier.height(6.dp))
        VoirTitle(demand.title, style = MaterialTheme.typography.titleMedium, maxLines = 4)
        demand.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.live_headline_open), style = MaterialTheme.typography.labelLarge, color = palette.brand)
    }
}

/** A short post: its text, how long ago, and the [EXEMPLE] badge when it carries the marker. */
@Composable
private fun PostCard(post: FeedPost, nowSeconds: Long, live: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val tint: Color = if (live) palette.accentTint else palette.surface2
    Surface(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = tint, contentColor = palette.ink) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    RelativeTime.between(post.event.createdAt, nowSeconds).label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (live) palette.accentInk else palette.inkSubtle,
                    modifier = Modifier.weight(1f),
                )
                if (post.example) ExampleBadge()
            }
            Spacer(Modifier.height(4.dp))
            Text(post.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

