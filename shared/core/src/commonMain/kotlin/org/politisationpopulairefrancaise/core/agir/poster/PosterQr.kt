package org.politisationpopulairefrancaise.core.agir.poster

import org.politisationpopulairefrancaise.core.agir.map.Gzip
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** What the QR code of a poster carries (AG-61). */
sealed interface PosterPayload {
    /** The exact text written in the code. */
    val text: String

    /** The signed event itself, compressed: read and checked without network ([PosterQr.PREFIX]). */
    data class Event(override val text: String) : PosterPayload

    /** The stable address of the content ([AgirLinks]), when the event is too long for a readable code. */
    data class Address(override val text: String) : PosterPayload
}

/** What a code read on a poster turned out to be. */
sealed interface PosterScan {
    /** A signed event whose identifier and signature are right: it is exactly what its author signed. */
    data class Event(val event: SignedEvent) : PosterScan

    /** A code of PPF whose event does not match its signature: changed after signing, or made up. Refused. */
    data object Forged : PosterScan

    /** A code with the prefix of PPF that cannot be read (damaged, cut, or unreadable on this device). */
    data object Damaged : PosterScan

    /** The stable address of a content of "Agir" on a domain of PPF. */
    data class Address(val address: ContentAddress, val url: String) : PosterScan

    /** Anything else: another application's code, a link elsewhere, a text. Nothing is opened. */
    data object NotPpf : PosterScan
}

/**
 * The QR code of a poster (AG-61; format in docs/FORMATS.md, section 4 sexdecies): the signed event
 * itself whenever it fits a code still readable on an A4 sheet, so that a phone adds it to its
 * agenda without network after checking the signature; otherwise the stable address of the content,
 * without any parameter. Never anything about the person who made or printed the poster.
 *
 * Event form: [PREFIX] then Base45 (RFC 9285) of the gzip (RFC 1952) of the exact JSON of the signed
 * event, UTF-8, as [SignedEvent.toJson] writes it. Every character of it belongs to the alphanumeric
 * mode of QR codes (5.5 bits per character), which is why the prefix is in capitals.
 *
 * Size: at most [MAX_CHARACTERS] characters, the capacity of a version 25 code with error correction
 * level M (the level of the common component, `encodeQr`) in alphanumeric mode (ISO/IEC 18004,
 * table 7): 117 modules a side, at least 0.7 mm each on the code of an A4 poster, larger on A3.
 */
object PosterQr {
    /** The marker and version of the event form; a later version will take another number. */
    const val PREFIX: String = "PPF:EVENT:1:"

    /** Version 25, level M, alphanumeric mode: 1 451 characters. */
    const val MAX_CHARACTERS: Int = 1_451

    /** The largest code [MAX_CHARACTERS] gives: 17 + 4 x 25 modules a side, without the quiet zone. */
    const val MAX_MODULES: Int = 117

    /** A signed content is 64 KB at most (API of the server): a longer inflated text is refused. */
    const val MAX_JSON_BYTES: Int = 64 * 1024

    /**
     * The text of the code carrying [event], or null when it does not fit [MAX_CHARACTERS] (or the
     * platform cannot compress: iOS until its application is built).
     */
    fun encodeEvent(event: SignedEvent): String? {
        val compressed = Gzip.compress(event.toJson().encodeToByteArray()) ?: return null
        val text = PREFIX + Base45.encode(compressed)
        return text.takeIf { it.length <= MAX_CHARACTERS }
    }

    /** The event itself when it fits, otherwise its stable address [url] (built by [AgirLinks]). */
    fun payloadFor(event: SignedEvent, url: String): PosterPayload =
        encodeEvent(event)?.let { PosterPayload.Event(it) } ?: PosterPayload.Address(url)

    /** Reads the text of a code; never throws. */
    fun read(text: String): PosterScan {
        if (text.startsWith(PREFIX)) return readEvent(text.substring(PREFIX.length))
        AgirLinks.parse(text)?.let { return PosterScan.Address(it, text) }
        return PosterScan.NotPpf
    }

    private fun readEvent(body: String): PosterScan {
        if (body.isEmpty() || body.length > MAX_CHARACTERS) return PosterScan.Damaged
        val compressed = Base45.decode(body) ?: return PosterScan.Damaged
        val json = Gzip.decompress(compressed)?.takeIf { it.size <= MAX_JSON_BYTES } ?: return PosterScan.Damaged
        val event = runCatching { SignedEvent.fromJson(json.decodeToString(throwOnInvalidSequence = true)) }.getOrNull() ?: return PosterScan.Damaged
        return if (event.verify()) PosterScan.Event(event) else PosterScan.Forged
    }
}
