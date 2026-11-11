package org.politisationpopulairefrancaise.ui

import org.politisationpopulairefrancaise.ui.endirect.LiveBoardStore
import org.politisationpopulairefrancaise.ui.onboarding.TutorialStore
import org.politisationpopulairefrancaise.core.agir.security.DeviceSecurityStorage
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.device.DeviceErasable
import org.politisationpopulairefrancaise.core.device.DeviceEraseRegistry
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.notifications.Notifications
import org.politisationpopulairefrancaise.core.notifications.PushManager
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.core.urne.UrneRemote
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataKeys
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataTrust

/**
 * What each platform host (Android activity, desktop window, iOS view controller) provides to
 * the shared interface. Keeping it explicit avoids global state and makes tests trivial.
 */
class AppDependencies(
    val identityStore: IdentityStore,
    /** The offline copy of the contents; null means no local database (contents are then unavailable). */
    val contentStore: LocalEventStore? = null,
    /** Which authors' editorial contents are shown as PPF contents. */
    val editorialTrust: EditorialTrust = EditorialTrust.bundledExamples(),
    /** The server, when one is configured; null keeps the application fully offline. */
    val remote: RemoteEvents? = null,
    /** Which import keys sign the parliamentary open data this build trusts (D-064). */
    val openDataTrust: OpenDataTrust = OpenDataKeys.testBuilds(),
    /**
     * The accounts the user follows, kept on the device (never sent to the server as a list).
     * Null keeps the list in memory for the life of the process only (tests, previews).
     */
    val followStore: FollowStore? = null,
    /**
     * The local database itself, for the stores that other modules build on it (messages, rooms);
     * null means no local database.
     */
    val database: PpfDatabase? = null,
    /** The contents of "Voir" the user follows, on the device only; null keeps them in memory (tests, previews). */
    val contentFollowStore: ContentFollowStore? = null,
    /**
     * True while the device saves data (the system's data saver, a metered network): a large
     * download then asks a second confirmation (cahier des charges, mode économie de données). The
     * hosts do not report it yet, so it answers false by default.
     */
    val dataSaver: () -> Boolean = { false },
    /**
     * The notifications of the device (settings, centre, wake-up round, D-130); null without a
     * local database (tests, previews).
     */
    val notifications: Notifications? = null,
    /** The push registrations; null without a server or a push system (desktop, iOS for now). */
    val push: PushManager? = null,
    /**
     * The address of our own ntfy server, to type in the distributor application (production or
     * preproduction, given by the host); null shows the production address.
     */
    val pushServer: String? = null,
    /**
     * Asks the system permission to show notifications (Android 13 and later) and tells whether it
     * was granted; elsewhere there is nothing to ask.
     */
    val requestNotificationPermission: ((granted: Boolean) -> Unit) -> Unit = { it(true) },
    /**
     * The ballot-box API of the server (D-110), with its two HTTP clients: the one of the account and
     * a separate anonymous one for the ballots. Null keeps voting unavailable (no server, tests).
     */
    val urneRemote: UrneRemote? = null,
    /** Where the lock and the other security settings of the device are kept (S-43, S-45); null keeps them in memory (tests, previews). */
    val securityStorage: DeviceSecurityStorage? = null,
    /**
     * What the quick erase (S-44) goes through: each pole registers here what it keeps on the
     * device ([DeviceErasable]); the shell adds the identity, the rally point and the settings.
     */
    val erasables: DeviceEraseRegistry = DeviceEraseRegistry(),
    /** Closes the application after the quick erase; null where it cannot (tests, previews). */
    val closeApplication: (() -> Unit)? = null,
    /** Remembers that the tutorial of the first launch was seen (D-216); null: no tutorial (tests, previews). */
    val tutorial: TutorialStore? = null,
    /** Keeps how the person composed « En direct » (views, blocks), on the device only; null: in memory (tests, previews). */
    val liveBoard: LiveBoardStore? = null,
) {
    companion object {
        /** For tests and previews: nothing survives the process, no contents, no network. */
        fun inMemory(): AppDependencies = AppDependencies(identityStore = InMemoryIdentityStore())

        /**
         * The usual wiring of a platform host: every store of the device (contents, followed accounts)
         * is built on the same local [database], opened once by the platform.
         */
        fun withDatabase(
            identityStore: IdentityStore,
            database: PpfDatabase,
            editorialTrust: EditorialTrust = EditorialTrust.bundledExamples(),
            remote: RemoteEvents? = null,
            notifications: Notifications? = null,
            push: PushManager? = null,
            pushServer: String? = null,
            requestNotificationPermission: ((granted: Boolean) -> Unit) -> Unit = { it(true) },
            urneRemote: UrneRemote? = null,
            openDataTrust: OpenDataTrust = OpenDataKeys.testBuilds(),
            securityStorage: DeviceSecurityStorage? = null,
            closeApplication: (() -> Unit)? = null,
            tutorial: TutorialStore? = null,
            liveBoard: LiveBoardStore? = null,
        ): AppDependencies = AppDependencies(
            identityStore = identityStore,
            openDataTrust = openDataTrust,
            contentStore = LocalEventStore(database),
            editorialTrust = editorialTrust,
            remote = remote,
            followStore = FollowStore(database),
            database = database,
            contentFollowStore = ContentFollowStore(database),
            // The personal reminders of the device (DE-28, S-98) reach the centre with the other novelties.
            notifications = notifications ?: Notifications(database, identityStore, remote, notifier = null, extraSources = ReminderStore.notificationSources(database)),
            push = push,
            pushServer = pushServer,
            requestNotificationPermission = requestNotificationPermission,
            urneRemote = urneRemote,
            securityStorage = securityStorage,
            closeApplication = closeApplication,
            tutorial = tutorial,
            liveBoard = liveBoard,
        )
    }
}
