package org.politisationpopulairefrancaise.server.push

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.module
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushApiTest {

    private val publicUrl = "https://site.example.org"
    private val config = PushConfig(
        vapid = VapidKeys(P256.generateKeyPair().private as ECPrivateKey),
        allowedHosts = setOf("push.example.org"),
        publicUrl = publicUrl,
    )
    private val account = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun subscriptionJson(scope: String, endpoint: String = "https://push.example.org/up${randomTopic()}?up=1"): String {
        val receiver = P256.generateKeyPair()
        val p256dh = Base64Url.encode(P256.encodePoint(receiver.public as ECPublicKey))
        val auth = Base64Url.encode(ByteArray(16) { (it * 7).toByte() })
        return """{"scope":"$scope","endpoint":"$endpoint","p256dh":"$p256dh","auth":"$auth"}"""
    }

    private fun randomTopic(): String = Base64Url.encode(ByteArray(9).also(java.security.SecureRandom()::nextBytes)).replace('-', 'a').replace('_', 'b')

    private suspend fun nip98(
        body: String,
        url: String = "$publicUrl/api/v1/push/subscriptions",
        method: String = "POST",
        payload: String = Nip98.sha256Hex(body.encodeToByteArray()),
        createdAt: Long = System.currentTimeMillis() / 1000,
        kind: Int = Nip98.KIND,
        signer: LocalEventSigner = account,
    ): String {
        val event = signer.sign(kind = kind, tags = listOf(listOf("u", url), listOf("method", method), listOf("payload", payload)), createdAt = createdAt)
        return "Nostr " + Base64.getEncoder().encodeToString(event.toJson().encodeToByteArray())
    }

    private suspend fun HttpClient.subscribe(body: String, authorization: String? = null): HttpResponse = post("/api/v1/push/subscriptions") {
        contentType(ContentType.Application.Json)
        authorization?.let { header(HttpHeaders.Authorization, it) }
        setBody(body)
    }

    private suspend fun HttpResponse.error(): String = Json.decodeFromString<ApiError>(bodyAsText()).error

    private suspend fun HttpResponse.id(): String = Json.decodeFromString<SubscriptionResponse>(bodyAsText()).id

    @Test
    fun pushIsOffWithoutAVapidKey() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, push = PushConfig.DISABLED) }
        val vapid = client.get("/api/v1/push/vapid")
        assertEquals(HttpStatusCode.ServiceUnavailable, vapid.status)
        assertEquals("push_disabled", vapid.error())
        val subscribe = client.subscribe(subscriptionJson("public"))
        assertEquals(HttpStatusCode.ServiceUnavailable, subscribe.status)
        assertEquals("push_disabled", subscribe.error())
    }

    @Test
    fun publishesTheVapidKey() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, push = config) }
        val answer = client.get("/api/v1/push/vapid")
        assertEquals(HttpStatusCode.OK, answer.status)
        val key = Json.decodeFromString<VapidKeyResponse>(answer.bodyAsText()).publicKey
        assertEquals(config.vapid!!.publicKeyBase64Url, key)
        assertEquals(65, Base64Url.decode(key).size)
    }

    @Test
    fun publicSubscriptionIsAnonymousRenewableAndDeletable() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, push = config) }
        val body = subscriptionJson("public")
        val created = client.subscribe(body)
        assertEquals(HttpStatusCode.Created, created.status)
        val id = created.id()
        assertEquals(22, id.length)

        val repository = PushSubscriptions(TestDatabase.dataSource)
        val stored = repository.find(id)!!
        assertEquals(PushScope.PUBLIC, stored.scope)
        assertNull(stored.pubkey)

        // The same endpoint again: same id, renewed.
        Thread.sleep(5)
        val renewed = client.subscribe(body)
        assertEquals(HttpStatusCode.Created, renewed.status)
        assertEquals(id, renewed.id())
        assertTrue(repository.find(id)!!.renewedAt.isAfter(stored.renewedAt))
        assertEquals(1L, repository.count())

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/push/subscriptions/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/push/subscriptions/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/push/subscriptions/not-an-id").status)
        assertEquals(0L, repository.count())
    }

    @Test
    fun refusesOtherHostsAndBadInputs() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, push = config) }
        val otherHost = client.subscribe(subscriptionJson("public", "https://push.elsewhere.example/up123"))
        assertEquals(HttpStatusCode.UnprocessableEntity, otherHost.status)
        assertEquals("push_host_not_allowed", otherHost.error())
        // http is accepted in development only.
        assertEquals("bad_endpoint", client.subscribe(subscriptionJson("public", "http://push.example.org/up123")).error())
        assertEquals("bad_endpoint", client.subscribe(subscriptionJson("public", "https://user@push.example.org/up123")).error())
        assertEquals("bad_scope", client.subscribe(subscriptionJson("everyone")).error())
        assertEquals("malformed_subscription", client.subscribe("{}").error())
        val shortAuth = """{"scope":"public","endpoint":"https://push.example.org/up1","p256dh":"${Base64Url.encode(P256.encodePoint(P256.generateKeyPair().public as ECPublicKey))}","auth":"${Base64Url.encode(ByteArray(8))}"}"""
        assertEquals("bad_keys", client.subscribe(shortAuth).error())
        val notAPoint = """{"scope":"public","endpoint":"https://push.example.org/up1","p256dh":"${Base64Url.encode(ByteArray(65) { 4 })}","auth":"${Base64Url.encode(ByteArray(16))}"}"""
        assertEquals("bad_keys", client.subscribe(notAPoint).error())
        assertEquals(0L, PushSubscriptions(TestDatabase.dataSource).count())
    }

    @Test
    fun mailboxSubscriptionNeedsAValidNip98Authorization() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, push = config) }
        val body = subscriptionJson("mailbox")
        val now = System.currentTimeMillis() / 1000

        val refusals = listOf(
            null,
            "Bearer abc",
            "Nostr not-base64!",
            nip98(body, url = "https://other.example.org/api/v1/push/subscriptions"),
            nip98(body, method = "GET"),
            nip98(body, payload = Nip98.sha256Hex("{}".encodeToByteArray())),
            nip98(body, createdAt = now - 120),
            nip98(body, createdAt = now + 120),
            nip98(body, kind = 1),
        )
        for (authorization in refusals) {
            val refused = client.subscribe(body, authorization)
            assertEquals(HttpStatusCode.Unauthorized, refused.status, "authorization: $authorization")
            assertEquals("unauthorized", refused.error())
        }
        // A forged signature: the event of another key with this account's pubkey.
        val forged = run {
            val event = account.sign(kind = Nip98.KIND, tags = listOf(listOf("u", "$publicUrl/api/v1/push/subscriptions"), listOf("method", "POST"), listOf("payload", Nip98.sha256Hex(body.encodeToByteArray()))))
            val tampered = event.copy(tags = event.tags + listOf(listOf("x", "y")))
            "Nostr " + Base64.getEncoder().encodeToString(tampered.toJson().encodeToByteArray())
        }
        assertEquals(HttpStatusCode.Unauthorized, client.subscribe(body, forged).status)

        val accepted = client.subscribe(body, nip98(body))
        assertEquals(HttpStatusCode.Created, accepted.status)
        val stored = PushSubscriptions(TestDatabase.dataSource).find(accepted.id())!!
        assertEquals(PushScope.MAILBOX, stored.scope)
        assertEquals(account.publicKey.hex, stored.pubkey)
    }

    @Test
    fun limitsSubscriptionsPerKeyAndNewPublicOnesPerDay() = testApplication {
        val service = PushService(
            config,
            PushSubscriptions(TestDatabase.dataSource),
            dispatcher = null,
            publicPerDay = org.politisationpopulairefrancaise.server.revendications.PeriodBudget(2, kotlin.time.Duration.parse("24h"), maxTrackedKeys = 1),
        )
        application {
            install(ContentNegotiation) { json() }
            routing { pushApi(service) }
        }
        repeat(PushService.SUBSCRIPTIONS_PER_KEY_PER_MINUTE) {
            val body = subscriptionJson("mailbox")
            assertEquals(HttpStatusCode.Created, client.subscribe(body, nip98(body)).status)
        }
        val body = subscriptionJson("mailbox")
        val limited = client.subscribe(body, nip98(body))
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertTrue(limited.headers[HttpHeaders.RetryAfter]!!.toLong() >= 1)

        val first = subscriptionJson("public")
        assertEquals(HttpStatusCode.Created, client.subscribe(first).status)
        assertEquals(HttpStatusCode.Created, client.subscribe(subscriptionJson("public")).status)
        val third = client.subscribe(subscriptionJson("public"))
        assertEquals(HttpStatusCode.TooManyRequests, third.status)
        assertTrue(third.headers[HttpHeaders.RetryAfter]!!.toLong() >= 1)
        // A renewal is not a new subscription: it never counts.
        assertEquals(HttpStatusCode.Created, client.subscribe(first).status)
        assertNotEquals(0L, PushSubscriptions(TestDatabase.dataSource).count())
    }
}
