package org.politisationpopulairefrancaise.core.platform

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RootRequestsTest {
    private val root = IdentityKeys.generate().publicKey.hex
    private val voir = IdentityKeys.generate().publicKey.hex
    private val index = IdentityKeys.generate().publicKey.hex
    private val now = 1_793_000_000L
    private val day = 86_400L
    private val known = mapOf(voir to SigningUse.EDITORIAL_VOIR, index to SigningUse.OPENDATA_INDEX)

    private fun certificate(key: String = voir, use: SigningUse = SigningUse.EDITORIAL_VOIR, notAfter: Long = now + 90 * day) =
        KeyCertificate.unsigned(root, key, listOf(use), now, notAfter, now)

    private fun revocation(key: String = index, from: Long = now - 10) =
        KeyCertificate.unsigned(root, key, listOf(SigningUse.OPENDATA_INDEX), now - 30 * day, now + 60 * day, now, KeyStatus.REVOKED, from)

    private val alice = TeamMember("alice", now, null, setOf(SigningUse.EDITORIAL_VOIR), listOf(TeamCredential("Y3JlZC1hbGljZQ", "pQECAyYgASFYIA")))

    private fun team(members: List<TeamMember> = listOf(alice), notAfter: Long = now + 90 * day) = TeamList.unsigned(root, members, now, notAfter)

    private fun edited(event: UnsignedEvent, tags: List<List<String>> = event.tags, kind: Int = event.kind, content: String = event.content, pubkey: String = event.pubkey, createdAt: Long = event.createdAt) =
        UnsignedEvent(pubkey, createdAt, kind, tags, content)

    private fun code(vararg events: UnsignedEvent): String? = RootRequests.check(events.toList(), root, known::get, now)?.code

    @Test
    fun theThreeFormsAreReadBack() {
        val certificate = assertIs<RootRequestItem.Certificate>(RootRequests.read(certificate(), root))
        assertEquals(voir, certificate.key)
        assertEquals(SigningUse.EDITORIAL_VOIR, certificate.use)
        assertEquals(now + 90 * day, certificate.notAfter)
        val revocation = assertIs<RootRequestItem.Revocation>(RootRequests.read(revocation(), root))
        assertEquals(now - 10, revocation.revokedFrom)
        val team = assertIs<RootRequestItem.Team>(RootRequests.read(team(), root))
        assertEquals(listOf(alice), team.members)
        assertNull(code(certificate(), revocation(), team()))
    }

    @Test
    fun theHashIsTheSha256OfTheCanonicalArray() {
        val events = listOf(certificate(), team())
        val text = "[" + events[0].canonicalJson() + "," + events[1].canonicalJson() + "]"
        assertEquals(text, RootRequests.canonical(events))
        assertEquals(Crypto.sha256(text.encodeToByteArray()).toHexString(), RootRequests.sha256(events))
        assertEquals(64, RootRequests.sha256(events).length)
        // The order is bound too.
        assertEquals(false, RootRequests.sha256(events) == RootRequests.sha256(events.reversed()))
    }

    @Test
    fun everyMalformedRequestIsRefused() {
        assertEquals(SigningRefusals.MALFORMED_REQUEST, RootRequests.check(emptyList(), root, known::get, now)?.code)
        assertEquals(SigningRefusals.MALFORMED_REQUEST, RootRequests.check(List(101) { certificate() }, root, known::get, now)?.code)
        assertEquals(SigningRefusals.NOT_A_ROOT_EVENT, code(edited(certificate(), pubkey = voir)))
        assertEquals(SigningRefusals.NOT_A_ROOT_EVENT, code(edited(certificate(), kind = EventKinds.LONG_FORM_ARTICLE)))
        assertEquals(SigningRefusals.BAD_CONTENT, code(edited(certificate(), content = "x")))
        assertEquals(SigningRefusals.UNKNOWN_KEY, code(certificate(key = IdentityKeys.generate().publicKey.hex)))
        assertEquals(SigningRefusals.WRONG_USES, code(certificate(use = SigningUse.EDITORIAL_COMPRENDRE)))
        val twoUses = certificate().tags.toMutableList().apply { add(1, listOf(KeyCertificate.TAG_USE, "editorial.comprendre")) }
        assertEquals(SigningRefusals.WRONG_USES, code(edited(certificate(), tags = twoUses)))
        assertEquals(SigningRefusals.BAD_VALIDITY, code(edited(certificate(), tags = certificate().tags.map { if (it[0] == KeyCertificate.TAG_NOT_AFTER) listOf(it[0], "${now + 91 * day}") else it })))
        assertEquals(SigningRefusals.BAD_VALIDITY, code(edited(certificate(), tags = certificate().tags.map { if (it[0] == KeyCertificate.TAG_NOT_AFTER) listOf(it[0], "$now") else it })))
        assertEquals(SigningRefusals.BAD_STATUS, code(edited(certificate(), tags = certificate().tags.filter { it[0] != KeyCertificate.TAG_STATUS })))
        assertEquals(SigningRefusals.BAD_STATUS, code(edited(revocation(), tags = revocation().tags.map { if (it[0] == KeyCertificate.TAG_STATUS) it.take(2) else it })))
        assertEquals(SigningRefusals.TAG_NOT_ALLOWED, code(edited(certificate(), tags = certificate().tags + listOf(listOf("p", voir)))))
        assertEquals(SigningRefusals.CREATED_AT_IN_FUTURE, code(edited(certificate(), createdAt = now + 301)))
        assertEquals(SigningRefusals.CREATED_AT_TOO_OLD, code(edited(certificate(), createdAt = now - day - 1)))
        assertEquals(SigningRefusals.DUPLICATE_ADDRESS, code(certificate(), certificate()))
        assertEquals(SigningRefusals.DUPLICATE_ADDRESS, code(team(), team()))
        assertEquals(SigningRefusals.NOT_A_TEAM_LIST, code(edited(team(), tags = team().tags.map { if (it[0] == "d") listOf("d", "autre") else it })))
        assertEquals(SigningRefusals.NOT_A_TEAM_LIST, code(edited(team(), tags = team().tags.map { if (it[0] == TeamList.TAG_NOT_AFTER) listOf(it[0], "${now + 91 * day}") else it })))
        // An automatic use is never given to a member, and nothing would be dropped by the reading of the list.
        val automatic = team().tags + listOf(listOf(TeamList.TAG_MEMBER_USE, "alice", SigningUse.OPENDATA_INDEX.id))
        assertEquals(SigningRefusals.BAD_TAG, code(edited(team(), tags = automatic)))
        val dropped = team().tags + listOf(listOf(TeamList.TAG_MEMBER, "bob", "pas-une-date", ""))
        assertEquals(SigningRefusals.BAD_TAG, code(edited(team(), tags = dropped)))
        val badPasskey = team().tags + listOf(listOf(TeamList.TAG_CREDENTIAL, "alice", "Y3JlZC1hbGljZQ==", "pQECAyYgASFYIA"))
        assertEquals(SigningRefusals.BAD_TAG, code(edited(team(), tags = badPasskey)))
    }

    @Test
    fun theQrTextReadsBack() {
        val id = "0123456789abcdef0123456789abcdef"
        val hash = RootRequests.sha256(listOf(certificate()))
        val qr = RootRequestQr("preproduction", id, hash)
        assertEquals("PPF-RACINE:1:preproduction:$id:$hash", qr.text())
        assertEquals(qr, RootRequestQr.parse(qr.text()))
        assertNotNull(RootRequestQr.parse("PPF-RACINE:1:production:$id:$hash"))
        assertNull(RootRequestQr.parse("PPF-RACINE:2:production:$id:$hash"))
        assertNull(RootRequestQr.parse("PPF-RACINE:1:essai:$id:$hash"))
        assertNull(RootRequestQr.parse("PPF-RACINE:1:production:${id.uppercase()}:$hash"))
        assertNull(RootRequestQr.parse("PPF-RACINE:1:production:$id:${hash.dropLast(1)}"))
        assertNull(RootRequestQr.parse("PPF-RACINE:1:production:$id:$hash:x"))
    }
}
