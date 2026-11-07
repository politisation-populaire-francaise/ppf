package org.politisationpopulairefrancaise.ui.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.notifications.NotificationCandidate
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.NotificationSettings
import org.politisationpopulairefrancaise.core.notifications.Notifications
import org.politisationpopulairefrancaise.core.notifications.PushApi
import org.politisationpopulairefrancaise.core.notifications.PushEndpoint
import org.politisationpopulairefrancaise.core.notifications.PushManager
import org.politisationpopulairefrancaise.core.notifications.PushRegistrar
import org.politisationpopulairefrancaise.core.notifications.PushResult
import org.politisationpopulairefrancaise.core.notifications.PushScope
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.groups.RoomsFixtures
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The notifications of "Mon espace" and the notification centre (M31): everything off by default,
 * a category enabled after the system's permission, every loosening confirmed first, the centre
 * opened by the bell, and the bell disabled without notifications.
 */
@OptIn(ExperimentalTestApi::class)
class NotificationsUiTest {

    /** A device without any distributor application: wake-ups cannot be registered. */
    private class NoDistributor : PushRegistrar {
        override fun isAvailable(): Boolean = false
        override fun register(scope: PushScope, vapidPublicKey: String) = Unit
        override fun unregister(scope: PushScope) = Unit
    }

    private class FakePushApi : PushApi {
        override suspend fun vapidPublicKey(): PushResult<String> = PushResult.Done("vapid")
        override suspend fun subscribe(scope: PushScope, endpoint: PushEndpoint, signer: EventSigner?): PushResult<String> = PushResult.Done("id")
        override suspend fun unsubscribe(subscriptionId: String): PushResult<Unit> = PushResult.Done(Unit)
    }

    private class Device(withAccount: Boolean, withPush: Boolean, var granted: Boolean = true) {
        val database = JvmDatabase.inMemory()
        val identityStore = if (withAccount) {
            InMemoryIdentityStore(StoredIdentity.of(RoomsFixtures.phrase.deriveKeys(), RoomsFixtures.phrase))
        } else {
            InMemoryIdentityStore()
        }
        val notifications = Notifications(database, identityStore, remote = null, notifier = null)
        var asked = 0
        val push = if (withPush) PushManager(database, FakePushApi(), NoDistributor(), signer = { null }, onWake = { }) else null
        val dependencies = AppDependencies.withDatabase(
            identityStore,
            database,
            notifications = notifications,
            push = push,
            requestNotificationPermission = { answer ->
                asked++
                answer(granted)
            },
        )

        fun saved(): NotificationSettings = notifications.settings.load()
    }

    private fun ComposeUiTest.show(device: Device, width: Int = 360, height: Int = 740) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(device.dependencies) } }
        waitForIdle()
    }

    private fun ComposeUiTest.openMySpace(device: Device) {
        show(device)
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(NotificationsTags.CARD).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.switch(tag: String) = onNodeWithTag(tag).performScrollTo()

    @Test
    fun everythingIsOffByDefaultAndMessagesNeedAnAccount() = runComposeUiTest {
        val device = Device(withAccount = false, withPush = false)
        openMySpace(device)
        NotificationCategory.entries.forEach { switch(NotificationsTags.CATEGORY + it.key).assertIsOff() }
        switch(NotificationsTags.CATEGORY + NotificationCategory.MESSAGES.key).assertIsNotEnabled()
        switch(NotificationsTags.CATEGORY + NotificationCategory.VOTES.key).assertIsEnabled()
        switch(NotificationsTags.DETAILS).assertIsOff()
        switch(NotificationsTags.SILENCE).assertIsOff()
        // Without a server, nothing can wake the device: no wake-up switch, an explanation instead.
        onNodeWithTag(NotificationsTags.NO_SERVER).performScrollTo()
        assertEquals(0, onAllNodesWithTag(NotificationsTags.PUBLIC_WAKE).fetchSemanticsNodes().size)
        assertEquals(NotificationSettings.MOST_PROTECTIVE, device.saved())
    }

    @Test
    fun enablingACategoryAsksThePermissionThenSaves() = runComposeUiTest {
        val device = Device(withAccount = true, withPush = false)
        openMySpace(device)
        switch(NotificationsTags.CATEGORY + NotificationCategory.VOTES.key).performClick()
        waitForIdle()
        assertEquals(1, device.asked)
        assertEquals(setOf(NotificationCategory.VOTES), device.saved().enabled)
        switch(NotificationsTags.CATEGORY + NotificationCategory.VOTES.key).assertIsOn()
        assertEquals(0, onAllNodesWithTag(NotificationsTags.PERMISSION_REFUSED).fetchSemanticsNodes().size)

        // Refused by the system: enabled all the same, and the centre of the application is explained.
        device.granted = false
        switch(NotificationsTags.CATEGORY + NotificationCategory.MESSAGES.key).performClick()
        waitForIdle()
        assertEquals(2, device.asked)
        assertTrue(device.saved().isEnabled(NotificationCategory.MESSAGES))
        onNodeWithTag(NotificationsTags.PERMISSION_REFUSED).performScrollTo()

        // Turning a category off asks nothing.
        switch(NotificationsTags.CATEGORY + NotificationCategory.VOTES.key).performClick()
        waitForIdle()
        assertEquals(2, device.asked)
        assertEquals(setOf(NotificationCategory.MESSAGES), device.saved().enabled)
    }

    @Test
    fun relaxingIsConfirmedFirstAndCancelKeepsTheSetting() = runComposeUiTest {
        val device = Device(withAccount = true, withPush = true)
        openMySpace(device)

        switch(NotificationsTags.DETAILS).performClick()
        waitForIdle()
        onNodeWithText("Garder désactivé").assertExists()
        onNodeWithTag(NotificationsTags.WARNING_KEEP).performClick()
        waitForIdle()
        switch(NotificationsTags.DETAILS).assertIsOff()
        assertFalse(device.saved().showDetails)

        switch(NotificationsTags.DETAILS).performClick()
        waitForIdle()
        onNodeWithTag(NotificationsTags.WARNING_CONFIRM).performClick()
        waitForIdle()
        switch(NotificationsTags.DETAILS).assertIsOn()
        assertTrue(device.saved().showDetails)

        // Back to the protective side: no warning.
        switch(NotificationsTags.DETAILS).performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithTag(NotificationsTags.WARNING_CONFIRM).fetchSemanticsNodes().size)
        assertFalse(device.saved().showDetails)

        // The public wake-up needs a category, then a confirmation; without a distributor, the steps are shown.
        switch(NotificationsTags.PUBLIC_WAKE).assertIsNotEnabled()
        switch(NotificationsTags.CATEGORY + NotificationCategory.VOTES.key).performClick()
        waitForIdle()
        switch(NotificationsTags.PUBLIC_WAKE).performClick()
        waitForIdle()
        onNodeWithTag(NotificationsTags.WARNING_CONFIRM).performClick()
        waitForIdle()
        assertTrue(device.saved().publicWake)
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(NotificationsTags.RETRY + "public").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(NotificationsSettingsState.DEFAULT_DISTRIBUTOR_SERVER, substring = true).performScrollTo()

        // The mailbox wake-up waits for the Messages category.
        switch(NotificationsTags.MAILBOX_WAKE).assertIsNotEnabled()
    }

    @Test
    fun theCentreListsMarksReadAndClearsAfterConfirmation() = runComposeUiTest {
        val device = Device(withAccount = true, withPush = false)
        val now = currentEpochSeconds()
        device.notifications.center.add(
            listOf(
                NotificationCandidate("vote:1", NotificationCategory.VOTES, "[EXEMPLE] Nouvelle pétition", createdAt = now - 600),
                NotificationCandidate("team:1", NotificationCategory.TEAM, "[EXEMPLE] Réponse de l’équipe", createdAt = now - 60),
            ),
        )
        show(device)
        waitUntil(timeoutMillis = 10_000) { device.notifications.center.unreadCount() == 2L }
        onNodeWithContentDescription("Notifications, 2 non lues").assertExists()
        onNodeWithTag(ShellTags.NOTIFICATIONS).performClick()
        waitForIdle()

        // Newest first, unread said in words.
        onNodeWithTag(NotificationCenterTags.SCREEN).assertExists()
        val texts = listOf("[EXEMPLE] Réponse de l’équipe", "[EXEMPLE] Nouvelle pétition")
        val tops = texts.map { onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }
        assertTrue(tops[0] < tops[1], "newest first")
        onNodeWithTag(NotificationCenterTags.UNREAD + "vote:1", useUnmergedTree = true).assertExists()

        // Tapping an entry without a known target only marks it read.
        onNodeWithTag(NotificationCenterTags.ENTRY + "vote:1").performScrollTo().performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithTag(NotificationCenterTags.UNREAD + "vote:1", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1L, device.notifications.center.unreadCount())
        onNodeWithTag(NotificationCenterTags.SCREEN).assertExists()

        onNodeWithTag(NotificationCenterTags.MARK_ALL).performScrollTo().performClick()
        waitForIdle()
        assertEquals(0L, device.notifications.center.unreadCount())

        // Clearing asks first; cancelled, nothing is lost.
        onNodeWithTag(NotificationCenterTags.CLEAR).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(NotificationCenterTags.CLEAR_CANCEL).performClick()
        waitForIdle()
        assertEquals(2, device.notifications.center.entries().size)
        onNodeWithTag(NotificationCenterTags.CLEAR).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(NotificationCenterTags.CLEAR_CONFIRM).performClick()
        waitForIdle()
        assertTrue(device.notifications.center.entries().isEmpty())
        onNodeWithTag(NotificationCenterTags.EMPTY).assertExists()

        // Back to the application.
        onNodeWithTag(NotificationCenterTags.BACK).performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithTag(NotificationCenterTags.SCREEN).fetchSemanticsNodes().size)
        onNodeWithTag(ShellTags.BOTTOM_BAR).assertExists()
    }

    @Test
    fun aMessageEntryOpensMessagesAndWideWindowsKeepTheSideBar() = runComposeUiTest {
        val device = Device(withAccount = true, withPush = false)
        device.notifications.center.add(
            listOf(NotificationCandidate("message:1", NotificationCategory.MESSAGES, RoomsFixtures.other.publicKey.hex, "[EXEMPLE] Bonjour", "ppf:messages/abc", currentEpochSeconds() - 30)),
        )
        show(device, width = 1000, height = 740)
        waitUntil(timeoutMillis = 10_000) { device.notifications.center.unreadCount() == 1L }
        onNodeWithTag(ShellTags.NOTIFICATIONS).performClick()
        waitForIdle()
        onNodeWithTag(NotificationCenterTags.SCREEN).assertExists()
        onNodeWithTag(ShellTags.NAV_DRAWER).assertExists()
        // Without the user's permission to show details, the sender and the excerpt stay hidden.
        onNodeWithText("Nouveau message privé").assertExists()
        assertEquals(0, onAllNodesWithText("[EXEMPLE] Bonjour").fetchSemanticsNodes().size)

        onNodeWithTag(NotificationCenterTags.ENTRY + "message:1").performClick()
        waitForIdle()
        assertEquals(0L, device.notifications.center.unreadCount())
        assertEquals(0, onAllNodesWithTag(NotificationCenterTags.SCREEN).fetchSemanticsNodes().size)
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).assertIsSelected()
    }

    @Test
    fun theBellIsDisabledWithoutNotifications() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(360.dp, 740.dp)) { PpfApp(AppDependencies.inMemory()) } }
        waitForIdle()
        onNodeWithTag(ShellTags.NOTIFICATIONS).onChild().assertIsNotEnabled()
    }
}
