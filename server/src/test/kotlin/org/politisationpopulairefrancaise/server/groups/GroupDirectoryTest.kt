package org.politisationpopulairefrancaise.server.groups

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy.Decision
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupDirectoryTest {

    private val repository = EventRepository(TestDatabase.dataSource)
    private val directory = GroupDirectory(repository)
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val otherEditor = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun metadata(signer: LocalEventSigner, id: String, name: String, createdAt: Long = 1_000, flags: List<String> = emptyList()): SignedEvent =
        runBlocking {
            val tags = listOf(listOf("d", id), listOf("name", name), listOf("about", "[EXEMPLE] à propos")) + flags.map { listOf(it) }
            signer.sign(kind = GroupKinds.METADATA, tags = tags, createdAt = createdAt)
        }

    private fun message(signer: LocalEventSigner, groupId: String, content: String): SignedEvent =
        runBlocking { signer.sign(kind = GroupKinds.CHAT_MESSAGE, content = content, tags = listOf(listOf("h", groupId)), createdAt = 2_000) }

    @Test
    fun readsGroupsFromTheirMetadataEvents() {
        val open = metadata(editor, "demo", "[EXEMPLE] Zèbres")
        val closed = metadata(editor, "ferme", "[EXEMPLE] Abeilles", flags = listOf("closed"))
        val restricted = metadata(editor, "restreint", "[EXEMPLE] Chats", flags = listOf("private", "restricted"))
        listOf(open, closed, restricted).forEach(repository::store)
        // An event that is not well-formed metadata is ignored, like any other kind.
        repository.store(runBlocking { editor.sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "bad id!")), createdAt = 1) })
        repository.store(runBlocking { editor.sign(kind = EventKinds.TEXT_NOTE, tags = listOf(listOf("d", "note")), createdAt = 1) })

        val groups = directory.list()
        assertEquals(listOf("ferme", "restreint", "demo"), groups.map { it.id }, "sorted by name")
        val demo = directory.find("demo")!!
        assertEquals("[EXEMPLE] Zèbres", demo.name)
        assertEquals("[EXEMPLE] à propos", demo.about)
        assertNull(demo.picture)
        assertTrue(demo.isPublic)
        assertTrue(demo.isOpen)
        assertEquals(open, demo.event)
        assertFalse(directory.find("ferme")!!.isOpen)
        assertFalse(directory.find("restreint")!!.isOpen)
        assertFalse(directory.find("restreint")!!.isPublic)
        assertTrue(directory.find("ferme")!!.isPublic)
        assertNull(directory.find("inconnu"))
        assertNull(directory.find("bad id!"))
    }

    @Test
    fun aRepublishedDescriptionReplacesThePreviousOne() {
        repository.store(metadata(editor, "demo", "[EXEMPLE] v1", createdAt = 1_000))
        repository.store(metadata(editor, "demo", "[EXEMPLE] v2", createdAt = 2_000, flags = listOf("closed")))
        assertEquals(1, directory.list().size)
        assertEquals("[EXEMPLE] v2", directory.find("demo")!!.name)
        assertFalse(directory.find("demo")!!.isOpen)
    }

    @Test
    fun admitsMessagesToExistingOpenGroupsOnly() {
        repository.store(metadata(editor, "demo", "[EXEMPLE] Ouvert"))
        repository.store(metadata(editor, "ferme", "[EXEMPLE] Fermé", flags = listOf("closed")))

        assertEquals(Decision.Allowed, directory.admit(message(citizen, "demo", "[EXEMPLE] bonjour")))
        val closed = assertIs<Decision.Refused>(directory.admit(message(citizen, "ferme", "x")))
        assertEquals(403, closed.status)
        assertEquals("group_closed", closed.error)
        val unknown = assertIs<Decision.Refused>(directory.admit(message(citizen, "inconnu", "x")))
        assertEquals(404, unknown.status)
        assertEquals("group_not_found", unknown.error)
        // Any kind sent to a group goes through the same check; events without an h tag are not about groups.
        val noteInGroup = runBlocking { citizen.sign(kind = EventKinds.TEXT_NOTE, content = "x", tags = listOf(listOf("h", "inconnu")), createdAt = 1) }
        assertEquals(404, assertIs<Decision.Refused>(directory.admit(noteInGroup)).status)
        val plainNote = runBlocking { citizen.sign(kind = EventKinds.TEXT_NOTE, content = "x", createdAt = 1) }
        assertEquals(Decision.Allowed, directory.admit(plainNote))
        // Every named group is checked, not only the first one (the policy refuses several h tags anyway).
        val smuggled = runBlocking { citizen.sign(kind = GroupKinds.CHAT_MESSAGE, content = "x", tags = listOf(listOf("h", "demo"), listOf("h", "ferme")), createdAt = 1) }
        assertEquals("group_closed", assertIs<Decision.Refused>(directory.admit(smuggled)).error)
    }

    @Test
    fun aDescriptionWithSeveralIdsIsNotAGroupAndCannotHijackOne() {
        repository.store(metadata(editor, "demo", "[EXEMPLE] Légitime", createdAt = 2_000))
        val twoIds = runBlocking {
            otherEditor.sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "libre"), listOf("d", "demo"), listOf("name", "[EXEMPLE] Double")), createdAt = 1_000)
        }
        assertEquals("bad_group_id", assertIs<Decision.Refused>(directory.admit(twoIds)).error)
        val noId = runBlocking { editor.sign(kind = GroupKinds.METADATA, tags = listOf(listOf("name", "[EXEMPLE] Sans identifiant")), createdAt = 1) }
        assertEquals("bad_group_id", assertIs<Decision.Refused>(directory.admit(noId)).error)
        // Even if such an event reached the database, it would describe nothing.
        repository.store(twoIds)
        assertEquals("[EXEMPLE] Légitime", directory.find("demo")!!.name)
        assertNull(directory.find("libre"))
        assertEquals(listOf("demo"), directory.list().map { it.id })
    }

    @Test
    fun aGroupIdBelongsToTheKeyThatDescribedItFirst() {
        val first = metadata(editor, "demo", "[EXEMPLE] Premier", createdAt = 1_000)
        assertEquals(Decision.Allowed, directory.admit(first))
        repository.store(first)
        assertEquals(Decision.Allowed, directory.admit(metadata(editor, "demo", "[EXEMPLE] Premier, modifié", createdAt = 3_000)))
        val taken = assertIs<Decision.Refused>(directory.admit(metadata(otherEditor, "demo", "[EXEMPLE] Usurpé", createdAt = 2_000)))
        assertEquals(409, taken.status)
        assertEquals("group_taken", taken.error)
        assertEquals(Decision.Allowed, directory.admit(metadata(otherEditor, "autre", "[EXEMPLE] Autre")))

        // Companions of the metadata (admins, roles) come from the managing key, for an existing group.
        val admins = runBlocking { editor.sign(kind = GroupKinds.ADMINS, tags = listOf(listOf("d", "demo"), listOf("p", editor.publicKey.hex, "admin")), createdAt = 1) }
        assertEquals(Decision.Allowed, directory.admit(admins))
        val foreignAdmins = runBlocking { otherEditor.sign(kind = GroupKinds.ADMINS, tags = listOf(listOf("d", "demo")), createdAt = 1) }
        assertEquals(403, assertIs<Decision.Refused>(directory.admit(foreignAdmins)).status)
        val orphanRoles = runBlocking { editor.sign(kind = GroupKinds.ROLES, tags = listOf(listOf("d", "inconnu")), createdAt = 1) }
        assertEquals(404, assertIs<Decision.Refused>(directory.admit(orphanRoles)).status)
    }

    @Test
    fun whenTwoKeysClaimedTheSameIdTheEarliestDescriptionWins() {
        // Only possible through a race between two authorized keys; the outcome must still be deterministic.
        repository.store(metadata(otherEditor, "demo", "[EXEMPLE] Second", createdAt = 2_000))
        repository.store(metadata(editor, "demo", "[EXEMPLE] Premier", createdAt = 1_000))
        assertEquals("[EXEMPLE] Premier", directory.find("demo")!!.name)
        assertEquals(listOf("[EXEMPLE] Premier"), directory.list().map { it.name })
    }
}
