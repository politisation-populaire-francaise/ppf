package org.politisationpopulairefrancaise.server.agir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.DAY
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.HOUR
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.callTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilizationTags
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** The review queue of the Agir space (table held_content), on the embedded PostgreSQL. */
class HeldQueueTest {
    private val author = LocalEventSigner(IdentityKeys.generate())
    private val events = EventRepository(TestDatabase.dataSource)
    private val origin = Instant.parse("2026-11-06T12:00:00Z")
    private val clock = MutableClock(origin)
    private val queue = HeldQueue(TestDatabase.dataSource, events, clock)

    /** Ten days after the clock of the queue: never expired unless the test moves the clock. */
    private val later = origin.epochSecond + 10 * DAY

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun LocalEventSigner.event(d: String = "manif-exemple", title: String = "[EXEMPLE] Manifestation", start: Long = later, end: Long? = null, createdAt: Long = 1_000) =
        mobilization(mobilizationTags(d = d, title = title, start = start, end = end), createdAt = createdAt)

    @Test
    fun aNewVersionReplacesThePendingOneAndAnOlderOneIsDropped() = runBlocking {
        val first = author.event(createdAt = 1_000)
        val second = author.event(title = "[EXEMPLE] Lieu corrigé", createdAt = 2_000)
        val older = author.event(title = "[EXEMPLE] Ancienne version", createdAt = 500)
        assertEquals(HoldOutcome.Queued(null), queue.submit(first))
        assertEquals(HoldOutcome.AlreadyPending, queue.submit(first))
        assertEquals(HoldOutcome.Queued(first.id), queue.submit(second))
        assertNull(queue.find(first.id))
        assertEquals(HoldOutcome.Superseded, queue.submit(older))
        assertEquals(listOf(second.id), queue.pending().map { it.id })
        assertEquals(second, queue.find(second.id)?.event)
        assertEquals(second, queue.pendingAt("31923:${author.publicKey.hex}:manif-exemple")?.event)
        assertEquals(origin.epochSecond, queue.find(second.id)?.receivedAt)
    }

    @Test
    fun thereIsNoCeilingOnTheQueue() = runBlocking {
        // Decision of the owner (2026-11-07): no limit per key, per day nor for the whole server.
        val contents = (1..40).map { author.event(d = "manif-$it") }
        contents.forEach { assertIs<HoldOutcome.Queued>(queue.submit(it)) }
        assertEquals(40, queue.pending().size)
    }

    @Test
    fun concurrentVersionsOfOneAddressLeaveOnePendingVersion() = runBlocking {
        val versions = (1..16).map { author.event(title = "[EXEMPLE] Version $it", createdAt = 1_000L + it) }
        versions.map { version -> async(Dispatchers.IO) { queue.submit(version) } }.awaitAll()
        assertEquals(listOf(versions.last().id), queue.pending().map { it.id })
    }

    @Test
    fun approvingPublishesExactlyWhatWasSigned() = runBlocking {
        val held = author.event()
        queue.submit(held)
        clock.advanceSeconds(90)
        val decision = assertIs<HeldDecision.Approved>(queue.approve(held.id))
        assertEquals(StoreOutcome.STORED, decision.stored)
        assertEquals(held, events.find(held.id))
        val state = queue.find(held.id)!!
        assertEquals(HeldStatus.APPROVED, state.status)
        assertEquals(origin.epochSecond + 90, state.decidedAt)
        assertEquals(HeldDecision.AlreadyDecided(HeldStatus.APPROVED), queue.approve(held.id))
        assertEquals(HeldDecision.AlreadyDecided(HeldStatus.APPROVED), queue.reject(held.id, "trop tard"))
        assertEquals(HoldOutcome.AlreadyApproved, queue.submit(held))
        // A version older than the published one is not even queued.
        assertEquals(HoldOutcome.Superseded, queue.submit(author.event(createdAt = 900)))
        assertEquals(HeldDecision.NotFound, queue.approve("f".repeat(64)))
    }

    @Test
    fun anApprovedVersionOlderThanThePublishedOneIsNotPublished() = runBlocking {
        val held = author.event(createdAt = 1_000)
        queue.submit(held)
        // Meanwhile the author became trusted and published a newer version directly.
        val newer = author.event(title = "[EXEMPLE] Version directe", createdAt = 2_000)
        assertEquals(StoreOutcome.STORED, events.store(newer))
        assertEquals(StoreOutcome.SUPERSEDED, assertIs<HeldDecision.Approved>(queue.approve(held.id)).stored)
        assertEquals(newer, events.find(newer.id))
        assertNull(events.find(held.id))
    }

    @Test
    fun rejectingKeepsTheReasonAndPublishesNothing() = runBlocking {
        val held = author.call("debattre")
        queue.submit(held)
        assertFailsWith<IllegalArgumentException> { queue.reject(held.id, "  ") }
        assertFailsWith<IllegalArgumentException> { queue.reject(held.id, "x".repeat(HeldQueue.MAX_REASON_LENGTH + 1)) }
        assertEquals(HeldDecision.Rejected, queue.reject(held.id, "  [EXEMPLE] Doublon d'un appel existant.  "))
        val state = queue.find(held.id)!!
        assertEquals(HeldStatus.REJECTED, state.status)
        assertEquals("[EXEMPLE] Doublon d'un appel existant.", state.reason)
        assertNull(events.find(held.id))
        assertEquals(HoldOutcome.AlreadyRejected, queue.submit(held))
        assertEquals(HeldDecision.AlreadyDecided(HeldStatus.REJECTED), queue.approve(held.id))
    }

    @Test
    fun aMobilizationWhoseDatePassedIsRefusedAutomatically() = runBlocking {
        val now = origin.epochSecond
        val withoutEnd = author.event(d = "sans-fin", start = now + HOUR)
        val withEnd = author.event(d = "avec-fin", start = now + HOUR, end = now + 3 * DAY)
        // A call is never refused for its dates: a call for witnesses describes past facts by nature.
        val witnesses = author.call(AgirFormats.CALL_TYPE_WITNESSES, callTags(AgirFormats.CALL_TYPE_WITNESSES, start = now - 10 * DAY))
        listOf(withoutEnd, withEnd, witnesses).forEach { queue.submit(it) }
        assertEquals(0, queue.rejectExpired())

        // The start passed: the event without end is over, the other one is under way.
        clock.advanceSeconds(2 * HOUR)
        val states = queue.states(listOf(withoutEnd.id, withEnd.id, witnesses.id)).associateBy { it.id }
        assertEquals(HeldStatus.REJECTED, states.getValue(withoutEnd.id).status)
        assertEquals(HeldQueue.EXPIRED_REASON, states.getValue(withoutEnd.id).reason)
        assertEquals(origin.epochSecond + 2 * HOUR, states.getValue(withoutEnd.id).decidedAt)
        assertEquals(HeldStatus.PENDING, states.getValue(withEnd.id).status)
        assertEquals(HeldStatus.PENDING, states.getValue(witnesses.id).status)

        // The end passed: the team can no longer approve it, the periodic cleanup refuses it as well.
        clock.advanceSeconds(3 * DAY)
        assertEquals(HeldDecision.AlreadyDecided(HeldStatus.REJECTED), queue.approve(withEnd.id))
        assertEquals(HeldQueue.EXPIRED_REASON, queue.find(withEnd.id)?.reason)
        assertNull(events.find(withEnd.id))
        assertEquals(HeldStatus.PENDING, queue.find(witnesses.id)?.status)
        val other = author.event(d = "autre", start = origin.epochSecond + 3 * DAY)
        queue.submit(other)
        HeldMaintenance(queue, 30.days, clock).purgeOnce()
        assertEquals(HeldStatus.REJECTED, queue.find(other.id)?.status)
        assertEquals(listOf(witnesses.id), queue.pending().map { it.id })
    }

    @Test
    fun anEndingVersionWithdrawsTheOlderPendingVersion() = runBlocking {
        val pending = author.event(createdAt = 1_000)
        queue.submit(pending)
        // An older version (or the same date with a greater id) never withdraws anything.
        assertFalse(queue.withdrawOlderPending(author.mobilization(mobilizationTags(start = later, status = "annule"), createdAt = 900)))
        assertTrue(queue.withdrawOlderPending(author.mobilization(mobilizationTags(start = later, status = "annule"), createdAt = 2_000)))
        val state = queue.find(pending.id)!!
        assertEquals(HeldStatus.REJECTED, state.status)
        assertEquals(AuthorChanges.WITHDRAWN_REASON, state.reason)
        assertFalse(queue.withdrawOlderPending(author.mobilization(mobilizationTags(start = later, status = "annule"), createdAt = 3_000)))
    }

    @Test
    fun decidedContentsAreDeletedThirtyDaysAfterTheDecision() = runBlocking {
        val farAway = origin.epochSecond + 1_000 * DAY
        val approved = author.event(d = "approuve", start = farAway)
        val rejected = author.event(d = "refuse", start = farAway)
        val pending = author.event(d = "en-attente", start = farAway)
        listOf(approved, rejected, pending).forEach { queue.submit(it) }
        queue.approve(approved.id)
        clock.advanceSeconds(3_600)
        queue.reject(rejected.id, "[EXEMPLE] motif")
        val maintenance = HeldMaintenance(queue, 30.days, clock)

        clock.advanceSeconds(30L * 86_400)
        // Exactly 30 days after the rejection: kept; the approval, one hour older, goes.
        assertEquals(1, maintenance.purgeOnce())
        assertNull(queue.find(approved.id))
        assertEquals(HeldStatus.REJECTED, queue.find(rejected.id)?.status)
        clock.advanceSeconds(1)
        assertEquals(1, maintenance.purgeOnce())
        assertNull(queue.find(rejected.id))
        // A pending content stays as long as its date is ahead; the published one lives on in the events table.
        clock.advanceSeconds(400L * 86_400)
        assertEquals(0, maintenance.purgeOnce())
        assertEquals(HeldStatus.PENDING, queue.find(pending.id)?.status)
        assertEquals(approved, events.find(approved.id))
        // Once deleted, an id is simply unknown to its author.
        assertEquals(listOf(pending.id), queue.states(listOf(approved.id, rejected.id, pending.id)).map { it.id })
    }

    @Test
    fun theTableHoldsNothingAboutTheRequest() {
        val columns = TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_name = 'held_content'").use { statement ->
                val rows = statement.executeQuery()
                buildSet { while (rows.next()) add(rows.getString(1)) }
            }
        }
        assertEquals(
            setOf("id", "pubkey", "kind", "identifier", "address", "created_at", "event", "tags", "expires_at", "status", "received_at", "decided_at", "decision_reason"),
            columns,
        )
    }

    @Test
    fun usefulTagsAreAShortCopyOfTheEvent() = runBlocking {
        val event = author.mobilization(mobilizationTags(organizers = listOf("a".repeat(64))), content = "[EXEMPLE] texte long")
        val tags = HeldQueue.usefulTags(event)
        assertEquals(setOf("d", "title", "start", "ppf-event-type", "ppf-territory"), tags.map { it[0] }.toSet())
        assertTrue(tags.all { it.size == 2 })
        assertEquals(event.firstTagValue("start"), tags.first { it[0] == "start" }[1])
        assertEquals(event.firstTagValue("start")?.toLong(), HeldQueue.expiryOf(event))
        assertNull(HeldQueue.expiryOf(author.call("debattre")))
    }
}
