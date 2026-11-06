package org.politisationpopulairefrancaise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.politisationpopulairefrancaise.core.identity.AndroidIdentityStore
import org.politisationpopulairefrancaise.core.local.AndroidDatabase
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp

/** The single activity of the Android app: it hosts the shared Compose UI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // One local database, opened once, shared by every store (contents, followed accounts).
        val dependencies = AppDependencies.withDatabase(
            identityStore = AndroidIdentityStore(applicationContext),
            database = AndroidDatabase.open(applicationContext),
            // No server yet: the application works with the contents bundled in the APK.
            remote = null,
        )
        setContent {
            PpfApp(dependencies)
        }
    }
}
