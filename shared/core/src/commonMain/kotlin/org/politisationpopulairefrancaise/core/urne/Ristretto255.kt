package org.politisationpopulairefrancaise.core.urne

/**
 * The prime-order group ristretto255 (RFC 9496), as the voting protocol needs it (D-110).
 *
 * Elements and scalars are their canonical 32-byte encodings. The protocol code ([Voprf]) only
 * sees this interface, so that the library underneath can be replaced on one platform without
 * touching the protocol (CLAUDE.md, iOS rule). The identity element is never accepted from
 * outside: [isValidElement] says nothing about it, [Voprf] rejects it itself.
 */
interface Ristretto255 {
    /** True if [encoded] is the canonical encoding of a group element (the identity included). */
    fun isValidElement(encoded: ByteArray): Boolean

    /** The one-way map of RFC 9496 (`FROM_UNIFORM_BYTES`): 64 uniform bytes to an element. */
    fun elementFromUniformBytes(uniformBytes: ByteArray): ByteArray

    /** Group law; both operands are valid elements, the identity excepted. */
    fun add(p: ByteArray, q: ByteArray): ByteArray

    /** `scalar * element`, for a non-zero canonical scalar and a valid element other than the identity. */
    fun multiply(scalar: ByteArray, element: ByteArray): ByteArray

    /** `scalar * G`, for a non-zero canonical scalar. */
    fun multiplyBase(scalar: ByteArray): ByteArray

    /** A 64-byte little-endian integer reduced modulo the group order. */
    fun reduceWide(bytes: ByteArray): ByteArray

    fun scalarAdd(a: ByteArray, b: ByteArray): ByteArray
    fun scalarSub(a: ByteArray, b: ByteArray): ByteArray
    fun scalarMul(a: ByteArray, b: ByteArray): ByteArray

    /** Inverse modulo the group order of a non-zero scalar. */
    fun scalarInvert(a: ByteArray): ByteArray

    /** A uniformly random scalar in ]0, L[, from the system's secure random generator. */
    fun randomScalar(): ByteArray

    companion object {
        const val ELEMENT_SIZE: Int = 32
        const val SCALAR_SIZE: Int = 32

        /** The encoding of the identity element: 32 zero bytes. */
        val IDENTITY: ByteArray = ByteArray(ELEMENT_SIZE)

        /**
         * The group order L = 2^252 + 27742317777372353535851937790883648493, little-endian
         * (RFC 9496, section 4).
         */
        private val ORDER: ByteArray = byteArrayOf(
            0xed.toByte(), 0xd3.toByte(), 0xf5.toByte(), 0x5c, 0x1a, 0x63, 0x12, 0x58,
            0xd6.toByte(), 0x9c.toByte(), 0xf7.toByte(), 0xa2.toByte(), 0xde.toByte(), 0xf9.toByte(), 0xde.toByte(), 0x14,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0x10,
        )

        /** True if [scalar] is 32 bytes and, read little-endian, strictly below the group order. */
        fun isCanonicalScalar(scalar: ByteArray): Boolean {
            if (scalar.size != SCALAR_SIZE) return false
            // Compare from the most significant byte, without stopping early (same work for every value).
            var less = 0
            var equal = 1
            for (i in SCALAR_SIZE - 1 downTo 0) {
                val a = scalar[i].toInt() and 0xff
                val b = ORDER[i].toInt() and 0xff
                less = less or (equal and ((a - b) ushr 31))
                equal = equal and (((a xor b) - 1) ushr 31)
            }
            return less == 1
        }

        fun isZero(bytes: ByteArray): Boolean {
            var acc = 0
            for (b in bytes) acc = acc or b.toInt()
            return acc == 0
        }
    }
}
