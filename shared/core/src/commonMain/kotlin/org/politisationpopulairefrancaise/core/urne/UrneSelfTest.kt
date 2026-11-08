package org.politisationpopulairefrancaise.core.urne

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The result of [UrneSelfTest]: voting stays closed on this device unless it [passed]. */
data class UrneSelfTestResult(val passed: Boolean, val failedCheck: String?)

/**
 * Self-test of the voting cryptography, run on the device at first use (D-110, condition b).
 *
 * It replays the RFC 9497 vector A.1.2 (VOPRF, ristretto255-SHA512, test vector 1), checks that a
 * tampered proof is refused and that a fresh random round trip gives the server's output. There
 * is no Android emulator on the development machine: this is what proves that libsodium works on
 * a real phone. A failure closes voting with a clear message; it never sends anything.
 */
object UrneSelfTest {

    private val lock = Mutex()
    private var firstResult: UrneSelfTestResult? = null

    /** The result on this device, computed at first use and kept for the life of the process. */
    suspend fun onThisDevice(): UrneSelfTestResult = lock.withLock {
        firstResult ?: run().also { firstResult = it }
    }

    suspend fun run(): UrneSelfTestResult = try {
        run(LibsodiumRistretto255.load())
    } catch (e: Throwable) {
        // The library could not even be loaded on this device (missing native library, for example).
        UrneSelfTestResult(passed = false, failedCheck = "load")
    }

    fun run(group: Ristretto255): UrneSelfTestResult {
        var check = "init"
        return try {
            val voprf = Voprf(group, VoprfMode.VOPRF)
            check = "derive-key-pair"
            val keyPair = voprf.deriveKeyPair(hex(SEED), hex(KEY_INFO))
            expect(keyPair.secretKey, SK)
            expect(keyPair.publicKey, PK)
            check = "blind"
            val blinded = voprf.blindWith(hex(INPUT), hex(BLIND))
            expect(blinded.blindedElement, BLINDED)
            check = "blind-evaluate"
            val evaluation = voprf.blindEvaluateWith(keyPair, listOf(blinded.blindedElement), hex(PROOF_RANDOM))
            expect(evaluation.evaluatedElements.single(), EVALUATED)
            expect(evaluation.proof!!, PROOF)
            check = "finalize"
            expect(voprf.finalize(hex(INPUT), blinded, evaluation.evaluatedElements.single(), evaluation.proof, keyPair.publicKey), OUTPUT)
            expect(voprf.evaluate(keyPair.secretKey, hex(INPUT)), OUTPUT)
            check = "tampered-proof"
            val tampered = evaluation.proof.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
            val refused = try {
                voprf.finalize(hex(INPUT), blinded, evaluation.evaluatedElements.single(), tampered, keyPair.publicKey)
                false
            } catch (e: VoprfError) {
                e.reason == VoprfError.Reason.VERIFY
            }
            if (!refused) throw IllegalStateException(check)
            check = "random-round-trip"
            val fresh = voprf.deriveKeyPair(ByteArray(Voprf.SEED_SIZE) { 7 }, "ppf-autotest".encodeToByteArray())
            val input = group.randomScalar()
            val freshBlinded = voprf.blind(input)
            val freshEvaluation = voprf.blindEvaluate(fresh, listOf(freshBlinded.blindedElement))
            val output = voprf.finalize(input, freshBlinded, freshEvaluation.evaluatedElements.single(), freshEvaluation.proof, fresh.publicKey)
            if (!Voprf.constantTimeEquals(output, voprf.evaluate(fresh.secretKey, input))) throw IllegalStateException(check)
            UrneSelfTestResult(passed = true, failedCheck = null)
        } catch (e: Throwable) {
            UrneSelfTestResult(passed = false, failedCheck = check)
        }
    }

    private fun expect(actual: ByteArray, expectedHex: String) {
        if (!Voprf.constantTimeEquals(actual, hex(expectedHex))) throw IllegalStateException("mismatch")
    }

    private fun hex(value: String): ByteArray = value.hexToByteArray()

    // RFC 9497, Appendix A.1.2 (VOPRF mode, ristretto255-SHA512), test vector 1.
    private const val SEED = "a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3"
    private const val KEY_INFO = "74657374206b6579"
    private const val SK = "e6f73f344b79b379f1a0dd37e07ff62e38d9f71345ce62ae3a9bc60b04ccd909"
    private const val PK = "c803e2cc6b05fc15064549b5920659ca4a77b2cca6f04f6b357009335476ad4e"
    private const val INPUT = "00"
    private const val BLIND = "64d37aed22a27f5191de1c1d69fadb899d8862b58eb4220029e036ec4c1f6706"
    private const val BLINDED = "863f330cc1a1259ed5a5998a23acfd37fb4351a793a5b3c090b642ddc439b945"
    private const val EVALUATED = "aa8fa048764d5623868679402ff6108d2521884fa138cd7f9c7669a9a014267e"
    private const val PROOF = "ddef93772692e535d1a53903db24367355cc2cc78de93b3be5a8ffcc6985dd066d4346421d17bf5117a2a1ff0fcb2a759f58a539dfbe857a40bce4cf49ec600d"
    private const val PROOF_RANDOM = "222a5e897cf59db8145db8d16e597e8facb80ae7d4e26d9881aa6f61d645fc0e"
    private const val OUTPUT = "b58cfbe118e0cb94d79b5fd6a6dafb98764dff49c14e1770b566e42402da1a7da4d8527693914139caee5bd03903af43a491351d23b430948dd50cde10d32b3c"
}
