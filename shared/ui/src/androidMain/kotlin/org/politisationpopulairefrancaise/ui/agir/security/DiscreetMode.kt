package org.politisationpopulairefrancaise.ui.agir.security

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * « Application discrète » (S-45) seen from Android code without Compose: the notifications of
 * the host (pôle Communauté: neutral title, no preview, `VISIBILITY_SECRET`, neutral channels and
 * the small icon `@drawable/ic_notification_neutral` of the Android module) and the task shown in
 * the recent applications.
 *
 * The state is the one Android keeps for the two launcher entries of the manifest
 * (`.LauncherDefault`, `.LauncherDiscreet`): PPF stores nothing. The entries are found by their
 * simple names in the package, so the test builds (suffix `.test` of the application id) work too.
 */
object DiscreetMode {
    /** The simple name of the neutral launcher entry (`activity-alias` of the Android manifest). */
    const val DISCREET_ENTRY = "LauncherDiscreet"

    /** The simple name of the usual launcher entry. */
    const val DEFAULT_ENTRY = "LauncherDefault"

    /**
     * True when the neutral entry (« Outils », grey icon) is the one the launcher shows. False when
     * it was never switched on (the manifest has it disabled), when the entry cannot be found or when
     * the package manager fails. Any thread; cheap enough to be read before each notification.
     */
    fun isOn(context: Context): Boolean = runCatching {
        val entry = entry(context, DISCREET_ENTRY) ?: return false
        context.packageManager.getComponentEnabledSetting(entry) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }.getOrDefault(false)

    /**
     * Gives the task of [activity] the name and icon of the launcher entry enabled now, so that the
     * list of recent applications (and the screen reader reading it) says « Outils » in discreet
     * mode, even right after a switch, before the application is opened again. Does nothing if the
     * entry cannot be read.
     */
    fun applyToTask(activity: Activity) {
        runCatching {
            val entry = entry(activity, if (isOn(activity)) DISCREET_ENTRY else DEFAULT_ENTRY) ?: return
            val manager = activity.packageManager
            val info = manager.getActivityInfo(entry, PackageManager.MATCH_DISABLED_COMPONENTS)
            val label = info.loadLabel(manager).toString()
            val icon = manager.getActivityIcon(entry).let { drawable ->
                val size = activity.resources.getDimensionPixelSize(android.R.dimen.app_icon_size).coerceAtLeast(1)
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(Canvas(bitmap))
                }
            }
            // The (label, bitmap) constructor exists on every supported version (Android 8 and later).
            @Suppress("DEPRECATION")
            activity.setTaskDescription(ActivityManager.TaskDescription(label, icon))
        }
    }

    /** The launcher entry of this package whose simple name is [simpleName], enabled or not. */
    internal fun entry(context: Context, simpleName: String): ComponentName? {
        @Suppress("DEPRECATION")
        val activities = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS)
            .activities ?: return null
        val name = activities.firstOrNull { it.name.substringAfterLast('.') == simpleName }?.name ?: return null
        return ComponentName(context.packageName, name)
    }
}
