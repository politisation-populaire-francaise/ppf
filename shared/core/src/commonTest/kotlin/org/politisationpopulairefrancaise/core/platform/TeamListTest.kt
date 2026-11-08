package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamListTest {
    private val root = IdentityKeys.generate()
    private val rootHex = root.publicKey.hex
    private val nostr = IdentityKeys.generate().publicKey.hex
    private val day = 86_400L
    private val created = 1_793_000_100L

    private val alice = TeamMember(
        account = "11111111-1111-1111-1111-111111111111",
        validFrom = 1_793_000_000,
        nostrKey = nostr,
        uses = setOf(SigningUse.EDITORIAL_VOIR, SigningUse.EDITORIAL_COMPRENDRE),
        credentials = listOf(TeamCredential("Y3JlZGVudGlhbC1hbGljZQ", "pQECAyYgASFYIA")),
    )
    private val bob = TeamMember("bob", 1_793_000_000, null, setOf(SigningUse.AGIR_ORGANISATIONS), listOf(TeamCredential("Y3JlZC1ib2I", "pQECAyYgASFY")))

    /** A list whose tags are written by hand, to check the reading of malformed ones. */
    private fun raw(tags: List<List<String>>, createdAt: Long = created, notAfter: Long? = created + day): TeamList? {
        val all = listOf(listOf("d", TeamList.IDENTIFIER)) + listOfNotNull(notAfter?.let { listOf("ppf-not-after", "$it") }) + tags
        return TeamList.read(signWith(root, UnsignedEvent(rootHex, createdAt, EventKinds.PLATFORM_TEAM_LIST, all, "")), rootHex)
    }

    @Test
    fun aListIsReadBackAsWritten() {
        val event = signWith(root, TeamList.unsigned(rootHex, listOf(alice, bob), created, created + 90 * day))
        val list = assertNotNull(TeamList.read(event, rootHex))
        assertEquals(alice, list.members[alice.account])
        assertEquals(bob, list.members["bob"])
        assertEquals(alice to alice.credentials.single(), list.credential("Y3JlZGVudGlhbC1hbGljZQ"))
        assertNull(list.credential("inconnue"))
        assertTrue(alice.mayApprove(SigningUse.EDITORIAL_VOIR, 1_793_000_000))
        assertTrue(!alice.mayApprove(SigningUse.EDITORIAL_VOIR, 1_792_999_999))
        assertTrue(!alice.mayApprove(SigningUse.DECIDER_PACTE, 1_793_000_000))
        assertEquals(created + 90 * day, list.notAfter)
        assertTrue(list.usableAt(created + 90 * day - 1))
        assertFalse(list.usableAt(created + 90 * day))
    }

    @Test
    fun aListWithoutAValidEndIsNeverUsable() {
        val members = listOf(listOf("ppf-member", "bob", "10", ""))
        assertFalse(assertNotNull(raw(members, notAfter = null)).usableAt(created))
        assertNull(raw(members, notAfter = created + 90 * day + 1)?.notAfter)
        assertNull(raw(members, notAfter = created)?.notAfter)
        assertNull(raw(members + listOf(listOf("ppf-not-after", "${created + day}")))?.notAfter, "two ends")
        assertFalse(assertNotNull(raw(members, notAfter = created + day)).usableAt(created + day))
        assertFailsWith<IllegalArgumentException> { TeamList.unsigned(rootHex, listOf(bob), created, created + 90 * day + 1) }
        assertFailsWith<IllegalArgumentException> { TeamList.unsigned(rootHex, listOf(bob), created, created) }
    }

    @Test
    fun onlyTheRootSignsTheList() {
        val impostor = IdentityKeys.generate()
        val forged = signWith(impostor, TeamList.unsigned(impostor.publicKey.hex, listOf(alice), created, created + day))
        assertNull(TeamList.read(forged, rootHex))
        val tampered = signWith(root, TeamList.unsigned(rootHex, listOf(alice), created, created + day)).copy(createdAt = created + 100)
        assertNull(TeamList.read(tampered, rootHex))
        val wrongIdentifier = signWith(root, UnsignedEvent(rootHex, 1, EventKinds.PLATFORM_TEAM_LIST, listOf(listOf("d", "autre")), ""))
        assertNull(TeamList.read(wrongIdentifier, rootHex))
    }

    @Test
    fun ambiguitiesDropTheMembersAndPasskeysConcerned() {
        val list = assertNotNull(
            raw(
                listOf(
                    listOf("ppf-member", "alice", "10", ""),
                    listOf("ppf-member", "alice", "20", ""),
                    listOf("ppf-member", "bob", "10", ""),
                    listOf("ppf-member-use", "bob", "editorial.voir"),
                    listOf("ppf-member-use", "bob", "usage.inconnu"),
                    listOf("ppf-credential", "bob", "partagee", "a2V5LTE"),
                    listOf("ppf-credential", "carole", "partagee", "a2V5LTI"),
                    listOf("ppf-credential", "bob", "cHJvcHJl", "a2V5LTM"),
                    listOf("ppf-credential", "bob", "pas=base64", "a2V5LTQ"),
                    listOf("ppf-member", "dan", "pas une date", ""),
                    listOf("ppf-member", "erin", "10", "pas une clé"),
                ),
            ),
        )
        assertEquals(setOf("bob"), list.members.keys)
        assertEquals(setOf(SigningUse.EDITORIAL_VOIR), list.members.getValue("bob").uses)
        assertEquals(listOf(TeamCredential("cHJvcHJl", "a2V5LTM")), list.members.getValue("bob").credentials)
        assertNull(list.credential("partagee"))
    }

    @Test
    fun aMemberLineTooShortDropsTheAccount() {
        val list = assertNotNull(
            raw(
                listOf(
                    listOf("ppf-member", "frank", "10", ""),
                    listOf("ppf-member", "frank"),
                    listOf("ppf-member", "gina", "10"),
                    listOf("ppf-member"),
                    listOf("ppf-credential", "frank", "ZnJhbms", "a2V5LWY"),
                    listOf("ppf-credential", "gina", "Z2luYQ", "a2V5LWc"),
                ),
            ),
        )
        assertEquals(setOf("gina"), list.members.keys, "a line of three fields (no Nostr key) is enough")
    }

    @Test
    fun credentialIdsAreReadStrictlyAndComparedOnTheirBytes() {
        // "QQ" and "QR" decode to the same byte with a lenient decoder: only the canonical text is accepted.
        val list = assertNotNull(
            raw(
                listOf(
                    listOf("ppf-member", "gina", "10", ""),
                    listOf("ppf-member", "bob", "10", ""),
                    listOf("ppf-credential", "gina", "QQ", "a2V5LWc"),
                    listOf("ppf-credential", "bob", "QR", "a2V5LWI"),
                    listOf("ppf-credential", "bob", "Ym9i", "a2V5LWJi="),
                ),
            ),
        )
        assertEquals(listOf(TeamCredential("QQ", "a2V5LWc")), list.members.getValue("gina").credentials)
        assertEquals(emptyList(), list.members.getValue("bob").credentials)
        assertEquals("gina", list.credential("QQ")?.first?.account)
        assertNull(list.credential("QR"))
    }

    @Test
    fun aPublicKeyListedTwiceDropsEveryAccountThatListsIt() {
        val list = assertNotNull(
            raw(
                listOf(
                    listOf("ppf-member", "harry", "10", ""),
                    listOf("ppf-member", "ivan", "10", ""),
                    listOf("ppf-member", "jane", "10", ""),
                    listOf("ppf-member", "kim", "10", ""),
                    listOf("ppf-credential", "harry", "aGFycnk", "c2hhcmVk"),
                    listOf("ppf-credential", "ivan", "aXZhbg", "c2hhcmVk"),
                    listOf("ppf-credential", "jane", "amFuZS0x", "amFuZQ"),
                    listOf("ppf-credential", "jane", "amFuZS0y", "amFuZQ"),
                    listOf("ppf-credential", "kim", "a2lt", "a2lt"),
                ),
            ),
        )
        assertEquals(setOf("kim"), list.members.keys)
        assertFailsWith<IllegalArgumentException> {
            TeamList.unsigned(rootHex, listOf(bob.copy(credentials = listOf(TeamCredential("aWQx", "c2hhcmVk"))), alice.copy(credentials = listOf(TeamCredential("aWQy", "c2hhcmVk")))), created, created + day)
        }
        assertFailsWith<IllegalArgumentException> {
            TeamList.unsigned(rootHex, listOf(bob.copy(credentials = listOf(TeamCredential("QR", "a2V5")))), created, created + day)
        }
    }

    @Test
    fun theLatestListWins() {
        val first = signWith(root, TeamList.unsigned(rootHex, listOf(alice, bob), 100, 100 + day))
        val second = signWith(root, TeamList.unsigned(rootHex, listOf(bob), 200, 200 + day))
        val latest = assertNotNull(TeamList.latest(listOf(second, first), rootHex))
        assertEquals(setOf("bob"), latest.members.keys)
        assertNull(TeamList.latest(emptyList(), rootHex))
        assertFailsWith<IllegalArgumentException> { TeamList.unsigned(rootHex, listOf(bob, bob), 1, 2) }
    }

    @Test
    fun aPasskeyHasAShortFingerprintToCompareByEye() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals("ba78 16bf 8f01 cfea", PasskeyFingerprint.of("abc".encodeToByteArray()))
        assertEquals("ba78 16bf 8f01 cfea", TeamCredential("aWQ", "YWJj").fingerprint())
        assertNull(TeamCredential("aWQ", "YWJk=").fingerprint())
    }

    @Test
    fun canonicalBase64UrlIsTheOnlyAcceptedForm() {
        assertContentEquals(byteArrayOf(0x41), decodeCanonicalBase64Url("QQ"))
        assertNull(decodeCanonicalBase64Url("QR"))
        assertNull(decodeCanonicalBase64Url("QQ=="))
        assertNull(decodeCanonicalBase64Url("Q"))
        assertNull(decodeCanonicalBase64Url(""))
        assertNull(decodeCanonicalBase64Url("a+b/"))
        assertNull(decodeCanonicalBase64Url("YWJj", maxLength = 3))
    }
}
