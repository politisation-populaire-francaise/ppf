package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.security.ChangeResult
import org.politisationpopulairefrancaise.core.agir.security.PinProblem
import org.politisationpopulairefrancaise.core.agir.security.PinRules
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.sec_cancel
import org.politisationpopulairefrancaise.ui.resources.sec_change_confirm
import org.politisationpopulairefrancaise.ui.resources.sec_confirm_pin
import org.politisationpopulairefrancaise.ui.resources.sec_current_pin
import org.politisationpopulairefrancaise.ui.resources.sec_current_wrong
import org.politisationpopulairefrancaise.ui.resources.sec_disable_confirm
import org.politisationpopulairefrancaise.ui.resources.sec_disable_text
import org.politisationpopulairefrancaise.ui.resources.sec_disable_title
import org.politisationpopulairefrancaise.ui.resources.sec_enable_confirm
import org.politisationpopulairefrancaise.ui.resources.sec_enable_text
import org.politisationpopulairefrancaise.ui.resources.sec_enable_title
import org.politisationpopulairefrancaise.ui.resources.sec_change
import org.politisationpopulairefrancaise.ui.resources.sec_minutes
import org.politisationpopulairefrancaise.ui.resources.sec_new_pin
import org.politisationpopulairefrancaise.ui.resources.sec_problem_digits
import org.politisationpopulairefrancaise.ui.resources.sec_problem_long
import org.politisationpopulairefrancaise.ui.resources.sec_problem_mismatch
import org.politisationpopulairefrancaise.ui.resources.sec_problem_short
import org.politisationpopulairefrancaise.ui.resources.sec_problem_simple
import org.politisationpopulairefrancaise.ui.resources.sec_seconds
import org.politisationpopulairefrancaise.ui.resources.sec_show_confirm
import org.politisationpopulairefrancaise.ui.resources.sec_show_text
import org.politisationpopulairefrancaise.ui.resources.sec_show_title
import org.politisationpopulairefrancaise.ui.resources.sec_state_off
import org.politisationpopulairefrancaise.ui.resources.sec_state_on
import org.politisationpopulairefrancaise.ui.resources.sec_wait
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_confirm
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_not_yet
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_phrase
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_server
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_text
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the security of the device. */
object SecurityTags {
    const val CARD = "security-card"
    const val LOCK_TOGGLE = "security-lock-toggle"
    const val HIDE_TOGGLE = "security-hide-toggle"
    const val AFTER = "security-after-" // followed by the number of seconds
    const val LOCK_NOW = "security-lock-now"
    const val CHANGE = "security-change"
    const val WIPE = "security-wipe"
    const val PIN_CURRENT = "security-pin-current"
    const val PIN_NEW = "security-pin-new"
    const val PIN_CONFIRM = "security-pin-confirm"
    const val PIN_PROBLEM = "security-pin-problem"
    const val DIALOG_CONFIRM = "security-dialog-confirm"
    const val DIALOG_CANCEL = "security-dialog-cancel"
    const val LOCK_SCREEN = "security-lock-screen"
    const val UNLOCK_PIN = "security-unlock-pin"
    const val UNLOCK = "security-unlock"
    const val UNLOCK_MESSAGE = "security-unlock-message"
    const val FORGOT = "security-forgot"
    const val WIPED = "security-wiped"
    const val WIPE_RETRY = "security-wipe-retry"
    const val WIPE_CLOSE = "security-wipe-close"
    const val DISCREET_TOGGLE = "security-discreet-toggle"
    const val PREPARE = "security-prepare"
    const val PREPARE_ITEM = "security-prepare-" // followed by lock, code-only, hide, discreet, notifications, map, phrase, wipe
    const val PREPARE_LOCK = "security-prepare-lock-action"
    const val PREPARE_HIDE = "security-prepare-hide-action"
    const val PREPARE_DETAILS = "security-prepare-details-action"
    const val HELP_WIPE = "security-help-wipe"
}

internal fun problemText(problem: PinProblem): StringResource = when (problem) {
    PinProblem.TOO_SHORT -> Res.string.sec_problem_short
    PinProblem.TOO_LONG -> Res.string.sec_problem_long
    PinProblem.NOT_DIGITS -> Res.string.sec_problem_digits
    PinProblem.TOO_SIMPLE -> Res.string.sec_problem_simple
    PinProblem.MISMATCH -> Res.string.sec_problem_mismatch
}

/** A wait in words: « Trop d'essais. Réessayez dans 30 s. », « … dans 4 min 30 s. » */
@Composable
internal fun waitText(seconds: Long): String {
    val duration = if (seconds < 60) {
        stringResource(Res.string.sec_seconds, seconds.toInt())
    } else {
        stringResource(Res.string.sec_minutes, (seconds / 60).toInt(), (seconds % 60).toInt())
    }
    return stringResource(Res.string.sec_wait, duration)
}

/**
 * A field for a code: digits only, hidden as it is typed, the numeric keyboard on phones, its name
 * for the screen reader; Enter (or the keyboard's « OK ») submits.
 */
@Composable
internal fun PinField(value: String, onValueChange: (String) -> Unit, label: String, onSubmit: () -> Unit, modifier: Modifier = Modifier, fieldModifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = { typed -> onValueChange(typed.filter { it in '0'..'9' }.take(PinRules.MAX_LENGTH)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            textStyle = MaterialTheme.typography.titleLarge.copy(color = palette.ink),
            cursorBrush = SolidColor(palette.brand),
            modifier = fieldModifier.fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (enter && event.type == KeyEventType.KeyUp) onSubmit()
                    enter
                }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .background(palette.surface1, MaterialTheme.shapes.extraSmall)
                        .border(1.dp, palette.borderStrong, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

/** A sentence announced by the screen reader as soon as it appears (an error, a wait). */
@Composable
internal fun Announced(text: String, modifier: Modifier = Modifier, warning: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (warning) PpfTheme.palette.accentInk else PpfTheme.palette.inkMuted,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** A setting turned on or off: label, one sentence, a switch; the whole row is one control. */
@Composable
internal fun ToggleRow(label: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = PpfTheme.palette
    val state = stringResource(if (checked) Res.string.sec_state_on else Res.string.sec_state_off)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .semantics { stateDescription = state }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onBrand,
                checkedTrackColor = palette.brand,
                uncheckedThumbColor = palette.borderStrong,
                uncheckedTrackColor = palette.surface1,
                uncheckedBorderColor = palette.borderStrong,
            ),
        )
    }
}

/** The usual warning dialog of PPF: an icon, a title, a text, « Annuler » and the confirmation. */
@Composable
internal fun WarningDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    icon: ImageVector = Lucide.TriangleExclamationPoint,
    content: @Composable () -> Unit,
) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        icon = { Icon(icon, contentDescription = null, tint = palette.warning) },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.testTag(SecurityTags.DIALOG_CONFIRM)) {
                Text(confirmLabel, color = if (confirmEnabled) palette.accentInk else palette.inkSubtle)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(SecurityTags.DIALOG_CANCEL)) { Text(stringResource(Res.string.sec_cancel), color = palette.inkMuted) }
        },
    )
}

/** What a code dialog does. */
internal enum class PinDialogMode { ENABLE, CHANGE, DISABLE }

/**
 * Turning the lock on (the code twice), changing the code (the current one, then the new one
 * twice) or turning it off (a warning, principle 10, then the current code). Wrong current codes
 * count like those of the lock screen.
 */
@Composable
internal fun PinDialog(security: DeviceSecurityState, mode: PinDialogMode, onDone: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var wait by remember { mutableStateOf(0L) }
    val submit: () -> Unit = {
        message = null
        wait = 0
        when (mode) {
            PinDialogMode.ENABLE -> {
                val problem = PinRules.check(pin, confirmation)
                if (problem != null) message = problemText(problem) else security.enable(pin, confirmation) { result -> if (result == null) onDone() else message = problemText(result) }
            }
            PinDialogMode.CHANGE -> {
                val problem = PinRules.check(pin, confirmation)
                if (problem != null) {
                    message = problemText(problem)
                } else {
                    security.change(current, pin, confirmation) { result ->
                        when (result) {
                            ChangeResult.Changed -> onDone()
                            ChangeResult.WrongCurrent -> { message = Res.string.sec_current_wrong; wait = security.waitSeconds() }
                            is ChangeResult.Refused -> message = result.problem?.let(::problemText)
                        }
                    }
                }
            }
            PinDialogMode.DISABLE -> security.disable(current) { ok -> if (ok) onDone() else { message = Res.string.sec_current_wrong; wait = security.waitSeconds() } }
        }
    }
    val (title, confirm) = when (mode) {
        PinDialogMode.ENABLE -> Res.string.sec_enable_title to Res.string.sec_enable_confirm
        PinDialogMode.CHANGE -> Res.string.sec_change to Res.string.sec_change_confirm
        PinDialogMode.DISABLE -> Res.string.sec_disable_title to Res.string.sec_disable_confirm
    }
    WarningDialog(
        title = stringResource(title),
        onDismiss = onDone,
        confirmLabel = stringResource(confirm),
        onConfirm = submit,
        confirmEnabled = !security.busy,
        icon = if (mode == PinDialogMode.DISABLE) Lucide.TriangleExclamationPoint else Lucide.Lock,
    ) {
        Text(stringResource(if (mode == PinDialogMode.DISABLE) Res.string.sec_disable_text else Res.string.sec_enable_text), style = MaterialTheme.typography.bodyMedium)
        if (mode != PinDialogMode.ENABLE) {
            PinField(current, { current = it }, stringResource(Res.string.sec_current_pin), submit, fieldModifier = Modifier.testTag(SecurityTags.PIN_CURRENT))
        }
        if (mode != PinDialogMode.DISABLE) {
            PinField(pin, { pin = it }, stringResource(Res.string.sec_new_pin), submit, fieldModifier = Modifier.testTag(SecurityTags.PIN_NEW))
            PinField(confirmation, { confirmation = it }, stringResource(Res.string.sec_confirm_pin), submit, fieldModifier = Modifier.testTag(SecurityTags.PIN_CONFIRM))
        }
        message?.let { Announced(stringResource(it), Modifier.testTag(SecurityTags.PIN_PROBLEM)) }
        if (wait > 0) Announced(waitText(wait))
    }
}

/** Showing the content again in the recent applications: a warning first (principle 10). */
@Composable
internal fun ShowContentDialog(security: DeviceSecurityState, onDone: () -> Unit) {
    WarningDialog(
        title = stringResource(Res.string.sec_show_title),
        onDismiss = onDone,
        confirmLabel = stringResource(Res.string.sec_show_confirm),
        onConfirm = {
            security.setHideContent(false)
            onDone()
        },
    ) {
        Text(stringResource(Res.string.sec_show_text), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The confirmation of the quick erase (S-44): what is erased, the reminder of the recovery
 * phrase, what stays on the server; « Annuler » or « Tout effacer ». [extra] comes first (the
 * forgotten code of the lock screen).
 */
@Composable
internal fun WipeDialog(security: DeviceSecurityState, onDone: () -> Unit, extra: String? = null) {
    WarningDialog(
        title = stringResource(Res.string.sec_wipe_title),
        onDismiss = onDone,
        confirmLabel = stringResource(Res.string.sec_wipe_confirm),
        onConfirm = {
            onDone()
            security.wipe()
        },
    ) {
        if (extra != null) Text(extra, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.sec_wipe_text), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.Top) {
            Icon(Lucide.KeyRound, contentDescription = null, tint = PpfTheme.palette.warning, modifier = Modifier.padding(top = 2.dp).size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.sec_wipe_phrase), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.ink)
        }
        Text(stringResource(Res.string.sec_wipe_server), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.sec_wipe_not_yet), style = MaterialTheme.typography.bodySmall)
    }
}
