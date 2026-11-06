package org.politisationpopulairefrancaise.ui.revendications.propose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.revendications.ProposalState
import org.politisationpopulairefrancaise.core.revendications.ProposalStatus
import org.politisationpopulairefrancaise.core.revendications.ProposalStatusSource
import org.politisationpopulairefrancaise.core.revendications.ProposalStatuses
import org.politisationpopulairefrancaise.core.revendications.checks.IssueKind
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSeverity
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSource
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalIssue
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.revendications.RevendicationTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals

/** From the wall to the form and back: the user's own demand waits, is reviewed, and only then joins the wall. */
@OptIn(ExperimentalTestApi::class)
class ProposeFromTheWallTest {
    private val phrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")
    private val title = "Un médecin de garde joignable le soir dans chaque canton rural"

    private fun ComposeUiTest.openForm(dependencies: AppDependencies) {
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        onNodeWithTag(ShellTags.navItem(Space.REVENDICATIONS)).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(RevendicationTags.PROPOSE).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasTestTag(RevendicationTags.PROPOSE) and hasAnyAncestor(hasTestTag(RevendicationTags.PAGE + "tout"))).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposalTags.SCREEN).assertExists()
    }

    private fun ComposeUiTest.fillAndSend() {
        onNodeWithTag(ProposalTags.TITLE).performTextInput(title)
        onNodeWithTag(ProposalTags.WHY).performScrollTo().performTextInput("Le soir, il ne reste que les urgences.")
        onNodeWithTag(ProposalTags.ASKS).performScrollTo().performTextInput("Une permanence de garde par canton.")
        onNodeWithTag(ProposalTags.THEME + "sante").performScrollTo().performClick()
        onNodeWithTag(ProposalTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ProposalTags.RESULT).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.showMine() {
        onNodeWithTag(ProposalTags.RESULT_BACK).performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.TAB + "mes-revendications").performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun withoutServerTheDemandWaitsInMesRevendicationsAndStaysOffTheWall() = runComposeUiTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        openForm(AppDependencies(InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase)), contentStore = store))
        fillAndSend()
        onNodeWithText("Revendication gardée sur cet appareil").assertExists()
        assertEquals(1, store.pending().size)

        showMine()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(RevendicationTags.OWN_STATE + "waiting", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(title).assertExists()

        onNodeWithTag(RevendicationTags.TAB + "tout").performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag(RevendicationTags.PAGE + "tout").assertExists()
        assertEquals(0, onAllNodesWithTag(RevendicationTags.OWN_STATE + "waiting", useUnmergedTree = true).fetchSemanticsNodes().size, "not on the wall before its review")
    }

    @Test
    fun aDemandSentForReviewShowsItsStateThenTheServersReport() = runComposeUiTest {
        val server = ReviewingServer()
        val store = LocalEventStore(JvmDatabase.inMemory())
        openForm(AppDependencies(InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase)), contentStore = store, remote = server))
        fillAndSend()
        onNodeWithText("Revendication envoyée en relecture").assertExists()
        assertEquals(1, server.held.size)

        showMine()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(RevendicationTags.OWN_STATE + "in_review", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(title).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.OWN_REVIEW).assertExists()
        onNodeWithText("[EXEMPLE] Avis de l'IA.").assertExists()
        onNodeWithText("Analyse par IA, à confirmer par l’équipe").assertExists()

    }
}
