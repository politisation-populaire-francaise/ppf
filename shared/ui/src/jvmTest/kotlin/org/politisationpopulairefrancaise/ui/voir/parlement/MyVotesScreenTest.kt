package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.elus.ElusDirectory
import org.politisationpopulairefrancaise.core.elus.InMemoryElusStorage
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryVoteAlertStorage
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * « Mes élus ont voté » (VO-34, D-102) in the Parliament of « Voir »: reached from the list, it asks for
 * a commune when none is chosen, keeps « seulement en Wi-Fi » on by default, follows no member of the
 * European Parliament by default, and keeps the followed words on the device only.
 */
@OptIn(ExperimentalTestApi::class)
class MyVotesScreenTest {

    private val keys = ParliamentUiFixtures.Keys()
    private val packages = ParliamentUiFixtures.packages()
    private val storage = InMemoryVoteAlertStorage()

    private fun myVotesTest(block: suspend ComposeUiTest.() -> Unit) =
        runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes, block)

    private suspend fun ComposeUiTest.openPage(): ParliamentUiFixtures.Server {
        val server = ParliamentUiFixtures.Server(keys.sign(ParliamentUiFixtures.index(packages)), packages)
        var holder: ParliamentState? = null
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember {
                val myVotes = MyVotesState(storage, ElusDirectory(null, InMemoryElusStorage()), scope)
                ParliamentState(ParliamentRepository(server, InMemoryParliamentStorage(), trust = keys.trust, clock = { ParliamentUiFixtures.NOW }), scope, myVotes).also { it.open() }
            }
            holder = state
            PpfTheme(darkTheme = false) { Box(Modifier.requiredSize(400.dp, 760.dp)) { if (state.isOpen) ParliamentScreen(state) } }
        }
        waitUntil(timeoutMillis = 10_000) { holder?.loading == false && holder?.opening != null }
        // The members first, for the choice of the members of the European Parliament.
        onNodeWithTag(ParliamentTags.DOWNLOAD_MEMBERS).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ParliamentTags.DOWNLOAD_MEMBERS).fetchSemanticsNodes().isEmpty() }
        onNodeWithTag(MyVotesTags.ENTRY).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(MyVotesTags.PAGE).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        return server
    }

    @Test
    fun withoutCommuneThePageAsksForOneAndShowsNoBallot() = myVotesTest {
        val server = openPage()
        onNodeWithTag(MyVotesTags.NO_COMMUNE).assertExists()
        onNodeWithTag(MyVotesTags.NONE).performScrollTo().assertExists()
        // Opening the page asked nothing more of the server.
        assertEquals(listOf(null, "membres"), server.requests)
    }

    @Test
    fun wifiOnlyIsOnByDefaultAndCanBeTurnedOff() = myVotesTest {
        openPage()
        onNodeWithTag(MyVotesTags.WIFI_ONLY).performScrollTo().assertIsOn()
        onNodeWithTag(MyVotesTags.WIFI_ONLY).performClick()
        waitForIdle()
        onNodeWithTag(MyVotesTags.WIFI_ONLY).assertIsOff()
        assertEquals(false, storage.wifiOnly())
    }

    @Test
    fun noMemberOfTheEuropeanParliamentIsFollowedByDefault() = myVotesTest {
        openPage()
        onNodeWithTag(MyVotesTags.EUROPEANS_COUNT).performScrollTo().assert(hasText("0 suivis."))
        onNodeWithTag(MyVotesTags.EUROPEANS_ALL).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(MyVotesTags.EUROPEANS_COUNT).assert(hasText("1 suivis."))
        assertEquals(setOf("pe:990001"), storage.europeans())
        onNodeWithTag(MyVotesTags.EUROPEANS_NONE).performScrollTo().performClick()
        waitForIdle()
        assertTrue(storage.europeans().isEmpty())
    }

    @Test
    fun aFollowedWordStaysOnTheDeviceAndIsNotAddedTwice() = myVotesTest {
        val server = openPage()
        onNodeWithTag(MyVotesTags.WORD_FIELD).performScrollTo().performTextInput("Retraite")
        onNodeWithTag(MyVotesTags.WORD_ADD).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(MyVotesTags.word("Retraite")).performScrollTo().assertExists()
        assertEquals(listOf("Retraite"), storage.words())
        onNodeWithTag(MyVotesTags.WORD_FIELD).performScrollTo().performTextInput("RETRAITE")
        onNodeWithTag(MyVotesTags.WORD_ADD).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(MyVotesTags.WORD_PROBLEM).assert(hasText("Ce mot est déjà suivi."))
        assertTrue(server.requests.none { it.orEmpty().contains("retraite", ignoreCase = true) })
    }
}
