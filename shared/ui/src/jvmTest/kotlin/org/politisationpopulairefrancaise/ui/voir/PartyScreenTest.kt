package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test

/** The sheets of the political parties in "Voir", read from the bundled examples (all [EXEMPLE]). */
@OptIn(ExperimentalTestApi::class)
class PartyScreenTest {

    private val database = JvmDatabase.inMemory()

    private fun dependencies() = AppDependencies(
        identityStore = InMemoryIdentityStore(),
        contentStore = LocalEventStore(database),
        editorialTrust = EditorialTrust.bundledExamples(),
        contentFollowStore = ContentFollowStore(database),
    )

    /** Opens "Voir" on the axis of power. */
    private fun ComposeUiTest.openPower(width: Int = 1000, height: Int = 740) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies()) } }
        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
        }
        onNodeWithText("Voir").performClick()
        waitForIdle()
        if (width < 600) {
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(VoirTags.card("manifestants-journees")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(VoirTags.tab(1)).performClick()
        waitForIdle()
    }

    @Test
    fun aPartyOpensFromThePowerAxisWithItsSeatsItsMembersAndItsPromises() = runComposeUiTest {
        openPower()
        // The party with the most seats comes first.
        onNodeWithTag(VoirTags.card("parti-exemple-a")).performScrollTo().assert(hasText("PEA")).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("parti-exemple-a")).assertExists()
        // Two sources for the seats at the Assembly: both shown, the official one retained.
        onNodeWithTag(VoirTags.section("party-seats")).performScrollTo()
            .assert(hasAnyDescendant(hasText("Assemblée nationale")))
            .assert(hasAnyDescendant(hasText("Chiffre retenu")))
            .assert(hasAnyDescendant(hasText("124")))
        onNodeWithTag(VoirTags.section("party-members")).performScrollTo().assert(hasAnyDescendant(hasText("Camille Exemple")))
        onNodeWithTag(VoirTags.card("promesse-parti-a-smic")).performScrollTo().assertExists()
        // The same sections on every sheet, even empty.
        listOf("party-presentation", "party-leaders", "promesse", "vote", "affaire", "argent-public", "next").forEach { section ->
            onNodeWithTag(VoirTags.section(section)).performScrollTo().assertExists()
        }
        // A member opens from the party.
        onNodeWithTag(VoirTags.card("camille-exemple")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("camille-exemple")).assertExists()
    }

    @Test
    fun aPartySheetNeverShowsTheCaseOfAMember() = runComposeUiTest {
        openPower()
        onNodeWithTag(VoirTags.seeAll("parties")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("parti-exemple-d")).performScrollTo().assertExists()
        onNodeWithTag(VoirTags.card("parti-exemple-b")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("parti-exemple-b")).assertExists()
        onNodeWithTag(VoirTags.section("party-leaders")).performScrollTo().assert(hasAnyDescendant(hasText("Dominique Fictif")))
        onNodeWithTag(VoirTags.card("affaire-parti-b-comptes")).performScrollTo().assertExists()
        onNodeWithTag(VoirTags.PRESUMPTION).performScrollTo().assertExists()
        // The case of the party's leader stays on the leader's own sheet.
        onNodeWithTag(VoirTags.card("affaire-dominique-marche-public")).assertDoesNotExist()
        onNodeWithText("Affaires judiciaires : ", substring = true).assertDoesNotExist()
    }

    @Test
    fun thePartyOfAPersonIsOneTouchAwayOnTheSheet() = runComposeUiTest {
        openPower()
        onNodeWithTag(VoirTags.card("dominique-fictif")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("dominique-fictif")).assertExists()
        onNodeWithTag(VoirTags.PARTY_CHIP).assert(hasText("Parti d'exemple B", substring = true)).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("parti-exemple-b")).assertExists()
        onNodeWithTag(VoirTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("dominique-fictif")).assertExists()
    }

    @Test
    fun personsCanBeFilteredByParty() = runComposeUiTest {
        openPower()
        onNodeWithTag(VoirTags.seeAll("people")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("sacha-temoin")).performScrollTo().assertExists()
        onNodeWithTag(VoirTags.filter("party-parti-exemple-a")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("camille-exemple")).performScrollTo().assertExists()
        onNodeWithTag(VoirTags.card("sacha-temoin")).assertDoesNotExist()
        onNodeWithTag(VoirTags.filter("party-all")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("sacha-temoin")).performScrollTo().assertExists()
    }

    @Test
    fun onAPhoneThePartiesComeFirstOnThePowerAxis() = runComposeUiTest {
        openPower(width = 400, height = 740)
        onNodeWithTag(VoirTags.card("parti-exemple-a")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("parti-exemple-a")).assertExists()
    }
}
