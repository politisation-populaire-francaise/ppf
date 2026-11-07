package org.politisationpopulairefrancaise.core.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Opens the local database on the JVM (desktop); the schema is created or migrated automatically. */
object JvmDatabase {
    /**
     * How long a connection waits for another one holding a write lock before failing: with a file,
     * each thread has its own connection, and two writes at the same moment must queue, not fail.
     */
    private const val BUSY_TIMEOUT_MILLIS = "10000"

    fun open(file: Path): PpfDatabase {
        file.toAbsolutePath().parent?.let(Files::createDirectories)
        return PpfDatabase(JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}", properties(), PpfDatabase.Schema))
    }

    /**
     * For tests: a fresh database that lives as long as the test process, in a temporary file
     * deleted at exit. Not SQLite's ":memory:", whose single connection is shared by every thread:
     * two coroutines on different threads then collide ("cannot start a transaction within a
     * transaction") where the application, which uses a file, never does.
     */
    fun inMemory(): PpfDatabase {
        val file = Files.createTempFile("ppf-test-", ".db")
        Files.delete(file)
        file.toFile().deleteOnExit()
        listOf("-journal", "-wal", "-shm").forEach { java.io.File(file.toString() + it).deleteOnExit() }
        return open(file)
    }

    private fun properties(): Properties = Properties().apply { setProperty("busy_timeout", BUSY_TIMEOUT_MILLIS) }
}
