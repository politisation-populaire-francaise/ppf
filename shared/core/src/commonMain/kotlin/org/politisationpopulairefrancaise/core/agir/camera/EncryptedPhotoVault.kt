package org.politisationpopulairefrancaise.core.agir.camera

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.media.MediaDigest

/**
 * The cryptography of the platform under the vault (D-089): AES-256-GCM, and a master key that never
 * leaves the device. On Android: `javax.crypto` for the key of each photo, the Android Keystore for
 * the master key (non-exportable, hardware-backed when the phone has it).
 */
interface VaultCrypto {
    /** 32 fresh random bytes: the key of one file. The caller wipes it after use. */
    fun newKey(): ByteArray

    /** AES-256-GCM of [plaintext] under [key]: nonce (12 bytes) ‖ ciphertext ‖ tag (16 bytes); [aad] is authenticated, not encrypted. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray

    /** Opens what [seal] wrote; throws when a single byte was changed or [aad] differs. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray

    /** [key] encrypted by the master key (AES-256-GCM inside the Keystore), which is created on first use. */
    fun wrap(key: ByteArray, aad: ByteArray): ByteArray

    /** The key [wrap] encrypted; throws when the master key is gone or the wrapped key was changed. */
    fun unwrap(wrapped: ByteArray, aad: ByteArray): ByteArray

    /** True while the master key exists. */
    fun hasMasterKey(): Boolean

    /** Destroys the master key: every wrapped key, every copy of every file, becomes unreadable at once. */
    fun destroyMasterKey()
}

/** The private directory of the vault (on Android, inside the app's no-backup directory). Names are short, lowercase, without path. */
interface VaultFiles {
    fun read(name: String): ByteArray?

    /** Writes [bytes] under [name] in one move, never half written. */
    fun write(name: String, bytes: ByteArray)

    /** Deletes [name]; throws if it is still there. Nothing happens when it does not exist. */
    fun delete(name: String)

    fun names(): List<String>
}

class VaultException(message: String) : Exception(message)

/**
 * The vault of the protected camera (AG-58, D-089) on [files] and [crypto]:
 * - each photo is sealed (AES-256-GCM) by a key of its own, random, used once; that key is wrapped
 *   by the master key of [crypto] and written in the photo's own file, so that deleting the file
 *   deletes the only copy of its key;
 * - the listing ([ProtectedPhoto]: identifier, date, size, digests, never a pixel) is a small JSON
 *   document sealed the same way, with a fresh key at each write ([INDEX]);
 * - the associated data bind each wrapped key and each sealed photo to its identifier: a file
 *   renamed or a key moved to another file is refused;
 * - nothing is ever written in clear: the bytes reach [files] already sealed.
 *
 * File layout (`<id>.photo` and `index.bin`): "PPFC" ‖ version (1) ‖ length of the wrapped key
 * (2 bytes, big-endian) ‖ wrapped key (nonce ‖ ciphertext ‖ tag) ‖ sealed content (nonce ‖ ciphertext ‖ tag).
 *
 * The quick erase ([eraseAll]) destroys the master key first: whatever the flash memory keeps of
 * the deleted files (wear-levelling) cannot be read any more; then it deletes the files.
 */
class EncryptedPhotoVault(
    private val files: VaultFiles,
    private val crypto: VaultCrypto,
    private val newId: () -> String = ProtectedIds::next,
) : ProtectedVault {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun store(bytes: ByteArray, meta: ProtectedPhotoMeta): ProtectedPhoto = mutex.withLock {
        require(bytes.isNotEmpty() && bytes.size <= MAX_PHOTO_BYTES) { "Photos of 1 byte to $MAX_PHOTO_BYTES bytes" }
        dropLeftovers()
        val photos = readIndex()
        val id = newId()
        require(ProtectedIds.isValid(id)) { "an identifier of 32 hexadecimal characters" }
        val photo = ProtectedPhoto(id, meta.takenAt, bytes.size.toLong(), MediaDigest.sha256Hex(bytes), meta.cameraSha256, meta.mimeType)
        files.write(photoFile(id), seal(bytes, keyAad(id), photoAad(id)))
        try {
            writeIndex(listOf(photo) + photos)
        } catch (e: Throwable) {
            // Not listed, not kept: the photo goes with its key.
            runCatching { files.delete(photoFile(id)) }
            throw e
        }
        photo
    }

    override suspend fun list(): List<ProtectedPhoto> = mutex.withLock {
        dropLeftovers()
        val present = files.names().toSet()
        readIndex().filter { photoFile(it.id) in present }.sortedByDescending { it.takenAt }
    }

    override suspend fun open(id: String): ByteArray? = mutex.withLock {
        if (!ProtectedIds.isValid(id) || !crypto.hasMasterKey()) return@withLock null
        val photo = readIndex().firstOrNull { it.id == id } ?: return@withLock null
        val blob = files.read(photoFile(id)) ?: return@withLock null
        val bytes = open(blob, keyAad(id), photoAad(id))
        if (MediaDigest.sha256Hex(bytes) != photo.sha256) {
            bytes.fill(0)
            throw VaultException("the photo does not match its listing")
        }
        bytes
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            require(ProtectedIds.isValid(id)) { "an identifier of 32 hexadecimal characters" }
            files.delete(photoFile(id))
            if (photoFile(id) in files.names()) throw VaultException("the photo could not be deleted")
            if (crypto.hasMasterKey()) {
                val photos = readIndex()
                if (photos.any { it.id == id }) writeIndex(photos.filter { it.id != id })
            }
        }
    }

    override suspend fun eraseAll() {
        mutex.withLock {
            // The key first: from this moment, no copy of any file can be read, even if a file resists.
            val keyFailure = runCatching { crypto.destroyMasterKey() }.exceptionOrNull()
            for (name in files.names()) runCatching { files.delete(name) }
            val left = files.names()
            if (left.isNotEmpty()) throw VaultException("${left.size} file(s) of the protected camera could not be erased")
            if (keyFailure != null) throw keyFailure
            if (crypto.hasMasterKey()) throw VaultException("the master key of the protected camera is still there")
        }
    }

    /**
     * Files left by an erase interrupted after the master key was destroyed (or a key lost by the
     * system): unreadable by construction, they are deleted rather than kept out of sight.
     */
    private fun dropLeftovers() {
        if (crypto.hasMasterKey()) return
        for (name in files.names()) files.delete(name)
    }

    private fun readIndex(): List<ProtectedPhoto> {
        val blob = files.read(INDEX) ?: return emptyList()
        val plain = open(blob, keyAad(INDEX_ID), INDEX_AAD)
        try {
            val document = json.decodeFromString(IndexDocument.serializer(), plain.decodeToString())
            if (document.version != INDEX_VERSION) throw VaultException("unknown version of the listing")
            return document.photos.filter { ProtectedIds.isValid(it.id) }
        } finally {
            plain.fill(0)
        }
    }

    private fun writeIndex(photos: List<ProtectedPhoto>) {
        val plain = json.encodeToString(IndexDocument.serializer(), IndexDocument(INDEX_VERSION, photos)).encodeToByteArray()
        try {
            files.write(INDEX, seal(plain, keyAad(INDEX_ID), INDEX_AAD))
        } finally {
            plain.fill(0)
        }
    }

    private fun seal(plaintext: ByteArray, keyAad: ByteArray, contentAad: ByteArray): ByteArray {
        val key = crypto.newKey()
        try {
            val wrapped = crypto.wrap(key, keyAad)
            if (wrapped.size > 0xFFFF) throw VaultException("wrapped key too long")
            val sealed = crypto.seal(key, plaintext, contentAad)
            return MAGIC + byteArrayOf(VERSION, (wrapped.size ushr 8).toByte(), wrapped.size.toByte()) + wrapped + sealed
        } finally {
            key.fill(0)
        }
    }

    private fun open(blob: ByteArray, keyAad: ByteArray, contentAad: ByteArray): ByteArray {
        val header = MAGIC.size + 3
        if (blob.size <= header || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw VaultException("not a file of the protected camera")
        if (blob[MAGIC.size] != VERSION) throw VaultException("unknown version")
        val length = ((blob[MAGIC.size + 1].toInt() and 0xFF) shl 8) or (blob[MAGIC.size + 2].toInt() and 0xFF)
        if (length == 0 || blob.size <= header + length) throw VaultException("truncated file")
        val key = crypto.unwrap(blob.copyOfRange(header, header + length), keyAad)
        try {
            return crypto.open(key, blob.copyOfRange(header + length, blob.size), contentAad)
        } finally {
            key.fill(0)
        }
    }

    @Serializable
    private data class IndexDocument(val version: Int, val photos: List<ProtectedPhoto>)

    companion object {
        /** The listing of the photos. */
        const val INDEX: String = "index.bin"

        /** Largest photo kept, in bytes (the chain of media accepts originals up to 25 MiB). */
        const val MAX_PHOTO_BYTES: Int = 40 * 1024 * 1024

        private const val INDEX_ID = "index"
        private const val INDEX_VERSION = 1
        private const val VERSION: Byte = 1
        private val MAGIC = "PPFC".encodeToByteArray()
        private val INDEX_AAD = "ppf-camera-index-v1".encodeToByteArray()

        /** The file of the photo [id]. */
        fun photoFile(id: String): String = "$id.photo"

        private fun keyAad(id: String): ByteArray = "ppf-camera-key-v1:$id".encodeToByteArray()

        private fun photoAad(id: String): ByteArray = "ppf-camera-photo-v1:$id".encodeToByteArray()
    }
}
