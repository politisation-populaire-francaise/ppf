package org.politisationpopulairefrancaise.core.agir.testimonies

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.media.CaptureSource
import org.politisationpopulairefrancaise.core.media.MediaSamples
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.media.PhotoResult
import org.politisationpopulairefrancaise.core.media.Provenance
import org.politisationpopulairefrancaise.core.messaging.GiftWrapException
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestimonyComposerTest {
    private val call = "30023:" + "a".repeat(64) + ":appel-temoins-exemple"

    private fun draft() = TestimonyDraft(
        text = "[EXEMPLE] J'ai vu un groupe bloquer la sortie de la place vers 18 h.",
        factDate = "2026-11-05",
        factTime = "18:05",
        place = "[EXEMPLE] Place fictive, Lyon",
        callAddress = call,
        charterAccepted = true,
    )

    private suspend fun draftWithPhoto(giveOriginal: Boolean = false): TestimonyDraft {
        val original = MediaSamples.withMetadata(MediaSamples.minimalJpeg())
        val provenance = Provenance.signWithOneTimeKey(original, "image/jpeg", CaptureSource.IMPORT)
        val prepared = assertIs<PhotoResult.Ready>(PhotoPipeline.prepare(original, provenance, codec = null)).photo
        return draft().copy(photo = prepared, giveOriginal = giveOriginal)
    }

    @Test
    fun theRulesNameEachProblem() {
        assertEquals(emptyList(), TestimonyRules.check(draft(), today = "2026-11-07"))
        assertEquals(listOf(TestimonyProblem.TEXT_TOO_SHORT), TestimonyRules.check(draft().copy(text = "  trop court  "), "2026-11-07"))
        assertEquals(listOf(TestimonyProblem.FUTURE_DATE), TestimonyRules.check(draft().copy(factDate = "2026-11-08"), "2026-11-07"))
        assertEquals(listOf(TestimonyProblem.BAD_DATE), TestimonyRules.check(draft().copy(factDate = "2026-02-30"), "2026-11-07"))
        assertEquals(listOf(TestimonyProblem.BAD_TIME), TestimonyRules.check(draft().copy(factTime = "24:10"), "2026-11-07"))
        assertEquals(listOf(TestimonyProblem.CHARTER_NOT_ACCEPTED), TestimonyRules.check(draft().copy(charterAccepted = false), "2026-11-07"))
        assertEquals(listOf(TestimonyProblem.BAD_CALL), TestimonyRules.check(draft().copy(callAddress = "31923:x:y"), "2026-11-07"))
        assertEquals(listOf(TestimonyProblem.CONTROL_CHARACTERS), TestimonyRules.check(draft().copy(text = draft().text + "\u0007"), "2026-11-07"))
        assertEquals(emptyList(), TestimonyRules.check(draft().copy(text = draft().text + "\r\nsuite", factDate = "", factTime = null, place = null, callAddress = null), "2026-11-07"))
        assertTrue(TestimonyRules.isValidDate("2028-02-29"))
        assertFalse(TestimonyRules.isValidDate("2027-02-29"))
    }

    @Test
    fun onlyTheModerationKeyOpensTheTestimonyAndTheWitnessIsTheAccount() = runTest {
        val account = LocalMessagingSigner(IdentityKeys.generate())
        val built = TestimonyComposer.build(draft(), account, TestimonyRecipients.EXAMPLE, now = 1_793_966_400)
        // The server sees a one-time key, the moderation key and an opaque content.
        assertFalse(built.envelope.pubkey == account.publicKey.hex)
        assertEquals(listOf(listOf("p", TestimonyRecipients.EXAMPLE.moderation.hex)), built.envelope.tags)
        assertFalse(built.envelope.content.contains("EXEMPLE"))
        assertNull(built.evidenceEnvelope)
        assertTrue(built.files.isEmpty() && built.stamps.isEmpty())

        val moderation = LocalMessagingSigner(TestimonyRecipients.exampleModerationKeys())
        val opened = TestimonyReader.open(built.envelope, moderation)
        assertEquals(account.publicKey.hex, opened.author)
        assertEquals(draft().text, opened.text)
        assertEquals("2026-11-05", opened.factDate)
        assertEquals("18:05", opened.factTime)
        assertEquals("[EXEMPLE] Place fictive, Lyon", opened.place)
        assertEquals(call, opened.callAddress)
        assertTrue(opened.media.isEmpty())

        // The evidence key, a stranger or the witness cannot open it.
        assertFailsWith<GiftWrapException> { TestimonyReader.open(built.envelope, LocalMessagingSigner(TestimonyRecipients.exampleEvidenceKeys())) }
        assertFailsWith<GiftWrapException> { TestimonyReader.open(built.envelope, LocalMessagingSigner(IdentityKeys.generate())) }
        assertFailsWith<GiftWrapException> { TestimonyReader.open(built.envelope, account) }
    }

    @Test
    fun thePhotoTravelsCleanAndTheOriginalOnlyForTheEvidenceKey() = runTest {
        val account = LocalMessagingSigner(IdentityKeys.generate())
        val photoDraft = draftWithPhoto()
        val photo = photoDraft.photo!!
        // Without the witness's consent: the original stays on the device.
        val kept = TestimonyComposer.build(photoDraft, account, TestimonyRecipients.EXAMPLE)
        assertNull(kept.evidenceEnvelope)
        assertEquals(1, kept.files.size)
        assertTrue(kept.evidenceFiles.isEmpty())
        assertEquals(listOf(photo.provenance.id, photo.publishedSha256), kept.stamps)
        val moderation = LocalMessagingSigner(TestimonyRecipients.exampleModerationKeys())
        val opened = TestimonyReader.open(kept.envelope, moderation)
        assertFalse(opened.originalGiven)
        val media = opened.media.single()
        assertEquals("image/jpeg", media.mimeType)
        assertEquals(photo.originalSha256, media.originalSha256)
        val published = TestimonyReader.decrypt(kept.files.single().bytes, media)
        assertContentEquals(photo.published, published)
        for (secret in MediaSamples.SECRETS) assertFalse(MediaSamples.contains(published, secret), secret)
        assertTrue(Provenance.matches(opened.provenance!!, photo.original))
        // The keys of the files were wiped once written in the envelope.
        assertTrue(kept.files.single().key.all { it == 0.toByte() })
        // An altered file is refused.
        assertFailsWith<TestimonyFormatException> { TestimonyReader.decrypt(kept.files.single().bytes.copyOf().also { it[50] = (it[50] + 1).toByte() }, media) }

        // With the consent: a second envelope that only the evidence key opens.
        val given = TestimonyComposer.build(photoDraft.copy(giveOriginal = true), account, TestimonyRecipients.EXAMPLE)
        val evidenceEnvelope = given.evidenceEnvelope!!
        assertEquals(listOf(listOf("p", TestimonyRecipients.EXAMPLE.evidence.hex)), evidenceEnvelope.tags)
        assertTrue(TestimonyReader.open(given.envelope, moderation).originalGiven)
        assertFailsWith<GiftWrapException> { TestimonyReader.openEvidence(evidenceEnvelope, moderation) }
        val evidence = TestimonyReader.openEvidence(evidenceEnvelope, LocalMessagingSigner(TestimonyRecipients.exampleEvidenceKeys()))
        assertEquals(TestimonyReader.open(given.envelope, moderation).rumor.id, evidence.testimonyId)
        val original = TestimonyReader.decrypt(given.evidenceFiles.single().bytes, evidence.media.single())
        assertContentEquals(photo.original, original)
        assertTrue(MediaSamples.contains(original, MediaSamples.GPS), "the original is untouched, metadata included")
    }

    @Test
    fun theExampleKeysAreStableAndDistinct() {
        val a = TestimonyRecipients.EXAMPLE
        assertEquals(TestimonyRecipients.exampleModerationKeys().publicKey, a.moderation)
        assertFalse(a.moderation == a.evidence)
        assertTrue(a.isExample)
        assertFalse(TestimonyRecipients(IdentityKeys.generate().publicKey, a.evidence).isExample)
    }

    @Test
    fun theOutgoingRequestNamesEveryFile() = runTest {
        val account = LocalMessagingSigner(IdentityKeys.generate())
        val built = TestimonyComposer.build(draftWithPhoto(giveOriginal = true), account, TestimonyRecipients.EXAMPLE)
        val outgoing = built.outgoing()
        assertEquals(built.id, outgoing.id)
        assertEquals(built.files.map { it.id }, outgoing.submission.files)
        assertEquals(built.evidenceFiles.map { it.id }, outgoing.submission.evidenceFiles)
        assertEquals(setOf(built.files.single().id, built.evidenceFiles.single().id), outgoing.files.keys)
        val json = TestimonyJson.encodeToString(TestimonySubmission.serializer(), outgoing.submission)
        assertEquals(outgoing.submission, TestimonyJson.decodeFromString(TestimonySubmission.serializer(), json))
    }
}
