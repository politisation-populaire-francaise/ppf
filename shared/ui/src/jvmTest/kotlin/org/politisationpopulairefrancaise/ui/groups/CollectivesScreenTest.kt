package org.politisationpopulairefrancaise.ui.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupMessage
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.messages.MessagesTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Collectifs" view of the messages and the rooms (D-027, D-028): joined rooms first, search,
 * suggestions by territory, joining without publishing anything, reading without an account,
 * writing a message signed by the account (sent at once or later), creating a room and the offer
 * to join the existing one when the server refuses a duplicate. Phone (360 dp) and desktop (1000 dp).
 */
@OptIn(ExperimentalTestApi::class)
class CollectivesScreenTest {

    private fun ComposeUiTest.openCollectives(dependencies: AppDependencies, width: Int = 360, height: Int = 740) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) } }
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        onNodeWithTag(MessagesTags.TAB + "collectifs").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(GroupsTags.LIST).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.awaitTag(tag: String, unmerged: Boolean = false) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.scrollListTo(tag: String) {
        onNodeWithTag(GroupsTags.LIST).performScrollToNode(hasTestTag(tag))
        waitForIdle()
    }

    /** Waits until the list holds the room (it may lie below the visible part of the list), then shows it. */
    private fun ComposeUiTest.awaitRoom(id: String) {
        waitUntil(timeoutMillis = 10_000) {
            runCatching { onNodeWithTag(GroupsTags.LIST).performScrollToNode(hasTestTag(GroupsTags.ROOM + id)) }.isSuccess
        }
        waitForIdle()
    }

    /** The button at the top of the list, wherever the list was scrolled to. */
    private fun ComposeUiTest.clickCreate() {
        onNodeWithTag(GroupsTags.LIST).performScrollToIndex(0)
        waitForIdle()
        onNodeWithTag(GroupsTags.CREATE).performClick()
    }

    private fun ComposeUiTest.openRoom(id: String) {
        awaitRoom(id)
        onNodeWithTag(GroupsTags.ROOM + id).performClick()
        waitForIdle()
        awaitTag(GroupsTags.SCREEN)
    }

    private fun count(tag: String, test: ComposeUiTest, unmerged: Boolean = false) =
        test.onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().size

    @Test
    fun onAPhoneJoiningARoomPutsItFirstAndPublishesNothing() = runComposeUiTest {
        val server = RoomsFixtures.server()
        val dependencies = RoomsFixtures.withoutAccount(server)
        openCollectives(dependencies)
        awaitTag(GroupsTags.MINE_EMPTY)
        awaitRoom(RoomsFixtures.LIVREURS)

        onNodeWithTag(GroupsTags.JOIN + RoomsFixtures.LIVREURS, useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(GroupsTags.LIST).performScrollToIndex(0)
        waitForIdle()
        awaitTag(GroupsTags.JOINED + RoomsFixtures.LIVREURS, unmerged = true)
        assertEquals(0, count(GroupsTags.MINE_EMPTY, this))

        // "Mes salons" first: the joined room sits above the section of suggestions.
        awaitTag(GroupsTags.SUGGESTIONS)
        val mineTop = onNodeWithTag(GroupsTags.ROOM + RoomsFixtures.LIVREURS).fetchSemanticsNode().boundsInRoot.top
        val suggestionsTop = onNodeWithTag(GroupsTags.SUGGESTIONS).fetchSemanticsNode().boundsInRoot.top
        assertTrue(mineTop < suggestionsTop, "joined rooms come before the suggestions")

        // Nothing was published, and the list is kept on the device only.
        assertTrue(server.published.isEmpty(), "joining publishes nothing")
        val database = dependencies.database!!
        waitUntil(timeoutMillis = 5_000) { GroupStore(database).isMember(RoomsFixtures.LIVREURS) }
    }

    @Test
    fun theSearchFiltersTheRoomsAndOffersToCreateTheMissingOne() = runComposeUiTest {
        openCollectives(RoomsFixtures.withoutAccount(RoomsFixtures.server()))
        awaitRoom(RoomsFixtures.QUARTIER)
        onNodeWithTag(GroupsTags.SEARCH).performTextInput("livreurs")
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        awaitRoom(RoomsFixtures.LIVREURS)
        assertEquals(0, count(GroupsTags.ROOM + RoomsFixtures.QUARTIER, this))
        assertEquals(0, count(GroupsTags.ROOM + RoomsFixtures.SANTE, this))

        onNodeWithTag(GroupsTags.SEARCH).performTextInput(" de nuit")
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        awaitTag(GroupsTags.EMPTY)
        onNodeWithText("Aucun salon ne correspond à « livreurs de nuit ».").assertExists()
        scrollListTo(GroupsTags.CREATE_FROM_QUERY)
        onNodeWithText("Créer le salon « livreurs de nuit »").performClick()
        waitForIdle()
        awaitTag(GroupsTags.FORM)
        onNodeWithTag(GroupsTags.FORM_NAME).assert(hasText("livreurs de nuit"))
    }

    @Test
    fun suggestionsByTerritoryKeepTheRoomsOfOnePlace() = runComposeUiTest {
        openCollectives(RoomsFixtures.withoutAccount(RoomsFixtures.server()), width = 1000)
        awaitTag(GroupsTags.TERRITORY + "lyon")
        onNodeWithTag(GroupsTags.TERRITORY + "paris").performClick()
        waitForIdle()
        awaitRoom(RoomsFixtures.QUARTIER)
        assertEquals(0, count(GroupsTags.ROOM + RoomsFixtures.SANTE, this))
        assertEquals(0, count(GroupsTags.ROOM + RoomsFixtures.LIVREURS, this))

        onNodeWithTag(GroupsTags.TERRITORY + "all").performClick()
        waitForIdle()
        awaitRoom(RoomsFixtures.SANTE)
    }

    @Test
    fun withoutAccountARoomIsReadableAndWritingAsksForAnAccount() = runComposeUiTest {
        openCollectives(RoomsFixtures.withoutAccount(RoomsFixtures.server()))
        openRoom(RoomsFixtures.SANTE)
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(RoomsFixtures.FIRST_MESSAGE, substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(RoomsFixtures.SECOND_MESSAGE, substring = true).assertExists()
        onNodeWithTag(GroupsTags.TITLE).assert(hasText("Collectif santé"))
        onNodeWithTag(GroupsTags.NOTICE).performScrollTo().assertExists()
        onNodeWithText("pas chiffrés de bout en bout", substring = true).assertExists()
        onNodeWithTag(GroupsTags.NEED_ACCOUNT).assertExists()
        assertEquals(0, count(GroupsTags.INPUT, this))

        // On a phone the room takes the whole screen; the arrow leads back to the list.
        assertEquals(0, count(GroupsTags.LIST, this))
        onNodeWithTag(GroupsTags.BACK).performClick()
        waitForIdle()
        awaitTag(GroupsTags.LIST)
        assertEquals(0, count(GroupsTags.SCREEN, this))
    }

    @Test
    fun aMessageIsSignedByTheAccountAndSentToTheRoom() = runComposeUiTest {
        val server = RoomsFixtures.server()
        openCollectives(RoomsFixtures.withAccount(server))
        openRoom(RoomsFixtures.SANTE)
        onNodeWithTag(GroupsTags.JOIN_TO_WRITE).performClick()
        waitForIdle()
        awaitTag(GroupsTags.INPUT)
        // The tag sits on the box around the button; the button itself is announced as "Envoyer".
        onNodeWithContentDescription("Envoyer").assertIsNotEnabled()
        onNodeWithTag(GroupsTags.INPUT).performTextInput("[EXEMPLE] Je viendrai jeudi.")
        waitForIdle()
        onNodeWithContentDescription("Envoyer").assertIsEnabled()
        onNodeWithTag(GroupsTags.SEND).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { server.published.any { it.kind == GroupMessage.KIND } }

        val sent = server.published.single { it.kind == GroupMessage.KIND }
        assertEquals(RoomsFixtures.ownKey, sent.pubkey)
        assertEquals(listOf(listOf("h", RoomsFixtures.SANTE)), sent.tags)
        assertEquals("[EXEMPLE] Je viendrai jeudi.", sent.content)
        assertTrue(sent.verify())
        awaitTag(GroupsTags.MESSAGE + sent.id)
        onNode(hasText("Vous", substring = true) and hasAnyAncestor(hasTestTag(GroupsTags.MESSAGE + sent.id))).assertExists()
        assertEquals(0, count(GroupsTags.MESSAGE_PENDING + sent.id, this, unmerged = true))
        // Leaving is local too.
        onNodeWithTag(GroupsTags.ROOM_LEAVE).performClick()
        waitForIdle()
        awaitTag(GroupsTags.ROOM_JOIN)
        assertEquals(1, server.published.size)
    }

    @Test
    fun aMessageWrittenOfflineWaitsOnTheDeviceThenLeaves() = runComposeUiTest {
        val server = RoomsFixtures.server()
        val dependencies = RoomsFixtures.withAccount(server)
        openCollectives(dependencies)
        openRoom(RoomsFixtures.SANTE)
        onNodeWithTag(GroupsTags.JOIN_TO_WRITE).performClick()
        waitForIdle()
        server.online = false
        onNodeWithTag(GroupsTags.INPUT).performTextInput("[EXEMPLE] Message sans réseau.")
        onNodeWithTag(GroupsTags.SEND).performClick()
        waitForIdle()
        val store = dependencies.contentStore!!
        waitUntil(timeoutMillis = 10_000) { store.pending().any { it.kind == GroupMessage.KIND } }
        val waiting = store.pending().single { it.kind == GroupMessage.KIND }
        awaitTag(GroupsTags.MESSAGE_PENDING + waiting.id, unmerged = true)
        awaitTag(GroupsTags.MESSAGES_STATUS)

        server.online = true
        onNodeWithTag(GroupsTags.REFRESH_MESSAGES).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { server.stored.any { it.id == waiting.id } }
        waitUntil(timeoutMillis = 10_000) { count(GroupsTags.MESSAGE_PENDING + waiting.id, this, unmerged = true) == 0 }
        assertTrue(store.pending().none { it.id == waiting.id })
    }

    @Test
    fun onADesktopARoomIsCreatedBesideTheListAfterTheCharter() = runComposeUiTest {
        val server = RoomsFixtures.server()
        val dependencies = RoomsFixtures.withAccount(server)
        openCollectives(dependencies, width = 1000)
        awaitTag(GroupsTags.SELECT_HINT)
        clickCreate()
        waitForIdle()
        awaitTag(GroupsTags.FORM)
        onNodeWithTag(GroupsTags.LIST).assertExists()

        onNodeWithTag(GroupsTags.FORM_NAME).performTextInput("[EXEMPLE] Collectif vélo")
        onNodeWithTag(GroupsTags.FORM_TERRITORY).performTextInput("Lyon")
        onNodeWithTag(GroupsTags.FORM_ABOUT).performTextInput("[EXEMPLE] Des pistes cyclables sûres.")
        waitForIdle()
        onNodeWithTag(GroupsTags.FORM_ID).assert(hasText("Identifiant du salon : collectif-velo-lyon"))
        // The safe choice first: the charter is unticked, the button waits for it, and the warning about the pseudonym is shown.
        onNodeWithTag(GroupsTags.FORM_SUBMIT).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(GroupsTags.FORM_BLOCKED).assertExists()
        onNodeWithTag(GroupsTags.FORM_IDENTITY).assertExists()
        onNodeWithTag(GroupsTags.FORM_CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(GroupsTags.FORM_SUBMIT).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        awaitTag(GroupsTags.SCREEN)

        val described = server.published.single { it.kind == Group.KIND }
        assertEquals(RoomsFixtures.ownKey, described.pubkey)
        val room = Group.from(described)!!
        assertEquals("collectif-velo-lyon", room.id)
        assertEquals("[EXEMPLE] Collectif vélo", room.name)
        assertEquals("Lyon", room.territory)
        assertEquals(Group.CHARTER_VERSION, room.charter)
        onNodeWithTag(GroupsTags.TITLE).assert(hasText("Collectif vélo"))
        // The creator joined it on this device; the list stays beside the room.
        onNodeWithTag(GroupsTags.LIST).assertExists()
        awaitTag(GroupsTags.JOINED + "collectif-velo-lyon", unmerged = true)
        waitUntil(timeoutMillis = 5_000) { GroupStore(dependencies.database!!).isMember("collectif-velo-lyon") }
    }

    @Test
    fun aDuplicateRefusedByTheServerOffersToJoinTheExistingRoom() = runComposeUiTest {
        val server = RoomsFixtures.server()
        // The existing room is not in the list the device received: only the server knows it.
        server.hiddenFromDirectory += RoomsFixtures.SANTE
        openCollectives(RoomsFixtures.withAccount(server), width = 1000)
        awaitRoom(RoomsFixtures.LIVREURS)
        assertEquals(0, count(GroupsTags.ROOM + RoomsFixtures.SANTE, this))

        clickCreate()
        waitForIdle()
        onNodeWithTag(GroupsTags.FORM_NAME).performTextInput("[EXEMPLE] Collectif sante")
        onNodeWithTag(GroupsTags.FORM_TERRITORY).performTextInput("lyon")
        onNodeWithTag(GroupsTags.FORM_CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(GroupsTags.FORM_SUBMIT).performScrollTo().performClick()
        waitForIdle()
        awaitTag(GroupsTags.FORM_EXISTS)
        onNodeWithText("« Collectif santé »", substring = true).assertExists()
        assertTrue(server.stored.none { it.kind == Group.KIND && it.pubkey == RoomsFixtures.ownKey }, "no second room was created")

        onNodeWithTag(GroupsTags.FORM_JOIN_EXISTING).performScrollTo().performClick()
        waitForIdle()
        awaitTag(GroupsTags.SCREEN)
        onNodeWithTag(GroupsTags.TITLE).assert(hasText("Collectif santé"))
        awaitTag(GroupsTags.ROOM_LEAVE)
        awaitTag(GroupsTags.JOINED + RoomsFixtures.SANTE, unmerged = true)
    }

    @Test
    fun theFormShowsTheRoomsThatLookAlikeAndNeedsAnAccountToCreate() = runComposeUiTest {
        openCollectives(RoomsFixtures.withoutAccount(RoomsFixtures.server()))
        awaitRoom(RoomsFixtures.SANTE)
        clickCreate()
        waitForIdle()
        awaitTag(GroupsTags.FORM)
        onNodeWithTag(GroupsTags.FORM_NAME).performTextInput("Collectif santé")
        waitForIdle()
        awaitTag(GroupsTags.FORM_SIMILAR)
        onNodeWithTag(GroupsTags.ROOM + RoomsFixtures.SANTE).assertExists()
        onNodeWithTag(GroupsTags.FORM_NEED_ACCOUNT).performScrollTo().assertExists()
        assertEquals(0, count(GroupsTags.FORM_SUBMIT, this))
        onNodeWithTag(GroupsTags.FORM_CANCEL).performScrollTo().performClick()
        waitForIdle()
        awaitTag(GroupsTags.LIST)
    }

    @Test
    fun withoutServerTheRoomsWaitAndCreationIsExplained() = runComposeUiTest {
        openCollectives(RoomsFixtures.withAccount(server = null))
        awaitTag(GroupsTags.EMPTY)
        onNodeWithText("Hors ligne : salons enregistrés sur l’appareil.").assertExists()
        clickCreate()
        waitForIdle()
        awaitTag(GroupsTags.FORM_NO_SERVER)
        onNodeWithTag(GroupsTags.FORM_NAME).performTextInput("[EXEMPLE] Collectif hors ligne")
        onNodeWithTag(GroupsTags.FORM_TERRITORY).performTextInput("Lille")
        onNodeWithTag(GroupsTags.FORM_CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(GroupsTags.FORM_SUBMIT).performScrollTo().assertIsNotEnabled()

        // What was typed survives a visit to another space.
        onNodeWithTag(ShellTags.navItem(Space.FIL)).performClick()
        waitForIdle()
        assertEquals(0, count(GroupsTags.FORM, this))
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        awaitTag(GroupsTags.FORM)
        onNodeWithTag(GroupsTags.FORM_NAME).assert(hasText("[EXEMPLE] Collectif hors ligne"))
        onNodeWithTag(GroupsTags.FORM_TERRITORY).assert(hasText("Lille"))
    }
}
