package org.politisationpopulairefrancaise.server.groups

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The comparison of room names and the hold of a name close to a very active room elsewhere (D-132). */
class GroupGuardrailsTest {

    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val other = LocalEventSigner(IdentityKeys.generate())
    private var clock = 1_000L

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun words(name: String, territory: String = "") = GroupSimilarity.significantWords(GroupNames.normalize(name), GroupNames.normalize(territory))

    private fun close(a: String, b: String, territory: String = "") = GroupSimilarity.similar(words(a, territory), words(b, territory))

    @Test
    fun namesAreCloseOnceTheEmptyWordsAndTheTerritoryAreSetAside() {
        assertEquals(listOf("sante"), words("[EXEMPLE] Collectif de la Santé Lyon", "Lyon"))
        assertEquals(listOf("exemple", "collectif"), words("[EXEMPLE] Collectif"), "nothing meaningful left: every word counts")
        assertTrue(close("Collectif Santé", "Santé"))
        assertTrue(close("Logement digne", "Digne logement"), "same words in another order")
        assertTrue(close("Logement digne", "Logements dignes"), "two letters on a long name")
        assertTrue(close("Collectif Santé", "Collectif Santee"), "one letter on a short name")
        assertFalse(close("Vélo", "Velos"), "names under five letters must be equal")
        assertFalse(close("Collectif Santé", "Collectif Logement"))
        assertFalse(close("Écoles", "Transports"))
        assertTrue(close("Collectif Santé Lyon", "Santé", territory = "Lyon"))
    }

    @Test
    fun aRoomResemblesAnOrganisationWhenItContainsItsName() {
        val cgt = words("CGT")
        assertTrue(GroupSimilarity.resemblesOrganisation(words("Comité CGT santé"), cgt))
        assertTrue(GroupSimilarity.resemblesOrganisation(words("Union des locataires de Lyon", "Lyon"), words("Union des Locataires")))
        assertFalse(GroupSimilarity.resemblesOrganisation(words("Union des cyclistes"), words("Union des Locataires")))
        assertFalse(GroupSimilarity.resemblesOrganisation(words("AB santé"), words("AB")), "a two-letter name holds back nothing")
    }

    @Test
    fun theEditDistanceStopsAtItsLimit() {
        assertEquals(0, GroupSimilarity.distance("abc", "abc", 2))
        assertEquals(1, GroupSimilarity.distance("sante", "santee", 2))
        assertEquals(3, GroupSimilarity.distance("abcdef", "uvwxyz", 2), "beyond the limit: limit + 1")
    }

    private fun room(signer: LocalEventSigner, id: String, name: String, territory: String): SignedEvent = runBlocking {
        signer.sign(
            kind = GroupKinds.METADATA,
            tags = listOf(listOf("d", id), listOf("name", name), listOf(GroupKinds.TERRITORY_TAG, territory), listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION)),
            createdAt = ++clock,
        )
    }

    private fun message(signer: LocalEventSigner, groupId: String): SignedEvent = runBlocking {
        signer.sign(kind = GroupKinds.CHAT_MESSAGE, content = "[EXEMPLE] Message", tags = listOf(listOf("h", groupId)), createdAt = ++clock)
    }

    @Test
    fun aNameCloseToAVeryActiveRoomOfAnotherTerritoryIsHeld() {
        val repository = EventRepository(TestDatabase.dataSource)
        val directory = GroupDirectory(repository, guardrails = GroupGuardrails(TestDatabase.dataSource, activeRoomMessages = 2))
        assertIs<GroupDirectory.Admission.Stored>(directory.admitAndStore(room(citizen, "greve-paris", "[EXEMPLE] Grève générale", "Paris")))
        assertIs<GroupDirectory.Admission.Stored>(directory.admitAndStore(room(citizen, "velo-paris", "[EXEMPLE] Vélo", "Paris")))
        // A quiet room elsewhere holds nothing back.
        assertIs<GroupDirectory.Admission.Stored>(directory.admitAndStore(room(other, "velo-lyon", "[EXEMPLE] Vélo", "Lyon")))

        repeat(2) { assertIs<GroupDirectory.Admission.Stored>(directory.admitAndStore(message(other, "greve-paris"))) }
        val copy = room(other, "greve-lyon", "[EXEMPLE] Grève générale", "Lyon")
        val held = assertIs<GroupDirectory.Admission.Held>(directory.admitAndStore(copy))
        assertEquals(copy.id, held.id)
        val pending = directory.guardrails.creations.find(copy.id)!!
        assertEquals(HoldCause.ACTIVE_ROOM, pending.cause)
        assertEquals(listOf("[EXEMPLE] Grève générale (Paris)"), pending.resembles)
        assertEquals(null, directory.find("greve-lyon"))
        assertEquals(GroupDirectory.CreationDecision.Done, directory.approveCreation(copy.id))
        assertEquals("greve-lyon", directory.find("greve-lyon")?.id)
        assertEquals(listOf(other.publicKey.hex), directory.moderation.roles("greve-lyon").roles.map { it.pubkey }, "the creator of an approved room is its administrator")
    }
}
