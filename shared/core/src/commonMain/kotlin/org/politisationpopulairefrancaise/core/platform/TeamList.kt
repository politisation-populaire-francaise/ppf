package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** A passkey of a member: its WebAuthn credential id and its COSE public key, both in base64url without padding. */
data class TeamCredential(val id: String, val publicKey: String)

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
 *
 * Reading fails closed: a member described twice, or a passkey listed twice, is dropped, so that no
 * ambiguity ever gives a passkey to the wrong account.
 */
class TeamList private constructor(val event: SignedEvent, val members: Map<String, TeamMember>) {

    /** The member holding the passkey [credentialId] (base64url as listed), with that passkey. */
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
        const val ALT: String = "Liste de l'équipe de la plateforme PPF (accords de signature)"

        private val ACCOUNT = Regex("^[A-Za-z0-9._-]{1,64}$")
        private const val MAX_CREDENTIAL_ID = 1_366
        private const val MAX_PUBLIC_KEY = 2_048

        /** Reads [event] as the team list signed by [root]; null when it is not one. */
        fun read(event: SignedEvent, root: String): TeamList? {
            if (event.kind != EventKinds.PLATFORM_TEAM_LIST || event.pubkey != root || !event.verify()) return null
            if (event.tags.singleValue("d") != IDENTIFIER) return null
            val described = mutableMapOf<String, MutableList<TeamMember>>()
            val malformed = mutableSetOf<String>()
            for (tag in event.tags) {
                if (tag.size < 3 || tag[0] != TAG_MEMBER) continue
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
            val accounts = described
                .filter { (account, lines) -> lines.size == 1 && account !in malformed && ACCOUNT.matches(account) }
                .mapValues { it.value.single() }
            val uses = mutableMapOf<String, MutableSet<SigningUse>>()
            for (tag in event.tags) {
                if (tag.size < 3 || tag[0] != TAG_MEMBER_USE || tag[1] !in accounts) continue
                SigningUse.fromId(tag[2])?.let { uses.getOrPut(tag[1]) { mutableSetOf() }.add(it) }
            }
            val credentials = event.tags.filter { it.size >= 4 && it[0] == TAG_CREDENTIAL }
            val idCounts = credentials.groupingBy { it[2] }.eachCount()
            val byAccount = mutableMapOf<String, MutableList<TeamCredential>>()
            for (tag in credentials) {
                val (account, id, key) = Triple(tag[1], tag[2], tag[3])
                if (account !in accounts || idCounts[id] != 1) continue
                if (!isBase64Url(id, MAX_CREDENTIAL_ID) || !isBase64Url(key, MAX_PUBLIC_KEY)) continue
                byAccount.getOrPut(account) { mutableListOf() }.add(TeamCredential(id, key))
            }
            val members = accounts.mapValues { (account, member) ->
                member.copy(uses = uses[account].orEmpty(), credentials = byAccount[account].orEmpty())
            }
            return TeamList(event, members)
        }

        /** The latest team list of [root] among [events], null when there is none. */
        fun latest(events: Iterable<SignedEvent>, root: String): TeamList? =
            events.mapNotNull { read(it, root) }.reduceOrNull { best, candidate ->
                if (EventReplacement.supersedes(candidate.event, best.event)) candidate else best
            }

        /** The unsigned list the root signs (prepared by the back-office, signed on the root's phone). */
        fun unsigned(root: String, members: List<TeamMember>, createdAt: Long): UnsignedEvent {
            require(members.map { it.account }.distinct().size == members.size) { "Each account is listed once" }
            val tags = buildList {
                add(listOf("d", IDENTIFIER))
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
}

internal fun isBase64Url(value: String, maxLength: Int): Boolean =
    value.isNotEmpty() && value.length <= maxLength && value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
