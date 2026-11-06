package org.politisationpopulairefrancaise.core.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentityKeysTest {

    // NIP-19 test vectors (https://github.com/nostr-protocol/nips/blob/master/19.md).
    private val npub = "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg"
    private val npubHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"
    private val nsecHex = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"

    @Test
    fun publicKeyEncodesAndDecodesNip19() {
        val key = PublicKey.fromHex(npubHex)
        assertEquals(npub, key.npub)
        assertEquals(key, PublicKey.fromNpub(npub))
        assertEquals(npubHex, PublicKey.fromNpub(npub).hex)
        assertEquals(npub, key.toString())
    }

    @Test
    fun secretKeyEncodesAndDecodesNip19() {
        val keys = IdentityKeys.fromHex(nsecHex)
        assertEquals(nsec, keys.nsec)
        assertEquals(nsecHex, IdentityKeys.fromNsec(nsec).secretKey().toHexString())
        // Public key example of NIP-19.
        assertEquals(
            "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6",
            PublicKey.fromHex("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d").npub,
        )
    }

    @Test
    fun wrongPrefixOrChecksumIsRejected() {
        assertFailsWith<IllegalArgumentException> { PublicKey.fromNpub(nsec) }
        assertFailsWith<IllegalArgumentException> { IdentityKeys.fromNsec(npub) }
        assertFailsWith<IllegalArgumentException> { PublicKey.fromNpub(npub.dropLast(1) + "q") }
        assertFailsWith<IllegalArgumentException> { IdentityKeys.fromHex("00".repeat(32)) }
    }

    @Test
    fun generatedKeysSignAndVerify() {
        val keys = IdentityKeys.generate()
        val other = IdentityKeys.generate()
        assertNotEquals(keys.publicKey, other.publicKey)

        val message = ByteArray(32) { it.toByte() }
        val signature = keys.sign(message)
        assertEquals(64, signature.size)
        assertTrue(keys.publicKey.verify(signature, message))
        assertFalse(other.publicKey.verify(signature, message))
        assertFalse(keys.publicKey.verify(signature, ByteArray(32)))
        assertFalse(keys.publicKey.verify(signature.copyOf(63), message))
        // Fresh auxiliary randomness: two signatures of the same message differ, both are valid.
        val again = keys.sign(message)
        assertFalse(signature.contentEquals(again))
        assertTrue(keys.publicKey.verify(again, message))
    }

    @Test
    fun roundTripThroughNsec() {
        val keys = IdentityKeys.generate()
        val restored = IdentityKeys.fromNsec(keys.nsec)
        assertEquals(keys.publicKey, restored.publicKey)
        assertTrue(restored.secretKey().contentEquals(keys.secretKey()))
    }

    @Test
    fun destroyedKeysCannotSignAndNeverPrintTheSecret() {
        val keys = IdentityKeys.generate()
        val npubBefore = keys.publicKey.npub
        assertFalse(keys.toString().contains(keys.nsec))
        keys.destroy()
        assertTrue(keys.isDestroyed)
        assertEquals(npubBefore, keys.publicKey.npub)
        assertFailsWith<IllegalStateException> { keys.sign(ByteArray(32)) }
        assertFailsWith<IllegalStateException> { keys.nsec }
        assertFailsWith<IllegalStateException> { keys.secretKey() }
    }

    @Test
    fun publicKeyDefensivelyCopiesItsBytes() {
        val bytes = ByteArray(32) { 7 }
        val key = PublicKey(bytes)
        bytes[0] = 1
        assertEquals(7, key.bytes[0])
        key.bytes[1] = 1
        assertEquals(7, key.bytes[1])
    }
}
