package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.revendications.CustomRevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.rv_add_view
import org.politisationpopulairefrancaise.ui.resources.rv_cancel
import org.politisationpopulairefrancaise.ui.resources.rv_catalog_add
import org.politisationpopulairefrancaise.ui.resources.rv_catalog_empty
import org.politisationpopulairefrancaise.ui.resources.rv_catalog_title
import org.politisationpopulairefrancaise.ui.resources.rv_create_custom
import org.politisationpopulairefrancaise.ui.resources.rv_custom_limit
import org.politisationpopulairefrancaise.ui.resources.rv_done
import org.politisationpopulairefrancaise.ui.resources.rv_edit
import org.politisationpopulairefrancaise.ui.resources.rv_manage_intro
import org.politisationpopulairefrancaise.ui.resources.rv_manage_permanent
import org.politisationpopulairefrancaise.ui.resources.rv_manage_title
import org.politisationpopulairefrancaise.ui.resources.rv_move_down
import org.politisationpopulairefrancaise.ui.resources.rv_move_up
import org.politisationpopulairefrancaise.ui.resources.rv_name_save
import org.politisationpopulairefrancaise.ui.resources.rv_panel_close
import org.politisationpopulairefrancaise.ui.resources.rv_remove
import org.politisationpopulairefrancaise.ui.resources.rv_remove_confirm
import org.politisationpopulairefrancaise.ui.resources.rv_remove_keep
import org.politisationpopulairefrancaise.ui.resources.rv_remove_text
import org.politisationpopulairefrancaise.ui.resources.rv_remove_title
import org.politisationpopulairefrancaise.ui.resources.rv_rename
import org.politisationpopulairefrancaise.ui.resources.rv_reset
import org.politisationpopulairefrancaise.ui.resources.rv_reset_confirm
import org.politisationpopulairefrancaise.ui.resources.rv_reset_keep
import org.politisationpopulairefrancaise.ui.resources.rv_reset_text
import org.politisationpopulairefrancaise.ui.resources.rv_reset_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of "Personnaliser les vues". */
object ManageViewsTags {
    const val DIALOG = "views-manage"
    const val CLOSE = "views-close"
    const val ROW = "views-row-" // followed by the view id
    const val MOVE_UP = "views-up-" // same suffix
    const val MOVE_DOWN = "views-down-" // same suffix
    const val RENAME = "views-rename-" // same suffix
    const val EDIT = "views-edit-" // same suffix
    const val REMOVE = "views-remove-" // same suffix
    const val ADD = "views-add"
    const val CATALOG = "views-catalog-" // followed by the id of the built-in view
    const val CREATE = "views-create"
    const val RESET = "views-reset"
    const val DONE = "views-done"
    const val KEEP = "views-keep"
    const val CONFIRM = "views-confirm"
}

/**
 * "Personnaliser les vues": the views shown as tabs, in order, each with buttons to move it
 * (no drag-only gesture), to rename it and change its criteria when it is a custom view, and to
 * remove it ("Toutes" is permanent). "Ajouter une vue" lists the built-in views not shown and
 * offers to create a custom one. "Rétablir les vues par défaut" asks first, the safe choice first.
 *
 * Built-in views come back from the catalog, so they are removed at once; a custom view loses its
 * criteria, so its removal is confirmed.
 */
@Composable
fun ManageViewsDialog(
    state: RevendicationViewsState,
    compact: Boolean,
    onDismiss: () -> Unit,
    onEditCriteria: (CustomRevendicationView) -> Unit,
    onCreate: () -> Unit,
) {
    val palette = PpfTheme.palette
    var showCatalog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CustomRevendicationView?>(null) }
    var removing by remember { mutableStateOf<CustomRevendicationView?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    AdaptivePanel(compact = compact, onDismiss = onDismiss, modifier = Modifier.testTag(ManageViewsTags.DIALOG)) { close ->
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = if (compact) 0.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.rv_manage_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            PpfIconButton(Lucide.X, stringResource(Res.string.rv_panel_close), onClick = close, modifier = Modifier.testTag(ManageViewsTags.CLOSE), tint = palette.ink)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = palette.border)

        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 3.dp).size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.rv_manage_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            Spacer(Modifier.height(12.dp))
            val views = state.views
            views.forEach { view ->
                ViewRow(
                    view = view,
                    state = state,
                    compact = compact,
                    onRename = { renaming = it },
                    onEdit = onEditCriteria,
                    onRemove = { if (it is CustomRevendicationView) removing = it else state.remove(it.id) },
                )
                HorizontalDivider(color = palette.border)
            }

            Spacer(Modifier.height(12.dp))
            PpfSecondaryButton(
                onClick = { showCatalog = !showCatalog },
                text = stringResource(Res.string.rv_add_view),
                icon = if (showCatalog) Lucide.ChevronUp else Lucide.Plus,
                modifier = Modifier.testTag(ManageViewsTags.ADD),
            )
            if (showCatalog) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.rv_catalog_title), style = MaterialTheme.typography.titleSmall, color = palette.brand, modifier = Modifier.semantics { heading() })
                val catalog = state.catalog
                if (catalog.isEmpty()) {
                    Text(stringResource(Res.string.rv_catalog_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                }
                catalog.forEach { view ->
                    val label = viewLabel(view)
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(viewDescription(view), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                        }
                        PpfIconButton(
                            icon = Lucide.Plus,
                            contentDescription = stringResource(Res.string.rv_catalog_add, label),
                            onClick = { state.add(view) },
                            modifier = Modifier.testTag(ManageViewsTags.CATALOG + view.id),
                            tint = palette.brand,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PpfSecondaryButton(
                    onClick = onCreate,
                    text = stringResource(Res.string.rv_create_custom),
                    icon = Lucide.SlidersHorizontal,
                    enabled = state.canCreateCustom,
                    modifier = Modifier.testTag(ManageViewsTags.CREATE),
                )
                if (!state.canCreateCustom) {
                    Text(stringResource(Res.string.rv_custom_limit, RevendicationViews.MAX_CUSTOM_VIEWS), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
            }
        }

        HorizontalDivider(color = palette.border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).then(if (compact) Modifier.navigationBarsPadding() else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PpfTextButton(
                onClick = { confirmReset = true },
                text = stringResource(Res.string.rv_reset),
                enabled = !state.isDefault,
                color = palette.accentInk,
                modifier = Modifier.weight(1f, fill = false).testTag(ManageViewsTags.RESET),
            )
            Spacer(Modifier.weight(0.01f))
            PpfPrimaryButton(onClick = close, text = stringResource(Res.string.rv_done), modifier = Modifier.testTag(ManageViewsTags.DONE))
        }
    }

    renaming?.let { view ->
        var name by remember(view.id) { mutableStateOf(view.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = palette.surface2,
            titleContentColor = palette.ink,
            textContentColor = palette.inkMuted,
            shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(Res.string.rv_rename, view.name), style = MaterialTheme.typography.titleMedium) },
            text = { NameField(name, onChange = { name = it }) },
            confirmButton = {
                PpfTextButton(
                    onClick = { if (state.rename(view.id, name)) renaming = null },
                    text = stringResource(Res.string.rv_name_save),
                    enabled = RevendicationViews.normalizeName(name) != null,
                    modifier = Modifier.testTag(FilterPanelTags.NAME_CONFIRM),
                )
            },
            dismissButton = {
                PpfTextButton(onClick = { renaming = null }, text = stringResource(Res.string.rv_cancel), color = palette.inkMuted, modifier = Modifier.testTag(FilterPanelTags.NAME_CANCEL))
            },
        )
    }

    removing?.let { view ->
        SafeChoiceDialog(
            title = stringResource(Res.string.rv_remove_title, view.name),
            text = stringResource(Res.string.rv_remove_text),
            keepLabel = stringResource(Res.string.rv_remove_keep),
            confirmLabel = stringResource(Res.string.rv_remove_confirm),
            onKeep = { removing = null },
            onConfirm = {
                state.remove(view.id)
                removing = null
            },
        )
    }

    if (confirmReset) {
        SafeChoiceDialog(
            title = stringResource(Res.string.rv_reset_title),
            text = stringResource(Res.string.rv_reset_text),
            keepLabel = stringResource(Res.string.rv_reset_keep),
            confirmLabel = stringResource(Res.string.rv_reset_confirm),
            onKeep = { confirmReset = false },
            onConfirm = {
                state.reset()
                confirmReset = false
            },
        )
    }
}

/** One view of the list, with its buttons; on phones the buttons go under the name. */
@Composable
private fun ViewRow(
    view: RevendicationView,
    state: RevendicationViewsState,
    compact: Boolean,
    onRename: (CustomRevendicationView) -> Unit,
    onEdit: (CustomRevendicationView) -> Unit,
    onRemove: (RevendicationView) -> Unit,
) {
    val palette = PpfTheme.palette
    val label = viewLabel(view)
    val permanent = view.id == RevendicationViews.ALL_ID
    val texts: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (permanent) stringResource(Res.string.rv_manage_permanent) else viewDescription(view),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
            )
        }
    }
    val buttons: @Composable () -> Unit = {
        if (!permanent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PpfIconButton(Lucide.ChevronUp, stringResource(Res.string.rv_move_up, label), { state.moveUp(view.id) }, Modifier.testTag(ManageViewsTags.MOVE_UP + view.id), enabled = state.canMoveUp(view.id), tint = palette.ink)
                PpfIconButton(Lucide.ChevronDown, stringResource(Res.string.rv_move_down, label), { state.moveDown(view.id) }, Modifier.testTag(ManageViewsTags.MOVE_DOWN + view.id), enabled = state.canMoveDown(view.id), tint = palette.ink)
                if (view is CustomRevendicationView) {
                    PpfIconButton(Lucide.Pencil, stringResource(Res.string.rv_rename, label), { onRename(view) }, Modifier.testTag(ManageViewsTags.RENAME + view.id), tint = palette.ink)
                    PpfIconButton(Lucide.SlidersHorizontal, stringResource(Res.string.rv_edit, label), { onEdit(view) }, Modifier.testTag(ManageViewsTags.EDIT + view.id), tint = palette.ink)
                }
                PpfIconButton(Lucide.X, stringResource(Res.string.rv_remove, label), { onRemove(view) }, Modifier.testTag(ManageViewsTags.REMOVE + view.id), tint = palette.accentInk)
            }
        }
    }
    if (compact) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(ManageViewsTags.ROW + view.id)) {
            texts(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { buttons() }
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp).testTag(ManageViewsTags.ROW + view.id), verticalAlignment = Alignment.CenterVertically) {
            texts(Modifier.weight(1f))
            buttons()
        }
    }
}

/**
 * A warning before an irreversible change, the safe choice first and most visible (as in the
 * owner's mock-ups): a filled button to keep things as they are, then a text button to go ahead.
 */
@Composable
internal fun SafeChoiceDialog(
    title: String,
    text: String,
    keepLabel: String,
    confirmLabel: String,
    onKeep: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PpfPrimaryButton(onClick = onKeep, text = keepLabel, modifier = Modifier.fillMaxWidth().testTag(ManageViewsTags.KEEP))
                PpfTextButton(onClick = onConfirm, text = confirmLabel, color = palette.accentInk, modifier = Modifier.testTag(ManageViewsTags.CONFIRM))
            }
        },
    )
}

