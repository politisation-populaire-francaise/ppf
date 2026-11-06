package org.politisationpopulairefrancaise.server.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.relay.RelayConfig
import org.politisationpopulairefrancaise.server.relay.RelayKinds
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventsApiTest {

    private val signer = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private suspend fun HttpClient.publishJson(body: String): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.error(): String = Json.decodeFromString<ApiError>(bodyAsText()).error

    private suspend fun HttpResponse.publishStatus(): String = Json.decodeFromString<PublishResponse>(bodyAsText()).status

    private suspend fun HttpClient.list(query: String): List<SignedEvent> =
        Json.decodeFromString<List<SignedEvent>>(get("/api/v1/events?$query").bodyAsText())

    @Test
    fun publishesListsAndFetchesAuthenticEvents() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] première publication", createdAt = 1_700_000_000)

        val created = client.publish(event)
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(PublishResponse(event.id, "stored"), Json.decodeFromString<PublishResponse>(created.bodyAsText()))

        val again = client.publish(event)
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals("duplicate", again.publishStatus())
        // A stored id with another content is not a duplicate but a forgery.
        val tampered = client.publish(event.copy(content = "[EXEMPLE] contenu remplacé"))
        assertEquals(HttpStatusCode.BadRequest, tampered.status)
        assertEquals("invalid_signature", tampered.error())

        assertEquals(listOf(event), client.list("kind=1&author=${event.pubkey}"))

        val one = client.get("/api/v1/events/${event.id}")
        assertEquals(HttpStatusCode.OK, one.status)
        assertEquals(event, SignedEvent.fromJson(one.bodyAsText()))
        assertTrue(SignedEvent.fromJson(one.bodyAsText()).verify())

        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/events/${"0".repeat(64)}").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events/not-an-id").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?kind=abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?author=ABC").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?identifier=${"x".repeat(1025)}").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?" + (1..21).joinToString("&") { "identifier=$it" }).status)
    }

    @Test
    fun refusesForgedMalformedEphemeralAndOversizedEvents() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] authentique", createdAt = 1_700_000_000)

        val forged = client.publish(event.copy(content = "[EXEMPLE] falsifié"))
        assertEquals(HttpStatusCode.BadRequest, forged.status)
        assertEquals("invalid_signature", forged.error())

        val malformed = client.publishJson("{\"hello\": 1}")
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("malformed_event", malformed.error())

        val ephemeral = signer.sign(kind = 20001, content = "x", createdAt = 1_700_000_000)
        val refused = client.publish(ephemeral)
        assertEquals(HttpStatusCode.BadRequest, refused.status)
        assertEquals("ephemeral_kind", refused.error())

        val huge = signer.sign(kind = EventKinds.TEXT_NOTE, content = "x".repeat(70_000), createdAt = 1_700_000_000)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.publish(huge).status)

        val tagFlood = signer.sign(kind = EventKinds.TEXT_NOTE, content = "x", tags = List(1_001) { listOf("t", "a") }, createdAt = 1_700_000_000)
        val tooManyTags = client.publish(tagFlood)
        assertEquals(HttpStatusCode.BadRequest, tooManyTags.status)
        assertEquals("too_many_tags", tooManyTags.error())

        assertEquals("[]", client.get("/api/v1/events").bodyAsText())
    }

    @Test
    fun editorialContentsNeedAnEditorKey() = testApplication {
        val editor = LocalEventSigner(IdentityKeys.generate())
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY) }
        val tags = listOf(listOf("d", "fiche"), listOf("title", "[EXEMPLE] Fiche"), listOf("ppf-origin", "human"))

        val fromCitizen = signer.sign(kind = Article.KIND, content = "x", tags = tags, createdAt = 1_700_000_000)
        val refused = client.publish(fromCitizen)
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("not_allowed", refused.error())

        val fromEditor = editor.sign(kind = Article.KIND, content = "x", tags = tags, createdAt = 1_700_000_000)
        assertEquals(HttpStatusCode.Created, client.publish(fromEditor).status)
    }

    @Test
    fun listsByRepeatableTagParameters() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val carol = "c".repeat(64)
        val dave = "d".repeat(64)
        val toCarol = signer.sign(kind = EventKinds.TEXT_NOTE, content = "1", tags = listOf(listOf("p", carol)), createdAt = 100)
        val toDave = signer.sign(kind = EventKinds.TEXT_NOTE, content = "2", tags = listOf(listOf("p", dave), listOf("t", "a:b")), createdAt = 200)
        for (event in listOf(toCarol, toDave)) {
            assertEquals(HttpStatusCode.Created, client.publish(event).status)
        }

        assertEquals(listOf(toCarol), client.list("tag=p:$carol"))
        assertEquals(listOf(toDave, toCarol), client.list("tag=p:$carol&tag=p:$dave"))
        assertEquals(listOf(toDave), client.list("tag=p:$dave&tag=t:a:b&kind=1"))
        assertEquals(emptyList(), client.list("tag=p:$carol&tag=t:a:b"))
        assertEquals(emptyList(), client.list("tag=p:$carol&author=$carol"))

        for (invalid in listOf("tag=p", "tag=:value", "tag=", "tag=${"n".repeat(65)}:v", "tag=t:${"v".repeat(1025)}")) {
            val response = client.get("/api/v1/events?$invalid")
            assertEquals(HttpStatusCode.BadRequest, response.status, invalid)
            assertEquals("bad_parameter", response.error())
        }
        val tooMany = (1..21).joinToString("&") { "tag=t:$it" }
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?$tooMany").status)
        val mixed = (1..11).joinToString("&") { "tag=t:$it" } + "&" + (1..10).joinToString("&") { "identifier=$it" }
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?$mixed").status, "identifiers count as tag filters")
    }

    @Test
    fun relaysEncryptedEnvelopesToTheirRecipientWithoutOpeningThem() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        // A gift wrap is signed by a one-time key and carries only the recipient and an opaque payload.
        val oneTimeKey = LocalEventSigner(IdentityKeys.generate())
        val recipient = IdentityKeys.generate().publicKey.hex
        val other = IdentityKeys.generate().publicKey.hex
        val payload = "AqF3bXQ4[EXEMPLE ciphertext NIP-44]" + "x".repeat(200)
        val wrap = oneTimeKey.sign(kind = RelayKinds.GIFT_WRAP, content = payload, tags = listOf(listOf("p", recipient)), createdAt = 1_699_000_000)
        val toOther = signer.sign(kind = RelayKinds.GIFT_WRAP, content = "y", tags = listOf(listOf("p", other, "wss://relay.example")), createdAt = 1_699_000_001)

        for (event in listOf(wrap, toOther)) {
            assertEquals(HttpStatusCode.Created, client.publish(event).status)
        }
        assertEquals(listOf(wrap), client.list("kind=1059&tag=p:$recipient"), "stored byte for byte, including the opaque content")
        assertEquals(emptyList(), client.list("kind=1059&tag=p:${"0".repeat(64)}"))

        // An envelope without exactly one recipient could never be fetched: refused.
        for (tags in listOf(emptyList(), listOf(listOf("p", recipient), listOf("p", other)), listOf(listOf("p", "not-a-key")), listOf(listOf("p", recipient.uppercase())))) {
            val response = client.publish(signer.sign(kind = RelayKinds.GIFT_WRAP, content = "z", tags = tags, createdAt = 1))
            assertEquals(HttpStatusCode.BadRequest, response.status, tags.toString())
            assertEquals("bad_recipient", response.error())
        }
    }

    @Test
    fun keepsOnlyTheNewestEnvelopesOfARecipient() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY.copy(wrapMaxPerRecipient = 3)) }
        val recipient = IdentityKeys.generate().publicKey.hex
        val other = IdentityKeys.generate().publicKey.hex
        val wraps = (1..5).map { index ->
            LocalEventSigner(IdentityKeys.generate()).sign(kind = RelayKinds.GIFT_WRAP, content = "wrap $index", tags = listOf(listOf("p", recipient)), createdAt = 1_000L + index)
        }
        val forOther = signer.sign(kind = RelayKinds.GIFT_WRAP, content = "other", tags = listOf(listOf("p", other)), createdAt = 1)
        assertEquals(HttpStatusCode.Created, client.publish(forOther).status)
        for (wrap in wraps) {
            assertEquals(HttpStatusCode.Created, client.publish(wrap).status)
        }
        // The three most recently received envelopes remain (reception order = publication order here).
        assertEquals(wraps.takeLast(3).map { it.id }.toSet(), client.list("kind=1059&tag=p:$recipient").map { it.id }.toSet())
        assertEquals(listOf(forOther), client.list("kind=1059&tag=p:$other"), "another mailbox is untouched")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/events/${wraps[0].id}").status)
    }

    @Test
    fun limitsThePublicationRatePerKey() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY.copy(postRatePerMinute = 3)) }
        repeat(3) { index ->
            val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] $index", createdAt = 1_700_000_000L + index)
            assertEquals(HttpStatusCode.Created, client.publish(event).status)
        }
        val fourth = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] 4", createdAt = 1_700_000_010)
        val limited = client.publish(fourth)
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("rate_limited", limited.error())
        val retryAfter = limited.headers["Retry-After"]?.toIntOrNull()
        assertTrue(retryAfter != null && retryAfter in 1..20, "one token every 20 seconds at 3 per minute, got $retryAfter")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/events/${fourth.id}").status, "the refused event is not stored")

        // Another key still publishes, and a forged event never spends the forged key's budget.
        val someoneElse = LocalEventSigner(IdentityKeys.generate())
        val accepted = client.publish(someoneElse.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] autre", createdAt = 1_700_000_000))
        assertEquals(HttpStatusCode.Created, accepted.status)
        repeat(5) {
            val forged = client.publish(someoneElse.sign(kind = EventKinds.TEXT_NOTE, content = "x", createdAt = 1_700_000_000).copy(content = "forged $it"))
            assertEquals(HttpStatusCode.BadRequest, forged.status)
        }
        assertEquals(HttpStatusCode.Created, client.publish(someoneElse.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] encore", createdAt = 1_700_000_001)).status)
    }

    @Test
    fun replayingPublicEventsDoesNotDrainTheirAuthorsBudget() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY.copy(postRatePerMinute = 3)) }
        val note = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] publique", createdAt = 1_700_000_000)
        val profileV1 = signer.sign(kind = EventKinds.METADATA, content = "{\"name\":\"[EXEMPLE] v1\"}", createdAt = 1_700_000_000)
        val profileV2 = signer.sign(kind = EventKinds.METADATA, content = "{\"name\":\"[EXEMPLE] v2\"}", createdAt = 1_700_000_100)
        assertEquals(HttpStatusCode.Created, client.publish(note).status)
        assertEquals(HttpStatusCode.Created, client.publish(profileV2).status)
        // Two of three tokens used. Anyone can re-send a stored event: answered as a duplicate without touching the budget.
        repeat(10) {
            val replay = client.publish(note)
            assertEquals(HttpStatusCode.OK, replay.status)
            assertEquals("duplicate", replay.publishStatus())
        }
        // Replaying an old version of a replaceable content is superseded and the token given back.
        repeat(10) {
            val replay = client.publish(profileV1)
            assertEquals(HttpStatusCode.OK, replay.status)
            assertEquals("superseded", replay.publishStatus())
        }
        // The last token is still there; without the refunds the replays would have taken it.
        assertEquals(HttpStatusCode.Created, client.publish(signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] troisième", createdAt = 1_700_000_200)).status)
        assertEquals(HttpStatusCode.TooManyRequests, client.publish(signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] quatrième", createdAt = 1_700_000_300)).status)
    }

    @Test
    fun theServerWideCeilingAppliesToEveryKey() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY.copy(globalPostRatePerMinute = 2)) }
        repeat(2) { index ->
            val oneTimeKey = LocalEventSigner(IdentityKeys.generate())
            assertEquals(HttpStatusCode.Created, client.publish(oneTimeKey.sign(kind = EventKinds.TEXT_NOTE, content = "$index", createdAt = 1)).status)
        }
        val third = client.publish(LocalEventSigner(IdentityKeys.generate()).sign(kind = EventKinds.TEXT_NOTE, content = "3", createdAt = 1))
        assertEquals(HttpStatusCode.TooManyRequests, third.status)
        assertEquals("rate_limited", third.error())
        assertTrue(third.headers["Retry-After"] != null)
    }

    @Test
    fun withoutDatabaseTheApiSaysSoAndTheRestWorks() = testApplication {
        application { module(null, NO_EDITORS, TEST_RELAY) }
        val response = client.get("/api/v1/events")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("database_unavailable", response.error())
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/groups").status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }
}
