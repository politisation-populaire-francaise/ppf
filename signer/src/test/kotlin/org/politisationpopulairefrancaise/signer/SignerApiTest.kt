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
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.PlatformTrust
import org.politisationpopulairefrancaise.core.platform.SignRequest
import org.politisationpopulairefrancaise.core.platform.SignResponse
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The service started in-process behind its real HTTP routes, as the server will call it. */
class SignerApiTest {
    private val f = SignerFixture()
    private val json = SigningProtocol.json

    private fun withSigner(block: suspend ApplicationTestBuilder.(SignerService) -> Unit) = testApplication {
        val service = f.open()
        application { signerModule(service, ClientToken(TEST_TOKEN)) }
        block(service)
    }

    private suspend fun ApplicationTestBuilder.authedGet(path: String) = client.get(path) { header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN") }

    private suspend fun ApplicationTestBuilder.authedPost(path: String, body: String) = client.post(path) {
        header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun encode(request: SignRequest) = json.encodeToString(SignRequest.serializer(), request)

    @Test
    fun everyRouteButHealthNeedsTheTokenOfTheServer() = withSigner {
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        for (path in listOf("/v1/keys", "/v1/certificates", "/v1/team-list", "/v1/journal", "/v1/journal/head")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status, path)
            assertEquals(HttpStatusCode.Unauthorized, client.get(path) { header(HttpHeaders.Authorization, "Bearer ${TEST_TOKEN}x") }.status, path)
            assertEquals(HttpStatusCode.Unauthorized, client.get(path) { header(HttpHeaders.Authorization, TEST_TOKEN) }.status, path)
        }
        val refused = client.post("/v1/sign") { contentType(ContentType.Application.Json); setBody("{}") }
        assertEquals(HttpStatusCode.Unauthorized, refused.status)
        assertEquals(SigningRefusals.UNAUTHORIZED, json.parseToJsonElement(refused.bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content)
        assertEquals("no-store", authedGet("/v1/keys").headers[HttpHeaders.CacheControl])
    }

    @Test
    fun theServerCertifiesLoadsTheTeamAndGetsSignatures() = withSigner { service ->
        val keys = json.decodeFromString(KeysResponse.serializer(), authedGet("/v1/keys").bodyAsText())
        assertTrue(keys.rootConfigured)
        assertTrue(keys.keys.all { it.state == WorkingKeyStates.AWAITING_CERTIFICATION && !it.current })
        val voir = keys.keys.single { it.use == SigningUse.EDITORIAL_VOIR.id }.pubkey
        val certificate = f.certificate(voir, listOf(SigningUse.EDITORIAL_VOIR))
        val loaded = authedPost("/v1/certificates", "[${certificate.toJson()}]")
        assertEquals(HttpStatusCode.OK, loaded.status)
        assertEquals("stored", json.parseToJsonElement(loaded.bodyAsText()).jsonArray.single().jsonObject["status"]?.jsonPrimitive?.content)
        assertEquals("duplicate", json.parseToJsonElement(authedPost("/v1/certificates", certificate.toJson()).bodyAsText()).jsonArray.single().jsonObject["status"]?.jsonPrimitive?.content)
        val team = authedPost("/v1/team-list", f.teamList(listOf(f.alice.member(), f.bob.member())).toJson())
        assertEquals(HttpStatusCode.OK, team.status)

        val event = f.editorial(voir)
        val signedResponse = authedPost("/v1/sign", encode(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, event)))))
        assertEquals(HttpStatusCode.OK, signedResponse.status)
        val signed = json.decodeFromString(SignResponse.serializer(), signedResponse.bodyAsText())
        assertEquals(SignResponse.SIGNED, signed.status)
        val trust = PlatformTrust.of(f.rootHex, json.decodeFromString(ListSerializer(SignedEvent.serializer()), authedGet("/v1/certificates").bodyAsText()))
        assertTrue(trust.isPlatformContent(signed.event!!, SigningUse.EDITORIAL_VOIR))

        val again = authedPost("/v1/sign", encode(f.request(SigningUse.EDITORIAL_VOIR, event, listOf(f.alice.approve(SigningUse.EDITORIAL_VOIR, event)))))
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals(SigningRefusals.ALREADY_SIGNED, json.decodeFromString(SignResponse.serializer(), again.bodyAsText()).code)

        val other = f.editorial(voir, title = "[EXEMPLE] Autre")
        val refused = authedPost("/v1/sign", encode(f.request(SigningUse.EDITORIAL_VOIR, other)))
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertEquals(SigningRefusals.APPROVALS_MISSING, json.decodeFromString(SignResponse.serializer(), refused.bodyAsText()).code)

        val head = json.parseToJsonElement(authedGet("/v1/journal/head").bodyAsText()).jsonObject
        assertEquals(service.journalHead().seq.toString(), head["seq"]?.jsonPrimitive?.content)
        val lines = json.parseToJsonElement(authedGet("/v1/journal?after=0&limit=1000").bodyAsText()).jsonArray
        assertEquals(service.journalHead().seq.toInt(), lines.size)
        assertEquals(HttpStatusCode.BadRequest, authedGet("/v1/journal?limit=0").status)
        assertEquals(HttpStatusCode.OK, authedGet("/v1/team-list").status)
    }

    @Test
    fun malformedOrOversizedRequestsAreRefusedUnread() = withSigner {
        assertEquals(HttpStatusCode.BadRequest, authedPost("/v1/sign", "pas du json").status)
        assertEquals(HttpStatusCode.BadRequest, authedPost("/v1/team-list", "{}").status)
        assertEquals(HttpStatusCode.BadRequest, authedPost("/v1/certificates", "42").status)
        assertEquals(HttpStatusCode.PayloadTooLarge, authedPost("/v1/sign", "x".repeat(200 * 1024)).status)
        assertEquals(HttpStatusCode.NotFound, authedGet("/v1/team-list").status)
        val noTeam = authedPost("/v1/team-list", f.teamList(listOf(f.alice.member())).toJson().replace("\"kind\":37241", "\"kind\":37240"))
        assertEquals(HttpStatusCode.UnprocessableEntity, noTeam.status)
    }
}
