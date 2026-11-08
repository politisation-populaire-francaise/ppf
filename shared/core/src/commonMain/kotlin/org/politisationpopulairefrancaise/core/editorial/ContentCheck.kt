package org.politisationpopulairefrancaise.core.editorial

import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The official addresses of PPF (CLAUDE.md, section 1): the main domain and the five domains reserved to
 * protect the name. A link elsewhere is not PPF, whatever its page says.
 */
object OfficialSite {
    const val MAIN_DOMAIN: String = "politisation-populaire-francaise.org"
    val DOMAINS: Set<String> = setOf("org", "fr", "com", "eu", "net", "app").map { "politisation-populaire-francaise.$it" }.toSet()

    /** The short address of a verification code, printed on the images of PPF: `https://…/v/<code>`. */
    fun verificationUrl(code: String): String = "https://$MAIN_DOMAIN/v/$code"

    /** Whether [host] is one of [DOMAINS] (case and a leading `www.` ignored, never a subdomain or a look-alike). */
    fun isOfficial(host: String): Boolean = host.lowercase().removePrefix("www.").removeSuffix(".") in DOMAINS
}

/** What a person gives to check a content (S-141): read by [CheckQuery.parse]. */
sealed interface CheckQuery {
    /** A signed event pasted or opened from a file: checked by itself, then compared with PPF's copy. */
    data class Event(val event: SignedEvent) : CheckQuery

    /** The full identifier of a version (64 hexadecimal characters). */
    data class Id(val id: String) : CheckQuery

    /** A verification code: the first [ContentCheck.CODE_LENGTH] characters of the identifier, printed on an image. */
    data class Code(val prefix: String) : CheckQuery

    /** A link that is not a verification address: [official] tells whether it leads to a domain of PPF. */
    data class Link(val host: String, val official: Boolean) : CheckQuery

    /** Nothing that can be read: an empty text, a broken JSON, an unknown form. */
    data object Unreadable : CheckQuery

    companion object {
        private val HEX64 = Regex("[0-9a-f]{64}")
        private val CODE = Regex("[0-9a-f]{${ContentCheck.CODE_LENGTH}}")
        private val VERIFICATION_PATH = Regex("/v/([0-9a-fA-F]{${ContentCheck.CODE_LENGTH}}|[0-9a-fA-F]{64})/?")

        /**
         * Reads [input]: a JSON event (`{…}`), an identifier, a code (spaces and hyphens ignored, as printed in
         * groups), or a link. A link of a verification address (`/v/<code>`) is followed only on a domain of
         * PPF; elsewhere it stays a [Link] to an unofficial site, even if its page claims the opposite.
         */
        fun parse(input: String): CheckQuery {
            val text = input.trim()
            if (text.isEmpty() || text.length > ContentCheck.MAX_INPUT_CHARS) return Unreadable
            if (text.startsWith("{")) {
                return runCatching { Event(SignedEvent.fromJson(text)) }.getOrDefault(Unreadable)
            }
            val compact = text.filterNot { it == ' ' || it == '-' || it == ' ' || it == ' ' }.lowercase()
            if (HEX64.matches(compact)) return Id(compact)
            if (CODE.matches(compact)) return Code(compact)
            return link(text) ?: Unreadable
        }

        private fun link(text: String): CheckQuery? {
            if (text.any { it.isWhitespace() }) return null
            val rest = when {
                text.startsWith("https://", ignoreCase = true) -> text.substring(8)
                text.startsWith("http://", ignoreCase = true) -> text.substring(7)
                text.contains('.') && text.contains('/') -> text
                else -> return null
            }
            val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
            // A user name before « @ » hides the real host (« https://politisation-populaire-francaise.org@piege.example/ »).
            val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
            if (host.isEmpty() || !host.contains('.')) return null
            val official = OfficialSite.isOfficial(host) && !authority.contains('@')
            val path = "/" + rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
            val match = VERIFICATION_PATH.matchEntire(path)
            if (official && match != null) {
                val value = match.groupValues[1].lowercase()
                return if (value.length == 64) Id(value) else Code(value)
            }
            return Link(host, official)
        }
    }
}

/** What the check found (S-141), in the words the site and the application show. */
enum class CheckOutcome {
    /** Signed by a key of PPF and the current version of its content. */
    AUTHENTIC_CURRENT,

    /** Signed by a key of PPF, but a more recent version exists: [CheckResult.current]. */
    AUTHENTIC_OUTDATED,

    /** Signed by a key of PPF, but this copy of PPF does not have it (withdrawn, or not received yet). */
    AUTHENTIC_UNLISTED,

    /** A valid signature, but by a key that is not one of PPF's: not a content of PPF. */
    NOT_PPF_KEY,

    /** The signature does not match the text: changed or damaged after its publication. */
    ALTERED,

    /** No content of PPF has this identifier or code. */
    UNKNOWN,

    /** A link to a domain of PPF (open it to read the current version). */
    OFFICIAL_LINK,

    /** A link to a site that is not PPF, whatever it says. */
    UNOFFICIAL_LINK,

    /** Nothing that can be checked was given. */
    UNREADABLE,
}

/** The answer of [ContentCheck.check]: [event] is the version checked, [current] the version PPF serves now. */
data class CheckResult(val outcome: CheckOutcome, val event: SignedEvent? = null, val current: SignedEvent? = null, val host: String? = null)

/** Where the check looks: the trusted keys and PPF's copy of its contents (the server's, or the device's own). */
interface ContentLookup {
    fun isTrusted(pubkeyHex: String): Boolean

    fun byId(id: String): SignedEvent?

    /** The contents whose identifier starts with [prefix] (one, normally). */
    fun byCode(prefix: String): List<SignedEvent>

    /** The version served now at [address], if any. */
    fun latestAt(address: ContentAddress): SignedEvent?
}

/**
 * « Vérifier un contenu PPF » (S-141, M33; docs/integration/export-ouvert.md, section 6): the same check on the
 * site and in the application, even offline. A code only finds a content; the proof is always the signature
 * of the event found, by a key of PPF, which the person compares with what they received.
 */
object ContentCheck {
    /** The characters of the identifier printed as the verification code (64 bits). */
    const val CODE_LENGTH: Int = 16

    /** A signed content is 64 KB at most (API of the server); a little more for the spaces of a pasted JSON. */
    const val MAX_INPUT_CHARS: Int = 96 * 1024

    /** The code of [event]: the first [CODE_LENGTH] characters of its identifier. */
    fun codeOf(event: SignedEvent): String = event.id.take(CODE_LENGTH)

    fun check(query: CheckQuery, lookup: ContentLookup): CheckResult = when (query) {
        is CheckQuery.Event -> checkEvent(query.event, lookup)
        is CheckQuery.Id -> lookup.byId(query.id)?.let { checkEvent(it, lookup) } ?: CheckResult(CheckOutcome.UNKNOWN)
        is CheckQuery.Code -> lookup.byCode(query.prefix).singleOrNull()?.let { checkEvent(it, lookup) } ?: CheckResult(CheckOutcome.UNKNOWN)
        is CheckQuery.Link -> CheckResult(if (query.official) CheckOutcome.OFFICIAL_LINK else CheckOutcome.UNOFFICIAL_LINK, host = query.host)
        CheckQuery.Unreadable -> CheckResult(CheckOutcome.UNREADABLE)
    }

    private fun checkEvent(event: SignedEvent, lookup: ContentLookup): CheckResult {
        if (!event.verify()) return CheckResult(CheckOutcome.ALTERED, event)
        if (!lookup.isTrusted(event.pubkey)) return CheckResult(CheckOutcome.NOT_PPF_KEY, event)
        val current = ContentAddress.of(event)?.let(lookup::latestAt) ?: lookup.byId(event.id)
        return when {
            current == null -> CheckResult(CheckOutcome.AUTHENTIC_UNLISTED, event)
            current.id == event.id -> CheckResult(CheckOutcome.AUTHENTIC_CURRENT, event, current)
            // The same rule as the server: the most recent wins, the smaller id at the same second.
            current.createdAt > event.createdAt || (current.createdAt == event.createdAt && current.id < event.id) ->
                CheckResult(CheckOutcome.AUTHENTIC_OUTDATED, event, current)
            // More recent than this copy: not received here yet.
            else -> CheckResult(CheckOutcome.AUTHENTIC_UNLISTED, event)
        }
    }
}
