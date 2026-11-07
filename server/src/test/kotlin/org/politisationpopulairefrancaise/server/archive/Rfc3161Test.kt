package org.politisationpopulairefrancaise.server.archive

import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** RFC 3161 answers of a fake authority built with BouncyCastle: the good one is kept, every faulty one refused. */
class Rfc3161Test {
    private val tsa = FakeTsa()
    private val imprint = exampleDigest(42)
    private val request = Rfc3161Verifier.request(imprint, BigInteger.valueOf(0x5eed_1234_5678L))

    @Test
    fun aGoodTokenIsKeptWithItsTimeAndTheFingerprintOfItsCertificate() {
        val before = Instant.now().minusSeconds(5)
        val result = assertIs<Rfc3161Result.Accepted>(Rfc3161Verifier.verify(request, tsa.answer(request.encoded, FakeTsa.Mode.OK)))
        assertTrue(result.token.time.isAfter(before) && result.token.time.isBefore(Instant.now().plusSeconds(5)))
        assertContentEquals(sha256(tsa.certificate.encoded), result.token.tsaCertSha256)
        // The token kept is the DER TimeStampToken itself, readable again on its own.
        val token = org.bouncycastle.tsp.TimeStampToken(org.bouncycastle.cms.CMSSignedData(result.token.token))
        assertContentEquals(imprint, token.timeStampInfo.messageImprintDigest)
        assertEquals(request.nonce, token.timeStampInfo.nonce)
    }

    @Test
    fun aFaultyAnswerIsAlwaysRefused() {
        val expected = mapOf(
            FakeTsa.Mode.WRONG_NONCE to "invalid_token",
            FakeTsa.Mode.WRONG_IMPRINT to "invalid_token",
            FakeTsa.Mode.REJECTED to "status_2",
            FakeTsa.Mode.NO_CERTIFICATE to "no_certificate",
        )
        for ((mode, code) in expected) {
            assertEquals(Rfc3161Result.Refused(code), Rfc3161Verifier.verify(request, tsa.answer(request.encoded, mode)), mode.name)
        }
        // A changed signature: refused, whichever check notices it first.
        assertIs<Rfc3161Result.Refused>(Rfc3161Verifier.verify(request, tsa.answer(request.encoded, FakeTsa.Mode.TAMPERED)))
        assertIs<Rfc3161Result.Refused>(Rfc3161Verifier.verify(request, "not a timestamp response".encodeToByteArray()))
        assertIs<Rfc3161Result.Refused>(Rfc3161Verifier.verify(request, ByteArray(0)))
        // An answer to another request (another nonce) does not fit this one.
        val other = Rfc3161Verifier.request(imprint, BigInteger.ONE)
        assertEquals(Rfc3161Result.Refused("invalid_token"), Rfc3161Verifier.verify(request, tsa.answer(other.encoded, FakeTsa.Mode.OK)))
    }

    @Test
    fun requestsAreSpacedByAtLeastOneSecond() = runBlocking {
        val pauses = ArrayList<Duration>()
        val spacing = MinimumSpacing(1.seconds) { pauses += it }
        spacing.await()
        assertTrue(pauses.isEmpty(), "the first request does not wait")
        spacing.await()
        spacing.await()
        assertEquals(2, pauses.size)
        pauses.forEach { assertTrue(it > 900.milliseconds && it <= 1.seconds, it.toString()) }
    }
}
