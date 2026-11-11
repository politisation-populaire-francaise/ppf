package org.politisationpopulairefrancaise.core.platform

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * One event of a root request, read from its tags: what the root is asked to sign (contract
 * docs/integration/service-signature.md, step 4; docs/FORMATS.md, section 4 decies). The back-office
 * shows it, the signing service checks it, and the owner's phone (step 5) shows it again in clear
 * before signing.
 */
sealed interface RootRequestItem {
    val event: UnsignedEvent

    /** The certificate of the working key [key]: `active` for [use] from [notBefore] until [notAfter] excluded. */
    data class Certificate(override val event: UnsignedEvent, val key: String, val use: SigningUse, val notBefore: Long, val notAfter: Long) : RootRequestItem

    /** The revocation of the working key [key] from [revokedFrom] (definitive once received). */
    data class Revocation(
        override val event: UnsignedEvent,
        val key: String,
        val use: SigningUse,
        val notBefore: Long,
        val notAfter: Long,
        val revokedFrom: Long,
    ) : RootRequestItem

    /** A new team list, valid until [notAfter] excluded, with its [members] sorted by account. */
    data class Team(override val event: UnsignedEvent, val notAfter: Long, val members: List<TeamMember>) : RootRequestItem
}

/** Why a root request, or one of its events, is refused: a stable [code] and, sometimes, the tag concerned (never a value). */
data class RootRequestRefusal(val code: String, val detail: String? = null)

/**
 * The root requests of the signing service (D-141): the back-office composes the unsigned events the
 * root must sign (certificates of the keys awaiting certification, revocations, the team list), deposits
 * them in the service (`POST /v1/root-requests`), and shows a QR code ([RootRequestQr]) that the owner's
 * phone scans; the phone fetches the request from the approval origin of its environment, checks its
 * hash ([sha256]), shows everything in clear, signs with the root and posts the signed events to the
 * service directly.
 *
 * Every event is read strictly ([read]), so that the root never signs anything but the three forms the
 * back-office prepares: no other tag, an empty content, one use per certificate equal to the use of the key,
 * 90 days of validity at most, a revocation always dated, a team list in which no member nor passkey would
 * be dropped by the fail-closed reading of [TeamList] and in which no automatic use is given to a member.
 */
object RootRequests {
    /** At most this many events in one request. */
    const val MAX_EVENTS: Int = 100

    /** The body of `POST /v1/root-requests` is at most this size. */
    const val MAX_BODY_BYTES: Int = 512 * 1024

    /** The service keeps a request this long (in memory: a restart loses it, the back-office prepares a new one). */
    const val LIFETIME_SECONDS: Long = 86_400

    /** The service keeps at most this many pending requests; the oldest is dropped. */
    const val MAX_PENDING: Int = 20

    /** An event of a request is dated at most 5 minutes in the future and at most [LIFETIME_SECONDS] in the past. */
    const val FUTURE_TOLERANCE_SECONDS: Long = 300

    /** The tags a certificate or a revocation of a request may carry, and nothing else. */
    val CERTIFICATE_TAGS: Set<String> = setOf("d", KeyCertificate.TAG_USE, KeyCertificate.TAG_NOT_BEFORE, KeyCertificate.TAG_NOT_AFTER, KeyCertificate.TAG_STATUS, "alt")

    /** The tags a team list of a request may carry, and nothing else. */
    val TEAM_LIST_TAGS: Set<String> = setOf("d", TeamList.TAG_NOT_AFTER, TeamList.TAG_MEMBER, TeamList.TAG_MEMBER_USE, TeamList.TAG_CREDENTIAL, "alt")

    /**
     * The text whose SHA-256 is the hash of a request: the JSON array of the NIP-01 canonical serializations
     * of its events, in order, without any space (`[[0,"<root>",…],[0,"<root>",…]]`). Each element is the
     * text whose SHA-256 is the id of its event, so the hash binds every event and their order.
     */
    fun canonical(events: List<UnsignedEvent>): String = events.joinToString(separator = ",", prefix = "[", postfix = "]") { it.canonicalJson() }

    /** The hash of a request (lowercase hex), the one written in its QR code. */
    fun sha256(events: List<UnsignedEvent>): String = Crypto.sha256(canonical(events).encodeToByteArray()).toHexString()

    /** [event] read as a certificate, a revocation or a team list of [root]; null when [refusalOf] refuses it. */
    fun read(event: UnsignedEvent, root: String): RootRequestItem? = parse(event, root) as? RootRequestItem

    /** Why [event] is not a well-formed root event of [root], null when it is one. */
    fun refusalOf(event: UnsignedEvent, root: String): RootRequestRefusal? = parse(event, root) as? RootRequestRefusal

    /**
     * Why the request of [events] must not be kept, null when it may: 1 to [MAX_EVENTS] events, each
     * well-formed ([refusalOf]) and dated around [now]; each certificate and revocation names a working key
     * [useOf] knows, with its use; no address twice (one event per key, one team list).
     */
    fun check(events: List<UnsignedEvent>, root: String, useOf: (String) -> SigningUse?, now: Long): RootRequestRefusal? {
        if (events.isEmpty() || events.size > MAX_EVENTS) return RootRequestRefusal(SigningRefusals.MALFORMED_REQUEST, "events")
        val addresses = mutableSetOf<String>()
        for (event in events) {
            if (event.createdAt > now + FUTURE_TOLERANCE_SECONDS) return RootRequestRefusal(SigningRefusals.CREATED_AT_IN_FUTURE)
            if (event.createdAt < now - LIFETIME_SECONDS) return RootRequestRefusal(SigningRefusals.CREATED_AT_TOO_OLD)
            val address = when (val item = parse(event, root)) {
                is RootRequestRefusal -> return item
                is RootRequestItem.Certificate -> {
                    keyRefusal(item.key, item.use, useOf)?.let { return it }
                    "${event.kind}:${item.key}"
                }
                is RootRequestItem.Revocation -> {
                    keyRefusal(item.key, item.use, useOf)?.let { return it }
                    "${event.kind}:${item.key}"
                }
                is RootRequestItem.Team -> "${event.kind}:${TeamList.IDENTIFIER}"
                else -> return RootRequestRefusal(SigningRefusals.NOT_A_ROOT_EVENT)
            }
            if (!addresses.add(address)) return RootRequestRefusal(SigningRefusals.DUPLICATE_ADDRESS)
        }
        return null
    }

    private fun keyRefusal(key: String, use: SigningUse, useOf: (String) -> SigningUse?): RootRequestRefusal? {
        val known = useOf(key) ?: return RootRequestRefusal(SigningRefusals.UNKNOWN_KEY)
        return if (known != use) RootRequestRefusal(SigningRefusals.WRONG_USES) else null
    }

    /** A [RootRequestItem] or a [RootRequestRefusal]. */
    private fun parse(event: UnsignedEvent, root: String): Any {
        if (event.pubkey != root) return RootRequestRefusal(SigningRefusals.NOT_A_ROOT_EVENT)
        if (event.content.isNotEmpty()) return RootRequestRefusal(SigningRefusals.BAD_CONTENT)
        return when (event.kind) {
            EventKinds.PLATFORM_KEY_CERTIFICATE -> parseCertificate(event, root)
            EventKinds.PLATFORM_TEAM_LIST -> parseTeamList(event)
            else -> RootRequestRefusal(SigningRefusals.NOT_A_ROOT_EVENT)
        }
    }

    private fun parseCertificate(event: UnsignedEvent, root: String): Any {
        val tags = event.tags
        tagRefusal(tags, CERTIFICATE_TAGS)?.let { return it }
        // Every tag of a certificate but its status has exactly a name and a value.
        tags.firstOrNull { it[0] != KeyCertificate.TAG_STATUS && it.size != 2 }?.let { return RootRequestRefusal(SigningRefusals.BAD_TAG, it[0]) }
        if (tags.count { it[0] == "alt" } > 1) return RootRequestRefusal(SigningRefusals.BAD_TAG, "alt")
        val key = tags.singleValue("d")?.takeIf { isHexKey(it) && it != root } ?: return RootRequestRefusal(SigningRefusals.BAD_TAG, "d")
        val uses = tags.filter { it[0] == KeyCertificate.TAG_USE }.map { it[1] }
        val use = uses.singleOrNull()?.let(SigningUse::fromId) ?: return RootRequestRefusal(SigningRefusals.WRONG_USES)
        val notBefore = tags.singleValue(KeyCertificate.TAG_NOT_BEFORE)?.let(::parseSeconds)
        val notAfter = tags.singleValue(KeyCertificate.TAG_NOT_AFTER)?.let(::parseSeconds)
        if (notBefore == null || notAfter == null || notAfter <= notBefore || notAfter - notBefore > KeyCertificate.MAX_VALIDITY_SECONDS) {
            return RootRequestRefusal(SigningRefusals.BAD_VALIDITY)
        }
        val status = tags.filter { it[0] == KeyCertificate.TAG_STATUS }.singleOrNull() ?: return RootRequestRefusal(SigningRefusals.BAD_STATUS)
        return when {
            status.size == 2 && status[1] == KeyStatus.ACTIVE.tagValue -> RootRequestItem.Certificate(event, key, use, notBefore, notAfter)
            status.size == 3 && status[1] == KeyStatus.REVOKED.tagValue -> {
                val from = parseSeconds(status[2]) ?: return RootRequestRefusal(SigningRefusals.BAD_STATUS)
                RootRequestItem.Revocation(event, key, use, notBefore, notAfter, from)
            }
            else -> RootRequestRefusal(SigningRefusals.BAD_STATUS)
        }
    }

    private fun parseTeamList(event: UnsignedEvent): Any {
        val tags = event.tags
        tagRefusal(tags, TEAM_LIST_TAGS)?.let { return it }
        if (tags.count { it[0] == "alt" } > 1 || tags.any { it[0] == "alt" && it.size != 2 }) return RootRequestRefusal(SigningRefusals.BAD_TAG, "alt")
        if (tags.singleValue("d") != TeamList.IDENTIFIER || tags.any { it[0] == "d" && it.size != 2 }) return RootRequestRefusal(SigningRefusals.NOT_A_TEAM_LIST, "d")
        val (members, notAfter) = TeamList.parse(tags, event.createdAt)
        if (notAfter == null || tags.any { it[0] == TeamList.TAG_NOT_AFTER && it.size != 2 }) return RootRequestRefusal(SigningRefusals.NOT_A_TEAM_LIST, TeamList.TAG_NOT_AFTER)
        // Nothing the root signs may be dropped by the fail-closed reading: every member line is read, every passkey kept.
        val memberLines = tags.filter { it[0] == TeamList.TAG_MEMBER }
        if (memberLines.any { it.size != 4 } || memberLines.map { it[1] }.toSet() != members.keys || memberLines.size != members.size) {
            return RootRequestRefusal(SigningRefusals.BAD_TAG, TeamList.TAG_MEMBER)
        }
        val useLines = tags.filter { it[0] == TeamList.TAG_MEMBER_USE }
        // Only the human uses are approved by members; an automatic use is never given to anyone.
        if (useLines.any { it.size != 3 || it[1] !in members || SigningUse.fromId(it[2])?.automatic != false } || useLines.map { it[1] to it[2] }.distinct().size != useLines.size) {
            return RootRequestRefusal(SigningRefusals.BAD_TAG, TeamList.TAG_MEMBER_USE)
        }
        val credentialLines = tags.filter { it[0] == TeamList.TAG_CREDENTIAL }
        if (credentialLines.any { it.size != 4 } || credentialLines.size != members.values.sumOf { it.credentials.size }) {
            return RootRequestRefusal(SigningRefusals.BAD_TAG, TeamList.TAG_CREDENTIAL)
        }
        return RootRequestItem.Team(event, notAfter, members.values.sortedBy { it.account })
    }

    /** A tag without name, or with a name outside [allowed]. */
    private fun tagRefusal(tags: List<List<String>>, allowed: Set<String>): RootRequestRefusal? {
        val wrong = tags.firstOrNull { it.isEmpty() || it[0] !in allowed } ?: return null
        return RootRequestRefusal(SigningRefusals.TAG_NOT_ALLOWED, wrong.firstOrNull()?.take(40))
    }
}

/**
 * The text of the QR code of a root request (D-141, docs/FORMATS.md, section 4 decies):
 * `PPF-RACINE:1:<environment>:<request id>:<sha256 hex>`. The phone takes from it the environment (to
 * fetch the request from the approval origin it knows for that environment, never from an address in the
 * code), the id of the request and the hash that binds its whole content.
 */
data class RootRequestQr(val environment: String, val id: String, val sha256: String) {
    init {
        require(environment in ENVIRONMENTS) { "The environment is production, preproduction or development" }
        require(isRequestId(id)) { "A request id is 32 lowercase hexadecimal characters" }
        require(isHexKey(sha256)) { "A request hash is 64 lowercase hexadecimal characters" }
    }

    fun text(): String = "$PREFIX:$VERSION:$environment:$id:$sha256"

    override fun toString(): String = text()

    companion object {
        const val PREFIX: String = "PPF-RACINE"
        const val VERSION: Int = 1
        val ENVIRONMENTS: Set<String> = setOf("production", "preproduction", "development")

        /** The QR text read back; null for anything else (another prefix or version, an unknown environment, a malformed id or hash). */
        fun parse(text: String): RootRequestQr? {
            val parts = text.split(':')
            if (parts.size != 5 || parts[0] != PREFIX || parts[1] != VERSION.toString()) return null
            if (parts[2] !in ENVIRONMENTS || !isRequestId(parts[3]) || !isHexKey(parts[4])) return null
            return RootRequestQr(parts[2], parts[3], parts[4])
        }

        /** A request id: 128 random bits in lowercase hexadecimal. */
        fun isRequestId(value: String): Boolean = value.length == 32 && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
