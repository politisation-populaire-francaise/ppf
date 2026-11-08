package org.politisationpopulairefrancaise.server.signing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.DraftRequest
import org.politisationpopulairefrancaise.core.platform.DraftResponse
import org.politisationpopulairefrancaise.core.platform.DraftStatusResponse
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.SignRequest
import org.politisationpopulairefrancaise.core.platform.SignResponse
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.WorkingKeyInfo
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformSignerTest {
    private val token = "token-of-the-tests-0123456789abcdef-xyz"
    private val root = IdentityKeys.generate()
    private val config = PlatformSignerConfig("http://signer:8090", token, root.publicKey.hex)
    private val working = IdentityKeys.generate()
    private val requests = mutableListOf<HttpRequestData>()
    private val json = SigningProtocol.json
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val start = 1_793_000_000L

    private fun index(createdAt: Long = start + 100) =
        UnsignedEvent(working.publicKey.hex, createdAt, EventKinds.APP_DATA, listOf(listOf("d", "ppf:parlement-index")), "{}")

    private fun editorial() = UnsignedEvent(working.publicKey.hex, start + 100, EventKinds.LONG_FORM_ARTICLE, listOf(listOf("d", "x"), listOf("title", "[EXEMPLE]")), "[EXEMPLE]")

    private fun certificate(use: SigningUse, key: IdentityKeys = working): SignedEvent {
        val unsigned = KeyCertificate.unsigned(root.publicKey.hex, key.publicKey.hex, listOf(use), start, start + 90 * 86_400, start)
        val id = unsigned.id()
        return SignedEvent(id.toHexString(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, root.sign(id).toHexString())
    }

    private fun certificates(vararg events: SignedEvent): String = json.encodeToString(ListSerializer(SignedEvent.serializer()), events.toList())

    /** A clock the tests move by hand. */
    private class MutableClock(var now: Instant = Instant.parse("2026-11-08T10:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private fun signer(clock: Clock = Clock.systemUTC(), answer: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>): HttpPlatformSigner =
        HttpPlatformSigner(
            config,
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val (status, body) = answer(request)
                    respond(body, status, jsonHeaders)
                },
            ) { expectSuccess = false },
            clock,
        )

    /** A service that signs whatever it is asked with [key] and holds [held] as certificates. */
    private fun signingService(key: IdentityKeys = working, held: () -> List<SignedEvent>) = signer { request ->
        when (request.url.encodedPath) {
            "/v1/certificates" -> HttpStatusCode.OK to certificates(*held().toTypedArray())
            else -> {
                val sent = json.decodeFromString(SignRequest.serializer(), (request.body as TextContent).text)
                val event = LocalEventSigner(key).sign(sent.event.toUnsigned())
                HttpStatusCode.OK to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, event))
            }
        }
    }

    @Test
    fun aSignedEventIsCheckedBeforeBeingReturned() = runBlocking<Unit> {
        val signer = signingService { listOf(certificate(SigningUse.EDITORIAL_VOIR)) }
        val ticket = signer.request(editorial(), SigningUse.EDITORIAL_VOIR)
        val status = assertIs<SigningStatus.Signed>(signer.status(ticket))
        assertEquals(editorial().idHex(), status.event.id)
        val sent = requests.first { it.url.encodedPath == "/v1/sign" }
        assertEquals("Bearer $token", sent.headers[HttpHeaders.Authorization])
        val body = (sent.body as TextContent).text
        assertEquals(SigningUse.EDITORIAL_VOIR.id, json.decodeFromString(SignRequest.serializer(), body).use)
        assertFalse("approval" in body, "the server never carries an approval")
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.UNKNOWN_TICKET), signer.status(SigningTicket("autre")))
        // The certificates are kept: a second signature does not fetch them again.
        val before = requests.count { it.url.encodedPath == "/v1/certificates" }
        assertIs<SigningStatus.Signed>(signer.status(signer.request(editorial().copy(content = "[EXEMPLE] 2"), SigningUse.EDITORIAL_VOIR)))
        assertEquals(before, requests.count { it.url.encodedPath == "/v1/certificates" })
    }

    @Test
    fun anEventWhoseKeyTheRootDoesNotCertifyForTheUseIsRefused() = runBlocking<Unit> {
        // Certified for another use only.
        val otherUse = signingService { listOf(certificate(SigningUse.EDITORIAL_COMPRENDRE)) }
        val refused = assertIs<SigningStatus.Refused>(otherUse.status(otherUse.request(editorial(), SigningUse.EDITORIAL_VOIR)))
        assertEquals(HttpPlatformSigner.UNTRUSTED_KEY_FROM_SIGNER, refused.code)
        assertEquals("not_certified_for_use", refused.detail)
        // Not certified at all, or certified by another root.
        val stranger = IdentityKeys.generate()
        val forged = LocalEventSigner(stranger).sign(KeyCertificate.unsigned(stranger.publicKey.hex, working.publicKey.hex, listOf(SigningUse.OPENDATA_INDEX), start, start + 86_400, start))
        val unknown = signingService { listOf(forged) }
        assertEquals(HttpPlatformSigner.UNTRUSTED_KEY_FROM_SIGNER, assertIs<SigningStatus.Refused>(unknown.status(unknown.request(index(), SigningUse.OPENDATA_INDEX))).code)
        // Without a root on this server, nothing signed is accepted.
        val noRoot = HttpPlatformSigner(PlatformSignerConfig("http://signer:8090", token, null), HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/v1/certificates") {
                respond(certificates(certificate(SigningUse.OPENDATA_INDEX)), HttpStatusCode.OK, jsonHeaders)
            } else {
                val sent = json.decodeFromString(SignRequest.serializer(), (request.body as TextContent).text)
                respond(json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, LocalEventSigner(working).sign(sent.event.toUnsigned()))), HttpStatusCode.OK, jsonHeaders)
            }
        }) { expectSuccess = false })
        assertEquals(HttpPlatformSigner.UNTRUSTED_KEY_FROM_SIGNER, assertIs<SigningStatus.Refused>(noRoot.status(noRoot.request(index(), SigningUse.OPENDATA_INDEX))).code)
    }

    @Test
    fun aNewlyCertifiedKeyIsLearntAndARevocationIsNeverForgotten() = runBlocking<Unit> {
        val next = IdentityKeys.generate()
        var held = listOf(certificate(SigningUse.OPENDATA_INDEX))
        var signingKey = working
        val clock = MutableClock()
        val signer = signer(clock) { request ->
            when (request.url.encodedPath) {
                "/v1/certificates" -> HttpStatusCode.OK to certificates(*held.toTypedArray())
                else -> {
                    val sent = json.decodeFromString(SignRequest.serializer(), (request.body as TextContent).text)
                    HttpStatusCode.OK to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, LocalEventSigner(signingKey).sign(sent.event.toUnsigned())))
                }
            }
        }
        assertIs<SigningStatus.Signed>(signer.status(signer.request(index(), SigningUse.OPENDATA_INDEX)))
        // The next key, certified after the first fetch: learnt by fetching again.
        held = listOf(certificate(SigningUse.OPENDATA_INDEX, next))
        signingKey = next
        assertIs<SigningStatus.Signed>(signer.status(signer.request(index(start + 200).copy(pubkey = next.publicKey.hex), SigningUse.OPENDATA_INDEX)))
        // The first key revoked, then the service serving an old list without the revocation: still revoked.
        val revocation = LocalEventSigner(root).sign(
            KeyCertificate.unsigned(root.publicKey.hex, working.publicKey.hex, listOf(SigningUse.OPENDATA_INDEX), start, start + 90 * 86_400, start + 300, org.politisationpopulairefrancaise.core.platform.KeyStatus.REVOKED, start),
        )
        held = listOf(revocation)
        signingKey = working
        // The certificates are kept 10 minutes, then fetched again.
        clock.now = clock.now.plusSeconds(601)
        assertEquals(HttpPlatformSigner.UNTRUSTED_KEY_FROM_SIGNER, assertIs<SigningStatus.Refused>(signer.status(signer.request(index(start + 300), SigningUse.OPENDATA_INDEX))).code)
        held = listOf(certificate(SigningUse.OPENDATA_INDEX))
        clock.now = clock.now.plusSeconds(601)
        assertEquals(HttpPlatformSigner.UNTRUSTED_KEY_FROM_SIGNER, assertIs<SigningStatus.Refused>(signer.status(signer.request(index(start + 400), SigningUse.OPENDATA_INDEX))).code)
    }

    @Test
    fun anotherEventOrABadSignatureFromTheServiceIsRefused() = runBlocking<Unit> {
        val signer = signer {
            val other = LocalEventSigner(working).sign(index(createdAt = 1))
            HttpStatusCode.OK to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, other))
        }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.BAD_SIGNATURE_FROM_SIGNER), signer.status(signer.request(index(), SigningUse.OPENDATA_INDEX)))
        val forged = signer {
            val event = LocalEventSigner(working).sign(index())
            HttpStatusCode.OK to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, event.copy(sig = "0".repeat(128))))
        }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.BAD_SIGNATURE_FROM_SIGNER), forged.status(forged.request(index(), SigningUse.OPENDATA_INDEX)))
    }

    @Test
    fun refusalsAndOutagesAreTold() = runBlocking<Unit> {
        val refusing = signer {
            HttpStatusCode.UnprocessableEntity to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.REFUSED, code = SigningRefusals.MISSING_TAG, detail = "title"))
        }
        assertEquals(SigningStatus.Refused(SigningRefusals.MISSING_TAG, "title"), refusing.status(refusing.request(index(), SigningUse.EDITORIAL_VOIR)))
        val unauthorized = signer { HttpStatusCode.Unauthorized to """{"status":"refused","code":"unauthorized"}""" }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.SIGNER_UNAUTHORIZED), unauthorized.status(unauthorized.request(index(), SigningUse.EDITORIAL_VOIR)))
        val broken = signer { throw java.io.IOException("connection refused") }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.SIGNER_UNAVAILABLE), broken.status(broken.request(index(), SigningUse.EDITORIAL_VOIR)))
        assertNull(broken.currentKey(SigningUse.EDITORIAL_VOIR))
        assertEquals(DraftSubmission.Refused(HttpPlatformSigner.SIGNER_UNAVAILABLE), broken.submitDraft(editorial(), SigningUse.EDITORIAL_VOIR))
        assertNull(broken.draftStatus(SigningUse.EDITORIAL_VOIR, editorial().idHex()))
        val nonsense = signer { HttpStatusCode.BadGateway to "<html>" }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.SIGNER_UNAVAILABLE), nonsense.status(nonsense.request(index(), SigningUse.EDITORIAL_VOIR)))
    }

    @Test
    fun aDraftIsSubmittedAndItsApprovalLinkChecked() = runBlocking<Unit> {
        val event = editorial()
        val url = "https://signature.equipe.preprod.example/approbation/editorial.voir/${event.idHex()}"
        var answer: DraftResponse = DraftResponse(DraftResponse.SUBMITTED, draft = event.idHex(), approvalUrl = url)
        val signer = signer { request ->
            when (request.url.encodedPath) {
                "/v1/drafts" -> HttpStatusCode.Created to json.encodeToString(DraftResponse.serializer(), answer)
                else -> HttpStatusCode.OK to json.encodeToString(
                    DraftStatusResponse.serializer(),
                    DraftStatusResponse("editorial.voir", event.idHex(), true, 2, emptyList(), false, true, start, start + 7 * 86_400, url),
                )
            }
        }
        val submitted = assertIs<DraftSubmission.Submitted>(signer.submitDraft(event, SigningUse.EDITORIAL_VOIR, doubleValidation = true, author = DraftAuthor(account = "dan")))
        assertEquals(url, submitted.approvalUrl)
        val sent = json.decodeFromString(DraftRequest.serializer(), (requests.last().body as TextContent).text)
        assertTrue(sent.doubleValidation)
        assertEquals("dan", sent.author?.account)
        assertEquals("Bearer $token", requests.last().headers[HttpHeaders.Authorization])
        assertEquals(2, signer.draftStatus(SigningUse.EDITORIAL_VOIR, event.idHex())?.required)
        assertEquals("/v1/drafts/editorial.voir/${event.idHex()}", requests.last().url.encodedPath)
        assertNull(signer.draftStatus(SigningUse.EDITORIAL_VOIR, "pas un identifiant"))
        // An approval link of another form, or for another event, is never handed to the back-office.
        for (bad in listOf("http://signature.example/approbation/editorial.voir/${event.idHex()}", "https://signature.example/autre/${event.idHex()}", "https://signature.example/approbation/editorial.voir/${"a".repeat(64)}", "javascript:alert(1)")) {
            answer = DraftResponse(DraftResponse.SUBMITTED, draft = event.idHex(), approvalUrl = bad)
            assertEquals(DraftSubmission.Refused(HttpPlatformSigner.BAD_ANSWER_FROM_SIGNER), signer.submitDraft(event, SigningUse.EDITORIAL_VOIR), bad)
        }
        answer = DraftResponse(DraftResponse.REFUSED, code = SigningRefusals.TEAM_LIST_EXPIRED)
        assertEquals(DraftSubmission.Refused(SigningRefusals.TEAM_LIST_EXPIRED), signer.submitDraft(event, SigningUse.EDITORIAL_VOIR))
    }

    @Test
    fun theCurrentKeyComesFromTheService() = runBlocking<Unit> {
        val keys = KeysResponse(
            rootConfigured = true,
            keys = listOf(
                WorkingKeyInfo(SigningUse.EDITORIAL_VOIR.id, "a".repeat(64), "expired", 1),
                WorkingKeyInfo(SigningUse.EDITORIAL_VOIR.id, "b".repeat(64), "active", 2, current = true),
            ),
        )
        val signer = signer { HttpStatusCode.OK to json.encodeToString(KeysResponse.serializer(), keys) }
        assertEquals("b".repeat(64), signer.currentKey(SigningUse.EDITORIAL_VOIR))
        assertNull(signer.currentKey(SigningUse.DECIDER_PACTE))
        assertEquals("/v1/keys", requests.last().url.encodedPath)
    }

    @Test
    fun theConfigurationTakesTheTokenFromAFileOnly() {
        assertNull(PlatformSignerConfig.fromEnvironment(emptyMap()))
        val file = Files.createTempFile("ppf-signer-token", "").also { Files.writeString(it, "$token\n") }
        val read = PlatformSignerConfig.fromEnvironment(mapOf("PPF_SIGNER_URL" to "http://signer:8090/", "PPF_SIGNER_CLIENT_TOKEN_FILE" to file.toString()))!!
        assertEquals("http://signer:8090", read.url)
        assertEquals(token, read.token)
        assertNull(read.rootPublicKey)
        assertTrue(token !in read.toString())
        val rooted = PlatformSignerConfig.fromEnvironment(mapOf("PPF_SIGNER_URL" to "http://signer:8090", "PPF_SIGNER_CLIENT_TOKEN_FILE" to file.toString(), "PPF_SIGNER_ROOT_PUBKEY" to root.publicKey.hex))!!
        assertEquals(root.publicKey.hex, rooted.rootPublicKey)
        assertFailsWith<IllegalStateException> { PlatformSignerConfig.fromEnvironment(mapOf("PPF_SIGNER_URL" to "http://signer:8090", "PPF_SIGNER_CLIENT_TOKEN_FILE" to file.toString(), "PPF_SIGNER_ROOT_PUBKEY" to "ABC")) }
        assertFailsWith<IllegalStateException> { PlatformSignerConfig.fromEnvironment(mapOf("PPF_SIGNER_URL" to "http://signer:8090", "PPF_SIGNER_CLIENT_TOKEN" to token)) }
        assertFailsWith<IllegalStateException> { PlatformSignerConfig.fromEnvironment(mapOf("PPF_SIGNER_URL" to "http://signer:8090")) }
        assertFailsWith<IllegalStateException> { PlatformSignerConfig.fromEnvironment(mapOf("PPF_SIGNER_URL" to "http://user@signer:8090", "PPF_SIGNER_CLIENT_TOKEN_FILE" to file.toString())) }
        assertFailsWith<IllegalStateException> { PlatformSignerConfig.fromEnvironment(mapOf("PPF_SIGNER_URL" to "ftp://signer", "PPF_SIGNER_CLIENT_TOKEN_FILE" to file.toString())) }
    }

    @Test
    fun theFakeSignerChecksWhatThePolesMustGetRight() = runBlocking<Unit> {
        val fake = FakePlatformSigner()
        val key = fake.currentKey(SigningUse.EDITORIAL_VOIR)
        val event = UnsignedEvent(key, System.currentTimeMillis() / 1000, EventKinds.LONG_FORM_ARTICLE, listOf(listOf("d", "x"), listOf("title", "[EXEMPLE]")), "[EXEMPLE]")
        assertEquals(SigningStatus.Refused(SigningRefusals.UNKNOWN_DRAFT), fake.status(fake.request(event, SigningUse.EDITORIAL_VOIR)))
        val submitted = assertIs<DraftSubmission.Submitted>(fake.submitDraft(event, SigningUse.EDITORIAL_VOIR))
        assertTrue(submitted.approvalUrl.startsWith(FakePlatformSigner.APPROVAL_ORIGIN))
        assertEquals(SigningStatus.Refused(SigningRefusals.APPROVALS_MISSING), fake.status(fake.request(event, SigningUse.EDITORIAL_VOIR)))
        assertNull(fake.approve(SigningUse.EDITORIAL_VOIR, event.idHex(), "alice"))
        assertTrue(fake.draftStatus(SigningUse.EDITORIAL_VOIR, event.idHex())!!.ready)
        val signed = assertIs<SigningStatus.Signed>(fake.status(fake.request(event, SigningUse.EDITORIAL_VOIR)))
        assertTrue(fake.trust.isPlatformContent(signed.event, SigningUse.EDITORIAL_VOIR))
        // The double validation: an author, two different accounts, never the author.
        val other = event.copy(content = "[EXEMPLE] autre")
        assertEquals(DraftSubmission.Refused(SigningRefusals.AUTHOR_REQUIRED), fake.submitDraft(other, SigningUse.EDITORIAL_VOIR, doubleValidation = true))
        fake.submitDraft(other, SigningUse.EDITORIAL_VOIR, doubleValidation = true, author = DraftAuthor(account = "alice"))
        assertEquals(DraftSubmission.Refused(SigningRefusals.DRAFT_CONFLICT), fake.submitDraft(other, SigningUse.EDITORIAL_VOIR))
        assertEquals(SigningRefusals.AUTHOR_CANNOT_APPROVE, fake.approve(SigningUse.EDITORIAL_VOIR, other.idHex(), "alice"))
        assertNull(fake.approve(SigningUse.EDITORIAL_VOIR, other.idHex(), "bob"))
        assertEquals(SigningRefusals.ALREADY_APPROVED, fake.approve(SigningUse.EDITORIAL_VOIR, other.idHex(), "bob"))
        assertEquals(SigningStatus.Refused(SigningRefusals.APPROVALS_MISSING), fake.status(fake.request(other, SigningUse.EDITORIAL_VOIR)))
        // A key or a form of another use, and an automatic use through a draft.
        assertEquals(SigningStatus.Refused(SigningRefusals.WRONG_KEY), fake.status(fake.request(event, SigningUse.EDITORIAL_COMPRENDRE)))
        assertEquals(DraftSubmission.Refused(SigningRefusals.AUTOMATIC_USE), fake.submitDraft(event, SigningUse.OPENDATA_INDEX))
        val opendata = UnsignedEvent(fake.currentKey(SigningUse.OPENDATA_INDEX), event.createdAt, EventKinds.APP_DATA, listOf(listOf("d", "ppf:parlement-index")), "{}")
        assertIs<SigningStatus.Signed>(fake.status(fake.request(opendata, SigningUse.OPENDATA_INDEX)))
    }
}
