package org.politisationpopulairefrancaise.ui.glossaire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_course_kind
import org.politisationpopulairefrancaise.ui.resources.comprendre_kind_history
import org.politisationpopulairefrancaise.ui.resources.comprendre_kind_who
import org.politisationpopulairefrancaise.ui.resources.comprendre_sources
import org.politisationpopulairefrancaise.ui.resources.content_origin_ai
import org.politisationpopulairefrancaise.ui.resources.content_origin_human
import org.politisationpopulairefrancaise.ui.resources.content_type_article
import org.politisationpopulairefrancaise.ui.resources.content_type_decryptage
import org.politisationpopulairefrancaise.ui.resources.content_type_dossier
import org.politisationpopulairefrancaise.ui.resources.content_type_fiche
import org.politisationpopulairefrancaise.ui.resources.content_updated
import org.politisationpopulairefrancaise.ui.resources.glossaire_kind
import org.politisationpopulairefrancaise.ui.resources.glossaire_page_back
import org.politisationpopulairefrancaise.ui.resources.glossaire_page_close
import org.politisationpopulairefrancaise.ui.resources.glossaire_page_definition
import org.politisationpopulairefrancaise.ui.resources.glossaire_page_open_full
import org.politisationpopulairefrancaise.ui.resources.glossaire_page_related
import org.politisationpopulairefrancaise.ui.resources.glossaire_page_see_also
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * « En savoir plus »: the page of an entry over the content being read, which stays where it was
 * (full screen on a phone, a centred window on tablets and computers). Escape, the system's back
 * and a touch outside the window close it.
 */
@Composable
internal fun GlossaryPageDialog(state: GlossaryState, entry: GlossaryEntry) {
    Dialog(onDismissRequest = state::closePages, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Its own window: a selection of the content underneath must not reach it.
        DisableSelection {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                val full = maxWidth < 600.dp
                val shape = if (full) RectangleShape else MaterialTheme.shapes.medium
                val size = if (full) Modifier.fillMaxSize() else Modifier.widthIn(max = 680.dp).fillMaxWidth(0.9f).heightIn(max = maxHeight * 0.88f)
                Box(size.shadow(12.dp, shape).clip(shape).background(PpfTheme.palette.surface1).testTag(GlossaryTags.PAGE)) {
                    GlossaryEntryContent(
                        entry = entry,
                        state = state,
                        compact = full,
                        onOpenEntry = state::openPage,
                        onClose = state::closePages,
                        onBack = if (state.pages.size > 1) state::backPage else null,
                        backLabel = stringResource(Res.string.glossaire_page_back),
                        onOpenFull = { state.browse(entry.slug) },
                    )
                }
            }
        }
    }
}

/**
 * The page of an entry: the term, the short definition, the detailed explanation (in which the
 * other terms are spotted, never its own), « Voir aussi », the related contents, the sources, and
 * « Ouvrir le glossaire complet » when [onOpenFull] is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlossaryEntryContent(
    entry: GlossaryEntry,
    state: GlossaryState,
    compact: Boolean,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    onOpenFull: (() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val scroll = rememberScrollState()
    // Each entry starts at its top.
    LaunchedEffect(entry.slug) { scroll.scrollTo(0) }
    // The page is shown in a dialog or beside a list: its texts take the ink of the theme, whatever is around.
    CompositionLocalProvider(LocalContentColor provides palette.ink) {
    Column(modifier.verticalScroll(scroll).padding(if (compact) 16.dp else 24.dp)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null || onClose != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) PpfIconButton(Lucide.ArrowLeft, backLabel, onBack, Modifier.testTag(GlossaryTags.PAGE_BACK), tint = palette.ink)
                    Spacer(Modifier.weight(1f))
                    if (onClose != null) PpfIconButton(Lucide.X, stringResource(Res.string.glossaire_page_close), onClose, Modifier.testTag(GlossaryTags.PAGE_CLOSE), tint = palette.ink)
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PpfBadge(stringResource(Res.string.glossaire_kind), BadgeStyle.BRAND)
                if (entry.example) {
                    Spacer(Modifier.width(8.dp))
                    ExampleBadge()
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(entry.term, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(metaLine(entry), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            Spacer(Modifier.height(16.dp))
            InfoBox(Lucide.Info) {
                Text(stringResource(Res.string.glossaire_page_definition), style = MaterialTheme.typography.titleSmall, color = palette.brand, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(entry.definition, style = MaterialTheme.typography.bodyLarge)
            }
            if (entry.explanation.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                // The explanation is spotted too, but never for its own term.
                GlossaryScope(key = entry.slug, excluded = setOf(entry.slug)) {
                    SelectionContainer { MarkdownText(entry.explanation) }
                }
            }
            val seeAlso = entry.seeAlso.mapNotNull(state.index::entry)
            if (seeAlso.isNotEmpty()) {
                SectionTitle(stringResource(Res.string.glossaire_page_see_also))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    seeAlso.forEach { other ->
                        PpfSecondaryButton(onClick = { onOpenEntry(other.slug) }, text = other.term, small = true, modifier = Modifier.testTag(GlossaryTags.seeAlso(other.slug)))
                    }
                }
            }
            val related = entry.related.mapNotNull { state.related[it] }
            if (related.isNotEmpty()) {
                SectionTitle(stringResource(Res.string.glossaire_page_related))
                related.forEach { content ->
                    PpfCard(
                        onClick = { state.openRelated(content.address) },
                        modifier = Modifier.fillMaxWidth().testTag(GlossaryTags.related(content.address.substringAfterLast(':'))),
                    ) {
                        Text(stringResource(kindOf(content.type)), style = MaterialTheme.typography.labelLarge, color = palette.brand)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(content.title.removePrefix(GlossaryEntry.EXAMPLE_MARKER).trim(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (content.title.startsWith(GlossaryEntry.EXAMPLE_MARKER)) {
                                Spacer(Modifier.width(8.dp))
                                ExampleBadge()
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (entry.sources.isNotBlank()) {
                SectionTitle(stringResource(Res.string.comprendre_sources))
                SelectionContainer { MarkdownText(entry.sources, glossary = false) }
            }
            if (onOpenFull != null) {
                Spacer(Modifier.height(16.dp))
                PpfSecondaryButton(onClick = onOpenFull, text = stringResource(Res.string.glossaire_page_open_full), icon = Lucide.BookOpen, modifier = Modifier.testTag(GlossaryTags.OPEN_FULL))
            }
        }
    }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun metaLine(entry: GlossaryEntry): String {
    val kind = stringResource(Res.string.glossaire_kind)
    val updated = stringResource(Res.string.content_updated, CivilDate.fromEpochSeconds(entry.article.updatedAt).toFrench())
    val origin = stringResource(if (entry.article.origin == ContentOrigin.AI_ASSISTED) Res.string.content_origin_ai else Res.string.content_origin_human)
    return "$kind · $updated · $origin"
}

private fun kindOf(type: ArticleType): StringResource = when (type) {
    ArticleType.PARCOURS -> Res.string.comprendre_course_kind
    ArticleType.FICHE_PRATIQUE -> Res.string.content_type_fiche
    ArticleType.DOSSIER -> Res.string.content_type_dossier
    ArticleType.DECRYPTAGE -> Res.string.content_type_decryptage
    ArticleType.HISTOIRE -> Res.string.comprendre_kind_history
    ArticleType.QUI_DECIDE -> Res.string.comprendre_kind_who
    else -> Res.string.content_type_article
}
