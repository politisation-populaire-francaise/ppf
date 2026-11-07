package org.politisationpopulairefrancaise.server.agir.testimonies

import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyFormats
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonySubmission
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.media.ImageFormat
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.media.MediaSniffer
import org.politisationpopulairefrancaise.core.media.MetadataStripper
import org.politisationpopulairefrancaise.core.messaging.Nip44
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import java.time.Clock
import javax.sql.DataSource
import kotlin.time.Duration

/** A refusal of the testimony API: HTTP status, stable code (as in docs/integration/agir-temoignages.md), English message. */
data class TestimonyRefusal(val status: Int, val code: String, val message: String)

sealed interface FileOutcome {
    data class Stored(val id: String, val created: Boolean) : FileOutcome
    data class Refused(val refusal: TestimonyRefusal) : FileOutcome
}

sealed interface SubmitOutcome {
    data class Queued(val id: String) : SubmitOutcome
    data class Duplicate(val id: String) : SubmitOutcome
    data class Refused(val refusal: TestimonyRefusal) : SubmitOutcome
}

sealed interface DecisionResult {
    data class Approved(val publishedId: String) : DecisionResult
    data object Rejected : DecisionResult
    data object NotFound : DecisionResult
    data object AlreadyDecided : DecisionResult
    data class Refused(val reason: String) : DecisionResult
}

/**
 * The testimonies on the server (M5; docs/integration/agir-temoignages.md, section 3). The server
 * checks the form of what it receives (envelopes addressed to the right keys, encrypted files of a
 * plausible shape and size, names that match) but can read none of it. After the team's review
 * (back-office to come, M15; development routes meanwhile), the public version, cleaned on the
 * device, checked by the team and signed by an editorial key outside the server, is published as an
 * ordinary content; its photos are checked once more for metadata before they are served.
 *
 * Logs receive counts and class names only: never an id, a digest, a size or anything of a request.
 */
class TestimonySpace(
    private val store: TestimonyStore,
    private val config: TestimonyConfig,
    private val events: EventRepository,
    private val policy: PublicationPolicy,
    private val clock: Clock,
    private val timestamping: org.politisationpopulairefrancaise.core.media.Timestamping,
) {
    private fun nowSeconds(): Long = clock.instant().epochSecond

    /** Dates kept by the server are rounded down to the hour: the exact moment of a testimony is not needed. */
    private fun hour(seconds: Long): Long = seconds - seconds % 3600

    /** An encrypted file, as `POST /api/v1/testimonies/files` receives it. Blocking (disk, database). */
    fun receiveFile(bytes: ByteArray): FileOutcome {
        if (bytes.size > config.maxFileBytes) return FileOutcome.Refused(TOO_LARGE)
        // The shape of MediaCipher: version 2, nonce, at least one padded block, authentication code. Nothing else can be checked.
        if (bytes.size < MIN_ENCRYPTED_BYTES || bytes[0].toInt() != Nip44.VERSION) return FileOutcome.Refused(NOT_ENCRYPTED)
        val id = MediaDigest.sha256Hex(bytes)
        if (store.hasFile(id)) return FileOutcome.Stored(id, created = false)
        if (store.storedBytes() + bytes.size > config.maxDiskBytes) return FileOutcome.Refused(STORAGE_FULL)
        val created = store.putFile(id, bytes, hour(nowSeconds()))
        return FileOutcome.Stored(id, created)
    }

    /** A testimony, as `POST /api/v1/testimonies` receives it. Blocking (database) except the timestamps. */
    suspend fun submit(submission: TestimonySubmission): SubmitOutcome {
        val envelope = submission.envelope
        val evidence = submission.evidenceEnvelope
        checkEnvelope(envelope, config.recipients.moderation.hex)?.let { return SubmitOutcome.Refused(it) }
        if (evidence != null) checkEnvelope(evidence, config.recipients.evidence.hex)?.let { return SubmitOutcome.Refused(it.copy(code = "bad_evidence_envelope")) }
        if ((evidence == null) != submission.evidenceFiles.isEmpty()) return SubmitOutcome.Refused(BAD_EVIDENCE)
        val all = submission.files + submission.evidenceFiles
        if (submission.files.size > MAX_FILES || submission.evidenceFiles.size > MAX_FILES || all.toSet().size != all.size) return SubmitOutcome.Refused(BAD_FILES)
        if (all.any { !MediaDigest.isHex(it) }) return SubmitOutcome.Refused(BAD_FILES)
        if (submission.stamps.size > MAX_STAMPS || submission.stamps.any { !MediaDigest.isHex(it) }) return SubmitOutcome.Refused(BAD_STAMPS)
        val owners = store.owners(all)
        if (owners.keys != all.toSet()) return SubmitOutcome.Refused(MISSING_FILE)
        val now = nowSeconds()
        val outcome = store.insert(
            envelope = envelope,
            files = submission.files,
            evidence = evidence,
            evidenceFiles = submission.evidenceFiles,
            receivedAt = hour(now),
            evidenceExpiresAt = now + config.evidenceRetention.inWholeSeconds,
        )
        return when (outcome) {
            TestimonyStore.Insert.Duplicate -> SubmitOutcome.Duplicate(envelope.id)
            TestimonyStore.Insert.FileTaken -> SubmitOutcome.Refused(FILE_TAKEN)
            TestimonyStore.Insert.Stored -> {
                // Only digests leave for the timestamped archive (D-111); a failure never refuses the testimony.
                for (digest in submission.stamps) {
                    try {
                        timestamping.stamp(digest.hexToByteArray())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The archive is best effort here: the testimony is kept.
                    }
                }
                SubmitOutcome.Queued(envelope.id)
            }
        }
    }

    private fun checkEnvelope(envelope: SignedEvent, recipient: String): TestimonyRefusal? = when {
        envelope.kind != EventKinds.GIFT_WRAP -> BAD_ENVELOPE
        envelope.tags.size != 1 || envelope.tags.single().size < 2 || envelope.tags.single()[0] != "p" -> BAD_ENVELOPE
        envelope.tags.single()[1] != recipient -> WRONG_RECIPIENT
        envelope.content.length > MAX_ENVELOPE_CONTENT -> TestimonyRefusal(413, "too_large", "An envelope is at most $MAX_ENVELOPE_CONTENT characters.")
        !envelope.verify() -> TestimonyRefusal(400, "invalid_signature", "The id or the signature does not match the content.")
        else -> null
    }

    fun statuses(ids: List<String>) = store.states(ids)

    fun pending(): List<StoredTestimony> = store.pending()

    fun find(id: String): StoredTestimony? = store.find(id)

    fun encryptedFile(id: String): ByteArray? = store.readFile(id)

    fun evidenceEnvelope(id: String): SignedEvent? = store.evidenceEnvelope(id)

    fun publicMedia(sha256: String): Pair<String, ByteArray>? = if (MediaDigest.isHex(sha256)) store.readPublic(sha256) else null

    /**
     * Publishes the public version of a pending testimony: [published] is a content of kind 30023,
     * `ppf-type` = `temoignage`, signed by an editorial key (outside the server: the platform's keys
     * never live on it), and [photos] the files its `imeta` tags name by SHA-256 (field `x`). Each
     * photo must be a supported image without any metadata left, or nothing is published.
     */
    fun approve(id: String, published: SignedEvent, photos: List<ByteArray>): DecisionResult {
        val testimony = store.find(id) ?: return DecisionResult.NotFound
        if (testimony.status != HeldStatus.PENDING) return DecisionResult.AlreadyDecided
        if (published.kind != Article.KIND || !published.verify()) return DecisionResult.Refused("not a signed content of kind ${Article.KIND}")
        if (!policy.isEditor(published.pubkey)) return DecisionResult.Refused("not signed by an editorial key")
        if (published.firstTagValue("ppf-type") != TestimonyFormats.PUBLISHED_TYPE || published.firstTagValue("d").isNullOrBlank()) {
            return DecisionResult.Refused("not a testimony (ppf-type ${TestimonyFormats.PUBLISHED_TYPE}, d)")
        }
        val bySha = photos.associateBy { MediaDigest.sha256Hex(it) }
        val named = published.tags.filter { it.firstOrNull() == TestimonyFormats.TAG_MEDIA }.map { TestimonyFormats.fields(it) }
        val checked = mutableListOf<Triple<String, String, ByteArray>>()
        for (fields in named) {
            val sha = fields[TestimonyFormats.MEDIA_SHA256] ?: return DecisionResult.Refused("a media tag names no SHA-256")
            val bytes = bySha[sha] ?: return DecisionResult.Refused("a photo named by the content is missing")
            val format = MediaSniffer.imageFormatOf(bytes) ?: return DecisionResult.Refused("a photo is not a supported image")
            if (fields[TestimonyFormats.MEDIA_MIME] != format.mimeType) return DecisionResult.Refused("a media type does not match its photo")
            if (!MetadataStripper.isClean(bytes)) return DecisionResult.Refused("a photo still carries metadata")
            checked += Triple(sha, format.mimeType, bytes)
        }
        if (checked.size != photos.size) return DecisionResult.Refused("a photo is not named by the content")
        checked.forEach { (sha, mime, bytes) -> store.putPublic(sha, mime, bytes, published.id) }
        when (events.store(published)) {
            StoreOutcome.STORED, StoreOutcome.DUPLICATE -> Unit
            else -> return DecisionResult.Refused("the content could not be stored (a newer version exists)")
        }
        if (!store.decide(id, HeldStatus.APPROVED, nowSeconds(), null, published.id)) return DecisionResult.AlreadyDecided
        return DecisionResult.Approved(published.id)
    }

    fun reject(id: String, reason: String): DecisionResult {
        require(reason.isNotBlank() && reason.length <= MAX_REASON_LENGTH) { "A reason of 1 to $MAX_REASON_LENGTH characters" }
        val testimony = store.find(id) ?: return DecisionResult.NotFound
        if (testimony.status != HeldStatus.PENDING) return DecisionResult.AlreadyDecided
        return if (store.decide(id, HeldStatus.REJECTED, nowSeconds(), reason.trim(), null)) DecisionResult.Rejected else DecisionResult.AlreadyDecided
    }

    /** One pass of the retention rules (see V9__agir_temoignages.sql); returns the number of rows deleted. */
    fun purgeOnce(): Int {
        val now = nowSeconds()
        return store.purge(now, orphansBefore = now - config.orphanRetention.inWholeSeconds, decidedBefore = now - config.retention.inWholeSeconds)
    }

    companion object {
        const val MAX_FILES: Int = 4
        const val MAX_STAMPS: Int = 8
        const val MAX_REASON_LENGTH: Int = 1000

        /** Body of POST /api/v1/testimonies: two envelopes of 64 KiB at most and a few names. */
        const val MAX_SUBMISSION_BYTES: Long = 160 * 1024

        /** Content of an envelope: a NIP-44 payload of 64 KiB of plaintext at most, in base64. */
        const val MAX_ENVELOPE_CONTENT: Int = 90_000

        /** Version, nonce, smallest padded block (2 + 32), authentication code. */
        const val MIN_ENCRYPTED_BYTES: Int = 1 + 32 + 34 + 32

        val TOO_LARGE = TestimonyRefusal(413, "too_large", "This file is larger than the server accepts.")
        val NOT_ENCRYPTED = TestimonyRefusal(400, "not_encrypted", "Only files encrypted on the device are accepted.")
        val STORAGE_FULL = TestimonyRefusal(507, "storage_full", "The server cannot keep more files for now; try again later.")
        val BAD_ENVELOPE = TestimonyRefusal(400, "bad_envelope", "A testimony is a gift wrap (kind 1059) with one p tag.")
        val WRONG_RECIPIENT = TestimonyRefusal(400, "wrong_recipient", "The envelope is not encrypted for the key of this server's team.")
        val BAD_EVIDENCE = TestimonyRefusal(400, "bad_evidence", "An original for the lawyers comes with its envelope, and the other way round.")
        val BAD_FILES = TestimonyRefusal(400, "bad_files", "Files are named by distinct SHA-256 digests, $MAX_FILES at most per envelope.")
        val BAD_STAMPS = TestimonyRefusal(400, "bad_stamps", "At most $MAX_STAMPS SHA-256 digests to timestamp.")
        val MISSING_FILE = TestimonyRefusal(404, "missing_file", "A named file has not been sent yet.")
        val FILE_TAKEN = TestimonyRefusal(409, "file_taken", "A named file belongs to another testimony.")

        fun start(application: Application, dataSource: DataSource, events: EventRepository, policy: PublicationPolicy, setup: TestimonySetup): TestimonySpace? {
            val config = setup.config
            val directory = config.directory
            if (directory == null || config.closedReason != null) {
                application.log.info("Testimonies: closed (${config.closedReason ?: "${TestimonyConfig.DIRECTORY_VARIABLE} is not set"}).")
                return null
            }
            val space = TestimonySpace(TestimonyStore(dataSource, directory), config, events, policy, setup.clock, setup.timestamping)
            if (config.recipients.isExample) application.log.warn("Testimonies: encrypted for the EXAMPLE keys, which anybody can open. Development only.")
            val interval: Duration = config.cleanupInterval
            if (interval.isPositive()) {
                application.launch(Dispatchers.IO) {
                    while (isActive) {
                        try {
                            val deleted = space.purgeOnce()
                            if (deleted > 0) application.log.info("Testimonies retention: $deleted row(s) deleted.")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            application.log.warn("Testimonies retention failed (${e::class.simpleName}); next attempt in $interval.")
                        }
                        delay(interval)
                    }
                }
            }
            return space
        }
    }
}
