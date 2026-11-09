package org.politisationpopulairefrancaise.signer

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.EventDraft
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.KeyStatus
import org.politisationpopulairefrancaise.core.platform.RootRequestBody
import org.politisationpopulairefrancaise.core.platform.RootRequestResponse
import org.politisationpopulairefrancaise.core.platform.RootRequestView
import org.politisationpopulairefrancaise.core.platform.RootRequests
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.TeamList
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Root requests (D-141): the unsigned events the back-office deposits for the owner's phone. */
class RootRequestTest {
    private val f = SignerFixture()
    private val json = SigningProtocol.json

    /** The certificate the root must sign for the key of [use] awaiting it. */
    private fun certificate(service: SignerService, use: SigningUse = SigningUse.EDITORIAL_VOIR, notAfter: Long = f.now() + 90 * DAY): UnsignedEvent =
        KeyCertificate.unsigned(f.rootHex, f.key(service, use, WorkingKeyStates.AWAITING_CERTIFICATION), listOf(use), f.now(), notAfter, f.now())

    private fun revocation(key: String, use: SigningUse, from: Long = f.now()): UnsignedEvent =
        KeyCertificate.unsigned(f.rootHex, key, listOf(use), f.now() - DAY, f.now() + 89 * DAY, f.now(), KeyStatus.REVOKED, from)

    private fun team(): UnsignedEvent = TeamList.unsigned(f.rootHex, listOf(f.alice.member(), f.bob.member()), f.now(), f.now() + 90 * DAY)

    private fun create(service: SignerService, vararg events: UnsignedEvent): RootRequestOutcome = service.createRootRequest(events.map(EventDraft::of))

    private fun refusal(service: SignerService, vararg events: UnsignedEvent): String? = (create(service, *events) as? RootRequestOutcome.Refused)?.refusal?.code

    private fun edited(event: UnsignedEvent, tags: List<List<String>> = event.tags, kind: Int = event.kind, content: String = event.content) =
        UnsignedEvent(event.pubkey, event.createdAt, kind, tags, content)

    @Test
    fun aRequestIsKeptWithItsHashAndJournaledWithoutItsEvents() {
        val service = f.open()
        f.certify(service, SigningUse.OPENDATA_INDEX)
        val index = f.currentKey(service, SigningUse.OPENDATA_INDEX)!!
        val events = listOf(certificate(service), revocation(index, SigningUse.OPENDATA_INDEX), team())
        val created = assertIs<RootRequestOutcome.Created>(create(service, *events.toTypedArray()))
        assertTrue(Regex("^[0-9a-f]{32}$").matches(created.id))
        assertEquals(f.now() + 86_400, created.expiresAt)
        // The hash is the SHA-256 of the canonical JSON array of the unsigned events, in order.
        val canonical = "[" + events.joinToString(",") { it.canonicalJson() } + "]"
        assertEquals(MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).toHexString(), created.sha256)
        assertEquals(RootRequests.sha256(events), created.sha256)
        val view = assertNotNull(service.rootRequest(created.id))
        assertEquals(created.sha256, view.sha256)
        assertEquals(events, view.events.map { it.toUnsigned() })
        assertEquals(f.now(), view.createdAt)
        val line = service.journal(0, 1000).last()
        assertEquals(JournalActions.ROOT_REQUEST_CREATED, line.action)
        assertEquals(created.sha256, line.event)
        assertEquals(created.id, line.d)
        assertEquals("3", line.code)
        // Nothing of the events but their number enters the journal.
        assertTrue(service.journal(0, 1000).none { it.action.startsWith("root_request") && (it.key != null || it.use != null) })
    }

    @Test
    fun eachMalformedRequestIsRefused() {
        val service = f.open()
        val good = certificate(service)
        assertEquals(SigningRefusals.MALFORMED_REQUEST, refusal(service))
        assertEquals(SigningRefusals.MALFORMED_REQUEST, refusal(service, *Array(101) { good }))
        assertEquals(SigningRefusals.NOT_A_ROOT_EVENT, refusal(service, UnsignedEvent(f.alice.nostr.publicKey.hex, f.now(), EventKinds.PLATFORM_KEY_CERTIFICATE, good.tags, "")))
        assertEquals(SigningRefusals.NOT_A_ROOT_EVENT, refusal(service, edited(good, kind = EventKinds.LONG_FORM_ARTICLE)))
        assertEquals(SigningRefusals.UNKNOWN_KEY, refusal(service, KeyCertificate.unsigned(f.rootHex, IdentityKeys.generate().publicKey.hex, listOf(SigningUse.EDITORIAL_VOIR), f.now(), f.now() + DAY, f.now())))
        assertEquals(SigningRefusals.WRONG_USES, refusal(service, certificate(service, SigningUse.EDITORIAL_VOIR).let { edited(it, tags = it.tags.map { tag -> if (tag[0] == KeyCertificate.TAG_USE) listOf(tag[0], "editorial.comprendre") else tag }) }))
        assertEquals(SigningRefusals.BAD_VALIDITY, refusal(service, edited(good, tags = good.tags.map { if (it[0] == KeyCertificate.TAG_NOT_AFTER) listOf(it[0], "${f.now() + 91 * DAY}") else it })))
        assertEquals(SigningRefusals.BAD_VALIDITY, refusal(service, edited(good, tags = good.tags.map { if (it[0] == KeyCertificate.TAG_NOT_AFTER) listOf(it[0], "${f.now()}") else it })))
        assertEquals(SigningRefusals.BAD_STATUS, refusal(service, edited(good, tags = good.tags.map { if (it[0] == KeyCertificate.TAG_STATUS) listOf(it[0], "suspendue") else it })))
        assertEquals(SigningRefusals.BAD_CONTENT, refusal(service, edited(good, content = "[EXEMPLE]")))
        assertEquals(SigningRefusals.TAG_NOT_ALLOWED, refusal(service, edited(good, tags = good.tags + listOf(listOf("e", "a".repeat(64))))))
        assertEquals(SigningRefusals.NOT_A_TEAM_LIST, refusal(service, team().let { edited(it, tags = it.tags.map { tag -> if (tag[0] == "d") listOf("d", "autre") else tag }) }))
        assertEquals(SigningRefusals.NOT_A_TEAM_LIST, refusal(service, team().let { edited(it, tags = it.tags.map { tag -> if (tag[0] == TeamList.TAG_NOT_AFTER) listOf(tag[0], "${f.now() + 91 * DAY}") else tag }) }))
        assertEquals(SigningRefusals.DUPLICATE_ADDRESS, refusal(service, good, good))
        assertEquals(SigningRefusals.CREATED_AT_IN_FUTURE, refusal(service, UnsignedEvent(f.rootHex, f.now() + 600, good.kind, good.tags, "")))
        assertEquals(SigningRefusals.MALFORMED_EVENT, (service.createRootRequest(listOf(EventDraft("pas une clé", f.now(), good.kind))) as RootRequestOutcome.Refused).refusal.code)
        // Without a root nothing can be checked: refused.
        val rootless = SignerFixture().let { other -> other.open(other.config(root = null)) }
        assertEquals(SigningRefusals.NO_ROOT, (rootless.createRootRequest(listOf(EventDraft.of(good))) as RootRequestOutcome.Refused).refusal.code)
        // Every refusal is journaled with its code, never the events.
        assertTrue(service.journal(0, 1000).count { it.action == JournalActions.ROOT_REQUEST_REFUSED } >= 13)
    }

    @Test
    fun aRequestLastsTwentyFourHoursAndTwentyAtMostArePending() {
        val service = f.open()
        val first = assertIs<RootRequestOutcome.Created>(create(service, certificate(service)))
        f.clock.advance(86_399)
        assertNotNull(service.rootRequest(first.id))
        f.clock.advance(1)
        assertNull(service.rootRequest(first.id), "expired after 24 hours")
        val ids = List(21) { assertIs<RootRequestOutcome.Created>(create(service, team())).id }
        assertNull(service.rootRequest(ids.first()), "the oldest is dropped beyond 20")
        ids.drop(1).forEach { assertNotNull(service.rootRequest(it)) }
        assertNull(service.rootRequest("pas-un-identifiant"))
    }

    @Test
    fun theServerDepositsARequestAndThePhoneReadsItOnTheApprovalOrigin() {
        val service = f.open()
        val events = listOf(certificate(service), team())
        lateinit var created: RootRequestResponse
        testApplication {
            application { signerModule(service, ClientToken(TEST_TOKEN)) }
            val body = json.encodeToString(RootRequestBody.serializer(), RootRequestBody(events.map(EventDraft::of)))
            assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/root-requests") { contentType(ContentType.Application.Json); setBody(body) }.status)
            suspend fun post(text: String) = client.post("/v1/root-requests") {
                header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
                contentType(ContentType.Application.Json)
                setBody(text)
            }
            val response = post(body)
            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            created = json.decodeFromString(RootRequestResponse.serializer(), response.bodyAsText())
            assertEquals(setOf("id", "sha256", "expires_at"), json.parseToJsonElement(response.bodyAsText()).jsonObject.keys)
            assertEquals(RootRequests.sha256(events), created.sha256)
            assertEquals(HttpStatusCode.BadRequest, post("pas du json").status)
            assertEquals(HttpStatusCode.BadRequest, post("""{"events":[]}""").status)
            val many = json.encodeToString(RootRequestBody.serializer(), RootRequestBody(List(101) { EventDraft.of(events[1]) }))
            assertEquals(HttpStatusCode.BadRequest, post(many).status)
            assertEquals(HttpStatusCode.PayloadTooLarge, post("x".repeat(600 * 1024)).status)
            val refused = post(json.encodeToString(RootRequestBody.serializer(), RootRequestBody(listOf(EventDraft.of(edited(events[0], content = "x"))))))
            assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
            val answer = json.parseToJsonElement(refused.bodyAsText()).jsonObject
            assertEquals("refused", answer["status"]?.jsonPrimitive?.content)
            assertEquals(SigningRefusals.BAD_CONTENT, answer["code"]?.jsonPrimitive?.content)
            // The internal API never serves the request to read.
            assertEquals(HttpStatusCode.NotFound, client.get("/racine/demandes/${created.id}") { header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN") }.status)
        }
        testApplication {
            application { approvalModule(service, f.config()) }
            val page = client.get("/racine/demandes/${created.id}")
            assertEquals(HttpStatusCode.OK, page.status)
            assertEquals(
                "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
                page.headers["Content-Security-Policy"],
            )
            assertEquals("DENY", page.headers["X-Frame-Options"])
            assertEquals("nosniff", page.headers["X-Content-Type-Options"])
            assertEquals("no-referrer", page.headers["Referrer-Policy"])
            assertEquals("no-store", page.headers[HttpHeaders.CacheControl])
            val view = json.decodeFromString(RootRequestView.serializer(), page.bodyAsText())
            assertEquals(created.id, view.id)
            assertEquals(created.sha256, view.sha256)
            assertEquals(created.expiresAt, view.expiresAt)
            assertEquals(RootRequests.sha256(view.events.map { it.toUnsigned() }), view.sha256, "the phone recomputes the hash of the QR code")
            assertEquals(setOf("id", "created_at", "expires_at", "sha256", "events"), json.parseToJsonElement(page.bodyAsText()).jsonObject.keys)
            val unknown = client.get("/racine/demandes/${"0".repeat(32)}")
            assertEquals(HttpStatusCode.NotFound, unknown.status)
            assertEquals(SigningRefusals.UNKNOWN_REQUEST, json.parseToJsonElement(unknown.bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content)
            assertEquals("DENY", unknown.headers["X-Frame-Options"])
            assertEquals(HttpStatusCode.NotFound, client.get("/racine/demandes/x").status)
            // The approval port never takes a request in.
            val deposit = client.post("/v1/root-requests") {
                header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
                contentType(ContentType.Application.Json)
                setBody("""{"events":[]}""")
            }
            assertEquals(HttpStatusCode.NotFound, deposit.status)
        }
        f.clock.advance(86_400)
        testApplication {
            application { approvalModule(service, f.config()) }
            assertEquals(HttpStatusCode.NotFound, client.get("/racine/demandes/${created.id}").status, "expired")
        }
    }
}
