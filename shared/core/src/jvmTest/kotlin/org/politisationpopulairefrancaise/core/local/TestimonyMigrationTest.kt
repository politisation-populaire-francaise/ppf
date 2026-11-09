package org.politisationpopulairefrancaise.core.local

import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyStore
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A device at schema version 13 (the last before the testimonies) gets the
 * testimony tables of 13.sqm, exactly as Testimony.sq creates them on a fresh installation.
 */
class TestimonyMigrationTest {
    private fun definitions(url: String): List<String> = DriverManager.getConnection(url).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name LIKE 'testimony%' ORDER BY name").use { rows ->
                generateSequence { if (rows.next()) rows.getString(1).replace(Regex("\\s+"), " ") else null }.toList()
            }
        }
    }

    @Test
    fun aVersion14DatabaseGainsTheTestimonyTables() {
        val directory = Files.createTempDirectory("ppf-testimony-migration")
        try {
            val fresh = directory.resolve("fresh.db")
            JvmDatabase.open(fresh)
            val expected = definitions("jdbc:sqlite:${fresh.toAbsolutePath()}")
            assertEquals(2, expected.size)

            // The same database as a version 13 device has it: without the testimony tables.
            val old = directory.resolve("old.db")
            JvmDatabase.open(old)
            val url = "jdbc:sqlite:${old.toAbsolutePath()}"
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("DROP TABLE letter_note")
                    statement.executeUpdate("DROP TABLE reminder_plan")
                    statement.executeUpdate("DROP TABLE reminder")
                    statement.executeUpdate("DROP TABLE contact_verification")
                    statement.executeUpdate("DROP TABLE push_registration")
                    statement.executeUpdate("DROP TABLE notification_mark")
                    statement.executeUpdate("DROP TABLE notification_entry")
                    statement.executeUpdate("DROP TABLE notification_setting")
                    statement.executeUpdate("DROP TABLE testimony")
                    statement.executeUpdate("DROP TABLE testimony_file")
                    statement.executeUpdate("PRAGMA user_version = 13")
                }
            }
            val database = JvmDatabase.open(old)
            assertEquals(0, TestimonyStore(database).count())
            assertEquals(expected, definitions(url))
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA user_version").use { rows ->
                        rows.next()
                        assertEquals(PpfDatabase.Schema.version, rows.getLong(1))
                    }
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
