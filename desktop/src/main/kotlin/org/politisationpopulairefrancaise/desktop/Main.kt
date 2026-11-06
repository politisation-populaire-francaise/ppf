package org.politisationpopulairefrancaise.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ktor.client.HttpClient
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.identity.FileIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp

fun main() {
    // Everything personal lives in the user's private application-data directory.
    val dataDirectory = FileIdentityStore.defaultDirectory()
    // A server is used only when PPF_SERVER_URL is set (for example http://localhost:8080 during development).
    val remote = System.getenv("PPF_SERVER_URL")?.trim()?.takeIf { it.isNotEmpty() }?.let { url ->
        ServerPublicationPort(HttpClient(), url.trimEnd('/'))
    }
    // One local database, opened once, shared by every store (contents, followed accounts).
    val dependencies = AppDependencies.withDatabase(
        identityStore = FileIdentityStore.default(),
        database = JvmDatabase.open(dataDirectory.resolve("ppf.db")),
        remote = remote,
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
