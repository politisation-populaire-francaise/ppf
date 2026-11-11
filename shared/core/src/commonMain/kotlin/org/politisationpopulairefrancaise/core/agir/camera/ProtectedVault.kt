package org.politisationpopulairefrancaise.core.agir.camera

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.media.MediaDigest

/**
 * A photo kept by the protected camera (AG-58, D-089), as the encrypted index of the vault lists it:
 * never its pixels, only what the gallery of PPF needs to show it and check it.
 */
@Serializable
data class ProtectedPhoto(
    /** 16 random bytes in lowercase hexadecimal: says nothing about the photo. */
    val id: String,
    /** When it was taken, in seconds. */
    val takenAt: Long,
    /** Size of the kept copy, in bytes. */
    val sizeBytes: Long,
    /** SHA-256 of the kept copy (the camera's pixels, untouched, without metadata). */
    val sha256: String,
    /** SHA-256 of the bytes the camera handed over, metadata included, before they were cleaned (they are never written). */
    val cameraSha256: String,
    val mimeType: String = JPEG,
) {
    companion object {
        const val JPEG: String = "image/jpeg"
    }
}

/** What the vault is told about a photo it keeps; the rest ([ProtectedPhoto]) it works out itself. */
data class ProtectedPhotoMeta(
    val takenAt: Long,
    val cameraSha256: String,
    val mimeType: String = ProtectedPhoto.JPEG,
)

/**
 * Where the protected camera keeps its photos (AG-58, D-089): encrypted on the device, outside the
 * phone's gallery, never in a backup. Only cleaned photos come here ([ProtectedCapture]); nothing
 * here ever talks to a server. [open] gives the decrypted bytes in memory only.
 *
 * On Android, [EncryptedPhotoVault] on the app's no-backup directory and a master key of the Android
 * Keystore (`AndroidProtectedVault`); elsewhere there is none in step 1 (no camera on the computer,
 * iOS not built). Every function may throw when the storage or the cryptography fails.
 */
interface ProtectedVault {
    /** Encrypts and keeps [bytes]; the photo as the gallery will list it. */
    suspend fun store(bytes: ByteArray, meta: ProtectedPhotoMeta): ProtectedPhoto

    /** The photos kept, newest first. */
    suspend fun list(): List<ProtectedPhoto>

    /** The decrypted bytes of the photo [id], in memory; null when there is no such photo. */
    suspend fun open(id: String): ByteArray?

    /** Deletes the photo [id] (its file, with its key); throws if it is still there. */
    suspend fun delete(id: String)

    /**
     * The part `agir-camera` of the quick erase (S-44): destroys the key that protects every photo
     * (crypto-erasure), then the files; throws if anything is left. May be called again.
     */
    suspend fun eraseAll()
}

/** Identifiers of the vault: 16 random bytes in lowercase hexadecimal. */
object ProtectedIds {
    fun next(): String = secureRandomBytes(16).toHexString()

    fun isValid(id: String): Boolean = id.length == 32 && id.all { it in '0'..'9' || it in 'a'..'f' }
}

/**
 * A vault in memory only, for tests and previews: nothing survives the process. It keeps exactly
 * what [EncryptedPhotoVault] would keep (the cleaned bytes and their listing), without encryption.
 */
class InMemoryProtectedVault : ProtectedVault {
    private val mutex = Mutex()
    private val photos = LinkedHashMap<String, Pair<ProtectedPhoto, ByteArray>>()

    /** How many times [eraseAll] ran (tests). */
    var erasures: Int = 0
        private set

    override suspend fun store(bytes: ByteArray, meta: ProtectedPhotoMeta): ProtectedPhoto = mutex.withLock {
        require(bytes.isNotEmpty()) { "an empty photo" }
        val photo = ProtectedPhoto(ProtectedIds.next(), meta.takenAt, bytes.size.toLong(), MediaDigest.sha256Hex(bytes), meta.cameraSha256, meta.mimeType)
        photos[photo.id] = photo to bytes.copyOf()
        photo
    }

    override suspend fun list(): List<ProtectedPhoto> = mutex.withLock { photos.values.map { it.first }.sortedByDescending { it.takenAt } }

    override suspend fun open(id: String): ByteArray? = mutex.withLock { photos[id]?.second?.copyOf() }

    override suspend fun delete(id: String) {
        mutex.withLock { photos.remove(id) }
    }

    override suspend fun eraseAll() {
        mutex.withLock {
            photos.values.forEach { it.second.fill(0) }
            photos.clear()
            erasures++
        }
    }
}
