package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** « Espace presse » (PP-04): the four parts are there even before any press release. */
@OptIn(ExperimentalTestApi::class)
class PressScreenTest {
    @Test
    fun theFourPartsBeforeAnyRelease() = runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes) {
        setContent {
            PpfTheme(darkTheme = false) {
                Box(Modifier.requiredSize(900.dp, 3000.dp)) { PressPage(VoirCatalog.EMPTY, onTransparency = {}, onVictories = {}, modifier = Modifier.verticalScroll(rememberScrollState())) }
            }
        }
        waitForIdle()
        listOf(PressTags.RELEASES, PressTags.KIT, PressTags.FIGURES, PressTags.CONTACT).forEach { onNodeWithTag(it).assertExists() }
        assertTrue(onAllNodesWithText("Aucun communiqué n’est encore publié.").fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("Journaliste", substring = true).fetchSemanticsNodes().isNotEmpty())
    }
}
