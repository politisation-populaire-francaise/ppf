package org.politisationpopulairefrancaise.core.crypto

/**
 * ChaCha20 stream cipher (RFC 8439, section 2.4): 256-bit key, 96-bit nonce, 32-bit block counter.
 *
 * Written in plain Kotlin so that every platform (Android, desktop, iOS) produces exactly the same
 * bytes; the platform crypto APIs do not all expose ChaCha20 without Poly1305. It is only used
 * under NIP-44, where the ciphertext is authenticated by HMAC-SHA-256 before being decrypted.
 * ChaCha20 gives no integrity on its own, and a (key, nonce) pair must never be reused.
 */
object ChaCha20 {
    const val KEY_SIZE: Int = 32
    const val NONCE_SIZE: Int = 12
    private const val BLOCK_SIZE = 64

    /**
     * Encrypts or decrypts [data] (the operation is its own inverse) with the keystream starting
     * at block [counter]. The counter wraps at 2^32 like the RFC's 32-bit word.
     */
    fun process(key: ByteArray, nonce: ByteArray, data: ByteArray, counter: Int = 0): ByteArray {
        val state = initialState(key, nonce, counter)
        val output = ByteArray(data.size)
        val keystream = ByteArray(BLOCK_SIZE)
        val working = IntArray(16)
        var offset = 0
        while (offset < data.size) {
            block(state, working, keystream)
            val length = minOf(BLOCK_SIZE, data.size - offset)
            for (i in 0 until length) {
                output[offset + i] = (data[offset + i].toInt() xor keystream[i].toInt()).toByte()
            }
            offset += length
            state[12]++
        }
        keystream.fill(0)
        working.fill(0)
        state.fill(0)
        return output
    }

    /** One 64-byte keystream block (RFC 8439, section 2.3), exposed for the block function test vector. */
    internal fun keystreamBlock(key: ByteArray, nonce: ByteArray, counter: Int): ByteArray {
        val state = initialState(key, nonce, counter)
        val out = ByteArray(BLOCK_SIZE)
        block(state, IntArray(16), out)
        return out
    }

    private fun initialState(key: ByteArray, nonce: ByteArray, counter: Int): IntArray {
        require(key.size == KEY_SIZE) { "ChaCha20 key must be $KEY_SIZE bytes, got ${key.size}" }
        require(nonce.size == NONCE_SIZE) { "ChaCha20 nonce must be $NONCE_SIZE bytes, got ${nonce.size}" }
        val state = IntArray(16)
        // "expand 32-byte k" in little-endian words.
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = readLittleEndianInt(key, 4 * i)
        state[12] = counter
        for (i in 0 until 3) state[13 + i] = readLittleEndianInt(nonce, 4 * i)
        return state
    }

    private fun block(state: IntArray, working: IntArray, out: ByteArray) {
        state.copyInto(working)
        repeat(10) {
            // Column round.
            quarterRound(working, 0, 4, 8, 12)
            quarterRound(working, 1, 5, 9, 13)
            quarterRound(working, 2, 6, 10, 14)
            quarterRound(working, 3, 7, 11, 15)
            // Diagonal round.
            quarterRound(working, 0, 5, 10, 15)
            quarterRound(working, 1, 6, 11, 12)
            quarterRound(working, 2, 7, 8, 13)
            quarterRound(working, 3, 4, 9, 14)
        }
        for (i in 0 until 16) writeLittleEndianInt(out, 4 * i, working[i] + state[i])
    }

    private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = (x[d] xor x[a]).rotateLeft(16)
        x[c] += x[d]; x[b] = (x[b] xor x[c]).rotateLeft(12)
        x[a] += x[b]; x[d] = (x[d] xor x[a]).rotateLeft(8)
        x[c] += x[d]; x[b] = (x[b] xor x[c]).rotateLeft(7)
    }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun writeLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }
}
