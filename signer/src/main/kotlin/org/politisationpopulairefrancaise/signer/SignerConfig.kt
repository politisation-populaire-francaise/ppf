package org.politisationpopulairefrancaise.signer

import org.politisationpopulairefrancaise.core.platform.SigningUse
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** The configuration is invalid; the message names the variable, never its value. */
class SignerConfigException(message: String) : RuntimeException(message)

/**
 * Configuration of the signing service, from environment variables only. The two secrets (the
 * key-encryption key and the token of the server) are read from files, the form of the Docker secrets:
 * a variable holding a secret itself is refused.
 *
 * - `PPF_SIGNER_DATA_DIR`: directory of the encrypted working keys, certificates, team list and journal.
 * - `PPF_SIGNER_KEK_FILE`: file holding the key-encryption key, 64 hexadecimal characters (32 random bytes).
 * - `PPF_SIGNER_CLIENT_TOKEN_FILE`: file holding the token the server presents, 32 to 512 visible ASCII characters.
 * - `PPF_SIGNER_ROOT_PUBKEY`: the root public key (hex); empty until the root exists: the service then
 *   creates its keys but signs nothing.
 * - `PPF_SIGNER_APPROVAL_ORIGIN`: the origin of the back-office whose passkey approvals are accepted
 *   (`https://…`, or `http://localhost` in development); the WebAuthn relying party is its host name.
 * - `PPF_SIGNER_HOST` (default `127.0.0.1`; `0.0.0.0` inside its container) and `PPF_SIGNER_PORT` (default 8090).
 * - `PPF_SIGNER_USES`: the enabled uses, separated by commas (default: all).
 * - `PPF_SIGNER_MAX_PER_HOUR`: overrides of the hourly ceilings, `use=number` separated by commas.
 */
class SignerConfig(
    val dataDir: Path,
    kek: ByteArray,
    val clientToken: String,
    val rootPublicKey: String?,
    val approvalOrigin: String,
    val host: String = "127.0.0.1",
    val port: Int = DEFAULT_PORT,
    val enabledUses: Set<SigningUse> = SigningUse.entries.toSet(),
    val maxPerHour: Map<SigningUse, Int> = emptyMap(),
) {
    private val kekBytes: ByteArray = kek.copyOf()

    init {
        require(kekBytes.size == 32) { "The key-encryption key is 32 bytes long" }
        require(rootPublicKey == null || isHex64(rootPublicKey)) { "The root public key is 64 lowercase hexadecimal characters" }
    }

    /** A copy of the key-encryption key. */
    fun kek(): ByteArray = kekBytes.copyOf()

    /** The WebAuthn relying party id: the host name of [approvalOrigin]. */
    val rpId: String get() = URI(approvalOrigin).host

    override fun toString(): String = "SignerConfig(dataDir=$dataDir, root=${rootPublicKey ?: "none"}, origin=$approvalOrigin, port=$port)"

    companion object {
        const val DEFAULT_PORT: Int = 8090

        fun fromEnvironment(env: Map<String, String> = System.getenv()): SignerConfig {
            for (forbidden in listOf("PPF_SIGNER_KEK", "PPF_SIGNER_CLIENT_TOKEN")) {
                if (env[forbidden] != null) throw SignerConfigException("$forbidden is refused: give the path of a file in ${forbidden}_FILE")
            }
            val dataDir = env["PPF_SIGNER_DATA_DIR"]?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
                ?: throw SignerConfigException("PPF_SIGNER_DATA_DIR is required")
            val kek = parseKek(readSecretFile(env, "PPF_SIGNER_KEK_FILE"))
            val token = readSecretFile(env, "PPF_SIGNER_CLIENT_TOKEN_FILE").trim()
            if (!isValidClientToken(token)) throw SignerConfigException("PPF_SIGNER_CLIENT_TOKEN_FILE must hold 32 to 512 visible ASCII characters")
            val root = env["PPF_SIGNER_ROOT_PUBKEY"]?.trim().orEmpty().ifEmpty { null }
            if (root != null && !isHex64(root)) throw SignerConfigException("PPF_SIGNER_ROOT_PUBKEY must be 64 lowercase hexadecimal characters")
            val origin = env["PPF_SIGNER_APPROVAL_ORIGIN"]?.trim().orEmpty()
            if (!isValidOrigin(origin)) throw SignerConfigException("PPF_SIGNER_APPROVAL_ORIGIN must be https://<host>[:port] (http only for localhost), without path")
            val port = env["PPF_SIGNER_PORT"]?.let { it.toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: throw SignerConfigException("PPF_SIGNER_PORT must be a port number") }
                ?: DEFAULT_PORT
            val host = env["PPF_SIGNER_HOST"]?.trim()?.ifEmpty { null } ?: "127.0.0.1"
            val uses = env["PPF_SIGNER_USES"]?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.map {
                SigningUse.fromId(it) ?: throw SignerConfigException("PPF_SIGNER_USES names an unknown use")
            }?.toSet() ?: SigningUse.entries.toSet()
            val ceilings = parseCeilings(env["PPF_SIGNER_MAX_PER_HOUR"])
            return SignerConfig(dataDir, kek, token, root, origin.trimEnd('/'), host, port, uses, ceilings)
        }

        private fun readSecretFile(env: Map<String, String>, name: String): String {
            val path = env[name]?.takeIf { it.isNotBlank() } ?: throw SignerConfigException("$name is required")
            return try {
                val file = Paths.get(path)
                if (Files.size(file) > 4096) throw SignerConfigException("$name names a file that is too large")
                String(Files.readAllBytes(file), Charsets.UTF_8)
            } catch (e: java.io.IOException) {
                throw SignerConfigException("$name names a file that cannot be read")
            }
        }

        /** 64 hexadecimal characters (for example `openssl rand -hex 32`), spaces and line ends around ignored. */
        fun parseKek(text: String): ByteArray {
            val hex = text.trim().lowercase()
            if (!isHex64(hex)) throw SignerConfigException("PPF_SIGNER_KEK_FILE must hold 64 hexadecimal characters")
            return hex.hexToByteArray()
        }

        fun isValidClientToken(token: String): Boolean = token.length in 32..512 && token.all { it in '!'..'~' }

        fun isValidOrigin(origin: String): Boolean {
            val uri = try {
                URI(origin)
            } catch (e: java.net.URISyntaxException) {
                return false
            }
            val host = uri.host ?: return false
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) return false
            if (!(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) return false
            return when (uri.scheme) {
                "https" -> true
                "http" -> host == "localhost"
                else -> false
            }
        }

        private fun parseCeilings(text: String?): Map<SigningUse, Int> {
            if (text.isNullOrBlank()) return emptyMap()
            return text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.associate { item ->
                val parts = item.split('=')
                val use = parts.getOrNull(0)?.trim()?.let(SigningUse::fromId)
                val value = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (parts.size != 2 || use == null || value == null || value !in 0..100_000) {
                    throw SignerConfigException("PPF_SIGNER_MAX_PER_HOUR must read use=number, separated by commas")
                }
                use to value
            }
        }
    }
}

internal fun isHex64(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
