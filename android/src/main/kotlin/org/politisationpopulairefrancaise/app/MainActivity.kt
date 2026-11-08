package org.politisationpopulairefrancaise.app

import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataKeys
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.ktor.client.HttpClient
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.crash.CrashReportFile
import org.politisationpopulairefrancaise.core.currentPlatform
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.agir.security.AndroidDeviceSecurityStorage
import org.politisationpopulairefrancaise.core.identity.AndroidIdentityStore
import org.politisationpopulairefrancaise.core.local.AndroidDatabase
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.crash.PendingCrashReport
import java.io.File

/** The single activity of the Android app: it hosts the shared Compose UI. */
class MainActivity : ComponentActivity() {
    /** The connection to the server, closed with the activity; null when the build has no server. */
    private var httpClient: HttpClient? = null

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
        val remote = serverUrl()?.let { url ->
            val client = HttpClient().also { httpClient = it }
            ServerPublicationPort(client, url)
        }
        // One local database, opened once, shared by every store (contents, followed accounts).
        val dependencies = AppDependencies.withDatabase(
            identityStore = AndroidIdentityStore(applicationContext),
            database = AndroidDatabase.open(applicationContext),
            remote = remote,
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

    override fun onDestroy() {
        httpClient?.close()
        httpClient = null
        super.onDestroy()
    }

    private fun serverUrl(): String? =
        BuildConfig.PPF_SERVER_URL.trim().takeIf { it.startsWith("https://") }?.trimEnd('/')
}
