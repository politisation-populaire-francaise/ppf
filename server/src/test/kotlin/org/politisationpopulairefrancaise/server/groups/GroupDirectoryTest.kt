package org.politisationpopulairefrancaise.server.groups

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy.Decision
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    private fun metadata(
        signer: LocalEventSigner,
        id: String,
        name: String,
        createdAt: Long = 1_000,
        flags: List<String> = emptyList(),
        territory: String? = "Lyon",
        charter: String? = GroupKinds.CHARTER_VERSION,
        about: String? = "[EXEMPLE] à propos",
    ): SignedEvent = runBlocking {
        val tags = listOfNotNull(
            listOf("d", id),
            listOf("name", name),
            about?.let { listOf("about", it) },
            territory?.let { listOf(GroupKinds.TERRITORY_TAG, it) },
            charter?.let { listOf(GroupKinds.CHARTER_TAG, it) },
        ) + flags.map { listOf(it) }
        signer.sign(kind = GroupKinds.METADATA, tags = tags, createdAt = createdAt)
    }

    private fun message(signer: LocalEventSigner, groupId: String, content: String): SignedEvent =
        runBlocking { signer.sign(kind = GroupKinds.CHAT_MESSAGE, content = content, tags = listOf(listOf("h", groupId)), createdAt = 2_000) }

    @Test
    fun readsGroupsFromTheirMetadataEvents() {
        val open = metadata(editor, "demo", "[EXEMPLE] Zèbres")
        val closed = metadata(editor, "ferme", "[EXEMPLE] Abeilles", flags = listOf("closed"), territory = "Paris")
        val restricted = metadata(editor, "restreint", "[EXEMPLE] Chats", flags = listOf("private", "restricted"), territory = "Nantes")
        val legacy = metadata(citizen, "ancien", "[EXEMPLE] Sans territoire", territory = null, charter = "0.9")
        listOf(open, closed, restricted, legacy).forEach(repository::store)
        // An event that is not well-formed metadata is ignored, like any other kind.
        repository.store(runBlocking { editor.sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "bad id!")), createdAt = 1) })
        repository.store(runBlocking { editor.sign(kind = EventKinds.TEXT_NOTE, tags = listOf(listOf("d", "note")), createdAt = 1) })

        val groups = directory.list()
        assertEquals(listOf("ferme", "restreint", "ancien", "demo"), groups.map { it.id }, "sorted by name")
        val demo = directory.find("demo")!!
        assertEquals("[EXEMPLE] Zèbres", demo.name)
        assertEquals("[EXEMPLE] à propos", demo.about)
        assertNull(demo.picture)
        assertEquals("Lyon", demo.territory)
        assertEquals("1.0", demo.charter)
        assertEquals("exemple zebres", demo.normalizedName)
        assertEquals("lyon", demo.normalizedTerritory)
        assertTrue(demo.isPublic)
        assertTrue(demo.isOpen)
        assertEquals(open, demo.event)
        assertFalse(directory.find("ferme")!!.isOpen)
        assertFalse(directory.find("restreint")!!.isOpen)
        assertFalse(directory.find("restreint")!!.isPublic)
        assertTrue(directory.find("ferme")!!.isPublic)
        // A description stored before D-028 (or with another charter version) shows null.
        assertNull(directory.find("ancien")!!.territory)
        assertNull(directory.find("ancien")!!.charter)
        assertEquals("", directory.find("ancien")!!.normalizedTerritory)
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
        val first = metadata(citizen, "demo", "[EXEMPLE] Premier", createdAt = 1_000)
        assertEquals(Decision.Allowed, directory.admit(first))
        repository.store(first)
        assertEquals(Decision.Allowed, directory.admit(metadata(citizen, "demo", "[EXEMPLE] Premier, modifié", createdAt = 3_000)))
        val taken = assertIs<Decision.Refused>(directory.admit(metadata(otherEditor, "demo", "[EXEMPLE] Usurpé", createdAt = 2_000)))
        assertEquals(409, taken.status)
        assertEquals("group_taken", taken.error)
        assertNull(taken.existing)
        assertEquals(Decision.Allowed, directory.admit(metadata(otherEditor, "autre", "[EXEMPLE] Autre")))

        // Companions of the metadata (admins, roles) come from the managing key, for an existing group.
        val admins = runBlocking { citizen.sign(kind = GroupKinds.ADMINS, tags = listOf(listOf("d", "demo"), listOf("p", citizen.publicKey.hex, "admin")), createdAt = 1) }
        assertEquals(Decision.Allowed, directory.admit(admins))
        val foreignAdmins = runBlocking { otherEditor.sign(kind = GroupKinds.ADMINS, tags = listOf(listOf("d", "demo")), createdAt = 1) }
        assertEquals(403, assertIs<Decision.Refused>(directory.admit(foreignAdmins)).status)
        val orphanRoles = runBlocking { editor.sign(kind = GroupKinds.ROLES, tags = listOf(listOf("d", "inconnu")), createdAt = 1) }
        assertEquals(404, assertIs<Decision.Refused>(directory.admit(orphanRoles)).status)
    }

    @Test
    fun oneNameNormalizedPerTerritoryNormalized() {
        val original = metadata(citizen, "education-lyon", "[EXEMPLE] Collectif Éducation", territory = "Lyon")
        repository.store(original)

        val duplicate = assertIs<Decision.Refused>(directory.admit(metadata(otherEditor, "autre", "[EXEMPLE] collectif education !", territory = "LYON")))
        assertEquals(409, duplicate.status)
        assertEquals("group_exists", duplicate.error)
        assertEquals("education-lyon", duplicate.existing)
        // Same name elsewhere, or another name here: fine.
        assertEquals(Decision.Allowed, directory.admit(metadata(otherEditor, "education-paris", "[EXEMPLE] Collectif Éducation", territory = "Paris")))
        assertEquals(Decision.Allowed, directory.admit(metadata(otherEditor, "logement-lyon", "[EXEMPLE] Collectif Logement", territory = "Lyon")))
        // The managing key may republish its own description under the same id, same name included...
        assertEquals(Decision.Allowed, directory.admit(metadata(citizen, "education-lyon", "[EXEMPLE] Collectif Éducation", territory = "Lyon", createdAt = 2_000, about = "[EXEMPLE] nouveau texte")))
        // ...but may not rename it onto another group of the same territory.
        repository.store(metadata(otherEditor, "logement-lyon", "[EXEMPLE] Collectif Logement", territory = "Lyon"))
        val renamed = assertIs<Decision.Refused>(directory.admit(metadata(citizen, "education-lyon", "[EXEMPLE] Collectif Logement", territory = "lyon", createdAt = 3_000)))
        assertEquals("group_exists", renamed.error)
        assertEquals("logement-lyon", renamed.existing)

        assertEquals(directory.find("education-lyon"), directory.findDuplicate(Group.from(metadata(otherEditor, "x", "exemple, collectif education", territory = "lyon"))!!))
        assertNull(directory.findDuplicate(Group.from(metadata(otherEditor, "x", "collectif education", territory = "lyon"))!!), "a substring is not the same name")
        assertNull(directory.findDuplicate(directory.find("education-lyon")!!), "a group is not its own duplicate")
        // Descriptions without usable name or territory never match anything (the policy refuses them anyway).
        assertNull(directory.findDuplicate(Group.from(metadata(otherEditor, "x", "[EXEMPLE] Collectif Éducation", territory = null))!!))
    }

    @Test
    fun whenTwoKeysDescribedTheSameIdTheEarliestDescriptionWinsEverywhere() {
        // Only possible through a direct store now that descriptions are admitted one at a time;
        // the outcome must still be deterministic, in the listing and the search as in find.
        repository.store(metadata(otherEditor, "demo", "[EXEMPLE] Second", createdAt = 2_000))
        repository.store(metadata(editor, "demo", "[EXEMPLE] Premier", createdAt = 1_000))
        assertEquals("[EXEMPLE] Premier", directory.find("demo")!!.name)
        assertEquals(listOf("[EXEMPLE] Premier"), directory.list().map { it.name })
        assertEquals(emptyList(), directory.search("second", null), "the description that does not count is not searched")
        assertNull(directory.findDuplicate(Group.from(metadata(citizen, "autre", "[EXEMPLE] Second"))!!), "nor taken as a duplicate")
        assertEquals("demo", directory.findDuplicate(Group.from(metadata(citizen, "autre", "[EXEMPLE] Premier"))!!)?.id)
    }

    @Test
    fun searchesByTextAndTerritoryOnceNormalized() {
        repository.store(metadata(citizen, "education-lyon", "[EXEMPLE] Collectif Éducation", territory = "Lyon", about = "[EXEMPLE] Écoles du 7e"))
        repository.store(metadata(citizen, "education-paris", "[EXEMPLE] Collectif Éducation", territory = "Paris"))
        repository.store(metadata(otherEditor, "logement-lyon", "[EXEMPLE] Logement digne", territory = "LYON", about = null))
        repository.store(metadata(otherEditor, "saint-etienne", "[EXEMPLE] Transports", territory = "Saint-Étienne", about = null))

        assertEquals(listOf("education-lyon", "education-paris", "logement-lyon", "saint-etienne"), directory.list().map { it.id }, "by normalized name, then id")
        assertEquals(listOf("education-lyon", "education-paris"), directory.search("ÉDUCATION", null).map { it.id })
        assertEquals(listOf("education-lyon", "logement-lyon"), directory.search(null, "lyon").map { it.id })
        assertEquals(listOf("education-lyon"), directory.search("education", " Lyon ").map { it.id })
        assertEquals(listOf("education-lyon"), directory.search("ecoles", null).map { it.id }, "the description is searched too")
        assertEquals(listOf("saint-etienne"), directory.search("etienne", null).map { it.id }, "the territory is searched too")
        assertEquals(listOf("saint-etienne"), directory.search(null, "saint etienne").map { it.id })
        assertEquals(listOf("education-lyon", "logement-lyon"), directory.search(null, "Lyon").map { it.id })
        assertEquals(emptyList(), directory.search(null, "Lyon 3e"), "the territory is compared whole")
        assertEquals(4, directory.search("!!!", "  ").size, "a value without letter or digit does not filter")
        assertEquals(listOf("education-lyon"), directory.search(null, null, limit = 1).map { it.id })
        assertEquals(1, directory.search(null, null, limit = 0).size, "the limit is at least 1")
        assertEquals(emptyList(), directory.search("inconnu", null))
    }

    @Test
    fun theDirectoryFollowsTheStoredDescriptionsAndCatchesUpOnMissingOnes() {
        repository.store(metadata(citizen, "demo", "[EXEMPLE] Collectif"))
        // A description stored before the directory table existed: its row is missing.
        TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("DELETE FROM group_directory") }
        }
        assertEquals(emptyList(), directory.list())
        assertNull(directory.findDuplicate(Group.from(metadata(otherEditor, "autre", "[EXEMPLE] Collectif"))!!))
        assertEquals(1, directory.indexMissing())
        assertEquals(0, directory.indexMissing(), "nothing left to index")
        assertEquals(listOf("demo"), directory.list().map { it.id })
        assertEquals("demo", directory.findDuplicate(Group.from(metadata(otherEditor, "autre", "[EXEMPLE] Collectif"))!!)?.id)

        // The row goes with its event.
        TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("DELETE FROM events") }
        }
        assertEquals(emptyList(), directory.list())
    }

    @Test
    fun descriptionsPublishedAtTheSameInstantAreAdmittedOneAtATime() {
        val creators = List(8) { LocalEventSigner(IdentityKeys.generate()) }
        // Eight keys create the same room (same name, same territory) under eight ids at once...
        val sameName = race(creators.mapIndexed { i, key -> metadata(key, "salon-$i", "[EXEMPLE] Collectif Éducation", territory = "Lyon") })
        val stored = sameName.filterIsInstance<GroupDirectory.Admission.Stored>()
        assertEquals(listOf(StoreOutcome.STORED), stored.map { it.outcome }, sameName.toString())
        val winner = directory.list().single().id
        val refusals = sameName.filterIsInstance<GroupDirectory.Admission.Refused>().map { it.refusal }
        assertEquals(7, refusals.size)
        assertTrue(refusals.all { it.error == "group_exists" && it.existing == winner }, refusals.toString())

        // ...and eight keys claim the same free id at once.
        val sameId = race(creators.mapIndexed { i, key -> metadata(key, "convoite", "[EXEMPLE] Salon $i", territory = "Paris") })
        assertEquals(1, sameId.count { it is GroupDirectory.Admission.Stored })
        assertEquals(List(7) { "group_taken" }, sameId.filterIsInstance<GroupDirectory.Admission.Refused>().map { it.refusal.error })
        assertEquals(2, directory.list().size)
    }

    /** Runs [GroupDirectory.admitAndStore] for every event, each on its own thread and directory, all released together. */
    private fun race(events: List<SignedEvent>): List<GroupDirectory.Admission> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(events.size)
        try {
            val futures = events.map { event ->
                pool.submit(Callable { start.await(); GroupDirectory(EventRepository(TestDatabase.dataSource), GroupIndex(TestDatabase.dataSource, TEST_RELAY.groupLockWait)).admitAndStore(event) })
            }
            start.countDown()
            return futures.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
