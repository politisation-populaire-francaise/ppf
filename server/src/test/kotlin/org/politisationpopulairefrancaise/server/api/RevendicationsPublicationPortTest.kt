package org.politisationpopulairefrancaise.server.api

import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.revendications.ProposalState
import org.politisationpopulairefrancaise.core.revendications.ProposalStatus
import org.politisationpopulairefrancaise.core.revendications.ProposalStatuses
import org.politisationpopulairefrancaise.core.revendications.checks.CheckKind
import org.politisationpopulairefrancaise.core.revendications.checks.CheckState
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.demand
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.draft
import org.politisationpopulairefrancaise.server.revendications.ProposalQueue
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** The device's adapter (shared/core) talking to the real server code about citizen demands, end to end. */
class RevendicationsPublicationPortTest {
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val editor = LocalEventSigner(IdentityKeys.generate())

    /** The team's side of the queue (no HTTP route yet), on the database of the application. */
    private val queue = ProposalQueue(TestDatabase.dataSource, EventRepository(TestDatabase.dataSource))

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun ServerPublicationPort.states(vararg ids: String): List<ProposalState> =
        assertIs<ProposalStatuses.Known>(proposalStatuses(ids.toList())).states

    /** Waits for the background checks of [id]. */
    private suspend fun ServerPublicationPort.checked(id: String): ProposalState {
        repeat(200) {
            states(id).singleOrNull()?.takeIf { it.report != null }?.let { return it }
            delay(50)
        }
        fail("No report for the proposal after 10 seconds")
    }

    @Test
    fun aCitizenDemandIsHeldForReviewAndItsAuthorFollowsIt() = testApplication {
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY, testRevendications()) }
        val port = ServerPublicationPort(client, baseUrl = "")

        val pending = citizen.demand(slug = "en-attente-0001")
        assertEquals(PublicationResult.Accepted(pending.id, heldForReview = true), port.publish(pending))
        val fromTeam = editor.demand(draft(title = "Des places en crèche pour les parents en horaires décalés", themes = listOf("societe")), slug = "equipe-0001")
        assertEquals(PublicationResult.Accepted(fromTeam.id, heldForReview = false), port.publish(fromTeam))

        val approved = citizen.demand(draft(title = "Des transports en commun toute la nuit le week-end", themes = listOf("transports")), slug = "approuvee-0001")
        val rejected = citizen.demand(draft(title = "Des ateliers gratuits pour apprendre le numérique en mairie", themes = listOf("numerique")), slug = "refusee-0001")
        assertEquals(PublicationResult.Accepted(approved.id, heldForReview = true), port.publish(approved))
        assertEquals(PublicationResult.Accepted(rejected.id, heldForReview = true), port.publish(rejected))
        port.checked(rejected.id)
        queue.approve(approved.id)
        queue.reject(rejected.id, "[EXEMPLE] Formulation à revoir avant publication.")

        val waiting = port.checked(pending.id)
        assertEquals(ProposalStatus.PENDING, waiting.status)
        assertNull(waiting.decidedAt)
        assertEquals(CheckState.UNAVAILABLE, waiting.report!!.state(CheckKind.AI), "no model configured")

        val unknown = "0".repeat(64)
        val states = port.states(pending.id, approved.id, rejected.id, fromTeam.id, unknown).associateBy { it.id }
        assertEquals(setOf(pending.id, approved.id, rejected.id, fromTeam.id), states.keys, "an unknown id is simply absent")
        val published = states.getValue(approved.id)
        assertEquals(ProposalStatus.APPROVED, published.status)
        assertNotNull(published.decidedAt)
        val refused = states.getValue(rejected.id)
        assertEquals(ProposalStatus.REJECTED, refused.status)
        assertEquals("[EXEMPLE] Formulation à revoir avant publication.", refused.reason)
        assertNotNull(refused.decidedAt)
        assertEquals(CheckState.DONE, assertNotNull(refused.report).state(CheckKind.SIMILARITY), "the author reads why, with the checks")
        assertEquals(ProposalStatus.APPROVED, states.getValue(fromTeam.id).status, "published directly by the team")

        // 30 days after the decisions, the proposals are deleted: the published demand stays approved.
        assertEquals(2, queue.deleteDecidedBefore(Instant.now().plusSeconds(60)))
        val afterPurge = port.states(approved.id, rejected.id)
        val stillPublished = afterPurge.single()
        assertEquals(approved.id, stillPublished.id)
        assertEquals(ProposalStatus.APPROVED, stillPublished.status)
        assertNull(stillPublished.decidedAt)
        assertNull(stillPublished.report)
        assertEquals(PublicationResult.Accepted(approved.id), port.publish(approved), "already published")
        assertTrue(abs(DemandFixtures.nowSeconds() - stillPublished.receivedAt) < 600, "received when it reached the events table")
    }
}
