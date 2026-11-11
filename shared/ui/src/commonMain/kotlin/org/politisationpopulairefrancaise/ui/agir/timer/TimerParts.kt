package org.politisationpopulairefrancaise.ui.agir.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.timer.TimerFormats
import org.politisationpopulairefrancaise.core.agir.timer.TimerRecipient
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.timer_custom_hour
import org.politisationpopulairefrancaise.ui.resources.timer_custom_minute
import org.politisationpopulairefrancaise.ui.resources.timer_deadline_invalid
import org.politisationpopulairefrancaise.ui.resources.timer_deadline_preview
import org.politisationpopulairefrancaise.ui.resources.timer_duration_hm
import org.politisationpopulairefrancaise.ui.resources.timer_duration_m
import org.politisationpopulairefrancaise.ui.resources.timer_preset_custom
import org.politisationpopulairefrancaise.ui.resources.timer_preset_evening
import org.politisationpopulairefrancaise.ui.resources.timer_preset_hours
import org.politisationpopulairefrancaise.ui.resources.timer_time_other
import org.politisationpopulairefrancaise.ui.resources.timer_time_tomorrow
import org.politisationpopulairefrancaise.ui.resources.timer_time_yesterday
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Sans nouvelles de moi » (AG-55). */
object TimerTags {
    const val ENTRY = "timer-entry"
    const val ENTRY_OPEN = "timer-entry-open"
    const val SCREEN = "timer-screen"
    const val BACK = "timer-back"
    const val UNAVAILABLE = "timer-unavailable"
    const val SETUP = "timer-setup"
    const val RECIPIENT = "timer-recipient-" // followed by the key
    const val PRESET = "timer-preset-" // followed by the number of hours
    const val EVENING = "timer-preset-evening"
    const val CUSTOM = "timer-preset-custom"
    const val CUSTOM_HOUR = "timer-custom-hour"
    const val CUSTOM_MINUTE = "timer-custom-minute"
    const val DEADLINE_PREVIEW = "timer-deadline-preview"
    const val TEXT = "timer-text"
    const val PLACE = "timer-place"
    const val CODE = "timer-code"
    const val CODE_CONFIRM = "timer-code-confirm"
    const val DURESS = "timer-code-duress"
    const val PROBLEM = "timer-problem"
    const val CONTINUE = "timer-continue"
    const val CONFIRM_DIALOG = "timer-confirm-dialog"
    const val CONFIRM_ARM = "timer-confirm-arm"
    const val CONFIRM_CANCEL = "timer-confirm-cancel"
    const val ARMED = "timer-armed"
    const val COUNTDOWN = "timer-countdown"
    const val WAITING_DEPOSITS = "timer-waiting-deposits"
    const val OK = "timer-ok"
    const val POSTPONE = "timer-postpone"
    const val CODE_DIALOG = "timer-code-dialog"
    const val CODE_INPUT = "timer-code-input"
    const val CODE_SUBMIT = "timer-code-submit"
    const val CODE_CANCEL = "timer-code-cancel"
    const val CODE_MESSAGE = "timer-code-message"
    const val OUTCOME = "timer-outcome"
    const val OUTCOME_CLOSE = "timer-outcome-close"
    const val RELATIVES = "timer-relatives"
    const val RELATIVE = "timer-relative-" // followed by the timer id
    const val ALERT = "timer-alert-" // followed by the timer id
    const val ALERT_DISMISS = "timer-alert-dismiss-" // followed by the timer id
    const val DISMISS_CONFIRM = "timer-dismiss-confirm"
    const val HOME_OWN = "timer-home-own"
    const val HOME_OWN_OPEN = "timer-home-own-open"
}

/**
 * The deadline chosen on a screen: some hours after a base moment, this evening at 23:59, or a
 * precise time (today, or tomorrow when that time has passed). Times are those of Paris, like the
 * agenda, so that the person and the relatives read the same hour.
 */
internal class DeadlineChoice(initialHours: Int?) {
    var hours: Int? by mutableStateOf(initialHours)
        private set
    var evening: Boolean by mutableStateOf(false)
        private set
    var custom: Boolean by mutableStateOf(false)
        private set
    var hourText: String by mutableStateOf("")
    var minuteText: String by mutableStateOf("")

    fun selectHours(value: Int) {
        hours = value
        evening = false
        custom = false
    }

    fun selectEvening() {
        hours = null
        evening = true
        custom = false
    }

    fun selectCustom() {
        hours = null
        evening = false
        custom = true
    }

    fun deadline(base: Long, now: Long): Long? = deadlineFor(hours, evening, custom, hourText, minuteText, base, now)
}

/** The deadline a choice gives; null when the precise time is not a valid hour and minute. */
internal fun deadlineFor(hours: Int?, evening: Boolean, custom: Boolean, hourText: String, minuteText: String, base: Long, now: Long): Long? = when {
    hours != null -> base + hours * 3_600L
    evening -> ParisTime.toEpochSeconds(ParisTime.moment(now).date, 23, 59)
    custom -> {
        val hour = hourText.trim().toIntOrNull()
        val minute = minuteText.trim().ifEmpty { "0" }.toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            null
        } else {
            val today = ParisTime.toEpochSeconds(ParisTime.moment(now).date, hour, minute)
            if (today > now) today else ParisTime.toEpochSeconds(ParisTime.dateOf(ParisTime.epochDay(now) + 1), hour, minute)
        }
    }
    else -> null
}

/** Whether [deadline] can be chosen at [now]: between 15 minutes and 48 hours ahead. */
internal fun isValidDeadline(deadline: Long?, now: Long): Boolean =
    deadline != null && deadline - now >= TimerFormats.MIN_DURATION_SECONDS && deadline - now <= TimerFormats.MAX_DURATION_SECONDS

/** HH:MM, Paris time. */
internal fun clockText(at: Long): String {
    val moment = ParisTime.moment(at)
    return moment.hour.toString().padStart(2, '0') + ":" + moment.minute.toString().padStart(2, '0')
}

/** A moment as the screens write it: « 18:30 », « demain à 08:00 », « le 12 nov. 2026 à 08:00 ». */
@Composable
internal fun timeLabel(at: Long, now: Long): String {
    val time = clockText(at)
    return when (ParisTime.epochDay(at) - ParisTime.epochDay(now)) {
        0L -> time
        1L -> stringResource(Res.string.timer_time_tomorrow, time)
        -1L -> stringResource(Res.string.timer_time_yesterday, time)
        else -> stringResource(Res.string.timer_time_other, ParisTime.moment(at).date.toFrenchShort(), time)
    }
}

/** A duration as « 2 h 05 min » or « 12 min », rounded up to the minute. */
@Composable
internal fun durationLabel(seconds: Long): String {
    val minutes = (seconds.coerceAtLeast(0) + 59) / 60
    val hours = (minutes / 60).toInt()
    val rest = (minutes % 60).toInt()
    return if (hours > 0) stringResource(Res.string.timer_duration_hm, hours, rest) else stringResource(Res.string.timer_duration_m, rest)
}

/** The name the screens give a recipient or a relative: a legal team's name, else the short form of the key. */
internal fun personName(key: String, label: String? = null): String = label?.takeIf { it.isNotBlank() } ?: shortNpub(key)

internal fun recipientName(recipient: TimerRecipient): String = personName(recipient.key, recipient.label)

/** A small heading inside a card. */
@Composable
internal fun TimerHeading(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The deadline picker: a chip per number of hours in [presets] (after [base]), « Ce soir, 23:59 »,
 * « Heure précise » with its two fields, then the resulting hour in words, announced to screen readers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeadlinePicker(choice: DeadlineChoice, presets: List<Int>, base: Long, now: Long, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { hours ->
                PpfSelectableChip(
                    label = stringResource(Res.string.timer_preset_hours, hours),
                    selected = choice.hours == hours,
                    onClick = { choice.selectHours(hours) },
                    modifier = Modifier.testTag(TimerTags.PRESET + hours),
                )
            }
            PpfSelectableChip(
                label = stringResource(Res.string.timer_preset_evening),
                selected = choice.evening,
                onClick = choice::selectEvening,
                enabled = isValidDeadline(deadlineFor(null, true, false, "", "", base, now), now),
                modifier = Modifier.testTag(TimerTags.EVENING),
            )
            PpfSelectableChip(
                label = stringResource(Res.string.timer_preset_custom),
                selected = choice.custom,
                onClick = choice::selectCustom,
                modifier = Modifier.testTag(TimerTags.CUSTOM),
            )
        }
        if (choice.custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PpfTextField(
                    value = choice.hourText,
                    onValueChange = { typed -> choice.hourText = typed.filter { it in '0'..'9' }.take(2) },
                    placeholder = stringResource(Res.string.timer_custom_hour),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag(TimerTags.CUSTOM_HOUR),
                )
                PpfTextField(
                    value = choice.minuteText,
                    onValueChange = { typed -> choice.minuteText = typed.filter { it in '0'..'9' }.take(2) },
                    placeholder = stringResource(Res.string.timer_custom_minute),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag(TimerTags.CUSTOM_MINUTE),
                )
            }
        }
        val deadline = choice.deadline(base, now)
        val text = if (isValidDeadline(deadline, now) && deadline != null) {
            stringResource(Res.string.timer_deadline_preview, timeLabel(deadline, now))
        } else if (choice.hours != null || choice.evening || choice.custom) {
            stringResource(Res.string.timer_deadline_invalid)
        } else {
            null
        }
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.fillMaxWidth().testTag(TimerTags.DEADLINE_PREVIEW).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
