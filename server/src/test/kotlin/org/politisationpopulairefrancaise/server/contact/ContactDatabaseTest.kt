package org.politisationpopulairefrancaise.server.contact

import org.postgresql.ds.PGSimpleDataSource
import org.politisationpopulairefrancaise.core.contact.CaseNature
import org.politisationpopulairefrancaise.core.contact.ContactCategory
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.MutableClock
import org.politisationpopulairefrancaise.server.db.Database
import java.sql.SQLException
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The migration of the cases and the rights of the restricted role of the server on them (D-127), on the real PostgreSQL of the tests. */
class ContactDatabaseTest {
    private val clock = MutableClock(Instant.parse("2026-11-08T10:00:00Z"))
    private val baseUrl: String = TestDatabase.dataSource.connection.use { it.metaData.url.substringBefore('?') }

    @BeforeTest
    fun emptyTables() = TestDatabase.reset()

    private fun superuser(sql: String) {
        TestDatabase.dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    @Test
    fun anEnvelopeIsNeverRewrittenEvenByTheOwnerOfTheTable() {
        val space = ContactFixtures.space(clock)
        val draft = ContactFixtures.Draft(CaseNature.Contact(ContactCategory.CITIZEN), clock.instant())
        assertIs<CreateOutcome.Created>(space.create(draft.creation()))
        val error = assertFailsWith<SQLException> { superuser("UPDATE contact_envelope SET event = '{}' WHERE id = '${draft.envelope.id}'") }
        // P0001: raised by the trigger.
        assertEquals("P0001", error.sqlState)
    }

    @Test
    fun theServerRoleAddsAndDeletesEnvelopesButNeverUpdatesThem() {
        superuser("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$ROLE') THEN CREATE ROLE $ROLE NOLOGIN; END IF; END $$")
        superuser("ALTER ROLE $ROLE LOGIN")
        Database.runMigrations(TestDatabase.dataSource, ROLE)
        val app = PGSimpleDataSource().apply {
            setURL(baseUrl)
            user = ROLE
        }
        // The whole path of a case through the server's role: creation, follow-up, retention.
        val space = ContactSpace(ContactStore(app), ContactFixtures.config(), clock)
        val draft = ContactFixtures.Draft(CaseNature.Contact(ContactCategory.UNION), clock.instant())
        assertIs<CreateOutcome.Created>(space.create(draft.creation()))
        assertIs<FollowUpOutcome.Accepted>(space.followUp(draft.number.display, draft.secrets.authorization, org.politisationpopulairefrancaise.core.contact.CaseFollowUp(draft.followUp())))
        app.connection.use { connection ->
            listOf(
                "UPDATE contact_envelope SET event = '{}'",
                "TRUNCATE contact_envelope",
                "ALTER TABLE contact_envelope DISABLE TRIGGER USER",
            ).forEach { sql ->
                val error = assertFailsWith<SQLException>(sql) { connection.createStatement().use { it.execute(sql) } }
                // 42501: insufficient privilege (or not the owner).
                assertEquals("42501", error.sqlState, sql)
            }
            connection.createStatement().use { statement ->
                assertEquals(1, statement.executeUpdate("DELETE FROM contact_case WHERE number = '${draft.number.display}'"))
            }
        }
        assertNull(ContactStore(TestDatabase.dataSource).find(draft.number.display))
        Database.runMigrations(TestDatabase.dataSource, ROLE)
    }

    private companion object {
        const val ROLE = "ppf_app_test"
    }
}
