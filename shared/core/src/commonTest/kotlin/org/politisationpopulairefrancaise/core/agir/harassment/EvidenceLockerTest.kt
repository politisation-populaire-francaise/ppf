package org.politisationpopulairefrancaise.core.agir.harassment

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.camera.InMemoryProtectedVault
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedPhoto
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedPhotoMeta
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.media.MediaSamples
import org.politisationpopulairefrancaise.core.media.MetadataStripper
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 9 November 2026, 05:53 in Paris. */
private const val NOW = 1_794_200_000L

private const val URL = "https://reseau.example.org/publication/4821 [EXEMPLE]"
private const val CLEAN_URL = "https://reseau.example.org/publication/4821"
private const val TEXT = "[EXEMPLE] On sait où tu habites, on va venir."
private const val NOTE = "[EXEMPLE] Troisième message de ce compte cette semaine."

private fun draft(
    url: String = CLEAN_URL,
    text: String = TEXT,
    note: String = NOTE,
    date: String = "09/11/2026",
    time: String = "05:00",
) = EvidenceDraft(platform = "[EXEMPLE] Réseau social", url = url, text = text, note = note, seenDate = date, seenTime = time)

/** A screenshot with the metadata of the chain's tests (device model, position, date, author). */
private fun screenshot(): ByteArray = MediaSamples.withMetadata(MediaSamples.minimalJpeg(), 1)

/** A vault whose second write fails: the record of a piece of evidence after its picture. */
private class FailingRecordVault(private val inner: InMemoryProtectedVault = InMemoryProtectedVault()) : ProtectedVault by inner {
    private var stores = 0

    override suspend fun store(bytes: ByteArray, meta: ProtectedPhotoMeta): ProtectedPhoto {
        stores++
        if (stores == 2) error("[EXEMPLE] disk full")
        return inner.store(bytes, meta)
    }
}

/** AG-59: the evidence kept on the device, its digest, its picture without metadata, its erasure. */
class EvidenceLockerTest {
    private fun prepared(): PreparedEvidenceImage =
        assertIs<EvidenceImageResult.Ready>(EvidenceImages.prepare(screenshot(), EvidenceFormat.SOURCE_PICKER)).image

    @Test
    fun aPieceOfEvidenceComesBackAsItWasKept() = runTest {
        val vault = InMemoryProtectedVault()
        val locker = EvidenceLocker(vault) { NOW }
        val original = screenshot()
        val image = prepared()
        val cleaned = image.bytes.copyOf()
        val kept = assertIs<KeepResult.Kept>(locker.keep(draft(), image)).evidence
        // The picture's bytes handed over are wiped once kept.
        assertTrue(image.bytes.all { it == 0.toByte() })

        val shelf = locker.list()
        assertEquals(0, shelf.unreadable)
        val read = shelf.items.single()
        assertEquals(kept, read)
        val content = read.content
        assertEquals(CLEAN_URL, content.url)
        assertEquals(TEXT, content.text)
        assertEquals(NOTE, content.note)
        assertEquals("[EXEMPLE] Réseau social", content.platform)
        assertEquals(NOW, content.keptAt)
        // 05:00 in Paris on 9 November 2026 (UTC+1).
        assertEquals(NOW - (53 * 60 + 20), content.seenAt)

        // The picture: metadata removed, pixels untouched, both digests kept.
        val picture = assertNotNull(locker.image(read))
        assertContentEquals(cleaned, picture)
        assertTrue(MetadataStripper.isClean(picture))
        for (secret in MediaSamples.SECRETS) assertFalse(MediaSamples.contains(picture, secret), "still in the kept copy: $secret")
        val described = assertNotNull(content.image)
        assertEquals(MediaDigest.sha256Hex(original), described.originalSha256)
        assertEquals(MediaDigest.sha256Hex(picture), described.sha256)
        assertEquals(picture.size.toLong(), described.sizeBytes)
        assertEquals("image/jpeg", described.mimeType)
        assertEquals(EvidenceFormat.SOURCE_PICKER, described.source)
        // Two entries in the vault: the picture and the record.
        assertEquals(2, vault.list().size)
    }

    @Test
    fun theDigestIsTheSha256OfWhatWasKeptAndCoversEveryField() = runTest {
        val locker = EvidenceLocker(InMemoryProtectedVault()) { NOW }
        val kept = assertIs<KeepResult.Kept>(locker.keep(draft(), null)).evidence
        val canonical = EvidenceFormat.canonical(kept.content)
        assertEquals(MediaDigest.sha256Hex(canonical.encodeToByteArray()), kept.digest)
        assertEquals(kept.digest, EvidenceFormat.digestOf(kept.content))
        // The same content always gives the same bytes; the canonical text says what was kept.
        assertEquals(canonical, EvidenceFormat.canonical(kept.content.copy()))
        assertTrue(CLEAN_URL in canonical && "On sait où tu habites" in canonical)
        assertEquals(kept.content, EvidenceFormat.contentOf(canonical))
        // Any change, even of the note alone, changes the digest.
        for (changed in listOf(
            kept.content.copy(note = kept.content.note + "."),
            kept.content.copy(url = CLEAN_URL + "?"),
            kept.content.copy(text = TEXT.lowercase()),
            kept.content.copy(seenAt = kept.content.seenAt + 60),
            kept.content.copy(keptAt = kept.content.keptAt + 1),
            kept.content.copy(platform = "[EXEMPLE] Forum"),
        )) assertNotEquals(kept.digest, EvidenceFormat.digestOf(changed))
    }

    @Test
    fun whatCannotBeKeptIsSaidAndNothingIsWritten() = runTest {
        val vault = InMemoryProtectedVault()
        val locker = EvidenceLocker(vault) { NOW }
        assertEquals(setOf(EvidenceProblem.NOTHING_TO_KEEP), assertIs<KeepResult.Refused>(locker.keep(draft(url = "", text = ""), null)).problems)
        assertTrue(EvidenceProblem.URL_INVALID in assertIs<KeepResult.Refused>(locker.keep(draft(url = URL), null)).problems)
        assertTrue(EvidenceProblem.URL_INVALID in assertIs<KeepResult.Refused>(locker.keep(draft(url = "reseau.example.org/x"), null)).problems)
        assertTrue(EvidenceProblem.IN_FUTURE in assertIs<KeepResult.Refused>(locker.keep(draft(time = "23:00"), null)).problems)
        assertTrue(EvidenceProblem.DATE_INVALID in assertIs<KeepResult.Refused>(locker.keep(draft(date = "31/02/2026"), null)).problems)
        assertTrue(EvidenceProblem.TIME_INVALID in assertIs<KeepResult.Refused>(locker.keep(draft(time = "25:10"), null)).problems)
        assertTrue(EvidenceProblem.TEXT_TOO_LONG in assertIs<KeepResult.Refused>(locker.keep(draft(text = "a".repeat(EvidenceFormat.MAX_TEXT + 1)), null)).problems)
        assertTrue(vault.list().isEmpty())
        // A picture alone is enough; an address alone, a text alone too.
        assertIs<KeepResult.Kept>(locker.keep(draft(url = "", text = ""), prepared()))
        assertIs<KeepResult.Kept>(locker.keep(draft(text = ""), null))
        assertIs<KeepResult.Kept>(locker.keep(draft(url = ""), null))
        assertEquals(3, locker.list().items.size)
    }

    @Test
    fun onlyPicturesTheChainCanCleanAreAttached() {
        assertEquals(
            EvidenceImageRefusal.UNSUPPORTED,
            assertIs<EvidenceImageResult.Refused>(EvidenceImages.prepare("%PDF-1.7 [EXEMPLE]".encodeToByteArray(), EvidenceFormat.SOURCE_PICKER)).reason,
        )
        assertEquals(
            EvidenceImageRefusal.TOO_LARGE,
            assertIs<EvidenceImageResult.Refused>(EvidenceImages.prepare(ByteArray(PhotoPipeline.MAX_ORIGINAL_BYTES + 1), EvidenceFormat.SOURCE_PICKER)).reason,
        )
        val camera = assertIs<EvidenceImageResult.Ready>(EvidenceImages.prepare(screenshot(), EvidenceFormat.SOURCE_CAMERA)).image
        assertEquals(EvidenceFormat.SOURCE_CAMERA, camera.source)
    }

    @Test
    fun aPictureNeverStaysWithoutItsRecord() = runTest {
        val vault = FailingRecordVault()
        val locker = EvidenceLocker(vault) { NOW }
        val failure = runCatching { locker.keep(draft(), prepared()) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(vault.list().isEmpty(), "the picture went with its record")
    }

    @Test
    fun deletingAPieceOfEvidenceDeletesItsPictureToo() = runTest {
        val vault = InMemoryProtectedVault()
        val locker = EvidenceLocker(vault) { NOW }
        val first = assertIs<KeepResult.Kept>(locker.keep(draft(), prepared())).evidence
        val second = assertIs<KeepResult.Kept>(locker.keep(draft(note = ""), null)).evidence
        locker.delete(first.id)
        assertEquals(listOf(second), locker.list().items)
        assertEquals(1, vault.list().size)
        assertNull(vault.open(assertNotNull(first.imageEntry)))
    }

    @Test
    fun aRecordWhoseDigestDoesNotMatchIsNeverShown() = runTest {
        val vault = InMemoryProtectedVault()
        val locker = EvidenceLocker(vault) { NOW }
        val kept = assertIs<KeepResult.Kept>(locker.keep(draft(), null)).evidence
        val forged = EvidenceRecord(EvidenceFormat.VERSION, EvidenceFormat.canonical(kept.content.copy(text = "[EXEMPLE] changé")), kept.digest)
        vault.store(EvidenceFormat.json.encodeToString(EvidenceRecord.serializer(), forged).encodeToByteArray(), ProtectedPhotoMeta(NOW, kept.digest, EvidenceFormat.RECORD_MIME))
        val shelf = locker.list()
        assertEquals(listOf(kept), shelf.items)
        assertEquals(1, shelf.unreadable)
    }

    @Test
    fun theQuickEraseEmptiesTheVaultAndMayRunAgain() = runTest {
        val vault = InMemoryProtectedVault()
        val locker = EvidenceLocker(vault) { NOW }
        locker.keep(draft(), prepared())
        locker.eraseAll()
        assertTrue(vault.list().isEmpty())
        assertTrue(locker.list().items.isEmpty())
        locker.eraseAll()
        assertEquals(2, vault.erasures)
    }

    @Test
    fun theFieldsAreFilledWithThePresentMomentInParisTime() {
        assertEquals("09/11/2026" to "05:53", EvidenceFormat.typedDateAndTime(NOW))
        assertEquals("9 novembre 2026" to "05:53", EvidenceFormat.dateAndTime(NOW))
        val (date, time) = EvidenceFormat.typedDateAndTime(NOW)
        assertEquals(NOW - 20, EvidenceDraft(seenDate = date, seenTime = time).seenAt(NOW))
    }
}
