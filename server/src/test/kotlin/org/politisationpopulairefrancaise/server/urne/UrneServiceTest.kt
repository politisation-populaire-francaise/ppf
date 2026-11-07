package org.politisationpopulairefrancaise.server.urne

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.urne.Ballot
import org.politisationpopulairefrancaise.core.urne.BlindedRequest
import org.politisationpopulairefrancaise.core.urne.LibsodiumRistretto255
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.urne.PublicVote
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

    private fun box(id: String, days: Long = 10, opensAt: Instant = clock.instant()) =
        service.open(id, "30023:${"b".repeat(64)}:$id", listOf("pour", "contre"), opensAt, clock.instant().plus(Duration.ofDays(days)), liveResults = false)

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
        store.setLevel(citizen, VoteLevel.VERIFIE, UrneService.mondayOf(clock.instant().plus(Duration.ofDays(5))), UrneService.mondayOf(clock.instant()))
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
        val electedSigner = LocalEventSigner(IdentityKeys.generate())
        val elected = electedSigner.publicKey.hex
        listOf(verified, unverified, elected).forEach { service.register(it) }
        val lastMonday = UrneService.mondayOf(clock.instant().minus(Duration.ofDays(7)))
        store.setLevel(verified, VoteLevel.VERIFIE, lastMonday, lastMonday)
        store.setLevel(elected, VoteLevel.ELU, lastMonday, lastMonday)
        val number = box("votation", days = 3).number
        vote(number, tokens(verified).getValue(number), "pour")
        vote(number, tokens(unverified).getValue(number), "contre")
        // An elected official gets no anonymous token: their vote is public, signed with their key (D-115).
        assertTrue(tokens(elected).isEmpty())
        val info = service.list(0, 10).single { it.number == number }
        service.publicVote(runBlocking { PublicVote.sign(electedSigner, info, "pour", clock.instant().epochSecond) })
        // A citizen cannot vote publicly as an official, nor an official with an anonymous ballot.
        val citizen = LocalEventSigner(IdentityKeys.generate())
        service.register(citizen.publicKey.hex)
        assertEquals("not_elected", assertFailsWith<UrneRefusal> { service.publicVote(runBlocking { PublicVote.sign(citizen, info, "pour", clock.instant().epochSecond) }) }.code)
        assertEquals("elected_vote_publicly", assertFailsWith<UrneRefusal> { vote(number, Triple("elu", ByteArray(32), ByteArray(64)), "pour") }.code)
        assertEquals(1, service.publicVotes(number).votes.size)
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
        assertTrue(closing.ballots.all { it.day % 86_400 == 0L }, "times published by the day only")
        val announced = service.list(0, 10).single { it.number == number }.keys
        assertEquals(RecountResult.CORRECT, UrneRecount.check(closing, announced, listOf("pour", "contre"), group))
        assertEquals(1, closing.publicVotes.size, "the officials' signed votes are published with the closing")
        val tampered = closing.copy(publicVotes = emptyList())
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
    fun aFewBallotsOfOneLevelAreHiddenWhileOpenAndPublishedAtTheClosing() {
        val shown = UrneService.create(store, UrneFixtures.CONFIG.copy(minDisplay = 5), clock)
        val citizen = pubkey()
        service.register(citizen)
        val number = service.open("petite", "30023:${"b".repeat(64)}:petite", listOf("pour", "contre"), clock.instant(), clock.instant().plus(Duration.ofDays(1)), liveResults = true).number
        vote(number, tokens(citizen).getValue(number), "pour")
        val live = shown.list(0, 10).single().results!!
        assertTrue(live.notVerified.hidden && live.citizens.hidden)
        assertEquals(5, live.minimum)
        assertTrue(shown.journal(number).ballots["non-verifie"] == null, "the number of ballots of the level is hidden too")
        clock.instant = clock.instant.plus(Duration.ofDays(2))
        shown.closeDue()
        val closed = shown.journal(number).results!!
        assertEquals(mapOf("pour" to 1L), closed.citizens.counts)
        assertEquals(null, closed.minimum)
    }

    @Test
    fun anEarlyClosingBringsTheEndForwardButNeverPushesItBack() {
        val planned = box("annulee", days = 10)
        val end = planned.closesAt!!
        // Asked for later than planned: nothing changes.
        assertEquals(end, service.closeEarly("annulee", end.plus(Duration.ofDays(3)))?.closesAt)
        // Asked for tomorrow: the end moves forward, the box stays open until then.
        val tomorrow = clock.instant().plus(Duration.ofDays(1))
        val moved = service.closeEarly("annulee", tomorrow)!!
        assertEquals(tomorrow, moved.closesAt)
        assertEquals(false, moved.closed)
        // A box without an end, closed at a moment already passed: closed now, not in the past.
        service.open("sans-fin", "30023:${"b".repeat(64)}:sans-fin", listOf("pour", "contre"), clock.instant(), null, liveResults = true)
        val closed = service.closeEarly("sans-fin", clock.instant().minus(Duration.ofDays(2)))!!
        assertEquals(clock.instant(), closed.closesAt)
        assertTrue(closed.closed)
        assertEquals(null, service.closeEarly("inconnue", clock.instant()))
        // The usual course then closes the first one when its new end comes.
        clock.instant = tomorrow
        assertEquals(1, service.closeDue())
    }

    @Test
    fun twoChangesInOneWeekKeepTheLevelInForceForTheBoxesOpenedMeanwhile() {
        val citizen = pubkey()
        service.register(citizen)
        val thisWeek = UrneService.mondayOf(clock.instant())
        val nextWeek = thisWeek.plusDays(7)
        // Validated, then certified elected the same week: neither counts before next Monday.
        store.setLevel(citizen, VoteLevel.VERIFIE, nextWeek, thisWeek)
        store.setLevel(citizen, VoteLevel.ELU, nextWeek, thisWeek)
        val meanwhile = box("entre-deux")
        assertEquals("non-verifie", tokens(citizen).getValue(meanwhile.number).first)
        val status = service.status(citizen)
        assertEquals("elu", status.level)
        assertEquals("non-verifie", status.previousLevel)
        assertEquals(thisWeek.toString(), status.registeredWeek)
    }

    @Test
    fun aBoxIsNeverOpenedInAdvanceAndHasItsOwnKeys() {
        // A box numbered now and opened later would be skipped by every account served meanwhile.
        assertFailsWith<IllegalArgumentException> { box("plus-tard", opensAt = clock.instant().plusSeconds(3600)) }
        // Two boxes: different salts, hence different keys, whatever their identifiers.
        val one = box("une")
        val two = box("deux")
        assertTrue(!one.salt.contentEquals(two.salt))
        val keys = service.list(0, 10).associate { it.number to it.keys }
        assertTrue(keys.getValue(one.number).values.intersect(keys.getValue(two.number).values.toSet()).isEmpty())
    }

    @Test
    fun theSameIdentifierOpensTheSameBoxAndChoicesAreChecked() {
        val one = box("unique")
        assertEquals(one.number, box("unique").number)
        assertFailsWith<IllegalArgumentException> { service.open("x", "s", listOf("retire"), clock.instant(), null, true) }
        assertFailsWith<IllegalArgumentException> { service.open("x", "s", listOf("pour", "pour"), clock.instant(), null, true) }
    }
}
