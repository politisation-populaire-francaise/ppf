package org.politisationpopulairefrancaise.core.agir.testimonies

import org.politisationpopulairefrancaise.core.media.MediaCipher
import org.politisationpopulairefrancaise.core.media.MediaCipherException
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.signing.SignedEvent

class TestimonyFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** One encrypted file named in an envelope, with what is needed to decrypt and check it. */
class TestimonyMedia(
    /** The SHA-256 of the ciphertext, as the server names the file. */
    val fileId: String,
    val mimeType: String,
    /** SHA-256 of the decrypted file. */
    val sha256: String,
    val originalSha256: String?,
    val size: Long,
    val width: Int?,
    val height: Int?,
    val blurred: Boolean,
    val reencoded: Boolean,
    internal val key: ByteArray,
)

/** A testimony as the moderation team reads it, once its envelope is opened with the team's key. */
class OpenedTestimony(
    /** The sealed rumor; its author is the witness's account (checked by [GiftWrap.unwrap]). */
    val rumor: Rumor,
    val media: List<TestimonyMedia>,
    val provenance: SignedEvent?,
    val originalGiven: Boolean,
) {
    val author: String get() = rumor.pubkey
    val text: String get() = rumor.content
    val factDate: String? get() = rumor.firstTagValue(TestimonyFormats.TAG_FACT_DATE)
    val factTime: String? get() = rumor.firstTagValue(TestimonyFormats.TAG_FACT_TIME)
    val place: String? get() = rumor.firstTagValue(TestimonyFormats.TAG_PLACE)
    val callAddress: String? get() = rumor.firstTagValue(TestimonyFormats.TAG_CALL)
}

/** The original kept for the lawyers, once its envelope is opened with the evidence key. */
class OpenedEvidence(val rumor: Rumor, val testimonyId: String?, val media: List<TestimonyMedia>, val provenance: SignedEvent?)

/**
 * Opens the envelopes of testimonies (the team's side: development server, tests, and the
 * back-office to come, M15). Every check fails loudly: an envelope that does not open, a rumor of
 * another kind, a file whose content does not match its SHA-256.
 */
object TestimonyReader {
    fun open(envelope: SignedEvent, moderation: MessagingSigner): OpenedTestimony {
        val rumor = GiftWrap.unwrap(envelope, moderation)
        if (rumor.kind != TestimonyFormats.KIND_TESTIMONY) throw TestimonyFormatException("Not a testimony (kind ${rumor.kind})")
        return OpenedTestimony(
            rumor = rumor,
            media = mediaOf(rumor),
            provenance = provenanceOf(rumor),
            originalGiven = rumor.firstTagValue(TestimonyFormats.TAG_ORIGINAL) == TestimonyFormats.ORIGINAL_GIVEN,
        )
    }

    fun openEvidence(envelope: SignedEvent, evidence: MessagingSigner): OpenedEvidence {
        val rumor = GiftWrap.unwrap(envelope, evidence)
        if (rumor.kind != TestimonyFormats.KIND_EVIDENCE) throw TestimonyFormatException("Not an evidence envelope (kind ${rumor.kind})")
        return OpenedEvidence(rumor, rumor.firstTagValue(TestimonyFormats.TAG_TESTIMONY), mediaOf(rumor), provenanceOf(rumor))
    }

    /** Decrypts a file named in an envelope and checks it is exactly the file the witness sent. */
    fun decrypt(file: ByteArray, media: TestimonyMedia): ByteArray {
        if (MediaDigest.sha256Hex(file) != media.fileId) throw TestimonyFormatException("The encrypted file is not the one named in the envelope")
        val plain = try {
            MediaCipher.decrypt(file, media.key)
        } catch (e: MediaCipherException) {
            throw TestimonyFormatException("The file does not decrypt", e)
        }
        if (MediaDigest.sha256Hex(plain) != media.sha256) throw TestimonyFormatException("The decrypted file does not match its SHA-256")
        return plain
    }

    private fun provenanceOf(rumor: Rumor): SignedEvent? = rumor.firstTagValue(TestimonyFormats.TAG_PROVENANCE)?.let { json ->
        runCatching { SignedEvent.fromJson(json) }.getOrNull()?.takeIf { it.verify() }
    }

    private fun mediaOf(rumor: Rumor): List<TestimonyMedia> = rumor.tags.filter { it.firstOrNull() == TestimonyFormats.TAG_MEDIA }.map { tag ->
        val fields = TestimonyFormats.fields(tag)
        fun required(name: String): String = fields[name] ?: throw TestimonyFormatException("Missing $name in a media tag")
        val fileId = required(TestimonyFormats.MEDIA_FILE)
        val sha256 = required(TestimonyFormats.MEDIA_SHA256)
        if (!MediaDigest.isHex(fileId) || !MediaDigest.isHex(sha256)) throw TestimonyFormatException("Malformed digest in a media tag")
        if (required(TestimonyFormats.MEDIA_ALGORITHM) != MediaCipher.ALGORITHM) throw TestimonyFormatException("Unknown encryption")
        val key = runCatching { required(TestimonyFormats.MEDIA_KEY).hexToByteArray() }.getOrNull()?.takeIf { it.size == MediaCipher.KEY_SIZE }
            ?: throw TestimonyFormatException("Malformed key in a media tag")
        val dimensions = fields[TestimonyFormats.MEDIA_DIMENSIONS]?.split('x')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
        TestimonyMedia(
            fileId = fileId,
            mimeType = required(TestimonyFormats.MEDIA_MIME),
            sha256 = sha256,
            originalSha256 = fields[TestimonyFormats.MEDIA_ORIGINAL_SHA256],
            size = fields[TestimonyFormats.MEDIA_SIZE]?.toLongOrNull() ?: -1,
            width = dimensions?.get(0),
            height = dimensions?.get(1),
            blurred = fields[TestimonyFormats.MEDIA_BLURRED] == "1",
            reencoded = fields[TestimonyFormats.MEDIA_REENCODED] == "1",
            key = key,
        )
    }
}
