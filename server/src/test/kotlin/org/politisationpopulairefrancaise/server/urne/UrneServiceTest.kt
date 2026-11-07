package org.politisationpopulairefrancaise.server.urne

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.urne.Ballot
import org.politisationpopulairefrancaise.core.urne.BlindedRequest
import org.politisationpopulairefrancaise.core.urne.LibsodiumRistretto255
import org.politisationpopulairefrancaise.core.urne.RecountResult
import org.politisationpopulairefrancaise.core.urne.TokenRequest
import org.politisationpopulairefrancaise.core.urne.UrneRecount
import org.politisationpopulairefrancaise.core.urne.VoprfMode
import org.politisationpopulairefrancaise.core.urne.Voprf
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The rules of the ballot boxes that need a clock (D-110, D-112): the level frozen at the opening,
 * the elected officials counted apart, the closing that publishes everything, and the recount.
 */
class UrneServiceTest {
    private val group = runBlocking { LibsodiumRistretto255.load() }
    private val voprf = Voprf(group, VoprfMode.VOPRF)
    private val json = Json

    /** A Wednesday noon (UTC): the week starts on Monday 2 November 2026. */
    private val clock = MutableClock(Instant.parse("2026-11-04T12:00:00Z"))
    private val store = UrneStore(TestDatabase.dataSource)
    private val service = UrneFixtures.service(clock)

    @BeforeTest
    fun emptyTables() = UrneFixtures.reset()

    private fun pubkey() = IdentityKeys.generate().publicKey.hex

    private fun box(id: String, days: Long = 10) =
        service.open(id, "30023:${"b".repeat(64)}:$id", listOf("pour", "contre"), clock.instant(), clock.instant().plus(Duration.ofDays(days)), liveResults = false)

    /** Token request and finalization, as a device does it. */
    private fun tokens(pubkey: String): Map<Long, Triple<String, ByteArray, ByteArray>> {
        val pending = service.status(pubkey).pending
        val ys = pending.associateWith { ByteArray(32).also { SecureRandom().nextBytes(it) } }
        val blinded = ys.mapValues { voprf.blind(it.value) }
        val body = json.encodeToString(TokenRequest.serializer(), TokenRequest(pending.map { BlindedRequest(it, blinded.getValue(it).blindedElement.toHexString()) }))
        val keys = service.list(0, 100).associateBy { it.number }
        return service.issue(pubkey, body).tokens.associate { t ->
            val output = voprf.finalize(ys.getValue(t.urne), blinded.getValue(t.urne), t.evaluated.hexToByteArray(), t.proof.hexToByteArray(), keys.getValue(t.urne).keys.getValue(t.level).hexToByteArray())
            t.urne to Triple(t.level, ys.getValue(t.urne), output)
        }
    }

    private fun vote(number: Long, token: Triple<String, ByteArray, ByteArray>, choice: String) =
        service.ballot(Ballot.withProofOfWork(number, token.first, token.second.toHexString(), token.third.toHexString(), choice, UrneFixtures.CONFIG.ballotDifficulty))

    @Test
    fun theLevelIsTheOneOfTheWeekOfOpening() {
        val citizen = pubkey()
        service.register(citizen)
        val before = box("avant")
        // Validated in person this week: the new level counts from next Monday (9 November).
        store.setLevel(citizen, VoteLevel.VERIFIE, UrneService.mondayOf(clock.instant().plus(Duration.ofDays(5))))
        clock.instant = clock.instant.plus(Duration.ofDays(7))
        val after = box("apres")
        val issued = tokens(citizen)
        assertEquals("non-verifie", issued.getValue(before.number).first, "a box opened before the change keeps the old level")
        assertEquals("verifie", issued.getValue(after.number).first)
    }

    @Test
    fun electedOfficialsAreCountedApartAndTheClosingCanBeRecountedByAnyone() {
        val verified = pubkey()
        val unverified = pubkey()
        val elected = pubkey()
        listOf(verified, unverified, elected).forEach { service.register(it) }
        val lastMonday = UrneService.mondayOf(clock.instant().minus(Duration.ofDays(7)))
        store.setLevel(verified, VoteLevel.VERIFIE, lastMonday)
        store.setLevel(elected, VoteLevel.ELU, lastMonday)
        val number = box("votation", days = 3).number
        vote(number, tokens(verified).getValue(number), "pour")
        vote(number, tokens(unverified).getValue(number), "contre")
        vote(number, tokens(elected).getValue(number), "pour")
        // Results stay hidden until the closing for a question.
        assertEquals(null, service.journal(number).results)
        assertFailsWith<UrneRefusal> { service.closing(number) }
        clock.instant = clock.instant.plus(Duration.ofDays(4))
        assertEquals(1, service.closeDue())
        val closing = service.closing(number)
        assertEquals(mapOf("pour" to 1L), closing.results.verified.counts)
        assertEquals(mapOf("contre" to 1L), closing.results.notVerified.counts)
        assertEquals(mapOf("contre" to 1L, "pour" to 1L), closing.results.citizens.counts)
        assertEquals(mapOf("pour" to 1L), closing.results.electedOfficials.counts, "the elected officials' vote apart")
        assertEquals(2L, closing.results.citizens.ballots, "never added to the citizens' total")
        assertEquals(closing.ballots.map { it.y }.sorted(), closing.ballots.map { it.y }, "sorted by y, never by arrival")
        assertTrue(closing.ballots.all { it.hour % 3600 == 0L }, "times rounded to the hour")
        val announced = service.list(0, 10).single { it.number == number }.keys
        assertEquals(RecountResult.CORRECT, UrneRecount.check(closing, announced, listOf("pour", "contre"), group))
        val tampered = closing.copy(ballots = closing.ballots.map { if (it.level == "elu") it.copy(level = "verifie") else it })
        assertTrue(UrneRecount.check(tampered, announced, listOf("pour", "contre"), group) != RecountResult.CORRECT)
        // After the closing, no ballot is accepted any more.
        val late = assertFailsWith<UrneRefusal> { vote(number, Triple("verifie", ByteArray(32), ByteArray(64)), "pour") }
        assertEquals("urne_closed", late.code)
    }

    @Test
    fun aBoxOpenedLaterIsServedToAnAccountAlreadyServed() {
        val citizen = pubkey()
        service.register(citizen)
        box("premiere")
        assertEquals(1, tokens(citizen).size)
        assertTrue(service.status(citizen).pending.isEmpty())
        val second = box("seconde")
        assertEquals(listOf(second.number), service.status(citizen).pending)
        assertEquals(setOf(second.number), tokens(citizen).keys)
        assertEquals(mapOf("non-verifie" to 1L, "verifie" to 0L, "elu" to 0L), store.issuedByLevel(second.number))
    }

    @Test
    fun theSameIdentifierOpensTheSameBoxAndChoicesAreChecked() {
        val one = box("unique")
        assertEquals(one.number, box("unique").number)
        assertFailsWith<IllegalArgumentException> { service.open("x", "s", listOf("retire"), clock.instant(), null, true) }
        assertFailsWith<IllegalArgumentException> { service.open("x", "s", listOf("pour", "pour"), clock.instant(), null, true) }
    }
}
