package org.politisationpopulairefrancaise.server.urne

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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.urne.Ballot
import org.politisationpopulairefrancaise.core.urne.BlindedRequest
import org.politisationpopulairefrancaise.core.urne.LibsodiumRistretto255
import org.politisationpopulairefrancaise.core.urne.Nip98
import org.politisationpopulairefrancaise.core.urne.TokenRequest
import org.politisationpopulairefrancaise.core.urne.TokenResponse
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.urne.UrneJournal
import org.politisationpopulairefrancaise.core.urne.UrneList
import org.politisationpopulairefrancaise.core.urne.VoprfBlinded
import org.politisationpopulairefrancaise.core.urne.VoprfMode
import org.politisationpopulairefrancaise.core.urne.Voprf
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ballot-box API from end to end (D-110, step 2): an account registers with a proof of work,
 * gets its tokens with NIP-98 for every open box, then votes anonymously, changes its vote, and
 * nothing it sends with its account ever reaches a ballot.
 */
class UrneApiTest {
    private val json = Json { ignoreUnknownKeys = false }
    private val group = runBlocking { LibsodiumRistretto255.load() }
    private val voprf = Voprf(group, VoprfMode.VOPRF)
    private val voter = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyTables() = UrneFixtures.reset()

    private fun ApplicationTestBuilder.start() {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), urne = UrneFixtures.CONFIG) }
    }

    private fun openBox(id: String, liveResults: Boolean = true): Urne {
        val now = Instant.now()
        return UrneFixtures.service().open(id, "30023:${"a".repeat(64)}:$id", listOf("pour", "contre"), now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), liveResults)
    }

    private suspend fun auth(url: String, method: String, body: String?, difficulty: Int) =
        Nip98.authorization(voter, "http://localhost$url", method, body?.encodeToByteArray(), Instant.now().epochSecond, difficulty)

    private suspend fun HttpClient.register(): HttpResponse =
        post("/api/v1/urnes/register") { header(HttpHeaders.Authorization, auth("/api/v1/urnes/register", "POST", null, UrneFixtures.CONFIG.registerDifficulty)) }

    private suspend fun HttpClient.status(): VoterStatus {
        val response = get("/api/v1/urnes/me") { header(HttpHeaders.Authorization, auth("/api/v1/urnes/me", "GET", null, 0)) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString(VoterStatus.serializer(), response.bodyAsText())
    }

    private suspend fun HttpClient.askTokens(body: String): HttpResponse = post("/api/v1/urnes/tokens") {
        header(HttpHeaders.Authorization, auth("/api/v1/urnes/tokens", "POST", body, UrneFixtures.CONFIG.tokenDifficulty))
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.boxes(): List<UrneInfo> =
        json.decodeFromString(UrneList.serializer(), get("/api/v1/urnes").bodyAsText()).urnes

    private suspend fun HttpClient.vote(ballot: Ballot): HttpResponse = post("/api/v1/ballots") {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(Ballot.serializer(), ballot))
    }

    private suspend fun HttpClient.journal(number: Long): UrneJournal =
        json.decodeFromString(UrneJournal.serializer(), get("/api/v1/urnes/$number/journal").bodyAsText())

    /** What a device does: blind a fresh y for each pending box, ask, check every proof, keep (y, token). */
    private suspend fun HttpClient.tokensFor(pending: List<Long>): Map<Long, Pair<ByteArray, ByteArray>> {
        val ys = pending.associateWith { ByteArray(32).also { SecureRandom().nextBytes(it) } }
        val blinded: Map<Long, VoprfBlinded> = ys.mapValues { voprf.blind(it.value) }
        val body = json.encodeToString(TokenRequest.serializer(), TokenRequest(pending.map { BlindedRequest(it, blinded.getValue(it).blindedElement.toHexString()) }))
        val response = askTokens(body)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val tokens = json.decodeFromString(TokenResponse.serializer(), response.bodyAsText()).tokens
        val keys = boxes().associateBy { it.number }
        return tokens.associate { issued ->
            val publicKey = keys.getValue(issued.urne).keys.getValue(issued.level).hexToByteArray()
            val output = voprf.finalize(ys.getValue(issued.urne), blinded.getValue(issued.urne), issued.evaluated.hexToByteArray(), issued.proof.hexToByteArray(), publicKey)
            issued.urne to (ys.getValue(issued.urne) to output)
        }
    }

    private fun ballot(urne: Long, y: ByteArray, token: ByteArray, choice: String, level: String = "non-verifie") =
        Ballot.withProofOfWork(urne, level, y.toHexString(), token.toHexString(), choice, UrneFixtures.CONFIG.ballotDifficulty)

    @Test
    fun anAccountRegistersGetsItsTokensAndVotesAnonymously() = testApplication {
        start()
        openBox("question-1")
        openBox("question-2")
        assertEquals(HttpStatusCode.Created, client.register().status)
        assertEquals(HttpStatusCode.OK, client.register().status, "registering twice changes nothing")
        val status = client.status()
        assertEquals("non-verifie", status.level)
        // Every open box, wanted or not (D-155: the demands stored by other tests may have theirs too).
        assertTrue(status.pending.size >= 2, "every open box, wanted or not")
        val tokens = client.tokensFor(status.pending)
        assertTrue(client.status().pending.isEmpty(), "one token per box and account, never a second one")
        val (number, pair) = tokens.entries.first()
        val (y, token) = pair
        val sent = client.vote(ballot(number, y, token, "pour"))
        assertEquals(HttpStatusCode.Accepted, sent.status, sent.bodyAsText())
        // The same ballot again with another choice replaces it.
        assertEquals(HttpStatusCode.Accepted, client.vote(ballot(number, y, token, "contre")).status)
        val journal = client.journal(number)
        assertEquals(1L, journal.ballots["non-verifie"])
        assertEquals(1L, journal.replacements)
        assertEquals(1L, journal.issued["non-verifie"])
        assertEquals(1L, journal.accounts["non-verifie"])
        assertEquals(mapOf("contre" to 1L), journal.results!!.citizens.counts)
        assertEquals(0L, journal.results!!.electedOfficials.ballots)
        // Withdrawn: no longer counted.
        assertEquals(HttpStatusCode.Accepted, client.vote(ballot(number, y, token, UrneInfo.WITHDRAWN)).status)
        assertEquals(0L, client.journal(number).results!!.citizens.ballots)
    }

    @Test
    fun forgedOrSloppyBallotsAreRefused() = testApplication {
        start()
        val box = openBox("question-refus")
        client.register()
        val (y, token) = client.tokensFor(client.status().pending).getValue(box.number)
        val forged = token.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertEquals(HttpStatusCode.Forbidden, client.vote(ballot(box.number, y, forged, "pour")).status)
        assertEquals(HttpStatusCode.Forbidden, client.vote(ballot(box.number, y, token, "pour", level = "verifie")).status, "a token is valid for its level only")
        assertEquals(HttpStatusCode.BadRequest, client.vote(ballot(box.number, y, token, "peut-etre")).status)
        val noWork = Ballot(box.number, "non-verifie", y.toHexString(), token.toHexString(), "pour", 0)
        if (UrneFixtures.CONFIG.ballotDifficulty > 0 && org.politisationpopulairefrancaise.core.urne.ProofOfWork.leadingZeroBits(noWork.powDigest()) < UrneFixtures.CONFIG.ballotDifficulty) {
            assertEquals(HttpStatusCode.BadRequest, client.vote(noWork).status)
        }
        assertEquals(HttpStatusCode.NotFound, client.vote(ballot(999, y, token, "pour")).status)
        assertEquals(HttpStatusCode.Accepted, client.vote(ballot(box.number, y, token, "pour")).status)
    }

    @Test
    fun tokenRequestsMustCoverThePendingBoxesInOrderAndCannotBeReplayed() = testApplication {
        start()
        openBox("b1")
        openBox("b2")
        client.register()
        val pending = client.status().pending
        // Asking for the second box alone (a chosen subset) is refused: the request would say which one we want.
        val y = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val skipFirst = json.encodeToString(TokenRequest.serializer(), TokenRequest(listOf(BlindedRequest(pending[1], voprf.blind(y).blindedElement.toHexString()))))
        assertEquals(HttpStatusCode.Conflict, client.askTokens(skipFirst).status)
        // A prefix is fine; the same body asked again gets the same answer, not a second token.
        val first = json.encodeToString(TokenRequest.serializer(), TokenRequest(listOf(BlindedRequest(pending[0], voprf.blind(y).blindedElement.toHexString()))))
        val once = client.askTokens(first)
        assertEquals(HttpStatusCode.OK, once.status)
        val twice = client.askTokens(first)
        assertEquals(once.bodyAsText(), twice.bodyAsText())
        assertEquals(pending.drop(1), client.status().pending)
        // The very same authorization header cannot be used twice.
        val header = auth("/api/v1/urnes/me", "GET", null, 0)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/urnes/me") { header(HttpHeaders.Authorization, header) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/urnes/me") { header(HttpHeaders.Authorization, header) }.status)
        // Without authorization, or for another URL.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/urnes/me").status)
        val other = auth("/api/v1/urnes/register", "GET", null, 0)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/urnes/me") { header(HttpHeaders.Authorization, other) }.status)
    }

    @Test
    fun registrationNeedsItsProofOfWork() = testApplication {
        start()
        val weak = Nip98.authorization(voter, "http://localhost/api/v1/urnes/register", "POST", null, Instant.now().epochSecond, 0)
        val response = client.post("/api/v1/urnes/register") { header(HttpHeaders.Authorization, weak) }
        if (UrneFixtures.CONFIG.registerDifficulty > 0) assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(HttpStatusCode.Created, client.register().status)
    }

    @Test
    fun theListGivesTheSamePublicKeysToEveryone() = testApplication {
        start()
        val box = openBox("cles")
        val first = client.boxes().single { it.number == box.number }
        val second = client.boxes().single { it.number == box.number }
        assertEquals(first.keys, second.keys)
        assertEquals(setOf("non-verifie", "verifie", "elu"), first.keys.keys)
        assertEquals(3, first.keys.values.toSet().size, "one key per level")
        assertEquals(HttpStatusCode.Conflict, client.get("/api/v1/urnes/${box.number}/closing").status, "nothing secret while it is open")
    }
}
