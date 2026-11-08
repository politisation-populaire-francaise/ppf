package org.politisationpopulairefrancaise.ui.crash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.ui.PpfApp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The crash report offered at launch (S-101): shown in full, erased only on the person's choice. */
@OptIn(ExperimentalTestApi::class)
class CrashReportDialogTest {

    private val text = "Rapport de plantage PPF (rien n'est envoyé sans vous)\nVersion : 0.1.0\nErreur : java.lang.IllegalStateException\n"

    @Test
    fun theReportIsShownAndErasedOnRequest() = runComposeUiTest {
        var erased = 0
        setContent { PpfApp(crashReport = PendingCrashReport(text) { erased++ }) }
        onNodeWithTag(CrashReportTags.DIALOG).assertExists()
        onNodeWithText("Erreur : java.lang.IllegalStateException", substring = true).assertExists()
        // No share sheet on the computer: copy only.
        onNodeWithTag(CrashReportTags.SHARE).assertDoesNotExist()
        onNodeWithTag(CrashReportTags.COPY).performClick()
        onNodeWithText("Rapport copié").assertExists()
        assertEquals(0, erased)
        onNodeWithTag(CrashReportTags.DELETE).performClick()
        assertEquals(1, erased)
        onNodeWithTag(CrashReportTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun laterKeepsTheReport() = runComposeUiTest {
        var erased = 0
        setContent { PpfApp(crashReport = PendingCrashReport(text) { erased++ }) }
        onNodeWithTag(CrashReportTags.CLOSE).performClick()
        onNodeWithTag(CrashReportTags.DIALOG).assertDoesNotExist()
        assertEquals(0, erased)
    }

    @Test
    fun withoutReportNoDialog() = runComposeUiTest {
        setContent { PpfApp() }
        onNodeWithTag(CrashReportTags.DIALOG).assertDoesNotExist()
    }
}
