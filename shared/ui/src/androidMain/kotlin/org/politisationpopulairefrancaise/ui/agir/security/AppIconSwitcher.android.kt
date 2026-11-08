package org.politisationpopulairefrancaise.ui.agir.security

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The two launcher entries of the manifest (`.LauncherDefault`, `.LauncherDiscreet`, both aimed at
 * the activity): one is enabled, the other disabled. Their class names are those of the activity's
 * package (the namespace of the Android module), the package name is the application's (which
 * differs in the test builds, suffix `.test`).
 */
@Composable
actual fun rememberPlatformAppIconSwitcher(): AppIconSwitcher? {
    val context = LocalContext.current
    return remember(context) {
        val activity = context.findActivity() ?: return@remember null
        AliasIconSwitcher(activity, activity.javaClass.name.substringBeforeLast('.'))
    }
}

/** The task in the recent applications takes the name and icon of the launcher entry enabled now ([DiscreetMode.applyToTask]). */
@Composable
actual fun KeepTaskLikeLauncher() {
    val context = LocalContext.current
    DisposableEffect(context) {
        context.findActivity()?.let(DiscreetMode::applyToTask)
        onDispose { }
    }
}

private class AliasIconSwitcher(private val activity: Activity, aliasPackage: String) : AppIconSwitcher {
    private val context: Context = activity.applicationContext
    private val normal = ComponentName(context.packageName, "$aliasPackage.${DiscreetMode.DEFAULT_ENTRY}")
    private val discreet = ComponentName(context.packageName, "$aliasPackage.${DiscreetMode.DISCREET_ENTRY}")

    override val isDiscreet: Boolean
        get() = runCatching { context.packageManager.getComponentEnabledSetting(discreet) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }.getOrDefault(false)

    override fun setDiscreet(discreet: Boolean) {
        val manager = context.packageManager
        val (on, off) = if (discreet) this.discreet to normal else normal to this.discreet
        // The new entry first, so that the application always has one in the launcher.
        manager.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        manager.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        // The running task still carries the name of the entry that opened it: give it the new one at once.
        DiscreetMode.applyToTask(activity)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
