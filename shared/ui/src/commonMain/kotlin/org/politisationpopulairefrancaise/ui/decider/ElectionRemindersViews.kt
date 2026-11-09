package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.decider.ElectionCalendar
import org.politisationpopulairefrancaise.core.decider.ElectionReminderKind
import org.politisationpopulairefrancaise.core.reminders.Reminder
import org.politisationpopulairefrancaise.ui.agir.security.ToggleRow
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_ask
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_at
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_category_note
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_category_off
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_category_on
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_computer
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_eve
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_eve_hint
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_proxy
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_proxy_hint
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_registration
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_registration_hint
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_none
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_offer
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_permission_refused
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_remove
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_stop
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the reminders of an election (DE-28, step 3) and of a consultation (DE-27, one section per page). */
object ElectionReminderTags {
    const val SECTION = "decider-reminders"
    const val ASK = "decider-reminders-ask"
    const val STOP = "decider-reminders-stop"
    const val CATEGORY_OFF = "decider-reminders-category-off"
    const val CATEGORY_ON = "decider-reminders-category-on"
    const val NONE = "decider-reminders-none"
    fun kind(kind: ElectionReminderKind) = "decider-reminders-kind-${kind.key}"
    fun reminder(key: String) = "decider-reminders-item-$key"
    fun remove(key: String) = "decider-reminders-remove-$key"
}

/**
 * The section « Rappels » of the page of an election: the offer while nothing is asked (« Me rappeler ces dates »),
 * then a switch per kind of reminder, the reminders to come with their date, each one removable, and the way to
 * stop them all.
 */
@Composable
internal fun ElectionRemindersSection(election: ElectionCalendar, reminders: DeciderRemindersState) {
    val palette = PpfTheme.palette
    // Read again after each change of the store.
    val version = reminders.version
    val kinds = remember(election, version) { reminders.kindsOf(election) }
    val upcoming = remember(election, version) { reminders.upcoming(election) }
    Column(Modifier.fillMaxWidth().testTag(ElectionReminderTags.SECTION)) {
        Text(stringResource(Res.string.decider_reminders_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        if (kinds == null) {
            Text(stringResource(Res.string.decider_reminders_offer), style = MaterialTheme.typography.bodyMedium)
            if (!reminders.categoryOn) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.decider_reminders_category_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            PpfPrimaryButton(onClick = { reminders.ask(election) }, text = stringResource(Res.string.decider_reminders_ask), icon = Lucide.Bell, modifier = Modifier.testTag(ElectionReminderTags.ASK))
        } else {
            ChosenReminders(election, reminders, kinds, upcoming)
        }
    }
}

/** What the user asked for an election: the category, a switch per kind, the reminders to come, the way to stop. */
@Composable
private fun ChosenReminders(election: ElectionCalendar, reminders: DeciderRemindersState, kinds: Set<ElectionReminderKind>, upcoming: List<Reminder>) {
    Column(Modifier.fillMaxWidth()) {
        ReminderCategoryNotice(reminders)
        ElectionReminderKind.entries.forEach { kind ->
            ToggleRow(
                label = stringResource(kind.label),
                text = stringResource(kind.hint),
                checked = kind in kinds,
                onChange = { on -> reminders.setKind(election, kind, on) },
                modifier = Modifier.testTag(ElectionReminderTags.kind(kind)),
            )
        }
        Spacer(Modifier.height(8.dp))
        UpcomingReminders(upcoming, reminders, onStop = { reminders.stop(election) })
    }
}

/** Said above the reminders asked: « Votes et pétitions » turned off since (with the way to turn it on), or the system refusing. */
@Composable
internal fun ReminderCategoryNotice(reminders: DeciderRemindersState) {
    val palette = PpfTheme.palette
    if (!reminders.categoryOn) {
        InfoBox(Lucide.Bell, Modifier.testTag(ElectionReminderTags.CATEGORY_OFF)) {
            Text(stringResource(Res.string.decider_reminders_category_off), style = MaterialTheme.typography.bodyMedium)
            PpfTextButton(onClick = reminders::turnCategoryOn, text = stringResource(Res.string.decider_reminders_category_on), modifier = Modifier.testTag(ElectionReminderTags.CATEGORY_ON))
        }
        Spacer(Modifier.height(8.dp))
    } else if (reminders.permissionRefused) {
        Text(stringResource(Res.string.decider_reminders_permission_refused), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
    }
}

/** The reminders to come, each with its day and hour and a cross to remove it, then « Ne plus me rappeler ». */
@Composable
internal fun UpcomingReminders(upcoming: List<Reminder>, reminders: DeciderRemindersState, onStop: () -> Unit, stopText: StringResource = Res.string.decider_reminders_stop) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth()) {
        if (upcoming.isEmpty()) {
            Text(stringResource(Res.string.decider_reminders_none), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(ElectionReminderTags.NONE))
        }
        upcoming.forEachIndexed { index, reminder ->
            if (index > 0) HorizontalDivider(color = palette.border)
            val key = reminder.id.substringAfterLast('/')
            val moment = ParisTime.moment(reminder.dueAt)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(ElectionReminderTags.reminder(key)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(reminder.title, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.decider_reminders_at, moment.date.toFrench(), frenchClock(moment.hour, moment.minute)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
                Spacer(Modifier.width(8.dp))
                PpfIconButton(Lucide.X, stringResource(Res.string.decider_reminders_remove), onClick = { reminders.remove(reminder.id) }, modifier = Modifier.testTag(ElectionReminderTags.remove(key)))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.decider_reminders_computer), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        PpfTextButton(onClick = onStop, text = stringResource(stopText), modifier = Modifier.testTag(ElectionReminderTags.STOP))
    }
}

private val ElectionReminderKind.label get() = when (this) {
    ElectionReminderKind.REGISTRATION -> Res.string.decider_reminders_kind_registration
    ElectionReminderKind.PROXY -> Res.string.decider_reminders_kind_proxy
    ElectionReminderKind.EVE -> Res.string.decider_reminders_kind_eve
}

private val ElectionReminderKind.hint get() = when (this) {
    ElectionReminderKind.REGISTRATION -> Res.string.decider_reminders_kind_registration_hint
    ElectionReminderKind.PROXY -> Res.string.decider_reminders_kind_proxy_hint
    ElectionReminderKind.EVE -> Res.string.decider_reminders_kind_eve_hint
}
