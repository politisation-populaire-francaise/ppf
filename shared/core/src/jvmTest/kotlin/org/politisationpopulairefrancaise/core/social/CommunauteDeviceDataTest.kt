package org.politisationpopulairefrancaise.core.social

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.device.DeviceEraseRegistry
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.notifications.NotificationCandidate
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.NotificationCenterStore
import org.politisationpopulairefrancaise.core.notifications.NotificationSettings
import org.politisationpopulairefrancaise.core.notifications.NotificationSettingsStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The quick erase of what Communauté keeps on the device (S-44, agir-securite-appareil.md, section 2 bis). */
class CommunauteDeviceDataTest {

    private val database = JvmDatabase.inMemory()

    @Test
    fun theQuickEraseLeavesNothingOfCommunauteAndCanRunAgain() = runTest {
        val me = IdentityKeys.generate().publicKey
        val other = IdentityKeys.generate().publicKey
        val now = currentEpochSeconds()
        val messages = MessageStore(database)
        messages.store(ChatMessage.compose(other, listOf(me), "[EXEMPLE] Rendez-vous demain", createdAt = now - 60))
        ContactVerificationStore(database).markVerified(other.hex)
        GroupStore(database).join("collectif-demonstration")
        NotificationCenterStore(database).add(listOf(NotificationCandidate("v1", NotificationCategory.VOTES, "[EXEMPLE] Vote", createdAt = now)))
        NotificationSettingsStore(database).save(NotificationSettings(enabled = setOf(NotificationCategory.MESSAGES), mailboxWake = true))
        database.notificationsQueries.setRegistration("mailbox", "id", "https://push.example.test/up1", now)

        val data = CommunauteDeviceData(database)
        val registry = DeviceEraseRegistry()
        data.parts.forEach { registry.register(it) }
        assertEquals(listOf("communaute-messaging", "communaute-groups", "communaute-notifications"), registry.ids)

        val report = registry.eraseAll()
        assertTrue(report.complete, "every part erased: $report")
        assertEquals(0, messages.count())
        assertTrue(messages.conversations().isEmpty())
        assertTrue(ContactVerificationStore(database).all().isEmpty())
        assertEquals(0, GroupStore(database).count())
        assertTrue(NotificationCenterStore(database).entries().isEmpty())
        assertEquals(NotificationSettings.MOST_PROTECTIVE, NotificationSettingsStore(database).load())
        assertEquals(null, database.notificationsQueries.getRegistration("mailbox").executeAsOneOrNull())

        // Idempotent: a retry after a failure elsewhere erases nothing more and does not fail.
        assertTrue(registry.eraseAll().complete)
    }
}
