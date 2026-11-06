package org.politisationpopulairefrancaise.server.api

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

    @Test
    fun publishesListsAndFetchesAuthenticEvents() = testApplication {
        application { module(TestDatabase.dataSource) }
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] première publication", createdAt = 1_700_000_000)

        val created = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(event.toJson())
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(PublishResponse(event.id, "stored"), Json.decodeFromString<PublishResponse>(created.bodyAsText()))

        val again = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(event.toJson())
        }
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals("duplicate", Json.decodeFromString<PublishResponse>(again.bodyAsText()).status)

        val list = client.get("/api/v1/events?kind=1&author=${event.pubkey}")
        assertEquals(HttpStatusCode.OK, list.status)
        assertEquals(listOf(event), Json.decodeFromString<List<SignedEvent>>(list.bodyAsText()))

        val one = client.get("/api/v1/events/${event.id}")
        assertEquals(HttpStatusCode.OK, one.status)
        assertEquals(event, SignedEvent.fromJson(one.bodyAsText()))
        assertTrue(SignedEvent.fromJson(one.bodyAsText()).verify())

        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/events/${"0".repeat(64)}").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events/not-an-id").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?kind=abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?author=ABC").status)
    }

    @Test
    fun refusesForgedMalformedEphemeralAndOversizedEvents() = testApplication {
        application { module(TestDatabase.dataSource) }
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] authentique", createdAt = 1_700_000_000)

        val forged = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(event.copy(content = "[EXEMPLE] falsifié").toJson())
        }
        assertEquals(HttpStatusCode.BadRequest, forged.status)
        assertEquals("invalid_signature", Json.decodeFromString<ApiError>(forged.bodyAsText()).error)

        val malformed = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody("{\"hello\": 1}")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("malformed_event", Json.decodeFromString<ApiError>(malformed.bodyAsText()).error)

        val ephemeral = signer.sign(kind = 20001, content = "x", createdAt = 1_700_000_000)
        val refused = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(ephemeral.toJson())
        }
        assertEquals(HttpStatusCode.BadRequest, refused.status)
        assertEquals("ephemeral_kind", Json.decodeFromString<ApiError>(refused.bodyAsText()).error)

        val huge = signer.sign(kind = EventKinds.TEXT_NOTE, content = "x".repeat(70_000), createdAt = 1_700_000_000)
        val tooLarge = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(huge.toJson())
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, tooLarge.status)

        assertEquals("[]", client.get("/api/v1/events").bodyAsText())
    }

    @Test
    fun editorialContentsNeedAnEditorKey() = testApplication {
        val editor = LocalEventSigner(IdentityKeys.generate())
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex))) }
        val tags = listOf(listOf("d", "fiche"), listOf("title", "[EXEMPLE] Fiche"), listOf("ppf-origin", "human"))

        val fromCitizen = signer.sign(kind = Article.KIND, content = "x", tags = tags, createdAt = 1_700_000_000)
        val refused = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(fromCitizen.toJson())
        }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("not_allowed", Json.decodeFromString<ApiError>(refused.bodyAsText()).error)

        val fromEditor = editor.sign(kind = Article.KIND, content = "x", tags = tags, createdAt = 1_700_000_000)
        val accepted = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(fromEditor.toJson())
        }
        assertEquals(HttpStatusCode.Created, accepted.status)
    }

    @Test
    fun listsByRepeatableTagParameters() = testApplication {
        application { module(TestDatabase.dataSource) }
        val carol = "c".repeat(64)
        val dave = "d".repeat(64)
        val toCarol = signer.sign(kind = EventKinds.TEXT_NOTE, content = "1", tags = listOf(listOf("p", carol)), createdAt = 100)
        val toDave = signer.sign(kind = EventKinds.TEXT_NOTE, content = "2", tags = listOf(listOf("p", dave), listOf("t", "a:b")), createdAt = 200)
        for (event in listOf(toCarol, toDave)) {
            val response = client.post("/api/v1/events") {
                contentType(ContentType.Application.Json)
                setBody(event.toJson())
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }

        assertEquals(listOf(toCarol), Json.decodeFromString<List<SignedEvent>>(client.get("/api/v1/events?tag=p:$carol").bodyAsText()))
        assertEquals(listOf(toDave, toCarol), Json.decodeFromString<List<SignedEvent>>(client.get("/api/v1/events?tag=p:$carol&tag=p:$dave").bodyAsText()))
        assertEquals(listOf(toDave), Json.decodeFromString<List<SignedEvent>>(client.get("/api/v1/events?tag=p:$dave&tag=t:a:b&kind=1").bodyAsText()))
        assertEquals("[]", client.get("/api/v1/events?tag=p:$carol&tag=t:a:b").bodyAsText())
        assertEquals("[]", client.get("/api/v1/events?tag=p:$carol&author=$carol").bodyAsText())

        for (invalid in listOf("tag=p", "tag=:value", "tag=", "tag=${"n".repeat(65)}:v", "tag=t:${"v".repeat(1025)}")) {
            val response = client.get("/api/v1/events?$invalid")
            assertEquals(HttpStatusCode.BadRequest, response.status, invalid)
            assertEquals("bad_parameter", Json.decodeFromString<ApiError>(response.bodyAsText()).error)
        }
        val tooMany = (1..21).joinToString("&") { "tag=t:$it" }
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/events?$tooMany").status)
    }

    @Test
    fun relaysEncryptedEnvelopesToTheirRecipientWithoutOpeningThem() = testApplication {
        application { module(TestDatabase.dataSource) }
        // A gift wrap is signed by a one-time key and carries only the recipient and an opaque payload.
        val oneTimeKey = LocalEventSigner(IdentityKeys.generate())
        val recipient = IdentityKeys.generate().publicKey.hex
        val other = IdentityKeys.generate().publicKey.hex
        val payload = "AqF3bXQ4[EXEMPLE ciphertext NIP-44]" + "x".repeat(200)
        val wrap = oneTimeKey.sign(kind = RelayKinds.GIFT_WRAP, content = payload, tags = listOf(listOf("p", recipient)), createdAt = 1_699_000_000)
        val toOther = signer.sign(kind = RelayKinds.GIFT_WRAP, content = "y", tags = listOf(listOf("p", other)), createdAt = 1_699_000_001)

        for (event in listOf(wrap, toOther)) {
            val response = client.post("/api/v1/events") {
                contentType(ContentType.Application.Json)
                setBody(event.toJson())
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
        val inbox = Json.decodeFromString<List<SignedEvent>>(client.get("/api/v1/events?kind=1059&tag=p:$recipient").bodyAsText())
        assertEquals(listOf(wrap), inbox, "stored byte for byte, including the opaque content")
        assertEquals("[]", client.get("/api/v1/events?kind=1059&tag=p:${"0".repeat(64)}").bodyAsText())
    }

    @Test
    fun limitsThePublicationRatePerKey() = testApplication {
        application { module(TestDatabase.dataSource, relay = RelayConfig(postRatePerMinute = 3)) }
        repeat(3) { index ->
            val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] $index", createdAt = 1_700_000_000L + index)
            val response = client.post("/api/v1/events") {
                contentType(ContentType.Application.Json)
                setBody(event.toJson())
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
        val fourth = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] 4", createdAt = 1_700_000_010)
        val limited = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(fourth.toJson())
        }
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("rate_limited", Json.decodeFromString<ApiError>(limited.bodyAsText()).error)
        assertEquals("20", limited.headers["Retry-After"], "one token every 20 seconds at 3 per minute")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/events/${fourth.id}").status, "the refused event is not stored")

        // Another key still publishes, and a forged event never spends the forged key's budget.
        val someoneElse = LocalEventSigner(IdentityKeys.generate())
        val accepted = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(someoneElse.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] autre", createdAt = 1_700_000_000).toJson())
        }
        assertEquals(HttpStatusCode.Created, accepted.status)
        repeat(5) {
            val forged = client.post("/api/v1/events") {
                contentType(ContentType.Application.Json)
                setBody(someoneElse.sign(kind = EventKinds.TEXT_NOTE, content = "x", createdAt = 1_700_000_000).copy(content = "forged $it").toJson())
            }
            assertEquals(HttpStatusCode.BadRequest, forged.status)
        }
        val stillAccepted = client.post("/api/v1/events") {
            contentType(ContentType.Application.Json)
            setBody(someoneElse.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] encore", createdAt = 1_700_000_001).toJson())
        }
        assertEquals(HttpStatusCode.Created, stillAccepted.status)
    }

    @Test
    fun withoutDatabaseTheApiSaysSoAndTheRestWorks() = testApplication {
        application { module(null) }
        val response = client.get("/api/v1/events")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("database_unavailable", Json.decodeFromString<ApiError>(response.bodyAsText()).error)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }
}
