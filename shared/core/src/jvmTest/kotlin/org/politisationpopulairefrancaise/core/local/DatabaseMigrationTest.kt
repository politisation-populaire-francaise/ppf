package org.politisationpopulairefrancaise.core.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.politisationpopulairefrancaise.core.comprendre.LearningProgressStore
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaStore
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalRecord
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalReview
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The local database of an installed application is migrated, never recreated: a database of
 * version 1 (events only, first builds of November 2026) must gain the tables added since, and
 * keep its rows. The schema of version 1 is frozen here on purpose: it is what those builds wrote.
 */
class DatabaseMigrationTest {

    private val version1Schema = listOf(
        """CREATE TABLE event (
            id TEXT NOT NULL PRIMARY KEY, pubkey TEXT NOT NULL, created_at INTEGER NOT NULL, kind INTEGER NOT NULL,
            tags TEXT NOT NULL, content TEXT NOT NULL, sig TEXT NOT NULL, address TEXT, received_at INTEGER NOT NULL,
            pending INTEGER NOT NULL DEFAULT 0)""",
        "CREATE INDEX event_kind_created_at ON event(kind, created_at DESC, id)",
        "CREATE INDEX event_pubkey_created_at ON event(pubkey, created_at DESC, id)",
        "CREATE UNIQUE INDEX event_address ON event(address)",
        "CREATE INDEX event_pending ON event(pending)",
        "CREATE TABLE event_tag (event_id TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL)",
        "CREATE INDEX event_tag_lookup ON event_tag(name, value, event_id)",
        "CREATE INDEX event_tag_event ON event_tag(event_id)",
    )

    private val eventId = "a".repeat(64)

    /** Writes a version 1 database holding one event, exactly as the first builds left it. */
    private fun writeVersion1Database(file: Path) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            version1Schema.forEach { driver.execute(null, it, 0) }
            driver.execute(
                null,
                "INSERT INTO event(id, pubkey, created_at, kind, tags, content, sig, address, received_at, pending) " +
                    "VALUES ('$eventId', '${"b".repeat(64)}', 10, 1, '[]', '[EXEMPLE] ancienne note', '${"c".repeat(128)}', NULL, 11, 0)",
                0,
            )
            driver.execute(null, "PRAGMA user_version = 1", 0)
        } finally {
            driver.close()
        }
    }

    /**
     * Writes a version 5 database (the builds with the views of the demands wall, before the joined
     * rooms): the version 1 schema migrated up to 5, with one saved view.
     */
    private fun writeVersion5Database(file: Path) {
        writeVersion1Database(file)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            PpfDatabase.Schema.migrate(driver, 1, 5)
            driver.execute(
                null,
                "INSERT INTO revendication_view(id, position, name, query) VALUES ('national', 0, NULL, NULL)",
                0,
            )
            driver.execute(null, "PRAGMA user_version = 5", 0)
        } finally {
            driver.close()
        }
    }

    private fun userVersion(file: Path): Long {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            return driver.executeQuery(null, "PRAGMA user_version", { cursor ->
                cursor.next()
                QueryResult.Value(assertNotNull(cursor.getLong(0)))
            }, 0).value
        } finally {
            driver.close()
        }
    }

    private fun tempDatabaseFile(): Path {
        val file = Files.createTempFile("ppf-migration-", ".db")
        Files.delete(file)
        file.toFile().deleteOnExit()
        return file
    }

    @Test
    fun theSchemaIsAtVersion9() {
        // 1: events; 2: follow (1.sqm); 3: messaging (2.sqm); 4: sync cursors (3.sqm);
        // 5: views of the demands wall (4.sqm); 6: joined rooms (5.sqm); 7: progress in the courses (6.sqm);
        // 8: followed contents of Voir (7.sqm); 9: agenda answers and proposals (8.sqm).
        assertEquals(9L, PpfDatabase.Schema.version)
    }

    @Test
    fun aVersion1DatabaseGainsTheFollowTableAndKeepsItsEvents() {
        val file = tempDatabaseFile()
        writeVersion1Database(file)
        assertEquals(1L, userVersion(file))

        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))

        // The new table works, and the old rows are still there.
        val follows = FollowStore(database)
        assertEquals(0L, follows.count())
        val key = IdentityKeys.generate().publicKey.hex
        follows.follow(key)
        assertTrue(follows.isFollowed(key))
        val events = LocalEventStore(database)
        assertEquals(1L, events.count())
        assertEquals("[EXEMPLE] ancienne note", assertNotNull(events.find(eventId)).content)

        // So does the table of the views of the demands wall (version 5).
        val views = RevendicationViewStore(database)
        assertEquals(null, views.layout())
        views.save(listOf(RevendicationViews.ALL, RevendicationViews.NATIONAL))
        assertEquals(listOf(RevendicationViews.ALL, RevendicationViews.NATIONAL), views.layout())

        // And the table of the joined rooms (version 6).
        val rooms = GroupStore(database)
        assertEquals(0L, rooms.count())
        rooms.join("collectif-demonstration")
        assertTrue(rooms.isMember("collectif-demonstration"))

        // And the progress in the courses of "Comprendre" (version 7).
        val learning = LearningProgressStore(database)
        assertEquals(0L, learning.count())
        learning.reachStep("30023:abc:la-dissolution", 1, now = 10)
        assertEquals(1, assertNotNull(learning.progress("30023:abc:la-dissolution")).furthestStep)

        // And the table of the followed contents of Voir (version 8).
        val followedContents = ContentFollowStore(database)
        val address = ContentAddress.parse("30023:$key:manifestants-journees")
        followedContents.follow(address)
        assertEquals(listOf(address), followedContents.all())
        // And the tables of the agenda (version 9).
        val agenda = AgendaStore(database)
        assertEquals(0L, agenda.countResponses())
        agenda.respond("31923:${"b".repeat(64)}:rassemblement", AgendaResponse.GOING, endsAt = 100)
        assertEquals(1L, agenda.countResponses())
    }

    @Test
    fun aVersion6DatabaseGainsTheAgendaTablesAndKeepsItsRooms() {
        val file = tempDatabaseFile()
        writeVersion1Database(file)
        JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}").let { driver ->
            try {
                PpfDatabase.Schema.migrate(driver, 1, 6)
                driver.execute(null, "INSERT INTO group_membership(group_id, joined_at) VALUES ('collectif-demonstration', 1)", 0)
                driver.execute(null, "PRAGMA user_version = 6", 0)
            } finally {
                driver.close()
            }
        }
        assertEquals(6L, userVersion(file))

        val database = JvmDatabase.open(file)
        assertEquals(9L, userVersion(file))
        assertTrue(GroupStore(database).isMember("collectif-demonstration"))
        assertEquals("[EXEMPLE] ancienne note", assertNotNull(LocalEventStore(database).find(eventId)).content)

        val agenda = AgendaStore(database)
        assertEquals(0L, agenda.countResponses())
        assertEquals(0L, agenda.countProposals())
        agenda.respond("31923:${"b".repeat(64)}:rassemblement", AgendaResponse.INTERESTED, endsAt = 100)
        agenda.recordProposal("e".repeat(64), ProposalRecord(ProposalReview.REVIEW))
        assertEquals(AgendaResponse.INTERESTED, agenda.responses().values.single())
        assertEquals(ProposalReview.REVIEW, agenda.proposals().values.single().review)
    }

    @Test
    fun aVersion5DatabaseGainsTheJoinedRoomsAndKeepsItsViewsAndEvents() {
        val file = tempDatabaseFile()
        writeVersion5Database(file)
        assertEquals(5L, userVersion(file))

        val database = JvmDatabase.open(file)
        assertEquals(PpfDatabase.Schema.version, userVersion(file))

        // The rows written at version 5 survive.
        assertEquals("[EXEMPLE] ancienne note", assertNotNull(LocalEventStore(database).find(eventId)).content)
        assertEquals(1L, RevendicationViewStore(database).count())

        // The new table starts empty and works.
        val rooms = GroupStore(database)
        assertEquals(0L, rooms.count())
        rooms.join("collectif-demonstration")
        rooms.join("syndicat-demonstration")
        assertEquals(listOf("syndicat-demonstration", "collectif-demonstration").toSet(), rooms.all().toSet())
        rooms.leave("collectif-demonstration")
        assertFalse(rooms.isMember("collectif-demonstration"))
    }

    @Test
    fun aFreshDatabaseHasEveryTableAtTheLatestVersion() {
        val database = JvmDatabase.inMemory()
        assertEquals(0L, FollowStore(database).count())
        assertEquals(0L, LocalEventStore(database).count())
        assertEquals(0L, RevendicationViewStore(database).count())
        assertEquals(0L, GroupStore(database).count())
        val followed = ContentFollowStore(database)
        assertEquals(emptyList(), followed.all())
        val address = ContentAddress.parse("30023:${IdentityKeys.generate().publicKey.hex}:recours-49-3")
        followed.follow(address)
        followed.follow(address)
        assertEquals(listOf(address), followed.all())
        followed.unfollow(address)
        assertEquals(emptyList(), followed.all())
        assertEquals(0L, AgendaStore(database).countResponses())
        assertEquals(0L, AgendaStore(database).countProposals())
    }
}
