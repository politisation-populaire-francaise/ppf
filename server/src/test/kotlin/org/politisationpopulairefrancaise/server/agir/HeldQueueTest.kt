package org.politisationpopulairefrancaise.server.agir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilizationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.nowSeconds
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import org.politisationpopulairefrancaise.server.revendications.PeriodBudget
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun budget(permits: Int = 100) = PeriodBudget(permits)

    @Test
    fun aNewVersionReplacesThePendingOneAndAnOlderOneIsDropped() = runBlocking {
        val first = author.mobilization(createdAt = 1_000)
        val second = author.mobilization(mobilizationTags(title = "[EXEMPLE] Lieu corrigé"), createdAt = 2_000)
        val older = author.mobilization(mobilizationTags(title = "[EXEMPLE] Ancienne version"), createdAt = 500)
        assertEquals(HoldOutcome.Queued(null), queue.submit(first, 10, 100, budget()))
        assertEquals(HoldOutcome.AlreadyPending, queue.submit(first, 10, 100, budget()))
        assertEquals(HoldOutcome.Queued(first.id), queue.submit(second, 10, 100, budget()))
        assertNull(queue.find(first.id))
        assertEquals(HoldOutcome.Superseded, queue.submit(older, 10, 100, budget()))
        assertEquals(listOf(second.id), queue.pending().map { it.id })
        // The useful tags are kept next to the event, the event itself as signed.
        assertEquals(second, queue.find(second.id)?.event)
        assertEquals(origin.epochSecond, queue.find(second.id)?.receivedAt)
    }

    @Test
    fun approvingPublishesExactlyWhatWasSigned() = runBlocking {
        val held = author.mobilization(createdAt = 1_000)
        queue.submit(held, 10, 100, budget())
        clock.advanceSeconds(90)
        val decision = assertIs<HeldDecision.Approved>(queue.approve(held.id))
        assertEquals(StoreOutcome.STORED, decision.stored)
        assertEquals(held, events.find(held.id))
        val state = queue.find(held.id)!!
        assertEquals(HeldStatus.APPROVED, state.status)
        assertEquals(origin.epochSecond + 90, state.decidedAt)
        assertEquals(HeldDecision.AlreadyDecided(HeldStatus.APPROVED), queue.approve(held.id))
        assertEquals(HeldDecision.AlreadyDecided(HeldStatus.APPROVED), queue.reject(held.id, "trop tard"))
        assertEquals(HoldOutcome.AlreadyApproved, queue.submit(held, 10, 100, budget()))
        // A version older than the published one is not even queued.
        assertEquals(HoldOutcome.Superseded, queue.submit(author.mobilization(createdAt = 900), 10, 100, budget()))
        assertEquals(HeldDecision.NotFound, queue.approve("f".repeat(64)))
    }

    @Test
    fun anApprovedVersionOlderThanThePublishedOneIsNotPublished() = runBlocking {
        val held = author.mobilization(createdAt = 1_000)
        queue.submit(held, 10, 100, budget())
        // Meanwhile the author became trusted and published a newer version directly.
        val newer = author.mobilization(mobilizationTags(title = "[EXEMPLE] Version directe"), createdAt = 2_000)
        assertEquals(StoreOutcome.STORED, events.store(newer))
        assertEquals(StoreOutcome.SUPERSEDED, assertIs<HeldDecision.Approved>(queue.approve(held.id)).stored)
        assertEquals(newer, events.find(newer.id))
        assertNull(events.find(held.id))
    }

    @Test
    fun rejectingKeepsTheReasonAndPublishesNothing() = runBlocking {
        val held = author.call("debattre")
        queue.submit(held, 10, 100, budget())
        assertFailsWith<IllegalArgumentException> { queue.reject(held.id, "  ") }
        assertFailsWith<IllegalArgumentException> { queue.reject(held.id, "x".repeat(HeldQueue.MAX_REASON_LENGTH + 1)) }
        assertEquals(HeldDecision.Rejected, queue.reject(held.id, "  [EXEMPLE] Doublon d'un appel existant.  "))
        val state = queue.find(held.id)!!
        assertEquals(HeldStatus.REJECTED, state.status)
        assertEquals("[EXEMPLE] Doublon d'un appel existant.", state.reason)
        assertNull(events.find(held.id))
        assertEquals(HoldOutcome.AlreadyRejected, queue.submit(held, 10, 100, budget()))
        assertEquals(HeldDecision.AlreadyDecided(HeldStatus.REJECTED), queue.approve(held.id))
    }

    @Test
    fun decidedContentsAreDeletedThirtyDaysAfterTheDecision() = runBlocking {
        val approved = author.mobilization(mobilizationTags(d = "approuve"))
        val rejected = author.mobilization(mobilizationTags(d = "refuse"))
        val pending = author.mobilization(mobilizationTags(d = "en-attente"))
        listOf(approved, rejected, pending).forEach { queue.submit(it, 10, 100, budget()) }
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
        // A pending content stays, however old; the published one lives on in the events table.
        clock.advanceSeconds(400L * 86_400)
        assertEquals(0, maintenance.purgeOnce())
        assertEquals(HeldStatus.PENDING, queue.find(pending.id)?.status)
        assertEquals(approved, events.find(approved.id))
        // Once deleted, an id is simply unknown to its author.
        assertEquals(listOf(pending.id), queue.states(listOf(approved.id, rejected.id, pending.id)).map { it.id })
    }

    @Test
    fun theGlobalCeilingHoldsUnderConcurrentRequests() = runBlocking {
        val authors = List(16) { LocalEventSigner(IdentityKeys.generate()) }
        val contents = authors.map { it.mobilization() }
        val outcomes = contents.map { content -> async(Dispatchers.IO) { queue.submit(content, 10, 5, budget()) } }.awaitAll()
        assertEquals(5, outcomes.count { it is HoldOutcome.Queued })
        assertEquals(11, outcomes.count { it == HoldOutcome.QueueFull })
        assertEquals(5, queue.pending().size)
    }

    @Test
    fun theDailyBudgetIsRefundedWhenNothingIsStored() = runBlocking<Unit> {
        val once = budget(1)
        assertIs<HoldOutcome.Queued>(queue.submit(author.mobilization(mobilizationTags(d = "un")), 10, 100, once))
        assertIs<HoldOutcome.OverDailyBudget>(queue.submit(author.mobilization(mobilizationTags(d = "deux")), 10, 100, once))
        // Refused before the budget is asked: no token spent, the next version of "un" still fits.
        val another = budget(1)
        assertEquals(HoldOutcome.TooManyPendingForKey, queue.submit(author.mobilization(mobilizationTags(d = "trois")), 1, 100, another))
        assertIs<HoldOutcome.Queued>(queue.submit(author.mobilization(mobilizationTags(d = "un"), createdAt = nowSeconds()), 1, 100, another))
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
            setOf("id", "pubkey", "kind", "identifier", "address", "created_at", "event", "tags", "status", "received_at", "decided_at", "decision_reason"),
            columns,
        )
        assertTrue(HeldQueue.USEFUL_TAGS.none { it.contains("ip") })
    }

    @Test
    fun usefulTagsAreAShortCopyOfTheEvent() = runBlocking {
        val event = author.mobilization(mobilizationTags(organizers = listOf("a".repeat(64))), content = "[EXEMPLE] texte long")
        val tags = HeldQueue.usefulTags(event)
        assertEquals(setOf("d", "title", "start", "ppf-event-type", "ppf-territory"), tags.map { it[0] }.toSet())
        assertTrue(tags.all { it.size == 2 })
        assertEquals(event.firstTagValue("start"), tags.first { it[0] == "start" }[1])
    }
}
