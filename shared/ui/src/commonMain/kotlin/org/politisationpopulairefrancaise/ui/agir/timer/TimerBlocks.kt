package org.politisationpopulairefrancaise.ui.agir.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.timer.RelativeTimer
import org.politisationpopulairefrancaise.core.agir.timer.RelativeTimerPhase
import org.politisationpopulairefrancaise.ui.agir.manifestation.LocalHelpState
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.timer_alert_badge
import org.politisationpopulairefrancaise.ui.resources.timer_alert_danger
import org.politisationpopulairefrancaise.ui.resources.timer_alert_dismiss
import org.politisationpopulairefrancaise.ui.resources.timer_alert_message
import org.politisationpopulairefrancaise.ui.resources.timer_alert_offline
import org.politisationpopulairefrancaise.ui.resources.timer_alert_place
import org.politisationpopulairefrancaise.ui.resources.timer_alert_sync
import org.politisationpopulairefrancaise.ui.resources.timer_alert_sync_never
import org.politisationpopulairefrancaise.ui.resources.timer_alert_text
import org.politisationpopulairefrancaise.ui.resources.timer_alert_title
import org.politisationpopulairefrancaise.ui.resources.timer_cancel
import org.politisationpopulairefrancaise.ui.resources.timer_dismiss_confirm
import org.politisationpopulairefrancaise.ui.resources.timer_dismiss_text
import org.politisationpopulairefrancaise.ui.resources.timer_dismiss_title
import org.politisationpopulairefrancaise.ui.resources.timer_entry_armed
import org.politisationpopulairefrancaise.ui.resources.timer_entry_open
import org.politisationpopulairefrancaise.ui.resources.timer_entry_show
import org.politisationpopulairefrancaise.ui.resources.timer_entry_text
import org.politisationpopulairefrancaise.ui.resources.timer_relative_cancelled
import org.politisationpopulairefrancaise.ui.resources.timer_relative_waiting
import org.politisationpopulairefrancaise.ui.resources.timer_relatives_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/*
 * The blocks of « Sans nouvelles de moi » (AG-55, D-088) shown outside its screen: the entry of the
 * protest mode, the alerts of relatives (at the top of the home of "Agir" and in the help sheet),
 * the discreet lines « Minuteurs de vos proches », and the person's own timer on the home.
 */

/** The content of the entry « Sans nouvelles de moi » in the protest mode: one sentence and one button. */
@Composable
fun TimerEntry(timer: TimerState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val own = timer.own
    Column(modifier.testTag(TimerTags.ENTRY), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.timer_entry_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        if (own != null) {
            Text(stringResource(Res.string.timer_entry_armed, timeLabel(own.shownDeadline, timer.now)), style = MaterialTheme.typography.bodyMedium)
        }
        PpfPrimaryButton(
            onClick = onOpen,
            text = stringResource(if (own != null) Res.string.timer_entry_show else Res.string.timer_entry_open),
            icon = Lucide.Clock,
            modifier = Modifier.testTag(TimerTags.ENTRY_OPEN),
        )
    }
}

/**
 * The alerts of relatives whose deadline passed without a valid cancellation, one card each, with
 * the time of the last synchronization and the honest note that a cancellation may not have arrived.
 * Nothing when there is none, or outside the shell ([timer] null).
 */
@Composable
fun TimerAlerts(timer: TimerState?, modifier: Modifier = Modifier) {
    if (timer == null) return
    val alerts = timer.alerts
    if (alerts.isEmpty()) return
    var dismissing by remember { mutableStateOf<RelativeTimer?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        alerts.forEach { alert -> AlertCard(timer, alert, onDismiss = { dismissing = alert }) }
    }
    dismissing?.let { target ->
        DismissDialog(
            onCancel = { dismissing = null },
            onConfirm = {
                timer.dismiss(target)
                dismissing = null
            },
        )
    }
}

@Composable
private fun AlertCard(timer: TimerState, alert: RelativeTimer, onDismiss: () -> Unit) {
    val palette = PpfTheme.palette
    val now = timer.now
    val name = personName(alert.author)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = palette.accentTint,
        contentColor = palette.ink,
        border = BorderStroke(1.dp, palette.accent),
        modifier = Modifier.fillMaxWidth().testTag(TimerTags.ALERT + alert.id),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.timer_alert_title, name),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.accentInk,
                    modifier = Modifier.weight(1f).semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                Spacer(Modifier.width(8.dp))
                PpfBadge(stringResource(Res.string.timer_alert_badge), BadgeStyle.ACCENT)
            }
            Text(stringResource(Res.string.timer_alert_text, name, timeLabel(alert.deadline, now)), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(Res.string.timer_alert_message), style = MaterialTheme.typography.labelLarge)
            Text("« " + alert.text + " »", style = MaterialTheme.typography.bodyMedium)
            alert.place?.let { place -> Text(stringResource(Res.string.timer_alert_place, place), style = MaterialTheme.typography.bodyMedium) }
            val sync = timer.lastSyncAt
            Text(
                if (sync != null) stringResource(Res.string.timer_alert_sync, timeLabel(sync, now)) else stringResource(Res.string.timer_alert_sync_never),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(Res.string.timer_alert_offline), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(Res.string.timer_alert_danger), style = MaterialTheme.typography.labelLarge)
            PpfTextButton(
                onClick = onDismiss,
                text = stringResource(Res.string.timer_alert_dismiss),
                color = palette.accentInk,
                modifier = Modifier.testTag(TimerTags.ALERT_DISMISS + alert.id),
            )
        }
    }
}

/** Hiding an alert asks first: only after news by another way. */
@Composable
private fun DismissDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(stringResource(Res.string.timer_dismiss_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.timer_dismiss_text), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(TimerTags.DISMISS_CONFIRM)) {
                Text(stringResource(Res.string.timer_dismiss_confirm), color = palette.accentInk)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.timer_cancel), color = palette.inkMuted) }
        },
    )
}

/**
 * « Minuteurs de vos proches »: before the deadline, one discreet line per timer for which this
 * device was chosen (« … vous a désigné(e) pour un minuteur jusqu’à 18:30 »), and the recent
 * confirmations. Nothing about the content of the alert before its deadline.
 */
@Composable
fun TimerRelatives(timer: TimerState?, modifier: Modifier = Modifier) {
    if (timer == null) return
    val quiet = timer.quiet
    if (quiet.isEmpty()) return
    val palette = PpfTheme.palette
    val now = timer.now
    PpfCard(modifier.fillMaxWidth().testTag(TimerTags.RELATIVES)) {
        TimerHeading(stringResource(Res.string.timer_relatives_title), Lucide.Users)
        quiet.forEach { relative ->
            val name = personName(relative.author)
            val confirmed = relative.phase == RelativeTimerPhase.CANCELLED
            val text = if (confirmed) {
                stringResource(Res.string.timer_relative_cancelled, name, timeLabel(relative.cancelledAt ?: relative.deadline, now))
            } else {
                stringResource(Res.string.timer_relative_waiting, name, timeLabel(relative.deadline, now))
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {}.testTag(TimerTags.RELATIVE + relative.id),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (confirmed) Lucide.CircleCheck else Lucide.Clock, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * What the home of "Agir" shows of the timers, at its top: the alerts, the person's own timer (a
 * card that opens it in the protest mode, through [onOpenOwn]) and the discreet lines. Nothing
 * outside the shell or when there is nothing to show.
 */
@Composable
fun TimerHomeBlock(onOpenOwn: () -> Unit, modifier: Modifier = Modifier) {
    val timer = LocalHelpState.current?.timer ?: return
    val own = timer.own
    if (timer.alerts.isEmpty() && timer.quiet.isEmpty() && own == null) return
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimerAlerts(timer)
        if (own != null) {
            PpfCard(
                onClick = {
                    timer.requestScreen()
                    onOpenOwn()
                },
                modifier = Modifier.fillMaxWidth().testTag(TimerTags.HOME_OWN),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Clock, contentDescription = null, tint = palette.brand, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.timer_entry_armed, timeLabel(own.shownDeadline, timer.now)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
                }
            }
        }
        TimerRelatives(timer)
    }
}
