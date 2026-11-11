package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.sec_lock_reminder_button

/** Test tags of the reminder to lock PPF. */
object LockReminderTags {
    const val REMINDER = "security-lock-reminder"
    const val ENABLE = "security-lock-reminder-enable"
}

/**
 * A line on a screen that holds something private (the list of relatives, AG-63, D-190): while PPF has no code,
 * [text] says why to set one and the button opens the same dialog as « Mon espace ». Nothing is imposed (principle
 * 10); once a code is set, or outside the shell (no security state), nothing is shown.
 */
@Composable
fun LockReminder(text: StringResource, modifier: Modifier = Modifier) {
    val security = LocalDeviceSecurity.current ?: return
    var open by remember { mutableStateOf(false) }
    if (security.settings.lock == null) {
        InfoBox(Lucide.Lock, modifier.testTag(LockReminderTags.REMINDER)) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            PpfSecondaryButton(
                onClick = { open = true },
                text = stringResource(Res.string.sec_lock_reminder_button),
                icon = Lucide.KeyRound,
                small = true,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LockReminderTags.ENABLE),
            )
        }
    }
    if (open) PinDialog(security, PinDialogMode.ENABLE) { open = false }
}
