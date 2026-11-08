package org.politisationpopulairefrancaise.server

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.politisationpopulairefrancaise.server.db.Database
import java.time.Duration
import javax.sql.DataSource

/**
 * One real PostgreSQL 17, started in-process for the whole test run (binaries come from Maven
 * Central: nothing to install). Migrated once; emptied before each test with [reset].
 */
object TestDatabase {
    val dataSource: DataSource by lazy {
        // A loaded machine (several builds at once, continuous integration) can take longer than the
        // 10 seconds the library waits by default for PostgreSQL to start.
        val postgres = EmbeddedPostgres.builder().setPGStartupWait(Duration.ofMinutes(3)).start()
        Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
        postgres.postgresDatabase.also(Database::migrate)
    }

    fun reset() {
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.executeUpdate(
                    "TRUNCATE events, revendication_proposal, held_content, elus_import, elus_staging, territoires_import, " +
                        "testimony_submission, testimony_evidence, testimony_file, published_media, push_subscription, group_role, group_ban, " +
                        "group_removed_event, group_edit, group_held_creation, account_type, recovery_commitment, compromised_account, group_orphan, " +
                        "legal_register.content_operation, legal_register.account_pseudonym, legal_register.requisition_answer, " +
                        "editorial_signing, editorial_review, editorial_draft_version, editorial_draft, " +
                        // Décider: the ballot boxes and what they hold, the validations, the petitions to sign, the proofs
                        // of the archive; their background tasks must find nothing left by another test.
                        "vote_account, urne, urne_issued, ballot, public_vote, vote_validation_pending, vote_validation, " +
                        "vote_validation_batch, elected_certification, vote_session_review, deliberation_submission, petition_draft, " +
                        "archive_proof CASCADE",
                )
            }
        }
    }
}
