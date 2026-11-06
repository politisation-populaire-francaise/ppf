package org.politisationpopulairefrancaise.ui

import androidx.compose.ui.window.ComposeUIViewController
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.IosDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import platform.UIKit.UIViewController

/**
 * Entry point for the future iOS application: a SwiftUI app will host this
 * view controller. Not compiled into an app until the Apple Developer account
 * exists; kept here so that the shared code stays iOS-compatible.
 *
 * The identity store is in memory for now: the Keychain implementation will be
 * written and tested with the first build on a Mac (docs/ROADMAP.md).
 */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    PpfApp(
        AppDependencies(
            identityStore = InMemoryIdentityStore(),
            contentStore = LocalEventStore(IosDatabase.open()),
        ),
    )
}
