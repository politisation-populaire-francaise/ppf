package org.politisationpopulairefrancaise.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.crypto.HkdfSha256
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.SigningUse
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The service cannot start safely (an unreadable key file, a broken journal...); the message names no secret. */
class SignerStartupException(message: String) : RuntimeException(message)

/** One working key as kept on disk: public data, and the secret key encrypted (absent once retired). */
@Serializable
internal data class KeyFile(
    val version: Int = 1,
    val use: String,
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val nonce: String? = null,
    val ciphertext: String? = null,
    @SerialName("retired_at") val retiredAt: Long? = null,
)

/** A working key known to the service; [keys] is null once the key is retired (its secret destroyed). */
class WorkingKey internal constructor(
    val use: SigningUse,
    val pubkey: String,
    val createdAt: Long,
    internal val keys: IdentityKeys?,
    val retiredAt: Long?,
) {
    val isRetired: Boolean get() = keys == null

    override fun toString(): String = "WorkingKey($use, $pubkey)"
}

/**
 * The working keys at rest (D-129): one file per key in `<data>/keys`, the 32-byte secret encrypted with
 * AES-256-GCM under a key derived (HKDF-SHA-256) from the key-encryption key of `PPF_SIGNER_KEK_FILE`,
 * with a fresh 96-bit nonce, the use, the public key and the date as associated data (a file moved to
 * another use or key does not decrypt). Files are written to a temporary name, flushed to disk, then
 * renamed. Keys are generated here and their secret never leaves: no method returns it.
 */
class KeyVault(dataDir: Path, kek: ByteArray) {
    private val dir: Path = dataDir.resolve("keys")
    private val key: SecretKeySpec
    private val random = SecureRandom()

    init {
        val derived = HkdfSha256.derive(kek, SALT, INFO, 32)
        key = SecretKeySpec(derived, "AES")
        derived.fill(0)
        kek.fill(0)
        Files.createDirectories(dir)
    }

    /** Reads every key; a file that does not decrypt or does not match its public key stops the service. */
    fun loadAll(): List<WorkingKey> {
        Files.list(dir).use { stream -> stream.filter { it.fileName.toString().endsWith(".tmp") }.toList() }.forEach { Files.deleteIfExists(it) }
        val files = Files.list(dir).use { stream -> stream.filter { it.fileName.toString().endsWith(".json") }.sorted().toList() }
        return files.map(::load)
    }

    /** Generates a new key for [use], writes it encrypted, and returns it. */
    fun create(use: SigningUse, now: Long): WorkingKey {
        val keys = IdentityKeys.generate()
        val pubkey = keys.publicKey.hex
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val secret = keys.secretKey()
        val ciphertext = try {
            cipher(Cipher.ENCRYPT_MODE, nonce, associatedData(use.id, pubkey, now)).doFinal(secret)
        } finally {
            secret.fill(0)
        }
        write(KeyFile(use = use.id, pubkey = pubkey, createdAt = now, nonce = encode(nonce), ciphertext = encode(ciphertext)))
        return WorkingKey(use, pubkey, now, keys, null)
    }

    /** Destroys the secret of [key] (revoked or expired): the file keeps only its public data. */
    fun retire(key: WorkingKey, now: Long): WorkingKey {
        write(KeyFile(use = key.use.id, pubkey = key.pubkey, createdAt = key.createdAt, retiredAt = now))
        key.keys?.destroy()
        return WorkingKey(key.use, key.pubkey, key.createdAt, null, now)
    }

    private fun load(file: Path): WorkingKey {
        val name = file.fileName.toString()
        val record = try {
            JSON.decodeFromString(KeyFile.serializer(), String(Files.readAllBytes(file), Charsets.UTF_8))
        } catch (e: Exception) {
            throw SignerStartupException("Unreadable key file $name")
        }
        val use = SigningUse.fromId(record.use) ?: throw SignerStartupException("Key file $name names an unknown use")
        if (record.version != 1 || !isHex64(record.pubkey) || name != "${record.pubkey}.json") throw SignerStartupException("Malformed key file $name")
        if (record.retiredAt != null) return WorkingKey(use, record.pubkey, record.createdAt, null, record.retiredAt)
        val nonce = record.nonce?.let(::decode) ?: throw SignerStartupException("Malformed key file $name")
        val ciphertext = record.ciphertext?.let(::decode) ?: throw SignerStartupException("Malformed key file $name")
        val secret = try {
            cipher(Cipher.DECRYPT_MODE, nonce, associatedData(record.use, record.pubkey, record.createdAt)).doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw SignerStartupException("Key file $name does not decrypt with this key-encryption key")
        } catch (e: java.security.GeneralSecurityException) {
            throw SignerStartupException("Key file $name does not decrypt with this key-encryption key")
        }
        val keys = try {
            IdentityKeys.fromSecretKey(secret)
        } catch (e: IllegalArgumentException) {
            throw SignerStartupException("Key file $name holds an invalid key")
        } finally {
            secret.fill(0)
        }
        if (keys.publicKey.hex != record.pubkey) {
            keys.destroy()
            throw SignerStartupException("Key file $name does not match its public key")
        }
        return WorkingKey(use, record.pubkey, record.createdAt, keys, null)
    }

    private fun write(record: KeyFile) {
        val target = dir.resolve("${record.pubkey}.json")
        DurableFiles.writeAtomically(target, JSON.encodeToString(KeyFile.serializer(), record).toByteArray(Charsets.UTF_8))
    }

    private fun cipher(mode: Int, nonce: ByteArray, associatedData: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(associatedData)
        }

    private fun associatedData(use: String, pubkey: String, createdAt: Long): ByteArray =
        "ppf-signer-key-v1\n$use\n$pubkey\n$createdAt".toByteArray(Charsets.UTF_8)

    private companion object {
        val SALT: ByteArray = "ppf-signer".toByteArray(Charsets.UTF_8)
        val INFO: ByteArray = "ppf-signer-key-encryption-v1".toByteArray(Charsets.UTF_8)
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
        val JSON = Json { ignoreUnknownKeys = false; encodeDefaults = true; explicitNulls = false }

        fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        fun decode(text: String): ByteArray = try {
            Base64.getDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            throw SignerStartupException("Malformed key file")
        }
    }
}

/** Writes that survive a crash: a temporary file flushed to disk, then renamed over the target. */
internal object DurableFiles {
    fun writeAtomically(target: Path, bytes: ByteArray) {
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        java.nio.channels.FileChannel.open(
            temporary,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
            java.nio.file.StandardOpenOption.WRITE,
        ).use { channel ->
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        restrictToOwner(temporary)
        Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    /** On POSIX systems (the container), only the service's own user may read the file. */
    fun restrictToOwner(file: Path) {
        try {
            Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        } catch (e: UnsupportedOperationException) {
            // Windows (development): the permissions of the user's profile apply.
        }
    }
}
