package org.politisationpopulairefrancaise.ui.revendications.propose

import org.politisationpopulairefrancaise.ui.shell.openSpace
import androidx.compose.ui.test.ComposeTimeoutException
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

    private companion object {
        /**
         * No step of this test waits longer: a stuck one fails and says where. 30 s, as in main (0ae239d), for
         * the first composition on a loaded machine; nothing here waits on a real network.
         */
        const val MAX_WAIT_MILLIS: Long = 30_000
    }

    private fun ComposeUiTest.openForm(dependencies: AppDependencies) {
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        openSpace(Space.REVENDICATIONS)
        waitOrDump("the form's button") { onAllNodesWithTag(RevendicationTags.PROPOSE).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasTestTag(RevendicationTags.PROPOSE) and hasAnyAncestor(hasTestTag(RevendicationTags.PAGE + "tout"))).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposalTags.SCREEN).assertExists()
    }

    private fun ComposeUiTest.fillAndSend() {
        onNodeWithTag(ProposalTags.TITLE).performTextInput(title)
        if (onAllNodesWithTag(ProposalTags.WHY).fetchSemanticsNodes().isEmpty()) onNodeWithTag(ProposalTags.DETAILS).performScrollTo().performClick()
        onNodeWithTag(ProposalTags.WHY).performScrollTo().performTextInput("Le soir, il ne reste que les urgences.")
        onNodeWithTag(ProposalTags.ASKS).performScrollTo().performTextInput("Une permanence de garde par canton.")
        onNodeWithTag(ProposalTags.THEME + "sante").performScrollTo().performClick()
        onNodeWithTag(ProposalTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposalTags.SEND).performScrollTo().performClick()
        waitOrDump("the result of the sending") { onAllNodesWithTag(ProposalTags.RESULT).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.showMine() {
        onNodeWithTag(ProposalTags.RESULT_BACK).performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.TAB + "mes-revendications").performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    /**
     * A server that cannot be reached, answering at once: the same path as no server at all for the demand
     * (kept as pending, sent at the next synchronisation), without any real network nor real waiting.
     */
    private object UnreachableServer : RemoteEvents {
        override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Unavailable()
        override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
    }

    /**
     * [waitUntil] with an explicit maximum; on a timeout, the state of every thread goes to the test's error output
     * (seen once in a full run of every module: the sending never ended, while it takes a second alone).
     */
    private fun ComposeUiTest.waitOrDump(what: String, condition: () -> Boolean) {
        try {
            waitUntil(timeoutMillis = MAX_WAIT_MILLIS, condition = condition)
        } catch (e: ComposeTimeoutException) {
            System.err.println("ProposeFromTheWallTest: $what did not come within $MAX_WAIT_MILLIS ms. Threads:")
            Thread.getAllStackTraces().forEach { (thread, frames) ->
                System.err.println("  ${thread.name} (${thread.state}): " + frames.take(12).joinToString(" <- "))
            }
            throw e
        }
    }

    @Test
    fun withoutServerTheDemandWaitsInMesRevendicationsAndStaysOffTheWall() = runComposeUiTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        openForm(AppDependencies(InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase)), contentStore = store, remote = UnreachableServer))
        fillAndSend()
        onNodeWithText("Revendication gardée sur cet appareil").assertExists()
        assertEquals(1, store.pending().size)

        showMine()
        waitOrDump("the waiting demand in Mes revendications") { onAllNodesWithTag(RevendicationTags.OWN_STATE + "waiting", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
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
        waitOrDump("the demand in review in Mes revendications") { onAllNodesWithTag(RevendicationTags.OWN_STATE + "in_review", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(title).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.OWN_REVIEW).assertExists()
        onNodeWithText("[EXEMPLE] Avis de l'IA.").assertExists()
        onNodeWithText("Analyse par IA, à confirmer par l’équipe").assertExists()

    }
}
