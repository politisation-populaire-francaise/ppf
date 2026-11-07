package org.politisationpopulairefrancaise.core.voir

/**
 * The stable web addresses of the contents of "Voir": one page per content, linkable and
 * shareable, built from the content's identifier (the `d` tag, which never changes when the
 * content is corrected). Used by the web site and by the "Partager" button of the applications,
 * so that both always give the same address.
 *
 * | Content | Path |
 * |---|---|
 * | Indicator | `/voir/chiffres/<identifier>` |
 * | Sourced fact | `/voir/faits/<identifier>` |
 * | Public person | `/voir/personnes/<identifier>` |
 * | Bill | `/voir/textes/<identifier>` |
 * | Movement of the world | `/voir/monde/<identifier>` |
 * | Dossier | `/voir/dossiers/<identifier>` |
 *
 * The identifier is percent-encoded (RFC 3986): letters, digits, `-`, `.`, `_` and `~` stay as
 * they are, every other character is written as its UTF-8 bytes (`%C3%A9` for "é").
 */
object VoirLinks {
    /** The public address of the site (cahier des charges: main domain). */
    const val SITE: String = "https://politisation-populaire-francaise.org"

    const val HOME: String = "/voir"
    const val CHRONOLOGY: String = "/voir/chronologie"
    const val LIBERTIES: String = "/voir/libertes"
    const val CORRECTIONS: String = "/voir/corrections"
    const val METHOD: String = "/voir/methode"

    // The path segment of each kind of content.
    const val INDICATORS: String = "chiffres"
    const val FACTS: String = "faits"
    const val PERSONS: String = "personnes"
    const val TEXTS: String = "textes"
    const val WORLD: String = "monde"
    const val DOSSIERS: String = "dossiers"

    /** The segment of the kind of [item]. */
    fun segmentOf(item: VoirItem): String = when (item) {
        is VoirItem.OfIndicator -> INDICATORS
        is VoirItem.OfFact -> FACTS
        is VoirItem.OfPerson -> PERSONS
        is VoirItem.OfText -> TEXTS
        is VoirItem.OfMovement -> WORLD
        is VoirItem.OfDossier -> DOSSIERS
    }

    /** The path of the page of [item], from the root of the site: `/voir/faits/chrono-premiere-journee`. */
    fun pathOf(item: VoirItem): String = "$HOME/${segmentOf(item)}/${encodeSegment(item.address.identifier)}"

    /** The full address of the page of [item], to share it: `https://politisation-populaire-francaise.org/voir/...`. */
    fun urlOf(item: VoirItem, base: String = SITE): String = base.trimEnd('/') + pathOf(item)

    /** Percent-encodes one path segment (RFC 3986, section 2.3: only unreserved characters stay). */
    fun encodeSegment(value: String): String {
        // "." and ".." would be read as dot-segments and resolved away by browsers.
        if (value == "." || value == "..") return value.replace(".", "%2E")
        val out = StringBuilder()
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (code < 0x80 && (char.isLetterOrDigit() || char in UNRESERVED_MARKS)) {
                out.append(char)
            } else {
                out.append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
            }
        }
        return out.toString()
    }

    private const val UNRESERVED_MARKS: String = "-._~"
    private const val HEX: String = "0123456789ABCDEF"
}
