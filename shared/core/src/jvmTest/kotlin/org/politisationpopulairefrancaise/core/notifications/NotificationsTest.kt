package org.politisationpopulairefrancaise.core.notifications

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationsTest {

    private val database = JvmDatabase.inMemory()
    private var now = 1_800_000_000L

    @Test
    fun theDefaultsAreTheMostProtective() {
        val store = NotificationSettingsStore(database)
        val settings = store.load()
        assertEquals(NotificationSettings.MOST_PROTECTIVE, settings)
        assertTrue(settings.enabled.isEmpty())
        assertFalse(settings.publicWake)
        assertFalse(settings.mailboxWake)
        assertFalse(settings.showDetails)
    }

    @Test
    fun settingsAreKeptAndReset() {
        val store = NotificationSettingsStore(database)
        val chosen = NotificationSettings(
            enabled = setOf(NotificationCategory.VOTES, NotificationCategory.MESSAGES),
            publicWake = true,
            totalSilence = true,
        )
        store.save(chosen)
        assertEquals(chosen, store.load())
        store.reset()
        assertEquals(NotificationSettings.MOST_PROTECTIVE, store.load())
    }

    @Test
    fun relaxingAskAWarningButTighteningDoesNot() {
        val base = NotificationSettings()
        assertTrue(base.relaxes(base.copy(showDetails = true)))
        assertTrue(base.relaxes(base.copy(mailboxWake = true)))
        assertTrue(base.relaxes(base.copy(publicWake = true)))
        assertFalse(base.relaxes(base.copy(enabled = setOf(NotificationCategory.TEAM))))
        assertFalse(base.copy(showDetails = true).relaxes(base))
    }

    @Test
    fun theCentreAddsOnceAndForgetsOldEntries() {
        val center = NotificationCenterStore(database, clock = { now }, retentionSeconds = 100, maxEntries = 3)
        val first = candidate("a", createdAt = now - 10)
        assertEquals(listOf(first), center.add(listOf(first)))
        assertEquals(emptyList(), center.add(listOf(first)))
        assertEquals(1, center.unreadCount())

        center.add((1..4).map { candidate("b$it", createdAt = now - it) })
        assertEquals(listOf("b1", "b2", "b3"), center.entries().map { it.id })

        center.add(listOf(candidate("old", createdAt = now - 1000)))
        assertFalse(center.entries().any { it.id == "old" })

        center.markRead("b1")
        assertEquals(2, center.unreadCount())
        center.markAllRead()
        assertEquals(0, center.unreadCount())
        center.clear()
        assertTrue(center.entries().isEmpty())
    }

    @Test
    fun theEngineReadsOnlyEnabledCategoriesAndHidesDetailsByDefault() = runTest {
        val settings = NotificationSettingsStore(database)
        val center = NotificationCenterStore(database, clock = { now })
        val posted = mutableListOf<SystemNotification>()
        val votes = FakeSource("votes", NotificationCategory.VOTES)
        val engine = NotificationEngine(
            settings = settings::load,
            center = center,
            sources = listOf(NotificationCategory.VOTES to votes),
            notifier = object : SystemNotifier {
                override fun post(notification: SystemNotification) {
                    posted += notification
                }
            },
            clock = { now },
        )
        votes.items += candidate("v1", NotificationCategory.VOTES, createdAt = now - 5)

        // Nothing enabled: the source is not even read.
        assertEquals(0, engine.check().added)
        assertEquals(0, votes.reads)

        settings.save(NotificationSettings(enabled = setOf(NotificationCategory.VOTES)))
        val report = engine.check()
        assertEquals(1, report.added)
        assertEquals(listOf(SystemNotification(NotificationCategory.VOTES, count = 1, target = "ppf:decider/v1")), posted)

        // Seen once: never notified again.
        posted.clear()
        now += 60
        assertEquals(0, engine.check().added)
        assertTrue(posted.isEmpty())

        // Details only when the user allowed them; several novelties are grouped.
        settings.save(NotificationSettings(enabled = setOf(NotificationCategory.VOTES), showDetails = true, totalSilence = true))
        votes.items += candidate("v2", NotificationCategory.VOTES, createdAt = now - 1)
        engine.check()
        assertEquals("Titre v2", posted.single().title)
        assertTrue(posted.single().silent)
        votes.items += candidate("v3", NotificationCategory.VOTES, createdAt = now + 1)
        votes.items += candidate("v4", NotificationCategory.VOTES, createdAt = now + 2)
        posted.clear()
        now += 60
        engine.check()
        assertEquals(2, posted.single().count)
        assertNull(posted.single().title)
    }

    @Test
    fun aFirstReadingDoesNotFloodWithOldNovelties() = runTest {
        val settings = NotificationSettingsStore(database).apply { save(NotificationSettings(enabled = setOf(NotificationCategory.VOTES))) }
        val source = FakeSource("votes", NotificationCategory.VOTES)
        source.items += candidate("ancient", NotificationCategory.VOTES, createdAt = now - 10 * 24 * 3600)
        source.items += candidate("recent", NotificationCategory.VOTES, createdAt = now - 3600)
        val engine = NotificationEngine(settings::load, NotificationCenterStore(database, clock = { now }), listOf(NotificationCategory.VOTES to source), null, clock = { now })
        assertEquals(1, engine.check().added)
        assertEquals(listOf("recent"), NotificationCenterStore(database, clock = { now }).entries().map { it.id })
    }

    @Test
    fun unreadMessagesOfOthersAreNotified() = runTest {
        val me = IdentityKeys.generate().publicKey
        val other = IdentityKeys.generate().publicKey
        val messages = MessageStore(database, clock = { now })
        messages.store(ChatMessage.compose(other, listOf(me), "[EXEMPLE] Rendez-vous demain", createdAt = 100))
        messages.store(ChatMessage.compose(me, listOf(other), "[EXEMPLE] D'accord", createdAt = 101), read = true)
        val source = MessagesNotificationSource(database, me = { me.hex }, excerptLength = 10)

        val found = source.collect(now - 1)
        assertEquals(1, found.size)
        assertEquals(other.hex, found.single().title)
        assertEquals("[EXEMPLE] ", found.single().detail)
        assertEquals(now, found.single().createdAt)
        assertTrue(found.single().target!!.startsWith("ppf:messages/"))
        assertTrue(source.collect(now).isEmpty())
        assertTrue(MessagesNotificationSource(database, me = { null }).collect(null).isEmpty())
    }

    @Test
    fun theHttpAuthorizationIsASignedNip98Event() = runTest {
        val keys = IdentityKeys.generate()
        val header = PushClient.httpAuthorization(LocalEventSigner(keys), "https://example.test/api/v1/push/subscriptions", "POST", "{}".encodeToByteArray())
        assertTrue(header.startsWith("Nostr "))
        val event = SignedEvent.fromJson(Base64.decode(header.removePrefix("Nostr ")).decodeToString())
        assertTrue(event.verify())
        assertEquals(PushClient.HTTP_AUTH_KIND, event.kind)
        assertEquals(keys.publicKey.hex, event.pubkey)
        assertEquals("https://example.test/api/v1/push/subscriptions", event.firstTagValue("u"))
        assertEquals("POST", event.firstTagValue("method"))
        assertEquals("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a", event.firstTagValue("payload"))
    }

    @Test
    fun thePushManagerRegistersOnlyWhatIsEnabledAndCleansUp() = runTest {
        val api = FakePushApi()
        val registrar = FakeRegistrar()
        val keys = IdentityKeys.generate()
        var wakes = 0
        val manager = PushManager(database, api, registrar, signer = { LocalEventSigner(keys) }, onWake = { wakes++ }, clock = { now })

        manager.apply(NotificationSettings())
        assertTrue(registrar.registered.isEmpty())
        assertEquals(PushState.OFF, manager.state(PushScope.PUBLIC))

        manager.apply(NotificationSettings(publicWake = true))
        assertEquals(listOf(PushScope.PUBLIC), registrar.registered)
        assertEquals(PushState.WAITING_FOR_ADDRESS, manager.state(PushScope.PUBLIC))

        manager.onNewEndpoint(PushScope.PUBLIC, PushEndpoint("https://push.example.test/upAAA", "p", "a"))
        assertEquals(PushState.ACTIVE, manager.state(PushScope.PUBLIC))
        assertEquals(listOf(PushScope.PUBLIC to false), api.subscriptions)

        manager.onMessage()
        assertEquals(1, wakes)

        manager.apply(NotificationSettings(publicWake = false, mailboxWake = true))
        assertEquals(listOf("sub-1"), api.unsubscribed)
        assertEquals(PushState.OFF, manager.state(PushScope.PUBLIC))
        manager.onNewEndpoint(PushScope.MAILBOX, PushEndpoint("https://push.example.test/upBBB", "p", "a"))
        // The mailbox registration is signed, the public one never is.
        assertEquals(PushScope.MAILBOX to true, api.subscriptions.last())

        manager.clear()
        assertEquals(listOf("sub-1", "sub-2"), api.unsubscribed)
        assertEquals(PushState.OFF, manager.state(PushScope.MAILBOX))
    }

    @Test
    fun withoutDistributorTheUserIsTold() = runTest {
        val registrar = FakeRegistrar(available = false)
        val manager = PushManager(database, FakePushApi(), registrar, signer = { null }, onWake = {}, clock = { now })
        manager.apply(NotificationSettings(publicWake = true, mailboxWake = true))
        assertEquals(PushState.NO_DISTRIBUTOR, manager.state(PushScope.PUBLIC))
        // Without an account there is no mailbox to wake.
        assertEquals(PushState.OFF, manager.state(PushScope.MAILBOX))
        assertTrue(registrar.registered.isEmpty())
    }

    private fun candidate(id: String, category: NotificationCategory = NotificationCategory.FOLLOWED, createdAt: Long) =
        NotificationCandidate(id, category, "Titre $id", "Détail $id", "ppf:decider/$id", createdAt)

    private class FakeSource(override val name: String, val category: NotificationCategory) : NotificationSource {
        val items = mutableListOf<NotificationCandidate>()
        var reads = 0

        override suspend fun collect(after: Long?): List<NotificationCandidate> {
            reads++
            return items.filter { after == null || it.createdAt > after }
        }
    }

    private class FakeRegistrar(private val available: Boolean = true) : PushRegistrar {
        val registered = mutableListOf<PushScope>()

        override fun isAvailable(): Boolean = available

        override fun register(scope: PushScope, vapidPublicKey: String) {
            registered += scope
        }

        override fun unregister(scope: PushScope) {
            registered -= scope
        }
    }

    private class FakePushApi : PushApi {
        val subscriptions = mutableListOf<Pair<PushScope, Boolean>>()
        val unsubscribed = mutableListOf<String>()

        override suspend fun vapidPublicKey(): PushResult<String> = PushResult.Done("vapid")

        override suspend fun subscribe(scope: PushScope, endpoint: PushEndpoint, signer: org.politisationpopulairefrancaise.core.signing.EventSigner?): PushResult<String> {
            subscriptions += scope to (signer != null)
            return PushResult.Done("sub-${subscriptions.size}")
        }

        override suspend fun unsubscribe(subscriptionId: String): PushResult<Unit> {
            unsubscribed += subscriptionId
            return PushResult.Done(Unit)
        }
    }
}
