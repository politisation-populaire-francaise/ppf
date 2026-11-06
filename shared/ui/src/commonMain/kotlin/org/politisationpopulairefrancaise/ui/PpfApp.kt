package org.politisationpopulairefrancaise.ui

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * Root of the shared user interface. Every platform entry point
 * (Android activity, desktop window, iOS view controller) shows this,
 * handing over what only the platform can provide ([AppDependencies]).
 */
@Composable
fun PpfApp(dependencies: AppDependencies = AppDependencies.inMemory()) {
    PpfTheme {
        AdaptiveShell(dependencies)
    }
}
