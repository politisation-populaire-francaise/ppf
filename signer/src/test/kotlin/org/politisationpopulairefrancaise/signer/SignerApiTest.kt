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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.platform.ApprovalChallenge
import org.politisationpopulairefrancaise.core.platform.DraftResponse
import org.politisationpopulairefrancaise.core.platform.DraftStatusResponse
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.SignRequest
import org.politisationpopulairefrancaise.core.platform.SignResponse
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The service started in-process behind its real HTTP routes: the internal API as the server calls it, the approval pages as a browser opens them. */
class SignerApiTest {
    private val f = SignerFixture()
    private val json = SigningProtocol.json

    private fun withSigner(service: SignerService = f.open(), block: suspend ApplicationTestBuilder.(SignerService) -> Unit) = testApplication {
        application { signerModule(service, ClientToken(TEST_TOKEN)) }
        block(service)
    }

    private fun withApprovals(service: SignerService, block: suspend ApplicationTestBuilder.(SignerService) -> Unit) = testApplication {
        application { approvalModule(service, f.config()) }
        block(service)
    }

    private suspend fun ApplicationTestBuilder.authedGet(path: String) = client.get(path) { header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN") }

    private suspend fun ApplicationTestBuilder.authedPost(path: String, body: String) = client.post(path) {
        header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) = client.post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun encode(request: SignRequest) = json.encodeToString(SignRequest.serializer(), request)

    @Test
    fun everyRouteButHealthNeedsTheTokenOfTheServer() = withSigner {
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        for (path in listOf("/v1/keys", "/v1/certificates", "/v1/team-list", "/v1/journal", "/v1/journal/head", "/v1/drafts/editorial.voir/${"a".repeat(64)}")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status, path)
            assertEquals(HttpStatusCode.Unauthorized, client.get(path) { header(HttpHeaders.Authorization, "Bearer ${TEST_TOKEN}x") }.status, path)
            assertEquals(HttpStatusCode.Unauthorized, client.get(path) { header(HttpHeaders.Authorization, TEST_TOKEN) }.status, path)
        }
        for (path in listOf("/v1/sign", "/v1/drafts", "/v1/certificates", "/v1/team-list")) {
            val refused = client.post(path) { contentType(ContentType.Application.Json); setBody("{}") }
            assertEquals(HttpStatusCode.Unauthorized, refused.status, path)
            assertEquals(SigningRefusals.UNAUTHORIZED, json.parseToJsonElement(refused.bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content)
        }
        assertEquals("no-store", authedGet("/v1/keys").headers[HttpHeaders.CacheControl])
    }

    @Test
    fun theServerSubmitsADraftTheTeamApprovesOnTheServicePageThenTheServerGetsTheSignature() {
        val service = f.open()
        lateinit var voir: String
        lateinit var event: UnsignedEvent
        withSigner(service) {
            val keys = json.decodeFromString(KeysResponse.serializer(), authedGet("/v1/keys").bodyAsText())
            assertTrue(keys.rootConfigured)
            assertTrue(keys.keys.all { it.state == WorkingKeyStates.AWAITING_CERTIFICATION && !it.current })
            voir = keys.keys.single { it.use == SigningUse.EDITORIAL_VOIR.id }.pubkey
            val certificate = f.certificate(voir, listOf(SigningUse.EDITORIAL_VOIR))
            val loaded = authedPost("/v1/certificates", "[${certificate.toJson()}]")
            assertEquals(HttpStatusCode.OK, loaded.status)
            assertEquals("stored", json.parseToJsonElement(loaded.bodyAsText()).jsonArray.single().jsonObject["status"]?.jsonPrimitive?.content)
            assertEquals("duplicate", json.parseToJsonElement(authedPost("/v1/certificates", certificate.toJson()).bodyAsText()).jsonArray.single().jsonObject["status"]?.jsonPrimitive?.content)
            assertEquals(HttpStatusCode.OK, authedPost("/v1/team-list", f.teamList(listOf(f.alice.member(), f.bob.member())).toJson()).status)
            val listed = json.decodeFromString(KeysResponse.serializer(), authedGet("/v1/keys").bodyAsText())
            assertEquals(f.now() + 90 * DAY, listed.teamListNotAfter)

            event = f.editorial(voir)
            val created = authedPost("/v1/drafts", json.encodeToString(org.politisationpopulairefrancaise.core.platform.DraftRequest.serializer(), f.draft(SigningUse.EDITORIAL_VOIR, event)))
            assertEquals(HttpStatusCode.Created, created.status)
            val draft = json.decodeFromString(DraftResponse.serializer(), created.bodyAsText())
            assertEquals(DraftResponse.SUBMITTED, draft.status)
            assertEquals(event.idHex(), draft.draft)
            assertEquals("$TEST_ORIGIN/approbation/editorial.voir/${event.idHex()}", draft.approvalUrl)
            val again = authedPost("/v1/drafts", json.encodeToString(org.politisationpopulairefrancaise.core.platform.DraftRequest.serializer(), f.draft(SigningUse.EDITORIAL_VOIR, event)))
            assertEquals(HttpStatusCode.OK, again.status)
            val conflict = authedPost("/v1/drafts", json.encodeToString(org.politisationpopulairefrancaise.core.platform.DraftRequest.serializer(), f.draft(SigningUse.EDITORIAL_VOIR, event, double = true, author = org.politisationpopulairefrancaise.core.platform.DraftAuthor("dan"))))
            assertEquals(HttpStatusCode.Conflict, conflict.status)
            assertEquals(SigningRefusals.DRAFT_CONFLICT, json.decodeFromString(DraftResponse.serializer(), conflict.bodyAsText()).code)
            val pending = json.decodeFromString(DraftStatusResponse.serializer(), authedGet("/v1/drafts/editorial.voir/${event.idHex()}").bodyAsText())
            assertEquals(0, pending.approvals.size)
            assertFalse(pending.ready)
            assertEquals(HttpStatusCode.NotFound, authedGet("/v1/drafts/editorial.voir/${"a".repeat(64)}").status)
            val missing = authedPost("/v1/sign", encode(f.request(SigningUse.EDITORIAL_VOIR, event)))
            assertEquals(HttpStatusCode.UnprocessableEntity, missing.status)
            assertEquals(SigningRefusals.APPROVALS_MISSING, json.decodeFromString(SignResponse.serializer(), missing.bodyAsText()).code)
        }
        withApprovals(service) {
            val page = client.get("/approbation/editorial.voir/${event.idHex()}")
            assertEquals(HttpStatusCode.OK, page.status)
            val options = json.parseToJsonElement(postJson("/approbation/editorial.voir/${event.idHex()}/options", "{}").bodyAsText()).jsonObject["publicKey"]!!.jsonObject
            assertEquals(b64url(ApprovalChallenge.of(SigningUse.EDITORIAL_VOIR, event.idHex())), options["challenge"]?.jsonPrimitive?.content)
            assertEquals(TEST_RP_ID, options["rpId"]?.jsonPrimitive?.content)
            assertEquals("required", options["userVerification"]?.jsonPrimitive?.content)
            assertEquals(
                setOf(f.alice.passkey.credential.id, f.bob.passkey.credential.id),
                options["allowCredentials"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet(),
            )
            val assertion = f.alice.approve(SigningUse.EDITORIAL_VOIR, event)
            val approved = postJson("/approbation/editorial.voir/${event.idHex()}", json.encodeToString(PasskeyAssertion.serializer(), assertion))
            assertEquals(HttpStatusCode.OK, approved.status)
            val answer = json.parseToJsonElement(approved.bodyAsText()).jsonObject
            assertEquals("approved", answer["status"]?.jsonPrimitive?.content)
            assertEquals("true", answer["ready"]?.jsonPrimitive?.content)
            val twice = postJson("/approbation/editorial.voir/${event.idHex()}", json.encodeToString(PasskeyAssertion.serializer(), f.alice.approve(SigningUse.EDITORIAL_VOIR, event)))
            assertEquals(HttpStatusCode.Conflict, twice.status)
            assertEquals(SigningRefusals.ALREADY_APPROVED, json.parseToJsonElement(twice.bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content)
            assertTrue("Approbation : 1 sur 1" in client.get("/approbation/editorial.voir/${event.idHex()}").bodyAsText())
        }
        withSigner(service) {
            val ready = json.decodeFromString(DraftStatusResponse.serializer(), authedGet("/v1/drafts/editorial.voir/${event.idHex()}").bodyAsText())
            assertEquals(listOf("alice"), ready.approvals.map { it.account })
            assertTrue(ready.ready)
            // The form before version 1.2, with an approval in the body, is refused.
            val withApproval = """{"use":"editorial.voir","event":${json.encodeToString(org.politisationpopulairefrancaise.core.platform.EventDraft.serializer(), org.politisationpopulairefrancaise.core.platform.EventDraft.of(event))},"approval":{"assertions":[]}}"""
            val refused = authedPost("/v1/sign", withApproval)
            assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
            assertEquals(SigningRefusals.UNEXPECTED_APPROVAL, json.decodeFromString(SignResponse.serializer(), refused.bodyAsText()).code)
            val signedResponse = authedPost("/v1/sign", encode(f.request(SigningUse.EDITORIAL_VOIR, event)))
            assertEquals(HttpStatusCode.OK, signedResponse.status)
            val signed = json.decodeFromString(SignResponse.serializer(), signedResponse.bodyAsText())
            assertEquals(SignResponse.SIGNED, signed.status)
            val trust = PlatformTrust.of(f.rootHex, json.decodeFromString(ListSerializer(SignedEvent.serializer()), authedGet("/v1/certificates").bodyAsText()))
            assertTrue(trust.isPlatformContent(signed.event!!, SigningUse.EDITORIAL_VOIR))
            val again = authedPost("/v1/sign", encode(f.request(SigningUse.EDITORIAL_VOIR, event)))
            assertEquals(HttpStatusCode.Conflict, again.status)
            assertEquals(SigningRefusals.ALREADY_SIGNED, json.decodeFromString(SignResponse.serializer(), again.bodyAsText()).code)
            assertEquals(HttpStatusCode.Conflict, authedGet("/v1/drafts/editorial.voir/${event.idHex()}").status)

            val head = json.parseToJsonElement(authedGet("/v1/journal/head").bodyAsText()).jsonObject
            assertEquals(service.journalHead().seq.toString(), head["seq"]?.jsonPrimitive?.content)
            val lines = json.parseToJsonElement(authedGet("/v1/journal?after=0&limit=1000").bodyAsText()).jsonArray
            assertEquals(service.journalHead().seq.toInt(), lines.size)
            assertEquals(HttpStatusCode.BadRequest, authedGet("/v1/journal?limit=0").status)
            assertEquals(HttpStatusCode.OK, authedGet("/v1/team-list").status)
        }
    }

    @Test
    fun theApprovalPageShowsEverythingEscapedUnderAStrictPolicy() {
        val service = f.ready()
        val key = f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!
        val event = UnsignedEvent(
            key, f.now(), EventKinds.LONG_FORM_ARTICLE,
            listOf(listOf("d", "exemple"), listOf("title", "[EXEMPLE] <b>Titre</b>"), listOf("ppf-origin", "human"), listOf("a", "30023:${"b".repeat(64)}:personne", "", "personne")),
            "[EXEMPLE] Ligne un\n<script>alert('x')</script> & \"guillemets\"",
        )
        f.submit(service, SigningUse.EDITORIAL_VOIR, event, double = true, author = org.politisationpopulairefrancaise.core.platform.DraftAuthor("dan"))
        withApprovals(service) {
            val response = client.get("/approbation/editorial.voir/${event.idHex()}")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
                response.headers["Content-Security-Policy"],
            )
            assertEquals("DENY", response.headers["X-Frame-Options"])
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            val html = response.bodyAsText()
            assertTrue("&lt;script&gt;alert(" in html)
            assertFalse("<script>alert" in html)
            assertTrue("&lt;b&gt;Titre&lt;/b&gt;" in html)
            assertTrue(event.idHex() in html && key in html)
            assertTrue("30023:${"b".repeat(64)}:personne" in html)
            assertTrue("Double validation : 0 sur 2" in html)
            assertTrue("Brique sourcée de l'espace « Voir »" in html || "Brique sourcée de l&#39;espace « Voir »" in html)
            assertTrue("lang=\"fr\"" in html)
            // No inline script or style: one script and one style sheet, both from the service.
            assertEquals(1, Regex("<script").findAll(html).count())
            assertTrue("<script src=\"/approbation/static/approbation.js\"" in html)
            assertFalse("style=" in html || "<style" in html || "onclick" in html)
            val script = client.get("/approbation/static/approbation.js")
            assertEquals(HttpStatusCode.OK, script.status)
            assertTrue(script.headers[HttpHeaders.ContentType]!!.startsWith("text/javascript"))
            assertTrue("navigator.credentials.get" in script.bodyAsText())
            assertTrue(client.get("/approbation/static/approbation.css").headers[HttpHeaders.ContentType]!!.startsWith("text/css"))
            // Unknown drafts, automatic uses and malformed addresses: a page that says so.
            assertEquals(HttpStatusCode.NotFound, client.get("/approbation/editorial.voir/${"a".repeat(64)}").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/approbation/opendata.index/${event.idHex()}").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/approbation/editorial.voir/pas-un-identifiant").status)
            // Posts are JSON only, bounded.
            val form = client.post("/approbation/editorial.voir/${event.idHex()}") { contentType(ContentType.Application.FormUrlEncoded); setBody("a=b") }
            assertEquals(HttpStatusCode.UnsupportedMediaType, form.status)
            assertEquals(HttpStatusCode.PayloadTooLarge, postJson("/approbation/editorial.voir/${event.idHex()}", "x".repeat(70 * 1024)).status)
            assertEquals(HttpStatusCode.UnprocessableEntity, postJson("/approbation/editorial.voir/${event.idHex()}", "pas du json").status)
        }
    }

    @Test
    fun theOwnerReachesTheServiceDirectlyWithRootSignedEvents() {
        val service = f.open()
        val key = f.key(service, SigningUse.OPENDATA_INDEX, WorkingKeyStates.AWAITING_CERTIFICATION)
        withApprovals(service) {
            val certificate = f.certificate(key, listOf(SigningUse.OPENDATA_INDEX))
            val loaded = postJson("/racine/certificats", "[${certificate.toJson()}]")
            assertEquals(HttpStatusCode.OK, loaded.status)
            assertEquals("stored", json.parseToJsonElement(loaded.bodyAsText()).jsonArray.single().jsonObject["status"]?.jsonPrimitive?.content)
            assertEquals(HttpStatusCode.OK, postJson("/racine/liste-equipe", f.teamList(listOf(f.alice.member())).toJson()).status)
            val impostor = org.politisationpopulairefrancaise.core.identity.IdentityKeys.generate()
            val forged = signWith(impostor, org.politisationpopulairefrancaise.core.platform.TeamList.unsigned(impostor.publicKey.hex, listOf(f.bob.member()), f.now() + 10, f.now() + DAY))
            assertEquals(HttpStatusCode.UnprocessableEntity, postJson("/racine/liste-equipe", forged.toJson()).status)
            assertEquals(HttpStatusCode.PayloadTooLarge, postJson("/racine/certificats", "x".repeat(600 * 1024)).status)
        }
        assertEquals(WorkingKeyStates.ACTIVE, service.keys().keys.single { it.pubkey == key }.state)
        assertEquals(setOf("alice"), service.teamListEvent()?.let { org.politisationpopulairefrancaise.core.platform.TeamList.read(it, f.rootHex) }?.members?.keys)
    }

    @Test
    fun theTwoPortsShareNoRoute() {
        val service = f.ready()
        val event = f.editorial(f.currentKey(service, SigningUse.EDITORIAL_VOIR)!!)
        f.submit(service, SigningUse.EDITORIAL_VOIR, event)
        withApprovals(service) {
            for (path in listOf("/v1/keys", "/v1/certificates", "/v1/team-list", "/v1/journal", "/v1/journal/head", "/v1/drafts/editorial.voir/${event.idHex()}", "/health")) {
                assertEquals(HttpStatusCode.NotFound, client.get(path) { header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN") }.status, path)
            }
            for (path in listOf("/v1/sign", "/v1/drafts", "/v1/certificates", "/v1/team-list")) {
                assertEquals(HttpStatusCode.NotFound, client.post(path) { header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN"); contentType(ContentType.Application.Json); setBody("{}") }.status, path)
            }
        }
        withSigner(service) {
            assertEquals(HttpStatusCode.NotFound, authedGet("/approbation/editorial.voir/${event.idHex()}").status)
            assertEquals(HttpStatusCode.NotFound, authedGet("/approbation/static/approbation.js").status)
            for (path in listOf("/approbation/editorial.voir/${event.idHex()}", "/approbation/editorial.voir/${event.idHex()}/options", "/racine/certificats", "/racine/liste-equipe")) {
                assertEquals(HttpStatusCode.NotFound, authedPost(path, "{}").status, path)
            }
        }
    }

    @Test
    fun malformedOrOversizedRequestsAreRefusedUnread() = withSigner {
        assertEquals(HttpStatusCode.BadRequest, authedPost("/v1/sign", "pas du json").status)
        assertEquals(HttpStatusCode.BadRequest, authedPost("/v1/drafts", "pas du json").status)
        assertEquals(HttpStatusCode.BadRequest, authedPost("/v1/team-list", "{}").status)
        assertEquals(HttpStatusCode.BadRequest, authedPost("/v1/certificates", "42").status)
        assertEquals(HttpStatusCode.PayloadTooLarge, authedPost("/v1/sign", "x".repeat(200 * 1024)).status)
        assertEquals(HttpStatusCode.PayloadTooLarge, authedPost("/v1/drafts", "x".repeat(200 * 1024)).status)
        assertEquals(HttpStatusCode.NotFound, authedGet("/v1/team-list").status)
        val noTeam = authedPost("/v1/team-list", f.teamList(listOf(f.alice.member())).toJson().replace("\"kind\":37241", "\"kind\":37240"))
        assertEquals(HttpStatusCode.UnprocessableEntity, noTeam.status)
    }
}
