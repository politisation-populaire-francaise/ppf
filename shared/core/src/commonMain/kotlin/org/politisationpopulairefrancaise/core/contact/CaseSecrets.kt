package org.politisationpopulairefrancaise.core.contact

import fr.acinq.bitcoin.Crypto
import fr.acinq.secp256k1.Secp256k1
import org.politisationpopulairefrancaise.core.crypto.HkdfSha256
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import kotlin.io.encoding.Base64

/**
 * What the number and the secret code of a case give (docs/integration/contact-reclamations.md, section 4),
 * by HKDF-SHA-256 with the salt `ppf-case-v1` over the normalised code in ASCII:
 *
 * - `info = "access " + number` (display form `PPF-XXXXX-XXXXX`): 32 bytes, the **access token**, sent as
 *   `Authorization: Bearer <base64url>`; the server keeps only its SHA-256 ([accessHash]);
 * - `info = "key " + number + " " + counter`, counter 0, then 1, and so on while the 32 bytes are not a valid
 *   secp256k1 secret key: the **secret key of the case** ([keys]); the server keeps its public key (x only,
 *   hexadecimal). It signs the seals of the person's messages and receives the team's replies; it is linked
 *   to no account.
 *
 * Deterministic: the same number and code give the same secrets on every device. Call [destroy] when done.
 */
class CaseSecrets private constructor(
    val number: CaseNumber,
    private val token: ByteArray,
    /** The key pair of the case. */
    val keys: IdentityKeys,
    /** The counter that gave a valid key (0 almost always). */
    val keyCounter: Int,
) {
    /** The access token in base64url without padding (43 characters), for the `Authorization` header. */
    val accessToken: String get() = CaseAccess.encodeToken(token)

    /** SHA-256 of the access token, lowercase hexadecimal: what the server stores (`access_hash`). */
    val accessHash: String get() = CaseAccess.hashHex(token)

    /** `Bearer <token>`. */
    val authorization: String get() = "Bearer $accessToken"

    /** The public key of the case (`case_pubkey`). */
    val publicKey: PublicKey get() = keys.publicKey

    /** Signs and encrypts as the case, for the seals of the person's messages and to open the team's replies. */
    fun signer(): LocalMessagingSigner = LocalMessagingSigner(keys)

    /** Wipes the token and the secret key from memory. */
    fun destroy() {
        token.fill(0)
        keys.destroy()
    }

    override fun toString(): String = "CaseSecrets(${number.display})"

    companion object {
        const val SALT: String = "ppf-case-v1"
        const val TOKEN_BYTES: Int = 32

        /** The derivation gives up after this many invalid keys (the odds of even one are about 2^-128). */
        const val MAX_KEY_COUNTER: Int = 255

        fun derive(number: CaseNumber, code: CaseCode): CaseSecrets {
            val material = code.normalized().encodeToByteArray()
            val salt = SALT.encodeToByteArray()
            val token = HkdfSha256.derive(material, salt, "access ${number.display}".encodeToByteArray(), TOKEN_BYTES)
            for (counter in 0..MAX_KEY_COUNTER) {
                val candidate = HkdfSha256.derive(material, salt, "key ${number.display} $counter".encodeToByteArray(), 32)
                try {
                    if (Secp256k1.secKeyVerify(candidate)) return CaseSecrets(number, token, IdentityKeys.fromSecretKey(candidate), counter)
                } finally {
                    candidate.fill(0)
                }
            }
            token.fill(0)
            error("No valid case key after ${MAX_KEY_COUNTER + 1} attempts")
        }
    }
}

/** The access token as the server sees it: read from the header, hashed, never kept. */
object CaseAccess {
    private val BASE64URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** Length of a token in base64url without padding. */
    const val TOKEN_LENGTH: Int = 43

    fun encodeToken(token: ByteArray): String = BASE64URL.encode(token)

    /** Whether [hex] is the x-only form of a secp256k1 point (64 lowercase hexadecimal characters on the curve), as a `case_pubkey` must be. */
    fun isValidCaseKey(hex: String): Boolean {
        if (hex.length != 64 || !hex.all { it in '0'..'9' || it in 'a'..'f' }) return false
        val compressed = byteArrayOf(0x02) + hex.hexToByteArray()
        return runCatching { Secp256k1.pubkeyParse(compressed) }.isSuccess
    }

    /** Whether [hex] is a SHA-256 in lowercase hexadecimal, as an `access_hash` must be. */
    fun isValidHash(hex: String): Boolean = hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }

    /** SHA-256 of [token]. */
    fun hash(token: ByteArray): ByteArray = Crypto.sha256(token)

    fun hashHex(token: ByteArray): String = hash(token).toHexString()

    /**
     * The 32 bytes of an `Authorization: Bearer <base64url>` header, or null when it is absent or malformed
     * (scheme other than Bearer, characters outside base64url, padding, length other than 32 bytes).
     */
    fun tokenFromAuthorization(header: String?): ByteArray? {
        val value = header?.trim() ?: return null
        val space = value.indexOf(' ')
        if (space <= 0 || !value.substring(0, space).equals("Bearer", ignoreCase = true)) return null
        val text = value.substring(space + 1).trim()
        if (text.length != TOKEN_LENGTH || !text.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) return null
        val bytes = runCatching { BASE64URL.decode(text) }.getOrNull() ?: return null
        // Only the canonical encoding: the last character carries no stray bits.
        return bytes.takeIf { it.size == CaseSecrets.TOKEN_BYTES && encodeToken(it) == text }
    }
}
