package org.politisationpopulairefrancaise.ui.agir.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

actual val canHideContentFromSystem: Boolean = true

/**
 * `FLAG_SECURE` on the window of the activity: the system shows PPF empty in the recent
 * applications and refuses screenshots and recordings of it. The windows of the dialogs (AIDE
 * sheet, lock screen) inherit it (`SecureFlagPolicy.Inherit`, Compose's default).
 */
@Composable
actual fun HideContentFromSystem(hide: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, hide) {
        val window = context.findActivity()?.window
        if (hide) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
