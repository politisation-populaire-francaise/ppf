package org.politisationpopulairefrancaise.server.push

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * P-256 helpers built on the JDK only (no new dependency, contract section 4): uncompressed points
 * (`0x04 || x || y`, 65 bytes), keys from raw values, ECDH and HMAC-SHA-256.
 */
object P256 {
    const val POINT_BYTES: Int = 65
    private const val COORDINATE_BYTES: Int = 32

    val parameters: ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }

    private val keyFactory: KeyFactory get() = KeyFactory.getInstance("EC")

    fun generateKeyPair(random: SecureRandom = SecureRandom()): KeyPair =
        KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"), random)
            generateKeyPair()
        }

    fun encodePoint(key: ECPublicKey): ByteArray =
        byteArrayOf(0x04) + unsigned(key.w.affineX) + unsigned(key.w.affineY)

    /** The public key of an uncompressed point; throws when it is not a point of the curve. */
    fun decodePoint(bytes: ByteArray): ECPublicKey {
        require(bytes.size == POINT_BYTES && bytes[0] == 0x04.toByte()) { "Not an uncompressed P-256 point" }
        val x = BigInteger(1, bytes.copyOfRange(1, 1 + COORDINATE_BYTES))
        val y = BigInteger(1, bytes.copyOfRange(1 + COORDINATE_BYTES, POINT_BYTES))
        // The key factory of the JDK does not check that the point lies on the curve: done here, so
        // that no ECDH is ever computed with an invalid point.
        require(isOnCurve(x, y)) { "Not a point of P-256" }
        return keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), parameters)) as ECPublicKey
    }

    /** `y² = x³ + ax + b (mod p)`, with both coordinates in the field. */
    fun isOnCurve(x: BigInteger, y: BigInteger): Boolean {
        val p = (parameters.curve.field as java.security.spec.ECFieldFp).p
        if (x.signum() < 0 || y.signum() < 0 || x >= p || y >= p) return false
        val rhs = x.pow(3).add(parameters.curve.a.multiply(x)).add(parameters.curve.b).mod(p)
        return y.multiply(y).mod(p) == rhs
    }

    fun privateKey(scalar: ByteArray): ECPrivateKey =
        keyFactory.generatePrivate(ECPrivateKeySpec(BigInteger(1, scalar), parameters)) as ECPrivateKey

    fun ecdh(privateKey: ECPrivateKey, publicKey: ECPublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }

    /**
     * The public key of [privateKey], with JDK primitives only: ECDH with the generator gives the x
     * coordinate of `d·G`; y is one of the two square roots of `x³ - 3x + b` (P-256 has p ≡ 3 mod 4);
     * a test signature tells which one.
     */
    fun publicKeyOf(privateKey: ECPrivateKey): ECPublicKey {
        val generator = keyFactory.generatePublic(ECPublicKeySpec(parameters.generator, parameters)) as ECPublicKey
        val x = BigInteger(1, ecdh(privateKey, generator))
        val p = (parameters.curve.field as java.security.spec.ECFieldFp).p
        val a = parameters.curve.a
        val b = parameters.curve.b
        val rhs = x.pow(3).add(a.multiply(x)).add(b).mod(p)
        val root = rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p)
        val message = "ppf-vapid-key-check".encodeToByteArray()
        val signature = java.security.Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(message)
            sign()
        }
        for (y in listOf(root, p.subtract(root).mod(p))) {
            val candidate = runCatching {
                keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), parameters)) as ECPublicKey
            }.getOrNull() ?: continue
            val verifies = java.security.Signature.getInstance("SHA256withECDSA").run {
                initVerify(candidate)
                update(message)
                verify(signature)
            }
            if (verifies) return candidate
        }
        throw IllegalArgumentException("Not a valid P-256 private key")
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

    private fun unsigned(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == COORDINATE_BYTES -> bytes
            bytes.size > COORDINATE_BYTES -> bytes.copyOfRange(bytes.size - COORDINATE_BYTES, bytes.size)
            else -> ByteArray(COORDINATE_BYTES - bytes.size) + bytes
        }
    }
}

/** Base64url without padding, as Web Push uses it; decoding accepts padding. */
object Base64Url {
    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decode(text: String): ByteArray = Base64.getUrlDecoder().decode(text.trimEnd('='))

    fun decodeOrNull(text: String): ByteArray? = runCatching { decode(text) }.getOrNull()
}

/**
 * Message encryption for Web Push (RFC 8291) with the `aes128gcm` content coding (RFC 8188), in a
 * single record. Only the JDK is used: ECDH P-256, HKDF with HmacSHA256, AES/GCM/NoPadding.
 */
object WebPushEncryption {
    const val AUTH_SECRET_BYTES: Int = 16
    private const val SALT_BYTES: Int = 16
    private const val RECORD_SIZE: Int = 4096
    private const val TAG_BITS: Int = 128
    private const val PADDING_DELIMITER: Byte = 0x02

    /**
     * The encrypted body for a subscription whose keys are [userAgentPublic] (65-byte point) and
     * [authSecret] (16 bytes). [serverKeys] and [salt] are fresh for every message; tests fix them
     * to check the RFC 8291 example.
     */
    fun encrypt(
        plaintext: ByteArray,
        userAgentPublic: ByteArray,
        authSecret: ByteArray,
        serverKeys: KeyPair = P256.generateKeyPair(),
        salt: ByteArray = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes),
    ): ByteArray {
        require(authSecret.size == AUTH_SECRET_BYTES) { "The auth secret is 16 bytes" }
        require(salt.size == SALT_BYTES) { "The salt is 16 bytes" }
        require(plaintext.size + 1 + TAG_BITS / 8 <= RECORD_SIZE) { "One record only" }
        val uaPublic = P256.decodePoint(userAgentPublic)
        val asPublic = P256.encodePoint(serverKeys.public as ECPublicKey)
        val (key, nonce) = deriveKeyAndNonce(
            ecdhSecret = P256.ecdh(serverKeys.private as ECPrivateKey, uaPublic),
            authSecret = authSecret,
            userAgentPublic = userAgentPublic,
            serverPublic = asPublic,
            salt = salt,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext + PADDING_DELIMITER)
        val header = ByteBuffer.allocate(SALT_BYTES + 4 + 1 + asPublic.size)
            .put(salt)
            .putInt(RECORD_SIZE)
            .put(asPublic.size.toByte())
            .put(asPublic)
            .array()
        return header + ciphertext
    }

    /** RFC 8291 section 3.4 and RFC 8188 section 2.2: the content encryption key and the nonce. */
    internal fun deriveKeyAndNonce(
        ecdhSecret: ByteArray,
        authSecret: ByteArray,
        userAgentPublic: ByteArray,
        serverPublic: ByteArray,
        salt: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val prkKey = P256.hmacSha256(authSecret, ecdhSecret)
        val keyInfo = "WebPush: info".encodeToByteArray() + ZERO + userAgentPublic + serverPublic
        val ikm = expand(prkKey, keyInfo, 32)
        val prk = P256.hmacSha256(salt, ikm)
        val cek = expand(prk, "Content-Encoding: aes128gcm".encodeToByteArray() + ZERO, 16)
        val nonce = expand(prk, "Content-Encoding: nonce".encodeToByteArray() + ZERO, 12)
        return cek to nonce
    }

    /** HKDF-Expand for an output of at most 32 bytes (a single HMAC block). */
    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray =
        P256.hmacSha256(prk, info + ONE).copyOf(length)
}

private val ZERO = byteArrayOf(0)
private val ONE = byteArrayOf(1)
