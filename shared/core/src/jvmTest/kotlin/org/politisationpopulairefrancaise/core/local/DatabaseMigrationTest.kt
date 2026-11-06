package org.politisationpopulairefrancaise.core.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.core.social.FollowStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun theSchemaIsAtVersion5() {
        // 1: events; 2: follow (1.sqm); 3: messaging (2.sqm); 4: sync cursors (3.sqm); 5: views of the demands wall (4.sqm).
        assertEquals(5L, PpfDatabase.Schema.version)
    }

    @Test
    fun aVersion1DatabaseGainsTheFollowTableAndKeepsItsEvents() {
        val file = Files.createTempFile("ppf-migration-", ".db")
        Files.delete(file)
        file.toFile().deleteOnExit()
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
    }

    @Test
    fun aFreshDatabaseHasEveryTableAtTheLatestVersion() {
        val database = JvmDatabase.inMemory()
        assertEquals(0L, FollowStore(database).count())
        assertEquals(0L, LocalEventStore(database).count())
        assertEquals(0L, RevendicationViewStore(database).count())
    }
}
