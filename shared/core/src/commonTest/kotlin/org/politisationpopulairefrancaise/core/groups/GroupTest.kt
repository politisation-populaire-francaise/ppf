package org.politisationpopulairefrancaise.core.groups

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupTest {

    private val signer = LocalEventSigner(IdentityKeys.generate())

    @Test
    fun slugifyDerivesTheIdFromTheName() {
        assertEquals("collectif-de-demonstration", Group.slugify("[EXEMPLE] Collectif de démonstration"))
        assertEquals("ca-ira-mieux-demain", Group.slugify("  Ça ira mieux demain !  "))
        assertEquals("assemblee-du-13e-paris", Group.slugify("Assemblée du 13e (Paris)"))
        assertEquals("", Group.slugify("###"))
        assertEquals(Group.MAX_ID_LENGTH, Group.slugify("a".repeat(100)).length)
        // The cut never ends with a hyphen.
        assertFalse(Group.slugify("a".repeat(63) + " b").endsWith("-"))
        assertTrue(Group.isValidId("collectif-demonstration"))
        assertFalse(Group.isValidId("Collectif"))
        assertFalse(Group.isValidId(""))
        assertFalse(Group.isValidId("a b"))
    }

    @Test
    fun theIdOfANewRoomJoinsItsNameAndTerritory() {
        assertEquals("collectif-sante-lyon", Group.idFor("[EXEMPLE] Collectif santé", " Lyon "))
        assertEquals("collectif-sante-saint-etienne", Group.idFor("Collectif santé", "Saint-Étienne"))
        // The territory is not repeated when the name already ends with it.
        assertEquals("assemblee-paris", Group.idFor("Assemblée Paris", "Paris"))
        assertEquals("france", Group.idFor("France", "France"))
        assertEquals("", Group.idFor("!!!", "Lyon"))
        val long = Group.idFor("a".repeat(60), "Lyon")
        assertTrue(long.length <= Group.MAX_ID_LENGTH && Group.isValidId(long) && !long.endsWith("-"))

        assertEquals("collectif-sante-lyon-2", Group.numbered("collectif-sante-lyon", 2))
        val numbered = Group.numbered("a".repeat(Group.MAX_ID_LENGTH), 3)
        assertEquals(Group.MAX_ID_LENGTH, numbered.length)
        assertTrue(numbered.endsWith("-3") && Group.isValidId(numbered))
    }

    @Test
    fun aDescriptionRoundTripsThroughBuildAndFrom() = runTest {
        val unsigned = Group.build(signer.publicKey, "collectif-demonstration", " [EXEMPLE] Collectif de démonstration ", "  Un salon pour essayer. ", " Lyon ", createdAt = 1_700_000_000)
        assertEquals(EventKinds.GROUP_METADATA, unsigned.kind)
        assertEquals(
            listOf(
                listOf("d", "collectif-demonstration"),
                listOf("name", "[EXEMPLE] Collectif de démonstration"),
                listOf("about", "Un salon pour essayer."),
                listOf("ppf-territory", "Lyon"),
                listOf("ppf-charter", "1.0"),
            ),
            unsigned.tags,
        )
        val group = assertNotNull(Group.from(signer.sign(unsigned)))
        assertEquals("collectif-demonstration", group.id)
        assertEquals("[EXEMPLE] Collectif de démonstration", group.name)
        assertEquals("Collectif de démonstration", group.displayName)
        assertTrue(group.example)
        assertEquals("Un salon pour essayer.", group.about)
        assertNull(group.picture)
        assertEquals("Lyon", group.territory)
        assertEquals("1.0", group.charter)
        assertTrue(group.public)
        assertTrue(group.open)

        // Without a description the about tag is absent.
        val bare = Group.build(signer.publicKey, "x", "X", null, "France", createdAt = 1)
        assertEquals(listOf(listOf("d", "x"), listOf("name", "X"), listOf("ppf-territory", "France"), listOf("ppf-charter", "1.0")), bare.tags)
    }

    @Test
    fun buildRefusesWhatTheServerWouldRefuse() {
        val author = signer.publicKey
        assertFailsWith<IllegalArgumentException> { Group.build(author, "Majuscules", "Nom", null, "Lyon", createdAt = 1) }
        assertFailsWith<IllegalArgumentException> { Group.build(author, "", "Nom", null, "Lyon", createdAt = 1) }
        assertFailsWith<IllegalArgumentException> { Group.build(author, "x", "  ", null, "Lyon", createdAt = 1) }
        assertFailsWith<IllegalArgumentException> { Group.build(author, "x", "Nom", null, " ", createdAt = 1) }
        assertFailsWith<IllegalArgumentException> { Group.build(author, "x", "n".repeat(Group.MAX_NAME_LENGTH + 1), null, "Lyon", createdAt = 1) }
        assertFailsWith<IllegalArgumentException> { Group.build(author, "x", "Nom", "a".repeat(Group.MAX_ABOUT_LENGTH + 1), "Lyon", createdAt = 1) }
        assertFailsWith<IllegalArgumentException> { Group.build(author, "x", "Nom", null, "Lyon", charter = " ", createdAt = 1) }
    }

    @Test
    fun fromReadsFlagsAndRejectsMalformedDescriptions() = runTest {
        val closed = signer.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "ferme"), listOf("name", "Fermé"), listOf("closed")))
        val readClosed = assertNotNull(Group.from(closed))
        assertFalse(readClosed.open)
        assertTrue(readClosed.public)
        val restricted = signer.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "r"), listOf("restricted")))
        assertFalse(assertNotNull(Group.from(restricted)).open)
        val private = signer.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "p"), listOf("private")))
        val readPrivate = assertNotNull(Group.from(private))
        assertFalse(readPrivate.public)
        assertEquals("p", readPrivate.name) // the name falls back to the id

        assertNull(Group.from(signer.sign(kind = EventKinds.TEXT_NOTE, tags = listOf(listOf("d", "x")))))
        assertNull(Group.from(signer.sign(kind = EventKinds.GROUP_METADATA)))
        assertNull(Group.from(signer.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "a"), listOf("d", "b")))))
        assertNull(Group.from(signer.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "Not Valid")))))
    }

    @Test
    fun duplicatesIgnoreAccentsCaseAndPunctuation() = runTest {
        val sampleEvent = signer.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "x")))
        fun room(id: String, name: String, territory: String?) = Group(id, name, null, null, territory, "1.0", true, true, event = sampleEvent)
        assertTrue(Group.sameNameAndTerritory(room("a", "Collectif du 13e", "Paris"), room("b", "collectif  du 13E !", "PARIS")))
        assertTrue(Group.sameNameAndTerritory(room("a", "Assemblée", "Lyon"), room("b", "Assemblee", "lyon")))
        assertFalse(Group.sameNameAndTerritory(room("a", "Collectif", "Paris"), room("b", "Collectif", "Lyon")))
        assertFalse(Group.sameNameAndTerritory(room("a", "Collectif", "Paris"), room("b", "Collectif 2", "Paris")))
    }

    @Test
    fun aMessageRoundTripsAndIsValidated() = runTest {
        val unsigned = GroupMessage.build(signer.publicKey, "collectif-demonstration", "  [EXEMPLE] Bonjour à tous ", createdAt = 42)
        assertEquals(EventKinds.GROUP_CHAT, unsigned.kind)
        assertEquals(listOf(listOf("h", "collectif-demonstration")), unsigned.tags)
        assertEquals("[EXEMPLE] Bonjour à tous", unsigned.content)
        val signed = signer.sign(unsigned)
        val message = assertNotNull(GroupMessage.from(signed))
        assertEquals(signed.id, message.id)
        assertEquals("collectif-demonstration", message.groupId)
        assertEquals(signer.publicKey.hex, message.author)
        assertEquals("[EXEMPLE] Bonjour à tous", message.text)
        assertEquals(42, message.createdAt)

        assertFailsWith<IllegalArgumentException> { GroupMessage.build(signer.publicKey, "Bad Id", "x", createdAt = 1) }
        assertFailsWith<IllegalArgumentException> { GroupMessage.build(signer.publicKey, "ok", "   ", createdAt = 1) }
        assertFailsWith<IllegalArgumentException> { GroupMessage.build(signer.publicKey, "ok", "a".repeat(GroupMessage.MAX_TEXT_LENGTH + 1), createdAt = 1) }

        assertNull(GroupMessage.from(signer.sign(kind = EventKinds.TEXT_NOTE, content = "x", tags = listOf(listOf("h", "ok")))))
        assertNull(GroupMessage.from(signer.sign(kind = EventKinds.GROUP_CHAT, content = "x")))
        assertNull(GroupMessage.from(signer.sign(kind = EventKinds.GROUP_CHAT, content = "x", tags = listOf(listOf("h", "a"), listOf("h", "b")))))
        assertNull(GroupMessage.from(signer.sign(kind = EventKinds.GROUP_CHAT, content = " ", tags = listOf(listOf("h", "a")))))
    }
}
