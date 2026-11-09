package org.politisationpopulairefrancaise.core.agir.harassment

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationDraft
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.media.ImageFormat
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.media.MediaKind
import org.politisationpopulairefrancaise.core.media.MediaSniffer
import org.politisationpopulairefrancaise.core.media.MetadataStripper
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.media.StripResult

/*
 * AG-59 « Kit contre le harcèlement et la divulgation d'informations personnelles », step 1: the
 * evidence a person keeps on her own device, encrypted in the vault of the protected camera's
 * format (core/agir/camera), never sent anywhere by PPF. Conception: docs/integration/agir-harcelement.md.
 */

/** The picture of a piece of evidence (a screenshot, a photo), as the vault keeps it: never its pixels here. */
@Serializable
data class EvidenceImage(
    /** SHA-256 of the file as it was chosen (or as the protected camera handed it over); that file is never written by PPF. */
    val originalSha256: String,
    /** SHA-256 of the copy kept in the vault: metadata removed, pixels untouched. */
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
    /** Where it came from: [EvidenceFormat.SOURCE_PICKER] or [EvidenceFormat.SOURCE_CAMERA]. */
    val source: String,
)

/**
 * What a piece of evidence says, exactly as it was kept: its SHA-256 ([Evidence.digest]) is taken
 * over the canonical form of this document ([EvidenceFormat.canonical]), so that one digest covers
 * the address, the text, the dates, the platform, the note and the digest of the picture.
 */
@Serializable
data class EvidenceContent(
    val version: Int = EvidenceFormat.VERSION,
    /** When the person saw the content, as she typed it (seconds; read in Paris time). */
    val seenAt: Long,
    /** When PPF kept it, by the clock of this device: nobody else certifies it (no timestamp proof in step 1). */
    val keptAt: Long,
    /** The platform or the site, in the person's words ("Instagram", "un forum"). */
    val platform: String = "",
    /** The exact address of the content, if there is one (a private message often has none). */
    val url: String = "",
    /** The text copied from the content, word for word. */
    val text: String = "",
    /** A short note of the person, for herself. */
    val note: String = "",
    val image: EvidenceImage? = null,
)

/** One piece of evidence kept on this device. [id] is the vault's identifier of its record (32 random hexadecimal characters). */
data class Evidence(
    val id: String,
    val content: EvidenceContent,
    /** SHA-256 of [EvidenceFormat.canonical] of [content]: the digest of what was kept. */
    val digest: String,
    /** The vault's identifier of the picture, if any. */
    val imageEntry: String?,
) {
    override fun toString(): String = "Evidence($id)"
}

/**
 * The record written in the vault for each piece of evidence: the canonical text itself (so that
 * the digest stays checkable whatever later versions of the serializer do), its digest and the
 * identifier of the picture.
 */
@Serializable
internal data class EvidenceRecord(
    val version: Int,
    val canonical: String,
    val digest: String,
    val imageEntry: String? = null,
)

/** The format of the evidence (version 1). */
object EvidenceFormat {
    const val VERSION: Int = 1

    /** The type of a record in the vault's listing (a picture keeps its own image type). */
    const val RECORD_MIME: String = "application/vnd.ppf.evidence+json"

    const val SOURCE_PICKER: String = "picker"
    const val SOURCE_CAMERA: String = "camera"

    const val MAX_PLATFORM: Int = 80
    const val MAX_URL: Int = 2048
    const val MAX_TEXT: Int = 10_000
    const val MAX_NOTE: Int = 1_000

    /** A minute of tolerance between the clock of the device and a moment typed "now". */
    const val FUTURE_TOLERANCE_SECONDS: Long = 60

    /** Fixed order of the fields, defaults and nulls written, compact: the same content always gives the same bytes. */
    private val canonicalJson = Json { encodeDefaults = true }
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The canonical text of [content]: compact JSON, fields in their declared order. */
    fun canonical(content: EvidenceContent): String = canonicalJson.encodeToString(EvidenceContent.serializer(), content)

    /** The SHA-256 of [canonical], in lowercase hexadecimal. */
    fun digestOf(canonical: String): String = MediaDigest.sha256Hex(canonical.encodeToByteArray())

    fun digestOf(content: EvidenceContent): String = digestOf(canonical(content))

    /** Reads a canonical text back; null when it is not one. */
    fun contentOf(canonical: String): EvidenceContent? =
        runCatching { json.decodeFromString(EvidenceContent.serializer(), canonical) }.getOrNull()

    /** An address the person can paste: http or https, no space, a host. */
    fun isUrl(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty() || value.length > MAX_URL || value.any { it.isWhitespace() }) return false
        val lower = value.lowercase()
        val rest = when {
            lower.startsWith("https://") -> value.substring(8)
            lower.startsWith("http://") -> value.substring(7)
            else -> return false
        }
        val host = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        return host.isNotEmpty() && !host.startsWith('.') && !host.startsWith('@')
    }

    /** "9 novembre 2026" and "14:30", Paris time, for the screen and the templates. */
    fun dateAndTime(epochSeconds: Long): Pair<String, String> {
        val moment = ParisTime.moment(epochSeconds)
        return moment.date.toFrench() to (two(moment.hour) + ":" + two(moment.minute))
    }

    /** "09/11/2026" and "14:30", the forms the fields accept, to fill them with the present moment. */
    fun typedDateAndTime(epochSeconds: Long): Pair<String, String> {
        val moment = ParisTime.moment(epochSeconds)
        val date = moment.date
        return (two(date.day) + "/" + two(date.month) + "/" + date.year) to (two(moment.hour) + ":" + two(moment.minute))
    }

    private fun two(value: Int): String = value.toString().padStart(2, '0')
}

/** Why a piece of evidence cannot be kept yet; said next to the field concerned. */
enum class EvidenceProblem {
    /** Neither a picture, nor an address, nor a text: nothing to keep. */
    NOTHING_TO_KEEP,
    URL_INVALID,
    DATE_INVALID,
    TIME_INVALID,
    /** The moment typed is later than now (a date without its year is read as this year or the next). */
    IN_FUTURE,
    PLATFORM_TOO_LONG,
    TEXT_TOO_LONG,
    NOTE_TOO_LONG,
}

/** The form of a piece of evidence, in memory only, as the person types it. */
data class EvidenceDraft(
    val platform: String = "",
    val url: String = "",
    val text: String = "",
    val note: String = "",
    /** "09/11/2026", "9/11/26", "2026-11-09" (MobilizationDraft.parseDate). */
    val seenDate: String = "",
    /** "14:30", "14h30" (MobilizationDraft.parseTime). */
    val seenTime: String = "",
) {
    /** The moment typed, or null when the date or the time cannot be read. */
    fun seenAt(now: Long): Long? {
        val today = ParisTime.moment(now).date
        val date = MobilizationDraft.parseDate(seenDate, today) ?: return null
        val (hour, minute) = MobilizationDraft.parseTime(seenTime) ?: return null
        return ParisTime.toEpochSeconds(date, hour, minute)
    }

    /** What prevents keeping it; empty when it can be kept. */
    fun problems(hasImage: Boolean, now: Long): Set<EvidenceProblem> {
        val problems = linkedSetOf<EvidenceProblem>()
        if (!hasImage && url.isBlank() && text.isBlank()) problems += EvidenceProblem.NOTHING_TO_KEEP
        if (url.isNotBlank() && !EvidenceFormat.isUrl(url)) problems += EvidenceProblem.URL_INVALID
        val today = ParisTime.moment(now).date
        val date = MobilizationDraft.parseDate(seenDate, today)
        if (date == null) problems += EvidenceProblem.DATE_INVALID
        if (MobilizationDraft.parseTime(seenTime) == null) problems += EvidenceProblem.TIME_INVALID
        val seen = seenAt(now)
        if (seen != null && seen > now + EvidenceFormat.FUTURE_TOLERANCE_SECONDS) problems += EvidenceProblem.IN_FUTURE
        if (platform.trim().length > EvidenceFormat.MAX_PLATFORM) problems += EvidenceProblem.PLATFORM_TOO_LONG
        if (text.trim().length > EvidenceFormat.MAX_TEXT) problems += EvidenceProblem.TEXT_TOO_LONG
        if (note.trim().length > EvidenceFormat.MAX_NOTE) problems += EvidenceProblem.NOTE_TOO_LONG
        return problems
    }
}

/** Why a picture is not attached. */
enum class EvidenceImageRefusal {
    /** Larger than [PhotoPipeline.MAX_ORIGINAL_BYTES]. */
    TOO_LARGE,

    /** Not a JPEG, PNG or WebP picture (a HEIF photo, a video, a document): nothing is kept. */
    UNSUPPORTED,

    /** A picture whose structure is broken. */
    MALFORMED,

    /** The cleaned copy still carries metadata (never expected): nothing is kept. */
    NOT_CLEAN,
}

/** A picture ready for the vault: [bytes] carry no metadata but the orientation. */
class PreparedEvidenceImage(
    val bytes: ByteArray,
    val format: ImageFormat,
    val originalSha256: String,
    val source: String,
) {
    override fun toString(): String = "PreparedEvidenceImage(${bytes.size} bytes)"
}

sealed interface EvidenceImageResult {
    class Ready(val image: PreparedEvidenceImage) : EvidenceImageResult
    data class Refused(val reason: EvidenceImageRefusal) : EvidenceImageResult
}

/**
 * The chain of a picture before the vault, with core/media and nothing duplicated: the SHA-256 of
 * the file as it came, then its metadata removed without decoding (EXIF with the position, the
 * device and the date, XMP, comments, thumbnails; the pixels stay exactly those of the file), the
 * orientation alone kept so that it shows upright, then checked clean.
 */
object EvidenceImages {
    fun prepare(original: ByteArray, source: String): EvidenceImageResult {
        if (original.size > PhotoPipeline.MAX_ORIGINAL_BYTES) return EvidenceImageResult.Refused(EvidenceImageRefusal.TOO_LARGE)
        if (MediaSniffer.kindOf(original) !is MediaKind.Image) return EvidenceImageResult.Refused(EvidenceImageRefusal.UNSUPPORTED)
        val originalSha256 = MediaDigest.sha256Hex(original)
        val clean = when (val strip = MetadataStripper.strip(original, keepOrientation = true)) {
            is StripResult.Clean -> strip.image
            is StripResult.Unsupported -> return EvidenceImageResult.Refused(EvidenceImageRefusal.UNSUPPORTED)
            is StripResult.Malformed -> return EvidenceImageResult.Refused(EvidenceImageRefusal.MALFORMED)
        }
        if (!MetadataStripper.isClean(clean.bytes)) return EvidenceImageResult.Refused(EvidenceImageRefusal.NOT_CLEAN)
        return EvidenceImageResult.Ready(PreparedEvidenceImage(clean.bytes, clean.format, originalSha256, source))
    }
}
