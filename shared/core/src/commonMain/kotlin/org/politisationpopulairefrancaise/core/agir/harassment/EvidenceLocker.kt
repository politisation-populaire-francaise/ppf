package org.politisationpopulairefrancaise.core.agir.harassment

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedPhoto
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedPhotoMeta
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** How keeping a piece of evidence ended. */
sealed interface KeepResult {
    data class Kept(val evidence: Evidence) : KeepResult
    data class Refused(val problems: Set<EvidenceProblem>) : KeepResult
}

/** The evidence that could be read, newest first, and how many records could not ([unreadable], never shown). */
data class EvidenceShelf(val items: List<Evidence>, val unreadable: Int = 0)

/**
 * The evidence of the harassment kit (AG-59) on a [ProtectedVault]: the vault of the protected
 * camera's format (AG-58, D-089), in its own directory and under its own master key on Android
 * (`AndroidProtectedVault.createEvidence`). No second cryptography: each picture is one entry of
 * the vault (its cleaned bytes), each record another one (a small JSON document,
 * [EvidenceFormat.RECORD_MIME]), both sealed by their own key.
 *
 * Nothing here talks to a server, a platform or an authority: the person copies or shares the
 * templates herself ([HarassmentReports]). There is no timestamp proof in step 1 (D-111 stamps
 * the platform's contents only): [EvidenceContent.keptAt] is the device's clock, said as such.
 */
class EvidenceLocker(
    private val vault: ProtectedVault,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private val mutex = Mutex()

    /**
     * Keeps [draft] (and [image], already through [EvidenceImages.prepare]) encrypted. The picture
     * goes first; if the record cannot be written, the picture is deleted with it. [image]'s bytes
     * are wiped once kept.
     */
    suspend fun keep(draft: EvidenceDraft, image: PreparedEvidenceImage?): KeepResult = mutex.withLock {
        val now = clock()
        val problems = draft.problems(hasImage = image != null, now = now)
        if (problems.isNotEmpty()) return@withLock KeepResult.Refused(problems)
        val seenAt = draft.seenAt(now) ?: return@withLock KeepResult.Refused(setOf(EvidenceProblem.DATE_INVALID))
        var picture: ProtectedPhoto? = null
        try {
            val kept = image?.let { prepared ->
                vault.store(prepared.bytes, ProtectedPhotoMeta(takenAt = now, cameraSha256 = prepared.originalSha256, mimeType = prepared.format.mimeType)).also { picture = it }
            }
            val content = EvidenceContent(
                seenAt = seenAt,
                keptAt = now,
                platform = draft.platform.trim(),
                url = draft.url.trim(),
                text = draft.text.trim(),
                note = draft.note.trim(),
                image = if (kept != null && image != null) {
                    EvidenceImage(image.originalSha256, kept.sha256, kept.sizeBytes, kept.mimeType, image.source)
                } else {
                    null
                },
            )
            val canonical = EvidenceFormat.canonical(content)
            val digest = EvidenceFormat.digestOf(canonical)
            val record = EvidenceRecord(EvidenceFormat.VERSION, canonical, digest, kept?.id)
            val bytes = EvidenceFormat.json.encodeToString(EvidenceRecord.serializer(), record).encodeToByteArray()
            val stored = try {
                vault.store(bytes, ProtectedPhotoMeta(takenAt = now, cameraSha256 = digest, mimeType = EvidenceFormat.RECORD_MIME))
            } finally {
                bytes.fill(0)
            }
            picture = null
            KeepResult.Kept(Evidence(stored.id, content, digest, kept?.id))
        } finally {
            image?.bytes?.fill(0)
            // A picture without its record is never left behind.
            picture?.let { orphan -> runCatching { vault.delete(orphan.id) } }
        }
    }

    /** The evidence kept, newest first; a record whose digest does not match its text is not shown. */
    suspend fun list(): EvidenceShelf = mutex.withLock {
        var unreadable = 0
        val items = vault.list().filter { it.mimeType == EvidenceFormat.RECORD_MIME }.mapNotNull { entry ->
            val evidence = try {
                read(entry.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (evidence == null) unreadable++
            evidence
        }
        EvidenceShelf(items.sortedByDescending { it.content.keptAt }, unreadable)
    }

    /** The decrypted bytes of the picture of [evidence], in memory; null when it has none or it is gone. */
    suspend fun image(evidence: Evidence): ByteArray? = mutex.withLock {
        val entry = evidence.imageEntry ?: return@withLock null
        val expected = evidence.content.image?.sha256 ?: return@withLock null
        val bytes = vault.open(entry) ?: return@withLock null
        if (MediaDigest.sha256Hex(bytes) != expected) {
            bytes.fill(0)
            throw EvidenceException("the picture does not match its evidence")
        }
        bytes
    }

    /** Deletes the evidence [id]: its picture first, then its record; throws if either is still there. */
    suspend fun delete(id: String) {
        mutex.withLock {
            val picture = runCatching { read(id)?.imageEntry }.getOrNull()
            if (picture != null) vault.delete(picture)
            vault.delete(id)
        }
    }

    /**
     * The part `agir-harcelement` of the quick erase (S-44): the vault destroys its own master key,
     * then its files ([ProtectedVault.eraseAll]); throws when something is left, may be called again.
     */
    suspend fun eraseAll() {
        mutex.withLock { vault.eraseAll() }
    }

    private suspend fun read(id: String): Evidence? {
        val bytes = vault.open(id) ?: return null
        try {
            val record = EvidenceFormat.json.decodeFromString(EvidenceRecord.serializer(), bytes.decodeToString())
            if (record.version != EvidenceFormat.VERSION) return null
            if (EvidenceFormat.digestOf(record.canonical) != record.digest) return null
            val content = EvidenceFormat.contentOf(record.canonical) ?: return null
            return Evidence(id, content, record.digest, record.imageEntry)
        } finally {
            bytes.fill(0)
        }
    }
}

class EvidenceException(message: String) : Exception(message)
