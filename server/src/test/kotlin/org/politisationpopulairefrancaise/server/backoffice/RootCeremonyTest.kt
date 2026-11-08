package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.KeyCertificate
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.platform.PasskeyFingerprint
import org.politisationpopulairefrancaise.core.platform.RootRequestItem
import org.politisationpopulairefrancaise.core.platform.RootRequests
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.TeamList
import org.politisationpopulairefrancaise.core.platform.TeamMember
import org.politisationpopulairefrancaise.core.platform.WorkingKeyInfo
import org.politisationpopulairefrancaise.core.platform.WorkingKeyStates
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** The composition of the ceremony of the root (D-141), without database nor service. */
class RootCeremonyTest {
    private val root = IdentityKeys.generate()
    private val rootHex = root.publicKey.hex
    private val day = 86_400L
    private val now = 1_793_000_123L
    private val start = 1_793_000_100L

    private fun key(use: SigningUse, state: String, current: Boolean = false, notBefore: Long? = now - 80 * day, lastSignedAt: Long? = null, revokedFrom: Long? = null) =
        WorkingKeyInfo(use.id, IdentityKeys.generate().publicKey.hex, state, 1, current, notBefore, notBefore?.plus(90 * day), revokedFrom, lastSignedAt)

    private fun candidate(pseudonym: String, roles: Set<TeamRole>, keys: Int = 2, nostr: String? = null, cose: List<ByteArray>? = null): TeamListCandidate {
        val id = UUID.randomUUID()
        val account = TeamAccount(id, pseudonym, nostr, Tokens.randomBytes(32), AccountStatus.ACTIVE, Instant.ofEpochSecond(now - 100 * day), null, null)
        val passkeys = (cose ?: List(keys) { Tokens.randomBytes(77) }).mapIndexed { index, bytes -> CandidateKey(UUID.randomUUID(), "Clé $index", Tokens.randomBytes(32), bytes) }
        return TeamListCandidate(account, TeamActor.of(id, pseudonym, roles, passkeys.size), passkeys)
    }

    @Test
    fun theMappingNamesEveryHumanUseOnce() {
        assertEquals(SigningUse.entries.filter { !it.automatic }.toSet(), SigningApprovals.PERMISSIONS.keys)
        assertEquals("queue.editorial.comprendre.publish", SigningApprovals.PERMISSIONS[SigningUse.EDITORIAL_COMPRENDRE])
        assertEquals("queue.editorial.voir.publish", SigningApprovals.PERMISSIONS[SigningUse.EDITORIAL_VOIR])
        assertEquals("queue.agir.organisations.decide", SigningApprovals.PERMISSIONS[SigningUse.AGIR_ORGANISATIONS])
        assertEquals("queue.revendications.proposals.decide", SigningApprovals.PERMISSIONS[SigningUse.REVENDICATIONS_STATUS])
        assertEquals(Permissions.DECIDER_PACTE_PUBLISH, SigningApprovals.PERMISSIONS[SigningUse.DECIDER_PACTE])
        // D-144: the takeover of a room left without administrator, by the members of the queue groups.orphans.
        assertEquals("queue.groups.orphans.decide", SigningApprovals.PERMISSIONS[SigningUse.GROUPS_TAKEOVER])
        assertTrue(Permissions.queueDecide("groups.orphans") in TeamRole.MODERATOR.permissions)
        assertEquals(setOf(SigningUse.REVENDICATIONS_STATUS, SigningUse.GROUPS_TAKEOVER), SigningApprovals.usesOf(TeamActor.of(UUID.randomUUID(), "x", setOf(TeamRole.MODERATOR), 2)))
        assertEquals(setOf(SigningUse.EDITORIAL_COMPRENDRE, SigningUse.EDITORIAL_VOIR), SigningApprovals.usesOf(TeamActor.of(UUID.randomUUID(), "x", setOf(TeamRole.EDITOR), 2)))
        assertTrue(SigningApprovals.usesOf(TeamActor.of(UUID.randomUUID(), "x", setOf(TeamRole.MODERATOR), 1)).isEmpty(), "a single passkey: no queue permission, no use")
        assertTrue(Permissions.PLATFORM_KEYS in TeamRole.ADMIN.permissions)
        assertTrue(TeamActor.of(UUID.randomUUID(), "x", setOf(TeamRole.SUPER_ADMIN), 2).has(Permissions.PLATFORM_KEYS))
        assertTrue(!TeamActor.of(UUID.randomUUID(), "x", setOf(TeamRole.EDITOR), 2).has(Permissions.PLATFORM_KEYS))
    }

    @Test
    fun oneCeremonyAsksForEveryCertificateEveryClosingRevocationAndANewTeamList() {
        val awaiting = key(SigningUse.EDITORIAL_VOIR, WorkingKeyStates.AWAITING_CERTIFICATION, notBefore = null)
        val currentComprendre = key(SigningUse.EDITORIAL_COMPRENDRE, WorkingKeyStates.ACTIVE, current = true, notBefore = now - 10 * day)
        val replaced = key(SigningUse.EDITORIAL_COMPRENDRE, WorkingKeyStates.ACTIVE, lastSignedAt = now - 20 * day)
        val expired = key(SigningUse.OPENDATA_INDEX, WorkingKeyStates.EXPIRED, notBefore = now - 95 * day)
        val closed = key(SigningUse.ARCHIVES_TIMESTAMPS, WorkingKeyStates.REVOKED, notBefore = now - 95 * day, revokedFrom = now - 6 * day)
        val expiredClosed = key(SigningUse.DECIDER_PACTE, WorkingKeyStates.EXPIRED, notBefore = now - 95 * day, revokedFrom = now - 50 * day)
        val scheduled = key(SigningUse.REVENDICATIONS_STATUS, WorkingKeyStates.SCHEDULED, notBefore = now + day)
        val keys = KeysResponse(true, keys = listOf(awaiting, currentComprendre, replaced, expired, closed, expiredClosed, scheduled))

        val nostr = IdentityKeys.generate().publicKey.hex
        val editor = candidate("Éditrice", setOf(TeamRole.EDITOR), nostr = nostr)
        val moderator = candidate("Modérateur", setOf(TeamRole.MODERATOR))
        val owner = candidate("Propriétaire", setOf(TeamRole.SUPER_ADMIN))
        val verifier = candidate("Vérificatrice", setOf(TeamRole.ORGANISATION_VERIFIER))
        val singleKey = candidate("Une seule clé", setOf(TeamRole.EDITOR), keys = 1)
        val noKey = candidate("Sans clé", setOf(TeamRole.EDITOR), keys = 0)
        val validator = candidate("Validateur", setOf(TeamRole.VALIDATOR))
        val shared = Tokens.randomBytes(77)
        val sharingA = candidate("Partage A", setOf(TeamRole.EDITOR), cose = listOf(shared, Tokens.randomBytes(77)))
        val sharingB = candidate("Partage B", setOf(TeamRole.MODERATOR), cose = listOf(shared, Tokens.randomBytes(77)))
        // The editor is already in the signed list: their approvals keep counting from the same date.
        val current = TeamList.read(
            LocalEventSigner(root).signNow(TeamList.unsigned(rootHex, listOf(TeamMember(editor.account.id.toString(), now - 30 * day, nostr, setOf(SigningUse.EDITORIAL_VOIR), editor.keys.map { it.credential })), now - 40 * day, now + 50 * day)),
            rootHex,
        )

        val plan = RootCeremony.compose(rootHex, keys, listOf(editor, moderator, owner, verifier, singleKey, noKey, validator, sharingA, sharingB), current, now)

        val certificate = assertIs<RootRequestItem.Certificate>(RootRequests.read(plan.certificates.single(), rootHex))
        assertEquals(awaiting.pubkey, certificate.key)
        assertEquals(SigningUse.EDITORIAL_VOIR, certificate.use)
        assertEquals(start, certificate.notBefore, "now rounded down to the minute")
        assertEquals(start + 90 * day, certificate.notAfter)
        assertEquals(now, plan.certificates.single().createdAt)

        val revocations = plan.revocations.map { assertIs<RootRequestItem.Revocation>(RootRequests.read(it, rootHex)) }.associateBy { it.key }
        assertEquals(setOf(replaced.pubkey, expired.pubkey), revocations.keys, "replaced and expired keys, never one already revoked")
        assertEquals(now - 20 * day + 1, revocations.getValue(replaced.pubkey).revokedFrom, "last_signed_at + 1")
        assertEquals(expired.notBefore, revocations.getValue(expired.pubkey).revokedFrom, "a key that never signed is revoked from its start")
        assertEquals(replaced.notAfter, revocations.getValue(replaced.pubkey).notAfter)

        // The team list reads back with TeamList, as the service will read it once signed by the root.
        val list = assertNotNull(TeamList.read(LocalEventSigner(root).signNow(assertNotNull(plan.teamList)), rootHex))
        assertEquals(now + 90 * day, list.notAfter)
        assertEquals(setOf(editor, moderator, owner, verifier).map { it.account.id.toString() }.toSet(), list.members.keys)
        val listedEditor = list.members.getValue(editor.account.id.toString())
        assertEquals(now - 30 * day, listedEditor.validFrom, "validFrom kept from the current list")
        assertEquals(nostr, listedEditor.nostrKey)
        assertEquals(setOf(SigningUse.EDITORIAL_COMPRENDRE, SigningUse.EDITORIAL_VOIR), listedEditor.uses)
        assertEquals(editor.keys.map { it.credential }, listedEditor.credentials)
        assertEquals(editor.keys.map { PasskeyFingerprint.of(it.coseKey) }, listedEditor.credentials.map { it.fingerprint() })
        assertEquals(now, list.members.getValue(moderator.account.id.toString()).validFrom, "a new member counts from now")
        assertNull(list.members.getValue(moderator.account.id.toString()).nostrKey)
        // The moderator approves the statuses of the demands and the takeovers of rooms (D-144); the editor never a takeover.
        assertEquals(setOf(SigningUse.REVENDICATIONS_STATUS, SigningUse.GROUPS_TAKEOVER), list.members.getValue(moderator.account.id.toString()).uses)
        assertTrue(SigningUse.GROUPS_TAKEOVER !in listedEditor.uses)
        assertEquals(setOf(SigningUse.AGIR_ORGANISATIONS), list.members.getValue(verifier.account.id.toString()).uses)
        assertEquals(SigningUse.entries.filter { !it.automatic }.toSet(), list.members.getValue(owner.account.id.toString()).uses)
        assertTrue(list.members.values.none { member -> member.uses.any { it.automatic } }, "an automatic use is never approved by anyone")
        assertEquals(
            mapOf(
                singleKey.account.pseudonym to Exclusion.NO_USE,
                noKey.account.pseudonym to Exclusion.NO_PASSKEY,
                validator.account.pseudonym to Exclusion.NO_USE,
                sharingA.account.pseudonym to Exclusion.SHARED_PASSKEY,
                sharingB.account.pseudonym to Exclusion.SHARED_PASSKEY,
            ),
            plan.excluded.associate { (candidate, why) -> candidate.account.pseudonym to why },
        )

        // The whole request is one the signing service accepts.
        val uses = keys.keys.associate { it.pubkey to SigningUse.fromId(it.use)!! }
        assertNull(RootRequests.check(plan.events, rootHex, uses::get, now))
        assertEquals(KeyCertificate.MAX_VALIDITY_SECONDS, certificate.notAfter - certificate.notBefore)
    }

    @Test
    fun withoutAnyListableAccountTheListStaysAsItIs() {
        val plan = RootCeremony.compose(rootHex, KeysResponse(true, keys = emptyList()), listOf(candidate("Sans clé", setOf(TeamRole.EDITOR), keys = 0)), null, now)
        assertNull(plan.teamList)
        assertTrue(plan.events.isEmpty())
    }
}

/** The tests sign as the root outside any coroutine (the signer of the core suspends). */
private fun LocalEventSigner.signNow(event: UnsignedEvent): SignedEvent = runBlocking { sign(event) }
