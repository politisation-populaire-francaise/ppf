package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.KeyStatus
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.TeamCredential
import org.politisationpopulairefrancaise.core.platform.TeamList
import org.politisationpopulairefrancaise.core.platform.TeamMember
import org.politisationpopulairefrancaise.core.platform.WorkingKeyInfo
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.util.UUID

/**
 * Who may approve the platform signature of each human use (D-141): the **only** table that maps a use of
 * the signing service to the back-office permission of the queue that publishes it. A member of the team
 * list receives a use when their account holds that permission (with the backup-key rule of [TeamActor]:
 * an account with a single passkey holds no queue permission, hence no use). Automatic uses never appear:
 * nobody approves them.
 *
 * | Use | Permission | Role today |
 * |---|---|---|
 * | `editorial.comprendre` | `queue.editorial.comprendre.publish` | editor |
 * | `editorial.voir` | `queue.editorial.voir.publish` | editor |
 * | `agir.organisations` | `queue.agir.organisations.decide` | organisation verifier |
 * | `revendications.status` | `queue.revendications.proposals.decide` | moderator |
 * | `decider.pacte` | `queue.decider.petitions.decide` (the official petitions, D-115: the only queue of Décider that signs this use today; its other queues are added here when they sign) | administrator |
 * | `groups.takeover` | `queue.groups.orphans.decide` (D-144) | moderator |
 */
object SigningApprovals {
    val PERMISSIONS: Map<SigningUse, String> = mapOf(
        SigningUse.EDITORIAL_COMPRENDRE to "queue.editorial.comprendre.publish",
        SigningUse.EDITORIAL_VOIR to "queue.editorial.voir.publish",
        SigningUse.AGIR_ORGANISATIONS to Permissions.queueDecide("agir.organisations"),
        SigningUse.REVENDICATIONS_STATUS to Permissions.queueDecide("revendications.proposals"),
        SigningUse.DECIDER_PACTE to Permissions.queueDecide("decider.petitions"),
        // The takeover of a room left without administrator: approved by another member of the queue groups.orphans (D-144).
        SigningUse.GROUPS_TAKEOVER to Permissions.queueDecide("groups.orphans"),
    )

    init {
        check(PERMISSIONS.keys == SigningUse.entries.filter { !it.automatic }.toSet()) { "Every human use, and only them, has a permission" }
    }

    /** The uses [actor] may approve. */
    fun usesOf(actor: TeamActor): Set<SigningUse> = PERMISSIONS.filterValues(actor::has).keys
}

/** A passkey of an active account, as the team list names it: its credential id and COSE key, raw. */
class CandidateKey(val id: UUID, val name: String, val credentialId: ByteArray, val coseKey: ByteArray) {
    val credential: TeamCredential get() = TeamCredential(Tokens.base64Url(credentialId), Tokens.base64Url(coseKey))
    val fingerprint: String get() = org.politisationpopulairefrancaise.core.platform.PasskeyFingerprint.of(coseKey)
}

/** An active account of the team, with what the team list needs of it. */
class TeamListCandidate(val account: TeamAccount, val actor: TeamActor, val keys: List<CandidateKey>) {
    val uses: Set<SigningUse> get() = SigningApprovals.usesOf(actor)
}

/** Why an active account is left out of the team list. */
enum class Exclusion {
    NO_PASSKEY,
    NO_USE,

    /** One of its passkeys has the public key of a passkey of another account: the reading of the list would drop both. */
    SHARED_PASSKEY,
}

/** What the root is asked to sign in one ceremony, with the accounts left out of the team list. */
class CeremonyPlan(
    val certificates: List<UnsignedEvent>,
    val revocations: List<UnsignedEvent>,
    /** Null when no account may be listed: the service keeps its current list. */
    val teamList: UnsignedEvent?,
    val listed: List<TeamListCandidate>,
    val excluded: List<Pair<TeamListCandidate, Exclusion>>,
) {
    val events: List<UnsignedEvent> get() = certificates + revocations + listOfNotNull(teamList)
}

/**
 * The composition of the ceremony of the root (contract docs/integration/service-signature.md, step 4;
 * D-141), a pure function of what the signing service says ([KeysResponse], its current team list) and of
 * the active accounts of the team, so that it is tested without any service.
 */
object RootCeremony {
    /** A key whose certificate or team list ends within this is flagged on the page. */
    const val WARNING_SECONDS: Long = 15L * 86_400

    /**
     * In one go: the certificate of every key `awaiting_certification` (from now rounded down to the minute,
     * 90 days), the closing revocation of every key expired or replaced and not revoked yet ([closing]), and a
     * new team list valid 90 days. A member already in [currentList] keeps their `validFrom`; a new member's
     * approvals count from [now]. Accounts with no passkey or no use are left out (and said so).
     */
    fun compose(root: String, keys: KeysResponse, candidates: List<TeamListCandidate>, currentList: TeamList?, now: Long): CeremonyPlan {
        val start = now - Math.floorMod(now, 60L)
        val certificates = keys.keys.filter { it.state == WorkingKeyStates.AWAITING_CERTIFICATION }.mapNotNull { key ->
            val use = SigningUse.fromId(key.use) ?: return@mapNotNull null
            KeyCertificate.unsigned(root, key.pubkey, listOf(use), start, start + KeyCertificate.MAX_VALIDITY_SECONDS, now)
        }
        val revocations = keys.keys.filter { closing(it, keys.keys) }.mapNotNull { key -> revocation(root, key, effectiveFrom = closingDate(key), now) }
        val excluded = mutableListOf<Pair<TeamListCandidate, Exclusion>>()
        val keyCounts = candidates.flatMap { candidate -> candidate.keys.map { it.coseKey.toHexString() }.distinct() }.groupingBy { it }.eachCount()
        val listed = candidates.sortedBy { it.account.id.toString() }.filter { candidate ->
            when {
                candidate.keys.isEmpty() -> false.also { excluded += candidate to Exclusion.NO_PASSKEY }
                candidate.uses.isEmpty() -> false.also { excluded += candidate to Exclusion.NO_USE }
                candidate.keys.any { keyCounts.getValue(it.coseKey.toHexString()) > 1 } -> false.also { excluded += candidate to Exclusion.SHARED_PASSKEY }
                else -> true
            }
        }
        val members = listed.map { candidate ->
            val account = candidate.account.id.toString()
            TeamMember(
                account = account,
                validFrom = currentList?.members?.get(account)?.validFrom ?: now,
                // Only a well-formed key: a malformed one would make the reading of the list drop the member.
                nostrKey = candidate.account.nostrPubkey?.takeIf { key -> key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' } },
                uses = candidate.uses,
                // The same passkey twice for one account (never stored so) would be listed once.
                credentials = candidate.keys.distinctBy { it.coseKey.toHexString() }.map { it.credential },
            )
        }
        val teamList = if (members.isEmpty()) null else TeamList.unsigned(root, members, now, now + TeamList.MAX_VALIDITY_SECONDS)
        return CeremonyPlan(certificates, revocations, teamList, listed, excluded)
    }

    /**
     * Whether [key] needs its closing revocation (step 4): expired, or replaced (still active but no longer
     * the key the service signs with, another key of its use being the current one), certified, and not
     * revoked yet.
     */
    fun closing(key: WorkingKeyInfo, all: List<WorkingKeyInfo>): Boolean {
        if (key.revokedFrom != null || key.notBefore == null || key.notAfter == null) return false
        val replaced = key.state == WorkingKeyStates.ACTIVE && !key.current && all.any { it.use == key.use && it.current && it.pubkey != key.pubkey }
        return key.state == WorkingKeyStates.EXPIRED || replaced
    }

    /**
     * The effective date of a closing revocation: `last_signed_at + 1` (everything the key signed stays
     * valid, nothing it could sign afterwards is); a key that never signed is revoked from the start of its
     * certificate.
     */
    fun closingDate(key: WorkingKeyInfo): Long = key.lastSignedAt?.plus(1) ?: requireNotNull(key.notBefore)

    /** The revocation of [key] from [effectiveFrom]; null when the key has no readable certificate (nothing trusts it). */
    fun revocation(root: String, key: WorkingKeyInfo, effectiveFrom: Long, now: Long): UnsignedEvent? {
        val use = SigningUse.fromId(key.use) ?: return null
        val notBefore = key.notBefore ?: return null
        val notAfter = key.notAfter ?: return null
        return KeyCertificate.unsigned(root, key.pubkey, listOf(use), notBefore, notAfter, now, KeyStatus.REVOKED, effectiveFrom)
    }
}
