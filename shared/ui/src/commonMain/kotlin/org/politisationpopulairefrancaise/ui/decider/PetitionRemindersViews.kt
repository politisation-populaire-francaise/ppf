package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.Petition
import org.politisationpopulairefrancaise.core.decider.PetitionReminders
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_petition_eve_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_petition_eve_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_petition_week_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_petition_week_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_category_note
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_petition_ask
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_petition_offer
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_petition_stop
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The texts of a reminder of a petition, named after its title (an example keeps its [EXEMPLE]). */
suspend fun petitionReminderText(petition: Petition, key: String): ReminderText {
    val closes = petition.closesOn?.toFrench().orEmpty()
    return when (key) {
        PetitionReminders.WEEK_BEFORE -> ReminderText(
            getString(Res.string.decider_reminder_petition_week_title),
            getString(Res.string.decider_reminder_petition_week_detail, petition.title, closes),
        )
        else -> ReminderText(
            getString(Res.string.decider_reminder_petition_eve_title),
            getString(Res.string.decider_reminder_petition_eve_detail, petition.title, closes),
        )
    }
}

/** « Rappels » of an open petition with a closing date: the offer while nothing is asked, then the reminders to come. */
@Composable
internal fun PetitionRemindersSection(petition: Petition, reminders: DeciderRemindersState) {
    val palette = PpfTheme.palette
    val version = reminders.version
    val asked = remember(petition, version) { reminders.isAsked(petition) }
    val upcoming = remember(petition, version) { reminders.upcoming(petition) }
    Column(Modifier.fillMaxWidth().testTag(ElectionReminderTags.SECTION)) {
        Text(stringResource(Res.string.decider_reminders_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        if (!asked) {
            Text(stringResource(Res.string.decider_reminders_petition_offer), style = MaterialTheme.typography.bodyMedium)
            if (!reminders.categoryOn) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.decider_reminders_category_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            PpfPrimaryButton(
                onClick = { reminders.ask(petition) },
                text = stringResource(Res.string.decider_reminders_petition_ask),
                icon = Lucide.Bell,
                modifier = Modifier.testTag(ElectionReminderTags.ASK),
            )
        } else {
            ReminderCategoryNotice(reminders)
            UpcomingReminders(upcoming, reminders, onStop = { reminders.stop(petition) }, stopText = Res.string.decider_reminders_petition_stop)
        }
    }
}
