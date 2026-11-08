package org.politisationpopulairefrancaise.core.platform

import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.groups.RoomRole
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The form of the takeover of a room left without administrator, signed by the platform (D-144). */
class GroupTakeoverTest {
    private val root = IdentityKeys.generate()
    private val working = IdentityKeys.generate()
    private val platformKey = working.publicKey.hex
    private val target = IdentityKeys.generate().publicKey.hex
    private val other = IdentityKeys.generate().publicKey.hex
    private val start = 1_793_000_000L
    private val reason = "[EXEMPLE] Le seul administrateur a été déclaré compromis ; le modérateur en place reprend le salon."
    private val use = SigningUse.GROUPS_TAKEOVER

    private val room = listOf("h", "collectif-exemple")
    private val admin = listOf("p", target, "admin")

    private fun event(tags: List<List<String>> = listOf(room, admin), content: String = reason, kind: Int = RoomModeration.PUT_USER) =
        UnsignedEvent(platformKey, start + 100, kind, tags, content)

    @Test
    fun theUseIsHumanOnKind9000AndAlwaysNamesItsAuthor() {
        assertEquals("groups.takeover", use.id)
        assertEquals(setOf(9000), use.kinds)
        assertFalse(use.automatic)
        assertEquals(use, SigningUse.fromId("groups.takeover"))
        assertTrue(use in SigningUse.AUTHOR_REQUIRED_USES)
        assertFalse(DoubleValidation.requiredFor(use, event()), "one approval, the author never being the one who approves")
        assertEquals(RoomRole.ADMIN.id, GroupTakeover.ROLE)
    }

    @Test
    fun aTakeoverOfTheShapeOfThePutUserOfTheRoomsMatches() {
        assertTrue(use.matches(event()))
        assertNull(GroupTakeover.breach(RoomModeration.PUT_USER, listOf(room, admin), reason))
        // The order of the tags does not matter; a reason of 500 characters is the longest.
        assertTrue(use.matches(event(listOf(admin, room))))
        assertTrue(use.matches(event(content = "x".repeat(GroupTakeover.MAX_REASON_LENGTH))))
        // The builder gives the same form as the put-user of the rooms, the reason aside.
        val built = GroupTakeover.unsigned(platformKey, "collectif-exemple", target, "  $reason\n", start + 100)
        assertEquals(event(), built)
        val putUser = RoomModeration.putUser(working.publicKey, "collectif-exemple", target, RoomRole.ADMIN, start + 100)
        assertEquals(putUser.tags, built.tags)
        assertEquals(putUser.kind, built.kind)
        // Without its reason, the put-user of a room member is never a takeover of the platform.
        assertFalse(use.matches(putUser))
        assertEquals(target, GroupTakeover.target(built.tags))
    }

    @Test
    fun everyOtherFormIsRefusedWithTheCodeOfTheContract() {
        val cases = mapOf(
            "no h" to (event(listOf(admin)) to GroupTakeover.Breach(SigningRefusals.MISSING_TAG, "h")),
            "two h" to (event(listOf(room, listOf("h", "autre-salon"), admin)) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "h")),
            "empty h" to (event(listOf(listOf("h", ""), admin)) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "h")),
            "h without value" to (event(listOf(listOf("h"), admin)) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "h")),
            "invalid room id" to (event(listOf(listOf("h", "Salon Exemple"), admin)) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "h")),
            "no p" to (event(listOf(room)) to GroupTakeover.Breach(SigningRefusals.MISSING_TAG, "p")),
            "moderator" to (event(listOf(room, listOf("p", target, "moderator"))) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "p")),
            "no role" to (event(listOf(room, listOf("p", target))) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "p")),
            "role in capitals" to (event(listOf(room, listOf("p", target, "Admin"))) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "p")),
            "fourth element" to (event(listOf(room, listOf("p", target, "admin", "x"))) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "p")),
            "key in capitals" to (event(listOf(room, listOf("p", target.uppercase(), "admin"))) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "p")),
            "two p" to (event(listOf(room, admin, listOf("p", other, "admin"))) to GroupTakeover.Breach(SigningRefusals.BAD_TAG, "p")),
            "extra tag" to (event(listOf(room, admin, listOf("alt", "Nomination"))) to GroupTakeover.Breach(SigningRefusals.TAG_NOT_ALLOWED, "alt")),
            "previous" to (event(listOf(room, admin, listOf("previous", "abcd1234"))) to GroupTakeover.Breach(SigningRefusals.TAG_NOT_ALLOWED, "previous")),
            "empty tag" to (event(listOf(room, admin, emptyList())) to GroupTakeover.Breach(SigningRefusals.TAG_NOT_ALLOWED, null)),
            "empty reason" to (event(content = "") to GroupTakeover.Breach(SigningRefusals.BAD_CONTENT)),
            "blank reason" to (event(content = " \n\t ") to GroupTakeover.Breach(SigningRefusals.BAD_CONTENT)),
            "501 characters" to (event(content = "x".repeat(501)) to GroupTakeover.Breach(SigningRefusals.BAD_CONTENT)),
            "remove-user" to (event(kind = RoomModeration.REMOVE_USER) to GroupTakeover.Breach(SigningRefusals.KIND_NOT_ALLOWED)),
            "chat message" to (event(kind = EventKinds.GROUP_CHAT) to GroupTakeover.Breach(SigningRefusals.KIND_NOT_ALLOWED)),
        )
        for ((name, case) in cases) {
            val (refused, breach) = case
            assertFalse(use.matches(refused), name)
            assertEquals(breach, GroupTakeover.breach(refused.kind, refused.tags, refused.content), name)
        }
        // The form of the kind and tags alone, without its reason, never matches.
        assertFalse(use.matches(RoomModeration.PUT_USER, listOf(room, admin)))
        assertTrue(use.matches(RoomModeration.PUT_USER, listOf(room, admin), reason))
    }

    @Test
    fun noOtherUseTakesATakeoverAndATakeoverTakesNoContentOfAnotherUse() {
        for (each in SigningUse.entries.filter { it != use }) {
            assertFalse(each.matches(event()), "a takeover as ${each.id}")
        }
        val editorial = UnsignedEvent(platformKey, start, EventKinds.LONG_FORM_ARTICLE, listOf(listOf("d", "x"), listOf("title", "[EXEMPLE]"), room, admin), reason)
        assertFalse(use.matches(editorial))
        assertTrue(SigningUse.EDITORIAL_VOIR.matches(editorial), "the editorial form itself only looks at ppf-type")
        assertFalse(use.matches(UnsignedEvent(platformKey, start, EventKinds.LABEL, listOf(listOf("L", "ppf-status"), room, admin), reason)))
    }

    @Test
    fun theBuilderRefusesWhatTheFormRefuses() {
        assertFailsWith<IllegalArgumentException> { GroupTakeover.unsigned(platformKey, "collectif-exemple", platformKey, reason, start) }
        assertFailsWith<IllegalArgumentException> { GroupTakeover.unsigned(platformKey, "collectif-exemple", target, "   ", start) }
        assertFailsWith<IllegalArgumentException> { GroupTakeover.unsigned(platformKey, "collectif-exemple", target, "x".repeat(501), start) }
        assertFailsWith<IllegalArgumentException> { GroupTakeover.unsigned(platformKey, "Salon Exemple", target, reason, start) }
        assertFailsWith<IllegalArgumentException> { GroupTakeover.unsigned(platformKey, "collectif-exemple", "npub1exemple", reason, start) }
    }

    @Test
    fun onlyAKeyCertifiedForTheTakeoverMakesItAContentOfThePlatform() {
        val day = 86_400L
        fun certificate(uses: List<SigningUse>) =
            signWith(root, KeyCertificate.unsigned(root.publicKey.hex, platformKey, uses, start, start + 90 * day, start - day))
        val signed = signWith(working, event())
        val trust = PlatformTrust.of(root.publicKey.hex, listOf(certificate(listOf(use))))
        assertEquals(TrustVerdict.TRUSTED, trust.check(signed, use))
        assertEquals(TrustVerdict.KIND_NOT_ALLOWED, trust.check(signed, SigningUse.EDITORIAL_VOIR))
        // The same key, a 9000 whose reason is missing: not the form of the use.
        assertEquals(TrustVerdict.FORM_OF_ANOTHER_USE, trust.check(signWith(working, event(content = "")), use))
        assertEquals(TrustVerdict.FORM_OF_ANOTHER_USE, trust.check(signWith(working, event(listOf(room, listOf("p", target, "moderator")))), use))
        // A key certified for another use never makes the platform name an administrator.
        val editorialTrust = PlatformTrust.of(root.publicKey.hex, listOf(certificate(listOf(SigningUse.EDITORIAL_VOIR))))
        assertEquals(TrustVerdict.NOT_CERTIFIED_FOR_USE, editorialTrust.check(signed, use))
    }
}
