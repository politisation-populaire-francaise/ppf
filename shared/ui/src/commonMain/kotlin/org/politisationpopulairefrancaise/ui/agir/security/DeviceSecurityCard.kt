package org.politisationpopulairefrancaise.ui.agir.security

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.security.DeviceSecuritySettings
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.sec_after_0
import org.politisationpopulairefrancaise.ui.resources.sec_after_300
import org.politisationpopulairefrancaise.ui.resources.sec_after_60
import org.politisationpopulairefrancaise.ui.resources.sec_after_900
import org.politisationpopulairefrancaise.ui.resources.sec_after_title
import org.politisationpopulairefrancaise.ui.resources.sec_card_text
import org.politisationpopulairefrancaise.ui.resources.sec_card_title
import org.politisationpopulairefrancaise.ui.resources.sec_change
import org.politisationpopulairefrancaise.ui.resources.sec_code_only
import org.politisationpopulairefrancaise.ui.resources.sec_discreet_study
import org.politisationpopulairefrancaise.ui.resources.sec_help_text
import org.politisationpopulairefrancaise.ui.resources.sec_hide_text
import org.politisationpopulairefrancaise.ui.resources.sec_hide_toggle
import org.politisationpopulairefrancaise.ui.resources.sec_lock_now
import org.politisationpopulairefrancaise.ui.resources.sec_lock_toggle
import org.politisationpopulairefrancaise.ui.resources.sec_lock_toggle_text
import org.politisationpopulairefrancaise.ui.resources.sec_phone_legal
import org.politisationpopulairefrancaise.ui.resources.sec_phone_settings_1
import org.politisationpopulairefrancaise.ui.resources.sec_phone_settings_2
import org.politisationpopulairefrancaise.ui.resources.sec_phone_settings_3
import org.politisationpopulairefrancaise.ui.resources.sec_phone_settings_4
import org.politisationpopulairefrancaise.ui.resources.sec_phone_settings_title
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_code_only
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_code_only_on
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_hide
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_hide_action
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_lock
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_lock_action
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_lock_off
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_lock_on
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_phrase
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_phrase_text
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_text
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_title
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_wipe
import org.politisationpopulairefrancaise.ui.resources.sec_prepare_wipe_text
import org.politisationpopulairefrancaise.ui.resources.sec_state_off
import org.politisationpopulairefrancaise.ui.resources.sec_status_ok
import org.politisationpopulairefrancaise.ui.resources.sec_status_todo
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_button
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_hint
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The words of each delay of « Verrouiller après ». */
private fun afterLabel(seconds: Int): StringResource = when (seconds) {
    0 -> Res.string.sec_after_0
    60 -> Res.string.sec_after_60
    300 -> Res.string.sec_after_300
    else -> Res.string.sec_after_900
}

/** Which dialog of the security settings is open. */
private enum class SecurityDialog { ENABLE, CHANGE, DISABLE, SHOW_CONTENT, WIPE }

/**
 * « Sécurité de cet appareil », in « Mon espace » (M33): the lock by a code (S-43, off until the
 * user turns it on), the delay before it locks again, « masquer le contenu » (S-45, on by default,
 * Android only) and the quick erase (S-44). Nothing outside the shell (no security state).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceSecurityCard(modifier: Modifier = Modifier) {
    val security = LocalDeviceSecurity.current ?: return
    val palette = PpfTheme.palette
    val settings = security.settings
    var dialog by remember { mutableStateOf<SecurityDialog?>(null) }
    PpfCard(modifier.fillMaxWidth().testTag(SecurityTags.CARD)) {
        Text(stringResource(Res.string.sec_card_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.sec_card_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        ToggleRow(
            label = stringResource(Res.string.sec_lock_toggle),
            text = stringResource(Res.string.sec_lock_toggle_text),
            checked = settings.lockEnabled,
            onChange = { on -> dialog = if (on) SecurityDialog.ENABLE else SecurityDialog.DISABLE },
            modifier = Modifier.testTag(SecurityTags.LOCK_TOGGLE),
        )
        if (settings.lockEnabled) {
            Text(stringResource(Res.string.sec_after_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.selectableGroup()) {
                DeviceSecuritySettings.LOCK_AFTER_CHOICES.forEach { seconds ->
                    val selected = settings.lockAfterSeconds == seconds
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { security.setLockAfter(seconds) })
                            .testTag(SecurityTags.AFTER + seconds),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = palette.brand, unselectedColor = palette.borderStrong))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(afterLabel(seconds)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                PpfSecondaryButton(onClick = security::lockNow, text = stringResource(Res.string.sec_lock_now), icon = Lucide.Lock, small = true, modifier = Modifier.testTag(SecurityTags.LOCK_NOW))
                PpfSecondaryButton(onClick = { dialog = SecurityDialog.CHANGE }, text = stringResource(Res.string.sec_change), icon = Lucide.KeyRound, small = true, modifier = Modifier.testTag(SecurityTags.CHANGE))
            }
        }
        Text(stringResource(Res.string.sec_code_only), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.padding(vertical = 4.dp))
        HorizontalDivider(color = palette.border)
        if (canHideContentFromSystem) {
            ToggleRow(
                label = stringResource(Res.string.sec_hide_toggle),
                text = stringResource(Res.string.sec_hide_text),
                checked = settings.hideContent,
                // Hiding is the most protective: at once; showing again asks first (principle 10).
                onChange = { hide -> if (hide) security.setHideContent(true) else dialog = SecurityDialog.SHOW_CONTENT },
                modifier = Modifier.testTag(SecurityTags.HIDE_TOGGLE),
            )
            HorizontalDivider(color = palette.border)
        }
        Spacer(Modifier.height(8.dp))
        WipeButton(onClick = { dialog = SecurityDialog.WIPE })
        Text(stringResource(Res.string.sec_wipe_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.padding(top = 4.dp))
    }
    when (dialog) {
        SecurityDialog.ENABLE -> PinDialog(security, PinDialogMode.ENABLE) { dialog = null }
        SecurityDialog.CHANGE -> PinDialog(security, PinDialogMode.CHANGE) { dialog = null }
        SecurityDialog.DISABLE -> PinDialog(security, PinDialogMode.DISABLE) { dialog = null }
        SecurityDialog.SHOW_CONTENT -> ShowContentDialog(security) { dialog = null }
        SecurityDialog.WIPE -> WipeDialog(security, onDone = { dialog = null })
        null -> Unit
    }
}

@Composable
private fun WipeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PpfSecondaryButton(
        onClick = onClick,
        text = stringResource(Res.string.sec_wipe_button),
        icon = Lucide.TriangleExclamationPoint,
        modifier = modifier.fillMaxWidth().testTag(SecurityTags.WIPE),
    )
}

/** The quick erase in the AIDE sheet (S-44): one sentence and the button; nothing outside the shell. */
@Composable
fun HelpWipeBlock(modifier: Modifier = Modifier) {
    val security = LocalDeviceSecurity.current ?: return
    var open by remember { mutableStateOf(false) }
    Column(modifier.testTag(SecurityTags.HELP_WIPE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.sec_help_text), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
        WipeButton(onClick = { open = true })
    }
    if (open) WipeDialog(security, onDone = { open = false })
}

/**
 * « Préparer mon téléphone » (AG-56), in the protest mode: where PPF stands on this phone (lock,
 * code only, content hidden, recovery phrase noted, quick erase ready), each fixed in one gesture,
 * then the settings of the phone itself ([EXEMPLE], to be read by a lawyer). PPF never offers a
 * fingerprint or a face: it is always « code seul », so there is nothing to switch off before leaving.
 * Returns false when there is no security state (outside the shell).
 */
@Composable
fun PreparePhoneBlock(modifier: Modifier = Modifier): Boolean {
    val security = LocalDeviceSecurity.current ?: return false
    val palette = PpfTheme.palette
    val settings = security.settings
    var dialog by remember { mutableStateOf<SecurityDialog?>(null) }
    var phraseNoted by rememberSaveable { mutableStateOf(false) }
    Column(modifier.testTag(SecurityTags.PREPARE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.sec_prepare_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.sec_prepare_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        StatusItem("lock", Lucide.Lock, stringResource(Res.string.sec_prepare_lock), stringResource(if (settings.lockEnabled) Res.string.sec_prepare_lock_on else Res.string.sec_prepare_lock_off), done = settings.lockEnabled) {
            if (!settings.lockEnabled) {
                PpfSecondaryButton(onClick = { dialog = SecurityDialog.ENABLE }, text = stringResource(Res.string.sec_prepare_lock_action), icon = Lucide.Lock, small = true, modifier = Modifier.testTag(SecurityTags.PREPARE_LOCK))
            }
        }
        StatusItem("code-only", Lucide.KeyRound, stringResource(Res.string.sec_prepare_code_only), stringResource(Res.string.sec_prepare_code_only_on), done = true)
        if (canHideContentFromSystem) {
            StatusItem("hide", Lucide.EyeOff, stringResource(Res.string.sec_prepare_hide), stringResource(if (settings.hideContent) Res.string.sec_status_ok else Res.string.sec_state_off), done = settings.hideContent) {
                if (!settings.hideContent) {
                    PpfSecondaryButton(onClick = { security.setHideContent(true) }, text = stringResource(Res.string.sec_prepare_hide_action), icon = Lucide.EyeOff, small = true, modifier = Modifier.testTag(SecurityTags.PREPARE_HIDE))
                }
            }
        }
        PhraseItem(phraseNoted) { phraseNoted = it }
        StatusItem("wipe", Lucide.TriangleExclamationPoint, stringResource(Res.string.sec_prepare_wipe), stringResource(Res.string.sec_prepare_wipe_text), done = true) {
            WipeButton(onClick = { dialog = SecurityDialog.WIPE })
        }

        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.sec_phone_settings_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        listOf(Res.string.sec_phone_settings_1, Res.string.sec_phone_settings_2, Res.string.sec_phone_settings_3, Res.string.sec_phone_settings_4).forEach { tip ->
            Text("• " + stringResource(tip), style = MaterialTheme.typography.bodyMedium)
        }
        InfoBox(Lucide.Info) {
            Text(stringResource(Res.string.sec_phone_legal), style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(Res.string.sec_discreet_study), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
    when (dialog) {
        SecurityDialog.ENABLE -> PinDialog(security, PinDialogMode.ENABLE) { dialog = null }
        SecurityDialog.WIPE -> WipeDialog(security, onDone = { dialog = null })
        else -> Unit
    }
    return true
}

/** One line of « Préparer mon téléphone »: its state in words (never by colour alone) and, if needed, the gesture that fixes it. */
@Composable
private fun StatusItem(id: String, icon: ImageVector, title: String, state: String, done: Boolean, action: @Composable () -> Unit = {}) {
    val palette = PpfTheme.palette
    val status = stringResource(if (done) Res.string.sec_status_ok else Res.string.sec_status_todo)
    Column(Modifier.fillMaxWidth().testTag(SecurityTags.PREPARE_ITEM + id)) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = status }, verticalAlignment = Alignment.Top) {
            Icon(if (done) Lucide.CircleCheck else icon, contentDescription = null, tint = if (done) palette.success else palette.warning, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(state, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
        }
        Column(Modifier.padding(start = 30.dp, top = 4.dp)) { action() }
    }
}

/** « Phrase de récupération notée »: only the user knows; ticked in memory, never stored. */
@Composable
private fun PhraseItem(noted: Boolean, onChange: (Boolean) -> Unit) {
    val palette = PpfTheme.palette
    val status = stringResource(if (noted) Res.string.sec_status_ok else Res.string.sec_status_todo)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .toggleable(value = noted, role = Role.Checkbox, onValueChange = onChange)
            .semantics { stateDescription = status }
            .testTag(SecurityTags.PREPARE_ITEM + "phrase"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = noted, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = palette.brand, uncheckedColor = palette.borderStrong, checkmarkColor = palette.onBrand))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.sec_prepare_phrase), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(Res.string.sec_prepare_phrase_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}
