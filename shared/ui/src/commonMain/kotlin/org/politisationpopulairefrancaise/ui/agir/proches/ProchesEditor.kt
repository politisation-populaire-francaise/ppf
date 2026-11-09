package org.politisationpopulairefrancaise.ui.agir.proches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.proches.ProcheStep
import org.politisationpopulairefrancaise.core.agir.proches.ProchesRules
import org.politisationpopulairefrancaise.core.agir.proches.SuggestionKind
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.proches_cancel
import org.politisationpopulairefrancaise.ui.resources.proches_form_add_title
import org.politisationpopulairefrancaise.ui.resources.proches_form_edit_title
import org.politisationpopulairefrancaise.ui.resources.proches_full
import org.politisationpopulairefrancaise.ui.resources.proches_gone_error
import org.politisationpopulairefrancaise.ui.resources.proches_kind_call
import org.politisationpopulairefrancaise.ui.resources.proches_kind_course
import org.politisationpopulairefrancaise.ui.resources.proches_kind_fiche
import org.politisationpopulairefrancaise.ui.resources.proches_kind_mobilization
import org.politisationpopulairefrancaise.ui.resources.proches_nickname_count
import org.politisationpopulairefrancaise.ui.resources.proches_nickname_error
import org.politisationpopulairefrancaise.ui.resources.proches_nickname_help
import org.politisationpopulairefrancaise.ui.resources.proches_nickname_label
import org.politisationpopulairefrancaise.ui.resources.proches_nickname_placeholder
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_date
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_help
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_month
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_none
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_three_days
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_title
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_tomorrow
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_two_weeks
import org.politisationpopulairefrancaise.ui.resources.proches_reminder_week
import org.politisationpopulairefrancaise.ui.resources.proches_save
import org.politisationpopulairefrancaise.ui.resources.proches_step_done
import org.politisationpopulairefrancaise.ui.resources.proches_step_done_text
import org.politisationpopulairefrancaise.ui.resources.proches_step_invite
import org.politisationpopulairefrancaise.ui.resources.proches_step_invite_text
import org.politisationpopulairefrancaise.ui.resources.proches_step_share
import org.politisationpopulairefrancaise.ui.resources.proches_step_share_text
import org.politisationpopulairefrancaise.ui.resources.proches_step_talk
import org.politisationpopulairefrancaise.ui.resources.proches_step_talk_text
import org.politisationpopulairefrancaise.ui.resources.proches_step_title
import org.politisationpopulairefrancaise.ui.resources.proches_step_together
import org.politisationpopulairefrancaise.ui.resources.proches_step_together_text
import org.politisationpopulairefrancaise.ui.resources.proches_subjects_full
import org.politisationpopulairefrancaise.ui.resources.proches_subjects_help
import org.politisationpopulairefrancaise.ui.resources.proches_subjects_title
import org.politisationpopulairefrancaise.ui.resources.proches_warning_cancel
import org.politisationpopulairefrancaise.ui.resources.proches_warning_device
import org.politisationpopulairefrancaise.ui.resources.proches_warning_kept
import org.politisationpopulairefrancaise.ui.resources.proches_warning_sensitive
import org.politisationpopulairefrancaise.ui.resources.proches_warning_start
import org.politisationpopulairefrancaise.ui.resources.proches_warning_title
import org.politisationpopulairefrancaise.ui.revendications.themeLabel
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The quick choices of the reminder, counted in days from today (Paris time). */
enum class ProcheReminderChoice(val id: String, val days: Int) {
    TOMORROW("demain", 1),
    THREE_DAYS("trois-jours", 3),
    WEEK("une-semaine", 7),
    TWO_WEEKS("deux-semaines", 14),
    MONTH("un-mois", 30),
}

/** The texts of the ladder, the kinds of contents and the reminders. */
internal object ProchesLabels {
    fun step(step: ProcheStep): StringResource = when (step) {
        ProcheStep.TALK -> Res.string.proches_step_talk
        ProcheStep.SHARE -> Res.string.proches_step_share
        ProcheStep.INVITE -> Res.string.proches_step_invite
        ProcheStep.GO_TOGETHER -> Res.string.proches_step_together
        ProcheStep.DONE -> Res.string.proches_step_done
    }

    fun stepText(step: ProcheStep): StringResource = when (step) {
        ProcheStep.TALK -> Res.string.proches_step_talk_text
        ProcheStep.SHARE -> Res.string.proches_step_share_text
        ProcheStep.INVITE -> Res.string.proches_step_invite_text
        ProcheStep.GO_TOGETHER -> Res.string.proches_step_together_text
        ProcheStep.DONE -> Res.string.proches_step_done_text
    }

    fun kind(kind: SuggestionKind): StringResource = when (kind) {
        SuggestionKind.FICHE -> Res.string.proches_kind_fiche
        SuggestionKind.COURSE -> Res.string.proches_kind_course
        SuggestionKind.MOBILIZATION -> Res.string.proches_kind_mobilization
        SuggestionKind.CALL -> Res.string.proches_kind_call
    }

    fun kindIcon(kind: SuggestionKind): ImageVector = when (kind) {
        SuggestionKind.FICHE -> Lucide.BookOpen
        SuggestionKind.COURSE -> Lucide.Lightbulb
        SuggestionKind.MOBILIZATION -> Lucide.Calendar
        SuggestionKind.CALL -> Lucide.Megaphone
    }

    fun reminder(choice: ProcheReminderChoice): StringResource = when (choice) {
        ProcheReminderChoice.TOMORROW -> Res.string.proches_reminder_tomorrow
        ProcheReminderChoice.THREE_DAYS -> Res.string.proches_reminder_three_days
        ProcheReminderChoice.WEEK -> Res.string.proches_reminder_week
        ProcheReminderChoice.TWO_WEEKS -> Res.string.proches_reminder_two_weeks
        ProcheReminderChoice.MONTH -> Res.string.proches_reminder_month
    }
}

/**
 * Before the first entry (GDPR, article 9: what one notes of someone else may reveal their opinions):
 * what the list keeps, that it stays on this device and is never sent, that a nickname is enough.
 * « Annuler » keeps nothing; « Commencer ma liste » opens the form, and still nothing is written.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProchesWarning(state: ProchesState, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier.fillMaxWidth().testTag(ProchesTags.WARNING)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.ShieldCheck, contentDescription = null, tint = palette.brand, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.proches_warning_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.proches_warning_kept), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.proches_warning_device), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        InfoBox(Lucide.TriangleExclamationPoint) {
            Text(stringResource(Res.string.proches_warning_sensitive), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            PpfTextButton({ state.cancelWarning() }, stringResource(Res.string.proches_warning_cancel), Modifier.testTag(ProchesTags.WARNING_CANCEL))
            PpfPrimaryButton({ state.acceptWarning() }, stringResource(Res.string.proches_warning_start), modifier = Modifier.testTag(ProchesTags.WARNING_START))
        }
    }
}

/**
 * The form of one relative: the nickname (« un surnom suffit »), up to three subjects among the themes
 * of the contents (chips that are checkboxes), the next step (radio buttons), the reminder (radio
 * buttons), then « Annuler » and « Enregistrer ». What is typed stays in memory until « Enregistrer ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProcheEditor(state: ProchesState, draft: ProcheDraft, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier.fillMaxWidth().testTag(ProchesTags.EDITOR)) {
        Text(
            stringResource(if (draft.editing == null) Res.string.proches_form_add_title else Res.string.proches_form_edit_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))

        // The nickname
        Text(stringResource(Res.string.proches_nickname_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        PpfTextField(
            value = draft.nickname,
            onValueChange = { state.typeNickname(it) },
            placeholder = stringResource(Res.string.proches_nickname_placeholder),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(ProchesTags.NICKNAME),
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.proches_nickname_help), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Text(
            stringResource(Res.string.proches_nickname_count, draft.nickname.length, ProchesRules.MAX_NICKNAME),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle,
        )
        if (draft.error == ProcheDraftError.NO_NICKNAME) {
            ErrorLine(stringResource(Res.string.proches_nickname_error), Modifier.testTag(ProchesTags.NICKNAME_ERROR))
        }
        Spacer(Modifier.height(16.dp))

        // The subjects
        Text(stringResource(Res.string.proches_subjects_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.proches_subjects_help), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(8.dp))
        val fullSubjects = draft.subjects.size >= ProchesRules.MAX_SUBJECTS
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ProchesRules.SUBJECTS.forEach { id ->
                val selected = id in draft.subjects
                PpfSelectableChip(
                    label = themeLabel(id),
                    selected = selected,
                    onClick = { state.toggleSubject(id) },
                    enabled = selected || !fullSubjects,
                    modifier = Modifier.testTag(ProchesTags.SUBJECT + id),
                )
            }
        }
        if (fullSubjects) {
            Text(
                stringResource(Res.string.proches_subjects_full),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(16.dp))

        // The next step: one rung of the ladder
        Text(stringResource(Res.string.proches_step_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Column(Modifier.selectableGroup()) {
            ProcheStep.entries.forEach { step ->
                PpfRadioRow(
                    label = stringResource(ProchesLabels.step(step)),
                    selected = draft.step == step,
                    onClick = { state.chooseStep(step) },
                    supporting = stringResource(ProchesLabels.stepText(step)),
                    modifier = Modifier.testTag(ProchesTags.STEP + step.id),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // The reminder
        Text(stringResource(Res.string.proches_reminder_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.proches_reminder_help), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(4.dp))
        val chosen = draft.reminder
        val offered = ProcheReminderChoice.entries.map { it to state.dayIn(it.days) }
        Column(Modifier.selectableGroup()) {
            PpfRadioRow(
                label = stringResource(Res.string.proches_reminder_none),
                selected = chosen == null,
                onClick = { state.chooseReminder(null) },
                modifier = Modifier.testTag(ProchesTags.REMINDER + "none"),
            )
            offered.forEach { (choice, date) ->
                PpfRadioRow(
                    label = stringResource(ProchesLabels.reminder(choice)),
                    selected = chosen == date,
                    onClick = { state.chooseReminder(date) },
                    supporting = date.toFrench(),
                    modifier = Modifier.testTag(ProchesTags.REMINDER + choice.id),
                )
            }
            // A date chosen earlier that none of the quick choices gives any more: kept until another is chosen.
            if (chosen != null && offered.none { it.second == chosen }) {
                PpfRadioRow(
                    label = stringResource(Res.string.proches_reminder_date, chosen.toFrench()),
                    selected = true,
                    onClick = { state.chooseReminder(chosen) },
                    modifier = Modifier.testTag(ProchesTags.REMINDER + "date"),
                )
            }
        }
        when (draft.error) {
            ProcheDraftError.FULL -> ErrorLine(stringResource(Res.string.proches_full, ProchesRules.MAX_ENTRIES))
            ProcheDraftError.GONE -> ErrorLine(stringResource(Res.string.proches_gone_error))
            else -> Unit
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            PpfTextButton({ state.cancelDraft() }, stringResource(Res.string.proches_cancel), Modifier.testTag(ProchesTags.CANCEL))
            PpfPrimaryButton(
                onClick = { state.saveDraft() },
                text = stringResource(Res.string.proches_save),
                enabled = !state.busy,
                icon = Lucide.Check,
                modifier = Modifier.testTag(ProchesTags.SAVE),
            )
        }
    }
}

@Composable
private fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = PpfTheme.palette.accentInk,
        modifier = modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}
