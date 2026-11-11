package org.politisationpopulairefrancaise.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.agir.calls.CallTags
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.decider.DeciderTags
import org.politisationpopulairefrancaise.ui.endirect.EnDirectTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test

/**
 * Checks the layouts of the adaptive shell: phone, phone in landscape, tablet and desktop.
 * The window is simulated by constraining the size of the app.
 */
@OptIn(ExperimentalTestApi::class)
class AdaptiveShellTest {

    @Test
    fun phoneOpensOnTheLiveHomeWithTheBottomBarOfTheOwner() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(360.dp, 700.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_COMPACT).assertExists()
        onNodeWithTag(ShellTags.MENU_BUTTON).assertIsDisplayed()
        onNodeWithTag(ShellTags.BOTTOM_BAR).assertIsDisplayed()
        // D-215: the first screen is « En direct », then Actu, Carte, Agir, Appels in the bottom bar.
        onNodeWithTag(ShellTags.navItem(Space.EN_DIRECT)).assertIsSelected()
        onNodeWithTag(EnDirectTags.SCREEN).assertExists()
        for (space in Space.quickAccess) onNodeWithTag(ShellTags.navItem(space)).assertIsDisplayed()
        onNodeWithTag(ShellTags.navItem(Space.FIL)).assert(hasText("Actu"))
        onNodeWithTag(ShellTags.navItem(Space.APPELS)).assert(hasText("Appels"))

        // The top bar: the red AIDE button, the messages and "Mon espace" (the avatar).
        onNodeWithTag(ManifestationTags.HELP_BUTTON).assertIsDisplayed()
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).assertIsDisplayed()
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).assertIsDisplayed()

        // "Actu" is the feed, under its stable id.
        onNodeWithTag(ShellTags.navItem(Space.FIL)).performClick()
        waitForIdle()
        onNodeWithText("Créer mon compte").assertExists() // no account: the composer is replaced by an invitation

        // The rest is in the menu, with the disclaimer: nothing already built is removed (D-210).
        onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
        waitForIdle()
        for (space in Space.more) onNodeWithTag(ShellTags.navItem(space)).assertExists()
        onNodeWithText("Plateforme citoyenne indépendante, sans lien avec l’État ni le gouvernement.").assertExists()
    }

    @Test
    fun theTopBarReachesTheMessagesAndTheMenuTheDemands() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(360.dp, 700.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).assertIsSelected()
        onNodeWithText("Conversations privées et de groupe").assertExists()

        onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.REVENDICATIONS)).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.REVENDICATIONS)).assertIsSelected()
        onNodeWithText("Aucune revendication pour l’instant.").assertExists() // no local database in this test
    }

    @Test
    fun theBottomBarOpensAgirAndTheWallOfCalls() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(360.dp, 700.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.navItem(Space.APPELS)).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.APPELS)).assertIsSelected()
        onNodeWithTag(CallTags.SCREEN).assertExists()
        // On its own, the wall has no way back to the home of Agir.
        onNodeWithTag(CallTags.BACK).assertDoesNotExist()

        onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.AGIR)).assertIsSelected()
    }

    @Test
    fun phoneInLandscapeUsesAShortRailAndTheMenu() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(780.dp, 360.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_MEDIUM).assertExists()
        onNodeWithTag(ShellTags.NAV_RAIL).assertExists()
        onNodeWithTag(ShellTags.BOTTOM_BAR).assertDoesNotExist()
        // The five daily entries are all visible without scrolling, the rest is in the menu.
        for (space in Space.quickAccess) onNodeWithTag(ShellTags.navItem(space)).assertIsDisplayed()
        onNodeWithTag(ManifestationTags.HELP_BUTTON).assertIsDisplayed()
        onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.DECIDER)).performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.SCREEN).assertExists()
    }

    @Test
    fun aWidePhoneInLandscapeDoesNotGetTheDesktopLayout() = runComposeUiTest {
        // A 412 dp phone turned in landscape is wider than 840 dp.
        setContent { Box(Modifier.requiredSize(915.dp, 412.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_EXPANDED).assertDoesNotExist()
        onNodeWithTag(ShellTags.SHELL_MEDIUM).assertExists()
        for (space in Space.quickAccess) onNodeWithTag(ShellTags.navItem(space)).assertIsDisplayed()
    }

    @Test
    fun tabletLayoutHasRailWithEveryEntry() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(800.dp, 740.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_MEDIUM).assertExists()
        for (space in Space.quickAccess + Space.more) onNodeWithTag(ShellTags.navItem(space)).assertExists()
        // Every space of the rail opens its real screen, "Décider" included.
        onNodeWithTag(ShellTags.navItem(Space.DECIDER)).performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.SCREEN).assertExists()
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).assertIsDisplayed()
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).assertIsDisplayed()
    }

    @Test
    fun desktopLayoutHasASideBar() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.SHELL_EXPANDED).assertExists()
        onNodeWithTag(ShellTags.NAV_DRAWER).assertExists()
        for (space in Space.quickAccess + Space.more) onNodeWithTag(ShellTags.navItem(space)).assertExists()
        onNodeWithTag(ShellTags.navItem(Space.DECIDER)).performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.SCREEN).assertExists()
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).assertIsDisplayed()
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
