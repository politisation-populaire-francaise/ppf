package org.politisationpopulairefrancaise.server.push

import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** The receiving side of RFC 8291, as a device would do it: only for the tests. */
internal fun decryptWebPush(body: ByteArray, userAgentPrivate: ECPrivateKey, userAgentPublic: ByteArray, authSecret: ByteArray): ByteArray {
    val buffer = ByteBuffer.wrap(body)
    val salt = ByteArray(16).also(buffer::get)
    val recordSize = buffer.int
    assertEquals(4096, recordSize)
    val keyIdLength = buffer.get().toInt()
    val serverPublic = ByteArray(keyIdLength).also(buffer::get)
    val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
    val secret = P256.ecdh(userAgentPrivate, P256.decodePoint(serverPublic))
    val (key, nonce) = WebPushEncryption.deriveKeyAndNonce(secret, authSecret, userAgentPublic, serverPublic, salt)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    val padded = cipher.doFinal(ciphertext)
    val end = padded.indexOfLast { it != 0.toByte() }
    assertEquals(0x02.toByte(), padded[end], "last record delimiter")
    return padded.copyOf(end)
}

/** A PKCS#8 PEM, built here so that no key block ever appears in the sources. */
internal fun pemOf(privateKey: ECPrivateKey): String {
    val label = "PRIVATE" + " KEY"
    return "-----BEGIN $label-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKey.encoded) +
        "\n-----END $label-----\n"
}

class WebPushCryptoTest {

    // RFC 8291, appendix A (and section 5): the published example, keys of the RFC itself.
    private val plaintext = "When I grow up, I want to be a watermelon".encodeToByteArray()
    private val asPrivate = Base64Url.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")
    private val asPublic = Base64Url.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")
    private val uaPrivate = Base64Url.decode("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94")
    private val uaPublic = Base64Url.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4")
    private val authSecret = Base64Url.decode("BTBZMqHH6r4Tts7J_aSIgg")
    private val salt = Base64Url.decode("DGv6ra1nlYgDCS1FRnbzlw")
    private val expectedBody = Base64Url.decode(
        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml" +
            "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT" +
            "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
    )

    @Test
    fun derivesTheRfcKeysAndNonce() {
        val secret = P256.ecdh(P256.privateKey(asPrivate), P256.decodePoint(uaPublic))
        assertContentEquals(Base64Url.decode("kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs"), secret)
        val (key, nonce) = WebPushEncryption.deriveKeyAndNonce(secret, authSecret, uaPublic, asPublic, salt)
        assertContentEquals(Base64Url.decode("oIhVW04MRdy2XN9CiKLxTg"), key)
        assertContentEquals(Base64Url.decode("4h_95klXJ5E_qnoN"), nonce)
    }

    @Test
    fun encryptsTheRfcExampleExactly() {
        val serverKeys = KeyPair(P256.decodePoint(asPublic), P256.privateKey(asPrivate))
        val body = WebPushEncryption.encrypt(plaintext, uaPublic, authSecret, serverKeys, salt)
        assertContentEquals(expectedBody, body)
    }

    @Test
    fun decryptsTheRfcExample() {
        assertContentEquals(plaintext, decryptWebPush(expectedBody, P256.privateKey(uaPrivate), uaPublic, authSecret))
    }

    @Test
    fun roundTripsTheWakeUpWithALocalReceiver() {
        val receiver = P256.generateKeyPair()
        val receiverPublic = P256.encodePoint(receiver.public as ECPublicKey)
        val auth = ByteArray(16) { it.toByte() }
        val first = WebPushEncryption.encrypt(WebPushSender.PAYLOAD, receiverPublic, auth)
        val second = WebPushEncryption.encrypt(WebPushSender.PAYLOAD, receiverPublic, auth)
        assertContentEquals("""{"v":1}""".encodeToByteArray(), decryptWebPush(first, receiver.private as ECPrivateKey, receiverPublic, auth))
        // A fresh key and salt every time: two identical wake-ups never look the same.
        assertTrue(!first.contentEquals(second))
    }

    @Test
    fun refusesPointsOutsideTheCurve() {
        val tampered = uaPublic.copyOf().also { it[64] = (it[64] + 1).toByte() }
        kotlin.test.assertFailsWith<IllegalArgumentException> { P256.decodePoint(tampered) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { P256.decodePoint(ByteArray(65) { 4 }) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { P256.decodePoint(uaPublic.copyOf(33)) }
    }

    @Test
    fun derivesThePublicKeyFromThePrivateKey() {
        assertContentEquals(asPublic, P256.encodePoint(P256.publicKeyOf(P256.privateKey(asPrivate))))
        repeat(5) {
            val pair = P256.generateKeyPair()
            assertEquals(pair.public, P256.publicKeyOf(pair.private as ECPrivateKey))
        }
    }

    @Test
    fun vapidTokenIsVerifiableWithThePublishedKey() {
        val pair = P256.generateKeyPair()
        val file = Files.createTempFile("vapid-test", ".pem")
        try {
            Files.writeString(file, pemOf(pair.private as ECPrivateKey))
            val vapid = VapidKeys.fromFile(file)
            assertEquals(Base64Url.encode(P256.encodePoint(pair.public as ECPublicKey)), vapid.publicKeyBase64Url)

            val now = Instant.ofEpochSecond(1_800_000_000)
            val header = vapid.authorization(java.net.URI("https://push.example.org/upAbc?up=1"), "https://site.example.org", now)
            val match = Regex("^vapid t=([^,]+), k=(.+)$").matchEntire(header)!!
            val (token, key) = match.destructured
            assertEquals(vapid.publicKeyBase64Url, key)

            val (encodedHeader, encodedClaims, encodedSignature) = token.split('.')
            val published = P256.decodePoint(Base64Url.decode(key))
            val valid = Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initVerify(published)
                update("$encodedHeader.$encodedClaims".encodeToByteArray())
                verify(Base64Url.decode(encodedSignature))
            }
            assertTrue(valid)
            assertEquals(64, Base64Url.decode(encodedSignature).size)
            val jwtHeader = Json.parseToJsonElement(Base64Url.decode(encodedHeader).decodeToString()) as JsonObject
            assertEquals("ES256", jwtHeader["alg"]!!.jsonPrimitive.content)
            val claims = Json.parseToJsonElement(Base64Url.decode(encodedClaims).decodeToString()) as JsonObject
            assertEquals("https://push.example.org", claims["aud"]!!.jsonPrimitive.content)
            assertEquals("https://site.example.org", claims["sub"]!!.jsonPrimitive.content)
            assertEquals(now.epochSecond + VapidKeys.TOKEN_LIFETIME_SECONDS, claims["exp"]!!.jsonPrimitive.long)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
