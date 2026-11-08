package org.politisationpopulairefrancaise.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * Wakes the phone when the next personal reminder is due (DE-28, S-98; `docs/integration/rappels.md`), even with the
 * application closed. An inexact alarm of the system: no exact-alarm permission, and Android may delay it by a few
 * minutes to save the battery, which a reminder at 10 h can afford. Armed again at each start of the process, each time
 * the screen of the application is left (the reminders may have changed), after each reminder and after a restart of
 * the phone. The alarm only runs the round of the device: nothing is asked from the server, nothing leaves the phone.
 */
object ReminderAlarms {
    private const val REQUEST = 4_217

    fun arm(context: Context, database: PpfDatabase) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = runCatching { ReminderStore(database).nextDueAfter(currentEpochSeconds()) }.getOrNull()
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (next == null) {
            manager.cancel(intent)
            return
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next * 1000, intent)
    }

    /** Shows what is due on the device (the reminders, the alerts of the account), then arms the next reminder. */
    internal fun runRound(context: Context, receiver: BroadcastReceiver) {
        val app = context.applicationContext as? PpfApplication ?: return
        val pending = receiver.goAsync()
        app.scope.launch {
            try {
                // The device only: the engine reads the sources, never the server.
                runCatching { app.notifications.engine.check() }
                arm(app, app.database)
            } finally {
                pending.finish()
            }
        }
    }
}

/** The alarm of a reminder whose time has come (an explicit intent of the application only: not exported). */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ReminderAlarms.runRound(context, this)
}

/**
 * The restart of the phone, which forgets the alarms: they are armed again. Exported because the system sends it;
 * only the system may send this action, which is checked.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderAlarms.runRound(context, this)
    }
}
