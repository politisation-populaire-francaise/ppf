package org.politisationpopulairefrancaise.core.notifications

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.messaging.MessagingService
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.sync.SyncService

/**
 * The notifications of one device, wired once by the platform host (the Android application,
 * the desktop window): settings, centre, the sources of every space, and the round run on each
 * wake-up (synchronization, then [NotificationEngine.check]). Spaces add their own sources through
 * [extraSources] without touching this file.
 */
class Notifications(
    val database: PpfDatabase,
    private val identityStore: IdentityStore,
    private val remote: RemoteEvents?,
    notifier: SystemNotifier?,
    extraSources: List<Pair<NotificationCategory, NotificationSource>> = emptyList(),
) {
    val settings = NotificationSettingsStore(database)
    val center = NotificationCenterStore(database)
    private val round = Mutex()
    private var accountKey: String? = null

    val engine = NotificationEngine(
        settings = settings::load,
        center = center,
        sources = listOf(NotificationCategory.MESSAGES to MessagesNotificationSource(database, me = { accountKey })) + extraSources,
        notifier = notifier,
    )

    /**
     * One round: fetches what is new (public contents, then the user's private messages when an
     * account exists and messages are enabled) and notifies. Rounds never overlap; a wake-up that
     * arrives during a round is simply absorbed by it.
     */
    suspend fun onWake() {
        if (round.isLocked) return
        round.withLock {
            val current = settings.load()
            val keys = identityStore.load()?.keys()
            accountKey = keys?.publicKey?.hex
            val remote = remote
            if (remote != null && current.enabled.isNotEmpty()) {
                runCatching { SyncService(LocalEventStore(database), remote).pull(kinds = listOf(Article.KIND, EventKinds.TEXT_NOTE)) }
                if (keys != null && current.isEnabled(NotificationCategory.MESSAGES)) {
                    runCatching { MessagingService(LocalMessagingSigner(keys), MessageStore(database), remote).pull() }
                }
            }
            engine.check()
        }
    }

    /** Forgets everything when the account is erased: centre, read marks, and settings back to the most protective. */
    fun forget() {
        center.clear()
        settings.reset()
    }
}
