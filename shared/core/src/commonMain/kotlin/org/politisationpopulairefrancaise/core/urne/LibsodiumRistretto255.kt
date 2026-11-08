@file:OptIn(ExperimentalUnsignedTypes::class)

package org.politisationpopulairefrancaise.core.urne

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.ristretto255.Ristretto255LowLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [Ristretto255] on libsodium, through ionspin's Kotlin Multiplatform bindings (D-110).
 *
 * Only the low-level functions are used; each one passes its bytes straight to libsodium. The
 * results are checked end to end by the RFC 9497 test vectors ([VoprfVectors], [UrneSelfTest]).
 * Obtain the instance with [load], which initialises libsodium once.
 */
class LibsodiumRistretto255 private constructor() : Ristretto255 {

    override fun isValidElement(encoded: ByteArray): Boolean =
        encoded.size == Ristretto255.ELEMENT_SIZE && Ristretto255LowLevel.isValidPoint(encoded.toUByteArray())

    override fun elementFromUniformBytes(uniformBytes: ByteArray): ByteArray {
        require(uniformBytes.size == 64) { "64 uniform bytes expected" }
        return Ristretto255LowLevel.pointFromHash(uniformBytes.toUByteArray()).toByteArray()
    }

    override fun add(p: ByteArray, q: ByteArray): ByteArray = guarded {
        Ristretto255LowLevel.addPoints(p.toUByteArray(), q.toUByteArray()).toByteArray()
    }

    override fun multiply(scalar: ByteArray, element: ByteArray): ByteArray = guarded {
        Ristretto255LowLevel.scalarMultiplication(scalar.toUByteArray(), element.toUByteArray()).toByteArray()
    }

    override fun multiplyBase(scalar: ByteArray): ByteArray = guarded {
        Ristretto255LowLevel.scalarMultiplicationBase(scalar.toUByteArray()).toByteArray()
    }

    override fun reduceWide(bytes: ByteArray): ByteArray {
        require(bytes.size == 64) { "64 bytes expected" }
        return Ristretto255LowLevel.reduceScalar(bytes.toUByteArray()).toByteArray()
    }

    override fun scalarAdd(a: ByteArray, b: ByteArray): ByteArray =
        Ristretto255LowLevel.addScalars(a.toUByteArray(), b.toUByteArray()).toByteArray()

    override fun scalarSub(a: ByteArray, b: ByteArray): ByteArray =
        Ristretto255LowLevel.subtractScalars(a.toUByteArray(), b.toUByteArray()).toByteArray()

    override fun scalarMul(a: ByteArray, b: ByteArray): ByteArray =
        Ristretto255LowLevel.multiplyScalars(a.toUByteArray(), b.toUByteArray()).toByteArray()

    override fun scalarInvert(a: ByteArray): ByteArray = guarded {
        Ristretto255LowLevel.invertScalar(a.toUByteArray()).toByteArray()
    }

    override fun randomScalar(): ByteArray = Ristretto255LowLevel.randomScalar().toByteArray()

    /** libsodium refuses an operation (invalid point, identity result): the protocol sees one error. */
    private inline fun guarded(operation: () -> ByteArray): ByteArray =
        try {
            operation()
        } catch (e: VoprfError) {
            throw e
        } catch (e: Exception) {
            throw VoprfError(VoprfError.Reason.GROUP_OPERATION)
        }

    companion object {
        private val lock = Mutex()
        private var instance: LibsodiumRistretto255? = null

        /** Initialises libsodium (once, on first use) and returns the group. */
        suspend fun load(): LibsodiumRistretto255 = lock.withLock {
            instance ?: run {
                if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
                LibsodiumRistretto255().also { instance = it }
            }
        }
    }
}
