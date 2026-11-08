package org.politisationpopulairefrancaise.server.urne

import org.politisationpopulairefrancaise.server.TestDatabase
import java.time.Clock

/** Settings and helpers of the ballot-box tests: a fixed seed, light proofs of work. */
object UrneFixtures {
    val SEED: ByteArray = ByteArray(32) { (it * 7 + 1).toByte() }

    val CONFIG: UrneConfig = UrneConfig(
        seeds = VoteSeeds.of(mapOf(1 to SEED)),
        registerDifficulty = 6,
        tokenDifficulty = 3,
        ballotDifficulty = 3,
        publicUrl = null,
        // The existing tests read a few ballots at once; the hiding of small columns has its own test.
        minDisplay = 1,
    )

    fun service(clock: Clock = Clock.systemUTC()): UrneService = UrneService.create(UrneStore(TestDatabase.dataSource), CONFIG, clock)

    /** The tables of V12__urnes.sql, emptied before each test (the shared reset does not know them). */
    fun reset() {
        TestDatabase.dataSource.connection.use { c ->
            c.createStatement().use { it.executeUpdate("TRUNCATE vote_account, urne, urne_issued, ballot, vote_validation_pending, vote_validation, vote_validation_batch, elected_certification, vote_session_review, deliberation_submission CASCADE") }
        }
    }
}
