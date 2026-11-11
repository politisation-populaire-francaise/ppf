package org.politisationpopulairefrancaise.ui.endirect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.endirect.LiveBlock
import org.politisationpopulairefrancaise.core.endirect.LivePreset
import org.politisationpopulairefrancaise.core.endirect.LiveView
import org.politisationpopulairefrancaise.core.reminders.Reminder
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.agir.calls.CallsLibrary
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.live_add_none
import org.politisationpopulairefrancaise.ui.resources.live_add_title
import org.politisationpopulairefrancaise.ui.resources.live_block_actu
import org.politisationpopulairefrancaise.ui.resources.live_block_alertes
import org.politisationpopulairefrancaise.ui.resources.live_block_appels
import org.politisationpopulairefrancaise.ui.resources.live_block_chiffres
import org.politisationpopulairefrancaise.ui.resources.live_block_chronologie
import org.politisationpopulairefrancaise.ui.resources.live_block_decider
import org.politisationpopulairefrancaise.ui.resources.live_block_en_cours
import org.politisationpopulairefrancaise.ui.resources.live_block_mobilisations
import org.politisationpopulairefrancaise.ui.resources.live_block_parlement
import org.politisationpopulairefrancaise.ui.resources.live_block_rappels
import org.politisationpopulairefrancaise.ui.resources.live_block_une
import org.politisationpopulairefrancaise.ui.resources.live_calls_due
import org.politisationpopulairefrancaise.ui.resources.live_calls_none
import org.politisationpopulairefrancaise.ui.resources.live_customize_done
import org.politisationpopulairefrancaise.ui.resources.live_customize_hint
import org.politisationpopulairefrancaise.ui.resources.live_customize_title
import org.politisationpopulairefrancaise.ui.resources.live_delete_view
import org.politisationpopulairefrancaise.ui.resources.live_move_down
import org.politisationpopulairefrancaise.ui.resources.live_move_up
import org.politisationpopulairefrancaise.ui.resources.live_new_view
import org.politisationpopulairefrancaise.ui.resources.live_new_view_create
import org.politisationpopulairefrancaise.ui.resources.live_new_view_hint
import org.politisationpopulairefrancaise.ui.resources.live_new_view_name
import org.politisationpopulairefrancaise.ui.resources.live_reminder_due
import org.politisationpopulairefrancaise.ui.resources.live_reminders_none
import org.politisationpopulairefrancaise.ui.resources.live_remove
import org.politisationpopulairefrancaise.ui.resources.live_reset
import org.politisationpopulairefrancaise.ui.resources.live_view_essentiel
import org.politisationpopulairefrancaise.ui.resources.live_view_mobilisation
import org.politisationpopulairefrancaise.ui.resources.live_view_politique
import org.politisationpopulairefrancaise.ui.resources.live_view_pres_de_chez_moi
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the blocks and of their setting. */
object LiveBoardTags {
    const val VIEWS = "en-direct-views"
    const val CUSTOMIZE = "en-direct-customize"
    const val EDITOR = "en-direct-editor"
    const val DONE = "en-direct-editor-done"
    const val RESET = "en-direct-editor-reset"
    const val NEW_VIEW_NAME = "en-direct-new-view-name"
    const val NEW_VIEW_CREATE = "en-direct-new-view-create"
    const val DELETE_VIEW = "en-direct-delete-view"
    const val REFRESH = "en-direct-refresh"
    const val CALLS = "en-direct-calls"
    const val REMINDERS = "en-direct-reminders"
    fun view(id: String) = "en-direct-view-$id"
    fun block(block: LiveBlock) = "en-direct-block-${block.id}"
    fun up(block: LiveBlock) = "en-direct-up-${block.id}"
    fun down(block: LiveBlock) = "en-direct-down-${block.id}"
    fun remove(block: LiveBlock) = "en-direct-remove-${block.id}"
    fun add(block: LiveBlock) = "en-direct-add-${block.id}"
}

internal fun titleOf(block: LiveBlock): StringResource = when (block) {
    LiveBlock.LIVE -> Res.string.live_block_en_cours
    LiveBlock.ALERTS -> Res.string.live_block_alertes
    LiveBlock.UPCOMING -> Res.string.live_block_mobilisations
    LiveBlock.CALLS -> Res.string.live_block_appels
    LiveBlock.DECIDER -> Res.string.live_block_decider
    LiveBlock.HEADLINE -> Res.string.live_block_une
    LiveBlock.FIGURES -> Res.string.live_block_chiffres
    LiveBlock.PARLIAMENT -> Res.string.live_block_parlement
    LiveBlock.REMINDERS -> Res.string.live_block_rappels
    LiveBlock.NEWS -> Res.string.live_block_actu
    LiveBlock.CHRONOLOGY -> Res.string.live_block_chronologie
}

@Composable
internal fun labelOf(view: LiveView): String = view.name ?: when (view.preset) {
    LivePreset.MOBILISATION -> stringResource(Res.string.live_view_mobilisation)
    LivePreset.POLITIQUE -> stringResource(Res.string.live_view_politique)
    LivePreset.PRES_DE_CHEZ_MOI -> stringResource(Res.string.live_view_pres_de_chez_moi)
    else -> stringResource(Res.string.live_view_essentiel)
}

private const val BLOCK_ITEMS: Int = 3

/** « Appels qui pressent »: the open calls whose deadline comes first; touching one opens it in Appels. */
@Composable
fun UrgentCallsBlock(calls: CallsLibrary, modifier: Modifier = Modifier, onOpen: (Call) -> Unit) {
    LaunchedEffect(calls) { calls.start() }
    val now = calls.now()
    val urgent = calls.calls
        .filter { it.isOpen && (it.deadline == null || it.deadline!! >= now) }
        .sortedBy { it.dueAt ?: Long.MAX_VALUE }
        .take(BLOCK_ITEMS)
    Column(modifier.testTag(LiveBoardTags.CALLS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (urgent.isEmpty()) Text(stringResource(Res.string.live_calls_none), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkSubtle)
        urgent.forEach { call -> LiveRow(call.displayTitle, call.dueAt?.let { stringResource(Res.string.live_calls_due, CivilDate.fromEpochSeconds(it).toFrenchShort()) }, call.isExample) { onOpen(call) } }
    }
}

/** « Mes rappels »: the next reminders of the device (DE-28, S-98); touching one opens what it points to. */
@Composable
fun RemindersBlock(store: ReminderStore, modifier: Modifier = Modifier, now: () -> Long = ::currentEpochSeconds, onOpen: (Reminder) -> Unit) {
    val reminders by produceState(emptyList<Reminder>(), store) {
        value = runCatching { withContext(Dispatchers.Default) { store.upcoming(now(), BLOCK_ITEMS.toLong()) } }.getOrDefault(emptyList())
    }
    Column(modifier.testTag(LiveBoardTags.REMINDERS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reminders.isEmpty()) Text(stringResource(Res.string.live_reminders_none), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkSubtle)
        reminders.forEach { reminder ->
            LiveRow(reminder.title, stringResource(Res.string.live_reminder_due, CivilDate.fromEpochSeconds(reminder.dueAt).toFrenchShort()), example = false) { onOpen(reminder) }
        }
    }
}

@Composable
private fun LiveRow(title: String, detail: String?, example: Boolean, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium, color = palette.surface2, contentColor = palette.ink) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (example) ExampleBadge()
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * « Personnaliser »: the blocks of the view on screen, to move up, down or take away, those to add,
 * the way back to a ready-made view, a new personal view and the removal of a personal one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveBoardEditor(state: LiveBoardState, modifier: Modifier = Modifier, available: Set<LiveBlock> = LiveBlock.entries.toSet()) {
    val palette = PpfTheme.palette
    val board = state.board
    val view = board.selected
    val viewLabel = labelOf(view)
    val addable = board.hidden.filter { it in available }
    PpfCard(modifier = modifier.fillMaxWidth().testTag(LiveBoardTags.EDITOR)) {
        Text(stringResource(Res.string.live_customize_title, viewLabel), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.live_customize_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))
        view.blocks.forEachIndexed { index, block ->
            val title = stringResource(titleOf(block))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                PpfIconButton(Lucide.ChevronUp, stringResource(Res.string.live_move_up, title), { state.move(block, -1) }, Modifier.testTag(LiveBoardTags.up(block)), enabled = index > 0)
                PpfIconButton(Lucide.ChevronDown, stringResource(Res.string.live_move_down, title), { state.move(block, 1) }, Modifier.testTag(LiveBoardTags.down(block)), enabled = index < view.blocks.lastIndex)
                PpfIconButton(Lucide.X, stringResource(Res.string.live_remove, title), { state.remove(block) }, Modifier.testTag(LiveBoardTags.remove(block)))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.live_add_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (addable.isEmpty()) Text(stringResource(Res.string.live_add_none), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            addable.forEach { block ->
                val title = stringResource(titleOf(block))
                PpfSecondaryButton(
                    onClick = { state.add(block) },
                    text = title,
                    small = true,
                    icon = Lucide.Plus,
                    modifier = Modifier.testTag(LiveBoardTags.add(block)),
                )
            }
        }
        if (view.modified) {
            Spacer(Modifier.height(8.dp))
            PpfTextButton({ state.reset() }, stringResource(Res.string.live_reset), Modifier.testTag(LiveBoardTags.RESET))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.live_new_view), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.live_new_view_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        var name by remember { mutableStateOf("") }
        val create = {
            if (name.isNotBlank()) {
                state.addPersonalView(name)
                name = ""
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = palette.surface2) {
                PpfTextField(
                    value = name,
                    onValueChange = { name = it.take(org.politisationpopulairefrancaise.core.endirect.LiveBoard.MAX_NAME) },
                    placeholder = stringResource(Res.string.live_new_view_name),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    modifier = Modifier.fillMaxWidth().padding(12.dp).testTag(LiveBoardTags.NEW_VIEW_NAME),
                )
            }
            Spacer(Modifier.width(8.dp))
            PpfSecondaryButton(onClick = create, text = stringResource(Res.string.live_new_view_create), enabled = name.isNotBlank(), modifier = Modifier.testTag(LiveBoardTags.NEW_VIEW_CREATE))
        }
        if (view.preset == null) {
            Spacer(Modifier.height(8.dp))
            PpfTextButton({ state.removePersonalView(view.id) }, stringResource(Res.string.live_delete_view, viewLabel), Modifier.testTag(LiveBoardTags.DELETE_VIEW), color = palette.accentInk)
        }
        Spacer(Modifier.height(16.dp))
        PpfPrimaryButton({ state.editing = false }, stringResource(Res.string.live_customize_done), Modifier.fillMaxWidth().testTag(LiveBoardTags.DONE))
    }
}
