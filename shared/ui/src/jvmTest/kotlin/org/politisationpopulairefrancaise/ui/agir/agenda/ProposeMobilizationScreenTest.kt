package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Proposer un événement": errors explained while typing, close events before sending, the
 * account, and the three ways a sending ends (published, held for review, waiting for the
 * server), then "Mes propositions" with the state the server gives.
 */
@OptIn(ExperimentalTestApi::class)
class ProposeMobilizationScreenTest {

    private fun ComposeUiTest.openForm() {
        waitUntil(timeoutMillis = 20_000) { cardCount() > 0 }
        onNodeWithTag(AgendaTags.PROPOSE).performClick()
        waitForIdle()
        onNodeWithTag(ProposeTags.FORM).assertExists()
    }

    private fun ComposeUiTest.type(tag: String, text: String) {
        onNodeWithTag(tag).performScrollTo().performTextInput(text)
        waitForIdle()
    }

    private fun ComposeUiTest.fillValid(title: String = "[EXEMPLE] Rassemblement devant la maternité", date: String = "20/11/2026") {
        onNodeWithTag(ProposeTags.TYPE + "rassemblement").performScrollTo().performClick()
        type(ProposeTags.TITLE, title)
        type(ProposeTags.START_DATE, date)
        type(ProposeTags.START_TIME, "11 h")
        type(ProposeTags.END_TIME, "13h")
        type(ProposeTags.LOCATION, "Devant la maternité (lieu fictif), Villefictive")
        type(ProposeTags.TERRITORY, "Villefictive")
        onNodeWithTag(ProposeTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun wrongValuesAreExplainedWhileTypingAndMissingOnesUnderTheButton() = runComposeUiTest {
        setContent { TestAgenda(AgendaDevice(withAccount = true), 380, 740) }
        openForm()
        onNodeWithTag(ProposeTags.SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(ProposeTags.MISSING).assertDoesNotExist()
        type(ProposeTags.TITLE, "[EXEMPLE] Réunion publique")
        onNodeWithTag(ProposeTags.MISSING).performScrollTo().assertTextContains("Pour envoyer, il manque : le type, la date, l’heure, le lieu, le territoire.")

        type(ProposeTags.START_DATE, "32/11/2026")
        onNodeWithTag(ProposeTags.PROBLEM + "start_date_invalid").performScrollTo().assertTextContains("Date invalide", substring = true)
        onNodeWithTag(ProposeTags.BLOCKED).performScrollTo().assertTextContains("Corrigez d’abord les champs signalés.")
        onNodeWithTag(ProposeTags.START_DATE).performTextReplacement("12/11/2026")
        type(ProposeTags.START_TIME, "18h")
        onNodeWithTag(ProposeTags.PROBLEM + "start_in_past").performScrollTo().assertExists()
        onNodeWithTag(ProposeTags.START_DATE).performScrollTo().performTextReplacement("20/11/2026")
        waitForIdle()
        onNodeWithTag(ProposeTags.PROBLEM + "start_in_past").assertDoesNotExist()
        type(ProposeTags.END_TIME, "17h")
        onNodeWithTag(ProposeTags.PROBLEM + "end_before_start").performScrollTo().assertTextContains("La fin doit venir après le début.")
        onNodeWithTag(ProposeTags.END_TIME).performScrollTo().performTextReplacement("20h")
        type(ProposeTags.LINK, "ftp://exemple.invalid")
        onNodeWithTag(ProposeTags.PROBLEM + "link_invalid").performScrollTo().assertExists()
        onNodeWithTag(ProposeTags.LINK).performScrollTo().performTextReplacement("exemple.invalid/reunion")
        waitForIdle()
        onNodeWithTag(ProposeTags.PROBLEM + "link_invalid").assertDoesNotExist()

        onNodeWithTag(ProposeTags.TYPE + "reunion").performScrollTo().performClick()
        type(ProposeTags.LOCATION, "Salle municipale, Villefictive")
        type(ProposeTags.TERRITORY, "Villefictive")
        onNodeWithTag(ProposeTags.MISSING).assertDoesNotExist()
        onNodeWithTag(ProposeTags.BLOCKED).performScrollTo().assertTextContains("Cochez l’acceptation de la charte pour envoyer.")
        onNodeWithTag(ProposeTags.SEND).assertIsNotEnabled()
        onNodeWithTag(ProposeTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposeTags.SEND).performScrollTo().assertIsEnabled()
        // The declaration is only asked for a demonstration.
        onNodeWithTag(ProposeTags.DECLARATION + "unset").assertDoesNotExist()
        onNodeWithTag(ProposeTags.TYPE + "manifestation").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposeTags.DECLARATION + "unset").performScrollTo().assertExists()
        // Decision of the owner (D-044): "Non déclarée en préfecture" is offered too.
        onNodeWithTag(ProposeTags.DECLARATION + "non-faite").performScrollTo().assertExists()
    }

    @Test
    fun closeEventsOfTheSameDayAreShownBeforeSending() = runComposeUiTest {
        var agenda: AgendaLibrary? = null
        setContent { TestAgenda(AgendaDevice(withAccount = true), 380, 740, onReady = { library, _ -> agenda = library }) }
        openForm()
        type(ProposeTags.TITLE, "Rassemblement pour défendre les services publics")
        type(ProposeTags.START_DATE, "14/11/2026")
        onNodeWithTag(ProposeTags.SIMILAR).assertDoesNotExist()
        type(ProposeTags.START_TIME, "15h")
        val paris = "rassemblement-services-publics-paris-2026-11-14"
        onNodeWithTag(ProposeTags.SIMILAR_ITEM + paris).performScrollTo().assertTextContains("Titre proche", substring = true)
        // Another day: nothing close.
        onNodeWithTag(ProposeTags.START_DATE).performScrollTo().performTextReplacement("15/11/2026")
        waitForIdle()
        onNodeWithTag(ProposeTags.SIMILAR).assertDoesNotExist()
        onNodeWithTag(ProposeTags.START_DATE).performScrollTo().performTextReplacement("14/11/2026")
        waitForIdle()
        // "Voir" opens the existing event; the draft is kept for later.
        onNodeWithTag(ProposeTags.SIMILAR_OPEN + paris).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposeTags.FORM).assertDoesNotExist()
        onNodeWithTag(AgendaTags.DETAIL).assertExists()
        assertEquals(paris, agenda?.selected?.identifier)
        assertEquals("Rassemblement pour défendre les services publics", agenda?.composer?.draft?.title)
    }

    @Test
    fun aProposalHeldForReviewIsFollowedInMyProposals() = runComposeUiTest {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        var agenda: AgendaLibrary? = null
        setContent { TestAgenda(device, 380, 740, onReady = { library, _ -> agenda = library }) }
        openForm()
        fillValid()
        onNodeWithTag(ProposeTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ProposeTags.RESULT + "-sent_for_review").fetchSemanticsNodes().isNotEmpty() }
        val sent = server.received.single()
        assertEquals(Mobilization.KIND, sent.kind)
        assertTrue(sent.verify())
        onNodeWithTag(ProposeTags.RESULT_BACK).performClick()
        waitForIdle()
        // Back on the agenda, in "Mes propositions".
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.OWN_STATE + "in_review", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(AgendaTags.TAB + "mine").assertExists()
        val identifier = Mobilization.from(sent)!!.identifier
        onNodeWithTag(AgendaTags.MINE_ITEM + identifier).assertTextContains("En attente de relecture par l’équipe", substring = true)
        // Not on the agenda before the team publishes it.
        onNodeWithTag(AgendaTags.TAB + "upcoming").performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.CARD + identifier).assertDoesNotExist()

        // The team approves it; the next time the agenda is shown, the state follows.
        server.decide(sent.id, HeldStatus.APPROVED)
        runOnIdle { agenda!!.refreshReviews() }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.CARD + identifier).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(AgendaTags.TAB + "mine").performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.OWN_STATE + "published", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aRejectedProposalShowsTheReasonAndCanBeRemoved() = runComposeUiTest {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        var agenda: AgendaLibrary? = null
        setContent { TestAgenda(device, 1000, 740, onReady = { library, _ -> agenda = library }) }
        openForm()
        fillValid()
        onNodeWithTag(ProposeTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { server.received.isNotEmpty() }
        val sent = server.received.single()
        server.decide(sent.id, HeldStatus.REJECTED, "[EXEMPLE] Il manque l’adresse précise.")
        onNodeWithTag(ProposeTags.RESULT_BACK).performClick()
        waitForIdle()
        runOnIdle { agenda!!.refreshReviews() }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.OWN_STATE + "not_published", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val identifier = Mobilization.from(sent)!!.identifier
        onNodeWithTag(AgendaTags.MINE_ITEM + identifier).assertTextContains("Raison donnée par l’équipe : [EXEMPLE] Il manque l’adresse précise.", substring = true)
        onNodeWithTag(AgendaTags.FORGET, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.MINE_ITEM + identifier).fetchSemanticsNodes().isEmpty() }
        assertEquals(null, device.store.find(sent.id))
    }

    @Test
    fun aVerifiedOrganisationIsPublishedAtOnce() = runComposeUiTest {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        server.verified += device.pubkey
        setContent { TestAgenda(device, 1000, 740) }
        openForm()
        fillValid()
        onNodeWithTag(ProposeTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ProposeTags.RESULT + "-published").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(ProposeTags.RESULT_BACK).performClick()
        waitForIdle()
        val identifier = Mobilization.from(server.published.single())!!.identifier
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.CARD + identifier).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun withoutServerTheProposalWaitsOnTheDevice() = runComposeUiTest {
        val device = AgendaDevice(withAccount = true)
        setContent { TestAgenda(device, 700, 740) }
        openForm()
        onNodeWithTag(ProposeTags.NO_SERVER).performScrollTo().assertExists()
        onNodeWithText("envoyée dès qu’il répondra", substring = true).assertExists()
        fillValid()
        onNodeWithTag(ProposeTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ProposeTags.RESULT + "-waiting_for_server").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, device.store.pending().size)
        onNodeWithTag(ProposeTags.RESULT_BACK).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.OWN_STATE + "waiting", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun sendingNeedsAnAccount() = runComposeUiTest {
        val visited = mutableListOf<Space>()
        setContent { TestAgenda(AgendaDevice(withAccount = false), 380, 740, onNavigate = { visited += it }) }
        openForm()
        onNodeWithTag(ProposeTags.SEND).assertDoesNotExist()
        onNodeWithTag(ProposeTags.CREATE_ACCOUNT).performScrollTo().performClick()
        assertEquals(listOf(Space.MON_ESPACE), visited)
        // Closing keeps the agenda.
        onNodeWithTag(ProposeTags.CLOSE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.LIST).assertExists()
    }
}
