package org.politisationpopulairefrancaise.core.media

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** How the device got the file: a photo taken in the application, or a file chosen by the user. */
enum class CaptureSource(val tagValue: String) {
    CAMERA("camera"),
    IMPORT("import"),
}

/**
 * The provenance of a file (AG-54, cahier des charges 5.12): an event signed on the device as soon
 * as the file is taken or chosen, before any change, that states the SHA-256 of the untouched
 * original, its type, its size and when the device got it. Its id (a SHA-256 of all this) is what
 * gets timestamped ([Timestamping]): the timestamp then proves that this exact original existed at
 * that moment, and the signature that the statement was not changed since.
 *
 * Who signs: by default a key made for this one file and destroyed right after ([signWithOneTimeKey]),
 * so that the statement, if it is ever shown (to the lawyers, in a procedure), does not name the
 * witness's pseudonym (cahier des charges 5.12: the key "est choisie pour ne pas relier le témoin à
 * son pseudonyme"). The envelope sent to the team, signed by the account, still ties both together
 * for the team alone. [sign] accepts any signer for a witness who wants to sign with the account.
 *
 * Format (kind [KIND], never published as is; it travels inside the encrypted envelope):
 * `["x", sha256 of the original]`, `["m", media type]`, `["size", bytes]`,
 * `["ppf-capture", "camera" | "import"]`, `["ppf-captured-at", seconds]`, `content` empty.
 */
object Provenance {
    /** A private kind: inside encrypted envelopes only (no NIP uses it as of November 2026). */
    const val KIND: Int = 8591
    const val TAG_ORIGINAL: String = "x"
    const val TAG_MIME: String = "m"
    const val TAG_SIZE: String = "size"
    const val TAG_CAPTURE: String = "ppf-capture"
    const val TAG_CAPTURED_AT: String = "ppf-captured-at"

    suspend fun sign(signer: EventSigner, original: ByteArray, mimeType: String, source: CaptureSource, capturedAt: Long = currentEpochSeconds()): SignedEvent =
        signer.sign(unsigned(signer.publicKey.hex, MediaDigest.sha256Hex(original), mimeType, original.size.toLong(), source, capturedAt))

    /** Signs with a brand new key, destroyed right after: the statement names no account. */
    suspend fun signWithOneTimeKey(original: ByteArray, mimeType: String, source: CaptureSource, capturedAt: Long = currentEpochSeconds()): SignedEvent {
        val keys = IdentityKeys.generate()
        try {
            return sign(LocalEventSigner(keys), original, mimeType, source, capturedAt)
        } finally {
            keys.destroy()
        }
    }

    private fun unsigned(pubkey: String, digest: String, mimeType: String, size: Long, source: CaptureSource, capturedAt: Long) = UnsignedEvent(
        pubkey = pubkey,
        createdAt = capturedAt,
        kind = KIND,
        tags = listOf(
            listOf(TAG_ORIGINAL, digest),
            listOf(TAG_MIME, mimeType),
            listOf(TAG_SIZE, size.toString()),
            listOf(TAG_CAPTURE, source.tagValue),
            listOf(TAG_CAPTURED_AT, capturedAt.toString()),
        ),
    )

    /** True when [event] is a valid provenance statement of exactly [original]. */
    fun matches(event: SignedEvent, original: ByteArray): Boolean =
        event.kind == KIND && event.verify() &&
            event.firstTagValue(TAG_ORIGINAL) == MediaDigest.sha256Hex(original) &&
            event.firstTagValue(TAG_SIZE) == original.size.toString()
}

/** What became of a request to timestamp a digest. */
enum class StampOutcome {
    /** Queued for the next batch of the timestamped archive (D-111). */
    QUEUED,

    /** Already known to the archive. */
    ALREADY_KNOWN,

    /** No archive here (yet): nothing was stamped; the caller keeps going. */
    UNAVAILABLE,
}

/**
 * The timestamped archive seen by the chain of media: only a SHA-256 leaves, never a file (D-111).
 * The archive itself belongs to the "Décider" session (OpenTimestamps and RFC 3161 on the server,
 * `ArchiveService.stamp(digest)` on their branch); until it reaches main, [NoTimestamping] stands
 * in, on the device as on the server, and the testimony is sent anyway.
 */
fun interface Timestamping {
    suspend fun stamp(digest: ByteArray): StampOutcome
}

/** Stamps nothing: to be replaced by the archive of "Décider" once it is in main. */
object NoTimestamping : Timestamping {
    override suspend fun stamp(digest: ByteArray): StampOutcome = StampOutcome.UNAVAILABLE
}
