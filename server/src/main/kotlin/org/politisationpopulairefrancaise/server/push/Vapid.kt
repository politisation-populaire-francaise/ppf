package org.politisationpopulairefrancaise.server.push

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * The VAPID key pair of the server (RFC 8292), one per environment, created on the server and never
 * in the repository. Only the private key is stored (PKCS#8 PEM); the public key is derived from it.
 */
class VapidKeys(val privateKey: ECPrivateKey, val publicKey: ECPublicKey = P256.publicKeyOf(privateKey)) {
    /** The public key as an uncompressed point, base64url: what the application passes to UnifiedPush. */
    val publicKeyBase64Url: String = Base64Url.encode(P256.encodePoint(publicKey))

    /**
     * The `Authorization` header value for a push to [endpoint]: `vapid t=<JWT ES256>, k=<public key>`.
     * The audience is the origin of the endpoint as the subscription gave it (its public name, even
     * when the request goes through the internal address); [subject] is the https address of the site,
     * never an e-mail address.
     */
    fun authorization(endpoint: URI, subject: String, now: Instant): String =
        "vapid t=${jwt(audienceOf(endpoint), subject, now.plusSeconds(TOKEN_LIFETIME_SECONDS).epochSecond)}, k=$publicKeyBase64Url"

    internal fun jwt(audience: String, subject: String, expiresAt: Long): String {
        val header = Base64Url.encode("""{"typ":"JWT","alg":"ES256"}""".encodeToByteArray())
        val claims = Base64Url.encode(
            """{"aud":${jsonString(audience)},"exp":$expiresAt,"sub":${jsonString(subject)}}""".encodeToByteArray(),
        )
        val signingInput = "$header.$claims"
        // JWS ES256 wants the raw r || s form (64 bytes), which the JDK produces directly in the
        // IEEE P1363 format instead of DER.
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initSign(privateKey)
            update(signingInput.encodeToByteArray())
            sign()
        }
        return "$signingInput.${Base64Url.encode(signature)}"
    }

    companion object {
        /** RFC 8292: at most 24 hours; 12 hours leaves room for clock differences. */
        const val TOKEN_LIFETIME_SECONDS: Long = 12 * 60 * 60

        fun audienceOf(endpoint: URI): String {
            val port = if (endpoint.port == -1) "" else ":${endpoint.port}"
            return "${endpoint.scheme.lowercase()}://${endpoint.host.lowercase()}$port"
        }

        /** Reads an EC P-256 private key in PKCS#8 PEM (`-----BEGIN PRIVATE KEY-----`). */
        fun fromPem(pem: String): VapidKeys {
            val body = pem.lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("-----") }
                .joinToString("")
            require(pem.contains("BEGIN PRIVATE KEY")) { "The VAPID key must be a PKCS#8 PEM private key" }
            val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)))
            require(key is ECPrivateKey && key.params.curve == P256.parameters.curve) { "The VAPID key must be an EC P-256 key" }
            return VapidKeys(key)
        }

        fun fromFile(path: Path): VapidKeys = fromPem(Files.readString(path))

        private fun jsonString(value: String): String = buildString {
            append('"')
            for (char in value) {
                when {
                    char == '"' -> append("\\\"")
                    char == '\\' -> append("\\\\")
                    char < ' ' -> append("\\u%04x".format(char.code))
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}
