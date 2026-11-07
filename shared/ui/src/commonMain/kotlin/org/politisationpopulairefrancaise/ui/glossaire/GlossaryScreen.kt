package org.politisationpopulairefrancaise.ui.glossaire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.comprendre.GlossaryIndex
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.glossaire_back_to_comprendre
import org.politisationpopulairefrancaise.ui.resources.glossaire_back_to_list
import org.politisationpopulairefrancaise.ui.resources.glossaire_count
import org.politisationpopulairefrancaise.ui.resources.glossaire_description
import org.politisationpopulairefrancaise.ui.resources.glossaire_kind
import org.politisationpopulairefrancaise.ui.resources.glossaire_none
import org.politisationpopulairefrancaise.ui.resources.glossaire_pick
import org.politisationpopulairefrancaise.ui.resources.glossaire_search
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * The full glossary, inside "Comprendre" (contract, section 2.4): a search that ignores accents and
 * case, the entries in alphabetical order under their letter, and the page of the chosen entry.
 * Like the other lists of "Comprendre": one pane on phones and tablets (the list, then the entry
 * with a back button), the list and the entry side by side on wide windows.
 */
@Composable
fun GlossaryScreen(state: GlossaryState, windowClass: WindowClass, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val twoPanes = windowClass.width == WindowWidthClass.EXPANDED
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val selected = state.selected?.let(state.index::entry)
    // The system's back closes the entry first (one pane), then the glossary.
    SystemBack(enabled = true) { if (selected != null && !twoPanes) state.select(null) else onBack() }
    CompositionLocalProvider(LocalContentColor provides PpfTheme.palette.ink) {
    Box(modifier.fillMaxSize().background(PpfTheme.palette.surface1).testTag(GlossaryTags.SCREEN)) {
        when {
            twoPanes -> Row(Modifier.fillMaxSize()) {
                GlossaryList(state, selected?.slug, onBack, Modifier.width(400.dp).fillMaxHeight(), compact = false)
                VerticalDivider(color = PpfTheme.palette.border)
                if (selected != null) {
                    GlossaryEntryContent(selected, state, compact = false, onOpenEntry = state::select, modifier = Modifier.fillMaxSize().testTag(GlossaryTags.DETAIL))
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        PageHeader(stringResource(Res.string.glossaire_kind), stringResource(Res.string.glossaire_description))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(Res.string.glossaire_pick), style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.inkMuted, modifier = Modifier.testTag(GlossaryTags.PICK))
                    }
                }
            }
            selected != null -> GlossaryEntryContent(
                entry = selected,
                state = state,
                compact = compact,
                onOpenEntry = state::select,
                modifier = Modifier.fillMaxSize().testTag(GlossaryTags.DETAIL),
                onBack = { state.select(null) },
                backLabel = stringResource(Res.string.glossaire_back_to_list),
            )
            else -> GlossaryList(state, null, onBack, Modifier.fillMaxSize(), compact)
        }
    }
    }
}

@Composable
private fun GlossaryList(state: GlossaryState, selected: String?, onBack: () -> Unit, modifier: Modifier, compact: Boolean) {
    val palette = PpfTheme.palette
    val index = state.index
    val query = state.query
    val results = remember(index, query) { index.search(query) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(GlossaryTags.LIST)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.glossaire_back_to_comprendre), onBack, Modifier.testTag(GlossaryTags.BACK), tint = palette.ink)
            Spacer(Modifier.height(8.dp))
            PageHeader(stringResource(Res.string.glossaire_kind), stringResource(Res.string.glossaire_description))
            Spacer(Modifier.height(16.dp))
            PpfTextField(
                value = query,
                onValueChange = { state.query = it },
                placeholder = stringResource(Res.string.glossaire_search),
                modifier = Modifier.fillMaxWidth().testTag(GlossaryTags.SEARCH),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                pluralStringResource(Res.plurals.glossaire_count, results.size, results.size),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (results.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.glossaire_none, query.trim()), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, modifier = Modifier.testTag(GlossaryTags.NONE))
            }
            var letter: String? = null
            results.forEach { entry ->
                val current = GlossaryIndex.letterOf(entry)
                if (current != letter) {
                    letter = current
                    Spacer(Modifier.height(16.dp))
                    Text(current, style = MaterialTheme.typography.titleLarge, color = palette.brand, modifier = Modifier.semantics { heading() }.testTag(GlossaryTags.letter(current)))
                    Spacer(Modifier.height(8.dp))
                }
                GlossaryRow(entry, selected = entry.slug == selected, onOpen = { state.select(entry.slug) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GlossaryRow(entry: GlossaryEntry, selected: Boolean, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = Modifier.fillMaxWidth().testTag(GlossaryTags.row(entry.slug))) {
        Row(verticalAlignment = Alignment.Top) {
            Text(entry.term, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (entry.example) {
                Spacer(Modifier.width(8.dp))
                ExampleBadge()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.definition, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
