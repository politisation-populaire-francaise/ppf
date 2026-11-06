package org.politisationpopulairefrancaise.server.revendications

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.revendications.checks.CheckKind
import org.politisationpopulairefrancaise.core.revendications.checks.CheckState
import org.politisationpopulairefrancaise.core.revendications.checks.IssueKind
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSeverity
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSource
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalChecker
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalIssue
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.revendications.checks.RelatedDemand
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.demand
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.draft
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ProposalQueueTest {
    private val start = Instant.parse("2026-11-06T12:00:00Z")
    private val clock = MutableClock(start)
    private val events = EventRepository(TestDatabase.dataSource)
    private val queue = ProposalQueue(TestDatabase.dataSource, events, clock)
    private val citizen = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun submit(event: SignedEvent, budget: PeriodBudget = PeriodBudget(100), maxPerKey: Int = 10, max: Int = 100): SubmitOutcome =
        queue.submit(event, event.firstTagValue("d")!!, maxPerKey, max, budget)

    private fun demand(slug: String, createdAt: Long = start.epochSecond - 60, signer: LocalEventSigner = citizen) =
        runBlocking { signer.demand(draft(), slug, createdAt) }

    @Test
    fun aNewVersionReplacesThePendingOneAndAnOlderOneIsDropped() {
        val first = demand("a", createdAt = 1_000)
        val second = demand("a", createdAt = 2_000)
        assertEquals(SubmitOutcome.Queued(null), submit(first))
        assertEquals(SubmitOutcome.AlreadyPending, submit(first))
        assertEquals(SubmitOutcome.Queued(first.id), submit(second))
        assertNull(queue.find(first.id), "the replaced version is gone")
        assertEquals(ProposalStatus.PENDING, queue.find(second.id)?.status)
        assertEquals(SubmitOutcome.Superseded, submit(first), "an older version does not come back")
        assertEquals(listOf(second.id), queue.pending().map { it.id })
    }

    @Test
    fun limitsCountPendingProposalsPerKeyAndOnTheWholeServer() {
        assertIs<SubmitOutcome.Queued>(submit(demand("a"), maxPerKey = 2))
        assertIs<SubmitOutcome.Queued>(submit(demand("b"), maxPerKey = 2))
        assertEquals(SubmitOutcome.TooManyPendingForKey, submit(demand("c"), maxPerKey = 2))
        assertIs<SubmitOutcome.Queued>(submit(demand("a", createdAt = start.epochSecond - 10), maxPerKey = 2), "a new version does not count twice")

        val other = LocalEventSigner(IdentityKeys.generate())
        assertEquals(SubmitOutcome.QueueFull, submit(demand("x", signer = other), max = 2))

        val daily = PeriodBudget(1)
        assertIs<SubmitOutcome.Queued>(submit(demand("y", signer = other), budget = daily))
        assertIs<SubmitOutcome.OverDailyBudget>(submit(demand("z", signer = other), budget = daily))
        assertNull(queue.find(demand("z", signer = other).id), "nothing stored when the budget is spent")
    }

    @Test
    fun approvingPublishesTheEventAndRejectingKeepsItOut() {
        val approved = demand("publiee")
        val rejected = demand("refusee")
        submit(approved)
        submit(rejected)
        clock.advanceSeconds(3_600)

        assertEquals(DecisionOutcome.Approved(StoreOutcome.STORED), queue.approve(approved.id))
        assertEquals(approved, events.find(approved.id), "published as signed by its author")
        assertEquals(DecisionOutcome.AlreadyDecided(ProposalStatus.APPROVED), queue.approve(approved.id))
        assertEquals(SubmitOutcome.AlreadyApproved, submit(approved))

        assertFailsWith<IllegalArgumentException> { queue.reject(rejected.id, "   ") }
        assertFailsWith<IllegalArgumentException> { queue.reject(rejected.id, "x".repeat(1_001)) }
        assertEquals(DecisionOutcome.Rejected, queue.reject(rejected.id, "[EXEMPLE] Une revendication identique existe déjà."))
        assertNull(events.find(rejected.id))
        assertEquals(DecisionOutcome.AlreadyDecided(ProposalStatus.REJECTED), queue.approve(rejected.id))
        assertEquals(SubmitOutcome.AlreadyRejected, submit(rejected))
        val decided = assertNotNull(queue.find(rejected.id))
        assertEquals("[EXEMPLE] Une revendication identique existe déjà.", decided.reason)
        assertEquals(start.epochSecond, decided.receivedAt)
        assertEquals(start.epochSecond + 3_600, decided.decidedAt)

        assertEquals(DecisionOutcome.NotFound, queue.approve("0".repeat(64)))
        assertEquals(DecisionOutcome.NotFound, queue.reject("0".repeat(64), "motif"))
        assertTrue(queue.pending().isEmpty())

        // A corrected version of the refused demand is a new proposal.
        assertIs<SubmitOutcome.Queued>(submit(demand("refusee", createdAt = start.epochSecond)))
        // A version older than the published one would be dropped at approval: refused at once.
        assertEquals(SubmitOutcome.Superseded, submit(demand("publiee", createdAt = 1)))
    }

    @Test
    fun decidedProposalsAreDeletedThirtyDaysAfterTheDecision() {
        val approved = demand("publiee")
        val rejected = demand("refusee")
        val pending = demand("en-attente")
        listOf(approved, rejected, pending).forEach { submit(it) }
        queue.approve(approved.id)
        queue.reject(rejected.id, "[EXEMPLE] motif")
        val maintenance = ProposalMaintenance(queue, reviewer = null, retention = 30.days, clock = clock)

        clock.advanceSeconds(30 * 86_400)
        assertEquals(0, maintenance.purgeOnce(), "decided exactly 30 days ago: kept")
        clock.advanceSeconds(1)
        assertEquals(2, maintenance.purgeOnce())
        assertNull(queue.find(approved.id))
        assertNull(queue.find(rejected.id))
        assertNotNull(queue.find(pending.id), "a pending proposal waits for the team, however long")
        assertEquals(approved, events.find(approved.id), "the approved demand lives on in the events table")
        assertEquals(0, maintenance.purgeOnce())
    }

    @Test
    fun theReviewerComparesWithPublishedAndPendingDemandsAndStoresTheReport() = runBlocking {
        val editor = LocalEventSigner(IdentityKeys.generate())
        val published = editor.demand(draft(title = "Un médecin de garde joignable le soir dans chaque bassin de vie"), "medecin-garde", 1_000)
        events.store(published)
        val otherCitizen = LocalEventSigner(IdentityKeys.generate())
        val waiting = otherCitizen.demand(draft(title = "Des crèches ouvertes le soir pour les parents en horaires décalés", themes = listOf("societe")), "creches-soir", 1_000)
        submit(waiting)
        val proposal = demand("medecin-canton")
        submit(proposal)

        val reviewer = ProposalReviewer(queue, ProposalChecker(clock = { clock.instant().epochSecond }), LoggerFactory.getLogger("test"))
        val report = assertNotNull(reviewer.check(proposal.id))
        assertEquals(report, queue.find(proposal.id)?.report)
        assertEquals(2, report.checks.first { it.kind == CheckKind.SIMILARITY }.compared, "one published, one pending, not itself")
        assertEquals(CheckState.UNAVAILABLE, report.state(CheckKind.AI))
        assertEquals(CheckState.UNAVAILABLE, report.state(CheckKind.LEGAL))
        val related = report.issues.filter { it.kind == IssueKind.DUPLICATE || it.kind == IssueKind.SIMILAR }.flatMap { it.related }.map { it.address }
        assertTrue("30023:${editor.publicKey.hex}:medecin-garde" in related, related.toString())
        assertEquals(start.epochSecond, report.checkedAt)

        assertEquals(listOf(waiting.id), queue.uncheckedPending(10))
        queue.approve(proposal.id)
        assertNull(reviewer.check(proposal.id), "a decided proposal is not checked again")
    }

    @Test
    fun theAuthorNeverSeesAnUnpublishedProposalInTheReport() {
        val publishedAddress = "30023:${"a".repeat(64)}:publiee"
        val pendingAddress = "30023:${"b".repeat(64)}:en-attente"
        fun related(address: String) = RelatedDemand(address, "[EXEMPLE] titre", 0.8)
        val report = ProposalReport(
            issues = listOf(
                ProposalIssue(IssueKind.DUPLICATE, IssueSeverity.WARNING, IssueSource.SIMILARITY, related = listOf(related(pendingAddress))),
                ProposalIssue(IssueKind.SIMILAR, IssueSeverity.INFO, IssueSource.AI, related = listOf(related(publishedAddress), related(pendingAddress)), detail = "[EXEMPLE] cite les deux"),
                ProposalIssue(IssueKind.UNCLEAR, IssueSeverity.INFO, IssueSource.AI, detail = "[EXEMPLE] peu clair"),
            ),
            checks = emptyList(),
            checkedAt = 1,
        )
        val served = RevendicationProposals.forAuthor(report, setOf(publishedAddress))
        assertEquals(listOf(IssueKind.SIMILAR, IssueKind.UNCLEAR), served.issues.map { it.kind })
        assertEquals(listOf(publishedAddress), served.issues[0].related.map { it.address })
        assertNull(served.issues[0].detail, "the explanation may have quoted the unpublished one")
        assertEquals("[EXEMPLE] peu clair", served.issues[1].detail)
    }
}
