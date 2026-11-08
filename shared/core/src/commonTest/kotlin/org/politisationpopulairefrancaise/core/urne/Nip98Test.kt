package org.politisationpopulairefrancaise.core.urne

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** NIP-98 authorizations and NIP-13 proofs of work, as the ballot-box API and the back-office use them (D-110). */
@OptIn(ExperimentalEncodingApi::class)
class Nip98Test {
    private val signer = LocalEventSigner(IdentityKeys.generate())
    private val url = "https://politisation-populaire-francaise.org/api/v1/urnes/tokens"
    private val now = 1_794_000_000L

    @Test
    fun aValidAuthorizationNamesItsKey() = runTest {
        val body = """{"requests":[]}""".encodeToByteArray()
        val header = Nip98.authorization(signer, url, "post", body, now, difficulty = 6)
        val result = Nip98.verify(header, url, "POST", body, now + 30, difficulty = 6)
        assertIs<Nip98.Result.Valid>(result)
        assertEquals(signer.publicKey.hex, result.pubkey)
    }

    @Test
    fun everyMismatchIsRefusedWithItsCode() = runTest {
        val body = "corps".encodeToByteArray()
        val header = Nip98.authorization(signer, url, "POST", body, now, difficulty = 4)
        fun reason(result: Nip98.Result) = (result as Nip98.Result.Invalid).reason
        assertEquals("missing_authorization", reason(Nip98.verify(null, url, "POST", body, now)))
        assertEquals("malformed_authorization", reason(Nip98.verify("Nostr !!!", url, "POST", body, now)))
        assertEquals("wrong_url", reason(Nip98.verify(header, "$url?x=1", "POST", body, now)))
        assertEquals("wrong_method", reason(Nip98.verify(header, url, "GET", body, now)))
        assertEquals("wrong_payload", reason(Nip98.verify(header, url, "POST", "autre".encodeToByteArray(), now)))
        assertEquals("wrong_payload", reason(Nip98.verify(header, url, "POST", null, now)))
        assertEquals("expired_authorization", reason(Nip98.verify(header, url, "POST", body, now + 61)))
        assertEquals("proof_of_work_too_low", reason(Nip98.verify(header, url, "POST", body, now, difficulty = 30)))
    }

    @Test
    fun theKeyIsAskedForBeforeTheSignatureIsChecked() = runTest {
        val header = Nip98.authorization(signer, url, "GET", null, now)
        val event = SignedEvent.fromJson(Base64.Default.decode(header.removePrefix("Nostr ")).decodeToString())
        val forged = "Nostr " + Base64.Default.encode(event.copy(sig = "0".repeat(128)).toJson().encodeToByteArray())
        val asked = mutableListOf<String>()
        // An unknown key: refused before any signature check, even a forged one.
        assertEquals(Nip98.Result.Invalid(Nip98.UNKNOWN_SIGNER), Nip98.verify(forged, { it == url }, "GET", null, now) { asked += it; false })
        assertEquals(listOf(signer.publicKey.hex), asked)
        assertEquals(Nip98.Result.Invalid("invalid_signature"), Nip98.verify(forged, { it == url }, "GET", null, now) { true })
        assertEquals(Nip98.Result.Valid(signer.publicKey.hex, event.id, now), Nip98.verify(header, { it == url }, "GET", null, now) { true })
    }

    @Test
    fun aSecondAddressOrMethodIsNotIgnored() = runTest {
        val twice = signer.sign(kind = Nip98.KIND, createdAt = now, tags = listOf(listOf("u", url), listOf("u", "$url/autre"), listOf("method", "GET")))
        val header = "Nostr " + Base64.Default.encode(twice.toJson().encodeToByteArray())
        assertEquals(Nip98.Result.Invalid("wrong_url"), Nip98.verify(header, url, "GET", null, now))
        assertEquals(Nip98.Result.Invalid("malformed_authorization"), Nip98.verify("Nostr " + "A".repeat(Nip98.MAX_HEADER_LENGTH), url, "GET", null, now))
    }

    @Test
    fun anAuthorizationIsUsedOnceWithinItsWindow() {
        val guard = Nip98ReplayGuard(windowSeconds = 100)
        assertTrue(guard.firstUse("a", now))
        assertFalse(guard.firstUse("a", now + 50))
        assertTrue(guard.firstUse("b", now + 50))
        // Long after its window, an id is forgotten (the time check refuses it anyway).
        assertTrue(guard.firstUse("a", now + 500))
    }

    @Test
    fun proofsOfWorkCountLeadingZeroBits() {
        assertEquals(0, ProofOfWork.leadingZeroBits(byteArrayOf(0x80.toByte())))
        assertEquals(7, ProofOfWork.leadingZeroBits(byteArrayOf(0x01)))
        assertEquals(12, ProofOfWork.leadingZeroBits(byteArrayOf(0x00, 0x0f)))
        val ballot = Ballot.withProofOfWork(1, "verifie", "aa".repeat(32), "bb".repeat(64), "pour", difficulty = 8)
        assertTrue(ProofOfWork.leadingZeroBits(ballot.powDigest()) >= 8)
        assertFalse(ProofOfWork.leadingZeroBits(ballot.copy(choice = "contre").powDigest()) >= 8 && ballot.nonce == 0L)
    }

    @Test
    fun theKeyInfoSeparatesBoxesAndLevels() {
        val salt = ByteArray(16) { it.toByte() }
        assertEquals("ppf-urne-v1\u001fq1\u001f000102030405060708090a0b0c0d0e0f\u001fverifie", UrneKeyInfo.of("q1", salt, VoteLevel.VERIFIE).decodeToString())
        assertFalse(UrneKeyInfo.of("q1", salt, VoteLevel.ELU).contentEquals(UrneKeyInfo.of("q1", salt, VoteLevel.VERIFIE)))
        // Another salt, other keys: a box whose identifier came back does not get the keys of the first.
        assertFalse(UrneKeyInfo.of("q1", salt, VoteLevel.ELU).contentEquals(UrneKeyInfo.of("q1", ByteArray(16), VoteLevel.ELU)))
    }
}
