package org.politisationpopulairefrancaise.desktop

import org.politisationpopulairefrancaise.ui.components.ppfLogoPainter
import org.politisationpopulairefrancaise.ui.endirect.LiveBoardStore
import org.politisationpopulairefrancaise.ui.onboarding.TutorialStore
import java.io.File
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.urne.HttpUrneRemote
import org.politisationpopulairefrancaise.core.urne.UrneSelfTest
import kotlin.system.exitProcess
import org.politisationpopulairefrancaise.core.crash.CrashReportFile
import org.politisationpopulairefrancaise.core.currentPlatform
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.agir.security.FileDeviceSecurityStorage
import org.politisationpopulairefrancaise.core.identity.FileIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.crash.PendingCrashReport

fun main(args: Array<String>) {
    // Packaging check (D-110, condition c): `--autotest-vote` replays the voting self-test with the
    // packaged runtime and libsodium, then exits with 0 when it passes and 1 otherwise.
    if ("--autotest-vote" in args) {
        val result = runBlocking { UrneSelfTest.run() }
        println(if (result.passed) "VOTE-AUTOTEST OK" else "VOTE-AUTOTEST FAILED at ${result.failedCheck}")
        exitProcess(if (result.passed) 0 else 1)
    }
    // Everything personal lives in the user's private application-data directory.
    val dataDirectory = FileIdentityStore.defaultDirectory()
    // After a crash, a report is written there and offered at the next launch, never sent (S-101).
    val crashes = CrashReportFile(dataDirectory.resolve("crash-report.txt").toFile())
    val pendingCrash = crashes.pending()?.let { PendingCrashReport(it, crashes::delete) }
    val platform = currentPlatform()
    crashes.install(AppInfo.VERSION, "${platform.name} ${platform.version}")
    // A server is used only when PPF_SERVER_URL is set (for example http://localhost:8080 during development).
    val serverUrl = System.getenv("PPF_SERVER_URL")?.trim()?.takeIf { it.isNotEmpty() }?.trimEnd('/')
    val client = serverUrl?.let { HttpClient() }
    val remote = serverUrl?.let { ServerPublicationPort(client!!, it) }
    // Voting (D-110): the account's requests on the usual client, the ballots on a second one of
    // their own, without cookies, which never carries the account.
    val urneRemote = serverUrl?.let { HttpUrneRemote(client!!, HttpClient(), it) { currentEpochSeconds() } }
    // One local database, opened once, shared by every store (contents, followed accounts).
    val dependencies = AppDependencies.withDatabase(
        identityStore = FileIdentityStore.default(),
        database = JvmDatabase.open(dataDirectory.resolve("ppf.db")),
        remote = remote,
        urneRemote = urneRemote,
        // The lock of the application and the quick erase (M33: S-43, S-44).
        securityStorage = FileDeviceSecurityStorage(dataDirectory.resolve(FileDeviceSecurityStorage.FILE_NAME)),
        // The tutorial of the first launch (D-216): seen or not, kept on the computer only.
        tutorial = FileTutorialStore(dataDirectory.resolve("tutorial-seen").toFile()),
        liveBoard = FileLiveBoardStore(dataDirectory.resolve("en-direct-blocs.txt").toFile()),
        closeApplication = { exitProcess(0) },
    )
    // What the host itself keeps, for the quick erase: the crash report.
    dependencies.erasables.register(deviceErasable("host-crash-report") { crashes.delete() })
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = AppInfo.NAME,
            // The official logo of PPF, as on the installer (D-187).
            icon = ppfLogoPainter(),
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            PpfApp(dependencies, crashReport = pendingCrash)
        }
    }
}

/**
 * The tutorial of the first launch was seen (D-216): one empty file in the private directory of the
 * application, nothing else, never sent. Erased by the quick erase (the tutorial then shows again).
 */
private class FileTutorialStore(private val file: File) : TutorialStore {
    override fun seen(): Boolean = file.exists()

    override fun markSeen() {
        file.parentFile?.mkdirs()
        file.writeText("")
    }

    override fun forget() {
        check(!file.exists() || file.delete()) { "tutorial flag not erased" }
    }
}

/**
 * How the person composed « En direct » (views and blocks): a few lines of text in the private
 * directory of the application, never sent. Erased by the quick erase.
 */
private class FileLiveBoardStore(private val file: File) : LiveBoardStore {
    override fun load(): String? = file.takeIf { it.exists() }?.readText()

    override fun save(text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    override fun forget() {
        check(!file.exists() || file.delete()) { "live board not erased" }
    }
}
