package org.politisationpopulairefrancaise.server.db

import org.postgresql.ds.PGSimpleDataSource
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.AuditActor
import org.politisationpopulairefrancaise.server.backoffice.AuditEntry
import org.politisationpopulairefrancaise.server.backoffice.AuditFilter
import org.politisationpopulairefrancaise.server.backoffice.AuditLog
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The restricted role of the server (D-127): what `afterMigrate.sql` grants it, and what it can never do
 * to the team journal, checked on the real PostgreSQL 17 of the tests.
 */
class DatabaseRolesTest {
    private val baseUrl: String = TestDatabase.dataSource.connection.use { it.metaData.url.substringBefore('?') }

    private fun asRole(role: String, database: String? = null) = PGSimpleDataSource().apply {
        setURL(if (database == null) baseUrl else baseUrl.substringBeforeLast('/') + "/" + database)
        setUser(role)
    }

    private fun superuser(sql: String) {
        TestDatabase.dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    @Test
    fun theServerRoleReadsAndAppendsTheJournalButNeverRewritesIt() {
        // As in infra/: the role exists before the migration, created without rights (NOLOGIN, then LOGIN).
        superuser("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$ROLE') THEN CREATE ROLE $ROLE NOLOGIN; END IF; END $$")
        superuser("ALTER ROLE $ROLE LOGIN")
        Database.runMigrations(TestDatabase.dataSource, ROLE)
        val app = asRole(ROLE)
        val journal = AuditLog(app)
        val line = journal.record(AuditEntry(AuditActor.System, "team.role_test"))
        assertEquals(line, journal.query(AuditFilter(action = "team.role_test"), 1).single())
        app.connection.use { connection ->
            listOf(
                "UPDATE team_audit_log SET reason = 'x' WHERE seq = ${line.seq}",
                "DELETE FROM team_audit_log WHERE seq = ${line.seq}",
                "TRUNCATE team_audit_log",
                "ALTER TABLE team_audit_log DISABLE TRIGGER USER",
                "ALTER TABLE team_audit_log DISABLE TRIGGER ALL",
                "SET session_replication_role = replica",
                "DELETE FROM flyway_schema_history",
                "CREATE TABLE intrus (x INTEGER)",
            ).forEach { sql ->
                val error = assertFailsWith<SQLException>(sql) { connection.createStatement().use { it.execute(sql) } }
                // 42501: insufficient privilege (or not the owner): refused for lack of rights, not by chance.
                assertEquals("42501", error.sqlState, sql)
            }
            // Everything else of the data works.
            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO team_dashboard_alert (id, source, title, level, permission, created_at) " +
                        "VALUES ('00000000-0000-0000-0000-00000000a1e7', 'voir', '[EXEMPLE] x', 1, 'settings.manage', now())",
                )
                assertEquals(1, statement.executeUpdate("UPDATE team_dashboard_alert SET level = 2 WHERE id = '00000000-0000-0000-0000-00000000a1e7'"))
                assertEquals(1, statement.executeUpdate("DELETE FROM team_dashboard_alert WHERE id = '00000000-0000-0000-0000-00000000a1e7'"))
                assertTrue(statement.executeQuery("SELECT count(*) FROM flyway_schema_history").next())
            }
        }
        // The server, connected with this role, checks the migrations without changing anything.
        Database.checkMigrated(app)
        // The callback is idempotent.
        Database.runMigrations(TestDatabase.dataSource, ROLE)
    }

    @Test
    fun aServerThatDoesNotMigrateRefusesAPendingDatabase() {
        superuser("DROP DATABASE IF EXISTS ppf_pending_test")
        superuser("CREATE DATABASE ppf_pending_test")
        val empty = asRole("postgres", "ppf_pending_test")
        val error = assertFailsWith<IllegalStateException> { Database.checkMigrated(empty) }
        assertTrue(error.message.orEmpty().contains("pending"))
        Database.prepare(empty, migrate = true, appRole = "")
        Database.prepare(empty, migrate = false, appRole = "")
    }

    @Test
    fun theMigrationCommandSaysWhereItActsAndPrintsCounts() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val env = mapOf("PPF_ENVIRONMENT" to "preproduction", Database.APP_ROLE_VARIABLE to "")
        assertEquals(0, DatabaseCli.run(listOf("migrate"), env::get, PrintStream(out), PrintStream(err), TestDatabase.dataSource), err.toString())
        val lines = out.toString().lines()
        assertTrue(lines[0].startsWith("Environment: preproduction, database host:"))
        assertEquals("Migrations applied: 0.", lines[1])
        assertEquals(2, DatabaseCli.run(listOf("migrer"), env::get, PrintStream(out), PrintStream(err), TestDatabase.dataSource))
        assertEquals(2, DatabaseCli.run(listOf("migrate"), { if (it == Database.APP_ROLE_VARIABLE) "Mauvais Nom" else null }, PrintStream(out), PrintStream(err), TestDatabase.dataSource))
    }

    private companion object {
        const val ROLE = "ppf_app_test"
    }
}
