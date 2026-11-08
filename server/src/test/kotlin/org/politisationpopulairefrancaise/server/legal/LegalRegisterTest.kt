package org.politisationpopulairefrancaise.server.legal

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilizationTags
import org.politisationpopulairefrancaise.server.agir.HeldQueue
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import java.sql.SQLException
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The legal register of level 1 (D-077) on the embedded PostgreSQL 17: what enters it, kind by kind, and when it leaves. */
class LegalRegisterTest {
    private val alice = LocalEventSigner(IdentityKeys.generate())
    private val bob = LocalEventSigner(IdentityKeys.generate())
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val repository = EventRepository(TestDatabase.dataSource, LegalRegister { it == editor.publicKey.hex })

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private data class Row(
        val eventId: String,
        val operation: String,
        val receivedAt: Instant,
        val createdAt: Long,
        val kind: Int,
        val pubkey: String,
        val protocol: Int,
        val removedId: String?,
        val removedCreatedAt: Long?,
        val removedPubkey: String?,
    )

    private fun operations(): List<Row> = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery(
                "SELECT event_id, operation, received_at, created_at, kind, pubkey, protocol, removed_event_id, removed_created_at, removed_pubkey " +
                    "FROM legal_register.content_operation ORDER BY id",
            )
            buildList {
                while (rows.next()) {
                    add(
                        Row(
                            rows.getString(1), rows.getString(2), rows.getObject(3, OffsetDateTime::class.java).toInstant(), rows.getLong(4), rows.getInt(5),
                            rows.getString(6), rows.getInt(7), rows.getString(8), rows.getObject(9)?.let { rows.getLong(9) }, rows.getString(10),
                        ),
                    )
                }
            }
        }
    }

    private fun pseudonyms(): List<Triple<String, String?, String?>> = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery("SELECT pubkey, name, display_name FROM legal_register.account_pseudonym ORDER BY id")
            buildList { while (rows.next()) add(Triple(rows.getString(1), rows.getString(2), rows.getString(3))) }
        }
    }

    /** Everything the register holds, as text, to look for what must never be there. */
    private fun everything(): String = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery(
                "SELECT (SELECT coalesce(string_agg(row_to_json(c)::text, ' '), '') FROM legal_register.content_operation c) || " +
                    "(SELECT coalesce(string_agg(row_to_json(p)::text, ' '), '') FROM legal_register.account_pseudonym p)",
            )
            rows.next()
            rows.getString(1)
        }
    }

    private fun superuser(sql: String) {
        TestDatabase.dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun event(signer: LocalEventSigner, kind: Int, content: String = "[EXEMPLE] contenu $kind", tags: List<List<String>> = emptyList(), createdAt: Long = 1_000): SignedEvent =
        runBlocking { signer.sign(kind = kind, content = content, tags = tags, createdAt = createdAt) }

    /** The tags an event of [kind] needs to be a valid content of that kind (an identifier when addressable). */
    private fun tagsFor(kind: Int, d: String = "exemple"): List<List<String>> = if (EventKinds.isAddressable(kind)) listOf(listOf("d", d)) else emptyList()

    private fun dbNow(): Instant = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery("SELECT now()")
            rows.next()
            rows.getObject(1, OffsetDateTime::class.java).toInstant()
        }
    }

    @Test
    fun theListOfPublicKindsIsExactlyTheOneDocumented() {
        assertEquals(setOf(0, 1, 5, 7, 9, 1111, 30023, 31923, 31925, 39000), LegalRegister.PUBLIC_KINDS)
    }

    @Test
    fun everyPublicKindIsRecordedWhenCreated() {
        val kinds = LegalRegister.PUBLIC_KINDS - EventKinds.DELETION_REQUEST
        val before = dbNow().minusSeconds(1)
        val stored = kinds.map { kind -> event(alice, kind, tags = tagsFor(kind), createdAt = 1_000L + kind).also { assertEquals(StoreOutcome.STORED, repository.store(it)) } }
        val after = dbNow().plusSeconds(1)
        val rows = operations()
        assertEquals(kinds.size, rows.size)
        stored.zip(rows).forEach { (event, row) ->
            assertEquals(Row(event.id, "create", row.receivedAt, event.createdAt, event.kind, alice.publicKey.hex, LegalRegister.PROTOCOL_HTTPS_API, null, null, null), row, "kind ${event.kind}")
            assertTrue(!row.receivedAt.isBefore(before) && !row.receivedAt.isAfter(after), "received now, kind ${event.kind}")
            assertEquals(0, row.receivedAt.nano, "to the second")
        }
        // A replay changes nothing.
        stored.forEach { assertEquals(StoreOutcome.DUPLICATE, repository.store(it)) }
        assertEquals(kinds.size, operations().size)
    }

    @Test
    fun aReplacementKeepsTheIdDateAndKeyOfTheRemovedVersionNeverItsContent() {
        val replaceable = LegalRegister.PUBLIC_KINDS.filter { EventKinds.isReplaceable(it) || EventKinds.isAddressable(it) }
        assertEquals(listOf(0, 30023, 31923, 31925, 39000), replaceable.sorted())
        replaceable.forEach { kind ->
            val v1 = event(alice, kind, content = "[EXEMPLE] version secrète $kind", tags = tagsFor(kind), createdAt = 1_000)
            val v2 = event(alice, kind, content = "[EXEMPLE] version 2", tags = tagsFor(kind), createdAt = 2_000)
            val old = event(alice, kind, content = "[EXEMPLE] version 0", tags = tagsFor(kind), createdAt = 500)
            assertEquals(StoreOutcome.STORED, repository.store(v1))
            assertEquals(StoreOutcome.STORED, repository.store(v2))
            assertEquals(StoreOutcome.SUPERSEDED, repository.store(old))
            val rows = operations().filter { it.kind == kind }
            assertEquals(listOf("create", "replace"), rows.map { it.operation }, "kind $kind")
            val replacement = rows[1]
            assertEquals(v2.id, replacement.eventId)
            assertEquals(2_000, replacement.createdAt)
            assertEquals(v1.id, replacement.removedId)
            assertEquals(1_000, replacement.removedCreatedAt)
            assertEquals(alice.publicKey.hex, replacement.removedPubkey)
        }
        assertFalse(everything().contains("secrète"), "never the text of a version")
        assertFalse(everything().contains("[EXEMPLE]"), "never the text of a content")
    }

    @Test
    fun aDeletionRequestIsRecordedOncePerPublicContentOfItsAuthor() {
        val note = event(alice, EventKinds.TEXT_NOTE)
        val article = event(alice, EventKinds.LONG_FORM_ARTICLE, tags = tagsFor(EventKinds.LONG_FORM_ARTICLE, "appel-exemple"))
        val othersNote = event(bob, EventKinds.TEXT_NOTE)
        val setting = event(alice, EventKinds.APP_DATA, tags = listOf(listOf("d", "ppf:mailbox")))
        listOf(note, article, othersNote, setting).forEach { assertEquals(StoreOutcome.STORED, repository.store(it)) }
        val deletion = event(
            alice, EventKinds.DELETION_REQUEST, content = "",
            tags = listOf(listOf("e", note.id), listOf("e", othersNote.id), listOf("e", setting.id), listOf("a", "30023:${alice.publicKey.hex}:appel-exemple")),
            createdAt = 3_000,
        )
        assertEquals(StoreOutcome.STORED, repository.store(deletion))
        val deletions = operations().filter { it.operation == "delete" }
        assertEquals(setOf(note.id, article.id), deletions.map { it.removedId }.toSet(), "only the public contents of the author")
        deletions.forEach { row ->
            assertEquals(deletion.id, row.eventId)
            assertEquals(EventKinds.DELETION_REQUEST, row.kind)
            assertEquals(3_000, row.createdAt)
            assertEquals(alice.publicKey.hex, row.pubkey)
            assertEquals(alice.publicKey.hex, row.removedPubkey)
            assertEquals(1_000, row.removedCreatedAt)
        }
        // A deletion that names no public content of its author, or another deletion, leaves nothing.
        val nothing = event(alice, EventKinds.DELETION_REQUEST, content = "", tags = listOf(listOf("e", setting.id), listOf("e", deletion.id), listOf("e", "f".repeat(64))), createdAt = 4_000)
        assertEquals(StoreOutcome.STORED, repository.store(nothing))
        assertEquals(2, operations().count { it.operation == "delete" })
    }

    @Test
    fun privateKindsOtherKindsAndEditorialKeysNeverEnterTheRegister() {
        // Follow list, legacy message, seal, rumor, gift wrap, report, application data, personal lists,
        // repost, media, draft, room membership: none is a public content of PPF.
        val excluded = listOf(3, 4, 13, 14, 1059, 1984, 30078, 10000, 10002, 6, 16, 1063, 30024, 9021, 39001)
        excluded.forEach { kind ->
            val tags = tagsFor(kind) + if (kind == 1059) listOf(listOf("p", bob.publicKey.hex)) else emptyList()
            assertEquals(StoreOutcome.STORED, repository.store(event(alice, kind, tags = tags)), "kind $kind")
            assertFalse(kind in LegalRegister.PUBLIC_KINDS)
        }
        // PPF is the editor of what the editorial keys sign: not hosted, not recorded.
        listOf(EventKinds.TEXT_NOTE, EventKinds.LONG_FORM_ARTICLE, EventKinds.METADATA).forEach { kind ->
            val content = if (kind == EventKinds.METADATA) """{"name":"[EXEMPLE] Rédaction"}""" else "[EXEMPLE] édito"
            assertEquals(StoreOutcome.STORED, repository.store(event(editor, kind, content = content, tags = tagsFor(kind))))
        }
        assertEquals(emptyList(), operations())
        assertEquals(emptyList(), pseudonyms())
    }

    @Test
    fun pseudonymsAreRecordedAtEachChangeAndNothingElseOfTheProfile() {
        fun profile(createdAt: Long, content: String) = event(alice, EventKinds.METADATA, content = content, createdAt = createdAt)
        repository.store(profile(1_000, """{"name":"[EXEMPLE] Camille","display_name":"Cam","about":"texte de présentation","picture":"https://exemple.invalid/a.png"}"""))
        // Same pseudonyms, another presentation: no change.
        repository.store(profile(2_000, """{"name":"[EXEMPLE] Camille","display_name":"Cam","about":"autre présentation"}"""))
        repository.store(profile(3_000, """{"name":"[EXEMPLE] Camille D.","display_name":"Cam"}"""))
        // No readable pseudonym any more: a change too.
        repository.store(profile(4_000, "pas du JSON"))
        repository.store(profile(5_000, """{"name":"${"x".repeat(1_200)}","display_name":"a\u0000b"}"""))
        assertEquals(
            listOf(
                Triple(alice.publicKey.hex, "[EXEMPLE] Camille", "Cam"),
                Triple(alice.publicKey.hex, "[EXEMPLE] Camille D.", "Cam"),
                Triple(alice.publicKey.hex, null, null),
                Triple(alice.publicKey.hex, "x".repeat(LegalRegister.MAX_PSEUDONYM_LENGTH), "ab"),
            ),
            pseudonyms(),
        )
        assertEquals(listOf("create", "replace", "replace", "replace", "replace"), operations().map { it.operation })
        assertFalse(everything().contains("présentation"))
        assertFalse(everything().contains("exemple.invalid"))
        // A first profile without any pseudonym records the operation only.
        repository.store(event(bob, EventKinds.METADATA, content = """{"about":"[EXEMPLE] rien"}"""))
        assertTrue(pseudonyms().none { it.first == bob.publicKey.hex })
        assertEquals(1, operations().count { it.pubkey == bob.publicKey.hex })
    }

    @Test
    fun theRegisterHoldsNoAddressNoBrowserNoEmailNoContent() {
        TestDatabase.dataSource.connection.use { connection ->
            val columns = connection.prepareStatement("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'legal_register'").use { statement ->
                val rows = statement.executeQuery()
                buildList { while (rows.next()) add(rows.getString(1) + "." + rows.getString(2)) }
            }
            assertEquals(22, columns.size, columns.toString())
            val forbidden = listOf("ip", "port", "address", "agent", "browser", "email", "mail", "device", "phone", "content", "text", "tags", "sig")
            columns.forEach { column -> forbidden.forEach { word -> assertFalse(column.substringAfter('.').split('_').contains(word), column) } }
        }
    }

    @Test
    fun aFailedStoreLeavesNeitherTheEventNorItsRegisterRow() {
        superuser("CREATE OR REPLACE FUNCTION test_refuse_row() RETURNS trigger LANGUAGE plpgsql AS \$\$ BEGIN RAISE EXCEPTION 'refused by the test'; END \$\$")
        // The register refuses its row: the event is not published either.
        superuser("CREATE TRIGGER test_refuse_register BEFORE INSERT ON legal_register.content_operation FOR EACH ROW EXECUTE FUNCTION test_refuse_row()")
        val note = event(alice, EventKinds.TEXT_NOTE)
        try {
            assertFailsWith<SQLException> { repository.store(note) }
            assertNull(repository.find(note.id), "no publication escapes the register")
        } finally {
            superuser("DROP TRIGGER test_refuse_register ON legal_register.content_operation")
        }
        // Something after the register row fails (here the tags): the row goes with the event.
        superuser("CREATE TRIGGER test_refuse_tags BEFORE INSERT ON event_tags FOR EACH ROW EXECUTE FUNCTION test_refuse_row()")
        val tagged = event(alice, EventKinds.TEXT_NOTE, tags = listOf(listOf("t", "exemple")))
        try {
            assertFailsWith<SQLException> { repository.store(tagged) }
            assertNull(repository.find(tagged.id))
            assertEquals(emptyList(), operations(), "no register row for an event that was not stored")
        } finally {
            superuser("DROP TRIGGER test_refuse_tags ON event_tags")
            superuser("DROP FUNCTION test_refuse_row()")
        }
        assertEquals(StoreOutcome.STORED, repository.store(tagged))
        assertEquals(listOf(tagged.id), operations().map { it.eventId })
    }

    @Test
    fun heldContentIsRecordedWhenPublishedNotWhenSubmitted() = runBlocking {
        val queue = HeldQueue(TestDatabase.dataSource, repository)
        val start = Instant.now().epochSecond + 10 * 86_400
        val held = alice.mobilization(mobilizationTags(d = "manif-registre", start = start), createdAt = 1_000)
        assertEquals(AgirFormats.KIND_MOBILIZATION, held.kind)
        queue.submit(held)
        assertEquals(emptyList(), operations(), "nothing while it waits for review")
        queue.approve(held.id)
        assertEquals(listOf(held.id to "create"), operations().map { it.eventId to it.operation })
    }

    @Test
    fun thePurgeNeverDeletesAnythingYoungerThanOneYear() {
        val old = "a".repeat(64)
        val active = "b".repeat(64)
        val recent = "c".repeat(64)
        fun operation(key: String, age: String) /* age: an SQL interval */ = superuser(
            "INSERT INTO legal_register.content_operation (event_id, operation, received_at, created_at, kind, pubkey, protocol) " +
                "VALUES ('${"e".repeat(63)}${key.first()}', 'create', now() - $age, 1000, 1, '$key', 1)",
        )
        fun pseudonym(key: String, age: String) = superuser(
            "INSERT INTO legal_register.account_pseudonym (pubkey, name, changed_at, event_id, event_created_at) " +
                "VALUES ('$key', '[EXEMPLE] pseudo', now() - $age, '${"d".repeat(64)}', 1000)",
        )
        operation(old, "interval '366 days'")
        pseudonym(old, "interval '400 days'")
        operation(active, "interval '500 days'")
        operation(active, "interval '1 day'")
        pseudonym(active, "interval '500 days'")
        operation(recent, "interval '364 days'")
        operation(recent, "(interval '1 year' - interval '1 hour')")
        pseudonym(recent, "interval '364 days'")
        val report = LegalRegister.purge(TestDatabase.dataSource)
        assertEquals(LegalRegister.PurgeReport(operations = 2, pseudonyms = 1), report)
        val remaining = operations()
        assertEquals(listOf(active, recent, recent), remaining.map { it.pubkey }, "every operation younger than one year stays")
        assertTrue(remaining.all { it.receivedAt.isAfter(dbNow().minusSeconds(366L * 86_400)) })
        // The pseudonym of an account still active stays, however old; the one of an account silent for a year goes.
        assertEquals(listOf(active, recent), pseudonyms().map { it.first })
        assertEquals(LegalRegister.PurgeReport(0, 0), LegalRegister.purge(TestDatabase.dataSource), "nothing younger is ever deleted")
    }
}
