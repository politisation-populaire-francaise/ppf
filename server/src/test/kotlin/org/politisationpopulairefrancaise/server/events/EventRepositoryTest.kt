package org.politisationpopulairefrancaise.server.events

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventRepositoryTest {

    private val repository = EventRepository(TestDatabase.dataSource)
    private val alice = LocalEventSigner(IdentityKeys.generate())
    private val bob = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun note(signer: LocalEventSigner, content: String, createdAt: Long, tags: List<List<String>> = emptyList()): SignedEvent =
        runBlocking { signer.sign(kind = EventKinds.TEXT_NOTE, content = content, tags = tags, createdAt = createdAt) }

    private fun article(signer: LocalEventSigner, identifier: String, content: String, createdAt: Long): SignedEvent =
        runBlocking { signer.sign(kind = EventKinds.LONG_FORM_ARTICLE, content = content, tags = listOf(listOf("d", identifier)), createdAt = createdAt) }

    @Test
    fun storesFindsAndRefusesDuplicates() {
        val event = note(alice, "[EXEMPLE] bonjour", 1_000)
        assertEquals(StoreOutcome.STORED, repository.store(event))
        assertEquals(StoreOutcome.DUPLICATE, repository.store(event))
        assertEquals(event, repository.find(event.id))
        assertNull(repository.find("0".repeat(64)))
        assertEquals(1, repository.count())
    }

    @Test
    fun addressableContentKeepsOnlyTheNewestVersionPerIdentifier() {
        val v1 = article(alice, "fiche-droits", "[EXEMPLE] version 1", 1_000)
        val v2 = article(alice, "fiche-droits", "[EXEMPLE] version 2", 2_000)
        val other = article(alice, "fiche-greve", "[EXEMPLE] autre fiche", 1_500)
        val bobs = article(bob, "fiche-droits", "[EXEMPLE] la fiche de Bob", 1_000)

        assertEquals(StoreOutcome.STORED, repository.store(v2))
        assertEquals(StoreOutcome.SUPERSEDED, repository.store(v1))
        assertEquals(StoreOutcome.STORED, repository.store(other))
        assertEquals(StoreOutcome.STORED, repository.store(bobs))
        assertEquals(3, repository.count())
        assertNull(repository.find(v1.id))
        assertEquals(listOf(v2), repository.query(EventFilter(identifiers = listOf("fiche-droits"), authors = listOf(alice.publicKey.hex))))

        val v3 = article(alice, "fiche-droits", "[EXEMPLE] version 3", 3_000)
        assertEquals(StoreOutcome.STORED, repository.store(v3))
        assertNull(repository.find(v2.id))
        assertEquals(v3, repository.find(v3.id))
    }

    @Test
    fun replaceableKindsKeepOneEventPerAuthor() {
        val profile1 = runBlocking { alice.sign(kind = EventKinds.METADATA, content = "{\"name\":\"[EXEMPLE] Alice\"}", createdAt = 10) }
        val profile2 = runBlocking { alice.sign(kind = EventKinds.METADATA, content = "{\"name\":\"[EXEMPLE] Alice 2\"}", createdAt = 20) }
        assertEquals(StoreOutcome.STORED, repository.store(profile1))
        assertEquals(StoreOutcome.STORED, repository.store(profile2))
        assertEquals(listOf(profile2), repository.query(EventFilter(kinds = listOf(EventKinds.METADATA))))
    }

    @Test
    fun queriesFilterAndOrderNewestFirst() {
        val a1 = note(alice, "a1", 100)
        val a2 = note(alice, "a2", 300, tags = listOf(listOf("t", "greve")))
        val b1 = note(bob, "b1", 200)
        val article = article(bob, "fiche", "f", 250)
        listOf(a1, a2, b1, article).forEach { assertEquals(StoreOutcome.STORED, repository.store(it)) }

        assertEquals(listOf(a2, article, b1, a1), repository.query(EventFilter()))
        assertEquals(listOf(a2, b1, a1), repository.query(EventFilter(kinds = listOf(EventKinds.TEXT_NOTE))))
        assertEquals(listOf(a2, a1), repository.query(EventFilter(authors = listOf(alice.publicKey.hex))))
        assertEquals(listOf(article, b1), repository.query(EventFilter(since = 150, until = 299)))
        assertEquals(listOf(a2, article), repository.query(EventFilter(limit = 2)))
        assertEquals(listOf(article), repository.query(EventFilter(identifiers = listOf("fiche", "autre"))))
        assertEquals(emptyList(), repository.query(EventFilter(kinds = listOf(EventKinds.DELETION_REQUEST))))
    }

    @Test
    fun filtersByTagsOrWithinANameAndAcrossNames() {
        val carol = "c".repeat(64)
        val dave = "d".repeat(64)
        val toCarol = note(alice, "1", 100, tags = listOf(listOf("p", carol)))
        val toDave = note(alice, "2", 200, tags = listOf(listOf("p", dave), listOf("t", "greve")))
        val toBoth = note(bob, "3", 300, tags = listOf(listOf("p", carol), listOf("p", dave), listOf("t", "greve")))
        val untagged = note(bob, "4", 400)
        listOf(toCarol, toDave, toBoth, untagged).forEach { assertEquals(StoreOutcome.STORED, repository.store(it)) }

        assertEquals(listOf(toBoth, toCarol), repository.query(EventFilter(tags = mapOf("p" to listOf(carol)))))
        // Several values of one name: either of them.
        assertEquals(listOf(toBoth, toDave, toCarol), repository.query(EventFilter(tags = mapOf("p" to listOf(carol, dave)))))
        // Several names: all of them.
        assertEquals(listOf(toBoth), repository.query(EventFilter(tags = mapOf("p" to listOf(carol), "t" to listOf("greve")))))
        // Combined with the other filters.
        assertEquals(listOf(toCarol), repository.query(EventFilter(authors = listOf(alice.publicKey.hex), tags = mapOf("p" to listOf(carol)))))
        assertEquals(listOf(toDave), repository.query(EventFilter(kinds = listOf(EventKinds.TEXT_NOTE), until = 250, tags = mapOf("t" to listOf("greve")))))
        assertEquals(emptyList(), repository.query(EventFilter(tags = mapOf("p" to listOf("e".repeat(64))))))
        assertEquals(emptyList(), repository.query(EventFilter(tags = mapOf("x" to listOf(carol)))))
        // A value containing colons is matched as is.
        val colon = note(alice, "5", 500, tags = listOf(listOf("a", "30023:$carol:fiche:droits")))
        repository.store(colon)
        assertEquals(listOf(colon), repository.query(EventFilter(tags = mapOf("a" to listOf("30023:$carol:fiche:droits")))))
    }

    @Test
    fun tagRowsFollowTheirEvent() {
        val v1 = article(alice, "fiche", "[EXEMPLE] v1", 1_000)
        val v2 = article(alice, "fiche", "[EXEMPLE] v2", 2_000)
        repository.store(v1)
        repository.store(v2)
        assertEquals(listOf(v2), repository.query(EventFilter(tags = mapOf("d" to listOf("fiche")))))
        assertEquals(1, tagRows())

        // Tags beyond the index limits are kept in the event but not indexed; duplicates are indexed once.
        val huge = "x".repeat(2_000)
        val event = note(alice, "6", 3_000, tags = listOf(listOf("t", huge), listOf("t", "a"), listOf("t", "a"), listOf("", "b"), listOf("alone")))
        repository.store(event)
        assertEquals(event, repository.find(event.id))
        assertEquals(emptyList(), repository.query(EventFilter(tags = mapOf("t" to listOf(huge)))))
        assertEquals(listOf(event), repository.query(EventFilter(tags = mapOf("t" to listOf("a")))))
        assertEquals(2, tagRows())
    }

    private fun tagRows(): Int = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery("SELECT count(*) FROM event_tags")
            rows.next()
            rows.getInt(1)
        }
    }

    @Test
    fun tagsAndUnicodeContentSurviveTheRoundTrip() {
        val event = note(alice, "Grève générale : « on continue » \n ligne 2 \t é € 🙂", 1, tags = listOf(listOf("t", "grève", "extra"), listOf("d", "")))
        repository.store(event)
        assertEquals(event, repository.find(event.id))
    }
}
