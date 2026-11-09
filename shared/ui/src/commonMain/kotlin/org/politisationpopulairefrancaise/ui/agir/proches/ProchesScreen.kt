package org.politisationpopulairefrancaise.ui.agir.proches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.proches.Proche
import org.politisationpopulairefrancaise.core.agir.proches.ProcheSuggestion
import org.politisationpopulairefrancaise.core.agir.proches.ProchesRules
import org.politisationpopulairefrancaise.core.agir.proches.SuggestionKind
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.crash.rememberTextSharer
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.proches_add
import org.politisationpopulairefrancaise.ui.resources.proches_back
import org.politisationpopulairefrancaise.ui.resources.proches_copied
import org.politisationpopulairefrancaise.ui.resources.proches_copy
import org.politisationpopulairefrancaise.ui.resources.proches_count
import org.politisationpopulairefrancaise.ui.resources.proches_description
import org.politisationpopulairefrancaise.ui.resources.proches_due_badge
import org.politisationpopulairefrancaise.ui.resources.proches_edit
import org.politisationpopulairefrancaise.ui.resources.proches_entry_no_subject
import org.politisationpopulairefrancaise.ui.resources.proches_entry_reminder
import org.politisationpopulairefrancaise.ui.resources.proches_entry_step
import org.politisationpopulairefrancaise.ui.resources.proches_entry_subjects
import org.politisationpopulairefrancaise.ui.resources.proches_full
import org.politisationpopulairefrancaise.ui.resources.proches_intro_text
import org.politisationpopulairefrancaise.ui.resources.proches_intro_title
import org.politisationpopulairefrancaise.ui.resources.proches_list_title
import org.politisationpopulairefrancaise.ui.resources.proches_next
import org.politisationpopulairefrancaise.ui.resources.proches_privacy_device
import org.politisationpopulairefrancaise.ui.resources.proches_privacy_erase
import org.politisationpopulairefrancaise.ui.resources.proches_privacy_kept
import org.politisationpopulairefrancaise.ui.resources.proches_privacy_title
import org.politisationpopulairefrancaise.ui.resources.proches_remove
import org.politisationpopulairefrancaise.ui.resources.proches_remove_cancel
import org.politisationpopulairefrancaise.ui.resources.proches_remove_confirm
import org.politisationpopulairefrancaise.ui.resources.proches_remove_question
import org.politisationpopulairefrancaise.ui.resources.proches_share
import org.politisationpopulairefrancaise.ui.resources.proches_share_note
import org.politisationpopulairefrancaise.ui.resources.proches_share_title
import org.politisationpopulairefrancaise.ui.resources.proches_suggestions_no_subject
import org.politisationpopulairefrancaise.ui.resources.proches_suggestions_none
import org.politisationpopulairefrancaise.ui.resources.proches_suggestions_title
import org.politisationpopulairefrancaise.ui.resources.proches_title
import org.politisationpopulairefrancaise.ui.resources.proches_today_title
import org.politisationpopulairefrancaise.ui.resources.proches_unavailable_text
import org.politisationpopulairefrancaise.ui.resources.proches_unavailable_title
import org.politisationpopulairefrancaise.ui.resources.proches_write_failed
import org.politisationpopulairefrancaise.ui.revendications.themeLabel
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Mes proches à mobiliser ». */
object ProchesTags {
    const val SCREEN = "proches-screen"
    const val BACK = "proches-back"
    const val UNAVAILABLE = "proches-unavailable"
    const val INTRO = "proches-intro"
    const val ADD = "proches-add"
    const val FULL = "proches-full"
    const val COUNT = "proches-count"
    const val WRITE_FAILED = "proches-write-failed"
    const val WARNING = "proches-warning"
    const val WARNING_CANCEL = "proches-warning-cancel"
    const val WARNING_START = "proches-warning-start"
    const val EDITOR = "proches-editor"
    const val NICKNAME = "proches-nickname"
    const val NICKNAME_ERROR = "proches-nickname-error"
    const val SUBJECT = "proches-subject-" // followed by the theme id
    const val STEP = "proches-step-" // followed by the step id
    const val REMINDER = "proches-reminder-" // followed by the choice id, "none" or "date"
    const val SAVE = "proches-save"
    const val CANCEL = "proches-cancel"
    const val TODAY = "proches-today"
    const val LIST = "proches-list"
    const val ENTRY = "proches-entry-" // followed by the id of the entry
    const val DUE = "proches-due-" // followed by the id of the entry
    const val NEXT = "proches-next-" // followed by the id of the entry
    const val EDIT = "proches-edit-" // followed by the id of the entry
    const val REMOVE = "proches-remove-" // followed by the id of the entry
    const val REMOVE_CONFIRM = "proches-remove-confirm-" // followed by the id of the entry
    const val REMOVE_CANCEL = "proches-remove-cancel-" // followed by the id of the entry
    const val SUGGESTIONS = "proches-suggestions-" // followed by the id of the entry
    const val NO_SUGGESTION = "proches-no-suggestion-" // followed by the id of the entry
    const val PRIVACY = "proches-privacy"

    /** One suggestion of an entry, and its buttons. */
    fun suggestion(entryId: String, index: Int): String = "proches-suggestion-$entryId-$index"

    fun share(entryId: String, index: Int): String = "proches-share-$entryId-$index"

    fun copy(entryId: String, index: Int): String = "proches-copy-$entryId-$index"
}

/** Lets tests record what « Copier le lien » copies; null uses the system's clipboard. */
val LocalProchesClipboard = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** The widest a column of text and forms grows, for readable lines. */
internal val PROCHES_READABLE = 720.dp

/** From this width the list shows two cards per row. */
private val TWO_COLUMNS = 840.dp

/** How the screen gives a content away: the system's share sheet where there is one, the clipboard always. */
internal class ProchesShare(val copy: (String) -> Unit, val share: ((String) -> Unit)?)

/**
 * « Mes proches à mobiliser » (AG-63): the private list of a few relatives, on this device only.
 * Before the first entry, the warning (GDPR, article 9: what one notes of someone else is sensitive);
 * then the reminders whose day has come (« À faire aujourd'hui »), the list with each relative's next
 * step and the contents of the device that suit them, with « Partager » and « Copier le lien ».
 * Nothing on this screen sends anything: the share sheet and the clipboard are the person's own gesture.
 *
 * One column on phones and tablets, two cards per row on wide windows; the form and the warning keep a
 * readable width. [onBack] leads back to the home of "Agir".
 */
@Composable
fun ProchesScreen(
    state: ProchesState,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    LaunchedEffect(state) { state.load() }
    // The system's back first closes the form or the warning, then leaves the rubric (the screen of "Agir").
    SystemBack(enabled = state.draft != null || state.warningShown) {
        if (state.draft != null) state.cancelDraft() else state.cancelWarning()
    }
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val scroll = rememberScrollState()
    val formOpen = state.draft != null || state.warningShown
    // Opening the form or the warning shows it from its top.
    LaunchedEffect(formOpen) { if (formOpen) scroll.scrollTo(0) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val recorder = LocalProchesClipboard.current
    val sharer = rememberTextSharer(stringResource(Res.string.proches_share_title))
    val share = ProchesShare(copy = recorder ?: { text -> clipboard.setText(AnnotatedString(text)) }, share = sharer)
    Column(
        modifier.fillMaxSize().background(palette.surface1).verticalScroll(scroll)
            .padding(if (compact) 16.dp else 24.dp).testTag(ProchesTags.SCREEN),
    ) {
        Column(Modifier.widthIn(max = if (windowClass.width == WindowWidthClass.EXPANDED) 1100.dp else PROCHES_READABLE), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header(onBack)
            val draft = state.draft
            when {
                !state.hasStorage -> InfoBox(Lucide.Info, Modifier.testTag(ProchesTags.UNAVAILABLE)) {
                    Text(stringResource(Res.string.proches_unavailable_title), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.proches_unavailable_text), style = MaterialTheme.typography.bodyMedium)
                }
                state.warningShown -> ProchesWarning(state, Modifier.widthIn(max = PROCHES_READABLE))
                draft != null -> ProcheEditor(state, draft, Modifier.widthIn(max = PROCHES_READABLE))
                state.loaded -> ListContent(state, share)
            }
            PrivacyBox(Modifier.widthIn(max = PROCHES_READABLE))
        }
    }
}

@Composable
private fun Header(onBack: (() -> Unit)?) {
    Column {
        if (onBack != null) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.proches_back), onBack, Modifier.testTag(ProchesTags.BACK), tint = PpfTheme.palette.ink)
            Spacer(Modifier.height(8.dp))
        }
        PageHeader(stringResource(Res.string.proches_title), stringResource(Res.string.proches_description))
    }
}

@Composable
private fun ListContent(state: ProchesState, share: ProchesShare) {
    val palette = PpfTheme.palette
    if (state.writeFailed) {
        Text(
            stringResource(Res.string.proches_write_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.accentInk,
            modifier = Modifier.testTag(ProchesTags.WRITE_FAILED).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    val ordered = state.ordered
    if (ordered.isEmpty()) {
        PpfCard(Modifier.widthIn(max = PROCHES_READABLE).fillMaxWidth().testTag(ProchesTags.INTRO)) {
            Text(stringResource(Res.string.proches_intro_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.proches_intro_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            Spacer(Modifier.height(12.dp))
            PpfPrimaryButton({ state.startAdding() }, stringResource(Res.string.proches_add), icon = Lucide.Plus, modifier = Modifier.testTag(ProchesTags.ADD))
        }
        return
    }
    val due = ordered.filter { it.isDue(state.today) }
    val rest = ordered.filter { !it.isDue(state.today) }
    if (due.isNotEmpty()) {
        SectionTitle(stringResource(Res.string.proches_today_title))
        Entries(due, state, share, due = true, tag = ProchesTags.TODAY)
    }
    if (rest.isNotEmpty()) {
        SectionTitle(stringResource(Res.string.proches_list_title))
        Entries(rest, state, share, due = false, tag = ProchesTags.LIST)
    }
    Text(
        stringResource(Res.string.proches_count, state.entries.size, ProchesRules.MAX_ENTRIES),
        style = MaterialTheme.typography.bodyMedium,
        color = palette.inkMuted,
        modifier = Modifier.testTag(ProchesTags.COUNT),
    )
    if (state.isFull) {
        Text(stringResource(Res.string.proches_full, ProchesRules.MAX_ENTRIES), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(ProchesTags.FULL))
    } else {
        PpfPrimaryButton({ state.startAdding() }, stringResource(Res.string.proches_add), icon = Lucide.Plus, enabled = !state.busy, modifier = Modifier.testTag(ProchesTags.ADD))
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.semantics { heading() })
}

/** The cards in rows: one per row on phones and tablets, two on wide windows. */
@Composable
private fun Entries(entries: List<Proche>, state: ProchesState, share: ProchesShare, due: Boolean, tag: String) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag)) {
        val columns = if (maxWidth >= TWO_COLUMNS) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            entries.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { entry ->
                        key(entry.id) { EntryCard(entry, state, share, due, Modifier.weight(1f)) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One relative: the nickname as a heading, the next step, the subjects, the reminder, the actions, then the contents that suit them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(entry: Proche, state: ProchesState, share: ProchesShare, due: Boolean, modifier: Modifier) {
    val palette = PpfTheme.palette
    var confirming by remember(entry.id) { mutableStateOf(false) }
    PpfCard(modifier.testTag(ProchesTags.ENTRY + entry.id)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.nickname, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (due) {
                Spacer(Modifier.width(8.dp))
                PpfBadge(stringResource(Res.string.proches_due_badge), BadgeStyle.ACCENT, Modifier.testTag(ProchesTags.DUE + entry.id))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.proches_entry_step, stringResource(ProchesLabels.step(entry.nextStep))), style = MaterialTheme.typography.bodyMedium)
        val subjects = entry.subjects.map { themeLabel(it) }
        Text(
            if (subjects.isEmpty()) stringResource(Res.string.proches_entry_no_subject) else stringResource(Res.string.proches_entry_subjects, subjects.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
        )
        entry.reminderDate?.let { date ->
            Text(stringResource(Res.string.proches_entry_reminder, date.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        Spacer(Modifier.height(10.dp))
        if (confirming) {
            Inset(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.proches_remove_question), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PpfTextButton({ confirming = false }, stringResource(Res.string.proches_remove_cancel), Modifier.testTag(ProchesTags.REMOVE_CANCEL + entry.id))
                    PpfTextButton(
                        onClick = {
                            confirming = false
                            state.remove(entry)
                        },
                        text = stringResource(Res.string.proches_remove_confirm),
                        modifier = Modifier.testTag(ProchesTags.REMOVE_CONFIRM + entry.id),
                        enabled = !state.busy,
                        color = palette.accentInk,
                    )
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.nextStep.next?.let { next ->
                    PpfSecondaryButton(
                        onClick = { state.advance(entry) },
                        text = stringResource(Res.string.proches_next, stringResource(ProchesLabels.step(next))),
                        small = true,
                        enabled = !state.busy,
                        icon = Lucide.ChevronRight,
                        modifier = Modifier.testTag(ProchesTags.NEXT + entry.id),
                    )
                }
                PpfSecondaryButton({ state.edit(entry) }, stringResource(Res.string.proches_edit), small = true, icon = Lucide.Pencil, modifier = Modifier.testTag(ProchesTags.EDIT + entry.id))
                PpfTextButton({ confirming = true }, stringResource(Res.string.proches_remove), Modifier.testTag(ProchesTags.REMOVE + entry.id), color = palette.accentInk)
            }
        }
        Spacer(Modifier.height(12.dp))
        Suggestions(entry, state, share)
    }
}

/** The contents of the device on the subjects of [entry], in the order that suits its next step. */
@Composable
private fun Suggestions(entry: Proche, state: ProchesState, share: ProchesShare) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(ProchesTags.SUGGESTIONS + entry.id), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.proches_suggestions_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        val suggestions = state.suggestionsFor(entry)
        when {
            entry.subjects.isEmpty() -> Text(stringResource(Res.string.proches_suggestions_no_subject), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            suggestions.isEmpty() -> Text(
                stringResource(Res.string.proches_suggestions_none),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                modifier = Modifier.testTag(ProchesTags.NO_SUGGESTION + entry.id),
            )
            else -> {
                suggestions.forEachIndexed { index, suggestion ->
                    key(suggestion.key) { SuggestionRow(entry.id, index, suggestion, share) }
                }
                Text(stringResource(Res.string.proches_share_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
        }
    }
}

/** One content: its kind, its title, its date for a mobilization, then « Partager » and « Copier le lien ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionRow(entryId: String, index: Int, suggestion: ProcheSuggestion, share: ProchesShare) {
    val palette = PpfTheme.palette
    var copied by remember(suggestion.key) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(4_000)
            copied = false
        }
    }
    Inset(Modifier.fillMaxWidth().testTag(ProchesTags.suggestion(entryId, index))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ProchesLabels.kindIcon(suggestion.kind), contentDescription = null, tint = palette.brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(ProchesLabels.kind(suggestion.kind)), style = MaterialTheme.typography.labelMedium, color = palette.inkMuted)
            if (suggestion.example) {
                Spacer(Modifier.width(8.dp))
                ExampleBadge()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(suggestion.title, style = MaterialTheme.typography.bodyLarge)
        val at = suggestion.at
        if (suggestion.kind == SuggestionKind.MOBILIZATION && at != null) {
            Text(ParisTime.moment(at).date.toFrench(), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val shareSheet = share.share
            if (shareSheet != null) {
                PpfSecondaryButton(
                    onClick = { shareSheet(suggestion.shareText()) },
                    text = stringResource(Res.string.proches_share),
                    small = true,
                    icon = Lucide.Share2,
                    modifier = Modifier.testTag(ProchesTags.share(entryId, index)),
                )
            }
            PpfSecondaryButton(
                onClick = {
                    share.copy(suggestion.shareText())
                    copied = true
                },
                text = stringResource(Res.string.proches_copy),
                small = true,
                icon = if (copied) Lucide.Check else Lucide.Copy,
                modifier = Modifier.testTag(ProchesTags.copy(entryId, index)),
            )
            if (copied) {
                Text(
                    stringResource(Res.string.proches_copied),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.success,
                    modifier = Modifier.padding(start = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun PrivacyBox(modifier: Modifier = Modifier) {
    InfoBox(Lucide.Lock, modifier.testTag(ProchesTags.PRIVACY)) {
        Text(stringResource(Res.string.proches_privacy_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.proches_privacy_kept), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.proches_privacy_device), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.proches_privacy_erase), style = MaterialTheme.typography.bodyMedium)
    }
}
