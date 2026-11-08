package org.politisationpopulairefrancaise.core.contact

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.crypto.HmacSha256
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaseSecretsTest {

    private val number = CaseNumber.fromDisplay("PPF-ABCDE-12345")!!
    private val code = CaseCode.parse("0123-4567-89AB-CDEF-GHJK")!!

    /** HKDF-SHA-256 written again from RFC 5869 with the bare HMAC, for one block of output (32 bytes). */
    private fun hkdfOneBlock(material: String, info: String): ByteArray {
        val prk = HmacSha256.compute("ppf-case-v1".encodeToByteArray(), material.encodeToByteArray())
        return HmacSha256.compute(prk, info.encodeToByteArray(), byteArrayOf(1))
    }

    @Test
    fun theDerivationsFollowTheContractAndMatchAnIndependentRecomputation() {
        val secrets = CaseSecrets.derive(number, code)
        val token = hkdfOneBlock("0123456789ABCDEFGHJK", "access PPF-ABCDE-12345")
        assertEquals(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(token), secrets.accessToken)
        assertEquals(Crypto.sha256(token).toHexString(), secrets.accessHash)
        assertEquals("Bearer ${secrets.accessToken}", secrets.authorization)
        assertEquals(0, secrets.keyCounter)
        val key = IdentityKeys.fromSecretKey(hkdfOneBlock("0123456789ABCDEFGHJK", "key PPF-ABCDE-12345 0"))
        assertEquals(key.publicKey, secrets.publicKey)
        // Fixed vector, recomputed outside this code (Python hashlib and hmac, pure secp256k1 arithmetic):
        // only the start of the public values is written here.
        assertEquals(PINNED_DIGEST_START, secrets.accessHash.take(16))
        assertEquals(PINNED_CASE_X_START, secrets.publicKey.hex.take(16))
        key.destroy()
        secrets.destroy()
    }

    @Test
    fun theSameNumberAndCodeGiveTheSameSecretsAndAnyChangeGivesOthers() {
        val first = CaseSecrets.derive(number, code)
        val again = CaseSecrets.derive(CaseNumber.parse("ppf abcde 12345")!!, CaseCode.parse("o123 4567 89ab cdef ghjk")!!)
        assertEquals(first.accessHash, again.accessHash)
        assertEquals(first.publicKey, again.publicKey)
        val otherNumber = CaseSecrets.derive(CaseNumber.fromDisplay("PPF-ABCDE-12346")!!, code)
        val otherCode = CaseSecrets.derive(number, CaseCode.parse("0123-4567-89AB-CDEF-GHJM")!!)
        listOf(otherNumber, otherCode).forEach {
            assertNotEquals(first.accessHash, it.accessHash)
            assertNotEquals(first.publicKey, it.publicKey)
        }
        // The token and the key are independent outputs of the same code.
        assertNotEquals(first.accessHash, Crypto.sha256(first.keys.secretKey()).toHexString())
    }

    @Test
    fun destroyWipesTheSecrets() {
        val secrets = CaseSecrets.derive(number, code)
        val publicKey = secrets.publicKey
        secrets.destroy()
        assertFailsWith<IllegalStateException> { secrets.keys.secretKey() }
        assertEquals(publicKey, secrets.publicKey)
        assertEquals(CaseAccess.hashHex(ByteArray(32)), secrets.accessHash)
        assertTrue(secrets.toString().contains(number.display))
    }

    @Test
    fun theServerReadsTheTokenOfTheHeaderAndHashesIt() {
        val secrets = CaseSecrets.derive(number, code)
        val token = CaseAccess.tokenFromAuthorization(secrets.authorization)!!
        assertEquals(32, token.size)
        assertEquals(secrets.accessHash, CaseAccess.hashHex(token))
        assertContentEquals(token, CaseAccess.tokenFromAuthorization("bearer   ${secrets.accessToken} "))
        listOf(
            null,
            "",
            secrets.accessToken,
            "Basic ${secrets.accessToken}",
            "Bearer ${secrets.accessToken}=",
            "Bearer ${secrets.accessToken.dropLast(1)}",
            "Bearer ${secrets.accessToken}A",
            "Bearer ${secrets.accessToken.replace('-', '+').replace('_', '/')}x".takeIf { secrets.accessToken.any { it == '-' || it == '_' } },
        ).forEach { assertNull(CaseAccess.tokenFromAuthorization(it), it) }
        // Stray bits in the last character are not another spelling of the same token.
        val last = secrets.accessToken.last()
        val sibling = Crockford32.ALPHABET.first { it != last }
        val stray = CaseAccess.tokenFromAuthorization("Bearer ${secrets.accessToken.dropLast(1)}$sibling")
        if (stray != null) assertEquals(secrets.accessToken.dropLast(1) + sibling, CaseAccess.encodeToken(stray))
    }

    @Test
    fun theExampleInboxIsTheKeyOfItsPublicSentence() {
        val keys = ContactInbox.exampleKeys()
        assertEquals(keys.publicKey, ContactInbox.EXAMPLE)
        assertEquals(PINNED_INBOX_X_START, ContactInbox.EXAMPLE.hex.take(16))
        assertTrue(ContactInbox.EXAMPLE_SEED.contains("[EXEMPLE]"))
        assertNull(ContactInbox.production())
        keys.destroy()
    }

    private companion object {
        // Starts of public values only (an access digest and x coordinates), never a secret.
        const val PINNED_DIGEST_START = "f836c56bb64655d0"
        const val PINNED_CASE_X_START = "88cf3986d2133482"
        const val PINNED_INBOX_X_START = "db9d08219af6f6e7"
    }
}
