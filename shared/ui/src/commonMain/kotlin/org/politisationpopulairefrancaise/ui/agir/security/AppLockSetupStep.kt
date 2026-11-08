package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.security.PinRules
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.sec_confirm_pin
import org.politisationpopulairefrancaise.ui.resources.sec_new_pin
import org.politisationpopulairefrancaise.ui.resources.sec_setup_already
import org.politisationpopulairefrancaise.ui.resources.sec_setup_choose
import org.politisationpopulairefrancaise.ui.resources.sec_setup_choose_text
import org.politisationpopulairefrancaise.ui.resources.sec_setup_continue
import org.politisationpopulairefrancaise.ui.resources.sec_setup_enable
import org.politisationpopulairefrancaise.ui.resources.sec_setup_forgotten
import org.politisationpopulairefrancaise.ui.resources.sec_setup_skip
import org.politisationpopulairefrancaise.ui.resources.sec_setup_skip_confirm
import org.politisationpopulairefrancaise.ui.resources.sec_setup_skip_text
import org.politisationpopulairefrancaise.ui.resources.sec_setup_skip_title
import org.politisationpopulairefrancaise.ui.resources.sec_setup_skip_warning
import org.politisationpopulairefrancaise.ui.resources.sec_setup_text
import org.politisationpopulairefrancaise.ui.resources.sec_setup_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the step « Protéger PPF sur cet appareil ». */
object AppLockSetupTags {
    const val STEP = "security-setup"
    const val CHOOSE = "security-setup-choose"
    const val SKIP = "security-setup-skip"
    const val CONTINUE = "security-setup-continue"
}

/**
 * The step « Protéger PPF sur cet appareil » of the creation of an account (D-083, M33): the
 * lock by a code is **preselected** (principle 10, the most protective), with the code typed twice
 * right there; « Continuer sans code » is possible after a warning (« Confirmer » or « Annuler »).
 * Either way it can be changed later in « Mon espace », « Sécurité de cet appareil »
 * ([DeviceSecurityCard]). It says that a forgotten code only loses this device: the account comes
 * back with the recovery phrase.
 *
 * The creation of the account (pôle Communauté) places it after « Vérifier ma phrase »; nothing is
 * imposed without an account. [onDone] follows a code chosen (or a lock already on), [onSkip] the
 * confirmed « Continuer sans code ». The security state is the shell's ([LocalDeviceSecurity]):
 * outside the shell (no state, some tests), the step shows nothing and calls [onDone] at once.
 */
@Composable
fun AppLockSetupStep(onDone: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val security = LocalDeviceSecurity.current
    val done by rememberUpdatedState(onDone)
    if (security == null) {
        LaunchedEffect(Unit) { done() }
        return
    }
    val palette = PpfTheme.palette
    var withCode by rememberSaveable { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<StringResource?>(null) }
    var warning by remember { mutableStateOf(false) }
    val alreadyOn = security.settings.lockEnabled
    val submit: () -> Unit = {
        problem = null
        when {
            alreadyOn -> onDone()
            !withCode -> warning = true
            else -> {
                val local = PinRules.check(pin, confirmation)
                if (local != null) {
                    problem = problemText(local)
                } else {
                    security.enable(pin, confirmation) { result -> if (result == null) onDone() else problem = problemText(result) }
                }
            }
        }
    }
    Column(modifier.fillMaxWidth().testTag(AppLockSetupTags.STEP), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.sec_setup_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.sec_setup_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        if (alreadyOn) {
            Text(stringResource(Res.string.sec_setup_already), style = MaterialTheme.typography.bodyLarge)
        } else {
            Column(Modifier.selectableGroup()) {
                Choice(
                    selected = withCode,
                    label = stringResource(Res.string.sec_setup_choose),
                    text = stringResource(Res.string.sec_setup_choose_text),
                    tag = AppLockSetupTags.CHOOSE,
                ) { withCode = true }
                Choice(
                    selected = !withCode,
                    label = stringResource(Res.string.sec_setup_skip),
                    text = stringResource(Res.string.sec_setup_skip_text),
                    tag = AppLockSetupTags.SKIP,
                ) {
                    withCode = false
                    problem = null
                }
            }
            if (withCode) {
                PinField(pin, { pin = it }, stringResource(Res.string.sec_new_pin), submit, fieldModifier = Modifier.testTag(SecurityTags.PIN_NEW))
                PinField(confirmation, { confirmation = it }, stringResource(Res.string.sec_confirm_pin), submit, fieldModifier = Modifier.testTag(SecurityTags.PIN_CONFIRM))
                problem?.let { Announced(stringResource(it), Modifier.testTag(SecurityTags.PIN_PROBLEM)) }
            }
        }
        InfoBox(Lucide.KeyRound) {
            Text(stringResource(Res.string.sec_setup_forgotten), style = MaterialTheme.typography.bodyMedium)
        }
        PpfPrimaryButton(
            onClick = submit,
            text = stringResource(
                when {
                    alreadyOn || !withCode -> Res.string.sec_setup_continue
                    else -> Res.string.sec_setup_enable
                },
            ),
            icon = if (withCode && !alreadyOn) Lucide.Lock else null,
            enabled = !security.busy,
            modifier = Modifier.fillMaxWidth().testTag(AppLockSetupTags.CONTINUE),
        )
    }
    if (warning) {
        WarningDialog(
            title = stringResource(Res.string.sec_setup_skip_title),
            onDismiss = { warning = false },
            confirmLabel = stringResource(Res.string.sec_setup_skip_confirm),
            onConfirm = {
                warning = false
                onSkip()
            },
        ) {
            Text(stringResource(Res.string.sec_setup_skip_warning), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One of the two choices of the step: a radio button, its label and one sentence; the whole row is one control. */
@Composable
private fun Choice(selected: Boolean, label: String, text: String, tag: String, onSelect: () -> Unit) {
    val palette = PpfTheme.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = palette.brand, unselectedColor = palette.borderStrong))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}
