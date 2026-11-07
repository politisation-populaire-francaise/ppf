package org.politisationpopulairefrancaise.server.urne

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.urne.HttpUrneRemote
import org.politisationpopulairefrancaise.core.urne.LibsodiumRistretto255
import org.politisationpopulairefrancaise.core.urne.UrneAnswer
import org.politisationpopulairefrancaise.core.urne.UrneSelfTestResult
import org.politisationpopulairefrancaise.core.urne.UrneSession
import org.politisationpopulairefrancaise.core.urne.UrneSyncOutcome
import org.politisationpopulairefrancaise.core.urne.UrneTokenStore
import org.politisationpopulairefrancaise.core.urne.VoteOutcome
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The device's voting session against the real server (D-110, step 3): the same code as the
 * applications, with its local database and its HTTP client, talking to the ballot-box API.
 */
class UrneSessionEndToEndTest {
    private val group = runBlocking { LibsodiumRistretto255.load() }
    private val signer = LocalEventSigner(IdentityKeys.generate())

    /** The device's clock, moved by hand to send delayed ballots. */
    private var deviceNow = Instant.now().epochSecond

    @BeforeTest
    fun emptyTables() = UrneFixtures.reset()

    private fun ApplicationTestBuilder.session(selfTest: Boolean = true, delay: Int = 120): Pair<UrneSession, UrneTokenStore> {
        application { module(org.politisationpopulairefrancaise.server.TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), urne = UrneFixtures.CONFIG) }
        val remote = HttpUrneRemote(client, createClient {}, "http://localhost") { Instant.now().epochSecond }
        val store = UrneTokenStore(JvmDatabase.inMemory())
        val session = UrneSession(
            store, remote, group,
            now = { deviceNow },
            selfTest = { UrneSelfTestResult(selfTest, if (selfTest) null else "load") },
            randomDelay = { delay },
        )
        return session to store
    }

    private fun openBox(id: String) {
        val now = Instant.now()
        UrneFixtures.service().open(id, "30023:${"c".repeat(64)}:$id", listOf("pour", "contre"), now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), liveResults = true)
    }

    @Test
    fun aDeviceGetsItsTokensVotesLaterAndCanChangeItsVote() = testApplication {
        val (session, store) = session()
        openBox("e2e-1")
        openBox("e2e-2")
        val synced = session.sync(signer)
        assertEquals(UrneSyncOutcome.Done(received = 2, pending = 0), synced)
        assertEquals(UrneSyncOutcome.Done(received = 0, pending = 0), session.sync(signer), "nothing new to ask for")
        val number = store.tokens().first().urneNumber
        val scheduled = session.vote(number, "pour", listOf("pour", "contre"))
        assertIs<VoteOutcome.Scheduled>(scheduled)
        assertEquals(deviceNow + 120, scheduled.sendAfter, "the ballot waits before leaving")
        // Not yet: nothing leaves before its moment.
        assertEquals(0, session.sendDue(UrneFixtures.CONFIG.ballotDifficulty))
        deviceNow += 121
        assertEquals(1, session.sendDue(UrneFixtures.CONFIG.ballotDifficulty))
        assertEquals("pour" to true, session.choiceOf(number))
        // Changing one's mind: the new ballot replaces the first one.
        session.vote(number, "contre", listOf("pour", "contre"))
        assertEquals("contre" to false, session.choiceOf(number))
        deviceNow += 121
        assertEquals(1, session.sendDue(UrneFixtures.CONFIG.ballotDifficulty))
        val journal = UrneFixtures.service().journal(number)
        assertEquals(mapOf("contre" to 1L), journal.results!!.citizens.counts)
        assertEquals(1L, journal.replacements)
        // The server knows the account registered, never which ballot is its own.
        val remoteStatus = HttpUrneRemote(client, client, "http://localhost") { Instant.now().epochSecond }.status(signer)
        assertIs<UrneAnswer.Ok<*>>(remoteStatus)
    }

    @Test
    fun votingStaysClosedWhenTheSelfTestFailed() = testApplication {
        val (session, _) = session(selfTest = false)
        openBox("ferme")
        assertEquals(UrneSyncOutcome.VotingClosedOnThisDevice, session.sync(signer))
        assertEquals(VoteOutcome.VotingClosedOnThisDevice, session.vote(1, "pour", listOf("pour")))
    }

    @Test
    fun anotherAccountStartsFromNothing() = testApplication {
        val (session, store) = session()
        openBox("compte")
        session.sync(signer)
        assertEquals(1, store.tokens().size)
        session.sync(LocalEventSigner(IdentityKeys.generate()))
        assertTrue(store.tokens().size == 1 && store.state()?.first != signer.publicKey.hex, "the first account's tokens are gone")
    }
}
