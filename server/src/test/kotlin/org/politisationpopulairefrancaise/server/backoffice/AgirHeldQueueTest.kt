package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.coroutines.runBlocking
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirSetup
import org.politisationpopulairefrancaise.server.agir.AgirSpace
import org.politisationpopulairefrancaise.server.agir.HeldQueue
import org.politisationpopulairefrancaise.server.agir.HoldOutcome
import org.politisationpopulairefrancaise.server.events.EventRepository
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** The queue agir.held over the real HeldQueue: what the team sees and what each action does. */
class AgirHeldQueueTest {
    private val clock = MutableClock(java.time.Instant.now())
    private val events = EventRepository(TestDatabase.dataSource)
    private val space = AgirSpace.create(TestDatabase.dataSource, events, NO_EDITORS, AgirSetup(clock = clock))
    private val queue = AgirHeldQueue(space)
    private val author = LocalEventSigner(IdentityKeys.generate())
    private val actor = TeamActor.of(UUID.randomUUID(), "Modo", setOf(TeamRole.MODERATOR), 2)

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun html(itemId: String): String = createHTML().div { queue.render(itemId, this) }

    @Test
    fun aPendingMobilizationIsListedWithItsStartAsDeadline() = runBlocking {
        val start = clock.instant().epochSecond + 2 * AgirFixtures.DAY
        val event = author.mobilization(AgirFixtures.mobilizationTags(start = start))
        assertIs<HoldOutcome.Queued>(space.queue.submit(event))
        assertEquals(1, queue.pendingCount())
        val item = queue.page(null, 10).items.single()
        assertEquals(event.id, item.id)
        assertEquals(start, item.deadline)
        assertTrue(item.summary.orEmpty().startsWith(BackOfficeTexts.AGIR_MOBILIZATION))
        assertEquals(listOf("approve", "reject"), queue.actions(event.id).map { it.id })
        assertTrue(queue.actions(event.id).first().sensitive)
        assertTrue(queue.actions(event.id).last().reasonRequired)
        assertEquals("queue.agir.held.decide", queue.viewPermission)
        val page = html(event.id)
        assertTrue(page.contains(author.publicKey.npub))
        assertFalse(page.contains("<a "), "links of a content are shown as text, never followed")
    }

    @Test
    fun approvingPublishesAndRejectingNeedsAReason() = runBlocking {
        val first = author.mobilization(AgirFixtures.mobilizationTags(d = "une"))
        val second = author.mobilization(AgirFixtures.mobilizationTags(d = "deux"))
        space.queue.submit(first)
        space.queue.submit(second)
        assertEquals(ActionOutcome.Done, queue.perform(first.id, "approve", null, actor))
        assertNotNull(events.find(first.id))
        assertEquals(ActionOutcome.AlreadyDecided, queue.perform(first.id, "approve", null, actor))
        assertTrue(queue.actions(first.id).isEmpty())
        assertIs<ActionOutcome.Refused>(queue.perform(second.id, "reject", "  ", actor))
        assertEquals(ActionOutcome.Done, queue.perform(second.id, "reject", "Hors charte", actor))
        assertEquals("Hors charte", space.queue.find(second.id)!!.reason)
        assertNull(events.find(second.id))
        assertEquals(ActionOutcome.NotFound, queue.perform("0".repeat(64), "approve", null, actor))
    }

    @Test
    fun aMobilizationWhoseDatePassedCannotBeApproved() = runBlocking {
        val event = author.mobilization(AgirFixtures.mobilizationTags(start = clock.instant().epochSecond + AgirFixtures.DAY))
        space.queue.submit(event)
        clock.advance(2.days)
        assertEquals(ActionOutcome.Refused(HeldQueue.EXPIRED_REASON), queue.perform(event.id, "approve", null, actor))
        assertEquals(HeldStatus.REJECTED, space.queue.find(event.id)!!.status)
        assertNull(events.find(event.id))
    }

    @Test
    fun aCallForWitnessesSaysItIsAlwaysReviewed() = runBlocking {
        val event = author.call(AgirFormats.CALL_TYPE_WITNESSES)
        space.queue.submit(event)
        assertTrue(html(event.id).contains(BackOfficeTexts.AGIR_WITNESSES_ALWAYS_REVIEWED))
        assertTrue(queue.page(null, 10).items.single().summary.orEmpty().contains("appel à témoins"))
        assertTrue(html("0".repeat(64)).contains(BackOfficeTexts.AGIR_HELD_GONE))
    }
}
