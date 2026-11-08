package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyNumbers
import org.politisationpopulairefrancaise.core.agir.security.UnlockResult
import org.politisationpopulairefrancaise.ui.agir.manifestation.EmergencyNumbersBlock
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.Wordmark
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.sec_checking
import org.politisationpopulairefrancaise.ui.resources.sec_close
import org.politisationpopulairefrancaise.ui.resources.sec_forgot
import org.politisationpopulairefrancaise.ui.resources.sec_forgot_text
import org.politisationpopulairefrancaise.ui.resources.sec_lock_emergency
import org.politisationpopulairefrancaise.ui.resources.sec_lock_text
import org.politisationpopulairefrancaise.ui.resources.sec_lock_title
import org.politisationpopulairefrancaise.ui.resources.sec_pin_label
import org.politisationpopulairefrancaise.ui.resources.sec_unlock
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_done
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_failed
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_close_yourself
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_failed_parts
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_restore
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_retry
import org.politisationpopulairefrancaise.ui.resources.sec_wipe_running
import org.politisationpopulairefrancaise.ui.resources.sec_wrong
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * Wraps the whole interface (S-43, S-44, S-45): applies « masquer le contenu » to the window,
 * follows the application going to the background and back, and, while the application is locked
 * or erased, hides the interface (not drawn, nothing for the screen reader) under the lock screen.
 * The interface stays composed underneath, so that a draft survives a lock.
 */
@Composable
fun DeviceSecurityGate(security: DeviceSecurityState, content: @Composable () -> Unit) {
    HideContentFromSystem(security.settings.hideContent)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, security) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> security.onBackground()
                Lifecycle.Event.ON_START -> security.onForeground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val erased = security.wipeStatus == WipeStatus.ERASING || security.wipeStatus == WipeStatus.DONE
    val hidden = security.isLocked || erased
    Box(Modifier.fillMaxSize().then(if (hidden) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { } else Modifier)) {
        content()
    }
    when {
        erased -> WipedScreen(security)
        security.isLocked -> LockScreen(security)
    }
    if (security.wipeStatus == WipeStatus.FAILED) {
        WarningDialog(
            title = stringResource(Res.string.sec_wipe_failed),
            onDismiss = security::dismissWipe,
            confirmLabel = stringResource(Res.string.sec_wipe_retry),
            onConfirm = {
                security.dismissWipe()
                security.wipe()
            },
        ) {
            Text(stringResource(Res.string.sec_wipe_failed_parts, security.failedParts.size), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A window over everything, that neither the back key nor a tap outside closes. */
@Composable
private fun FullScreen(title: String, tag: String, content: @Composable () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
            Surface(
                color = PpfTheme.palette.surface1,
                contentColor = PpfTheme.palette.ink,
                modifier = Modifier.fillMaxSize().semantics { paneTitle = title }.testTag(tag),
            ) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * The lock screen: the code (hidden, numeric keyboard, Enter submits), the growing wait after
 * wrong codes, the 15 and the 112 without unlocking, and « Code oublié ? », whose only way out is
 * the quick erase.
 */
@Composable
private fun LockScreen(security: DeviceSecurityState) {
    val palette = PpfTheme.palette
    val title = stringResource(Res.string.sec_lock_title)
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var wait by remember { mutableStateOf(security.waitSeconds()) }
    var forgot by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait = security.waitSeconds()
        }
    }
    val submit: () -> Unit = {
        if (pin.isNotEmpty() && wait == 0L && !security.busy) {
            security.unlock(pin) { result ->
                pin = ""
                when (result) {
                    UnlockResult.Unlocked -> wrong = false
                    is UnlockResult.Wrong -> {
                        wrong = true
                        wait = result.waitSeconds
                    }
                    is UnlockResult.TooEarly -> wait = result.waitSeconds
                }
            }
        }
    }
    FullScreen(title, SecurityTags.LOCK_SCREEN) {
        Wordmark()
        Icon(Lucide.Lock, contentDescription = null, tint = palette.brand, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.sec_lock_text), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        PinField(pin, { pin = it }, stringResource(Res.string.sec_pin_label), submit, fieldModifier = Modifier.focusRequester(focus).testTag(SecurityTags.UNLOCK_PIN))
        Box(Modifier.testTag(SecurityTags.UNLOCK_MESSAGE)) {
            when {
                security.busy -> Announced(stringResource(Res.string.sec_checking), warning = false)
                wait > 0 -> Announced(waitText(wait))
                wrong -> Announced(stringResource(Res.string.sec_wrong))
            }
        }
        PpfPrimaryButton(
            onClick = submit,
            text = stringResource(Res.string.sec_unlock),
            icon = Lucide.KeyRound,
            enabled = pin.isNotEmpty() && wait == 0L && !security.busy,
            modifier = Modifier.fillMaxWidth().testTag(SecurityTags.UNLOCK),
        )
        HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 8.dp))
        // An emergency never waits for a code: the 15 and the 112, as on the phone's own lock screen.
        Text(stringResource(Res.string.sec_lock_emergency), style = MaterialTheme.typography.titleSmall)
        EmergencyNumbersBlock(EmergencyNumbers.emergency.filter { it.vital })
        Spacer(Modifier.height(4.dp))
        PpfTextButton(onClick = { forgot = true }, text = stringResource(Res.string.sec_forgot), color = palette.accentInk, modifier = Modifier.testTag(SecurityTags.FORGOT))
    }
    if (forgot) WipeDialog(security, onDone = { forgot = false }, extra = stringResource(Res.string.sec_forgot_text))
}

/** While and after the quick erase: nothing of the interface is shown any more; how to restore the account. */
@Composable
private fun WipedScreen(security: DeviceSecurityState) {
    val running = security.wipeStatus == WipeStatus.ERASING
    val text = stringResource(if (running) Res.string.sec_wipe_running else Res.string.sec_wipe_done)
    FullScreen(text, SecurityTags.WIPED) {
        Wordmark()
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (!running) {
            Text(stringResource(Res.string.sec_wipe_restore), style = MaterialTheme.typography.bodyLarge)
            if (security.canClose) {
                PpfSecondaryButton(onClick = security::close, text = stringResource(Res.string.sec_close), icon = Lucide.X, modifier = Modifier.testTag(SecurityTags.WIPE_CLOSE))
            } else {
                Text(stringResource(Res.string.sec_wipe_close_yourself), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
            }
        }
    }
}
