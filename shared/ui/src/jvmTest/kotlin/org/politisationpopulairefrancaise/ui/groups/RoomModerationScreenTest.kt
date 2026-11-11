package org.politisationpopulairefrancaise.ui.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.SimilarRoom
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.messages.MessagesTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Roles and moderation of a room by its collective (D-131): the menus of a message and of its author,
 * the confirmations that say the action is public and signed, the removed message, "À propos du
 * salon"; and the guardrails of the creation (D-132): rooms of close name, held creation. Phone
 * (360 dp) and desktop (1000 dp).
 */
@OptIn(ExperimentalTestApi::class)
class RoomModerationScreenTest {

    private val own = LocalEventSigner(RoomsFixtures.phrase.deriveKeys())

    /** The example rooms, plus one created by the user's own account (who is its administrator) with a message of another member. */
    private fun server(): RoomsServer = RoomsFixtures.server().apply {
        val now = currentEpochSeconds()
        seed(runBlocking { own.sign(Group.build(own.publicKey, MINE, "[EXEMPLE] Collectif logement", "[EXEMPLE] Pour des loyers justes.", "Lyon", createdAt = now - 7_200)) })
        seed(RoomsFixtures.message(MINE, OTHER_TEXT, now - 3_600))
    }

    private fun ComposeUiTest.openRoom(dependencies: AppDependencies, id: String, width: Int = 360) {
        setContent { Box(Modifier.requiredSize(width.dp, 740.dp)) { PpfApp(dependencies) } }
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        onNodeWithTag(MessagesTags.TAB + "collectifs").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithTag(GroupsTags.LIST).fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = WAIT_MILLIS) {
            runCatching { onNodeWithTag(GroupsTags.LIST).performScrollToNode(hasTestTag(GroupsTags.ROOM + id)) }.isSuccess
        }
        waitForIdle()
        onNodeWithTag(GroupsTags.ROOM + id).performClick()
        waitForIdle()
        awaitTag(GroupsTags.SCREEN)
    }

    private fun ComposeUiTest.awaitTag(tag: String, unmerged: Boolean = false) =
        waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.count(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun otherMessageId(server: RoomsServer) = server.stored.single { it.content == OTHER_TEXT }.id

    private fun ComposeUiTest.openMenuOf(messageId: String) {
        awaitTag(ModerationTags.MESSAGE_MENU + messageId)
        onNodeWithTag(ModerationTags.MESSAGE_MENU + messageId).performClick()
        waitForIdle()
    }

    @Test
    fun onAPhoneAnAdministratorExcludesThenReadmitsAnAuthorAfterAPublicConfirmation() = runComposeUiTest {
        val server = server()
        openRoom(RoomsFixtures.withAccount(server), MINE)
        val messageId = otherMessageId(server)
        openMenuOf(messageId)
        onNodeWithTag(ModerationTags.MENU_REMOVE).assertExists()
        onNodeWithTag(ModerationTags.MENU_ADMIN).assertExists()
        onNodeWithTag(ModerationTags.MENU_MODERATOR).assertExists()
        assertEquals(0, count(ModerationTags.MENU_READMIT))
        onNodeWithTag(ModerationTags.MENU_EXCLUDE).performClick()
        waitForIdle()

        // Public and signed, the safe choice first; the reason is mandatory.
        awaitTag(ModerationTags.DIALOG)
        onNodeWithTag(ModerationTags.DIALOG_PUBLIC).assertExists()
        onNodeWithText("publique", substring = true).assertExists()
        val cancelTop = onNodeWithTag(ModerationTags.DIALOG_CANCEL).fetchSemanticsNode().boundsInRoot.top
        val confirmTop = onNodeWithTag(ModerationTags.DIALOG_CONFIRM).fetchSemanticsNode().boundsInRoot.top
        assertTrue(cancelTop < confirmTop, "the safe choice comes first")
        onNodeWithTag(ModerationTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        awaitTag(ModerationTags.DIALOG_REASON_MISSING)
        assertTrue(server.published.none { it.kind == RoomModeration.REMOVE_USER }, "nothing sent without a reason")
        onNodeWithTag(ModerationTags.DIALOG_REASON).performTextInput("[EXEMPLE] Propos hors charte")
        onNodeWithTag(ModerationTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = WAIT_MILLIS) { server.stored.any { it.kind == RoomModeration.REMOVE_USER } }
        val exclusion = server.stored.single { it.kind == RoomModeration.REMOVE_USER }
        assertEquals(RoomsFixtures.ownKey, exclusion.pubkey)
        assertEquals("[EXEMPLE] Propos hors charte", exclusion.content)
        assertEquals(listOf(RoomsFixtures.other.publicKey.hex), exclusion.tagValues("p"))
        waitUntil(timeoutMillis = WAIT_MILLIS) { count(ModerationTags.DIALOG) == 0 }

        // The menu now offers to readmit.
        openMenuOf(messageId)
        onNodeWithTag(ModerationTags.MENU_READMIT).performClick()
        waitForIdle()
        awaitTag(ModerationTags.DIALOG)
        onNodeWithTag(ModerationTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = WAIT_MILLIS) { server.stored.any { it.kind == RoomModeration.PUT_USER } }
        assertEquals(listOf(listOf("h", MINE), listOf("p", RoomsFixtures.other.publicKey.hex)), server.stored.single { it.kind == RoomModeration.PUT_USER }.tags)
        // The test ends once the screen is done with the readmission (dialog closed, room read again), like the
        // other tests of this class: ending while the sending coroutine still ran left it uncompleted, at random,
        // and the test failed after a minute (UncompletedCoroutinesError, seen on 11 November 2026).
        waitUntil(timeoutMillis = WAIT_MILLIS) { count(ModerationTags.DIALOG) == 0 }
        waitForIdle()
    }

    @Test
    fun aRemovedMessageShowsWhoRemovedItAndWhyAndCancellingSendsNothing() = runComposeUiTest {
        val server = server()
        openRoom(RoomsFixtures.withAccount(server), MINE)
        val messageId = otherMessageId(server)
        openMenuOf(messageId)
        onNodeWithTag(ModerationTags.MENU_REMOVE).performClick()
        waitForIdle()
        awaitTag(ModerationTags.DIALOG)
        onNodeWithTag(ModerationTags.DIALOG_CANCEL).performClick()
        waitForIdle()
        assertEquals(0, count(ModerationTags.DIALOG))
        assertTrue(server.published.isEmpty(), "cancelling sends nothing")

        openMenuOf(messageId)
        onNodeWithTag(ModerationTags.MENU_REMOVE).performClick()
        waitForIdle()
        onNodeWithTag(ModerationTags.DIALOG_REASON).performTextInput("[EXEMPLE] Hors sujet")
        onNodeWithTag(ModerationTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        awaitTag(ModerationTags.REMOVED + messageId)
        onNodeWithText("Message retiré par un modérateur : [EXEMPLE] Hors sujet").assertExists()
        assertEquals(0, count(GroupsTags.MESSAGE + messageId))
        assertEquals(listOf(messageId), server.stored.single { it.kind == RoomModeration.DELETE_EVENT }.tagValues("e"))
    }

    @Test
    fun aMemberWithoutRoleSeesNoModerationButTheAboutPage() = runComposeUiTest {
        val server = server()
        openRoom(RoomsFixtures.withAccount(server), RoomsFixtures.SANTE)
        awaitTag(GroupsTags.MESSAGES)
        val foreign = server.stored.first { it.content == RoomsFixtures.FIRST_MESSAGE }.id
        awaitTag(GroupsTags.MESSAGE + foreign)
        assertEquals(0, count(ModerationTags.MESSAGE_MENU + foreign), "no role: no menu on the messages of others")
        onNodeWithTag(ModerationTags.ABOUT_OPEN).performClick()
        waitForIdle()
        awaitTag(ModerationTags.ABOUT)
        onNodeWithTag(ModerationTags.ABOUT_ADMIN + RoomsFixtures.other.publicKey.hex).performScrollTo().assertExists()
        onNodeWithTag(ModerationTags.ABOUT_CLOSE).performClick()
        waitForIdle()
        awaitTag(GroupsTags.SCREEN)
    }

    @Test
    fun onADesktopTheAboutPageListsTheRolesAndThePublicJournal() = runComposeUiTest {
        val server = server()
        openRoom(RoomsFixtures.withAccount(server), MINE, width = 1000)
        val messageId = otherMessageId(server)
        openMenuOf(messageId)
        onNodeWithTag(ModerationTags.MENU_MODERATOR).performClick()
        waitForIdle()
        awaitTag(ModerationTags.DIALOG)
        assertEquals(0, count(ModerationTags.DIALOG_REASON), "naming needs no reason")
        onNodeWithTag(ModerationTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = WAIT_MILLIS) { server.stored.any { it.kind == RoomModeration.PUT_USER } }
        waitUntil(timeoutMillis = WAIT_MILLIS) { count(ModerationTags.DIALOG) == 0 }

        onNodeWithTag(ModerationTags.ABOUT_OPEN).performClick()
        waitForIdle()
        awaitTag(ModerationTags.ABOUT)
        onNodeWithTag(ModerationTags.ABOUT_DESCRIPTION).assertExists()
        // The description also shows in the room card of the list beside it: look in the page only.
        onNodeWithTag(ModerationTags.ABOUT_DESCRIPTION).assertTextContains("[EXEMPLE] Pour des loyers justes.")
        onNodeWithTag(ModerationTags.ABOUT_TERRITORY).assertExists()
        onNodeWithTag(ModerationTags.ABOUT_ADMIN + RoomsFixtures.ownKey).performScrollTo().assertExists()
        onNodeWithTag(ModerationTags.ABOUT_MODERATOR + RoomsFixtures.other.publicKey.hex).performScrollTo().assertExists()
        val naming = server.stored.single { it.kind == RoomModeration.PUT_USER }
        onNodeWithTag(ModerationTags.ABOUT_JOURNAL_ENTRY + naming.id).performScrollTo().assertExists()
        onNodeWithText("vous a nommé", substring = true).assertExists()
    }

    @Test
    fun roomsOfCloseNameAreOfferedThenTheCreationIsConfirmedWithItsDifference() = runComposeUiTest {
        val server = server()
        server.creationAnswer = { event ->
            if (event.tags.none { it.firstOrNull() == Group.TAG_DIFFERENCE }) {
                PublicationResult.Rejected(event.id, RejectionReason.GROUP_SIMILAR, similar = listOf(SimilarRoom(RoomsFixtures.SANTE, "[EXEMPLE] Collectif santé", "Lyon")))
            } else {
                null
            }
        }
        val dependencies = RoomsFixtures.withAccount(server)
        openRoom(dependencies, RoomsFixtures.LIVREURS, width = 1000)
        onNodeWithTag(GroupsTags.LIST).performScrollToIndex(0)
        waitForIdle()
        onNodeWithTag(GroupsTags.CREATE).performClick()
        waitForIdle()
        awaitTag(GroupsTags.FORM)
        onNodeWithTag(GroupsTags.FORM_NAME).performTextInput("[EXEMPLE] Santé publique")
        onNodeWithTag(GroupsTags.FORM_TERRITORY).performTextInput("Lyon")
        onNodeWithTag(GroupsTags.FORM_CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(GroupsTags.FORM_SUBMIT).performScrollTo().performClick()
        waitForIdle()
        awaitTag(GroupsTags.FORM_SIMILAR_SERVER)
        onNodeWithTag(GroupsTags.FORM_SIMILAR_JOIN + RoomsFixtures.SANTE).performScrollTo().assertExists()

        onNodeWithTag(GroupsTags.FORM_CONFIRM_SIMILAR).performScrollTo().performClick()
        waitForIdle()
        awaitTag(GroupsTags.FORM_DIFFERENCE_MISSING)
        onNodeWithTag(GroupsTags.FORM_DIFFERENCE).performScrollTo().performTextInput("[EXEMPLE] Nous parlons des hôpitaux")
        onNodeWithTag(GroupsTags.FORM_CONFIRM_SIMILAR).performScrollTo().performClick()
        waitForIdle()
        awaitTag(GroupsTags.SCREEN)
        val created = server.stored.single { it.kind == Group.KIND && it.pubkey == RoomsFixtures.ownKey && it.firstTagValue("d") != MINE }
        assertEquals(listOf("[EXEMPLE] Nous parlons des hôpitaux"), created.tagValues(Group.TAG_DIFFERENCE))
        assertEquals(listOf(Group.SIMILAR_CONFIRMED), created.tagValues(Group.TAG_SIMILAR))
    }

    @Test
    fun onAPhoneAHeldCreationIsAnnouncedAsWaitingForVerification() = runComposeUiTest {
        val server = server()
        server.creationAnswer = { event -> PublicationResult.Accepted(event.id, heldForReview = true) }
        openRoom(RoomsFixtures.withAccount(server), RoomsFixtures.LIVREURS)
        onNodeWithTag(GroupsTags.BACK).performClick()
        waitForIdle()
        awaitTag(GroupsTags.LIST)
        onNodeWithTag(GroupsTags.LIST).performScrollToIndex(0)
        waitForIdle()
        onNodeWithTag(GroupsTags.CREATE).performClick()
        waitForIdle()
        awaitTag(GroupsTags.FORM)
        onNodeWithTag(GroupsTags.FORM_NAME).performTextInput("[EXEMPLE] Union des locataires")
        onNodeWithTag(GroupsTags.FORM_TERRITORY).performTextInput("Lyon")
        onNodeWithTag(GroupsTags.FORM_CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(GroupsTags.FORM_SUBMIT).performScrollTo().performClick()
        waitForIdle()
        awaitTag(GroupsTags.FORM_HELD)
        onNodeWithText("En attente de vérification").assertExists()
        assertEquals(0, count(GroupsTags.FORM_SUBMIT))
        onNodeWithTag(GroupsTags.FORM_HELD_CLOSE).performScrollTo().performClick()
        waitForIdle()
        awaitTag(GroupsTags.LIST)
    }

    private companion object {
        /**
         * Every wait is for a state (a dialog closed, an event received), never a fixed delay; on a
         * loaded machine (a full build, max-workers=1) a room screen and its database work can take
         * well over 30 s, as for AgirHarness.awaitTag.
         */
        const val WAIT_MILLIS: Long = 60_000
        const val MINE = "collectif-logement-lyon"
        const val OTHER_TEXT = "[EXEMPLE] Les loyers augmentent encore."
    }
}
