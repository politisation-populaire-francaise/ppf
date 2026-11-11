package org.politisationpopulairefrancaise.core.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.device.DeviceErasable
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.notifications.NotificationCenterStore
import org.politisationpopulairefrancaise.core.notifications.NotificationSettingsStore

/**
 * What the pole Communauté keeps on the device, for the quick erase (S-44, contract
 * docs/integration/agir-securite-appareil.md, section 2 bis): three parts, each erased for good,
 * without any network call, idempotent, and awaited to the end.
 *
 * - [messaging]: the private messages, their conversations and deliveries waiting, and the contacts
 *   checked in person (the security numbers).
 * - [groups]: the rooms joined on this device (the server never had that list, D-028).
 * - [notifications]: the notification centre, the settings (back to the most protective) and the
 *   push registrations of the device. The server forgets a registration it no longer hears from
 *   within 30 days (it holds an address of our ntfy server and, for the mailbox, the account key).
 *
 * The compromise notices of the contacts are public events of the local copy of the contents: they
 * go with the part of the shared base (`core-contents`). Circles and statuses will join here (M28).
 */
class CommunauteDeviceData(private val database: PpfDatabase) {

    val messaging: DeviceErasable = deviceErasable(MESSAGING) {
        withContext(Dispatchers.Default) {
            MessageStore(database).clear()
            ContactVerificationStore(database).clear()
        }
    }

    val groups: DeviceErasable = deviceErasable(GROUPS) {
        withContext(Dispatchers.Default) { GroupStore(database).clear() }
    }

    val notifications: DeviceErasable = deviceErasable(NOTIFICATIONS) {
        withContext(Dispatchers.Default) {
            NotificationCenterStore(database).clear()
            NotificationSettingsStore(database).reset()
            database.notificationsQueries.deleteAllRegistrations()
        }
    }

    /** The three parts, in the order the quick erase goes through them. */
    val parts: List<DeviceErasable> get() = listOf(messaging, groups, notifications)

    companion object {
        const val MESSAGING: String = "communaute-messaging"
        const val GROUPS: String = "communaute-groups"
        const val NOTIFICATIONS: String = "communaute-notifications"
    }
}
