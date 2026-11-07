package org.politisationpopulairefrancaise.server.push

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.relay.RelayKinds
import java.net.URI
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WakeDispatcherTest {

    private var now: Instant = Instant.parse("2026-11-07T12:00:00Z")
    private val clock: () -> Instant = { now }
    private val woken = CopyOnWriteArrayList<String>()
    private var answer: (PushSubscription) -> WakeResult = { WakeResult.Delivered }
    private val sender = WakeSender { subscription -> woken += subscription.id; answer(subscription) }
    private val subscriptions = PushSubscriptions(TestDatabase.dataSource)
    private val recipient = IdentityKeys.generate().publicKey.hex
    private val oneTimeKey = LocalEventSigner(IdentityKeys.generate())
    private val author = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    private fun subscribe(scope: PushScope, pubkey: String? = null, at: Instant = now): String {
        val receiver = P256.generateKeyPair()
        return subscriptions.save(
            scope,
            "https://push.example.org/up${System.nanoTime()}",
            Base64Url.encode(P256.encodePoint(receiver.public as ECPublicKey)),
            Base64Url.encode(ByteArray(16)),
            pubkey,
            at,
        ).id
    }

    private suspend fun giftWrap(to: String): SignedEvent =
        oneTimeKey.sign(kind = RelayKinds.GIFT_WRAP, content = "[EXEMPLE] enveloppe chiffrée", tags = listOf(listOf("p", to)))

    private suspend fun note(): SignedEvent = author.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] contenu public")

    @Test
    fun mailboxWakeUpsAreDelayedAndCoalescedToOnePerMinute() = runBlocking {
        val dispatcher = WakeDispatcher(subscriptions, sender, clock, jitter = { Duration.ofSeconds(30) })
        val mine = subscribe(PushScope.MAILBOX, recipient)
        subscribe(PushScope.MAILBOX, IdentityKeys.generate().publicKey.hex)
        subscribe(PushScope.PUBLIC)

        dispatcher.handle(giftWrap(recipient))
        dispatcher.handle(giftWrap(recipient))
        assertEquals(1, dispatcher.pendingMailboxCount)
        // The random delay: nothing before it.
        assertEquals(0, dispatcher.sendDueMailbox())
        advance(Duration.ofSeconds(29))
        assertEquals(0, dispatcher.sendDueMailbox())
        advance(Duration.ofSeconds(1))
        assertEquals(1, dispatcher.sendDueMailbox())
        assertEquals(listOf(mine), woken)
        assertEquals(now, subscriptions.find(mine)!!.lastWakeAt)

        // Ten seconds later another message: not before one minute after the last wake-up.
        advance(Duration.ofSeconds(10))
        dispatcher.handle(giftWrap(recipient))
        advance(Duration.ofSeconds(49))
        assertEquals(0, dispatcher.sendDueMailbox())
        advance(Duration.ofSeconds(1))
        assertEquals(1, dispatcher.sendDueMailbox())
        assertEquals(listOf(mine, mine), woken)

        // Public content never wakes a mailbox subscription.
        dispatcher.handle(note())
        advance(Duration.ofMinutes(5))
        assertEquals(0, dispatcher.sendDueMailbox())
    }

    @Test
    fun publicWakeUpsAreCoalescedToOneEveryThirtyMinutes() = runBlocking {
        val dispatcher = WakeDispatcher(subscriptions, sender, clock)
        val first = subscribe(PushScope.PUBLIC, at = now.minusSeconds(60))
        val mailbox = subscribe(PushScope.MAILBOX, recipient, at = now.minusSeconds(60))

        // Nothing published yet: nobody to wake.
        assertEquals(0, dispatcher.sendDuePublic())
        advance(Duration.ofSeconds(1))
        dispatcher.handle(note())
        // Envelopes and application data are not public content.
        dispatcher.handle(author.sign(kind = RelayKinds.APPLICATION_DATA, content = "{}", tags = listOf(listOf("d", "x"))))
        assertEquals(1, dispatcher.sendDuePublic())
        assertEquals(listOf(first), woken)
        assertEquals(0, dispatcher.sendDuePublic())

        // A subscription created after the newest content has nothing to fetch yet.
        advance(Duration.ofSeconds(1))
        val second = subscribe(PushScope.PUBLIC)
        advance(Duration.ofMinutes(5))
        dispatcher.handle(note())
        assertEquals(1, dispatcher.sendDuePublic())
        assertEquals(listOf(first, second), woken)

        advance(Duration.ofMinutes(10))
        dispatcher.handle(note())
        assertEquals(0, dispatcher.sendDuePublic())
        // 30 minutes after its last wake-up, the first one is woken again; the second waits its turn.
        advance(Duration.ofMinutes(15).plusSeconds(1))
        assertEquals(1, dispatcher.sendDuePublic())
        assertEquals(listOf(first, second, first), woken)
        advance(Duration.ofMinutes(5))
        assertEquals(1, dispatcher.sendDuePublic())
        assertEquals(listOf(first, second, first, second), woken)
        assertTrue(mailbox !in woken)
    }

    @Test
    fun goneSubscriptionsAreDeletedAndBlockedHostsRetried() = runBlocking {
        val dispatcher = WakeDispatcher(subscriptions, sender, clock, jitter = { Duration.ZERO })
        val gone = subscribe(PushScope.MAILBOX, recipient)
        answer = { WakeResult.Gone }
        dispatcher.handle(giftWrap(recipient))
        assertEquals(1, dispatcher.sendDueMailbox())
        assertNull(subscriptions.find(gone))

        val publicGone = subscribe(PushScope.PUBLIC, at = now.minusSeconds(10))
        dispatcher.handle(note())
        assertEquals(1, dispatcher.sendDuePublic())
        assertNull(subscriptions.find(publicGone))

        val blocked = subscribe(PushScope.MAILBOX, recipient)
        answer = { WakeResult.RetryAt(now.plusSeconds(120)) }
        dispatcher.handle(giftWrap(recipient))
        assertEquals(1, dispatcher.sendDueMailbox())
        assertEquals(1, dispatcher.pendingMailboxCount)
        answer = { WakeResult.Delivered }
        advance(Duration.ofSeconds(119))
        assertEquals(0, dispatcher.sendDueMailbox())
        advance(Duration.ofSeconds(1))
        assertEquals(1, dispatcher.sendDueMailbox())
        assertEquals(now, subscriptions.find(blocked)!!.lastWakeAt)
    }

    @Test
    fun cleanupDeletesSubscriptionsNotRenewedForThirtyDays() {
        val old = subscribe(PushScope.PUBLIC, at = now.minus(Duration.ofDays(31)))
        val recent = subscribe(PushScope.PUBLIC, at = now.minus(Duration.ofDays(29)))
        assertEquals(1, WakeDispatcher(subscriptions, sender, clock).cleanup())
        assertNull(subscriptions.find(old))
        assertEquals(recent, subscriptions.find(recent)!!.id)
    }

    @Test
    fun senderEncryptsAuthorizesAndGoesThroughTheInternalAddress() = runBlocking {
        val vapid = VapidKeys(P256.generateKeyPair().private as ECPrivateKey)
        val config = PushConfig(vapid, setOf("push.example.org"), URI("http://ntfy:80"), "https://site.example.org")
        val requests = CopyOnWriteArrayList<Triple<URI, Map<String, String>, ByteArray>>()
        var status = 201
        var retryAfter: String? = null
        val transport = PushTransport { url, headers, body -> requests += Triple(url, headers, body); PushTransport.Response(status, retryAfter) }
        val webPush = WebPushSender(config, transport, clock)
        val receiver = P256.generateKeyPair()
        val receiverPublic = P256.encodePoint(receiver.public as ECPublicKey)
        val auth = ByteArray(16) { 3 }
        val subscription = PushSubscription(
            id = "a".repeat(22), scope = PushScope.MAILBOX, endpoint = "https://push.example.org/upAbc?up=1",
            p256dh = Base64Url.encode(receiverPublic), auth = Base64Url.encode(auth), pubkey = recipient,
            createdAt = now, renewedAt = now, lastWakeAt = null,
        )

        assertEquals(WakeResult.Delivered, webPush.wake(subscription))
        val (url, headers, body) = requests.single()
        assertEquals(URI("http://ntfy:80/upAbc?up=1"), url)
        assertEquals("3600", headers["TTL"])
        assertEquals("aes128gcm", headers["Content-Encoding"])
        assertEquals("high", headers["Urgency"])
        assertTrue(headers["Authorization"]!!.startsWith("vapid t="))
        assertTrue(headers["Authorization"]!!.endsWith(", k=${vapid.publicKeyBase64Url}"))
        assertContentEquals(WebPushSender.PAYLOAD, decryptWebPush(body, receiver.private as ECPrivateKey, receiverPublic, auth))

        assertEquals("normal", run { webPush.wake(subscription.copy(scope = PushScope.PUBLIC, pubkey = null)); requests.last().second["Urgency"] })

        status = 410
        assertEquals(WakeResult.Gone, webPush.wake(subscription))
        status = 404
        assertEquals(WakeResult.Gone, webPush.wake(subscription))

        status = 429
        retryAfter = "120"
        assertEquals(WakeResult.RetryAt(now.plusSeconds(120)), webPush.wake(subscription))
        // The host is blocked: nothing is sent until Retry-After.
        val sent = requests.size
        status = 201
        assertIs<WakeResult.RetryAt>(webPush.wake(subscription))
        assertEquals(sent, requests.size)
        advance(Duration.ofSeconds(120))
        assertEquals(WakeResult.Delivered, webPush.wake(subscription))

        // A host no longer allowed is never contacted.
        assertEquals(WakeResult.Failed, webPush.wake(subscription.copy(endpoint = "https://push.elsewhere.example/up1")))
    }

    @Test
    fun goneAnswerOfThePushServiceDeletesTheSubscription() = runBlocking {
        val config = PushConfig(VapidKeys(P256.generateKeyPair().private as ECPrivateKey), setOf("push.example.org"))
        val webPush = WebPushSender(config, { _, _, _ -> PushTransport.Response(410, null) }, clock)
        val dispatcher = WakeDispatcher(subscriptions, webPush, clock, jitter = { Duration.ZERO })
        val id = subscribe(PushScope.MAILBOX, recipient)
        dispatcher.handle(giftWrap(recipient))
        assertEquals(1, dispatcher.sendDueMailbox())
        assertNull(subscriptions.find(id))
    }

    @Test
    fun retryAfterAcceptsSecondsAndDates() {
        assertEquals(30L, WebPushSender.retryAfterSeconds("30", now))
        assertEquals(60L, WebPushSender.retryAfterSeconds(null, now))
        assertEquals(90L, WebPushSender.retryAfterSeconds("Sat, 07 Nov 2026 12:01:30 GMT", now))
        assertEquals(WebPushSender.MAX_RETRY_SECONDS, WebPushSender.retryAfterSeconds("99999999", now))
    }
}
