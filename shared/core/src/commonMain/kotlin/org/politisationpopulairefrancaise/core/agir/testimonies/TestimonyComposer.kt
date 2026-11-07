package org.politisationpopulairefrancaise.core.agir.testimonies

import org.politisationpopulairefrancaise.core.media.EncryptedFile
import org.politisationpopulairefrancaise.core.media.MediaCipher
import org.politisationpopulairefrancaise.core.media.PreparedPhoto
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** What the witness wrote and chose, before anything is encrypted. */
data class TestimonyDraft(
    val text: String,
    /** Day of the facts, "AAAA-MM-JJ", as declared by the witness (nothing is read from the photo). */
    val factDate: String? = null,
    /** Time of the facts, "HH:MM", declared. */
    val factTime: String? = null,
    /** Place of the facts, declared, in words (no position is read from the device). */
    val place: String? = null,
    /** The call for witnesses this testimony answers (`30023:<key>:<d>`), if any. */
    val callAddress: String? = null,
    val photo: PreparedPhoto? = null,
    /** False by default (principle 10): the untouched original, metadata included, stays on the device. */
    val giveOriginal: Boolean = false,
    val charterAccepted: Boolean = false,
)

/** Why a draft cannot be sent yet; each problem names its field. */
enum class TestimonyProblem {
    TEXT_TOO_SHORT,
    TEXT_TOO_LONG,
    BAD_DATE,
    FUTURE_DATE,
    BAD_TIME,
    PLACE_TOO_LONG,
    BAD_CALL,
    CHARTER_NOT_ACCEPTED,
    CONTROL_CHARACTERS,
}

object TestimonyRules {
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TIME = Regex("""\d{2}:\d{2}""")

    /** The problems of [draft]; [today] ("AAAA-MM-JJ", Paris) refuses a date in the future. */
    fun check(draft: TestimonyDraft, today: String? = null): List<TestimonyProblem> = buildList {
        val text = draft.text.trim()
        if (text.length < TestimonyFormats.MIN_TEXT_LENGTH) add(TestimonyProblem.TEXT_TOO_SHORT)
        if (text.length > TestimonyFormats.MAX_TEXT_LENGTH) add(TestimonyProblem.TEXT_TOO_LONG)
        val all = listOfNotNull(draft.text, draft.place, draft.factDate, draft.factTime)
        if (all.any { value -> value.any { (it.code < 0x20 && it != '\n' && it != '\r' && it != '\t') || it.code == 0x7F } }) add(TestimonyProblem.CONTROL_CHARACTERS)
        draft.factDate?.trim()?.takeIf { it.isNotEmpty() }?.let { date ->
            if (!isValidDate(date)) add(TestimonyProblem.BAD_DATE) else if (today != null && date > today) add(TestimonyProblem.FUTURE_DATE)
        }
        draft.factTime?.trim()?.takeIf { it.isNotEmpty() }?.let { time ->
            if (!TIME.matches(time) || time.substring(0, 2).toInt() > 23 || time.substring(3, 5).toInt() > 59) add(TestimonyProblem.BAD_TIME)
        }
        if ((draft.place?.trim()?.length ?: 0) > TestimonyFormats.MAX_PLACE_LENGTH) add(TestimonyProblem.PLACE_TOO_LONG)
        draft.callAddress?.let { address ->
            val parsed = runCatching { ContentAddress.parse(address) }.getOrNull()
            if (parsed == null || parsed.kind != 30023) add(TestimonyProblem.BAD_CALL)
        }
        if (!draft.charterAccepted) add(TestimonyProblem.CHARTER_NOT_ACCEPTED)
    }

    fun isValidDate(value: String): Boolean {
        if (!DATE.matches(value)) return false
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        if (year < 1900 || month !in 1..12) return false
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[month - 1]
        return day in 1..days
    }
}

/**
 * Everything that leaves the device for one testimony: the envelope for the moderation team, the
 * one for the evidence key (only if the witness gave the original), the encrypted files, and the
 * SHA-256 digests to timestamp. Only digests and ciphertext are readable by the server.
 */
class TestimonyPackage(
    /** The envelope for the moderation team; its id is the number of the testimony, known to the witness only. */
    val envelope: SignedEvent,
    val evidenceEnvelope: SignedEvent?,
    val files: List<EncryptedFile>,
    val evidenceFiles: List<EncryptedFile>,
    /** Digests to timestamp: the provenance statement of each photo (which names the original's SHA-256) and the published copy. */
    val stamps: List<String>,
    val callAddress: String?,
    val hasPhoto: Boolean,
    val gaveOriginal: Boolean,
) {
    val id: String get() = envelope.id

    /** The request and the files, as the transport sends them and the device keeps them until then. */
    fun outgoing(): OutgoingTestimony = OutgoingTestimony(
        TestimonySubmission(
            envelope = envelope,
            evidenceEnvelope = evidenceEnvelope,
            files = files.map { it.id },
            evidenceFiles = evidenceFiles.map { it.id },
            stamps = stamps,
        ),
        (files + evidenceFiles).associate { it.id to it.bytes },
    )
}

/**
 * Builds a [TestimonyPackage] on the device. Requires an account: the testimony is sealed by it
 * (NIP-59), so the team knows which pseudonym testified (to answer, to refuse a flood) but the
 * server does not, and nobody can impersonate a witness.
 */
object TestimonyComposer {
    suspend fun build(
        draft: TestimonyDraft,
        account: MessagingSigner,
        recipients: TestimonyRecipients,
        now: Long = currentEpochSeconds(),
    ): TestimonyPackage {
        val photo = draft.photo
        val published = photo?.let { MediaCipher.encrypt(it.published) }
        val original = photo?.takeIf { draft.giveOriginal }?.let { MediaCipher.encrypt(it.original) }
        try {
            val tags = buildList {
                add(listOf(TestimonyFormats.TAG_CHARTER, TestimonyFormats.CHARTER_VERSION))
                draft.factDate?.trim()?.takeIf { it.isNotEmpty() }?.let { add(listOf(TestimonyFormats.TAG_FACT_DATE, it)) }
                draft.factTime?.trim()?.takeIf { it.isNotEmpty() }?.let { add(listOf(TestimonyFormats.TAG_FACT_TIME, it)) }
                draft.place?.trim()?.takeIf { it.isNotEmpty() }?.let { add(listOf(TestimonyFormats.TAG_PLACE, it)) }
                draft.callAddress?.let { add(listOf(TestimonyFormats.TAG_CALL, it)) }
                if (photo != null && published != null) {
                    add(mediaTag(published, photo.publishedFormat.mimeType, photo.publishedSha256, photo.published.size, photo) + listOfNotNull(
                        TestimonyFormats.field(TestimonyFormats.MEDIA_ORIGINAL_SHA256, photo.originalSha256),
                        photo.width?.let { w -> photo.height?.let { h -> TestimonyFormats.field(TestimonyFormats.MEDIA_DIMENSIONS, "${w}x$h") } },
                        TestimonyFormats.field(TestimonyFormats.MEDIA_BLURRED, if (photo.blurred) "1" else "0"),
                        TestimonyFormats.field(TestimonyFormats.MEDIA_REENCODED, if (photo.reencoded) "1" else "0"),
                    ))
                    add(listOf(TestimonyFormats.TAG_PROVENANCE, photo.provenance.toJson()))
                    add(listOf(TestimonyFormats.TAG_ORIGINAL, if (original != null) TestimonyFormats.ORIGINAL_GIVEN else TestimonyFormats.ORIGINAL_KEPT))
                }
            }
            val rumor = Rumor.of(UnsignedEvent(account.publicKey.hex, now, TestimonyFormats.KIND_TESTIMONY, tags, draft.text.trim()))
            val envelope = GiftWrap.wrapFor(rumor, account, recipients.moderation, now)
            val evidence = if (photo != null && original != null) {
                val evidenceTags = listOf(
                    listOf(TestimonyFormats.TAG_TESTIMONY, rumor.id),
                    mediaTag(original, mimeOf(photo), photo.originalSha256, photo.original.size, photo),
                    listOf(TestimonyFormats.TAG_PROVENANCE, photo.provenance.toJson()),
                )
                GiftWrap.wrapFor(Rumor.of(UnsignedEvent(account.publicKey.hex, now, TestimonyFormats.KIND_EVIDENCE, evidenceTags, "")), account, recipients.evidence, now)
            } else {
                null
            }
            val stamps = if (photo != null) listOf(photo.provenance.id, photo.publishedSha256) else emptyList()
            return TestimonyPackage(
                envelope = envelope,
                evidenceEnvelope = evidence,
                files = listOfNotNull(published),
                evidenceFiles = listOfNotNull(original),
                stamps = stamps,
                callAddress = draft.callAddress,
                hasPhoto = photo != null,
                gaveOriginal = original != null,
            )
        } finally {
            // The keys of the files now live inside the envelopes only.
            published?.key?.fill(0)
            original?.key?.fill(0)
        }
    }

    private fun mediaTag(file: EncryptedFile, mime: String, sha256: String, size: Int, photo: PreparedPhoto): List<String> = listOf(
        TestimonyFormats.TAG_MEDIA,
        TestimonyFormats.field(TestimonyFormats.MEDIA_FILE, file.id),
        TestimonyFormats.field(TestimonyFormats.MEDIA_MIME, mime),
        TestimonyFormats.field(TestimonyFormats.MEDIA_SHA256, sha256),
        TestimonyFormats.field(TestimonyFormats.MEDIA_SIZE, size.toString()),
        TestimonyFormats.field(TestimonyFormats.MEDIA_KEY, file.key.toHexString()),
        TestimonyFormats.field(TestimonyFormats.MEDIA_ALGORITHM, MediaCipher.ALGORITHM),
    )

    private fun mimeOf(photo: PreparedPhoto): String = when (val kind = photo.originalKind) {
        is org.politisationpopulairefrancaise.core.media.MediaKind.Image -> kind.format.mimeType
        org.politisationpopulairefrancaise.core.media.MediaKind.Heif -> "image/heic"
        else -> "application/octet-stream"
    }
}
