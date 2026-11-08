package org.politisationpopulairefrancaise.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.politisationpopulairefrancaise.ui.crash.CrashReportDialog
import org.politisationpopulairefrancaise.ui.crash.PendingCrashReport
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * Root of the shared user interface. Every platform entry point
 * (Android activity, desktop window, iOS view controller) shows this,
 * handing over what only the platform can provide ([AppDependencies]).
 * [crashReport] is the report written on the device after a crash, offered once at launch (S-101).
 */
@Composable
fun PpfApp(dependencies: AppDependencies = AppDependencies.inMemory(), crashReport: PendingCrashReport? = null) {
    PpfTheme {
        AdaptiveShell(dependencies)
        var offered by remember(crashReport) { mutableStateOf(crashReport) }
        offered?.let { report -> CrashReportDialog(report, onDismiss = { offered = null }) }
    }
}
