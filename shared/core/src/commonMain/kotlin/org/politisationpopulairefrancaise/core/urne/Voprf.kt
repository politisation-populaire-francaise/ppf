package org.politisationpopulairefrancaise.core.urne

import fr.acinq.bitcoin.crypto.Digest

/** Why a step of the protocol failed. Never carries a key, an input or a token (D-110, R1). */
class VoprfError(val reason: Reason) : Exception(reason.name) {
    enum class Reason {
        /** The input hashes to the identity element (RFC 9497, InvalidInputError). */
        INVALID_INPUT,

        /** Bytes that are not a canonical element or scalar, or the identity (DeserializeError). */
        DESERIALIZE,

        /** The proof does not match the public key and the elements (VerifyError). */
        VERIFY,

        /** DeriveKeyPair found no non-zero key in 256 tries (DeriveKeyPairError). */
        DERIVE_KEY_PAIR,

        /** The group library refused an operation. */
        GROUP_OPERATION,
    }
}

/** RFC 9497 modes used here. POPRF is deliberately left out (D-110: per-ballot-box keys are published). */
enum class VoprfMode(val id: Int) {
    OPRF(0x00),
    VOPRF(0x01),
}

/** A server key pair: canonical scalar and element encodings. */
class VoprfKeyPair(val secretKey: ByteArray, val publicKey: ByteArray)

/** What the client keeps between [Voprf.blind] and [Voprf.finalize]: the blind stays on the device. */
class VoprfBlinded(val blind: ByteArray, val blindedElement: ByteArray)

/** The server's answer: one evaluated element per blinded element, and the proof in VOPRF mode. */
class VoprfEvaluation(val evaluatedElements: List<ByteArray>, val proof: ByteArray?)

/**
 * The OPRF and VOPRF modes of RFC 9497, ciphersuite ristretto255-SHA512 (identifier
 * `ristretto255-SHA512`), written from the RFC and checked against its Appendix A.1.1 and A.1.2.
 *
 * Every function follows the pseudocode of the RFC, with its names. Sizes: Ne = Ns = 32 bytes,
 * Nh = 64 bytes (SHA-512). Inputs are at most 65 535 bytes (I2OSP on two bytes).
 * The random values (blind, proof scalar) can be given for the test vectors; otherwise they come
 * from [Ristretto255.randomScalar].
 */
class Voprf(private val group: Ristretto255, val mode: VoprfMode) {

    /** `"OPRFV1-" || I2OSP(mode, 1) || "-" || identifier` (RFC 9497, 3.1). */
    val contextString: ByteArray = "OPRFV1-".encodeToByteArray() + byteArrayOf(mode.id.toByte()) + "-ristretto255-SHA512".encodeToByteArray()

    private val hashToGroupDst = "HashToGroup-".encodeToByteArray() + contextString
    private val hashToScalarDst = "HashToScalar-".encodeToByteArray() + contextString
    private val deriveKeyPairDst = "DeriveKeyPair".encodeToByteArray() + contextString
    private val seedDst = "Seed-".encodeToByteArray() + contextString

    // ---------------------------------------------------------------- keys (3.2.1)

    /** DeriveKeyPair(seed, info): the same seed and info always give the same key pair. */
    fun deriveKeyPair(seed: ByteArray, info: ByteArray): VoprfKeyPair {
        require(seed.size == SEED_SIZE) { "the seed is $SEED_SIZE bytes" }
        val deriveInput = seed + i2osp(info.size, 2) + info
        for (counter in 0..255) {
            val skS = hashToScalar(deriveInput + i2osp(counter, 1), deriveKeyPairDst)
            if (!Ristretto255.isZero(skS)) return VoprfKeyPair(skS, group.multiplyBase(skS))
        }
        throw VoprfError(VoprfError.Reason.DERIVE_KEY_PAIR)
    }

    // ---------------------------------------------------------------- client (3.3.1, 3.3.2)

    /** Blind(input): hides the input before it is sent to the server. */
    fun blind(input: ByteArray, blind: ByteArray = group.randomScalar()): VoprfBlinded {
        checkInput(input)
        val scalar = deserializeScalar(blind, nonZero = true)
        val inputElement = hashToGroup(input)
        if (isIdentity(inputElement)) throw VoprfError(VoprfError.Reason.INVALID_INPUT)
        return VoprfBlinded(scalar, group.multiply(scalar, inputElement))
    }

    /**
     * Finalize: checks the proof in VOPRF mode (against [publicKey]), removes the blind and
     * returns the 64-byte output. One input; [finalizeBatch] for several under one proof.
     */
    fun finalize(
        input: ByteArray,
        blinded: VoprfBlinded,
        evaluatedElement: ByteArray,
        proof: ByteArray? = null,
        publicKey: ByteArray? = null,
    ): ByteArray = finalizeBatch(listOf(input), listOf(blinded), listOf(evaluatedElement), proof, publicKey).single()

    fun finalizeBatch(
        inputs: List<ByteArray>,
        blinded: List<VoprfBlinded>,
        evaluatedElements: List<ByteArray>,
        proof: ByteArray? = null,
        publicKey: ByteArray? = null,
    ): List<ByteArray> {
        require(inputs.isNotEmpty() && inputs.size == blinded.size && inputs.size == evaluatedElements.size) { "one evaluation per input" }
        val evaluated = evaluatedElements.map { deserializeElement(it) }
        if (mode == VoprfMode.VOPRF) {
            val key = deserializeElement(publicKey ?: throw VoprfError(VoprfError.Reason.VERIFY))
            if (!verifyProof(GENERATOR, key, blinded.map { it.blindedElement }, evaluated, proof ?: throw VoprfError(VoprfError.Reason.VERIFY))) {
                throw VoprfError(VoprfError.Reason.VERIFY)
            }
        }
        return inputs.indices.map { i ->
            checkInput(inputs[i])
            val unblinded = group.multiply(group.scalarInvert(blinded[i].blind), evaluated[i])
            finalizeHash(inputs[i], unblinded)
        }
    }

    // ---------------------------------------------------------------- server (3.3.1, 3.3.2)

    /**
     * BlindEvaluate: applies the secret key to blinded elements received from a client, and in
     * VOPRF mode proves that the key behind [VoprfKeyPair.publicKey] was used.
     */
    fun blindEvaluate(
        keyPair: VoprfKeyPair,
        blindedElements: List<ByteArray>,
        proofRandomScalar: ByteArray = group.randomScalar(),
    ): VoprfEvaluation {
        require(blindedElements.isNotEmpty()) { "at least one element" }
        val skS = deserializeScalar(keyPair.secretKey, nonZero = true)
        val blinded = blindedElements.map { deserializeElement(it) }
        val evaluated = blinded.map { group.multiply(skS, it) }
        val proof = if (mode == VoprfMode.VOPRF) {
            generateProof(skS, GENERATOR, deserializeElement(keyPair.publicKey), blinded, evaluated, deserializeScalar(proofRandomScalar, nonZero = true))
        } else {
            null
        }
        return VoprfEvaluation(evaluated, proof)
    }

    /** Evaluate: the output the client obtains for [input], computed directly with the secret key. */
    fun evaluate(secretKey: ByteArray, input: ByteArray): ByteArray {
        checkInput(input)
        val skS = deserializeScalar(secretKey, nonZero = true)
        val inputElement = hashToGroup(input)
        if (isIdentity(inputElement)) throw VoprfError(VoprfError.Reason.INVALID_INPUT)
        return finalizeHash(input, group.multiply(skS, inputElement))
    }

    // ---------------------------------------------------------------- proofs (2.2)

    internal fun generateProof(
        k: ByteArray,
        a: ByteArray,
        b: ByteArray,
        c: List<ByteArray>,
        d: List<ByteArray>,
        r: ByteArray,
    ): ByteArray {
        val (m, z) = computeComposites(b, c, d, k)
        val t2 = multiplyOrIdentity(r, a)
        val t3 = multiplyOrIdentity(r, m)
        val challenge = hashToScalar(challengeTranscript(b, m, z, t2, t3), hashToScalarDst)
        val s = group.scalarSub(r, group.scalarMul(challenge, k))
        return challenge + s
    }

    internal fun verifyProof(a: ByteArray, b: ByteArray, c: List<ByteArray>, d: List<ByteArray>, proof: ByteArray): Boolean {
        if (proof.size != 2 * Ristretto255.SCALAR_SIZE) return false
        val challenge = proof.copyOfRange(0, Ristretto255.SCALAR_SIZE)
        val s = proof.copyOfRange(Ristretto255.SCALAR_SIZE, 2 * Ristretto255.SCALAR_SIZE)
        if (!Ristretto255.isCanonicalScalar(challenge) || !Ristretto255.isCanonicalScalar(s)) return false
        val (m, z) = computeComposites(b, c, d, k = null)
        val t2 = addOrIdentity(multiplyOrIdentity(s, a), multiplyOrIdentity(challenge, b))
        val t3 = addOrIdentity(multiplyOrIdentity(s, m), multiplyOrIdentity(challenge, z))
        val expected = hashToScalar(challengeTranscript(b, m, z, t2, t3), hashToScalarDst)
        return constantTimeEquals(expected, challenge)
    }

    /**
     * ComputeComposites, and ComputeCompositesFast when the server passes its key [k]
     * (then Z = k * M, RFC 9497, 2.2.1).
     */
    private fun computeComposites(b: ByteArray, c: List<ByteArray>, d: List<ByteArray>, k: ByteArray?): Pair<ByteArray, ByteArray> {
        require(c.size == d.size && c.isNotEmpty()) { "as many evaluated elements as blinded ones" }
        val seedTranscript = i2osp(b.size, 2) + b + i2osp(seedDst.size, 2) + seedDst
        val seed = Digest.sha512().hash(seedTranscript)
        var m: ByteArray = Ristretto255.IDENTITY
        var z: ByteArray = Ristretto255.IDENTITY
        for (i in c.indices) {
            val compositeTranscript = i2osp(seed.size, 2) + seed + i2osp(i, 2) +
                i2osp(c[i].size, 2) + c[i] + i2osp(d[i].size, 2) + d[i] + "Composite".encodeToByteArray()
            val di = hashToScalar(compositeTranscript, hashToScalarDst)
            m = addOrIdentity(multiplyOrIdentity(di, c[i]), m)
            if (k == null) z = addOrIdentity(multiplyOrIdentity(di, d[i]), z)
        }
        if (k != null) z = multiplyOrIdentity(k, m)
        return m to z
    }

    private fun challengeTranscript(b: ByteArray, m: ByteArray, z: ByteArray, t2: ByteArray, t3: ByteArray): ByteArray =
        i2osp(b.size, 2) + b + i2osp(m.size, 2) + m + i2osp(z.size, 2) + z +
            i2osp(t2.size, 2) + t2 + i2osp(t3.size, 2) + t3 + "Challenge".encodeToByteArray()

    // ---------------------------------------------------------------- hashing (4.1, RFC 9380)

    /** HashToGroup: expand_message_xmd (SHA-512) to 64 bytes, then the ristretto255 one-way map. */
    internal fun hashToGroup(input: ByteArray): ByteArray =
        group.elementFromUniformBytes(expandMessageXmd(input, hashToGroupDst, 64))

    /** HashToScalar: expand_message_xmd (SHA-512) to 64 bytes, read little-endian, reduced modulo L. */
    internal fun hashToScalar(input: ByteArray, dst: ByteArray = hashToScalarDst): ByteArray =
        group.reduceWide(expandMessageXmd(input, dst, 64))

    private fun finalizeHash(input: ByteArray, element: ByteArray): ByteArray =
        Digest.sha512().hash(i2osp(input.size, 2) + input + i2osp(element.size, 2) + element + "Finalize".encodeToByteArray())

    // ---------------------------------------------------------------- encodings

    /** DeserializeElement: canonical, in the group, and never the identity (RFC 9497, 2.1). */
    private fun deserializeElement(bytes: ByteArray): ByteArray {
        if (bytes.size != Ristretto255.ELEMENT_SIZE || Ristretto255.isZero(bytes) || !group.isValidElement(bytes)) {
            throw VoprfError(VoprfError.Reason.DESERIALIZE)
        }
        return bytes.copyOf()
    }

    private fun deserializeScalar(bytes: ByteArray, nonZero: Boolean): ByteArray {
        if (!Ristretto255.isCanonicalScalar(bytes) || (nonZero && Ristretto255.isZero(bytes))) {
            throw VoprfError(VoprfError.Reason.DESERIALIZE)
        }
        return bytes.copyOf()
    }

    private fun isIdentity(element: ByteArray): Boolean = Ristretto255.isZero(element)

    private fun multiplyOrIdentity(scalar: ByteArray, element: ByteArray): ByteArray = when {
        Ristretto255.isZero(scalar) || isIdentity(element) -> Ristretto255.IDENTITY
        // Same result, about three times faster: libsodium's fixed-base multiplication.
        element.contentEquals(GENERATOR) -> group.multiplyBase(scalar)
        else -> group.multiply(scalar, element)
    }

    private fun addOrIdentity(p: ByteArray, q: ByteArray): ByteArray = when {
        isIdentity(p) -> q
        isIdentity(q) -> p
        else -> group.add(p, q)
    }

    private fun checkInput(input: ByteArray) {
        if (input.size > MAX_INPUT_SIZE) throw VoprfError(VoprfError.Reason.INVALID_INPUT)
    }

    private val GENERATOR: ByteArray by lazy { group.multiplyBase(ONE) }

    companion object {
        const val SEED_SIZE: Int = 32
        const val OUTPUT_SIZE: Int = 64
        const val PROOF_SIZE: Int = 64
        const val MAX_INPUT_SIZE: Int = 0xffff

        private val ONE: ByteArray = ByteArray(Ristretto255.SCALAR_SIZE).also { it[0] = 1 }

        /** I2OSP: big-endian encoding of [value] on [length] bytes. */
        internal fun i2osp(value: Int, length: Int): ByteArray {
            require(value >= 0 && (length >= 4 || value < (1 shl (8 * length)))) { "value too large for $length bytes" }
            return ByteArray(length) { i -> (value ushr (8 * (length - 1 - i))).toByte() }
        }

        /** expand_message_xmd of RFC 9380 (5.3.1) with SHA-512 (b_in_bytes = 64, s_in_bytes = 128). */
        internal fun expandMessageXmd(message: ByteArray, dst: ByteArray, lengthInBytes: Int): ByteArray {
            val bInBytes = 64
            val ell = (lengthInBytes + bInBytes - 1) / bInBytes
            require(ell in 1..255 && lengthInBytes <= 65535 && dst.size <= 255) { "expand_message_xmd: lengths out of range" }
            val dstPrime = dst + i2osp(dst.size, 1)
            val zPad = ByteArray(128)
            val msgPrime = zPad + message + i2osp(lengthInBytes, 2) + i2osp(0, 1) + dstPrime
            val b0 = Digest.sha512().hash(msgPrime)
            var previous = Digest.sha512().hash(b0 + i2osp(1, 1) + dstPrime)
            val out = ByteArray(ell * bInBytes)
            previous.copyInto(out, 0)
            for (i in 2..ell) {
                val mixed = ByteArray(bInBytes) { j -> (b0[j].toInt() xor previous[j].toInt()).toByte() }
                previous = Digest.sha512().hash(mixed + i2osp(i, 1) + dstPrime)
                previous.copyInto(out, (i - 1) * bInBytes)
            }
            return out.copyOf(lengthInBytes)
        }

        /** Comparison whose duration does not depend on where the arrays differ. */
        fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            return diff == 0
        }
    }
}
