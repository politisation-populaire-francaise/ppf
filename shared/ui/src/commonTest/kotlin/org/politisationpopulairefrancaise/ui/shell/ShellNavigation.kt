package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    onNodeWithTag(tag).performClick()
    waitForIdle()
}
