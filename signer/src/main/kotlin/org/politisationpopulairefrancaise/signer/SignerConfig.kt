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
 * - `PPF_SIGNER_APPROVAL_ORIGIN`: the origin of the service's own approval pages, as the browser sees it
 *   (for example `https://signature.equipe.preprod.politisation-populaire-francaise.org`); the only origin
 *   accepted in the passkey assertions.
 * - `PPF_SIGNER_RP_ID`: the WebAuthn relying party id of the back-office passkeys, the host name of the
 *   back-office (for example `equipe.preprod.politisation-populaire-francaise.org`); the host of the
 *   approval origin must be a subdomain of it, so that the members use their usual passkeys there.
 * - `PPF_SIGNER_BACKOFFICE_ORIGIN`: the origin of the back-office; its host is the relying party id, and the
 *   service refuses to start if it is the approval origin (the approvals must be made on a page the main
 *   server cannot serve).
 * - `PPF_ENVIRONMENT`: `development` alone accepts `http://localhost` origins (relying party `localhost`).
 * - `PPF_SIGNER_HOST` (default `127.0.0.1`; `0.0.0.0` inside its container), `PPF_SIGNER_PORT` (internal
 *   API for the server, default 8090) and `PPF_SIGNER_APPROVAL_PORT` (approval pages, default 8091).
 * - `PPF_SIGNER_USES`: the enabled uses, separated by commas (default: all).
 * - `PPF_SIGNER_MAX_PER_HOUR`: overrides of the hourly ceilings, `use=number` separated by commas.
 */
class SignerConfig(
    val dataDir: Path,
    kek: ByteArray,
    val clientToken: String,
    val rootPublicKey: String?,
    /** `scheme://host[:port]`, lowercase, without path. */
    val approvalOrigin: String,
    val rpId: String,
    val backOfficeOrigin: String,
    val host: String = "127.0.0.1",
    val port: Int = DEFAULT_PORT,
    val approvalPort: Int = DEFAULT_APPROVAL_PORT,
    val enabledUses: Set<SigningUse> = SigningUse.entries.toSet(),
    val maxPerHour: Map<SigningUse, Int> = emptyMap(),
    val development: Boolean = false,
) {
    private val kekBytes: ByteArray = kek.copyOf()

    init {
        require(kekBytes.size == 32) { "The key-encryption key is 32 bytes long" }
        require(rootPublicKey == null || isHex64(rootPublicKey)) { "The root public key is 64 lowercase hexadecimal characters" }
        require(port in 1..65535 && approvalPort in 1..65535 && port != approvalPort) { "The two ports differ" }
        checkOrigins(approvalOrigin, rpId, backOfficeOrigin, development)?.let { throw SignerConfigException(it) }
    }

    /** A copy of the key-encryption key. */
    fun kek(): ByteArray = kekBytes.copyOf()

    /** The approval page of the event [eventId] for [use]. */
    fun approvalUrl(use: SigningUse, eventId: String): String = "$approvalOrigin/approbation/${use.id}/$eventId"

    override fun toString(): String =
        "SignerConfig(dataDir=$dataDir, root=${rootPublicKey ?: "none"}, approval=$approvalOrigin, rpId=$rpId, ports=$port/$approvalPort)"

    companion object {
        const val DEFAULT_PORT: Int = 8090
        const val DEFAULT_APPROVAL_PORT: Int = 8091

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
            val development = env["PPF_ENVIRONMENT"]?.trim() == "development"
            val approvalOrigin = normalizedOrigin(env["PPF_SIGNER_APPROVAL_ORIGIN"]?.trim().orEmpty(), development)
                ?: throw SignerConfigException("PPF_SIGNER_APPROVAL_ORIGIN must be https://<host>[:port] (http://localhost only in development), without path")
            val backOfficeOrigin = normalizedOrigin(env["PPF_SIGNER_BACKOFFICE_ORIGIN"]?.trim().orEmpty(), development)
                ?: throw SignerConfigException("PPF_SIGNER_BACKOFFICE_ORIGIN must be https://<host>[:port] (http://localhost only in development), without path")
            val rpId = env["PPF_SIGNER_RP_ID"]?.trim()?.lowercase().orEmpty()
            checkOrigins(approvalOrigin, rpId, backOfficeOrigin, development)?.let { throw SignerConfigException(it) }
            val port = parsePort(env, "PPF_SIGNER_PORT", DEFAULT_PORT)
            val approvalPort = parsePort(env, "PPF_SIGNER_APPROVAL_PORT", DEFAULT_APPROVAL_PORT)
            if (port == approvalPort) throw SignerConfigException("PPF_SIGNER_APPROVAL_PORT must differ from PPF_SIGNER_PORT")
            val host = env["PPF_SIGNER_HOST"]?.trim()?.ifEmpty { null } ?: "127.0.0.1"
            val uses = env["PPF_SIGNER_USES"]?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.map {
                SigningUse.fromId(it) ?: throw SignerConfigException("PPF_SIGNER_USES names an unknown use")
            }?.toSet() ?: SigningUse.entries.toSet()
            val ceilings = parseCeilings(env["PPF_SIGNER_MAX_PER_HOUR"])
            return SignerConfig(dataDir, kek, token, root, approvalOrigin, rpId, backOfficeOrigin, host, port, approvalPort, uses, ceilings, development)
        }

        private fun parsePort(env: Map<String, String>, name: String, default: Int): Int =
            env[name]?.let { it.trim().toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: throw SignerConfigException("$name must be a port number") } ?: default

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

        /**
         * `scheme://host[:port]` in lowercase when [origin] is `https://<host>[:port]` with nothing after (a
         * final `/` is accepted), or `http://localhost[:port]` in [development] only; null otherwise.
         */
        fun normalizedOrigin(origin: String, development: Boolean): String? {
            val uri = try {
                URI(origin)
            } catch (e: java.net.URISyntaxException) {
                return null
            }
            val host = uri.host?.lowercase() ?: return null
            if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            if (!(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")) return null
            val scheme = when (uri.scheme?.lowercase()) {
                "https" -> "https"
                "http" -> if (development && host == "localhost") "http" else return null
                else -> return null
            }
            return "$scheme://$host" + if (uri.port != -1) ":${uri.port}" else ""
        }

        /**
         * Why the three settings of the approvals do not hold together, null when they do: the relying
         * party is a host name; the back-office is served on it; the approval pages on a subdomain of it
         * (`localhost` itself in development), never on the back-office origin.
         */
        fun checkOrigins(approvalOrigin: String, rpId: String, backOfficeOrigin: String, development: Boolean): String? {
            if (!HOST_NAME.matches(rpId)) return "PPF_SIGNER_RP_ID must be the host name of the back-office"
            val approvalHost = runCatching { URI(approvalOrigin).host?.lowercase() }.getOrNull()
                ?: return "PPF_SIGNER_APPROVAL_ORIGIN is not a valid origin"
            val backOfficeHost = runCatching { URI(backOfficeOrigin).host?.lowercase() }.getOrNull()
                ?: return "PPF_SIGNER_BACKOFFICE_ORIGIN is not a valid origin"
            if (backOfficeHost != rpId) return "PPF_SIGNER_RP_ID must be the host of PPF_SIGNER_BACKOFFICE_ORIGIN"
            if (approvalOrigin == backOfficeOrigin) return "PPF_SIGNER_APPROVAL_ORIGIN must differ from PPF_SIGNER_BACKOFFICE_ORIGIN"
            val subdomain = approvalHost.endsWith(".$rpId")
            val localDevelopment = development && rpId == "localhost" && approvalHost == "localhost"
            if (!subdomain && !localDevelopment) return "The host of PPF_SIGNER_APPROVAL_ORIGIN must be a subdomain of PPF_SIGNER_RP_ID"
            return null
        }

        private val HOST_NAME = Regex("^(?=.{1,253}$)[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$")

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
