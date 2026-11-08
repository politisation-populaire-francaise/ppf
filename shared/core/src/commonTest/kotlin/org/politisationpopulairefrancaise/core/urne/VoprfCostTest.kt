package org.politisationpopulairefrancaise.core.urne

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

/**
 * Cost of one token (D-110, T5): what the server spends to issue it, what the device spends to
 * blind it and check the proof. Printed as a line starting with `URNE-COST` (no time assertion,
 * machines differ); it feeds the volume estimate of `docs/integration/une-personne-une-voix.md`.
 */
class VoprfCostTest {

    @Test
    fun measureTheCostOfOneToken() = runTest {
        val group = LibsodiumRistretto255.load()
        val voprf = Voprf(group, VoprfMode.VOPRF)
        val keyPair = voprf.deriveKeyPair(ByteArray(Voprf.SEED_SIZE) { 3 }, "ppf-urne-v1|cout|N0".encodeToByteArray())
        val rounds = 300
        val inputs = List(rounds) { group.randomScalar() }
        // Warm-up (JIT), not measured.
        repeat(30) { i ->
            val b = voprf.blind(inputs[i])
            val e = voprf.blindEvaluate(keyPair, listOf(b.blindedElement))
            voprf.finalize(inputs[i], b, e.evaluatedElements.single(), e.proof, keyPair.publicKey)
        }
        val blinded = ArrayList<VoprfBlinded>(rounds)
        var mark = TimeSource.Monotonic.markNow()
        inputs.forEach { blinded += voprf.blind(it) }
        val blindTime = mark.elapsedNow()
        mark = TimeSource.Monotonic.markNow()
        val evaluations = blinded.map { voprf.blindEvaluate(keyPair, listOf(it.blindedElement)) }
        val serverTime = mark.elapsedNow()
        mark = TimeSource.Monotonic.markNow()
        val outputs = inputs.indices.map { i -> voprf.finalize(inputs[i], blinded[i], evaluations[i].evaluatedElements.single(), evaluations[i].proof, keyPair.publicKey) }
        val finalizeTime = mark.elapsedNow()
        mark = TimeSource.Monotonic.markNow()
        inputs.forEach { voprf.evaluate(keyPair.secretKey, it) }
        val checkTime = mark.elapsedNow()
        assertEquals(rounds, outputs.size)
        println(
            "URNE-COST per token: server issue ${serverTime.inWholeMicroseconds / rounds} µs, " +
                "server check of a ballot ${checkTime.inWholeMicroseconds / rounds} µs, " +
                "device blind ${blindTime.inWholeMicroseconds / rounds} µs + finalize with proof ${finalizeTime.inWholeMicroseconds / rounds} µs",
        )
    }
}
