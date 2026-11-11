package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.revendications.AuthorScope
import org.politisationpopulairefrancaise.core.revendications.CustomRevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationContext
import org.politisationpopulairefrancaise.core.revendications.RevendicationCriterion
import org.politisationpopulairefrancaise.core.revendications.RevendicationQuery
import org.politisationpopulairefrancaise.core.revendications.RevendicationSort
import org.politisationpopulairefrancaise.core.revendications.RevendicationThemes
import org.politisationpopulairefrancaise.core.revendications.RevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.SafeArea
import org.politisationpopulairefrancaise.ui.components.safeAreaBottomPadding
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.rv_apply
import org.politisationpopulairefrancaise.ui.resources.rv_cancel
import org.politisationpopulairefrancaise.ui.resources.rv_clear_filters
import org.politisationpopulairefrancaise.ui.resources.rv_count
import org.politisationpopulairefrancaise.ui.resources.rv_create_title
import org.politisationpopulairefrancaise.ui.resources.rv_custom_limit
import org.politisationpopulairefrancaise.ui.resources.rv_edit_note
import org.politisationpopulairefrancaise.ui.resources.rv_edit_save
import org.politisationpopulairefrancaise.ui.resources.rv_edit_title
import org.politisationpopulairefrancaise.ui.resources.rv_locked
import org.politisationpopulairefrancaise.ui.resources.rv_name_create
import org.politisationpopulairefrancaise.ui.resources.rv_name_hint
import org.politisationpopulairefrancaise.ui.resources.rv_name_placeholder
import org.politisationpopulairefrancaise.ui.resources.rv_name_title
import org.politisationpopulairefrancaise.ui.resources.rv_panel_close
import org.politisationpopulairefrancaise.ui.resources.rv_panel_title
import org.politisationpopulairefrancaise.ui.resources.rv_panel_view
import org.politisationpopulairefrancaise.ui.resources.rv_private_note
import org.politisationpopulairefrancaise.ui.resources.rv_save_as_view
import org.politisationpopulairefrancaise.ui.resources.rv_section_authors
import org.politisationpopulairefrancaise.ui.resources.rv_section_sort
import org.politisationpopulairefrancaise.ui.resources.rv_section_status
import org.politisationpopulairefrancaise.ui.resources.rv_section_scopes
import org.politisationpopulairefrancaise.ui.resources.rv_section_territory
import org.politisationpopulairefrancaise.ui.resources.rv_section_text
import org.politisationpopulairefrancaise.ui.resources.rv_section_themes
import org.politisationpopulairefrancaise.ui.resources.rv_sort_popular_note
import org.politisationpopulairefrancaise.ui.resources.rv_territory_none
import org.politisationpopulairefrancaise.ui.resources.rv_text_placeholder
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the filter panel and of the naming of a view. */
object FilterPanelTags {
    const val PANEL = "filter-panel"
    const val CLOSE = "filter-close"
    const val SORT = "filter-sort-" // followed by the sort name in lowercase
    const val THEME = "filter-theme-" // followed by the theme id
    const val TERRITORY = "filter-territory-" // followed by the folded territory
    const val STATUS = "filter-status-" // followed by the status tag
    const val SCOPE = "filter-scope-" // followed by the scope name in lowercase
    const val AUTHORS = "filter-authors-" // followed by the scope name in lowercase
    const val TEXT = "filter-text"
    const val LOCKED = "filter-locked-" // followed by the criterion name in lowercase
    const val APPLY = "filter-apply"
    const val CLEAR = "filter-clear"
    const val SAVE_AS_VIEW = "filter-save-as-view"
    const val NAME_FIELD = "view-name-field"
    const val NAME_CONFIRM = "view-name-confirm"
    const val NAME_CANCEL = "view-name-cancel"
}

/** What the filter panel edits. */
sealed interface PanelMode {
    /** The temporary filters of [view], on top of its own criteria (shown locked). */
    data class Filters(val view: RevendicationView) : PanelMode

    /** The criteria of a custom view. */
    data class EditView(val view: CustomRevendicationView) : PanelMode

    /** The criteria and the name of a new custom view. */
    data object CreateView : PanelMode
}

/**
 * The filter panel of the wall: a bottom sheet on phones, a dialog (at most 560 dp wide) on wider
 * windows. Sections: sort, themes, territory, status, authors, words. The criteria fixed by the
 * current view are shown locked. The changes are a draft until the main button: "Afficher N
 * revendications" (N counted live), or the saving of the view being edited or created.
 *
 * @param initial the filters of the view, or the criteria of the edited view
 * @param onApply the new filters (mode [PanelMode.Filters])
 * @param onSaveAsView a name and the complete query of a new view built from the current one
 * @param onSaveView the criteria of the edited view, or the name and criteria of the new one
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevendicationFilterPanel(
    mode: PanelMode,
    initial: RevendicationQuery,
    demands: List<Article>,
    context: RevendicationContext,
    hasAccount: Boolean,
    canCreateView: Boolean,
    compact: Boolean,
    onDismiss: () -> Unit,
    onApply: (RevendicationQuery) -> Unit = {},
    onSaveAsView: (String, RevendicationQuery) -> Unit = { _, _ -> },
    onSaveView: (String?, RevendicationQuery) -> Unit = { _, _ -> },
) {
    val palette = PpfTheme.palette
    var draft by remember { mutableStateOf(initial) }
    var naming by remember { mutableStateOf(mode == PanelMode.CreateView) }
    var name by remember { mutableStateOf("") }
    val view = (mode as? PanelMode.Filters)?.view
    val locked = view?.fixedCriteria.orEmpty()
    val lockedQuery = view?.query ?: RevendicationQuery()
    val viewName = view?.let { viewLabel(it) }
    // The query that the list would show with this draft.
    val effective = view?.query?.overlay(draft) ?: draft
    val count = remember(demands, effective, context) { effective.apply(demands, context).size }
    val territories = remember(demands) { territoriesOf(demands) }
    val nameValid = RevendicationViews.normalizeName(name) != null

    AdaptivePanel(compact = compact, onDismiss = onDismiss, modifier = Modifier.testTag(FilterPanelTags.PANEL)) { close ->
        // Title row.
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = if (compact) 0.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val title = when (mode) {
                    is PanelMode.Filters -> stringResource(Res.string.rv_panel_title)
                    is PanelMode.EditView -> stringResource(Res.string.rv_edit_title, mode.view.name)
                    PanelMode.CreateView -> stringResource(Res.string.rv_create_title)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (viewName != null) {
                    Text(stringResource(Res.string.rv_panel_view, viewName), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
            }
            PpfIconButton(Lucide.X, stringResource(Res.string.rv_panel_close), onClick = close, modifier = Modifier.testTag(FilterPanelTags.CLOSE), tint = palette.ink)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = palette.border)

        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (mode == PanelMode.CreateView) {
                NameField(name, onChange = { name = it })
            }
            if (mode !is PanelMode.Filters) {
                Text(stringResource(Res.string.rv_edit_note), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }

            Section(stringResource(Res.string.rv_section_sort)) {
                val current = effective.sort ?: RevendicationSort.NEWEST
                Column(Modifier.selectableGroup()) {
                    RevendicationSort.entries.forEach { sort ->
                        PpfRadioRow(
                            label = stringResource(sortLabelResource(sort)),
                            selected = current == sort,
                            onClick = { draft = draft.copy(sort = sort) },
                            supporting = if (sort == RevendicationSort.POPULAR) stringResource(Res.string.rv_sort_popular_note) else null,
                            modifier = Modifier.testTag(FilterPanelTags.SORT + sort.name.lowercase()),
                        )
                    }
                }
            }

            Section(stringResource(Res.string.rv_section_themes)) {
                val isLocked = RevendicationCriterion.THEMES in locked
                if (isLocked) LockedNote(RevendicationCriterion.THEMES, viewName.orEmpty())
                val chosen = if (isLocked) lockedQuery.themes else draft.themes
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RevendicationThemes.ALL.forEach { theme ->
                        PpfSelectableChip(
                            label = themeLabel(theme.id),
                            selected = theme.id in chosen,
                            onClick = { draft = draft.copy(themes = draft.themes.toggle(theme.id)) },
                            enabled = !isLocked,
                            modifier = Modifier.testTag(FilterPanelTags.THEME + theme.id),
                        )
                    }
                }
            }

            Section(stringResource(Res.string.rv_section_territory)) {
                val isLocked = RevendicationCriterion.TERRITORIES in locked
                if (isLocked) LockedNote(RevendicationCriterion.TERRITORIES, viewName.orEmpty())
                val chosen = if (isLocked) lockedQuery.territories else draft.territories
                // The territories of the demands, plus any chosen one no demand carries any more.
                val keys = territories.keys.toList() + chosen.filter { it !in territories }
                if (keys.isEmpty()) {
                    Text(stringResource(Res.string.rv_territory_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key ->
                        PpfSelectableChip(
                            label = territoryLabel(key, territories),
                            selected = key in chosen,
                            onClick = { draft = draft.copy(territories = draft.territories.toggle(key)) },
                            enabled = !isLocked,
                            leadingIcon = if (key in chosen) null else Lucide.MapPin,
                            modifier = Modifier.testTag(FilterPanelTags.TERRITORY + key),
                        )
                    }
                }
            }

            Section(stringResource(Res.string.rv_section_status)) {
                val isLocked = RevendicationCriterion.STATUSES in locked
                if (isLocked) LockedNote(RevendicationCriterion.STATUSES, viewName.orEmpty())
                val chosen = if (isLocked) lockedQuery.statuses else draft.statuses
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RevendicationStatus.entries.forEach { status ->
                        PpfSelectableChip(
                            label = stringResource(statusLabelResource(status)),
                            selected = status in chosen,
                            onClick = { draft = draft.copy(statuses = draft.statuses.toggle(status)) },
                            enabled = !isLocked,
                            modifier = Modifier.testTag(FilterPanelTags.STATUS + status.tagValue),
                        )
                    }
                }
            }

            // MVP 1: where a demand comes from or stands (the movement, Parliament, closed, near me, my votes).
            Section(stringResource(Res.string.rv_section_scopes)) {
                val isLocked = RevendicationCriterion.SCOPES in locked
                if (isLocked) LockedNote(RevendicationCriterion.SCOPES, viewName.orEmpty())
                val chosen = if (isLocked) lockedQuery.scopes else draft.scopes
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    org.politisationpopulairefrancaise.core.revendications.RevendicationScope.entries.forEach { scope ->
                        PpfSelectableChip(
                            label = stringResource(scopeLabelResource(scope)),
                            selected = scope in chosen,
                            onClick = { draft = draft.copy(scopes = draft.scopes.toggle(scope)) },
                            enabled = !isLocked,
                            modifier = Modifier.testTag(FilterPanelTags.SCOPE + scope.name.lowercase()),
                        )
                    }
                }
            }

            Section(stringResource(Res.string.rv_section_authors)) {
                val isLocked = RevendicationCriterion.AUTHORS in locked
                if (isLocked) LockedNote(RevendicationCriterion.AUTHORS, viewName.orEmpty())
                val chosen = if (isLocked) lockedQuery.authors else draft.authors
                val scopes = AuthorScope.entries.filter { it != AuthorScope.MINE || hasAccount || chosen == AuthorScope.MINE }
                Column(Modifier.selectableGroup()) {
                    scopes.forEach { scope ->
                        PpfRadioRow(
                            label = stringResource(authorsLabelResource(scope)),
                            selected = chosen == scope,
                            onClick = { draft = draft.copy(authors = scope) },
                            enabled = !isLocked,
                            modifier = Modifier.testTag(FilterPanelTags.AUTHORS + scope.name.lowercase()),
                        )
                    }
                }
            }

            Section(stringResource(Res.string.rv_section_text)) {
                val isLocked = RevendicationCriterion.TEXT in locked
                if (isLocked) {
                    LockedNote(RevendicationCriterion.TEXT, viewName.orEmpty())
                    Text("« ${lockedQuery.text} »", style = MaterialTheme.typography.bodyLarge)
                } else {
                    PpfTextField(
                        value = draft.text,
                        onValueChange = { draft = draft.copy(text = it.take(MAX_TEXT_LENGTH)) },
                        placeholder = stringResource(Res.string.rv_text_placeholder),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(FilterPanelTags.TEXT),
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.rv_private_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
        }

        // Actions, always visible under the scrolling sections.
        HorizontalDivider(color = palette.border)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).then(if (compact) Modifier.safeAreaBottomPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (mode) {
                is PanelMode.Filters -> if (naming) {
                    NameField(name, onChange = { name = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PpfPrimaryButton(
                            onClick = { onSaveAsView(name, mode.view.query.overlay(draft).copy(sort = draft.sort ?: mode.view.query.sort)) },
                            text = stringResource(Res.string.rv_name_create),
                            enabled = nameValid,
                            modifier = Modifier.testTag(FilterPanelTags.NAME_CONFIRM),
                        )
                        PpfTextButton(onClick = { naming = false }, text = stringResource(Res.string.rv_cancel), color = palette.inkMuted, modifier = Modifier.testTag(FilterPanelTags.NAME_CANCEL))
                    }
                } else {
                    PpfPrimaryButton(
                        onClick = { onApply(draft) },
                        text = pluralStringResource(Res.plurals.rv_apply, count, count),
                        modifier = Modifier.fillMaxWidth().testTag(FilterPanelTags.APPLY),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PpfSecondaryButton(
                            onClick = { draft = RevendicationQuery(sort = draft.sort) },
                            text = stringResource(Res.string.rv_clear_filters),
                            enabled = draft.constraintCount > 0,
                            modifier = Modifier.testTag(FilterPanelTags.CLEAR),
                        )
                        PpfTextButton(
                            onClick = { naming = true },
                            text = stringResource(Res.string.rv_save_as_view),
                            enabled = canCreateView,
                            modifier = Modifier.testTag(FilterPanelTags.SAVE_AS_VIEW),
                        )
                    }
                    if (!canCreateView) LimitNote()
                }
                is PanelMode.EditView -> {
                    Text(pluralStringResource(Res.plurals.rv_count, count, count), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                    PpfPrimaryButton(
                        onClick = { onSaveView(null, draft) },
                        text = stringResource(Res.string.rv_edit_save),
                        modifier = Modifier.fillMaxWidth().testTag(FilterPanelTags.APPLY),
                    )
                }
                PanelMode.CreateView -> {
                    Text(pluralStringResource(Res.plurals.rv_count, count, count), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                    PpfPrimaryButton(
                        onClick = { onSaveView(name, draft) },
                        text = stringResource(Res.string.rv_name_create),
                        enabled = nameValid && canCreateView,
                        modifier = Modifier.fillMaxWidth().testTag(FilterPanelTags.NAME_CONFIRM),
                    )
                    if (!canCreateView) LimitNote()
                }
            }
        }
    }
}

/** At most this many characters of searched words. */
private const val MAX_TEXT_LENGTH = 80

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = PpfTheme.palette.brand, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** "Défini par la vue « Santé »": the criterion belongs to the view, not to the filters. */
@Composable
private fun LockedNote(criterion: RevendicationCriterion, viewName: String) {
    val palette = PpfTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(FilterPanelTags.LOCKED + criterion.name.lowercase())) {
        Icon(Lucide.Lock, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.rv_locked, viewName), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    }
}

@Composable
private fun LimitNote() {
    Text(
        text = stringResource(Res.string.rv_custom_limit, RevendicationViews.MAX_CUSTOM_VIEWS),
        style = MaterialTheme.typography.bodySmall,
        color = PpfTheme.palette.inkSubtle,
    )
}

/** The name of a view: a field, its title and the rule (1 to 40 characters). */
@Composable
internal fun NameField(name: String, onChange: (String) -> Unit) {
    val palette = PpfTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.rv_name_title), style = MaterialTheme.typography.labelLarge)
        PpfTextField(
            value = name,
            onValueChange = { onChange(it.take(RevendicationViews.NAME_MAX_LENGTH)) },
            placeholder = stringResource(Res.string.rv_name_placeholder),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(FilterPanelTags.NAME_FIELD),
        )
        Text(stringResource(Res.string.rv_name_hint, RevendicationViews.NAME_MAX_LENGTH), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

/**
 * The container of the panels of the wall: a bottom sheet on phones ([compact]), a dialog of at
 * most 560 dp otherwise. [content] receives the function that closes it (with the sheet's
 * animation on phones).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptivePanel(
    compact: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val palette = PpfTheme.palette
    if (compact) {
        // Fully open or closed: no half-open step, the actions stay at the bottom of the screen.
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        val scope = rememberCoroutineScope()
        val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
            containerColor = palette.surface2,
            contentColor = palette.ink,
            scrimColor = palette.scrim,
            contentWindowInsets = { SafeArea.current.sheet },
        ) {
            content(close)
        }
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = palette.surface2,
                    contentColor = palette.ink,
                    shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(bottom = 4.dp)) { content(onDismiss) }
                }
            }
        }
    }
}
