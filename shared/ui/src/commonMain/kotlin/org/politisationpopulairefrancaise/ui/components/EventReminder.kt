package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.reminders.Reminder
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.event_reminder_day_before
import org.politisationpopulairefrancaise.ui.resources.event_reminder_note
import org.politisationpopulairefrancaise.ui.resources.event_reminder_on
import org.politisationpopulairefrancaise.ui.resources.event_reminder_remove
import org.politisationpopulairefrancaise.ui.resources.event_reminder_set
import org.politisationpopulairefrancaise.ui.resources.event_reminder_soon
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Me rappeler cet événement ». */
object EventReminderTags {
    const val SET = "event-reminder-set"
    const val ON = "event-reminder-on"
    const val REMOVE = "event-reminder-remove"
}

/** The moments of the reminders of an event starting at [startAt]: the day before and two hours before, those still to come. */
fun eventReminderTimes(startAt: Long, now: Long): List<Long> = listOf(startAt - 24 * 3_600, startAt - 2 * 3_600).filter { it > now }

/**
 * « Me rappeler cet événement » (S-98, TE-09): the day before and two hours before, programmed on the device only
 * ([ReminderStore], contract `docs/integration/rappels.md`), listed in « Mes rappels ». [owner] names the event
 * (`agir:mobilisation:<address>`); nothing is shown without a store or once the reminders would all be past.
 */
@Composable
fun EventReminderToggle(reminders: ReminderStore?, owner: String, title: String, startAt: Long, now: Long, modifier: Modifier = Modifier) {
    if (reminders == null) return
    var on by remember(owner) { mutableStateOf(reminders.upcomingOf(owner, now).isNotEmpty()) }
    val times = eventReminderTimes(startAt, now)
    if (times.isEmpty() && !on) return
    val dayBefore = stringResource(Res.string.event_reminder_day_before)
    val soon = stringResource(Res.string.event_reminder_soon)
    val palette = PpfTheme.palette
    Column(modifier) {
        if (on) {
            Text(stringResource(Res.string.event_reminder_on), style = MaterialTheme.typography.bodyMedium, color = palette.success, modifier = Modifier.testTag(EventReminderTags.ON))
            PpfTextButton(
                onClick = {
                    reminders.dropPlan(owner)
                    on = false
                },
                text = stringResource(Res.string.event_reminder_remove),
                modifier = Modifier.testTag(EventReminderTags.REMOVE),
            )
        } else {
            PpfSecondaryButton(
                onClick = {
                    times.forEach { due ->
                        val detail = if (startAt - due > 3 * 3_600) dayBefore else soon
                        reminders.schedule(Reminder("$owner:$due", owner, NotificationCategory.MOBILISATIONS, title, detail, dueAt = due, createdAt = now))
                    }
                    on = true
                },
                text = stringResource(Res.string.event_reminder_set),
                icon = Lucide.Bell,
                modifier = Modifier.testTag(EventReminderTags.SET),
            )
        }
        Text(stringResource(Res.string.event_reminder_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}
