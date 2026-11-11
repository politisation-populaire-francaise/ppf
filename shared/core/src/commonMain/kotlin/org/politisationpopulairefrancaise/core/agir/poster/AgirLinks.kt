package org.politisationpopulairefrancaise.core.agir.poster

import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.editorial.OfficialSite
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.VoirLinks

/**
 * The stable web addresses of the contents of "Agir" that a poster may carry (AG-61), built like
 * those of "Voir" and "Comprendre" ([VoirLinks]): one path per content, from what never changes
 * when the content is corrected. Contents of "Agir" have many authors, so the path names the key of
 * the author as well as the identifier (`d`): two authors may choose the same identifier.
 *
 * | Content | Path |
 * |---|---|
 * | Mobilization of the agenda (kind 31923) | `/agir/agenda/<key>/<identifier>` |
 * | Call (kind 30023, `ppf-type` = `appel`) | `/agir/appels/<key>/<identifier>` |
 *
 * Never any parameter nor fragment: nothing in the address says who printed the poster, where it
 * was put up or when it was read (no tracking). The pages of the site at these addresses are still
 * to be written (docs/integration/agir-affiche-qr.md, questions); the application reads them already.
 */
object AgirLinks {
    const val AGENDA: String = "/agir/agenda"
    const val CALLS: String = "/agir/appels"

    private val KEY = Regex("^[0-9a-f]{64}$")

    /** Identifiers written as they are in a path: the unreserved characters of RFC 3986 only. */
    private val SEGMENT = Regex("^[A-Za-z0-9._~-]{1,64}$")

    /** The path of a mobilization: `/agir/agenda/<key>/<identifier>`. */
    fun pathOf(mobilization: Mobilization): String = path(AGENDA, mobilization.address)

    /** The path of a call: `/agir/appels/<key>/<identifier>`. */
    fun pathOf(call: Call): String = path(CALLS, call.address)

    fun urlOf(mobilization: Mobilization, base: String = VoirLinks.SITE): String = base.trimEnd('/') + pathOf(mobilization)

    fun urlOf(call: Call, base: String = VoirLinks.SITE): String = base.trimEnd('/') + pathOf(call)

    private fun path(root: String, address: ContentAddress): String =
        "$root/${address.pubkey}/${VoirLinks.encodeSegment(address.identifier)}"

    /**
     * The content an address of a poster leads to, or null when [url] is not exactly one of these
     * addresses: `https` only, on a domain of PPF ([OfficialSite], never a look-alike nor a user name
     * before the host), no parameter, no fragment, no other path. A mobilization reads as kind 31923,
     * a call as kind 30023.
     */
    fun parse(url: String): ContentAddress? {
        if (url.any { it.isWhitespace() || it.code < 0x20 }) return null
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val rest = url.substring("https://".length)
        if ('?' in rest || '#' in rest) return null
        val authority = rest.substringBefore('/')
        if ('@' in authority || ':' in authority) return null
        if (!OfficialSite.isOfficial(authority)) return null
        val segments = rest.substringAfter('/', "").split('/')
        if (segments.size != 4 || segments[0] != "agir") return null
        val kind = when ("/agir/" + segments[1]) {
            AGENDA -> Mobilization.KIND
            CALLS -> Call.KIND
            else -> return null
        }
        val key = segments[2]
        val identifier = segments[3]
        if (!KEY.matches(key) || !SEGMENT.matches(identifier)) return null
        return runCatching { ContentAddress(kind, key, identifier) }.getOrNull()
    }
}
