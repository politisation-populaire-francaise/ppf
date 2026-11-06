package org.politisationpopulairefrancaise.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Test vectors of RFC 8439 (sections 2.3.2 and 2.4.2, appendix A.2). */
class ChaCha20Test {

    private val key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".hexToByteArray()

    @Test
    fun blockFunctionVectorOfSection232() {
        val nonce = "000000090000004a00000000".hexToByteArray()
        val block = ChaCha20.keystreamBlock(key, nonce, counter = 1)
        assertEquals(
            "10f1e7e4d13b5915500fdd1fa32071c4c7d1f4c733c068030422aa9ac3d46c4e" +
                "d2826446079faa0914c2d705d98b02a2b5129cd1de164eb9cbd083e8a2503c4e",
            block.toHexString(),
        )
    }

    @Test
    fun sunscreenVectorOfSection242() {
        val nonce = "000000000000004a00000000".hexToByteArray()
        val plaintext = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
        val expected = "6e2e359a2568f98041ba0728dd0d6981e97e7aec1d4360c20a27afccfd9fae0b" +
            "f91b65c5524733ab8f593dabcd62b3571639d624e65152ab8f530c359f0861d8" +
            "07ca0dbf500d6a6156a38e088a22b65e52bc514d16ccf806818ce91ab7793736" +
            "5af90bbf74a35be6b40b8eedf2785e42874d"
        val ciphertext = ChaCha20.process(key, nonce, plaintext.encodeToByteArray(), counter = 1)
        assertEquals(expected, ciphertext.toHexString())
        assertEquals(plaintext, ChaCha20.process(key, nonce, ciphertext, counter = 1).decodeToString())
    }

    @Test
    fun appendixA2VectorWithCounterZero() {
        // The counter NIP-44 starts from; an all-zero plaintext exposes the raw keystream.
        val keystream = ChaCha20.process(ByteArray(32), ByteArray(12), ByteArray(64), counter = 0)
        assertEquals(
            "76b8e0ada0f13d90405d6ae55386bd28bdd219b8a08ded1aa836efcc8b770dc7" +
                "da41597c5157488d7724e03fb8d84a376a43b8f41518a11cc387b669b2ee6586",
            keystream.toHexString(),
        )
    }

    @Test
    fun appendixA2VectorTwoStartsWithTheExpectedBytes() {
        val key2 = "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()
        val nonce2 = "000000000000000000000002".hexToByteArray()
        val plaintext = "Any submission to the IETF intended by the Contributor for publication as all or part of an IETF Internet-Draft or RFC and any statement made within the context of an IETF activity is considered an \"IETF Contribution\". Such statements include oral statements in IETF sessions, as well as written and electronic communications made at any time or place, which are addressed to"
        val ciphertext = ChaCha20.process(key2, nonce2, plaintext.encodeToByteArray(), counter = 1)
        assertEquals(375, ciphertext.size)
        assertEquals("a3fbf07df3fa2fde4f376ca23e82737041605d9f4f4f57bd8cff2c1d4b7955ec", ciphertext.copyOfRange(0, 32).toHexString())
        assertContentEquals(plaintext.encodeToByteArray(), ChaCha20.process(key2, nonce2, ciphertext, counter = 1))
    }

    @Test
    fun emptyInputAndWrongSizesAreHandled() {
        assertEquals(0, ChaCha20.process(key, ByteArray(12), ByteArray(0)).size)
        assertFailsWith<IllegalArgumentException> { ChaCha20.process(ByteArray(31), ByteArray(12), ByteArray(1)) }
        assertFailsWith<IllegalArgumentException> { ChaCha20.process(key, ByteArray(8), ByteArray(1)) }
    }
}
