package org.politisationpopulairefrancaise.server.push

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlin.math.abs

/**
 * HTTP authorization by a signed event (NIP-98): `Authorization: Nostr <base64 of a kind 27235 event>`
 * whose `u` tag is the absolute request URL, `method` the HTTP method and `payload` the hex SHA-256 of
 * the raw body, dated within 60 seconds. The author of the event is the authenticated key.
 */
object Nip98 {
    const val KIND: Int = 27235
    const val MAX_SKEW_SECONDS: Long = 60
    private const val MAX_HEADER_CHARS: Int = 8 * 1024
    private const val SCHEME: String = "Nostr "

    /** The authenticated public key, or null when the header does not authorize this exact request. */
    fun verify(header: String?, url: String, method: String, body: ByteArray, now: Instant): String? {
        if (header == null || header.length > MAX_HEADER_CHARS || !header.startsWith(SCHEME, ignoreCase = true)) return null
        val encoded = header.substring(SCHEME.length).trim()
        val json = runCatching { Base64.getDecoder().decode(encoded) }
            .recoverCatching { Base64.getUrlDecoder().decode(encoded) }
            .getOrNull()?.decodeToString() ?: return null
        val event = runCatching { SignedEvent.fromJson(json) }.getOrNull() ?: return null
        if (event.kind != KIND) return null
        if (abs(now.epochSecond - event.createdAt) > MAX_SKEW_SECONDS) return null
        if (event.tagValues("u") != listOf(url)) return null
        if (event.tagValues("method").map { it.uppercase() } != listOf(method.uppercase())) return null
        if (event.tagValues("payload") != listOf(sha256Hex(body))) return null
        if (!event.verify()) return null
        return event.pubkey
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
