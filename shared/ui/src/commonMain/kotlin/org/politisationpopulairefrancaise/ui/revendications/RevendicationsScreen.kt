package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.ActionButton
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.FeedPost
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.content_updated
import org.politisationpopulairefrancaise.ui.resources.desc_revendications
import org.politisationpopulairefrancaise.ui.resources.revendication_argument
import org.politisationpopulairefrancaise.ui.resources.revendication_back
import org.politisationpopulairefrancaise.ui.resources.revendication_debate
import org.politisationpopulairefrancaise.ui.resources.revendication_status_campagne
import org.politisationpopulairefrancaise.ui.resources.revendication_status_en_debat
import org.politisationpopulairefrancaise.ui.resources.revendication_status_nouvelle
import org.politisationpopulairefrancaise.ui.resources.revendication_status_retenue
import org.politisationpopulairefrancaise.ui.resources.revendication_status_tres_soutenue
import org.politisationpopulairefrancaise.ui.resources.revendication_vote_against
import org.politisationpopulairefrancaise.ui.resources.revendication_vote_for
import org.politisationpopulairefrancaise.ui.resources.revendication_vote_soon
import org.politisationpopulairefrancaise.ui.resources.revendications_intro
import org.politisationpopulairefrancaise.ui.resources.revendications_none
import org.politisationpopulairefrancaise.ui.resources.space_revendications
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object RevendicationTags {
    const val CARD = "revendication-" // followed by the slug
    const val LIST = "revendication-list"
    const val DETAIL = "revendication-detail"
    const val BACK = "revendication-back"
    const val VOTE_FOR = "revendication-vote-for"
}

/**
 * The demands wall (M11, DE-01): every demand is a card read at a glance. Voting and debating
 * open once the "one person, one vote" method is validated (decision pending); until then the
 * buttons are shown disabled and the bundled demands are examples.
 */
@Composable
fun RevendicationsScreen(library: ContentLibrary, windowClass: WindowClass, modifier: Modifier = Modifier) {
    val twoPanes = windowClass.width == WindowWidthClass.EXPANDED
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val selected = library.selectedRevendication
    Box(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        when {
            twoPanes -> Row(Modifier.fillMaxSize()) {
                RevendicationList(library, Modifier.width(380.dp).fillMaxSize(), compact = false)
                VerticalDivider(color = PpfTheme.palette.border)
                if (selected != null) {
                    RevendicationDetail(selected, onBack = null, compact = false, Modifier.fillMaxSize())
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        PageHeader(stringResource(Res.string.space_revendications), stringResource(Res.string.desc_revendications))
                    }
                }
            }
            selected != null -> RevendicationDetail(selected, onBack = library::closeRevendication, compact = compact, Modifier.fillMaxSize())
            else -> RevendicationList(library, Modifier.fillMaxSize(), compact = compact, withHeader = true)
        }
    }
}

@Composable
private fun RevendicationList(library: ContentLibrary, modifier: Modifier, compact: Boolean, withHeader: Boolean = false) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(RevendicationTags.LIST),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (withHeader) {
            PageHeader(stringResource(Res.string.space_revendications), stringResource(Res.string.desc_revendications))
            Spacer(Modifier.height(8.dp))
        }
        InfoBox(Lucide.ScrollText) {
            Text(stringResource(Res.string.revendications_intro), style = MaterialTheme.typography.bodyMedium)
        }
        if (library.revendications.isEmpty() && library.status != LibraryStatus.Loading) {
            Text(stringResource(Res.string.revendications_none), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
        }
        library.revendications.forEach { revendication ->
            RevendicationCard(
                revendication = revendication,
                onOpen = { library.openRevendication(revendication) },
                selected = library.selectedRevendication?.address == revendication.address,
            )
        }
    }
}

/** The card of a demand, used on the wall and in the feed: status, the demand, its territory and themes, the vote row. */
@Composable
fun RevendicationCard(revendication: Article, onOpen: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val palette = PpfTheme.palette
    val example = revendication.title.startsWith(FeedPost.EXAMPLE_MARKER)
    PpfCard(onClick = onOpen, selected = selected, modifier = modifier.fillMaxWidth().testTag(RevendicationTags.CARD + revendication.slug)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            revendication.status?.let { StatusBadge(it) }
            Spacer(Modifier.weight(1f))
            if (example) ExampleBadge()
        }
        Spacer(Modifier.height(10.dp))
        Text(revendication.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(), style = MaterialTheme.typography.titleSmall)
        revendication.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        MetaRow(revendication)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = palette.border)
        Spacer(Modifier.height(4.dp))
        VoteRow()
    }
}

@Composable
private fun MetaRow(revendication: Article) {
    val palette = PpfTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        revendication.territory?.let { territory ->
            Icon(Lucide.MapPin, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(territory, style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            if (revendication.topics.isNotEmpty()) Text(" · ", style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
        }
        if (revendication.topics.isNotEmpty()) {
            Text(revendication.topics.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelLarge, color = palette.brand)
        }
    }
}

/** Pour, Contre and Débat wait for the "one person, one vote" decision: shown, disabled, explained. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoteRow() {
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(Lucide.ThumbsUp, stringResource(Res.string.revendication_vote_for), onClick = { }, enabled = false, modifier = Modifier.testTag(RevendicationTags.VOTE_FOR))
            ActionButton(Lucide.ThumbsDown, stringResource(Res.string.revendication_vote_against), onClick = { }, enabled = false)
            ActionButton(Lucide.MessageCircle, stringResource(Res.string.revendication_debate), onClick = { }, enabled = false)
        }
        Text(stringResource(Res.string.revendication_vote_soon), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    }
}

@Composable
fun StatusBadge(status: RevendicationStatus, modifier: Modifier = Modifier) {
    val (label, style) = when (status) {
        RevendicationStatus.NOUVELLE -> Res.string.revendication_status_nouvelle to BadgeStyle.NEUTRAL
        RevendicationStatus.EN_DEBAT -> Res.string.revendication_status_en_debat to BadgeStyle.BRAND
        RevendicationStatus.TRES_SOUTENUE -> Res.string.revendication_status_tres_soutenue to BadgeStyle.SUCCESS
        RevendicationStatus.RETENUE -> Res.string.revendication_status_retenue to BadgeStyle.SUCCESS
        RevendicationStatus.CAMPAGNE -> Res.string.revendication_status_campagne to BadgeStyle.BRAND
    }
    PpfBadge(stringResource(label), style, modifier)
}

@Composable
private fun RevendicationDetail(revendication: Article, onBack: (() -> Unit)?, compact: Boolean, modifier: Modifier) {
    val palette = PpfTheme.palette
    val example = revendication.title.startsWith(FeedPost.EXAMPLE_MARKER)
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(RevendicationTags.DETAIL)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.revendication_back), onBack, Modifier.testTag(RevendicationTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                revendication.status?.let { StatusBadge(it) }
                if (example) ExampleBadge()
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = revendication.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            revendication.summary?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            MetaRow(revendication)
            Text(
                text = stringResource(Res.string.content_updated, CivilDate.fromEpochSeconds(revendication.updatedAt).toFrench()),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkSubtle,
            )
            Spacer(Modifier.height(12.dp))
            PpfCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { VoteRow() }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(Res.string.revendication_argument), style = MaterialTheme.typography.titleMedium, color = palette.brand, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                MarkdownText(revendication.body)
            }
        }
    }
}
