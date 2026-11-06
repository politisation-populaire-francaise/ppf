package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.groups.MailboxRetention
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.groups.RoomsFixtures
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The retention of private messages on the server, set in "Mon espace" (D-028): three months by
 * default, a signed NIP-78 event for six months or a year, confirmed first because the envelopes
 * then stay longer; kept on the device without network; the previous choice back when refused.
 */
@OptIn(ExperimentalTestApi::class)
class MailboxSettingTest {

    /** A server that can be switched off or made to refuse, and records what it received. */
    private class Server : RemoteEvents {
        val port = InMemoryPublicationPort()
        val received = mutableListOf<SignedEvent>()
        var online = true
        var refuse = false

        override suspend fun publish(event: SignedEvent): PublicationResult = when {
            !online -> PublicationResult.Unavailable()
            refuse -> PublicationResult.Rejected(event.id, RejectionReason.REFUSED)
            else -> {
                received += event
                port.publish(event)
            }
        }

        override suspend fun fetch(filter: RemoteFilter): FetchResult = if (online) port.fetch(filter) else FetchResult.Unavailable()
    }

    private fun SignedEvent.days(): String? = tags.firstOrNull { it.firstOrNull() == MailboxRetention.TAG_RETENTION }?.getOrNull(1)

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(20)
    }

    private fun ComposeUiTest.openMySpace(server: RemoteEvents?) {
        val dependencies = RoomsFixtures.withAccount(server)
        setContent { Box(Modifier.requiredSize(360.dp, 740.dp)) { PpfApp(dependencies) } }
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(MailboxTags.CARD).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(MailboxTags.CARD).performScrollTo()
    }

    @Test
    fun threeMonthsByDefaultAndALongerChoiceIsConfirmedFirst() = runComposeUiTest {
        val server = Server()
        openMySpace(server)
        onNodeWithTag(MailboxTags.OPTION + 90).assertIsSelected()
        onNodeWithText("Vous n’avez rien choisi : le serveur garde vos enveloppes 3 mois.").assertExists()

        // Cancelling the warning changes nothing.
        onNodeWithTag(MailboxTags.OPTION + 365).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(MailboxTags.CANCEL).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        assertTrue(server.received.isEmpty())
        onNodeWithTag(MailboxTags.OPTION + 90).assertIsSelected()

        // Confirmed: a signed setting of 180 days reaches the server.
        onNodeWithTag(MailboxTags.OPTION + 180).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(MailboxTags.CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { server.received.isNotEmpty() }
        val setting = server.received.single()
        assertEquals(MailboxRetention.KIND, setting.kind)
        assertEquals(RoomsFixtures.ownKey, setting.pubkey)
        assertEquals(MailboxRetention.IDENTIFIER, setting.firstTagValue("d"))
        assertEquals("180", setting.days())
        assertTrue(setting.verify())
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(MailboxTags.STATUS).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Réglage enregistré sur le serveur.").assertExists()
        onNodeWithTag(MailboxTags.OPTION + 180).assertIsSelected()
        onNodeWithTag(MailboxTags.OPTION + 90).assertIsNotSelected()

        // Back to the most protective duration: no warning.
        onNodeWithTag(MailboxTags.OPTION + 90).performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { server.received.size == 2 }
        assertEquals(0, onAllNodesWithTag(MailboxTags.CONFIRM).fetchSemanticsNodes().size)
        assertEquals("90", server.received.last().days())
        assertTrue(server.received.last().createdAt > setting.createdAt, "the new setting replaces the old one")
    }

    @Test
    fun withoutNetworkTheChoiceWaitsOnTheDeviceAndLeavesLater() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val server = Server().apply { online = false }
        val keys = IdentityKeys.generate()
        val signer = LocalEventSigner(keys)
        val setting = MailboxSetting(store, server, this)
        setting.showAccount(keys.publicKey.hex)
        setting.choose(signer, MailboxRetention.ONE_YEAR)
        assertEquals(MailboxStatus.PENDING, setting.status)
        assertEquals(MailboxRetention.ONE_YEAR, setting.retention)
        assertEquals(1, store.pending().size)

        // Next start, network back: the waiting setting leaves first.
        server.online = true
        val later = MailboxSetting(store, server, this)
        later.showAccount(keys.publicKey.hex)
        awaitUntil { later.status == MailboxStatus.SAVED }
        assertEquals(MailboxRetention.ONE_YEAR, later.retention)
        assertEquals("365", server.received.single().days())
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun aRefusedChoiceKeepsThePreviousOne() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val server = Server()
        val keys = IdentityKeys.generate()
        val signer = LocalEventSigner(keys)
        val setting = MailboxSetting(store, server, this)
        setting.showAccount(keys.publicKey.hex)
        delay(300)
        setting.choose(signer, MailboxRetention.SIX_MONTHS)
        assertEquals(MailboxStatus.SAVED, setting.status)

        server.refuse = true
        setting.choose(signer, MailboxRetention.ONE_YEAR)
        assertEquals(MailboxStatus.REJECTED, setting.status)
        assertEquals(MailboxRetention.SIX_MONTHS, setting.retention)
        assertTrue(store.pending().isEmpty())
        val kept = store.byTag(listOf(MailboxRetention.KIND), "d", MailboxRetention.IDENTIFIER).single()
        assertEquals("180", kept.days())
    }

    @Test
    fun aSettingMadeOnAnotherDeviceIsShownAndAnotherAccountSeesItsOwn() = runBlocking {
        val server = Server()
        val keys = IdentityKeys.generate()
        val signer = LocalEventSigner(keys)
        // Chosen on another device.
        server.port.publish(signer.sign(MailboxRetention.build(keys.publicKey, MailboxRetention.ONE_YEAR, createdAt = 1_000)))

        val store = LocalEventStore(JvmDatabase.inMemory())
        val setting = MailboxSetting(store, server, this)
        setting.showAccount(keys.publicKey.hex)
        awaitUntil { setting.status == MailboxStatus.SAVED }
        assertEquals(MailboxRetention.ONE_YEAR, setting.retention)

        // Another account on the same device starts from the default.
        setting.showAccount(IdentityKeys.generate().publicKey.hex)
        delay(200)
        assertEquals(MailboxStatus.DEFAULT, setting.status)
        assertEquals(MailboxRetention.THREE_MONTHS, setting.retention)
        setting.showAccount(null)
        assertEquals(MailboxRetention.THREE_MONTHS, setting.retention)
    }
}
