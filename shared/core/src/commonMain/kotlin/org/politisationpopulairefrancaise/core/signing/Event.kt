package org.politisationpopulairefrancaise.core.signing

import fr.acinq.bitcoin.Crypto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.PublicKey

/**
 * The signed envelope of every content published on the platform, in the Nostr event format
 * (NIP-01). Choosing this format from day one means contents can later be relayed on Nostr
 * without being re-signed (cahier des charges, section 3.2), and that anyone can check that
 * a content really comes from its author ("contenu authentique PPF").
 *
 * The JSON form of this class is the platform's open export format for signed contents.
 */
@Serializable
data class SignedEvent(
    /** SHA-256 of the canonical serialization, lowercase hex: the stable identifier of this exact content. */
    val id: String,
    /** Author's public key, lowercase hex. */
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
    /** BIP-340 Schnorr signature of [id] by the author, lowercase hex. */
    val sig: String,
) {
    fun unsigned(): UnsignedEvent = UnsignedEvent(pubkey, createdAt, kind, tags, content)

    /**
     * True only if [id] really is the digest of this content and [sig] is the author's valid
     * signature of it. Never throws: a malformed event is simply not authentic.
     */
    fun verify(): Boolean {
        if (!isLowercaseHex(id, 64) || !isLowercaseHex(pubkey, 64) || !isLowercaseHex(sig, 128)) return false
        val digest = runCatching { unsigned().id() }.getOrNull() ?: return false
        if (digest.toHexString() != id) return false
        val author = runCatching { PublicKey.fromHex(pubkey) }.getOrNull() ?: return false
        return author.verify(sig.hexToByteArray(), digest)
    }

    /** Values of every tag named [name] (second element of the tag). */
    fun tagValues(name: String): List<String> = tags.filter { it.size >= 2 && it[0] == name }.map { it[1] }

    fun firstTagValue(name: String): String? = tagValues(name).firstOrNull()

    fun toJson(): String = EventJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): SignedEvent = EventJson.decodeFromString(serializer(), json)
    }
}

/** An event before signature: everything the author states, without id and signature. */
data class UnsignedEvent(
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
) {
    init {
        require(kind in 0..65535) { "kind must be between 0 and 65535" }
        require(isLowercaseHex(pubkey, 64)) { "pubkey must be 64 lowercase hexadecimal characters" }
        require(createdAt >= 0) { "created_at must be positive" }
    }

    /** The exact string whose SHA-256 is the event id (NIP-01 serialization rules). */
    fun canonicalJson(): String = EventSerialization.canonical(this)

    fun id(): ByteArray = Crypto.sha256(canonicalJson().encodeToByteArray())

    fun idHex(): String = id().toHexString()

    companion object {
        fun create(
            author: PublicKey,
            kind: Int,
            createdAt: Long,
            tags: List<List<String>> = emptyList(),
            content: String = "",
        ): UnsignedEvent = UnsignedEvent(author.hex, createdAt, kind, tags, content)
    }
}

/**
 * NIP-01 canonical serialization: `[0,pubkey,created_at,kind,tags,content]` as compact JSON,
 * where only `"`, `\`, line feed, carriage return, tab, backspace and form feed are escaped and
 * every other character is written verbatim in UTF-8. Any deviation changes the id, so this is
 * written by hand rather than delegated to a generic JSON library.
 */
object EventSerialization {
    fun canonical(event: UnsignedEvent): String = buildString {
        append("[0,\"").append(event.pubkey).append("\",")
        append(event.createdAt).append(',').append(event.kind).append(',')
        append('[')
        event.tags.forEachIndexed { tagIndex, tag ->
            if (tagIndex > 0) append(',')
            append('[')
            tag.forEachIndexed { index, value ->
                if (index > 0) append(',')
                appendJsonString(value)
            }
            append(']')
        }
        append("],")
        appendJsonString(event.content)
        append(']')
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> append(char)
            }
        }
        append('"')
    }
}

internal fun isLowercaseHex(value: String, length: Int): Boolean =
    value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

private val EventJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
