package org.politisationpopulairefrancaise.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.crash.CrashReportFile
import org.politisationpopulairefrancaise.core.currentPlatform
import org.politisationpopulairefrancaise.core.agir.security.AndroidDeviceSecurityStorage
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataKeys
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.crash.PendingCrashReport
import java.io.File

/** The single activity of the Android app: it hosts the shared Compose UI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a crash, a report is written in the app's private files, and offered at the next launch:
        // never sent by the app (S-101). The previous report, if any, is read before the handler is set.
        val crashes = CrashReportFile(File(filesDir, "crash-report.txt"))
        val pendingCrash = crashes.pending()?.let { PendingCrashReport(it, crashes::delete) }
        val platform = currentPlatform()
        crashes.install("${AppInfo.VERSION} (${BuildConfig.VERSION_NAME}, ${BuildConfig.BUILD_TYPE})", "${platform.name} ${platform.version}")
        enableEdgeToEdge()
        // The server is fixed at build time (see build.gradle.kts): the preproduction for the test
        // version, none for the others, which then work with the contents bundled in the APK. HTTPS
        // only, checked again here because the HTTP engine does not consult Android's cleartext rule.
        // When the server cannot be reached (no WireGuard tunnel to the preproduction, no network),
        // the application stays offline as without a server and tries again at the next refresh.
        // The database, the connections and the notifications belong to the application process
        // (PpfApplication), so that a wake-up can use them while no screen is open.
        val app = application as PpfApplication
        val dependencies = AppDependencies.withDatabase(
            identityStore = app.identityStore,
            database = app.database,
            remote = app.remote,
            // Voting: the account's requests on the usual client, the ballots on a second one of their own.
            urneRemote = app.urneRemote,
            notifications = app.notifications,
            push = app.push,
            pushServer = app.pushServer,
            requestNotificationPermission = ::requestNotificationPermission,
            // The published build trusts the production import key only; test builds the preproduction and the examples.
            openDataTrust = if (BuildConfig.BUILD_TYPE == "release") OpenDataKeys.release() else OpenDataKeys.testBuilds(),
            // The lock of the application and the quick erase (M33: S-43, S-44, S-45).
            securityStorage = AndroidDeviceSecurityStorage(applicationContext),
            closeApplication = {
                finishAndRemoveTask()
                Process.killProcess(Process.myPid())
            },
        )
        // What the host itself keeps, for the quick erase: the crash report and the cache (shared files of the agenda).
        dependencies.erasables.register(deviceErasable("host-crash-report") { crashes.delete() })
        dependencies.erasables.register(deviceErasable("host-cache") { check(cacheDir.deleteRecursively()) { "cache not erased" } })
        setContent {
            PpfApp(dependencies, crashReport = pendingCrash)
        }
    }

    /** Leaving the application: the reminders may have changed, the alarm of the next one is armed again (DE-28). */
    override fun onStop() {
        super.onStop()
        ReminderAlarms.arm(this, (application as PpfApplication).database)
    }

    private var permissionAnswer: ((Boolean) -> Unit)? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionAnswer?.invoke(granted)
        permissionAnswer = null
    }

    /** Android 13 and later ask the user before an application may show notifications. */
    private fun requestNotificationPermission(answer: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            answer(true)
            return
        }
        permissionAnswer = answer
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
