package org.politisationpopulairefrancaise.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ktor.client.HttpClient
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.crash.CrashReportFile
import org.politisationpopulairefrancaise.core.currentPlatform
import org.politisationpopulairefrancaise.core.agir.security.DesktopDeviceEraser
import org.politisationpopulairefrancaise.core.agir.security.FileDeviceSecurityStorage
import org.politisationpopulairefrancaise.core.identity.FileIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.crash.PendingCrashReport

fun main() {
    // Everything personal lives in the user's private application-data directory.
    val dataDirectory = FileIdentityStore.defaultDirectory()
    // After a crash, a report is written there and offered at the next launch, never sent (S-101).
    val crashes = CrashReportFile(dataDirectory.resolve("crash-report.txt").toFile())
    val pendingCrash = crashes.pending()?.let { PendingCrashReport(it, crashes::delete) }
    val platform = currentPlatform()
    crashes.install(AppInfo.VERSION, "${platform.name} ${platform.version}")
    // A server is used only when PPF_SERVER_URL is set (for example http://localhost:8080 during development).
    val remote = System.getenv("PPF_SERVER_URL")?.trim()?.takeIf { it.isNotEmpty() }?.let { url ->
        ServerPublicationPort(HttpClient(), url.trimEnd('/'))
    }
    // One local database, opened once, shared by every store (contents, followed accounts).
    val databaseFile = dataDirectory.resolve("ppf.db")
    val dependencies = AppDependencies.withDatabase(
        identityStore = FileIdentityStore.default(),
        database = JvmDatabase.open(databaseFile),
        remote = remote,
        // The lock of the application and the quick erase (M33: S-43, S-44).
        securityStorage = FileDeviceSecurityStorage(dataDirectory.resolve(FileDeviceSecurityStorage.FILE_NAME)),
        deviceEraser = DesktopDeviceEraser(dataDirectory, databaseFile),
    )
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = AppInfo.NAME,
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            PpfApp(dependencies, crashReport = pendingCrash)
        }
    }
}
