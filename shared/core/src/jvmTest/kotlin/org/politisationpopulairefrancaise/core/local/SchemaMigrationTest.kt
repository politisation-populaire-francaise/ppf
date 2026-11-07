package org.politisationpopulairefrancaise.core.local

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A device that installed the application with the version 1 schema (table `event` only) must get
 * the messaging tables when it opens the database with the new schema: the `.sqm` migrations must
 * follow every table added to the `.sq` files, otherwise the application crashes at start-up.
 */
class SchemaMigrationTest {

    @Test
    fun aVersionOneDatabaseGainsTheMessagingTables() {
        val directory = Files.createTempDirectory("ppf-migration")
        val file = directory.resolve("ppf.db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    // The schema of version 1, exactly as Event.sq created it on the first installations.
                    statement.executeUpdate(
                        "CREATE TABLE event (id TEXT NOT NULL PRIMARY KEY, pubkey TEXT NOT NULL, created_at INTEGER NOT NULL, " +
                            "kind INTEGER NOT NULL, tags TEXT NOT NULL, content TEXT NOT NULL, sig TEXT NOT NULL, address TEXT, " +
                            "received_at INTEGER NOT NULL, pending INTEGER NOT NULL DEFAULT 0)",
                    )
                    statement.executeUpdate("CREATE INDEX event_kind_created_at ON event(kind, created_at DESC, id)")
                    statement.executeUpdate("CREATE INDEX event_pubkey_created_at ON event(pubkey, created_at DESC, id)")
                    statement.executeUpdate("CREATE UNIQUE INDEX event_address ON event(address)")
                    statement.executeUpdate("CREATE INDEX event_pending ON event(pending)")
                    statement.executeUpdate("CREATE TABLE event_tag (event_id TEXT NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL)")
                    statement.executeUpdate("CREATE INDEX event_tag_lookup ON event_tag(name, value, event_id)")
                    statement.executeUpdate("CREATE INDEX event_tag_event ON event_tag(event_id)")
                    statement.executeUpdate("INSERT INTO event VALUES ('a', 'b', 1, 1, '[]', 'kept', 'c', NULL, 1, 0)")
                    statement.executeUpdate("PRAGMA user_version = 1")
                }
            }

            val database = JvmDatabase.open(file)
            // The store works on the migrated database and the existing content is kept.
            assertEquals(0, MessageStore(database).count())
            assertEquals(1, LocalEventStore(database).count())
            assertEquals(0, RevendicationViewStore(database).count())

            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
                        generateSequence { if (rows.next()) rows.getString(1) else null }.toSet()
                    }
                    assertTrue(tables.containsAll(setOf("conversation", "message", "outgoing_message", "messaging_state")), tables.toString())
                    assertTrue(tables.containsAll(setOf("sync_cursor", "revendication_view")), tables.toString())
                    assertTrue(tables.containsAll(setOf("agenda_response", "agenda_proposal")), tables.toString())
                    assertTrue(tables.containsAll(setOf("group_membership", "call_response")), tables.toString())
                    assertTrue(tables.containsAll(setOf("elus_commune", "elus_package")), tables.toString())
                    assertTrue(tables.containsAll(setOf("testimony", "testimony_file")), tables.toString())
                    val version = statement.executeQuery("PRAGMA user_version").use { rows -> rows.next(); rows.getLong(1) }
                    assertEquals(PpfDatabase.Schema.version, version)
                    assertTrue(version >= 5, "schema version $version")
                }
            }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
