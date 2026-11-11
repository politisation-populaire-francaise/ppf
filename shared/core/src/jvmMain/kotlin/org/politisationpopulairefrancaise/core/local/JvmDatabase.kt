package org.politisationpopulairefrancaise.core.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Opens the local database on the JVM (desktop); the schema is created or migrated automatically. */
object JvmDatabase {
    fun open(file: Path): PpfDatabase {
        file.toAbsolutePath().parent?.let(Files::createDirectories)
        return PpfDatabase(JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}", Properties(), PpfDatabase.Schema))
    }

    /**
     * For tests: lives only as long as the driver. Safe when several threads use it at once (contents
     * imported in the background while a screen reads): see [SharedMemoryDriver].
     */
    fun inMemory(): PpfDatabase {
        val driver = SharedMemoryDriver()
        PpfDatabase.Schema.create(driver)
        return PpfDatabase(driver)
    }
}
