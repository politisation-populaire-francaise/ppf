package org.politisationpopulairefrancaise.app

import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataKeys
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.ktor.client.HttpClient
import org.politisationpopulairefrancaise.core.identity.AndroidIdentityStore
import org.politisationpopulairefrancaise.core.local.AndroidDatabase
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp

/** The single activity of the Android app: it hosts the shared Compose UI. */
class MainActivity : ComponentActivity() {
    /** The connection to the server, closed with the activity; null when the build has no server. */
    private var httpClient: HttpClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        )
        setContent {
            PpfApp(dependencies)
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
