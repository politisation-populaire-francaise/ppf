package org.politisationpopulairefrancaise.core.messaging

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Behaviour of the NIP-44 layer on every platform. The complete official vectors file is replayed
 * by Nip44VectorsTest on the JVM; here are the vectors quoted in the NIP text itself and our own checks.
 */
class Nip44Test {

    // Public test vector from the NIP-44 text: sec1 = 1, sec2 = 2 (not a secret).
    private val conversationKeyOfOneAndTwo = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d" // gitleaks:allow
    private val nonceOne = "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()

    @Test
    fun conversationKeyIsSymmetricAndMatchesTheNip() {
        val one = IdentityKeys.fromHex("0000000000000000000000000000000000000000000000000000000000000001")
        val two = IdentityKeys.fromHex("0000000000000000000000000000000000000000000000000000000000000002")
        val fromOne = Nip44.conversationKey(one, two.publicKey)
        val fromTwo = Nip44.conversationKey(two, one.publicKey)
        assertEquals(conversationKeyOfOneAndTwo, fromOne.toHexString())
        assertEquals(conversationKeyOfOneAndTwo, fromTwo.toHexString())
    }

    @Test
    fun exampleVectorOfTheNipText() {
        val key = conversationKeyOfOneAndTwo.hexToByteArray()
        val payload = Nip44.encrypt("a", key, nonceOne)
        assertEquals(
            "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb",
            payload,
        )
        assertEquals("a", Nip44.decrypt(payload, key))
    }

    @Test
    fun randomNonceMakesEveryPayloadDifferentAndReadable() {
        val alice = IdentityKeys.generate()
        val bob = IdentityKeys.generate()
        val key = Nip44.conversationKey(alice, bob.publicKey)
        val text = "[EXEMPLE] Rendez-vous à 18 h place de la République, apportez de l'eau."
        val first = Nip44.encrypt(text, key)
        val second = Nip44.encrypt(text, key)
        assertNotEquals(first, second)
        assertEquals(text, Nip44.decrypt(first, Nip44.conversationKey(bob, alice.publicKey)))
        assertEquals(text, Nip44.decrypt(second, key))
    }

    @Test
    fun tamperingOrWrongKeyIsRejected() {
        val key = conversationKeyOfOneAndTwo.hexToByteArray()
        val payload = Nip44.encrypt("bonjour", key, nonceOne)
        val otherKey = ByteArray(32) { 7 }
        assertFailsWith<Nip44Exception> { Nip44.decrypt(payload, otherKey) }
        val flipped = payload.toCharArray().also { it[60] = if (it[60] == 'A') 'B' else 'A' }.concatToString()
        assertFailsWith<Nip44Exception> { Nip44.decrypt(flipped, key) }
        assertFailsWith<Nip44Exception> { Nip44.decrypt(payload.dropLast(4), key) }
        assertFailsWith<Nip44Exception> { Nip44.decrypt("#$payload", key) }
        assertFailsWith<Nip44Exception> { Nip44.decrypt("", key) }
        assertFailsWith<Nip44Exception> { Nip44.encrypt("", key) }
        assertFailsWith<Nip44Exception> { Nip44.encrypt("x", key, ByteArray(31)) }
        assertFailsWith<Nip44Exception> { Nip44.encrypt("x", ByteArray(31), nonceOne) }
    }

    @Test
    fun paddingFollowsTheNip() {
        assertEquals(32, Nip44.calcPaddedLength(1))
        assertEquals(32, Nip44.calcPaddedLength(32))
        assertEquals(64, Nip44.calcPaddedLength(33))
        assertEquals(96, Nip44.calcPaddedLength(65))
        assertEquals(256, Nip44.calcPaddedLength(250))
        assertEquals(320, Nip44.calcPaddedLength(320))
        assertEquals(640, Nip44.calcPaddedLength(515))
        assertEquals(65536, Nip44.calcPaddedLength(65535))
        assertEquals(65536, Nip44.calcPaddedLength(65536))
        assertEquals(81920, Nip44.calcPaddedLength(65537))
        assertEquals(4294967296L, Nip44.calcPaddedLength(4294967295L))

        val padded = Nip44.pad("abc".encodeToByteArray())
        assertEquals(34, padded.size)
        assertEquals("0003616263" + "00".repeat(29), padded.toHexString())
        assertEquals("abc", Nip44.unpad(padded).decodeToString())
        assertFailsWith<Nip44Exception> { Nip44.unpad(padded.copyOf(33)) }
        assertFailsWith<Nip44Exception> { Nip44.unpad(byteArrayOf(0, 0, 0, 0, 0, 1)) }
        assertFailsWith<Nip44Exception> { Nip44.unpad(ByteArray(34)) }
    }

    @Test
    fun ourSizeLimitIsEnforcedBeforeDecoding() {
        val key = conversationKeyOfOneAndTwo.hexToByteArray()
        assertEquals(87472, Nip44.maxPayloadChars(Nip44.MAX_PLAINTEXT_BYTES))
        assertFailsWith<Nip44Exception> { Nip44.encrypt("a".repeat(Nip44.MAX_PLAINTEXT_BYTES + 1), key, nonceOne) }
        assertFailsWith<Nip44Exception> { Nip44.decrypt("A".repeat(Nip44.maxPayloadChars(Nip44.MAX_PLAINTEXT_BYTES) + 4), key) }
        assertFailsWith<Nip44Exception> { Nip44.encrypt("a", key, nonceOne, maxPlaintextBytes = 0) }
    }

    /**
     * The extended length prefix vectors quoted in the NIP text (plaintext 'a' repeated), which need
     * the size limit raised above our default. The 65536-byte payload must stay unreadable with the default.
     */
    @Test
    fun extendedLengthPrefixVectorsOfTheNipText() {
        val key = conversationKeyOfOneAndTwo.hexToByteArray()
        data class Vector(val length: Int, val plaintextSha256: String, val payloadSha256: String)
        val vectors = listOf(
            Vector(65535, "6e1bebca6a8229364a162a72ef064826c4cd7457bf54f190ef782bd9deff3e42", "6d8c2810d1e870fbaa1f0a0937126cca837a15f9260e27060c331d70a3c0bc84"),
            Vector(65536, "bf718b6f653bebc184e1479f1935b8da974d701b893afcf49e701f3e2f9f9c5a", "b7b4edb36ba92e267d322d56d9aebc22e7fa96ff52e3c12adc07f07a43cbc616"),
            Vector(65537, "008ffc88d3c96a9f307524eb361e47c5222a887fc45fa0c1fb8d429c5c23b430", "eeb7c7c5373894ea2c1547cfd3ccb15d5a0b2d619da852e5c79df792dcc9e435"),
        )
        for (vector in vectors) {
            val plaintext = "a".repeat(vector.length)
            assertEquals(vector.plaintextSha256, Crypto.sha256(plaintext.encodeToByteArray()).toHexString())
            val payload = Nip44.encrypt(plaintext, key, nonceOne, maxPlaintextBytes = 65537)
            assertEquals(vector.payloadSha256, Crypto.sha256(payload.encodeToByteArray()).toHexString(), "payload of ${vector.length}")
            assertEquals(plaintext, Nip44.decrypt(payload, key, maxPlaintextBytes = 65537))
            if (vector.length > Nip44.MAX_PLAINTEXT_BYTES) {
                assertFailsWith<Nip44Exception> { Nip44.decrypt(payload, key) }
            } else {
                assertEquals(plaintext, Nip44.decrypt(payload, key))
            }
        }
    }

    @Test
    fun invalidKeysAreRefused() {
        val valid = IdentityKeys.fromHex("0000000000000000000000000000000000000000000000000000000000000002").publicKey.bytes
        assertFailsWith<Nip44Exception> { Nip44.conversationKey(ByteArray(32), valid) }
        assertFailsWith<Nip44Exception> { Nip44.conversationKey("ff".repeat(32).hexToByteArray(), valid) }
        assertFailsWith<Nip44Exception> { Nip44.conversationKey(ByteArray(32) { 1 }, ByteArray(32)) }
        assertFailsWith<Nip44Exception> { Nip44.conversationKey(ByteArray(32) { 1 }, ByteArray(31)) }
        assertTrue(Nip44.conversationKey(ByteArray(32) { 1 }, valid).size == 32)
    }
}
