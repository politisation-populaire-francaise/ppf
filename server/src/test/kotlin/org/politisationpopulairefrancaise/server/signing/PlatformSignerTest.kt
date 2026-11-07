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
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.ApprovalAssertion
import org.politisationpopulairefrancaise.core.platform.ApprovalProof
import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.SignRequest
import org.politisationpopulairefrancaise.core.platform.SignResponse
import org.politisationpopulairefrancaise.core.platform.SigningProtocol
import org.politisationpopulairefrancaise.core.platform.SigningRefusals
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.WorkingKeyInfo
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformSignerTest {
    private val token = "token-of-the-tests-0123456789abcdef-xyz"
    private val config = PlatformSignerConfig("http://signer:8090", token)
    private val working = IdentityKeys.generate()
    private val requests = mutableListOf<HttpRequestData>()
    private val json = SigningProtocol.json
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun index(createdAt: Long = 1_793_000_000) =
        UnsignedEvent(working.publicKey.hex, createdAt, EventKinds.APP_DATA, listOf(listOf("d", "ppf:parlement-index")), "{}")

    private fun signer(answer: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>): HttpPlatformSigner =
        HttpPlatformSigner(
            config,
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val (status, body) = answer(request)
                    respond(body, status, jsonHeaders)
                },
            ) { expectSuccess = false },
        )

    @Test
    fun aSignedEventIsCheckedBeforeBeingReturned() = runBlocking<Unit> {
        val signer = signer { request ->
            val sent = json.decodeFromString(SignRequest.serializer(), (request.body as TextContent).text)
            val event = LocalEventSigner(working).sign(sent.event.toUnsigned())
            HttpStatusCode.OK to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, event))
        }
        val proof = ApprovalProof(assertions = listOf(ApprovalAssertion("alice", "aWQ", "YXV0aA", "Y2xpZW50", "c2ln")))
        val ticket = signer.request(index(), SigningUse.EDITORIAL_VOIR, proof)
        val status = assertIs<SigningStatus.Signed>(signer.status(ticket))
        assertEquals(index().idHex(), status.event.id)
        val sent = requests.single()
        assertEquals("Bearer $token", sent.headers[HttpHeaders.Authorization])
        assertEquals("/v1/sign", sent.url.encodedPath)
        val body = json.decodeFromString(SignRequest.serializer(), (sent.body as TextContent).text)
        assertEquals(SigningUse.EDITORIAL_VOIR.id, body.use)
        assertEquals(proof, body.approval)
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.UNKNOWN_TICKET), signer.status(SigningTicket("autre")))
    }

    @Test
    fun anotherEventOrABadSignatureFromTheServiceIsRefused() = runBlocking<Unit> {
        val signer = signer {
            val other = LocalEventSigner(working).sign(index(createdAt = 1))
            HttpStatusCode.OK to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, other))
        }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.BAD_SIGNATURE_FROM_SIGNER), signer.status(signer.request(index(), SigningUse.OPENDATA_INDEX, ApprovalProof.NONE)))
        val forged = signer {
            val event = LocalEventSigner(working).sign(index())
            HttpStatusCode.OK to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.SIGNED, event.copy(sig = "0".repeat(128))))
        }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.BAD_SIGNATURE_FROM_SIGNER), forged.status(forged.request(index(), SigningUse.OPENDATA_INDEX, ApprovalProof.NONE)))
    }

    @Test
    fun refusalsAndOutagesAreTold() = runBlocking<Unit> {
        val refusing = signer {
            HttpStatusCode.UnprocessableEntity to json.encodeToString(SignResponse.serializer(), SignResponse(SignResponse.REFUSED, code = SigningRefusals.MISSING_TAG, detail = "title"))
        }
        assertEquals(SigningStatus.Refused(SigningRefusals.MISSING_TAG, "title"), refusing.status(refusing.request(index(), SigningUse.EDITORIAL_VOIR, ApprovalProof.NONE)))
        val unauthorized = signer { HttpStatusCode.Unauthorized to """{"status":"refused","code":"unauthorized"}""" }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.SIGNER_UNAUTHORIZED), unauthorized.status(unauthorized.request(index(), SigningUse.EDITORIAL_VOIR, ApprovalProof.NONE)))
        val broken = signer { throw java.io.IOException("connection refused") }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.SIGNER_UNAVAILABLE), broken.status(broken.request(index(), SigningUse.EDITORIAL_VOIR, ApprovalProof.NONE)))
        assertNull(broken.currentKey(SigningUse.EDITORIAL_VOIR))
        val nonsense = signer { HttpStatusCode.BadGateway to "<html>" }
        assertEquals(SigningStatus.Refused(HttpPlatformSigner.SIGNER_UNAVAILABLE), nonsense.status(nonsense.request(index(), SigningUse.EDITORIAL_VOIR, ApprovalProof.NONE)))
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
        assertTrue(token !in read.toString())
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
        fun assertion(account: String) = ApprovalAssertion(account, "aWQ", "YQ", "Yg", "Yw")
        val signed = assertIs<SigningStatus.Signed>(fake.status(fake.request(event, SigningUse.EDITORIAL_VOIR, ApprovalProof(assertions = listOf(assertion("alice"))))))
        assertTrue(fake.trust.isPlatformContent(signed.event, SigningUse.EDITORIAL_VOIR))
        val double = ApprovalProof(doubleValidation = true, author = DraftAuthor(account = "alice"), assertions = listOf(assertion("alice"), assertion("bob")))
        assertEquals(SigningStatus.Refused(SigningRefusals.AUTHOR_CANNOT_APPROVE), fake.status(fake.request(event, SigningUse.EDITORIAL_VOIR, double)))
        assertEquals(SigningStatus.Refused(SigningRefusals.WRONG_KEY), fake.status(fake.request(event, SigningUse.EDITORIAL_COMPRENDRE, ApprovalProof(assertions = listOf(assertion("alice"))))))
        assertEquals(SigningStatus.Refused(SigningRefusals.APPROVALS_MISSING), fake.status(fake.request(event, SigningUse.EDITORIAL_VOIR, ApprovalProof.NONE)))
        assertEquals(4, fake.requests.size)
    }
}
