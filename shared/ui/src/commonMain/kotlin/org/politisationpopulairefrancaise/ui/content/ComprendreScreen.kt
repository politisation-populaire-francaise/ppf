package org.politisationpopulairefrancaise.ui.content

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.content_back
import org.politisationpopulairefrancaise.ui.resources.content_none
import org.politisationpopulairefrancaise.ui.resources.content_origin_ai
import org.politisationpopulairefrancaise.ui.resources.content_origin_human
import org.politisationpopulairefrancaise.ui.resources.content_refresh
import org.politisationpopulairefrancaise.ui.resources.content_status_loading
import org.politisationpopulairefrancaise.ui.resources.content_status_offline
import org.politisationpopulairefrancaise.ui.resources.content_status_synced
import org.politisationpopulairefrancaise.ui.resources.content_status_unavailable
import org.politisationpopulairefrancaise.ui.resources.content_status_unreachable
import org.politisationpopulairefrancaise.ui.resources.content_topics
import org.politisationpopulairefrancaise.ui.resources.content_type_article
import org.politisationpopulairefrancaise.ui.resources.content_type_decryptage
import org.politisationpopulairefrancaise.ui.resources.content_type_dossier
import org.politisationpopulairefrancaise.ui.resources.content_type_fiche
import org.politisationpopulairefrancaise.ui.resources.content_updated
import org.politisationpopulairefrancaise.ui.resources.desc_comprendre
import org.politisationpopulairefrancaise.ui.resources.guides_title
import org.politisationpopulairefrancaise.ui.resources.space_comprendre
import org.politisationpopulairefrancaise.ui.resources.space_revendications
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object ContentTags {
    const val GUIDE_CARD = "article-" // followed by the slug
    const val GUIDE_LIST = "guide-list"
    const val GUIDE_DETAIL = "guide-detail"
    const val BACK = "guide-back"
    const val REFRESH = "content-refresh"
    const val STATUS = "content-status"
}

/**
 * "Comprendre": for now, the practical guides (M6), readable offline. One pane on phones and
 * tablets (list, then the guide with a back button), two panes side by side on wide windows.
 */
@Composable
fun ComprendreScreen(library: ContentLibrary, windowClass: WindowClass, modifier: Modifier = Modifier) {
    val twoPanes = windowClass.width == WindowWidthClass.EXPANDED
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val selected = library.selected
    // The panes carry their own test tags, so the host's modifier stays on this container.
    Box(modifier.fillMaxSize().background(PpfTheme.palette.surface1)) {
        when {
            twoPanes -> Row(Modifier.fillMaxSize()) {
                GuideList(library, Modifier.width(380.dp).fillMaxSize(), compact = false)
                VerticalDivider(color = PpfTheme.palette.border)
                if (selected != null) {
                    GuideDetail(selected, onBack = null, compact = false, Modifier.fillMaxSize())
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        PageHeader(stringResource(Res.string.space_comprendre), stringResource(Res.string.desc_comprendre))
                    }
                }
            }
            selected != null -> GuideDetail(selected, onBack = library::close, compact = compact, Modifier.fillMaxSize())
            else -> GuideList(library, Modifier.fillMaxSize(), compact = compact, withHeader = true)
        }
    }
}

@Composable
private fun GuideList(library: ContentLibrary, modifier: Modifier, compact: Boolean, withHeader: Boolean = false) {
    val palette = PpfTheme.palette
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(ContentTags.GUIDE_LIST)) {
        if (withHeader) {
            PageHeader(stringResource(Res.string.space_comprendre), stringResource(Res.string.desc_comprendre))
            Spacer(Modifier.height(24.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.guides_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }.weight(1f),
            )
            if (library.status != LibraryStatus.Offline && library.status != LibraryStatus.Unavailable) {
                PpfIconButton(Lucide.RefreshCw, stringResource(Res.string.content_refresh), library::refresh, Modifier.testTag(ContentTags.REFRESH), tint = palette.brand)
            }
        }
        StatusLine(library.status)
        Spacer(Modifier.height(12.dp))
        if (library.guides.isEmpty() && library.status != LibraryStatus.Loading) {
            Text(stringResource(Res.string.content_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        library.guides.forEach { guide ->
            GuideCard(guide, onOpen = { library.open(guide) }, selected = library.selected?.address == guide.address)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatusLine(status: LibraryStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(ContentTags.STATUS)) {
        if (status == LibraryStatus.Loading) {
            CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), color = PpfTheme.palette.brand)
            Spacer(Modifier.width(8.dp))
        }
        val text = when (status) {
            LibraryStatus.Loading -> stringResource(Res.string.content_status_loading)
            LibraryStatus.Unavailable -> stringResource(Res.string.content_status_unavailable)
            LibraryStatus.Offline -> stringResource(Res.string.content_status_offline)
            is LibraryStatus.Synced -> stringResource(Res.string.content_status_synced, status.pulled)
            LibraryStatus.Unreachable -> stringResource(Res.string.content_status_unreachable)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    }
}

@Composable
private fun GuideCard(guide: Article, onOpen: () -> Unit, selected: Boolean) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = Modifier.fillMaxWidth().testTag(ContentTags.GUIDE_CARD + guide.slug)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(guide.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (guide.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                Spacer(Modifier.width(8.dp))
                ExampleBadge()
            }
        }
        guide.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        Text(metaLine(guide), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@Composable
private fun GuideDetail(guide: Article, onBack: (() -> Unit)?, compact: Boolean, modifier: Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(ContentTags.GUIDE_DETAIL)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.content_back), onBack, Modifier.testTag(ContentTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PpfBadge(typeLabel(guide.type), BadgeStyle.BRAND)
                if (guide.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                    Spacer(Modifier.width(8.dp))
                    ExampleBadge()
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = guide.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(metaLine(guide), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            if (guide.topics.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.content_topics, guide.topics.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkSubtle,
                )
            }
            Spacer(Modifier.height(20.dp))
            SelectionContainer {
                MarkdownText(guide.body)
            }
        }
    }
}

@Composable
private fun typeLabel(type: ArticleType): String = stringResource(
    when (type) {
        ArticleType.FICHE_PRATIQUE -> Res.string.content_type_fiche
        ArticleType.DOSSIER -> Res.string.content_type_dossier
        ArticleType.DECRYPTAGE -> Res.string.content_type_decryptage
        ArticleType.ARTICLE -> Res.string.content_type_article
        ArticleType.REVENDICATION -> Res.string.space_revendications
    },
)

@Composable
private fun metaLine(guide: Article): String {
    val type = typeLabel(guide.type)
    val origin = stringResource(
        when (guide.origin) {
            ContentOrigin.HUMAN -> Res.string.content_origin_human
            ContentOrigin.AI_ASSISTED -> Res.string.content_origin_ai
        },
    )
    val updated = stringResource(Res.string.content_updated, CivilDate.fromEpochSeconds(guide.updatedAt).toFrench())
    return "$type · $updated · $origin"
}
