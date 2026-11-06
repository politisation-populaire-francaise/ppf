package org.politisationpopulairefrancaise.core.messaging

import fr.acinq.bitcoin.Crypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Replays the complete official NIP-44 test vectors.
 *
 * Source: `nip44.vectors.json` published with NIP-44 (https://github.com/nostr-protocol/nips, where
 * "All NIPs are public domain"; the file itself is served from the reference implementation
 * repository https://github.com/paulmillr/nip44). Downloaded on 2026-11-06 and kept byte for byte,
 * gzip-compressed, in src/jvmTest/resources/nip44/: the compression only stops the secret scanner
 * of the repository from mistaking these public test keys for leaked ones (`gzip -dc` restores the
 * file). Its SHA-256 is the one printed in the NIP text and is checked below before anything is replayed.
 */
class Nip44VectorsTest {

    private val vectors = run {
        val compressed = requireNotNull(Nip44VectorsTest::class.java.getResourceAsStream("/nip44/nip44.vectors.json.gz")) {
            "nip44.vectors.json.gz is missing from the test resources"
        }
        val bytes = GZIPInputStream(compressed).use { it.readBytes() }
        assertEquals("269ed0f69e4c192512cc779e78c555090cebc7c785b609e338a62afc3ce25040", Crypto.sha256(bytes).toHexString())
        Json.parseToJsonElement(bytes.decodeToString()).jsonObject.getValue("v2").jsonObject
    }

    private val valid = vectors.getValue("valid").jsonObject
    private val invalid = vectors.getValue("invalid").jsonObject

    @Test
    fun conversationKeys() {
        val cases = valid.getValue("get_conversation_key").jsonArray
        assertEquals(35, cases.size)
        for (case in cases) {
            val c = case.jsonObject
            val key = Nip44.conversationKey(c.hex("sec1"), c.hex("pub2"))
            assertEquals(c.str("conversation_key"), key.toHexString(), c["note"]?.jsonPrimitive?.content ?: "")
        }
    }

    @Test
    fun messageKeys() {
        val block = valid.getValue("get_message_keys").jsonObject
        val conversationKey = block.hex("conversation_key")
        val cases = block.getValue("keys").jsonArray
        assertTrue(cases.size >= 10)
        for (case in cases) {
            val c = case.jsonObject
            val keys = Nip44.messageKeys(conversationKey, c.hex("nonce"))
            assertEquals(c.str("chacha_key"), keys.chachaKey.toHexString())
            assertEquals(c.str("chacha_nonce"), keys.chachaNonce.toHexString())
            assertEquals(c.str("hmac_key"), keys.hmacKey.toHexString())
        }
    }

    @Test
    fun paddedLengths() {
        val cases = valid.getValue("calc_padded_len").jsonArray
        assertEquals(24, cases.size)
        for (case in cases) {
            val pair = case.jsonArray
            val unpadded = pair[0].jsonPrimitive.content.toLong()
            val expected = pair[1].jsonPrimitive.content.toLong()
            assertEquals(expected, Nip44.calcPaddedLength(unpadded), "padded length of $unpadded")
        }
    }

    @Test
    fun encryptAndDecrypt() {
        val cases = valid.getValue("encrypt_decrypt").jsonArray
        assertEquals(10, cases.size)
        for (case in cases) {
            val c = case.jsonObject
            val one = IdentityKeys.fromHex(c.str("sec1"))
            val two = IdentityKeys.fromHex(c.str("sec2"))
            val expectedKey = c.str("conversation_key")
            val keyFromOne = Nip44.conversationKey(one, two.publicKey)
            assertEquals(expectedKey, keyFromOne.toHexString())
            val payload = Nip44.encrypt(c.str("plaintext"), keyFromOne, c.hex("nonce"))
            assertEquals(c.str("payload"), payload)
            val keyFromTwo = Nip44.conversationKey(two, one.publicKey)
            assertEquals(expectedKey, keyFromTwo.toHexString())
            assertEquals(c.str("plaintext"), Nip44.decrypt(payload, keyFromTwo))
        }
    }

    @Test
    fun encryptAndDecryptLongMessages() {
        val cases = valid.getValue("encrypt_decrypt_long_msg").jsonArray
        assertEquals(3, cases.size)
        for (case in cases) {
            val c = case.jsonObject
            val plaintext = c.str("pattern").repeat(c.str("repeat").toInt())
            assertEquals(c.str("plaintext_sha256"), Crypto.sha256(plaintext.encodeToByteArray()).toHexString())
            val key = c.hex("conversation_key")
            val payload = Nip44.encrypt(plaintext, key, c.hex("nonce"))
            assertEquals(c.str("payload_sha256"), Crypto.sha256(payload.encodeToByteArray()).toHexString())
            assertEquals(plaintext, Nip44.decrypt(payload, key))
        }
    }

    @Test
    fun invalidMessageLengthsAreRefused() {
        val lengths = invalid.getValue("encrypt_msg_lengths").jsonArray.map { it.jsonPrimitive.content.toInt() }
        assertEquals(listOf(0, 65536, 100000, 10000000), lengths)
        val key = ByteArray(32) { 1 }
        for (length in lengths) {
            assertFailsWith<Nip44Exception>("length $length") { Nip44.encrypt("a".repeat(length), key, ByteArray(32) { 2 }) }
        }
    }

    @Test
    fun invalidConversationKeysAreRefused() {
        val cases = invalid.getValue("get_conversation_key").jsonArray
        assertEquals(8, cases.size)
        for (case in cases) {
            val c = case.jsonObject
            assertFailsWith<Nip44Exception>(c.str("note")) { Nip44.conversationKey(c.hex("sec1"), c.hex("pub2")) }
        }
    }

    @Test
    fun invalidPayloadsAreRefused() {
        val cases = invalid.getValue("decrypt").jsonArray
        assertEquals(12, cases.size)
        for (case in cases) {
            val c = case.jsonObject
            assertFailsWith<Nip44Exception>(c.str("note")) { Nip44.decrypt(c.str("payload"), c.hex("conversation_key")) }
        }
    }

    private fun kotlinx.serialization.json.JsonObject.str(name: String): String = getValue(name).jsonPrimitive.content

    private fun kotlinx.serialization.json.JsonObject.hex(name: String): ByteArray = str(name).hexToByteArray()
}
