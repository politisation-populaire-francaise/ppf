package org.politisationpopulairefrancaise.core.platform

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.io.encoding.Base64

/**
 * A passkey of a member: its WebAuthn credential id and its COSE public key, both in canonical base64url
 * without padding (the only form [TeamList] accepts: decoding then encoding again gives the same text).
 */
data class TeamCredential(val id: String, val publicKey: String) {
    /** The short fingerprint of the passkey ([PasskeyFingerprint]), null when its key is not canonical base64url. */
    fun fingerprint(): String? = decodeCanonicalBase64Url(publicKey, MAX_PUBLIC_KEY_LENGTH)?.let(PasskeyFingerprint::of)
}

/**
 * The short fingerprint of a passkey, the same on the owner's phone (before signing the team list) and on
 * the registration screen of the back-office (when the member adds the key), so that both can be compared
 * by eye: the first 8 bytes of the SHA-256 of the COSE public key, in lowercase hexadecimal by groups of 4
 * characters, for example `3f2a 9c01 d4e7 0b88`.
 */
object PasskeyFingerprint {
    fun of(coseKey: ByteArray): String =
        Crypto.sha256(coseKey).copyOf(8).toHexString().chunked(4).joinToString(" ")
}

/**
 * A member of the team as the root lists them for the signing service: the back-office account id,
 * the date from which their approvals count, their Nostr key if the account has one (so that the author
 * of a draft is recognised), the uses they may approve and their passkeys.
 */
data class TeamMember(
    val account: String,
    val validFrom: Long,
    val nostrKey: String?,
    val uses: Set<SigningUse>,
    val credentials: List<TeamCredential>,
) {
    fun mayApprove(use: SigningUse, time: Long): Boolean = use in uses && time >= validFrom
}

/**
 * The list of the team members whose approvals the signing service accepts (docs/FORMATS.md, section
 * 4 decies; D-129): an addressable event of kind [EventKinds.PLATFORM_TEAM_LIST] signed by the root,
 * `d` = [IDENTIFIER]. The latest version wins; a member left out of it can no longer approve anything.
 * The list names its own end ([TAG_NOT_AFTER], 90 days at most after its date, like the certificates, so
 * that the owner signs everything in one ceremony): without it, or past it, no approval relies on it.
 *
 * Reading fails closed, so that no ambiguity ever gives a passkey to the wrong account: a member described
 * twice, or by a line too short or unreadable, is dropped; a credential id or a COSE key that is not
 * canonical base64url is ignored; a credential id listed twice (compared on its bytes) is dropped from
 * every account; a COSE public key listed twice, by one account or by several, drops every account that
 * lists it.
 */
class TeamList private constructor(
    val event: SignedEvent,
    val members: Map<String, TeamMember>,
    /** The end of the list (excluded), null when it is absent, unreadable or more than 90 days after its date. */
    val notAfter: Long?,
) {
    /** Whether approvals may rely on this list at [time]. */
    fun usableAt(time: Long): Boolean = notAfter != null && time < notAfter

    /** The member holding the passkey [credentialId] (canonical base64url), with that passkey. */
    fun credential(credentialId: String): Pair<TeamMember, TeamCredential>? {
        for (member in members.values) {
            member.credentials.firstOrNull { it.id == credentialId }?.let { return member to it }
        }
        return null
    }

    companion object {
        const val IDENTIFIER: String = "ppf-equipe"
        const val TAG_MEMBER: String = "ppf-member"
        const val TAG_MEMBER_USE: String = "ppf-member-use"
        const val TAG_CREDENTIAL: String = "ppf-credential"

        /** The end of the list, seconds since 1970 (the same tag name as the end of a certificate). */
        const val TAG_NOT_AFTER: String = "ppf-not-after"

        /** A list is valid 90 days at most after its `created_at`, as a certificate. */
        const val MAX_VALIDITY_SECONDS: Long = KeyCertificate.MAX_VALIDITY_SECONDS

        const val ALT: String = "Liste de l'équipe de la plateforme PPF (accords de signature)"

        private val ACCOUNT = Regex("^[A-Za-z0-9._-]{1,64}$")

        /** Reads [event] as the team list signed by [root]; null when it is not one. */
        fun read(event: SignedEvent, root: String): TeamList? {
            if (event.kind != EventKinds.PLATFORM_TEAM_LIST || event.pubkey != root || !event.verify()) return null
            if (event.tags.singleValue("d") != IDENTIFIER) return null
            val notAfter = event.tags.singleValue(TAG_NOT_AFTER)?.let(::parseSeconds)
                ?.takeIf { it > event.createdAt && it - event.createdAt <= MAX_VALIDITY_SECONDS }
            val described = mutableMapOf<String, MutableList<TeamMember>>()
            val malformed = mutableSetOf<String>()
            for (tag in event.tags) {
                if (tag.isEmpty() || tag[0] != TAG_MEMBER) continue
                if (tag.size < 3) {
                    // A line too short still names its account, which is then dropped.
                    tag.getOrNull(1)?.let { malformed += it }
                    continue
                }
                val account = tag[1]
                val validFrom = parseSeconds(tag[2])
                val nostr = tag.getOrNull(3).orEmpty()
                if (validFrom == null || (nostr.isNotEmpty() && !isHexKey(nostr))) {
                    // A malformed line still describes its account, which is then dropped.
                    malformed += account
                    continue
                }
                described.getOrPut(account) { mutableListOf() }.add(TeamMember(account, validFrom, nostr.ifEmpty { null }, emptySet(), emptyList()))
            }
            val credentials = event.tags.filter { it.size >= 4 && it[0] == TAG_CREDENTIAL }.mapNotNull { tag ->
                val id = decodeCanonicalBase64Url(tag[2], MAX_CREDENTIAL_ID_LENGTH) ?: return@mapNotNull null
                val key = decodeCanonicalBase64Url(tag[3], MAX_PUBLIC_KEY_LENGTH) ?: return@mapNotNull null
                ListedCredential(tag[1], TeamCredential(tag[2], tag[3]), id.toHexString(), key.toHexString())
            }
            val idCounts = credentials.groupingBy { it.idBytes }.eachCount()
            val keyCounts = credentials.groupingBy { it.keyBytes }.eachCount()
            credentials.filter { keyCounts.getValue(it.keyBytes) > 1 }.forEach { malformed += it.account }
            val accounts = described
                .filter { (account, lines) -> lines.size == 1 && account !in malformed && ACCOUNT.matches(account) }
                .mapValues { it.value.single() }
            val uses = mutableMapOf<String, MutableSet<SigningUse>>()
            for (tag in event.tags) {
                if (tag.size < 3 || tag[0] != TAG_MEMBER_USE || tag[1] !in accounts) continue
                SigningUse.fromId(tag[2])?.let { uses.getOrPut(tag[1]) { mutableSetOf() }.add(it) }
            }
            val byAccount = mutableMapOf<String, MutableList<TeamCredential>>()
            for (listed in credentials) {
                if (listed.account !in accounts || idCounts.getValue(listed.idBytes) != 1) continue
                byAccount.getOrPut(listed.account) { mutableListOf() }.add(listed.credential)
            }
            val members = accounts.mapValues { (account, member) ->
                member.copy(uses = uses[account].orEmpty(), credentials = byAccount[account].orEmpty())
            }
            return TeamList(event, members, notAfter)
        }

        /** The latest team list of [root] among [events], null when there is none. */
        fun latest(events: Iterable<SignedEvent>, root: String): TeamList? =
            events.mapNotNull { read(it, root) }.reduceOrNull { best, candidate ->
                if (EventReplacement.supersedes(candidate.event, best.event)) candidate else best
            }

        /**
         * The unsigned list the root signs (prepared by the back-office, signed on the root's phone), valid
         * until [notAfter] excluded, 90 days at most after [createdAt].
         */
        fun unsigned(root: String, members: List<TeamMember>, createdAt: Long, notAfter: Long): UnsignedEvent {
            require(members.map { it.account }.distinct().size == members.size) { "Each account is listed once" }
            require(notAfter > createdAt && notAfter - createdAt <= MAX_VALIDITY_SECONDS) { "A team list is valid 90 days at most" }
            val credentials = members.flatMap { it.credentials }
            require(credentials.all { decodeCanonicalBase64Url(it.id, MAX_CREDENTIAL_ID_LENGTH) != null && decodeCanonicalBase64Url(it.publicKey, MAX_PUBLIC_KEY_LENGTH) != null }) {
                "Credential ids and keys are canonical base64url without padding"
            }
            require(credentials.map { it.id }.distinct().size == credentials.size) { "A passkey is listed once" }
            require(credentials.map { it.publicKey }.distinct().size == credentials.size) { "A public key is listed once" }
            val tags = buildList {
                add(listOf("d", IDENTIFIER))
                add(listOf(TAG_NOT_AFTER, notAfter.toString()))
                for (member in members.sortedBy { it.account }) {
                    require(ACCOUNT.matches(member.account)) { "An account id has 1 to 64 letters, digits, dots, dashes or underscores" }
                    add(listOf(TAG_MEMBER, member.account, member.validFrom.toString(), member.nostrKey.orEmpty()))
                    member.uses.map { it.id }.sorted().forEach { add(listOf(TAG_MEMBER_USE, member.account, it)) }
                    member.credentials.forEach { add(listOf(TAG_CREDENTIAL, member.account, it.id, it.publicKey)) }
                }
                add(listOf("alt", ALT))
            }
            return UnsignedEvent(root, createdAt, EventKinds.PLATFORM_TEAM_LIST, tags, "")
        }
    }

    private class ListedCredential(val account: String, val credential: TeamCredential, val idBytes: String, val keyBytes: String)
}

/** A credential id is at most 1023 bytes (WebAuthn), 1366 characters in base64url. */
const val MAX_CREDENTIAL_ID_LENGTH: Int = 1_366
const val MAX_PUBLIC_KEY_LENGTH: Int = 2_048

private val BASE64URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

internal fun isBase64Url(value: String, maxLength: Int): Boolean =
    value.isNotEmpty() && value.length <= maxLength && value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }

/**
 * The bytes of [value] if it is canonical base64url without padding: only the URL alphabet, and encoding
 * the bytes again gives exactly [value] (so that no two texts name the same bytes). Null otherwise.
 */
fun decodeCanonicalBase64Url(value: String, maxLength: Int = MAX_PUBLIC_KEY_LENGTH): ByteArray? {
    if (!isBase64Url(value, maxLength)) return null
    val bytes = try {
        BASE64URL.decode(value)
    } catch (e: IllegalArgumentException) {
        return null
    }
    return if (BASE64URL.encode(bytes) == value) bytes else null
}
