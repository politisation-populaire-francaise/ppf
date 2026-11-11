package org.politisationpopulairefrancaise.server.signing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.RootRequestBody
import org.politisationpopulairefrancaise.core.platform.RootRequestResponse
import org.politisationpopulairefrancaise.core.platform.RootRequests
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.platform.TeamList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The administration routes of the signing service as the back-office calls them (D-141). */
class SignerAdminClientTest {
    private val token = "token-of-the-tests-0123456789abcdef-xyz"
    private val root = IdentityKeys.generate()
    private val json = SigningProtocol.json
    private val requests = mutableListOf<HttpRequestData>()
    private val start = 1_793_000_000L

    private fun signer(answer: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>): HttpPlatformSigner = HttpPlatformSigner(
        PlatformSignerConfig("http://signer:8090", token, root.publicKey.hex),
        HttpClient(MockEngine { request ->
            requests += request
            val (status, body) = answer(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { expectSuccess = false },
    )

    private val certificate = KeyCertificate.unsigned(root.publicKey.hex, IdentityKeys.generate().publicKey.hex, listOf(SigningUse.EDITORIAL_VOIR), start, start + 86_400, start)

    @Test
    fun aRootRequestIsDepositedAndItsAnswerChecked() = runBlocking<Unit> {
        val events = listOf(certificate)
        var answer = RootRequestResponse(id = "0123456789abcdef0123456789abcdef", sha256 = RootRequests.sha256(events), expiresAt = start + 86_400)
        var status = HttpStatusCode.Created
        val signer = signer { status to json.encodeToString(RootRequestResponse.serializer(), answer) }
        assertEquals(root.publicKey.hex, signer.rootPublicKey)
        val created = assertIs<RootRequestResult.Created>(signer.rootRequest(events))
        assertEquals(answer.id, created.id)
        assertEquals(answer.sha256, created.sha256)
        val sent = requests.last()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("/v1/root-requests", sent.url.encodedPath)
        assertEquals("Bearer $token", sent.headers[HttpHeaders.Authorization])
        assertEquals(events, json.decodeFromString(RootRequestBody.serializer(), (sent.body as TextContent).text).events.map { it.toUnsigned() })
        // A hash of other events, or a malformed id: never put in a QR code.
        answer = answer.copy(sha256 = "0".repeat(64))
        assertEquals(RootRequestResult.Refused(HttpPlatformSigner.BAD_ANSWER_FROM_SIGNER), signer.rootRequest(events))
        answer = answer.copy(sha256 = RootRequests.sha256(events), id = "pas-un-identifiant")
        assertEquals(RootRequestResult.Refused(HttpPlatformSigner.BAD_ANSWER_FROM_SIGNER), signer.rootRequest(events))
        status = HttpStatusCode.UnprocessableEntity
        answer = RootRequestResponse(status = RootRequestResponse.REFUSED, code = SigningRefusals.WRONG_USES)
        assertEquals(RootRequestResult.Refused(SigningRefusals.WRONG_USES), signer.rootRequest(events))
        status = HttpStatusCode.Unauthorized
        assertEquals(RootRequestResult.Refused(HttpPlatformSigner.SIGNER_UNAUTHORIZED), signer.rootRequest(events))
        val broken = signer { HttpStatusCode.InternalServerError to "pas du json" }
        assertEquals(RootRequestResult.Refused(HttpPlatformSigner.SIGNER_UNAVAILABLE), broken.rootRequest(events))
    }

    @Test
    fun theTeamListAndTheJournalAreRead() = runBlocking<Unit> {
        val list = LocalEventSigner(root).sign(TeamList.unsigned(root.publicKey.hex, emptyList(), start, start + 86_400))
        var teamStatus = HttpStatusCode.OK
        val signer = signer { request ->
            when (request.url.encodedPath) {
                "/v1/team-list" -> teamStatus to if (teamStatus == HttpStatusCode.OK) list.toJson() else """{"status":"refused","code":"no_team_list"}"""
                "/v1/journal" -> HttpStatusCode.OK to """[{"seq":1,"at":10,"action":"service.started","use":null,"previous_hash":"${"0".repeat(64)}","hash":"${"a".repeat(64)}","futur":"ignoré"},""" +
                    """{"seq":2,"at":11,"action":"approval.recorded","use":"editorial.voir","event":"${"b".repeat(64)}","approvers":["alice"],"credential":"Y3JlZA","previous_hash":"${"a".repeat(64)}","hash":"${"c".repeat(64)}"}]"""
                "/v1/journal/head" -> HttpStatusCode.OK to """{"seq":2,"hash":"${"c".repeat(64)}"}"""
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        assertEquals(list, signer.teamList())
        teamStatus = HttpStatusCode.NotFound
        assertNull(signer.teamList())
        val lines = signer.journal(0, 1_000)!!
        assertEquals(listOf(1L, 2L), lines.map { it.seq })
        assertEquals(listOf("alice"), lines[1].approvers)
        assertEquals("Y3JlZA", lines[1].credential)
        assertEquals("after=0&limit=1000", requests.last().url.encodedQuery)
        assertEquals(SignerJournalHead(2, "c".repeat(64)), signer.journalHead())
        assertNull(signer.journal(-1, 10), "never a malformed query")
        assertNull(signer.journal(0, 1_001))
    }
}
