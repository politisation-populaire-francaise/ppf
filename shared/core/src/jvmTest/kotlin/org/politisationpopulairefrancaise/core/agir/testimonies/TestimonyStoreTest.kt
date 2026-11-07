package org.politisationpopulairefrancaise.core.agir.testimonies

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.media.CaptureSource
import org.politisationpopulairefrancaise.core.media.MediaSamples
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.media.PhotoResult
import org.politisationpopulairefrancaise.core.media.Provenance
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** On a database in a temporary file (an in-memory SQLite is unreliable under load). */
class TestimonyStoreTest {
    private val directory = Files.createTempDirectory("ppf-testimonies")
    private val database = JvmDatabase.open(directory.resolve("ppf.db"))
    private val store = TestimonyStore(database)

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private suspend fun testimony(): TestimonyPackage {
        val original = MediaSamples.withMetadata(MediaSamples.minimalJpeg())
        val provenance = Provenance.signWithOneTimeKey(original, "image/jpeg", CaptureSource.IMPORT)
        val photo = assertIs<PhotoResult.Ready>(PhotoPipeline.prepare(original, provenance, codec = null)).photo
        val draft = TestimonyDraft("[EXEMPLE] Un témoignage fictif d'au moins vingt caractères.", photo = photo, giveOriginal = true, charterAccepted = true, callAddress = "30023:${"b".repeat(64)}:appel")
        return TestimonyComposer.build(draft, LocalMessagingSigner(IdentityKeys.generate()), TestimonyRecipients.EXAMPLE)
    }

    @Test
    fun anUnsentTestimonyWaitsEncryptedThenOnlyItsStateRemains() = runTest {
        val built = testimony()
        store.keep(built, now = 1_000)
        val waiting = store.all().single()
        assertEquals(TestimonyProgress.UNSENT, waiting.progress)
        assertTrue(waiting.hasPhoto && waiting.gaveOriginal)
        assertEquals("30023:${"b".repeat(64)}:appel", waiting.callAddress)
        assertEquals(listOf(built.id), store.unsent())
        val outgoing = store.outgoing(built.id)!!
        assertEquals(built.outgoing().submission, outgoing.submission)
        for (file in built.files + built.evidenceFiles) assertContentEquals(file.bytes, outgoing.files[file.id])
        // Nothing readable is kept: the text of the testimony is nowhere in the database file.
        val raw = Files.readAllBytes(directory.resolve("ppf.db"))
        assertFalse(MediaSamples.contains(raw, "témoignage fictif") || MediaSamples.contains(raw, "moins vingt"))

        store.markSent(built.id, now = 2_000)
        assertNull(store.outgoing(built.id))
        assertTrue(store.unsent().isEmpty())
        assertEquals(TestimonyProgress.PENDING, store.all().single().progress)

        store.update(TestimonyState(built.id, HeldStatus.REJECTED, 1_000, 3_000, "[EXEMPLE] Faits déjà publiés."), now = 3_000)
        assertEquals("[EXEMPLE] Faits déjà publiés.", store.all().single().reason)
        store.update(TestimonyState(built.id, HeldStatus.APPROVED, 1_000, 3_000, published = "c".repeat(64)), now = 3_000)
        assertEquals(TestimonyProgress.PUBLISHED, store.all().single().progress)
        assertEquals("c".repeat(64), store.all().single().published)

        store.clear()
        assertEquals(0, store.count())
    }

    @Test
    fun forgettingOneLeavesTheOthers() = runTest {
        val a = testimony()
        val b = testimony()
        store.keep(a, now = 1)
        store.keep(b, now = 2)
        store.forget(a.id)
        assertEquals(listOf(b.id), store.all().map { it.id })
        assertNull(store.outgoing(a.id))
    }
}
