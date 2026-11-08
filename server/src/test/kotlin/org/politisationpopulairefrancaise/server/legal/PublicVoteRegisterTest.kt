package org.politisationpopulairefrancaise.server.legal

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.urne.PublicVote
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import org.politisationpopulairefrancaise.server.urne.PublicVoteWrite
import org.politisationpopulairefrancaise.server.urne.UrneFixtures
import org.politisationpopulairefrancaise.server.urne.UrneRefusal
import org.politisationpopulairefrancaise.server.urne.UrneService
import org.politisationpopulairefrancaise.server.urne.UrneStore
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The public votes of certified elected officials in the legal register (D-077 follow-up, D-115): public and
 * nominative, they are recorded on creation, replacement and withdrawal, in the transaction of the vote;
 * a refused or rolled back vote leaves nothing, and the anonymous ballots never enter the register.
 */
class PublicVoteRegisterTest {
    /** A Wednesday noon (UTC). */
    private val clock = MutableClock(Instant.parse("2026-11-04T12:00:00Z"))
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val store = UrneStore(TestDatabase.dataSource, LegalRegister { it == editor.publicKey.hex })
    private val service = UrneService.create(store, UrneFixtures.CONFIG, clock)
    private val elected = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyDatabase() {
        UrneFixtures.reset()
        TestDatabase.reset()
    }

    private data class Row(val eventId: String, val operation: String, val kind: Int, val pubkey: String, val removedId: String?, val removedCreatedAt: Long?)

    private fun operations(): List<Row> = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery(
                "SELECT event_id, operation, kind, pubkey, removed_event_id, removed_created_at FROM legal_register.content_operation ORDER BY id",
            )
            buildList {
                while (rows.next()) {
                    add(Row(rows.getString(1), rows.getString(2), rows.getInt(3), rows.getString(4), rows.getString(5), rows.getObject(6)?.let { rows.getLong(6) }))
                }
            }
        }
    }

    /** Everything the register holds, as text, to look for what must never be there. */
    private fun everything(): String = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val rows = statement.executeQuery(
                "SELECT (SELECT coalesce(string_agg(row_to_json(c)::text, ' '), '') FROM legal_register.content_operation c) || " +
                    "(SELECT coalesce(string_agg(row_to_json(p)::text, ' '), '') FROM legal_register.account_pseudonym p)",
            )
            rows.next()
            rows.getString(1)
        }
    }

    private fun certify(signer: LocalEventSigner) {
        val lastMonday = UrneService.mondayOf(clock.instant().minus(Duration.ofDays(7)))
        service.register(signer.publicKey.hex)
        store.setLevel(signer.publicKey.hex, VoteLevel.ELU, lastMonday, lastMonday)
    }

    private fun box(id: String): UrneInfo {
        val urne = service.open(id, "30023:${"b".repeat(64)}:$id", listOf("pour", "contre", "blanc"), clock.instant().minus(Duration.ofHours(1)), clock.instant().plus(Duration.ofDays(3)), liveResults = true)
        return service.list(0, 10).single { it.number == urne.number }
    }

    private fun sign(signer: LocalEventSigner, urne: UrneInfo, choice: String, at: Long): SignedEvent = runBlocking { PublicVote.sign(signer, urne, choice, at) }

    @Test
    fun aPublicVoteIsRecordedWhenCreatedReplacedAndWithdrawn() {
        certify(elected)
        val urne = box("cantine")
        val key = elected.publicKey.hex
        val t = clock.instant().epochSecond - 600
        val first = sign(elected, urne, "pour", t).also(service::publicVote)
        val second = sign(elected, urne, "contre", t + 60).also(service::publicVote)
        // An older vote arriving late changes nothing, in the box or in the register.
        sign(elected, urne, "blanc", t + 30).also(service::publicVote)
        val withdrawal = sign(elected, urne, UrneInfo.WITHDRAWN, t + 120).also(service::publicVote)
        // A withdrawal with no vote to remove: no content, nothing recorded.
        sign(elected, urne, UrneInfo.WITHDRAWN, t + 180).also(service::publicVote)
        // A vote after a withdrawal is a creation again.
        val back = sign(elected, urne, "pour", t + 240).also(service::publicVote)
        assertEquals(
            listOf(
                Row(first.id, "create", PublicVote.KIND, key, null, null),
                Row(second.id, "replace", PublicVote.KIND, key, first.id, t),
                Row(withdrawal.id, "delete", PublicVote.KIND, key, second.id, t + 60),
                Row(back.id, "create", PublicVote.KIND, key, null, null),
            ),
            operations(),
        )
        assertEquals("pour", PublicVote.parse(service.publicVotes(urne.number).votes.single())!!.choice)
        // Never the content of the vote, its choice or its tags.
        val register = everything()
        listOf("pour", "contre", "blanc", UrneInfo.WITHDRAWN, "ppf-vote", urne.subject).forEach { assertFalse(register.contains(it), it) }
    }

    @Test
    fun aRefusedOrRolledBackVoteLeavesNothing() {
        certify(elected)
        val urne = box("piscine")
        val t = clock.instant().epochSecond - 60
        // Not a certified elected official: refused before any write.
        val citizen = LocalEventSigner(IdentityKeys.generate())
        service.register(citizen.publicKey.hex)
        assertEquals("not_elected", assertFailsWith<UrneRefusal> { service.publicVote(sign(citizen, urne, "pour", t)) }.code)
        // The register refuses its row (here, an identifier that is not one): the vote is rolled back with it.
        val broken = sign(elected, urne, "pour", t).copy(id = "z".repeat(64))
        assertFailsWith<SQLException> { store.recordPublicVote(PublicVote.parse(broken)!!, clock.instant()) }
        assertTrue(store.publicVotes(urne.number).isEmpty(), "both or neither")
        // The box closed when the transaction locks it: nothing written.
        val vote = PublicVote.parse(sign(elected, urne, "pour", t))!!
        assertEquals(PublicVoteWrite.CLOSED, store.recordPublicVote(vote, clock.instant().plus(Duration.ofDays(4))))
        assertTrue(store.publicVotes(urne.number).isEmpty())
        assertEquals(emptyList(), operations())
    }

    @Test
    fun theVotesOfAnEditorialKeyAreNotRecorded() {
        certify(editor)
        val urne = box("bibliotheque")
        service.publicVote(sign(editor, urne, "pour", clock.instant().epochSecond - 60))
        assertEquals(1, service.publicVotes(urne.number).votes.size)
        assertEquals(emptyList(), operations(), "PPF answers for its own keys as their editor, like in recordStored")
    }
}
