package org.politisationpopulairefrancaise.core.agir.poster

import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.voir.VoirLinks

/**
 * The paper sizes of a poster (ISO 216, portrait), in PostScript points (1/72 inch) as the PDF of
 * Android counts them. An A3 is an enlarged A4: the whole layout grows by [scale].
 */
enum class PosterFormat(val widthPt: Int, val heightPt: Int, val widthMm: Int, val heightMm: Int) {
    A4(595, 842, 210, 297),
    A3(842, 1191, 297, 420),
    ;

    val scale: Float get() = widthPt / A4.widthPt.toFloat()
}

/** What a poster announces (step 1: a mobilization of the agenda or a call; a fiche or a demand later). */
enum class PosterKind { MOBILIZATION, CALL }

/**
 * The common model of a poster (AG-61), whatever it announces: what the poster shows and what its
 * code carries, built on the device from a signed content. Nothing here comes from the person who
 * makes the poster (except the optional printer's mention, added by the screen), and nothing is
 * kept: the poster exists only in the file the person chooses to save.
 *
 * [hosts] are the keys shown as organisers, in their order (the screen names them as the
 * application does). [text] is the short text of the poster: the summary, or else the beginning
 * of the description; the code carries the whole event.
 */
data class PosterContent(
    val kind: PosterKind,
    val address: ContentAddress,
    /** The title without the [EXEMPLE] marker, which [example] says is there. */
    val title: String,
    val example: Boolean,
    val mobilizationType: MobilizationType?,
    val callType: CallType?,
    /** Seconds since 1970. */
    val start: Long?,
    val end: Long?,
    val deadline: Long?,
    /** The place at the level the author gave, with the territory when the place does not name it. */
    val place: String?,
    val territory: String,
    val hosts: List<String>,
    val text: String?,
    val payload: PosterPayload,
) {
    /** The beginning of the name of the saved file: `affiche-<identifier>` (lowercase ASCII, no personal data). */
    val fileBase: String
        get() {
            val identifier = address.identifier.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
                .split('-').filter { it.isNotEmpty() }.joinToString("-").take(MAX_FILE_IDENTIFIER).trimEnd('-')
            return if (identifier.isEmpty()) FILE_PREFIX.trimEnd('-') else FILE_PREFIX + identifier
        }

    /** The full file name without its extension, for [format]: `affiche-<identifier>-a4`. */
    fun fileName(format: PosterFormat): String = fileBase + "-" + format.name.lowercase()

    companion object {
        /** The short text of a poster is at most this many characters before the layout fits it to the page. */
        const val MAX_TEXT: Int = 420

        const val FILE_PREFIX: String = "affiche-"
        private const val MAX_FILE_IDENTIFIER: Int = 48
        private const val ELLIPSIS: String = "…"

        /**
         * The poster of [mobilization]. [hosts] are the organisers as the application shows them
         * (the author, then the organisations that confirmed with their own key, D-044); the author
         * alone by default.
         */
        fun of(mobilization: Mobilization, hosts: List<String> = listOf(mobilization.author), site: String = VoirLinks.SITE): PosterContent =
            PosterContent(
                kind = PosterKind.MOBILIZATION,
                address = mobilization.address,
                title = mobilization.displayTitle,
                example = mobilization.example,
                mobilizationType = mobilization.type,
                callType = null,
                start = mobilization.start,
                end = mobilization.end,
                deadline = null,
                place = placeOf(mobilization.location, mobilization.territory),
                territory = mobilization.territory,
                hosts = hosts.distinct(),
                text = shortText(mobilization.summary, mobilization.description),
                payload = PosterQr.payloadFor(mobilization.event, AgirLinks.urlOf(mobilization, site)),
            )

        /** The poster of [call]: its author as organiser, its place or else its territory. */
        fun of(call: Call, site: String = VoirLinks.SITE): PosterContent = PosterContent(
            kind = PosterKind.CALL,
            address = call.address,
            title = call.displayTitle,
            example = call.isExample,
            mobilizationType = null,
            callType = call.type,
            start = call.start,
            end = call.end,
            deadline = call.deadline,
            place = call.location?.let { placeOf(it, call.territory) },
            territory = call.territory,
            hosts = listOf(call.author),
            text = shortText(call.summary, call.text),
            payload = PosterQr.payloadFor(call.event, AgirLinks.urlOf(call, site)),
        )

        /** "Place de la République, Paris" stays as it is; "Salle des fêtes" in "Villefictive" becomes "Salle des fêtes, Villefictive". */
        fun placeOf(location: String, territory: String): String {
            val place = location.trim()
            val area = territory.trim()
            if (area.isEmpty() || FrenchText.fold(place).contains(FrenchText.fold(area))) return place
            return "$place, $area"
        }

        /** The summary when there is one; otherwise the beginning of [body], cut after a whole word, at most [MAX_TEXT] characters. */
        fun shortText(summary: String?, body: String): String? {
            summary?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val text = body.trim()
            if (text.isEmpty()) return null
            if (text.length <= MAX_TEXT) return text
            val cut = text.take(MAX_TEXT - ELLIPSIS.length)
            val space = cut.lastIndexOfAny(charArrayOf(' ', '\n'))
            val whole = if (space > MAX_TEXT / 2) cut.substring(0, space) else cut
            return whole.trimEnd(' ', '\n', ',', ';', ':') + ELLIPSIS
        }
    }
}
