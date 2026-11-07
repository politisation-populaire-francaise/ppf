package org.politisationpopulairefrancaise.core.agir.testimonies

import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.media.MediaSniffer
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The public version of a testimony, written by the team after its review (VO-08 shows them in
 * "Le mouvement"): a content of kind 30023, `ppf-type` = `temoignage`, signed by an editorial key,
 * so that it is a PPF content like the others (signature, stable address, export). It names neither
 * the witness nor the envelope: its `d` is random. Its photos are those the device cleaned, checked
 * again by the team (and blurred further if needed, from the decrypted copy), served by the server at
 * [TestimonyFormats.MEDIA_PATH] + SHA-256 and described by `imeta` tags (NIP-92).
 *
 * Tags: `d`, `title`, `ppf-type`, `published_at`, `ppf-origin` (`human`), and when known
 * `ppf-fact-date`, `ppf-fact-time`, `location`, `a` (the call for witnesses it answers, so that the
 * call can link to it), one `imeta` per photo (`url`, `m`, `x`, `dim`). `content`: the text as the
 * team publishes it.
 */
object PublishedTestimony {
    /** A photo the team publishes: the bytes (already clean) and, when known, their size in pixels. */
    class Photo(val bytes: ByteArray, val width: Int? = null, val height: Int? = null)

    fun unsigned(
        editor: PublicKey,
        title: String,
        text: String,
        factDate: String? = null,
        factTime: String? = null,
        place: String? = null,
        callAddress: String? = null,
        photos: List<Photo> = emptyList(),
        now: Long = currentEpochSeconds(),
    ): UnsignedEvent {
        val tags = buildList {
            add(listOf("d", "temoignage-" + secureRandomBytes(8).toHexString()))
            add(listOf("title", title))
            add(listOf(Article.TAG_TYPE, TestimonyFormats.PUBLISHED_TYPE))
            add(listOf("published_at", now.toString()))
            add(listOf("ppf-origin", "human"))
            factDate?.let { add(listOf(TestimonyFormats.TAG_FACT_DATE, it)) }
            factTime?.let { add(listOf(TestimonyFormats.TAG_FACT_TIME, it)) }
            place?.let { add(listOf(TestimonyFormats.TAG_PLACE, it)) }
            callAddress?.let { add(listOf(TestimonyFormats.TAG_CALL, it)) }
            for (photo in photos) {
                val sha = MediaDigest.sha256Hex(photo.bytes)
                val mime = MediaSniffer.imageFormatOf(photo.bytes)?.mimeType ?: error("A published photo is a JPEG, PNG or WebP image")
                add(
                    listOfNotNull(
                        TestimonyFormats.TAG_MEDIA,
                        TestimonyFormats.field("url", TestimonyFormats.MEDIA_PATH + sha),
                        TestimonyFormats.field(TestimonyFormats.MEDIA_MIME, mime),
                        TestimonyFormats.field(TestimonyFormats.MEDIA_SHA256, sha),
                        photo.width?.let { w -> photo.height?.let { h -> TestimonyFormats.field(TestimonyFormats.MEDIA_DIMENSIONS, "${w}x$h") } },
                    ),
                )
            }
        }
        return UnsignedEvent(editor.hex, now, Article.KIND, tags, text)
    }
}
