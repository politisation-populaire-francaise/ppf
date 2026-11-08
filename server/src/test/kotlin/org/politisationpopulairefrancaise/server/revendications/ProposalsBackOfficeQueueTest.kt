package org.politisationpopulairefrancaise.server.revendications

import kotlinx.coroutines.runBlocking
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.decider.CompromiseFixtures
import org.politisationpopulairefrancaise.server.decider.CompromisedAuthors
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.demand
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.draft
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The queue revendications.proposals over the real ProposalQueue: what the team sees and what each action does. */
class ProposalsBackOfficeQueueTest {
    private val start = Instant.parse("2026-11-06T12:00:00Z")
    private val clock = MutableClock(start)
    private val events = EventRepository(TestDatabase.dataSource)
    private val proposals = ProposalQueue(TestDatabase.dataSource, events, clock)
    private val queue = ProposalsBackOfficeQueue(proposals)
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val actor = TeamActor.of(UUID.randomUUID(), "Modo", setOf(TeamRole.MODERATOR), 2)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun demand(slug: String): SignedEvent = runBlocking { citizen.demand(draft(), slug, start.epochSecond - 60) }

    private fun submit(event: SignedEvent) = proposals.submit(event, event.firstTagValue("d")!!, 10, 100, PeriodBudget(100))

    @Test
    fun aProposalIsListedAndShownWithItsAuthorsPseudonym() {
        val event = demand("piscine-ouverte-1a2b")
        assertIs<SubmitOutcome.Queued>(submit(event))
        assertEquals(1, queue.pendingCount())
        val item = queue.page(null, 10).items.single()
        assertEquals(event.id, item.id)
        assertEquals("queue.revendications.proposals.decide", queue.viewPermission)
        assertEquals(listOf("approve", "reject"), queue.actions(event.id).map { it.id })
        assertTrue(queue.actions(event.id).all { it.sensitive })
        assertTrue(queue.actions(event.id).last().reasonRequired)
        val page = createHTML().div { queue.render(event.id, this) }
        assertTrue(page.contains(citizen.publicKey.npub))
        assertTrue(page.contains("Vérifications automatiques"))
    }

    @Test
    fun approvingPublishesAsSignedAndRejectingNeedsAReason() {
        val first = demand("premiere-1111")
        val second = demand("seconde-2222")
        submit(first)
        submit(second)
        assertEquals(ActionOutcome.Done, queue.perform(first.id, "approve", null, actor))
        assertNotNull(events.find(first.id), "published exactly as its author signed it")
        assertEquals(ActionOutcome.AlreadyDecided, queue.perform(first.id, "approve", null, actor))
        assertTrue(queue.actions(first.id).isEmpty())
        assertIs<ActionOutcome.Refused>(queue.perform(second.id, "reject", "  ", actor))
        assertEquals(ActionOutcome.Done, queue.perform(second.id, "reject", "Hors charte", actor))
        assertEquals("Hors charte", proposals.find(second.id)!!.reason)
        assertNull(events.find(second.id))
        assertEquals(ActionOutcome.NotFound, queue.perform("0".repeat(64), "approve", null, actor))
    }

    @Test
    fun aDemandSignedAfterItsAuthorWasDeclaredCompromisedCanOnlyBeRefused() {
        val compromised = CompromisedAccounts(events, clock = { clock.instant })
        val guarded = ProposalsBackOfficeQueue(proposals, CompromisedAuthors(compromised))
        val other = LocalEventSigner(IdentityKeys.generate())
        val before = demand("avant-1111")
        val after = runBlocking { other.demand(draft(), "apres-2222", start.epochSecond - 60) }
        submit(before)
        submit(after)
        // Two days later: the citizen's key is compromised since after their demand, the other's since before.
        clock.instant = start.plusSeconds(2 * 86_400)
        CompromiseFixtures.declare(compromised, citizen, since = start.epochSecond, at = clock.instant.epochSecond)
        CompromiseFixtures.declare(compromised, other, since = start.epochSecond - 3_600, at = clock.instant.epochSecond)

        assertTrue(guarded.page(null, 10).items.all { CompromisedAuthors.SHORT in it.summary.orEmpty() }, "both carry the mention")
        assertTrue(createHTML().div { guarded.render(before.id, this) }.contains("signée avant"))
        assertTrue(createHTML().div { guarded.render(after.id, this) }.contains("refusée. Compromis depuis le"))
        assertEquals(listOf("reject"), guarded.actions(after.id).map { it.id })
        assertEquals(ActionOutcome.Refused(CompromisedAuthors.REFUSED), guarded.perform(after.id, "approve", null, actor))
        assertNull(events.find(after.id))
        assertEquals(listOf("approve", "reject"), guarded.actions(before.id).map { it.id })
        assertEquals(ActionOutcome.Done, guarded.perform(before.id, "approve", null, actor))
        assertNotNull(events.find(before.id))
    }
}
