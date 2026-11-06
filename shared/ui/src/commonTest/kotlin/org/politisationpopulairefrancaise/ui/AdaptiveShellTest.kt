package org.politisationpopulairefrancaise.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test

/**
 * Checks the three layouts of the adaptive shell: phone, tablet and desktop.
 * The window is simulated by constraining the size of the app.
 */
@OptIn(ExperimentalTestApi::class)
class AdaptiveShellTest {

    @Test
    fun phoneOpensOnTheFeedWithABottomBarAndAMenu() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(360.dp, 700.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_COMPACT).assertExists()
        onNodeWithTag(ShellTags.MENU_BUTTON).assertIsDisplayed()
        onNodeWithTag(ShellTags.BOTTOM_BAR).assertIsDisplayed()
        onNodeWithTag(ShellTags.navItem(Space.FIL)).assertIsSelected()
        // The five daily entries (decisions of the owner, 6 November 2026).
        for (space in Space.quickAccess) onNodeWithTag(ShellTags.navItem(space)).assertIsDisplayed()
        onNodeWithText("Partager une publication…").assertDoesNotExist() // no account: the composer is replaced by an invitation
        onNodeWithText("Créer mon compte").assertExists()

        // "Mon espace" is the avatar of the top bar, not an entry of the bottom bar.
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).assertIsDisplayed()

        // The bottom bar reaches the live home, a placeholder for now.
        onNodeWithTag(ShellTags.navItem(Space.EN_DIRECT)).performClick()
        waitForIdle()
        onNodeWithText("Affichage téléphone").assertExists()

        // The pillars are in the menu, with the disclaimer.
        onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.DECIDER)).assertExists()
        onNodeWithText("Plateforme citoyenne indépendante, sans lien avec l’État ni le gouvernement.").assertExists()
    }

    @Test
    fun bottomBarReachesMessagesAndDemands() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(360.dp, 700.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).assertIsSelected()
        onNodeWithText("Conversations privées et de groupe").assertExists()

        onNodeWithTag(ShellTags.navItem(Space.REVENDICATIONS)).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.REVENDICATIONS)).assertIsSelected()
        onNodeWithText("Aucune revendication pour l’instant.").assertExists() // no local database in this test
    }

    @Test
    fun phoneInLandscapeUsesTheRail() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(780.dp, 360.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_MEDIUM).assertExists()
        onNodeWithTag(ShellTags.NAV_RAIL).assertExists()
        onNodeWithTag(ShellTags.BOTTOM_BAR).assertDoesNotExist()
    }

    @Test
    fun tabletLayoutHasRailWithEveryEntry() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(800.dp, 740.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_MEDIUM).assertExists()
        onNodeWithTag(ShellTags.navItem(Space.EN_DIRECT)).performClick()
        waitForIdle()
        onNodeWithText("Affichage tablette").assertExists()
        onNodeWithTag(ShellTags.navItem(Space.COMPRENDRE)).assertExists()
        onNodeWithTag(ShellTags.navItem(Space.REVENDICATIONS)).assertExists()
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).assertIsDisplayed()
    }

    @Test
    fun desktopLayoutHasASideBar() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_EXPANDED).assertExists()
        onNodeWithTag(ShellTags.NAV_DRAWER).assertExists()
        onNodeWithTag(ShellTags.navItem(Space.EN_DIRECT)).performClick()
        waitForIdle()
        onNodeWithText("Affichage ordinateur").assertExists()
    }

    @Test
    fun selectingComprendreUnfoldsItsEntries() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.subItem("fiches")).assertDoesNotExist()
        onNodeWithTag(ShellTags.navItem(Space.DECIDER)).performClick()
        waitForIdle()
        onNodeWithText("Revendiquer, faire campagne, obliger les élus à s’engager, décider ensemble.").assertExists()
        onNodeWithTag(ShellTags.navItem(Space.COMPRENDRE)).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.subItem("fiches")).assertIsDisplayed()
    }
}
