package org.politisationpopulairefrancaise.core.identity

import fr.acinq.bitcoin.Bech32

/**
 * NIP-19: human-readable bech32 encoding of keys and identifiers, shared by the whole Nostr
 * ecosystem. "npub1…" is a public key, "nsec1…" a secret key, "note1…" an event id.
 * Using it means a PPF identity can be exported to, or imported from, any Nostr application.
 */
object Nip19 {
    const val PUBLIC_KEY: String = "npub"
    const val SECRET_KEY: String = "nsec"
    const val NOTE: String = "note"

    /** Encodes 32 bytes under the given prefix. */
    fun encode(prefix: String, bytes: ByteArray): String {
        require(bytes.size == 32) { "NIP-19 keys and ids are 32 bytes long, got ${bytes.size}" }
        return Bech32.encodeBytes(prefix, bytes, Bech32.Encoding.Bech32)
    }

    /**
     * Decodes any bech32 entity into its prefix and raw bytes.
     * @throws IllegalArgumentException if the string is malformed or its checksum is wrong.
     */
    fun decode(encoded: String): Pair<String, ByteArray> {
        val (prefix, bytes, encoding) = Bech32.decodeBytes(encoded.trim(), noChecksum = false)
        require(encoding == Bech32.Encoding.Bech32) { "Not a NIP-19 entity (bech32 checksum expected)" }
        return prefix to bytes
    }

    /** Decodes an entity that must carry [expectedPrefix] and hold 32 bytes. */
    fun decode(expectedPrefix: String, encoded: String): ByteArray {
        val (prefix, bytes) = decode(encoded)
        require(prefix == expectedPrefix) { "Expected a \"$expectedPrefix\" entity, got \"$prefix\"" }
        require(bytes.size == 32) { "Malformed \"$expectedPrefix\" entity (${bytes.size} bytes)" }
        return bytes
    }
}
