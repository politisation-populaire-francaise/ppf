package org.politisationpopulairefrancaise.core.groups

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The roles, exclusions, edits and removals of a room computed on the device (D-131). */
class RoomModerationTest {

    private val creator = LocalEventSigner(IdentityKeys.generate())
    private val alice = LocalEventSigner(IdentityKeys.generate())
    private val bob = LocalEventSigner(IdentityKeys.generate())
    private val carol = LocalEventSigner(IdentityKeys.generate())
    private var clock = 1_000L

    private suspend fun room(): Group =
        Group.from(creator.sign(Group.build(creator.publicKey, ROOM, "[EXEMPLE] Collectif Logement", "[EXEMPLE] Texte", "Lyon", createdAt = ++clock)))!!

    private suspend fun put(by: LocalEventSigner, target: LocalEventSigner, role: RoomRole?) =
        by.sign(RoomModeration.putUser(by.publicKey, ROOM, target.publicKey.hex, role, ++clock))

    private suspend fun exclude(by: LocalEventSigner, target: LocalEventSigner, reason: String = "[EXEMPLE] Hors charte") =
        by.sign(RoomModeration.removeUser(by.publicKey, ROOM, target.publicKey.hex, reason, ++clock))

    private suspend fun remove(by: LocalEventSigner, message: SignedEvent, reason: String = "[EXEMPLE] Hors sujet") =
        by.sign(RoomModeration.deleteEvent(by.publicKey, ROOM, message.id, reason, ++clock))

    private suspend fun message(by: LocalEventSigner) = by.sign(GroupMessage.build(by.publicKey, ROOM, "[EXEMPLE] Bonjour", ++clock))

    @Test
    fun theCreatorIsTheFirstAdministratorAndNamesOthers() = runTest {
        val group = room()
        val events = listOf(put(creator, alice, RoomRole.ADMIN), put(alice, bob, RoomRole.MODERATOR), put(bob, carol, RoomRole.MODERATOR))
        val state = RoomModeration.compute(group, events)
        assertEquals(RoomRole.ADMIN, state.roleOf(creator.publicKey.hex))
        assertEquals(RoomRole.ADMIN, state.roleOf(alice.publicKey.hex))
        assertEquals(RoomRole.MODERATOR, state.roleOf(bob.publicKey.hex))
        assertNull(state.roleOf(carol.publicKey.hex), "a moderator names nobody")
        assertEquals(2, state.journal.size, "an event the server would refuse is not in the journal")
        assertTrue(state.canNameRoles(alice.publicKey.hex))
        assertFalse(state.canNameRoles(bob.publicKey.hex))
    }

    @Test
    fun theLastAdministratorKeepsTheRoleAndTheOrderIsByDate() = runTest {
        val group = room()
        val giveUp = put(creator, creator, null)
        val state = RoomModeration.compute(group, listOf(giveUp, exclude(creator, creator)))
        assertEquals(RoomRole.ADMIN, state.roleOf(creator.publicKey.hex))
        assertTrue(state.journal.isEmpty())
        assertFalse(state.canRemoveRole(creator.publicKey.hex, creator.publicKey.hex))
        assertFalse(state.canExclude(creator.publicKey.hex, creator.publicKey.hex))

        // Received in any order, the events are taken by date: naming Alice first lets the creator step down.
        val named = put(creator, alice, RoomRole.ADMIN)
        val stepDown = put(creator, creator, null)
        val after = RoomModeration.compute(group, listOf(stepDown, named))
        assertEquals(listOf(alice.publicKey.hex), after.admins)
    }

    @Test
    fun exclusionReadmissionAndRemovalOfMessages() = runTest {
        val group = room()
        val spam = message(carol)
        val own = message(alice)
        val events = listOf(
            put(creator, bob, RoomRole.MODERATOR),
            exclude(bob, carol),
            exclude(bob, creator), // refused: a moderator does not exclude an administrator
            remove(bob, spam),
            remove(alice, own, "[EXEMPLE] Erreur"),
            remove(carol, own), // refused: neither moderator nor author
        )
        val authors = mapOf(spam.id to carol.publicKey.hex, own.id to alice.publicKey.hex)
        val state = RoomModeration.compute(group, events, authors::get)
        assertTrue(state.isBanned(carol.publicKey.hex))
        assertEquals("[EXEMPLE] Hors charte", state.banned[carol.publicKey.hex])
        assertFalse(state.isBanned(creator.publicKey.hex))
        assertEquals(setOf(spam.id, own.id), state.removed.keys)
        assertEquals(bob.publicKey.hex, state.removed.getValue(spam.id).by)
        assertTrue(state.removed.getValue(own.id).byAuthor)
        assertTrue(state.canRemove(alice.publicKey.hex, alice.publicKey.hex))
        assertFalse(state.canRemove(alice.publicKey.hex, carol.publicKey.hex))
        assertTrue(state.canReadmit(bob.publicKey.hex, carol.publicKey.hex))

        val readmitted = RoomModeration.compute(group, events + put(bob, carol, null), authors::get)
        assertFalse(readmitted.isBanned(carol.publicKey.hex))
        assertIs<ModerationAction.Readmitted>(readmitted.journal.last().action)
    }

    @Test
    fun anAdministratorEditsTheNameAndPresentationOnly() = runTest {
        val group = room()
        val rename = creator.sign(RoomModeration.editMetadata(creator.publicKey, ROOM, "[EXEMPLE] Logement digne", null, ++clock))
        val about = creator.sign(RoomModeration.editMetadata(creator.publicKey, ROOM, null, "[EXEMPLE] Texte revu", ++clock))
        val foreign = alice.sign(RoomModeration.editMetadata(alice.publicKey, ROOM, "[EXEMPLE] Pris", null, ++clock))
        val state = RoomModeration.compute(group, listOf(rename, about, foreign))
        val shown = state.applyTo(group)
        assertEquals("[EXEMPLE] Logement digne", shown.name)
        assertEquals("[EXEMPLE] Texte revu", shown.about)
        assertEquals("Lyon", shown.territory)
        assertFailsWith<IllegalArgumentException> { RoomModeration.editMetadata(creator.publicKey, ROOM, null, null, 1) }
    }

    @Test
    fun buildersRequireAReasonAndValidKeys() {
        assertFailsWith<IllegalArgumentException> { RoomModeration.removeUser(creator.publicKey, ROOM, alice.publicKey.hex, "  ", 1) }
        assertFailsWith<IllegalArgumentException> { RoomModeration.deleteEvent(creator.publicKey, ROOM, "abc", "motif", 1) }
        assertFailsWith<IllegalArgumentException> { RoomModeration.putUser(creator.publicKey, ROOM, "pas une clé", null, 1) }
        val put = RoomModeration.putUser(creator.publicKey, ROOM, alice.publicKey.hex, RoomRole.MODERATOR, 1)
        assertEquals(listOf(listOf("h", ROOM), listOf("p", alice.publicKey.hex, "moderator")), put.tags)
        assertEquals(RoomModeration.PUT_USER, put.kind)
    }

    @Test
    fun aConfirmedCreationCarriesItsDifference() {
        val event = Group.build(creator.publicKey, ROOM, "[EXEMPLE] Santé", null, "Lyon", createdAt = 1, difference = " [EXEMPLE] Les hôpitaux ")
        assertTrue(listOf(Group.TAG_SIMILAR, Group.SIMILAR_CONFIRMED) in event.tags)
        assertTrue(listOf(Group.TAG_DIFFERENCE, "[EXEMPLE] Les hôpitaux") in event.tags)
        val plain = Group.build(creator.publicKey, ROOM, "[EXEMPLE] Santé", null, "Lyon", createdAt = 1)
        assertTrue(plain.tags.none { it.first() == Group.TAG_SIMILAR })
    }

    @Test
    fun theServerRefusalsOfTheGuardrailsAreRead() {
        val similar = ServerPublicationPort.rejection(
            "x",
            """{"error":"group_similar","message":"m","similar":[{"id":"sante-lyon","name":"[EXEMPLE] Santé","territory":"Lyon"},{"id":"Pas valide"}]}""",
        )
        assertEquals(RejectionReason.GROUP_SIMILAR, similar.reason)
        assertEquals(listOf("sante-lyon"), similar.similar.map { it.id })
        assertEquals("Lyon", similar.similar.single().territory)
        for ((code, reason) in listOf("last_admin" to RejectionReason.LAST_ADMIN, "banned" to RejectionReason.BANNED, "not_allowed" to RejectionReason.NOT_ALLOWED, "too_many_groups" to RejectionReason.TOO_MANY_GROUPS, "account_too_recent" to RejectionReason.ACCOUNT_TOO_RECENT)) {
            assertEquals(reason, ServerPublicationPort.rejection("x", """{"error":"$code","message":"m"}""").reason)
        }
    }

    private companion object {
        const val ROOM = "logement-lyon"
    }
}
