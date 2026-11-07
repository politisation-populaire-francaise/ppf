package org.politisationpopulairefrancaise.server.backoffice

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The secrets handed to a person (invitation links, session cookies): 256 random bits, written in
 * base64url without padding. The server stores only their SHA-256 ([hash]): a copy of the database
 * gives neither a valid link nor a valid session.
 */
object Tokens {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /** A fresh token of [bytes] random bytes (32 by default: 256 bits). */
    fun generate(bytes: Int = 32): String = encoder.encodeToString(randomBytes(bytes))

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    /** Lowercase hexadecimal SHA-256 of the token as typed (its UTF-8 bytes). */
    fun hash(token: String): String = sha256Hex(token.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    /** Whether [token] has the shape of a token of 32 bytes (43 base64url characters): anything else is refused unread. */
    fun looksValid(token: String): Boolean = token.length == 43 && token.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    fun base64Url(bytes: ByteArray): String = encoder.encodeToString(bytes)

    /** Decodes base64url (with or without padding); null when malformed. */
    fun fromBase64Url(value: String): ByteArray? = try {
        decoder.decode(value.trimEnd('='))
    } catch (e: IllegalArgumentException) {
        null
    }

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

/**
 * The anti-forgery token of the forms (CSRF): derived from the session token by HMAC-SHA-256, so that it
 * is never stored and changes with every session. Only someone who holds the session cookie can compute
 * it, and the cookie is unreadable by scripts (HttpOnly) and never sent by another site (SameSite=Strict).
 */
object Csrf {
    const val FIELD: String = "csrf"
    const val HEADER: String = "X-PPF-CSRF"

    private val LABEL = "ppf-equipe-csrf-v1".toByteArray(Charsets.UTF_8)

    fun tokenFor(sessionToken: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sessionToken.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Tokens.base64Url(mac.doFinal(LABEL))
    }

    /** Whether [submitted] is the token of [sessionToken], compared in constant time. */
    fun isValid(sessionToken: String, submitted: String?): Boolean =
        submitted != null && Tokens.constantTimeEquals(tokenFor(sessionToken), submitted)
}
