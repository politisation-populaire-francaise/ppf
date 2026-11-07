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
        // A loaded machine (several builds at once) can take more than the default 10 s to start it.
        val postgres = EmbeddedPostgres.builder().setPGStartupWait(java.time.Duration.ofSeconds(120)).start()
        Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
        postgres.postgresDatabase.also(Database::migrate)
    }

    fun reset() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("TRUNCATE events, revendication_proposal, held_content, elus_import, elus_staging CASCADE") }
        }
    }
}
