package org.politisationpopulairefrancaise.core.voir.parlement

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The index and the packages of the open data in the local database, and the migration 11 to 12. */
class ParliamentStoreTest {

    @Test
    fun theIndexIsOneRowAndForgettingErasesEverything() {
        val store = ParliamentStore(JvmDatabase.inMemory())
        assertNull(store.index())
        store.saveIndex(StoredIndex("{}", "2026-11-06", 1))
        store.saveIndex(StoredIndex("{\"v\":2}", "2026-11-07", 2))
        assertEquals(StoredIndex("{\"v\":2}", "2026-11-07", 2), store.index())
        store.savePackage(StoredParliamentPackage("membres", "a".repeat(64), "{}", "2026-11-07", 3))
        store.savePackage(StoredParliamentPackage("scrutins-an-2026-10", "b".repeat(64), "{}", null, 4))
        store.savePackage(StoredParliamentPackage("membres", "c".repeat(64), "{\"v\":2}", null, 5))
        assertEquals(StoredParliamentPackage("membres", "c".repeat(64), "{\"v\":2}", null, 5), store.storedPackage("membres"))
        assertEquals(listOf("membres", "scrutins-an-2026-10"), store.packageNames())
        store.deletePackage("scrutins-an-2026-10")
        assertEquals(1L, store.countPackages())
        store.forget()
        assertNull(store.index())
        assertEquals(0L, store.countPackages())
    }

    @Test
    fun theDownloadedDataSurviveARestartWithoutNetwork() = runTest {
        val keys = ParliamentFixtures.Keys()
        val packages = ParliamentFixtures.packages()
        val database = JvmDatabase.inMemory()
        val server = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(packages)), packages)
        val first = ParliamentRepository(server, ParliamentStore(database), trust = keys.trust, clock = { ParliamentFixtures.NOW })
        first.open()
        first.download("membres")
        first.download("scrutins-an-2026-11")
        server.online = false
        val restarted = ParliamentRepository(server, ParliamentStore(database), trust = keys.trust, clock = { ParliamentFixtures.NOW })
        val snapshot = assertIs<ParliamentOpening.Ready>(restarted.open()).snapshot
        assertEquals(DataOrigin.DEVICE, snapshot.origin)
        assertEquals(setOf("membres", "scrutins-an-2026-11"), snapshot.held)
        assertEquals(1, snapshot.catalog.participation("an:PA900003")?.voted)
    }

    @Test
    fun aDatabaseOfVersionElevenGainsTheTablesOfTheOpenData() {
        val directory = Files.createTempDirectory("ppf-parliament-migration")
        val file = directory.resolve("ppf.db")
        try {
            // A database as the builds of version 11 left it: created at version 11, without the new tables.
            val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
            try {
                PpfDatabase.Schema.create(driver)
                driver.execute(null, "DROP TABLE parliament_index", 0)
                driver.execute(null, "DROP TABLE parliament_package", 0)
                // Added by version 13 (referential of territories, 12.sqm), absent at version 11.
                driver.execute(null, "DROP TABLE territoire_package", 0)
                // Added by version 14 (testimonies of Agir, 13.sqm), absent at version 11.
                driver.execute(null, "DROP TABLE testimony_file", 0)
                driver.execute(null, "DROP TABLE testimony", 0)
                driver.execute(null, "PRAGMA user_version = 11", 0)
            } finally {
                driver.close()
            }
            val store = ParliamentStore(JvmDatabase.open(file))
            store.savePackage(StoredParliamentPackage("membres", "a".repeat(64), "{}", null, 1))
            assertEquals(listOf("membres"), store.packageNames())
            val check = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
            try {
                val version = check.executeQuery(null, "PRAGMA user_version", { cursor -> cursor.next(); QueryResult.Value(cursor.getLong(0)!!) }, 0).value
                assertEquals(PpfDatabase.Schema.version, version)
                assertTrue(version >= 12, "schema version $version")
            } finally {
                check.close()
            }
            // The migration creates exactly the tables of Parliament.sq, the reference for fresh installations.
            val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                PpfDatabase.Schema.create(fresh)
                val migrated = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
                try {
                    assertEquals(definitions(fresh), definitions(migrated))
                } finally {
                    migrated.close()
                }
            } finally {
                fresh.close()
            }
        } finally {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun definitions(driver: JdbcSqliteDriver): List<String> =
        driver.executeQuery(null, "SELECT sql FROM sqlite_master WHERE type = 'table' AND name LIKE 'parliament_%' ORDER BY name", { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) rows += cursor.getString(0)!!.replace(Regex("\\s+"), " ")
            QueryResult.Value(rows)
        }, 0).value
}
