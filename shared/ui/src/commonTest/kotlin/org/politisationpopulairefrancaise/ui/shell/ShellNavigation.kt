package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.politisationpopulairefrancaise.core.Space

/**
 * Opens [space] the way a person would on the current layout: straight from the bottom bar, the top
 * bar, the rail or the side bar when its entry is on screen, otherwise from the menu of phones
 * (D-215: Voir, Comprendre, Décider and Revendications are in the menu on phones).
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openSpace(space: Space) {
    val tag = ShellTags.navItem(space)
    val onScreen = runCatching { onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
    if (!onScreen && onAllNodesWithTag(ShellTags.MENU_BUTTON).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
        waitForIdle()
    }
    // The menu in seven rubrics (D-240): the entry sits in its rubric, unfolded first when it is folded.
    if (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
        rubricFor(space)?.let { rubric ->
            runCatching { onNodeWithTag(rubricTag(rubric)).performScrollTo() }
            onNodeWithTag(rubricTag(rubric)).performClick()
            waitForIdle()
        }
    }
    // The menu scrolls on small screens: bring the entry into view first.
    runCatching { onNodeWithTag(tag).performScrollTo() }
    onNodeWithTag(tag).performClick()
    waitForIdle()
}
