package org.politisationpopulairefrancaise.server.accounts

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.identity.RecoveryCommitment
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.GroupModeration
import org.politisationpopulairefrancaise.server.groups.GroupRole
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.push.Base64Url
import org.politisationpopulairefrancaise.server.push.Nip98
import org.politisationpopulairefrancaise.server.push.P256
import org.politisationpopulairefrancaise.server.push.PushConfig
import org.politisationpopulairefrancaise.server.push.PushScope
import org.politisationpopulairefrancaise.server.push.PushSubscriptions
import org.politisationpopulairefrancaise.server.push.VapidKeys
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Instant
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Déclarer mon compte compromis" on the server (S-146, D-134, docs/integration/compte-compromis.md). */
class CompromisedAccountsTest {

    /** An account of a test person [EXEMPLE]: its key, its recovery key and its commitment. */
    private class Account {
        val keys = LocalEventSigner(IdentityKeys.generate())
        val recovery = LocalEventSigner(IdentityKeys.generate())
        val hex: String get() = keys.publicKey.hex
    }

    private val alice = Account()
    private val bob = Account()
    private val repository = EventRepository(TestDatabase.dataSource)
    private val accounts = CompromisedAccounts(repository)
    private val day = 24L * 60 * 60

    private val publicUrl = "https://site.example.org"
    private val push = PushConfig(
        vapid = VapidKeys(P256.generateKeyPair().private as ECPrivateKey),
        allowedHosts = setOf("push.example.org"),
        publicUrl = publicUrl,
    )

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun now(): Long = Instant.now().epochSecond

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private suspend fun HttpResponse.error(): String = Json.decodeFromString<ApiError>(bodyAsText()).error

    private suspend fun HttpClient.status(pubkey: String): AccountStatus =
        get("/api/v1/accounts/$pubkey/status").let { answer ->
            assertEquals(HttpStatusCode.OK, answer.status)
            assertEquals("no-store", answer.headers[HttpHeaders.CacheControl])
            Json.decodeFromString(answer.bodyAsText())
        }

    private suspend fun Account.commit(recoveryKey: PublicKey = recovery.publicKey, at: Long = now() - 100 * day): SignedEvent =
        AccountRecovery.signCommitment(keys, recoveryKey, createdAt = at)

    private suspend fun Account.notice(
        commitment: SignedEvent,
        signer: LocalEventSigner = recovery,
        since: Long? = null,
        successor: PublicKey? = null,
        at: Long = now(),
    ): SignedEvent {
        val committed = AccountRecovery.parseCommitment(commitment)!!
        val named = RecoveryCommitment(committed.id, committed.account, signer.publicKey.hex, committed.createdAt)
        return signer.sign(AccountRecovery.notice(signer.publicKey, named, at, since ?: at, successor = successor))
    }

    @Test
    fun theFirstCommitmentWins() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val first = alice.commit()
        assertEquals(HttpStatusCode.Created, client.publish(first).status)
        assertEquals(alice.recovery.publicKey.hex, accounts.commitmentOf(alice.hex)?.recovery)
        // The same event again is a duplicate, not a second commitment.
        assertEquals(HttpStatusCode.OK, client.publish(first).status)
        // A thief with the account key names their own recovery key: refused, the first one stays.
        val thief = LocalEventSigner(IdentityKeys.generate())
        val second = client.publish(alice.commit(thief.publicKey, at = now()))
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals("recovery_already_set", second.error())
        assertEquals(first.id, accounts.commitmentOf(alice.hex)?.id)
        assertEquals(first.id, client.status(alice.hex).commitment)
        assertNull(client.status(bob.hex).commitment)

        // Something that is not a commitment of our form.
        val malformed = client.publish(alice.keys.sign(kind = AccountRecovery.COMMITMENT_KIND, tags = listOf(listOf("p", bob.hex))))
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("invalid_commitment", malformed.error())
        val inARoom = client.publish(bob.keys.sign(kind = AccountRecovery.COMMITMENT_KIND, tags = bob.commit().tags + listOf(listOf("h", "salon"))))
        assertEquals(HttpStatusCode.BadRequest, inARoom.status)
        assertNull(accounts.commitmentOf(bob.hex))
    }

    @Test
    fun onlyTheRecoveryKeyOfTheFirstCommitmentDeclaresTheCompromise() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val commitment = alice.commit()
        // No commitment yet: no notice.
        val early = client.publish(alice.notice(commitment))
        assertEquals(HttpStatusCode.BadRequest, early.status)
        assertEquals("invalid_notice", early.error())
        assertEquals(HttpStatusCode.Created, client.publish(commitment).status)

        // Signed by another key (the thief's), or naming another commitment: refused.
        val thief = LocalEventSigner(IdentityKeys.generate())
        val forged = client.publish(alice.notice(commitment, signer = thief))
        assertEquals(HttpStatusCode.BadRequest, forged.status)
        assertEquals("invalid_notice", forged.error())
        val otherCommitment = alice.commit(at = now() - 5)
        val wrongLink = client.publish(alice.notice(otherCommitment))
        assertEquals(HttpStatusCode.BadRequest, wrongLink.status)
        assertFalse(accounts.isCompromised(alice.hex))

        // "Compromised since" goes back 30 days at most, and a notice is not dated in the future.
        val tooOld = AccountRecovery.signNotice(alice.recovery, accounts.commitmentOf(alice.hex)!!, createdAt = now(), since = now())
            .let { valid -> alice.recovery.sign(kind = AccountRecovery.NOTICE_KIND, tags = valid.tags.map { if (it[0] == AccountRecovery.SINCE_TAG) listOf(it[0], (now() - 31 * day).toString()) else it }) }
        assertEquals("invalid_notice", client.publish(tooOld).error())
        assertEquals("invalid_notice", client.publish(alice.notice(commitment, at = now() + day)).error())
        assertFalse(accounts.isCompromised(alice.hex))

        // The real notice, backdated 30 days.
        val since = now() - 30 * day
        val notice = alice.notice(commitment, since = since)
        assertEquals(HttpStatusCode.Created, client.publish(notice).status)
        assertTrue(accounts.isCompromised(alice.hex))
        assertEquals(notice, accounts.noticeFor(alice.hex))
        val status = client.status(alice.hex)
        assertEquals(AccountStatus(compromised = true, since = since, successor = null, notice = notice, commitment = commitment.id), status)

        // Irreversible, once.
        val again = client.publish(alice.notice(commitment, at = now() + 1))
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("already_compromised", again.error())
    }

    @Test
    fun nothingMoreIsAcceptedFromACompromisedKey() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val before = alice.keys.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] avant", createdAt = now() - 10)
        assertEquals(HttpStatusCode.Created, client.publish(before).status)
        val commitment = alice.commit()
        assertEquals(HttpStatusCode.Created, client.publish(commitment).status)
        assertEquals(HttpStatusCode.Created, client.publish(alice.notice(commitment)).status)

        val refused = listOf(
            alice.keys.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] après"),
            alice.keys.sign(kind = EventKinds.METADATA, content = """{"name":"[EXEMPLE] Alice"}"""),
            alice.commit(LocalEventSigner(IdentityKeys.generate()).publicKey, at = now()),
            alice.keys.sign(kind = EventKinds.GIFT_WRAP, tags = listOf(listOf("p", bob.hex)), content = "[EXEMPLE]"),
        )
        for (event in refused) {
            val answer = client.publish(event)
            assertEquals(HttpStatusCode.Forbidden, answer.status, "kind ${event.kind}")
            assertEquals("account_compromised", answer.error())
        }
        // What was published before stays served (the device marks what is dated after "since").
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/events/${before.id}").status)
        // Other accounts are not concerned.
        assertEquals(HttpStatusCode.Created, client.publish(bob.keys.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] Bob")).status)
        assertFalse(client.status(bob.hex).compromised)
    }

    @Test
    fun aKeySucceedsOneAccountAtMost() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val successor = IdentityKeys.generate().publicKey
        val aliceCommitment = alice.commit()
        val bobCommitment = bob.commit()
        assertEquals(HttpStatusCode.Created, client.publish(aliceCommitment).status)
        assertEquals(HttpStatusCode.Created, client.publish(bobCommitment).status)

        assertEquals(HttpStatusCode.Created, client.publish(alice.notice(aliceCommitment, successor = successor)).status)
        assertEquals(successor.hex, accounts.successorOf(alice.hex))
        assertEquals(alice.hex, accounts.predecessorOf(successor.hex))
        assertEquals(successor.hex, client.status(alice.hex).successor)

        val taken = client.publish(bob.notice(bobCommitment, successor = successor))
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("successor_taken", taken.error())
        assertFalse(accounts.isCompromised(bob.hex))
        // A compromised account cannot be named successor either.
        val compromisedSuccessor = client.publish(bob.notice(bobCommitment, successor = alice.keys.publicKey))
        assertEquals(HttpStatusCode.BadRequest, compromisedSuccessor.status)
        // Without a successor, Bob's notice goes through.
        assertEquals(HttpStatusCode.Created, client.publish(bob.notice(bobCommitment)).status)
        assertNull(accounts.successorOf(bob.hex))
        assertNull(accounts.predecessorOf(bob.hex))
    }

    @Test
    fun theStatusOfAnUnknownOrMalformedKey() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(AccountStatus(compromised = false), client.status(alice.hex))
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/accounts/${alice.hex.uppercase()}/status").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/accounts/abc/status").status)
    }

    @Test
    fun theWakeUpsAndTheRoomRolesOfTheKeyAreRemoved() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, push = push) }
        val subscriptions = PushSubscriptions(TestDatabase.dataSource)
        val start = Instant.now()
        subscriptions.save(PushScope.MAILBOX, "https://push.example.org/upalice1?up=1", "p", "a", alice.hex, start)
        subscriptions.save(PushScope.MAILBOX, "https://push.example.org/upbob1?up=1", "p", "a", bob.hex, start)
        // No public subscription: nothing would be woken (no request leaves the test).
        // Alice created a room (first administrator), Bob another one.
        assertEquals(HttpStatusCode.Created, client.publish(room(alice.keys, "salon-alice")).status)
        assertEquals(HttpStatusCode.Created, client.publish(room(bob.keys, "salon-bob")).status)
        val moderation = GroupModeration(repository)
        assertEquals(GroupRole.ADMIN, moderation.roleOf("salon-alice", alice.hex))

        val commitment = alice.commit()
        assertEquals(HttpStatusCode.Created, client.publish(commitment).status)
        assertEquals(HttpStatusCode.Created, client.publish(alice.notice(commitment)).status)

        assertTrue(subscriptions.mailboxesOf(alice.hex).isEmpty())
        assertEquals(1, subscriptions.mailboxesOf(bob.hex).size)
        assertEquals(1L, subscriptions.count())
        assertNull(moderation.roleOf("salon-alice", alice.hex))
        assertEquals(GroupRole.ADMIN, moderation.roleOf("salon-bob", bob.hex))

        // The key gets no new mailbox subscription either.
        val body = subscriptionJson()
        val answer = client.post("/api/v1/push/subscriptions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, nip98(alice.keys, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.Forbidden, answer.status)
        assertEquals("account_compromised", answer.error())
        assertNotNull(subscriptions.mailboxesOf(bob.hex).singleOrNull())
    }

    private suspend fun room(signer: LocalEventSigner, id: String): SignedEvent = signer.sign(
        kind = GroupKinds.METADATA,
        tags = listOf(
            listOf("d", id),
            listOf("name", "[EXEMPLE] $id"),
            listOf(GroupKinds.TERRITORY_TAG, "Lyon"),
            listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION),
        ),
    )

    private fun subscriptionJson(): String {
        val receiver = P256.generateKeyPair()
        val p256dh = Base64Url.encode(P256.encodePoint(receiver.public as ECPublicKey))
        val auth = Base64Url.encode(ByteArray(16) { (it * 7).toByte() })
        val topic = Base64Url.encode(ByteArray(9).also(SecureRandom()::nextBytes)).replace('-', 'a').replace('_', 'b')
        return """{"scope":"mailbox","endpoint":"https://push.example.org/up$topic?up=1","p256dh":"$p256dh","auth":"$auth"}"""
    }

    private suspend fun nip98(signer: LocalEventSigner, body: String): String {
        val event = signer.sign(
            kind = Nip98.KIND,
            tags = listOf(
                listOf("u", "$publicUrl/api/v1/push/subscriptions"),
                listOf("method", "POST"),
                listOf("payload", Nip98.sha256Hex(body.encodeToByteArray())),
            ),
        )
        return "Nostr " + Base64.getEncoder().encodeToString(event.toJson().encodeToByteArray())
    }
}
