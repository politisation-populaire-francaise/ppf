package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamListTest {
    private val root = IdentityKeys.generate()
    private val rootHex = root.publicKey.hex
    private val nostr = IdentityKeys.generate().publicKey.hex

    private val alice = TeamMember(
        account = "11111111-1111-1111-1111-111111111111",
        validFrom = 1_793_000_000,
        nostrKey = nostr,
        uses = setOf(SigningUse.EDITORIAL_VOIR, SigningUse.EDITORIAL_COMPRENDRE),
        credentials = listOf(TeamCredential("Y3JlZGVudGlhbC1hbGljZQ", "pQECAyYgASFYIA")),
    )
    private val bob = TeamMember("bob", 1_793_000_000, null, setOf(SigningUse.AGIR_ORGANISATIONS), listOf(TeamCredential("Y3JlZC1ib2I", "pQECAyYgASFY")))

    @Test
    fun aListIsReadBackAsWritten() {
        val event = signWith(root, TeamList.unsigned(rootHex, listOf(alice, bob), 1_793_000_100))
        val list = assertNotNull(TeamList.read(event, rootHex))
        assertEquals(alice, list.members[alice.account])
        assertEquals(bob, list.members["bob"])
        assertEquals(alice to alice.credentials.single(), list.credential("Y3JlZGVudGlhbC1hbGljZQ"))
        assertNull(list.credential("inconnue"))
        assertTrue(alice.mayApprove(SigningUse.EDITORIAL_VOIR, 1_793_000_000))
        assertTrue(!alice.mayApprove(SigningUse.EDITORIAL_VOIR, 1_792_999_999))
        assertTrue(!alice.mayApprove(SigningUse.DECIDER_PACTE, 1_793_000_000))
    }

    @Test
    fun onlyTheRootSignsTheList() {
        val impostor = IdentityKeys.generate()
        val forged = signWith(impostor, TeamList.unsigned(impostor.publicKey.hex, listOf(alice), 1_793_000_100))
        assertNull(TeamList.read(forged, rootHex))
        val tampered = signWith(root, TeamList.unsigned(rootHex, listOf(alice), 1_793_000_100)).copy(createdAt = 1_793_000_200)
        assertNull(TeamList.read(tampered, rootHex))
        val wrongIdentifier = signWith(root, UnsignedEvent(rootHex, 1, EventKinds.PLATFORM_TEAM_LIST, listOf(listOf("d", "autre")), ""))
        assertNull(TeamList.read(wrongIdentifier, rootHex))
    }

    @Test
    fun ambiguitiesDropTheMembersAndPasskeysConcerned() {
        val tags = listOf(
            listOf("d", TeamList.IDENTIFIER),
            listOf("ppf-member", "alice", "10", ""),
            listOf("ppf-member", "alice", "20", ""),
            listOf("ppf-member", "bob", "10", ""),
            listOf("ppf-member-use", "bob", "editorial.voir"),
            listOf("ppf-member-use", "bob", "usage.inconnu"),
            listOf("ppf-credential", "bob", "partagee", "cose"),
            listOf("ppf-credential", "carole", "partagee", "cose"),
            listOf("ppf-credential", "bob", "propre", "cose"),
            listOf("ppf-credential", "bob", "pas=base64", "cose"),
            listOf("ppf-member", "dan", "pas une date", ""),
            listOf("ppf-member", "erin", "10", "pas une clé"),
        )
        val list = assertNotNull(TeamList.read(signWith(root, UnsignedEvent(rootHex, 1, EventKinds.PLATFORM_TEAM_LIST, tags, "")), rootHex))
        assertEquals(setOf("bob"), list.members.keys)
        assertEquals(setOf(SigningUse.EDITORIAL_VOIR), list.members.getValue("bob").uses)
        assertEquals(listOf(TeamCredential("propre", "cose")), list.members.getValue("bob").credentials)
        assertNull(list.credential("partagee"))
    }

    @Test
    fun theLatestListWins() {
        val first = signWith(root, TeamList.unsigned(rootHex, listOf(alice, bob), 100))
        val second = signWith(root, TeamList.unsigned(rootHex, listOf(bob), 200))
        val latest = assertNotNull(TeamList.latest(listOf(second, first), rootHex))
        assertEquals(setOf("bob"), latest.members.keys)
        assertNull(TeamList.latest(emptyList(), rootHex))
        assertFailsWith<IllegalArgumentException> { TeamList.unsigned(rootHex, listOf(bob, bob), 1) }
    }
}
