package org.politisationpopulairefrancaise.core.urne

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RFC 9497, Appendix A.1.1 (OPRF) and A.1.2 (VOPRF), ciphersuite ristretto255-SHA512, every
 * vector, then the refusals the protocol must make (D-110, condition a). Values copied from
 * https://www.rfc-editor.org/rfc/rfc9497.txt on 7 November 2026.
 */
class VoprfVectorsTest {

    private fun h(value: String): ByteArray = value.hexToByteArray()

    private val seed = h("a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3")
    private val keyInfo = h("74657374206b6579")
    private val blind1 = "64d37aed22a27f5191de1c1d69fadb899d8862b58eb4220029e036ec4c1f6706"
    private val input1 = "00"
    private val input2 = "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a"

    private class Vector(
        val input: String,
        val blind: String,
        val blindedElement: String,
        val evaluationElement: String,
        val output: String,
        val proof: String? = null,
        val proofRandomScalar: String? = null,
    )

    private val oprfVectors = listOf(
        Vector(
            input1, blind1,
            "609a0ae68c15a3cf6903766461307e5c8bb2f95e7e6550e1ffa2dc99e412803c",
            "7ec6578ae5120958eb2db1745758ff379e77cb64fe77b0b2d8cc917ea0869c7e",
            "527759c3d9366f277d8c6020418d96bb393ba2afb20ff90df23fb7708264e2f3ab9135e3bd69955851de4b1f9fe8a0973396719b7912ba9ee8aa7d0b5e24bcf6",
        ),
        Vector(
            input2, blind1,
            "da27ef466870f5f15296299850aa088629945a17d1f5b7f5ff043f76b3c06418",
            "b4cbf5a4f1eeda5a63ce7b77c7d23f461db3fcab0dd28e4e17cecb5c90d02c25",
            "f4a74c9c592497375e796aa837e907b1a045d34306a749db9f34221f7e750cb4f2a6413a6bf6fa5e19ba6348eb673934a722a7ede2e7621306d18951e7cf2c73",
        ),
    )

    private val voprfVectors = listOf(
        Vector(
            input1, blind1,
            "863f330cc1a1259ed5a5998a23acfd37fb4351a793a5b3c090b642ddc439b945",
            "aa8fa048764d5623868679402ff6108d2521884fa138cd7f9c7669a9a014267e",
            "b58cfbe118e0cb94d79b5fd6a6dafb98764dff49c14e1770b566e42402da1a7da4d8527693914139caee5bd03903af43a491351d23b430948dd50cde10d32b3c",
            "ddef93772692e535d1a53903db24367355cc2cc78de93b3be5a8ffcc6985dd066d4346421d17bf5117a2a1ff0fcb2a759f58a539dfbe857a40bce4cf49ec600d",
            "222a5e897cf59db8145db8d16e597e8facb80ae7d4e26d9881aa6f61d645fc0e",
        ),
        Vector(
            input2, blind1,
            "cc0b2a350101881d8a4cba4c80241d74fb7dcbfde4a61fde2f91443c2bf9ef0c",
            "60a59a57208d48aca71e9e850d22674b611f752bed48b36f7a91b372bd7ad468",
            "8a9a2f3c7f085b65933594309041fc1898d42d0858e59f90814ae90571a6df60356f4610bf816f27afdd84f47719e480906d27ecd994985890e5f539e7ea74b6",
            "401a0da6264f8cf45bb2f5264bc31e109155600babb3cd4e5af7d181a2c9dc0a67154fabf031fd936051dec80b0b6ae29c9503493dde7393b722eafdf5a50b02",
            "222a5e897cf59db8145db8d16e597e8facb80ae7d4e26d9881aa6f61d645fc0e",
        ),
    )

    private suspend fun voprf(mode: VoprfMode) = Voprf(LibsodiumRistretto255.load(), mode)

    @Test
    fun theKeyPairsAreDerivedAsInTheRfc() = runTest {
        assertContentEquals(h("5ebcea5ee37023ccb9fc2d2019f9d7737be85591ae8652ffa9ef0f4d37063b0e"), voprf(VoprfMode.OPRF).deriveKeyPair(seed, keyInfo).secretKey)
        val keyPair = voprf(VoprfMode.VOPRF).deriveKeyPair(seed, keyInfo)
        assertContentEquals(h("e6f73f344b79b379f1a0dd37e07ff62e38d9f71345ce62ae3a9bc60b04ccd909"), keyPair.secretKey)
        assertContentEquals(h("c803e2cc6b05fc15064549b5920659ca4a77b2cca6f04f6b357009335476ad4e"), keyPair.publicKey)
    }

    @Test
    fun theOprfVectorsMatch() = runTest {
        val oprf = voprf(VoprfMode.OPRF)
        val keyPair = oprf.deriveKeyPair(seed, keyInfo)
        oprfVectors.forEach { v ->
            val blinded = oprf.blind(h(v.input), h(v.blind))
            assertContentEquals(h(v.blindedElement), blinded.blindedElement)
            val evaluation = oprf.blindEvaluate(keyPair, listOf(blinded.blindedElement))
            assertEquals(null, evaluation.proof)
            assertContentEquals(h(v.evaluationElement), evaluation.evaluatedElements.single())
            assertContentEquals(h(v.output), oprf.finalize(h(v.input), blinded, evaluation.evaluatedElements.single()))
            assertContentEquals(h(v.output), oprf.evaluate(keyPair.secretKey, h(v.input)))
        }
    }

    @Test
    fun theVoprfVectorsMatch() = runTest {
        val voprf = voprf(VoprfMode.VOPRF)
        val keyPair = voprf.deriveKeyPair(seed, keyInfo)
        voprfVectors.forEach { v ->
            val blinded = voprf.blind(h(v.input), h(v.blind))
            assertContentEquals(h(v.blindedElement), blinded.blindedElement)
            val evaluation = voprf.blindEvaluate(keyPair, listOf(blinded.blindedElement), h(v.proofRandomScalar!!))
            assertContentEquals(h(v.evaluationElement), evaluation.evaluatedElements.single())
            assertContentEquals(h(v.proof!!), evaluation.proof)
            val output = voprf.finalize(h(v.input), blinded, evaluation.evaluatedElements.single(), evaluation.proof, keyPair.publicKey)
            assertContentEquals(h(v.output), output)
            assertContentEquals(h(v.output), voprf.evaluate(keyPair.secretKey, h(v.input)))
        }
    }

    @Test
    fun theBatchVoprfVectorMatches() = runTest {
        val voprf = voprf(VoprfMode.VOPRF)
        val keyPair = voprf.deriveKeyPair(seed, keyInfo)
        val inputs = listOf(h(input1), h(input2))
        val blinded = listOf(
            voprf.blind(inputs[0], h(blind1)),
            voprf.blind(inputs[1], h("222a5e897cf59db8145db8d16e597e8facb80ae7d4e26d9881aa6f61d645fc0e")),
        )
        assertContentEquals(h("863f330cc1a1259ed5a5998a23acfd37fb4351a793a5b3c090b642ddc439b945"), blinded[0].blindedElement)
        assertContentEquals(h("90a0145ea9da29254c3a56be4fe185465ebb3bf2a1801f7124bbbadac751e654"), blinded[1].blindedElement)
        val evaluation = voprf.blindEvaluate(keyPair, blinded.map { it.blindedElement }, h("419c4f4f5052c53c45f3da494d2b67b220d02118e0857cdbcf037f9ea84bbe0c"))
        assertContentEquals(h("aa8fa048764d5623868679402ff6108d2521884fa138cd7f9c7669a9a014267e"), evaluation.evaluatedElements[0])
        assertContentEquals(h("cc5ac221950a49ceaa73c8db41b82c20372a4c8d63e5dded2db920b7eee36a2a"), evaluation.evaluatedElements[1])
        assertContentEquals(h("cc203910175d786927eeb44ea847328047892ddf8590e723c37205cb74600b0a5ab5337c8eb4ceae0494c2cf89529dcf94572ed267473d567aeed6ab873dee08"), evaluation.proof)
        val outputs = voprf.finalizeBatch(inputs, blinded, evaluation.evaluatedElements, evaluation.proof, keyPair.publicKey)
        assertContentEquals(h(voprfVectors[0].output), outputs[0])
        assertContentEquals(h(voprfVectors[1].output), outputs[1])
    }

    @Test
    fun theSelfTestPasses() = runTest {
        assertEquals(UrneSelfTestResult(passed = true, failedCheck = null), UrneSelfTest.run())
    }

    // ------------------------------------------------------------------ refusals

    private suspend fun fixture(): Triple<Voprf, VoprfKeyPair, Pair<VoprfBlinded, VoprfEvaluation>> {
        val voprf = voprf(VoprfMode.VOPRF)
        val keyPair = voprf.deriveKeyPair(seed, keyInfo)
        val blinded = voprf.blind(h(input1), h(blind1))
        val evaluation = voprf.blindEvaluate(keyPair, listOf(blinded.blindedElement))
        return Triple(voprf, keyPair, blinded to evaluation)
    }

    private fun reason(block: () -> Unit): VoprfError.Reason = assertFailsWith<VoprfError> { block() }.reason

    @Test
    fun aTamperedProofIsRefused() = runTest {
        val (voprf, keyPair, pair) = fixture()
        val (blinded, evaluation) = pair
        for (index in listOf(0, 31, 32, 63)) {
            val tampered = evaluation.proof!!.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertEquals(
                VoprfError.Reason.VERIFY,
                reason { voprf.finalize(h(input1), blinded, evaluation.evaluatedElements.single(), tampered, keyPair.publicKey) },
                "byte $index",
            )
        }
        assertEquals(VoprfError.Reason.VERIFY, reason { voprf.finalize(h(input1), blinded, evaluation.evaluatedElements.single(), null, keyPair.publicKey) })
        assertEquals(VoprfError.Reason.VERIFY, reason { voprf.finalize(h(input1), blinded, evaluation.evaluatedElements.single(), evaluation.proof!!.copyOf(63), keyPair.publicKey) })
    }

    @Test
    fun aKeyOtherThanThePublishedOneIsRefused() = runTest {
        // A server answering one citizen with another key (to recognise the token later) is caught.
        val (voprf, keyPair, pair) = fixture()
        val (blinded, _) = pair
        val other = voprf.deriveKeyPair(seed, "other key".encodeToByteArray())
        val evaluation = voprf.blindEvaluate(other, listOf(blinded.blindedElement))
        assertEquals(VoprfError.Reason.VERIFY, reason { voprf.finalize(h(input1), blinded, evaluation.evaluatedElements.single(), evaluation.proof, keyPair.publicKey) })
        assertEquals(VoprfError.Reason.VERIFY, reason { voprf.finalize(h(input1), blinded, evaluation.evaluatedElements.single(), evaluation.proof, null) })
    }

    @Test
    fun anEvaluatedElementSwappedInABatchIsRefused() = runTest {
        val voprf = voprf(VoprfMode.VOPRF)
        val keyPair = voprf.deriveKeyPair(seed, keyInfo)
        val inputs = listOf(h(input1), h(input2))
        val blinded = inputs.map { voprf.blind(it) }
        val evaluation = voprf.blindEvaluate(keyPair, blinded.map { it.blindedElement })
        val swapped = evaluation.evaluatedElements.reversed()
        assertEquals(VoprfError.Reason.VERIFY, reason { voprf.finalizeBatch(inputs, blinded, swapped, evaluation.proof, keyPair.publicKey) })
    }

    @Test
    fun theIdentityAndInvalidEncodingsAreRefused() = runTest {
        val (voprf, keyPair, pair) = fixture()
        val (blinded, evaluation) = pair
        val identity = ByteArray(32)
        val notAPoint = ByteArray(32) { 0xff.toByte() }
        assertEquals(VoprfError.Reason.DESERIALIZE, reason { voprf.blindEvaluate(keyPair, listOf(identity)) })
        assertEquals(VoprfError.Reason.DESERIALIZE, reason { voprf.blindEvaluate(keyPair, listOf(notAPoint)) })
        assertEquals(VoprfError.Reason.DESERIALIZE, reason { voprf.blindEvaluate(keyPair, listOf(ByteArray(31) { 1 })) })
        assertEquals(VoprfError.Reason.DESERIALIZE, reason { voprf.finalize(h(input1), blinded, identity, evaluation.proof, keyPair.publicKey) })
        assertEquals(VoprfError.Reason.DESERIALIZE, reason { voprf.finalize(h(input1), blinded, evaluation.evaluatedElements.single(), evaluation.proof, identity) })
    }

    @Test
    fun nonCanonicalOrZeroScalarsAreRefused() = runTest {
        val (voprf, keyPair, pair) = fixture()
        val (blinded, evaluation) = pair
        // The group order L itself, and 2^256 - 1: both outside [0, L).
        val order = h("edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010")
        val maximum = ByteArray(32) { 0xff.toByte() }
        assertFalse(Ristretto255.isCanonicalScalar(order))
        assertFalse(Ristretto255.isCanonicalScalar(maximum))
        assertTrue(Ristretto255.isCanonicalScalar(h("ecd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010")))
        assertEquals(VoprfError.Reason.DESERIALIZE, reason { voprf.blind(h(input1), order) })
        assertEquals(VoprfError.Reason.DESERIALIZE, reason { voprf.blind(h(input1), ByteArray(32)) })
        assertEquals(VoprfError.Reason.DESERIALIZE, reason { voprf.blindEvaluate(VoprfKeyPair(ByteArray(32), keyPair.publicKey), listOf(blinded.blindedElement)) })
        // A proof whose second half is not canonical is refused before any computation.
        val badProof = evaluation.proof!!.copyOf().also { order.copyInto(it, 32) }
        assertEquals(VoprfError.Reason.VERIFY, reason { voprf.finalize(h(input1), blinded, evaluation.evaluatedElements.single(), badProof, keyPair.publicKey) })
    }

    @Test
    fun aRandomRoundTripGivesTheServerOutputAndEachKeyItsOwnToken() = runTest {
        val group = LibsodiumRistretto255.load()
        val voprf = Voprf(group, VoprfMode.VOPRF)
        val urneA = voprf.deriveKeyPair(seed, "ppf-urne-v1|a|N2".encodeToByteArray())
        val urneB = voprf.deriveKeyPair(seed, "ppf-urne-v1|b|N2".encodeToByteArray())
        repeat(20) {
            val y = group.randomScalar()
            val blinded = voprf.blind(y)
            val evaluation = voprf.blindEvaluate(urneA, listOf(blinded.blindedElement))
            val token = voprf.finalize(y, blinded, evaluation.evaluatedElements.single(), evaluation.proof, urneA.publicKey)
            assertEquals(Voprf.OUTPUT_SIZE, token.size)
            assertContentEquals(voprf.evaluate(urneA.secretKey, y), token)
            assertFalse(Voprf.constantTimeEquals(voprf.evaluate(urneB.secretKey, y), token), "a token is valid for its own ballot box only")
        }
        // Two blindings of the same input look unrelated to the server.
        val y = group.randomScalar()
        assertFalse(voprf.blind(y).blindedElement.contentEquals(voprf.blind(y).blindedElement))
    }

    @Test
    fun theOprfAndVoprfContextsDiffer() = runTest {
        assertContentEquals("OPRFV1-\u0000-ristretto255-SHA512".encodeToByteArray(), voprf(VoprfMode.OPRF).contextString)
        assertContentEquals("OPRFV1-\u0001-ristretto255-SHA512".encodeToByteArray(), voprf(VoprfMode.VOPRF).contextString)
    }
}
