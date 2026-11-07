package org.politisationpopulairefrancaise.desktop

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
import org.politisationpopulairefrancaise.core.identity.FileIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp

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
    )
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = AppInfo.NAME,
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            PpfApp(dependencies)
        }
    }
}
