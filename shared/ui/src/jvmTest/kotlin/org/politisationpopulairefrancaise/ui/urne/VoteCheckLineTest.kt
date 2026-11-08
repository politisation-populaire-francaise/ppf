package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test

/** The voting self-test runs on the device and says so in "Mon espace" (D-110, condition b). */
@OptIn(ExperimentalTestApi::class)
class VoteCheckLineTest {

    @Test
    fun theSelfTestPassesAndSaysSo() = runComposeUiTest {
        setContent { PpfTheme { VoteCheckLine() } }
        waitUntil(timeoutMillis = 20_000) {
            onAllNodes(hasTestTag(VOTE_CHECK_TAG) and hasText("réussie", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(VOTE_CHECK_TAG).assertTextContains("vérification réussie", substring = true)
    }
}
