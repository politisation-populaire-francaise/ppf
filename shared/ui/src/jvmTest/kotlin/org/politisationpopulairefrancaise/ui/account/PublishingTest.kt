package org.politisationpopulairefrancaise.ui.account

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The common point of every space for an account declared compromised (S-146). */
@OptIn(ExperimentalTestApi::class)
class PublishingTest {

    @Test
    fun publishingIsPossibleByDefaultAndTheNoticeShowsNothing() = runComposeUiTest {
        setContent {
            Text(if (LocalCanPublish.current) "oui" else "non")
            PublishBlockedNotice()
        }
        onNodeWithText("oui").assertExists()
        assertEquals(0, onAllNodesWithTag(PUBLISH_BLOCKED_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun aCompromisedAccountSeesWhyItCannotPublish() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalCanPublish provides false) {
                Text(if (LocalCanPublish.current) "oui" else "non")
                PublishBlockedNotice()
            }
        }
        onNodeWithText("non").assertExists()
        assertEquals(1, onAllNodesWithTag(PUBLISH_BLOCKED_TAG).fetchSemanticsNodes().size)
        onNodeWithText("il ne peut plus rien publier", substring = true).assertExists()
    }
}
