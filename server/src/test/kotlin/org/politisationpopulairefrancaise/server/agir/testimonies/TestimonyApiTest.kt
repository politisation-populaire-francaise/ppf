package org.politisationpopulairefrancaise.server.agir.testimonies

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.testimonies.PublishedTestimony
import org.politisationpopulairefrancaise.core.agir.testimonies.ServerTestimonyTransport
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyComposer
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyDraft
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyJson
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyPackage
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyReader
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyRecipients
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonySendResult
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyStatuses
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonySubmission
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.media.CaptureSource
import org.politisationpopulairefrancaise.core.media.MediaCipher
import org.politisationpopulairefrancaise.core.media.MetadataStripper
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.media.PhotoResult
import org.politisationpopulairefrancaise.core.media.Provenance
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.nio.file.Files
import java.time.Instant
import kotlinx.html.div
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The testimony routes on the embedded PostgreSQL, with the device's real transport (shared/core). */
class TestimonyApiTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val witness = LocalMessagingSigner(IdentityKeys.generate())
    private val moderation = LocalMessagingSigner(TestimonyRecipients.exampleModerationKeys())
    private val evidenceKey = LocalMessagingSigner(TestimonyRecipients.exampleEvidenceKeys())
    private val policy = PublicationPolicy(setOf(editor.publicKey.hex))
    private val directory = TestimonyFixtures.temporaryDirectory()
    private val clock = TestimonyFixtures.MovableClock(Instant.parse("2026-11-07T15:42:17Z"))
    private val stamps = TestimonyFixtures.RecordingTimestamping()
    private val store = TestimonyStore(TestDatabase.dataSource, directory)
    private val space = TestimonySpace(store, TestimonyFixtures.config(directory), EventRepository(TestDatabase.dataSource), policy, clock, stamps)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun ApplicationTestBuilder.server(setup: TestimonySetup = TestimonySetup(TestimonyFixtures.config(directory), clock, stamps)) {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir(), testimonies = setup) }
    }

    private fun testimony(photo: Boolean = true, giveOriginal: Boolean = false): TestimonyPackage = runBlocking {
        val prepared = if (photo) {
            val original = TestimonyFixtures.dirtyJpeg()
            val provenance = Provenance.signWithOneTimeKey(original, "image/jpeg", CaptureSource.IMPORT)
            assertIs<PhotoResult.Ready>(PhotoPipeline.prepare(original, provenance, codec = null)).photo
        } else {
            null
        }
        val draft = TestimonyDraft(
            text = "[EXEMPLE] Vers 18 h, la sortie de la rue fictive était fermée par un cordon.",
            factDate = "2026-11-05",
            place = "[EXEMPLE] Place fictive",
            callAddress = "30023:${"a".repeat(64)}:appel-temoins",
            photo = prepared,
            giveOriginal = giveOriginal,
            charterAccepted = true,
        )
        TestimonyComposer.build(draft, witness, TestimonyRecipients.EXAMPLE)
    }

    @Test
    fun aTestimonyTravelsEncryptedIsReviewedThenPublishedWithItsCleanPhoto() = testApplication {
        server()
        val transport = ServerTestimonyTransport(client, baseUrl = "")
        val built = testimony(giveOriginal = true)
        assertEquals(TestimonySendResult.Sent(built.id), transport.send(built.outgoing()))
        // Sending again is harmless.
        assertEquals(TestimonySendResult.Sent(built.id), transport.send(built.outgoing()))

        // The server holds ciphertext only: nothing of the photo, its metadata or the text on its disk.
        val onDisk = Files.list(directory.resolve(TestimonyStore.FILES)).use { files -> files.toList() }
        assertEquals(2, onDisk.size)
        for (file in onDisk) {
            val bytes = Files.readAllBytes(file)
            assertFalse(TestimonyFixtures.contains(bytes, "GPS") || TestimonyFixtures.contains(bytes, "EXEMPLE") || TestimonyFixtures.contains(bytes, "JFIF"))
        }
        val stored = assertNotNull(store.find(built.id))
        assertEquals(HeldStatus.PENDING, stored.status)
        assertEquals(built.evidenceEnvelope!!.id, stored.evidenceId)
        // The hour, not the moment.
        assertEquals(Instant.parse("2026-11-07T15:00:00Z").epochSecond, stored.receivedAt)
        // Only digests went to the archive: the provenance of the photo and its published copy.
        assertEquals(built.stamps, stamps.digests.distinct())

        val pending = assertIs<TestimonyStatuses.Known>(transport.statuses(listOf(built.id, "f".repeat(64)))).states
        assertEquals(listOf(built.id to HeldStatus.PENDING), pending.map { it.id to it.status })

        // The team opens it with its key, decrypts the photo and publishes a public version signed by an editorial key.
        val opened = TestimonyReader.open(stored.envelope, moderation)
        assertEquals(witness.publicKey.hex, opened.author)
        val media = opened.media.single()
        val photo = TestimonyReader.decrypt(assertNotNull(space.encryptedFile(media.fileId)), media)
        assertTrue(MetadataStripper.isClean(photo))
        assertFalse(TestimonyFixtures.contains(photo, "GPS"))
        val published = editor.sign(
            PublishedTestimony.unsigned(editor.publicKey, "[EXEMPLE] Sortie fermée", opened.text, opened.factDate, place = opened.place, callAddress = opened.callAddress, photos = listOf(PublishedTestimony.Photo(photo))),
        )
        assertEquals(DecisionResult.Approved(published.id), space.approve(built.id, published, listOf(photo)))
        assertEquals(DecisionResult.AlreadyDecided, space.approve(built.id, published, listOf(photo)))

        val decided = assertIs<TestimonyStatuses.Known>(transport.statuses(listOf(built.id))).states.single()
        assertEquals(HeldStatus.APPROVED, decided.status)
        assertEquals(published.id, decided.published)
        // The public testimony is an ordinary content, its photo served by its SHA-256.
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/events/${published.id}").status)
        val served = client.get("/api/v1/media/${org.politisationpopulairefrancaise.core.media.MediaDigest.sha256Hex(photo)}")
        assertEquals(HttpStatusCode.OK, served.status)
        assertEquals("image/jpeg", served.headers["Content-Type"])
        assertContentEquals(photo, served.bodyAsBytes())
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/media/${"e".repeat(64)}").status)

        // The original for the lawyers: only the evidence key opens its envelope, and it is untouched.
        val evidence = TestimonyReader.openEvidence(assertNotNull(space.evidenceEnvelope(stored.evidenceId!!)), evidenceKey)
        val original = TestimonyReader.decrypt(assertNotNull(space.encryptedFile(evidence.media.single().fileId)), evidence.media.single())
        assertTrue(TestimonyFixtures.contains(original, TestimonyFixtures.SECRET_GPS))
    }

    @Test
    fun theServerRefusesWhatItCannotTrust() = testApplication {
        server()
        suspend fun submit(submission: TestimonySubmission): Pair<HttpStatusCode, String> {
            val response = client.post("/api/v1/testimonies") {
                contentType(ContentType.Application.Json)
                setBody(TestimonyJson.encodeToString(TestimonySubmission.serializer(), submission))
            }
            return response.status to response.bodyAsText()
        }
        val built = testimony(photo = true)
        val outgoing = built.outgoing()
        // Files first: a submission naming an unknown file is refused.
        val (missing, missingBody) = submit(outgoing.submission)
        assertEquals(HttpStatusCode.NotFound, missing)
        assertTrue("missing_file" in missingBody)
        // A file that is not encrypted (a raw photo) is refused.
        val raw = client.post("/api/v1/testimonies/files") {
            contentType(ContentType.Application.OctetStream)
            setBody(TestimonyFixtures.dirtyJpeg())
        }
        assertEquals(HttpStatusCode.BadRequest, raw.status)
        assertTrue("not_encrypted" in raw.bodyAsText())
        for ((_, bytes) in outgoing.files) {
            val response = client.post("/api/v1/testimonies/files") {
                contentType(ContentType.Application.OctetStream)
                setBody(bytes)
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
        // An envelope for another key than the team's.
        val stranger = runBlocking {
            TestimonyComposer.build(TestimonyDraft("[EXEMPLE] Un témoignage pour une autre clé que celle de l'équipe.", charterAccepted = true), witness, TestimonyRecipients(IdentityKeys.generate().publicKey, TestimonyRecipients.EXAMPLE.evidence))
        }
        assertTrue("wrong_recipient" in submit(stranger.outgoing().submission).second)
        // A forged envelope (content changed after signing).
        val forged = outgoing.submission.copy(envelope = outgoing.submission.envelope.copy(content = outgoing.submission.envelope.content.reversed()))
        assertTrue("invalid_signature" in submit(forged).second)
        // Evidence files without their envelope.
        assertTrue("bad_evidence" in submit(outgoing.submission.copy(evidenceFiles = outgoing.submission.files)).second)
        // Stamps that are not digests.
        assertTrue("bad_stamps" in submit(outgoing.submission.copy(stamps = listOf("not-a-digest"))).second)
        assertTrue("malformed" in client.post("/api/v1/testimonies") { contentType(ContentType.Application.Json); setBody("{}") }.bodyAsText())
        // The right one goes, once; its file cannot be claimed by another testimony.
        assertEquals(HttpStatusCode.Accepted, submit(outgoing.submission).first)
        assertEquals(HttpStatusCode.OK to true, submit(outgoing.submission).let { it.first to ("duplicate" in it.second) })
        val other = testimony(photo = false).outgoing().submission.copy(files = outgoing.submission.files)
        assertTrue("file_taken" in submit(other).second)
        // Status: bad ids are refused, unknown ones absent, and nothing may cache the answer.
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/testimonies/status?id=xyz").status)
        val status = client.get("/api/v1/testimonies/status?id=${built.id}")
        assertEquals("no-store", status.headers["Cache-Control"])
        assertTrue(built.id in status.bodyAsText())
    }

    @Test
    fun aFileTooLargeIsRefused() = testApplication {
        server(TestimonySetup(TestimonyFixtures.config(directory, maxFileBytes = 200), clock, stamps))
        val large = MediaCipher.encrypt(ByteArray(1000) { 1 }).bytes
        val response = client.post("/api/v1/testimonies/files") {
            contentType(ContentType.Application.OctetStream)
            setBody(large)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun withoutAFolderEveryRouteAnswersUnavailable() = testApplication {
        server(TestimonySetup(TestimonyFixtures.config(directory = null), clock, stamps))
        val response = client.post("/api/v1/testimonies/files") { setBody(ByteArray(200)) }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue("testimonies_unavailable" in response.bodyAsText())
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/testimonies/status?id=${"a".repeat(64)}").status)
    }

    @Test
    fun theTeamCannotPublishADirtyPhotoNorWithoutAnEditorialKey() = runBlocking {
        val built = testimony(photo = true)
        built.outgoing().files.values.forEach { space.receiveFile(it) }
        assertIs<SubmitOutcome.Queued>(space.submit(built.outgoing().submission))
        val dirty = TestimonyFixtures.dirtyJpeg()
        val withDirty = editor.sign(PublishedTestimony.unsigned(editor.publicKey, "[EXEMPLE] Titre", "[EXEMPLE] Texte publié.", photos = listOf(PublishedTestimony.Photo(dirty))))
        assertEquals(DecisionResult.Refused("a photo still carries metadata"), space.approve(built.id, withDirty, listOf(dirty)))
        val citizen = LocalEventSigner(IdentityKeys.generate())
        val notEditor = citizen.sign(PublishedTestimony.unsigned(citizen.publicKey, "[EXEMPLE] Titre", "[EXEMPLE] Texte publié."))
        assertEquals(DecisionResult.Refused("not signed by an editorial key"), space.approve(built.id, notEditor, emptyList()))
        val missingPhoto = editor.sign(PublishedTestimony.unsigned(editor.publicKey, "[EXEMPLE] Titre", "[EXEMPLE] Texte publié.", photos = listOf(PublishedTestimony.Photo(TestimonyFixtures.dirtyJpeg()))))
        assertEquals(DecisionResult.Refused("a photo named by the content is missing"), space.approve(built.id, missingPhoto, emptyList()))
        assertEquals(HeldStatus.PENDING, store.find(built.id)!!.status)
        assertEquals(DecisionResult.Rejected, space.reject(built.id, "[EXEMPLE] Les visages des passants ne sont pas floutés."))
        assertEquals("[EXEMPLE] Les visages des passants ne sont pas floutés.", store.states(listOf(built.id)).single().reason)
        assertEquals(DecisionResult.NotFound, space.reject("0".repeat(64), "[EXEMPLE] motif"))
    }

    @Test
    fun retentionDeletesOrphansDecidedTestimoniesAndOldOriginals() = runBlocking {
        // An orphan file: never claimed, deleted after a day.
        val orphan = MediaCipher.encrypt(ByteArray(500) { 3 })
        assertIs<FileOutcome.Stored>(space.receiveFile(orphan.bytes))
        // A testimony with an original for the lawyers, rejected.
        val built = testimony(photo = true, giveOriginal = true)
        built.outgoing().files.values.forEach { space.receiveFile(it) }
        assertIs<SubmitOutcome.Queued>(space.submit(built.outgoing().submission))
        assertEquals(DecisionResult.Rejected, space.reject(built.id, "[EXEMPLE] Hors charte."))

        clock.now = clock.now.plusSeconds(25 * 3600)
        space.purgeOnce()
        assertNull(space.encryptedFile(orphan.id))
        assertNotNull(store.find(built.id))

        // 30 days after the decision the testimony and its files go; the original for the lawyers stays.
        clock.now = clock.now.plusSeconds(30L * 86_400)
        space.purgeOnce()
        assertNull(store.find(built.id))
        assertNull(space.encryptedFile(built.files.single().id))
        val evidenceId = built.evidenceEnvelope!!.id
        assertNotNull(space.evidenceEnvelope(evidenceId))
        assertNotNull(space.encryptedFile(built.evidenceFiles.single().id))

        // Six years later, the original goes too.
        clock.now = clock.now.plusSeconds(2190L * 86_400)
        space.purgeOnce()
        assertNull(space.evidenceEnvelope(evidenceId))
        assertNull(space.encryptedFile(built.evidenceFiles.single().id))
        assertFalse(Files.exists(directory.resolve(TestimonyStore.FILES).resolve(built.evidenceFiles.single().id)))
    }

    @Test
    fun productionRefusesTheExampleKeys() {
        val example = TestimonyConfig.fromEnvironment { name -> mapOf("PPF_ENVIRONMENT" to "production", "PPF_TESTIMONY_DIR" to "/tmp/x")[name] }
        assertFalse(example.open)
        val explicitExample = TestimonyConfig.fromEnvironment { name ->
            mapOf(
                "PPF_ENVIRONMENT" to "production",
                "PPF_TESTIMONY_DIR" to "/tmp/x",
                "PPF_MODERATION_KEY" to TestimonyRecipients.EXAMPLE.moderation.hex,
                "PPF_EVIDENCE_KEY" to TestimonyRecipients.EXAMPLE.evidence.hex,
            )[name]
        }
        assertFalse(explicitExample.open)
        val real = TestimonyConfig.fromEnvironment { name ->
            mapOf(
                "PPF_ENVIRONMENT" to "production",
                "PPF_TESTIMONY_DIR" to "/tmp/x",
                "PPF_MODERATION_KEY" to IdentityKeys.generate().publicKey.npub,
                "PPF_EVIDENCE_KEY" to IdentityKeys.generate().publicKey.hex,
            )[name]
        }
        assertTrue(real.open)
        val development = TestimonyConfig.fromEnvironment { name -> mapOf("PPF_TESTIMONY_DIR" to "/tmp/x")[name] }
        assertTrue(development.open && development.recipients.isExample)
        val same = IdentityKeys.generate().publicKey.hex
        assertFalse(TestimonyConfig.fromEnvironment { name -> mapOf("PPF_TESTIMONY_DIR" to "/tmp/x", "PPF_MODERATION_KEY" to same, "PPF_EVIDENCE_KEY" to same)[name] }.open)
    }

    @Test
    fun nothingOfTheRequestIsStored() {
        val columns = TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT table_name || '.' || column_name FROM information_schema.columns WHERE table_name IN ('testimony_submission', 'testimony_evidence', 'testimony_file', 'published_media')",
                ).use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
            }
        }.sorted()
        assertEquals(
            listOf(
                "published_media.event_id", "published_media.mime", "published_media.sha256", "published_media.size",
                "testimony_evidence.envelope", "testimony_evidence.expires_at", "testimony_evidence.id", "testimony_evidence.received_at",
                "testimony_file.evidence", "testimony_file.id", "testimony_file.owner", "testimony_file.received_at", "testimony_file.size",
                "testimony_submission.decided_at", "testimony_submission.envelope", "testimony_submission.evidence_id", "testimony_submission.id",
                "testimony_submission.published_id", "testimony_submission.reason", "testimony_submission.received_at", "testimony_submission.status",
            ),
            columns,
        )
    }

    @Test
    fun theBackOfficeQueueShowsOnlyWhatTheServerKnowsAndRefusesWithAReason() = runBlocking {
        val queue = TestimonyQueue(space)
        val built = testimony(photo = true, giveOriginal = true)
        built.outgoing().files.values.forEach { space.receiveFile(it) }
        assertIs<SubmitOutcome.Queued>(space.submit(built.outgoing().submission))
        assertEquals("queue.agir.testimonies.decide", queue.viewPermission)
        assertTrue(queue.viewPermission in org.politisationpopulairefrancaise.server.backoffice.TeamRole.MODERATOR.permissions)
        assertEquals(1, queue.pendingCount())
        val item = queue.page(null, 10).items.single()
        assertEquals(built.id, item.id)
        assertEquals("1 photo chiffrée, ${TestimonyQueue.ORIGINAL_GIVEN}", item.summary)
        val html = kotlinx.html.stream.createHTML().div { queue.render(built.id, this) }
        assertTrue(TestimonyQueue.UNREADABLE.substringBefore(" :") in html)
        // Nothing of the testimony reaches the page: neither its text, nor its place, nor the witness key.
        assertFalse("rue fictive" in html)
        assertFalse("Place fictive" in html)
        assertFalse(witness.publicKey.npub in html)
        val reject = queue.actions(built.id).single()
        assertEquals(TestimonyQueue.REJECT, reject.id)
        assertTrue(reject.reasonRequired && reject.sensitive)
        val actor = org.politisationpopulairefrancaise.server.backoffice.TeamActor.of(
            java.util.UUID.randomUUID(), "Modo", setOf(org.politisationpopulairefrancaise.server.backoffice.TeamRole.MODERATOR), 2,
        )
        assertIs<org.politisationpopulairefrancaise.server.backoffice.ActionOutcome.Refused>(queue.perform(built.id, TestimonyQueue.REJECT, "  ", actor))
        assertIs<org.politisationpopulairefrancaise.server.backoffice.ActionOutcome.Refused>(queue.perform(built.id, "approve", null, actor))
        assertEquals(org.politisationpopulairefrancaise.server.backoffice.ActionOutcome.Done, queue.perform(built.id, TestimonyQueue.REJECT, "[EXEMPLE] Hors charte.", actor))
        assertEquals(org.politisationpopulairefrancaise.server.backoffice.ActionOutcome.AlreadyDecided, queue.perform(built.id, TestimonyQueue.REJECT, "[EXEMPLE] Hors charte.", actor))
        assertEquals(HeldStatus.REJECTED, space.find(built.id)?.status)
        assertEquals(0, queue.pendingCount())
        assertTrue(queue.actions(built.id).isEmpty())
        assertEquals(org.politisationpopulairefrancaise.server.backoffice.ActionOutcome.NotFound, queue.perform("0".repeat(64), TestimonyQueue.REJECT, "[EXEMPLE] motif", actor))
    }
}