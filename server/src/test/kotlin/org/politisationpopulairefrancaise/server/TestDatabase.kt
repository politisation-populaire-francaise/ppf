package org.politisationpopulairefrancaise.server

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.politisationpopulairefrancaise.server.db.Database
import javax.sql.DataSource

/**
 * One real PostgreSQL 17, started in-process for the whole test run (binaries come from Maven
 * Central: nothing to install). Migrated once; emptied before each test with [reset].
 */
object TestDatabase {
    val dataSource: DataSource by lazy {
        val postgres = EmbeddedPostgres.builder().start()
        Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
        postgres.postgresDatabase.also(Database::migrate)
    }

    fun reset() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("TRUNCATE events CASCADE") }
        }
    }
}
