package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalRecord
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalReview
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two decisions of the owner of 2026-11-07: the author cancels a published event without review
 * (after a confirmation, the safe choice first); a held event whose date has passed is refused by
 * the server with a fixed reason, shown like any refusal in "Mes propositions".
 */
@OptIn(ExperimentalTestApi::class)
class CancelAndAutoRejectTest {

    @Test
    fun theAuthorCancelsAPublishedEventAfterConfirming() = runComposeUiTest {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        val own = AgendaKit(device.keys).mobilization("ma-reunion", "[EXEMPLE] Réunion du collectif", start = AgendaClock.at(2026, 11, 18, 19), end = AgendaClock.at(2026, 11, 18, 21))
        server.seed(own)
        device.store.store(own)
        device.state.recordProposal(own.id, ProposalRecord(ProposalReview.PUBLISHED))
        setContent { TestAgenda(device, 360, 740) }
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag(AgendaTags.CARD + "ma-reunion").fetchSemanticsNodes().isNotEmpty() }
        // Someone else's event cannot be cancelled from here.
        onNodeWithTag(AgendaTags.CARD + "rassemblement-services-publics-paris-2026-11-14").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.CANCEL_EVENT).assertDoesNotExist()
        onNodeWithTag(AgendaTags.BACK).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(AgendaTags.CARD + "ma-reunion").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.CANCEL_EVENT).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(AgendaTags.CANCEL_EVENT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.CANCEL_DIALOG).assertExists()
        // The safe choice first: keeping the event sends nothing.
        onNodeWithTag(AgendaTags.CANCEL_KEEP).performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertTrue(server.received.isEmpty())

        onNodeWithTag(AgendaTags.CANCEL_EVENT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.CANCEL_CONFIRM).performClick()
        mainClock.advanceTimeBy(500)
        waitUntil(timeoutMillis = 10_000) { server.received.isNotEmpty() }
        val cancellation = server.received.single()
        val cancelled = Mobilization.from(cancellation)!!
        assertEquals(MobilizationStatus.ANNULE, cancelled.status)
        assertEquals("ma-reunion", cancelled.identifier)
        assertTrue(server.held.isEmpty(), "published at once, without review")
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.CANCEL_MESSAGE).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(AgendaTags.CANCEL_MESSAGE).performScrollTo().assertTextContains("Votre événement est maintenant annoncé comme annulé.")
        onNodeWithTag(AgendaTags.NOTICE).performScrollTo().assertTextContains("annulé", substring = true)
        onNodeWithTag(AgendaTags.CANCEL_EVENT).assertDoesNotExist()
        // On the list too, very visible.
        onNodeWithTag(AgendaTags.BACK).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.CARD + "ma-reunion").performScrollTo().assertTextContains("Annulé", substring = true)
        assertEquals(MobilizationStatus.ANNULE, Mobilization.from(device.store.recent(listOf(Mobilization.KIND), 100).single { it.firstTagValue("d") == "ma-reunion" })!!.status)
    }

    @Test
    fun aHeldEventWhoseDateHasPassedShowsTheServersRefusal() = runComposeUiTest {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        val late = AgendaKit(device.keys).mobilization("trop-tard", "[EXEMPLE] Rassemblement d'hier", start = AgendaClock.NOW - 86_400, end = AgendaClock.NOW - 80_000, createdAt = AgendaClock.NOW - 5 * 86_400)
        runBlocking { server.publish(late) }
        device.store.store(late)
        device.state.recordProposal(late.id, ProposalRecord(ProposalReview.REVIEW))
        server.decide(late.id, HeldStatus.REJECTED, "[EXEMPLE] La date de l’événement est passée avant sa relecture.")
        setContent { TestAgenda(device, 360, 740) }
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag(AgendaTags.TAB + "mine").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(AgendaTags.TAB + "mine").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.OWN_STATE + "not_published", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(AgendaTags.MINE_ITEM + "trop-tard")
            .assertTextContains("Non publié", substring = true)
            .assertTextContains("Raison donnée par l’équipe : [EXEMPLE] La date de l’événement est passée avant sa relecture.", substring = true)
        // Never on the agenda, not even among the past ones.
        onNodeWithTag(AgendaTags.TAB + "past").performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.CARD + "trop-tard").assertDoesNotExist()
    }
}
