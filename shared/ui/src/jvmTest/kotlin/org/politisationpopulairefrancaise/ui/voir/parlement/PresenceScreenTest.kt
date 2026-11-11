package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * « Présence et délégations » (VO-35): the definition first, one line per group in alphabetical order over the
 * solemn ballots on the device, read by screen readers as one sentence per group, nothing member by member.
 */
@OptIn(ExperimentalTestApi::class)
class PresenceScreenTest {

    private val keys = ParliamentUiFixtures.Keys()
    private val packages = ParliamentUiFixtures.packages()

    private fun presenceTest(block: suspend ComposeUiTest.() -> Unit) =
        runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes, block)

    private suspend fun ComposeUiTest.show(month: String?) {
        val server = ParliamentUiFixtures.Server(keys.sign(ParliamentUiFixtures.index(packages)), packages)
        var holder: ParliamentState? = null
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember { ParliamentState(ParliamentRepository(server, InMemoryParliamentStorage(), trust = keys.trust, clock = { ParliamentUiFixtures.NOW }), scope).also { it.open() } }
            holder = state
            PpfTheme(darkTheme = false) { Box(Modifier.requiredSize(1000.dp, 760.dp)) { if (state.isOpen) ParliamentScreen(state) } }
        }
        waitUntil(timeoutMillis = 10_000) { holder?.loading == false && holder?.opening != null }
        onNodeWithTag(ParliamentTags.DOWNLOAD_MEMBERS).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ParliamentTags.DOWNLOAD_MEMBERS).fetchSemanticsNodes().isEmpty() }
        if (month != null) {
            onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.month(month)))
            onNodeWithTag(ParliamentTags.month(month)).performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasTestTag(ParliamentTags.month(month)) and hasText("sur cet appareil", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(PresenceTags.ENTRY))
        onNodeWithTag(PresenceTags.ENTRY).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(PresenceTags.PAGE).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
    }

    private fun ComposeUiTest.description(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()

    @Test
    fun withoutSolemnBallotThePageSaysWhatToDownload() = presenceTest {
        show(month = null)
        onNodeWithTag(PresenceTags.DEFINITION).assertExists()
        onNodeWithTag(PresenceTags.NONE).performScrollTo().assertExists()
    }

    @Test
    fun eachGroupIsOneSentenceWithItsFourOutcomes() = presenceTest {
        show(month = "scrutins-an-2026-10")
        onNodeWithTag(PresenceTags.TABLE).performScrollTo().assertExists()
        val acacias = description(PresenceTags.row("an:PO900002"))
        assertTrue(acacias.startsWith("Groupe des acacias (GA) (membres : 1 ; votes attendus : 1)"), acacias)
        assertTrue(acacias.contains("par délégation 100 %"), acacias)
        val tilleuls = description(PresenceTags.row("an:PO900001"))
        assertTrue(tilleuls.contains("(membres : 2 ; votes attendus : 1)") && tilleuls.contains("ont voté eux-mêmes 100 %"), tilleuls)
        // Alphabetical order of the groups.
        val top = { tag: String -> onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y }
        assertTrue(top(PresenceTags.row("an:PO900002")) < top(PresenceTags.row("an:PO900001")))
    }
}
