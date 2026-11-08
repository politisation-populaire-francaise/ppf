package org.politisationpopulairefrancaise.server.db

import kotlinx.coroutines.runBlocking
import org.postgresql.ds.PGSimpleDataSource
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.AuditActor
import org.politisationpopulairefrancaise.server.backoffice.AuditEntry
import org.politisationpopulairefrancaise.server.backoffice.AuditFilter
import org.politisationpopulairefrancaise.server.backoffice.AuditLog
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.legal.LegalRegister
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
    fun theServerRoleOnlyAppendsToTheLegalRegister() {
        superuser("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$ROLE') THEN CREATE ROLE $ROLE NOLOGIN; END IF; END $$")
        superuser("ALTER ROLE $ROLE LOGIN")
        Database.runMigrations(TestDatabase.dataSource, ROLE)
        TestDatabase.reset()
        val app = asRole(ROLE)
        // Publishing a public content through the server's role writes its register row.
        val note = runBlocking { LocalEventSigner(IdentityKeys.generate()).sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] note", createdAt = 1_000) }
        assertEquals(StoreOutcome.STORED, EventRepository(app).store(note))
        TestDatabase.dataSource.connection.use { connection ->
            val rows = connection.createStatement().executeQuery("SELECT event_id FROM legal_register.content_operation")
            assertTrue(rows.next())
            assertEquals(note.id, rows.getString(1))
        }
        app.connection.use { connection ->
            listOf(
                "SELECT * FROM legal_register.content_operation",
                "SELECT count(*) FROM legal_register.account_pseudonym",
                "SELECT * FROM legal_register.requisition_answer",
                "UPDATE legal_register.content_operation SET kind = 2",
                "DELETE FROM legal_register.content_operation",
                "DELETE FROM legal_register.account_pseudonym",
                "TRUNCATE legal_register.content_operation",
                "TRUNCATE legal_register.account_pseudonym",
                // The reception date is the database's: no row can be backdated, then purged early.
                "INSERT INTO legal_register.content_operation (event_id, operation, received_at, created_at, kind, pubkey, protocol) " +
                    "VALUES ('${"a".repeat(64)}', 'create', now() - interval '2 years', 1, 1, '${"b".repeat(64)}', 1)",
                "INSERT INTO legal_register.account_pseudonym (pubkey, changed_at, event_id, event_created_at) " +
                    "VALUES ('${"b".repeat(64)}', now() - interval '2 years', '${"a".repeat(64)}', 1)",
                // The public counter grows only with a real answer.
                "INSERT INTO legal_register.requisition_answer (approval_id, operation_rows, pseudonym_rows) VALUES (gen_random_uuid(), 0, 0)",
                "INSERT INTO legal_register.content_operation (event_id, operation, created_at, kind, pubkey, protocol) " +
                    "VALUES ('${"a".repeat(64)}', 'create', 1, 1, '${"b".repeat(64)}', 1) RETURNING id",
                "ALTER FUNCTION legal_register.purge() SECURITY INVOKER",
                "CREATE OR REPLACE FUNCTION legal_register.purge() RETURNS TABLE (deleted_operations BIGINT, deleted_pseudonyms BIGINT) LANGUAGE sql AS 'SELECT 0::bigint, 0::bigint'",
                "CREATE TABLE legal_register.intrus (x INTEGER)",
                "DROP TABLE legal_register.content_operation",
            ).forEach { sql ->
                val error = assertFailsWith<SQLException>(sql) { connection.createStatement().use { it.execute(sql) } }
                // 42501: insufficient privilege (or not the owner): refused for lack of rights, not by chance.
                assertEquals("42501", error.sqlState, sql)
            }
            connection.createStatement().use { statement ->
                // Appending the columns it fills works; the purge and the public counter run through their functions.
                statement.execute(
                    "INSERT INTO legal_register.account_pseudonym (pubkey, name, display_name, event_id, event_created_at) " +
                        "VALUES ('${"b".repeat(64)}', '[EXEMPLE] pseudo', NULL, '${"a".repeat(64)}', 1)",
                )
                val purged = statement.executeQuery("SELECT deleted_operations, deleted_pseudonyms FROM legal_register.purge()")
                assertTrue(purged.next())
                assertEquals(0, purged.getLong(1))
                statement.executeQuery("SELECT * FROM legal_register.requisitions_by_month()").close()
            }
            // Reading the rows needs a requisition approved by two people.
            val refused = assertFailsWith<SQLException> {
                connection.createStatement().use { it.executeQuery("SELECT legal_register.answer_requisition(gen_random_uuid())") }
            }
            assertEquals("LR001", refused.sqlState)
        }
        assertEquals(LegalRegister.PurgeReport(0, 0), LegalRegister.purge(app))
        TestDatabase.reset()
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
