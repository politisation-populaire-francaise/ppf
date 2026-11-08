package org.politisationpopulairefrancaise.server.decider

import kotlinx.coroutines.runBlocking
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.decider.DeliberationFormats
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import org.politisationpopulairefrancaise.server.urne.UrneFixtures
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The contributions of citizens to a deliberation (DE-25): limits, phases, review, retention. */
class DeliberationSubmissionsTest {
    private val start = Instant.parse("2026-12-07T00:00:00Z")
    private val week = 7 * 86_400L
    private val clock = MutableClock(start.plusSeconds(60))
    private val team = LocalEventSigner(IdentityKeys.generate())
    private val events = EventRepository(TestDatabase.dataSource)
    private val submissions = DeliberationSubmissions(TestDatabase.dataSource, events, { it == team.publicKey.hex }, clock)
    private val queue = DeliberationQueue(submissions)
    private val moderator = TeamActor.of(UUID.randomUUID(), "Modo", setOf(TeamRole.MODERATOR), 2)

    @BeforeTest
    fun emptyTables() {
        TestDatabase.reset()
        UrneFixtures.reset()
    }

    private fun sign(signer: LocalEventSigner, d: String, type: String, link: String?, content: String = "[EXEMPLE] Texte de $d", tags: List<List<String>> = emptyList()): SignedEvent = runBlocking {
        signer.sign(
            kind = EventKinds.LONG_FORM_ARTICLE,
            tags = listOfNotNull(listOf("d", d), listOf(DeliberationFormats.TAG_TYPE, type), listOf("title", "[EXEMPLE] $d"), link?.let { listOf("a", it) }) + tags,
            content = content,
            createdAt = clock.instant.epochSecond,
        )
    }

    private fun deliberation(signer: LocalEventSigner = team): String {
        val s = start.epochSecond
        val event = sign(
            signer, "pacte-2027", DeliberationFormats.TYPE_DELIBERATION, null,
            tags = listOf(
                listOf("ppf-measures", "5", "10"),
                listOf("ppf-phase", "propositions", "$s", "${s + 3 * week}"),
                listOf("ppf-phase", "amendements", "${s + 3 * week}", "${s + 5 * week}"),
                listOf("ppf-phase", "vote", "${s + 5 * week}", "${s + 7 * week}"),
            ),
        )
        events.store(event)
        return ContentAddress.of(event)!!.toString()
    }

    private fun refused(outcome: DeliberationSubmissions.Outcome): String = (outcome as DeliberationSubmissions.Outcome.Refused).code

    @Test
    fun aPropositionWaitsForReviewWithinItsPhaseAndItsLimits() {
        val deliberation = deliberation()
        val alice = LocalEventSigner(IdentityKeys.generate())
        val proposition = sign(alice, "medecins", DeliberationFormats.TYPE_PROPOSITION, deliberation)
        assertTrue(DeliberationSubmissions.isCitizenContribution(proposition) { it == team.publicKey.hex })
        assertFalse(DeliberationSubmissions.isCitizenContribution(sign(team, "x", DeliberationFormats.TYPE_PROPOSITION, deliberation)) { it == team.publicKey.hex })
        assertEquals(DeliberationSubmissions.Outcome.Queued, submissions.submit(proposition))
        assertEquals(DeliberationSubmissions.Outcome.AlreadyPending, submissions.submit(proposition))
        // One proposition per account; a newer version of the same one replaces it.
        assertEquals("one_proposition_per_account", refused(submissions.submit(sign(alice, "autre", DeliberationFormats.TYPE_PROPOSITION, deliberation))))
        clock.instant = clock.instant.plusSeconds(10)
        val corrected = sign(alice, "medecins", DeliberationFormats.TYPE_PROPOSITION, deliberation, content = "[EXEMPLE] Texte corrigé")
        assertEquals(DeliberationSubmissions.Outcome.Queued, submissions.submit(corrected))
        assertEquals(1, submissions.pendingCount())
        // Refused at once: the team's starting proposal, personal data, another key's deliberation, a malformed one.
        val bob = LocalEventSigner(IdentityKeys.generate())
        assertEquals("starting_proposal_reserved", refused(submissions.submit(sign(bob, "faux", DeliberationFormats.TYPE_PROPOSITION, deliberation, tags = listOf(listOf("ppf-origin", "depart"))))))
        assertEquals("personal_data", refused(submissions.submit(sign(bob, "contact", DeliberationFormats.TYPE_PROPOSITION, deliberation, content = "[EXEMPLE] Écrivez-moi à jean.exemple@example.org"))))
        val foreign = deliberation(LocalEventSigner(IdentityKeys.generate()))
        assertEquals("unknown_deliberation", refused(submissions.submit(sign(bob, "ailleurs", DeliberationFormats.TYPE_PROPOSITION, foreign))))
        assertEquals("malformed_contribution", refused(submissions.submit(sign(bob, "vide", DeliberationFormats.TYPE_PROPOSITION, deliberation, content = " "))))
        // Reviewed: approved, it is published exactly as signed.
        val item = queue.page(null, 10).items.single()
        assertEquals(corrected.id, item.id)
        assertTrue(createHTML().div { queue.render(corrected.id, this) }.contains("Texte corrigé"))
        assertEquals(ActionOutcome.Done, queue.perform(corrected.id, DeliberationQueue.APPROVE, null, moderator))
        assertNotNull(events.find(corrected.id))
        assertTrue(queue.actions(corrected.id).isEmpty())
        // After the phase of propositions: refused at once.
        clock.instant = start.plusSeconds(3 * week + 60)
        assertEquals("phase_closed", refused(submissions.submit(sign(bob, "tard", DeliberationFormats.TYPE_PROPOSITION, deliberation))))
    }

    @Test
    fun threeAmendmentsPerAccountAndNoneAfterItsPhase() {
        val deliberation = deliberation()
        val alice = LocalEventSigner(IdentityKeys.generate())
        val proposition = sign(alice, "medecins", DeliberationFormats.TYPE_PROPOSITION, deliberation)
        events.store(proposition)
        val target = ContentAddress.of(proposition)!!.toString()
        val bob = LocalEventSigner(IdentityKeys.generate())
        // Not during the phase of propositions.
        assertEquals("phase_closed", refused(submissions.submit(sign(bob, "a0", DeliberationFormats.TYPE_AMENDMENT, target))))
        clock.instant = start.plusSeconds(3 * week + 60)
        val amendments = (1..3).map { sign(bob, "a$it", DeliberationFormats.TYPE_AMENDMENT, target) }
        amendments.forEach { assertEquals(DeliberationSubmissions.Outcome.Queued, submissions.submit(it)) }
        assertEquals("three_amendments_per_account", refused(submissions.submit(sign(bob, "a4", DeliberationFormats.TYPE_AMENDMENT, target))))
        assertEquals("unknown_deliberation", refused(submissions.submit(sign(bob, "a5", DeliberationFormats.TYPE_AMENDMENT, "30023:${"c".repeat(64)}:inconnue"))))
        // Refused with a reason shown to the author.
        assertIs<ActionOutcome.Refused>(queue.perform(amendments[0].id, DeliberationQueue.REJECT, " ", moderator))
        assertEquals(ActionOutcome.Done, queue.perform(amendments[0].id, DeliberationQueue.REJECT, "Hors charte", moderator))
        assertEquals(SubmissionStatus(amendments[0].id, "rejected", "Hors charte"), submissions.status(amendments[0].id))
        // Reviewed after its phase: it would get no ballot box, approval is refused.
        clock.instant = start.plusSeconds(5 * week + 60)
        assertIs<ActionOutcome.Refused>(queue.perform(amendments[1].id, DeliberationQueue.APPROVE, null, moderator))
        assertNull(events.find(amendments[1].id))
        // Decided ones are deleted after 30 days; pending ones stay.
        clock.instant = clock.instant.plusSeconds(31 * 86_400L)
        assertEquals(1, submissions.purgeDecided())
        assertNull(submissions.status(amendments[0].id))
        assertEquals("pending", submissions.status(amendments[1].id)!!.status)
    }
}
