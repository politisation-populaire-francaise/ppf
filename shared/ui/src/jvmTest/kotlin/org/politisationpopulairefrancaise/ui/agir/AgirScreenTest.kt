package org.politisationpopulairefrancaise.ui.agir

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.agir.collectives.CollectivesTags
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationPageTags
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsTags
import org.politisationpopulairefrancaise.ui.groups.GroupsTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.agir.engagement.EngagementFeaturedTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The home of "Agir": its rubrics in a grid of 1, 2 or 3 columns, the rubrics in preparation
 * (disabled), the way into each rubric and back, the places left for the agenda and the calls,
 * the collectives of "Messages", the guides of "Comprendre", and the shell's route to it.
 */
@OptIn(ExperimentalTestApi::class)
class AgirScreenTest {

    private val available = AgirRubrics.available.map { it.id }

    /** The cards of the sections (« S'engager en politique » is the highlight above them). */
    private val cards = AgirRubrics.sections.flatMap { section -> section.rubrics.map { it.id } }
    private val inPreparation = AgirRubrics.inPreparation.map { it.id }

    @Test
    fun theHomeShowsTheRubricsThenThoseInPreparationDisabled() = runComposeUiTest {
        val harness = AgirHarness()
        setContent { harness.Content(360, 740) }
        awaitTag(AgirTags.HOME)
        onNodeWithText("Agir").assertExists()
        onNodeWithText("Se mobiliser, s’organiser, s’entraider, témoigner, communiquer même sans Internet", substring = true).assertExists()
        // D-231: four headed sections, then the rubrics in preparation; the highlight of « S'engager » first.
        onNodeWithTag(EngagementFeaturedTags.CARD).performScrollTo().assert(hasClickAction())
        AgirRubrics.sections.forEach { section -> onNodeWithTag(AgirTags.SECTION + section.id).performScrollTo() }
        cards.forEach { id -> onNodeWithTag(AgirTags.RUBRIC + id).performScrollTo().assert(hasClickAction()) }
        onNodeWithTag(AgirTags.SOON_TITLE).performScrollTo()
        onNodeWithText("Ces rubriques sont prévues mais pas encore prêtes", substring = true).assertExists()
        inPreparation.forEach { id ->
            onNodeWithTag(AgirTags.SOON + id).performScrollTo().assertIsNotEnabled().assert(hasClickAction().not())
        }
        // "Témoigner" (AG-07), the protest mode (AG-13), the solidarity funds (AG-10) and "Je veux aider" (AG-12) left the rubrics in preparation.
        assertEquals(3, inPreparation.size)
        assertEquals(listOf("se-mobiliser", "tenir", "federer", "se-proteger"), AgirRubrics.sections.map { it.id })
        assertEquals(listOf("agenda", "appels", "se-reunir", "collectifs", "organisations"), AgirRubrics.sections.first().rubrics.map { it.id })
        assertTrue("s-engager" in available && "s-engager" !in cards && "simulateur-greve" in cards)
        assertEquals(cards.size, cards.toSet().size, "each rubric once")
        assertTrue("solidarite" in available && "aider" in available)
        assertTrue("solidarite" !in inPreparation && "aider" !in inPreparation)
        onNodeWithText("Messagerie sans Internet").assertExists()
    }

    @Test
    fun theRubricsFormOneTwoOrThreeColumnsDependingOnTheWidth() {
        assertEquals(1, columnsAt(360))
        assertEquals(2, columnsAt(700))
        assertEquals(3, columnsAt(1000))
    }

    /** How many rubrics share the top of the first row at this width. */
    private fun columnsAt(width: Int): Int {
        var columns = 0
        runComposeUiTest {
            val harness = AgirHarness()
            setContent { harness.Content(width, 740) }
            awaitTag(AgirTags.HOME)
            val tops = available.take(3).map { onNodeWithTag(AgirTags.RUBRIC + it).fetchSemanticsNode().boundsInRoot.top }
            columns = tops.count { it == tops.first() }
        }
        return columns
    }

    @Test
    fun theAgendaAndTheCallsSayInPreparationUntilTheirScreensArePluggedIn() = runComposeUiTest {
        val harness = AgirHarness()
        setContent { harness.Content(360, 740) }
        awaitTag(AgirTags.HOME)
        for (rubric in listOf(AgirRubrics.AGENDA, AgirRubrics.CALLS)) {
            onNodeWithTag(AgirTags.RUBRIC + rubric.id).performScrollTo().performClick()
            waitForIdle()
            awaitTag(AgirTags.PENDING)
            onNodeWithText("Cette rubrique arrive bientôt", substring = true).assertExists()
            onNodeWithTag(AgirTags.BACK).performClick()
            waitForIdle()
            awaitTag(AgirTags.HOME)
        }
    }

    @Test
    fun pluggedScreensReplaceThePlacesAndComeBackHome() = runComposeUiTest {
        val harness = AgirHarness()
        setContent {
            harness.Content(
                width = 700,
                height = 740,
                agenda = { modifier, onBack ->
                    Column(modifier.testTag("plugged-agenda")) { Button(onClick = onBack, modifier = Modifier.testTag("plugged-agenda-back")) { Text("Retour") } }
                },
                calls = { modifier, onBack ->
                    Column(modifier.testTag("plugged-calls")) { Button(onClick = onBack, modifier = Modifier.testTag("plugged-calls-back")) { Text("Retour") } }
                },
            )
        }
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.AGENDA.id).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("plugged-agenda").assertExists()
        assertEquals(0, count(AgirTags.PENDING))
        onNodeWithTag("plugged-agenda-back").performClick()
        waitForIdle()
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.CALLS.id).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("plugged-calls").assertExists()
        onNodeWithTag("plugged-calls-back").performClick()
        waitForIdle()
        awaitTag(AgirTags.HOME)
    }

    @Test
    fun guidesLeadToComprendreAndCollectivesToTheirDirectoryThenTheRoomsOfMessages() = runComposeUiTest {
        val harness = AgirHarness()
        setContent { harness.Content(360, 740) }
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.GUIDES.id).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(Space.COMPRENDRE), harness.navigations)

        // M24: the rubric opens the directory of the collectifs; the plain rooms of "Messages" stay one tap away.
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.COLLECTIVES.id).performScrollTo().performClick()
        waitForIdle()
        awaitTag(AgirTags.COLLECTIVES)
        awaitTag(CollectivesTags.DIRECTORY)
        onNodeWithTag(CollectivesTags.ROOMS).performClick()
        waitForIdle()
        awaitTag(AgirTags.ROOMS)
        awaitTag(GroupsTags.PAGE)
        onNodeWithTag(AgirTags.BACK).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.DIRECTORY)
        onNodeWithTag(AgirTags.BACK).performClick()
        waitForIdle()
        awaitTag(AgirTags.HOME)
    }

    @Test
    fun theDirectoryOpensFromTheHomeAndLeadsBackToIt() = runComposeUiTest {
        val harness = AgirHarness()
        setContent { harness.Content(360, 740) }
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.ORGANISATIONS.id).performScrollTo().performClick()
        waitForIdle()
        awaitTag(OrganisationsTags.LIST)
        onNodeWithTag(OrganisationsTags.BACK).performClick()
        waitForIdle()
        awaitTag(AgirTags.HOME)
    }

    @Test
    fun aPluggedScreenOpensAnOrganisationsPageAndGetsItBack() = runComposeUiTest {
        val harness = AgirHarness()
        setContent {
            harness.Content(
                width = 360,
                height = 740,
                agenda = { modifier, _ ->
                    // The agenda of another block: its directory is the shared one, started by whoever needs the verified keys.
                    LaunchedEffect(Unit) { harness.organisations.ensureStarted() }
                    Column(modifier.testTag("plugged-agenda")) {
                        Button(onClick = { harness.organisations.openKey(AgirFixtures.LANTERNE_ACCOUNT) }, modifier = Modifier.testTag("plugged-organiser")) { Text("Organisateur") }
                    }
                },
            )
        }
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.AGENDA.id).performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { harness.organisations.verified.isVerified(AgirFixtures.LANTERNE_ACCOUNT) }
        onNodeWithTag("plugged-organiser").performClick()
        waitForIdle()
        awaitTag(OrganisationPageTags.PAGE)
        onNodeWithText("Association fictive Les Ateliers de la Lanterne").assertExists()
        onNodeWithTag(OrganisationPageTags.BACK).performClick()
        waitForIdle()
        awaitTag(OrganisationsTags.LIST)
        onNodeWithTag(OrganisationsTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag("plugged-agenda").assertExists()
    }

    @Test
    fun theShellLeadsToAgirOnADesktopAndFromTheBottomBarOfAPhone() {
        runComposeUiTest {
            setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(AgirFixtures.dependencies()) } }
            onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
            waitForIdle()
            awaitTag(AgirTags.HOME)
            assertTrue(count(AgirTags.RUBRIC + AgirRubrics.ORGANISATIONS.id) == 1)
        }
        runComposeUiTest {
            setContent { Box(Modifier.requiredSize(360.dp, 740.dp)) { PpfApp(AgirFixtures.dependencies()) } }
            // D-215: Agir is in the bottom bar of phones.
            onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
            waitForIdle()
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            awaitTag(AgirTags.HOME)
        }
    }
}
