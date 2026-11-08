package org.politisationpopulairefrancaise.server.decider

import kotlinx.coroutines.runBlocking
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.decider.Petition
import org.politisationpopulairefrancaise.core.decider.PetitionPlatform
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.demand
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.draft
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import org.politisationpopulairefrancaise.server.signing.FakePlatformSigner
import org.politisationpopulairefrancaise.server.urne.RevendicationUrnes
import org.politisationpopulairefrancaise.server.urne.UrneFixtures
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The official petition attached by the team to a demand (D-115), signed for decider.pacte after an approval. */
class PetitionLinksTest {
    private val clock = MutableClock(Instant.parse("2026-11-08T09:00:00Z"))
    private val events = EventRepository(TestDatabase.dataSource)
    private val signer = FakePlatformSigner { clock.instant.epochSecond }
    private val links = PetitionLinks(TestDatabase.dataSource, events, signer, clock)
    private val urnes = UrneFixtures.service(clock)
    private val queue = PetitionLinksQueue(links, urnes)
    private val admin = TeamActor.of(UUID.randomUUID(), "Admin", setOf(TeamRole.ADMIN), 2)
    private val citizen = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyTables() {
        TestDatabase.reset()
        UrneFixtures.reset()
    }

    private fun publishedDemand(slug: String): ContentAddress {
        val event = runBlocking { citizen.demand(draft(), slug, clock.instant.epochSecond - 60) }
        events.store(event)
        return ContentAddress.of(event)!!
    }

    private fun petitionsOf(demand: ContentAddress) =
        events.query(EventFilter(kinds = listOf(EventKinds.LONG_FORM_ARTICLE), tags = mapOf(Article.TAG_TYPE to listOf("petition"), "a" to listOf(demand.toString())), limit = 10))

    @Test
    fun theLinkIsSignedOnlyAfterTheApprovalThenPublishedAsAPetitionOfTheDemand() {
        val demand = publishedDemand("medecins-de-garde-1a2b")
        val url = "https://petitions.assemblee-nationale.fr/initiatives/i-1234"
        assertIs<PetitionLinks.Submit.Done>(links.submit(demand, url, "n° 1234"))
        assertIs<PetitionLinks.Submit.Refused>(links.submit(demand, url, null), "one waiting at a time")
        val draft = assertNotNull(links.pending(demand.toString()))
        // Not approved: nothing is asked, nothing published.
        assertEquals(0, links.runDue())
        assertTrue(signer.requests.isEmpty())
        assertNull(signer.approve(SigningUse.DECIDER_PACTE, draft.eventId, "membre-1"))
        assertEquals(0, links.runDue(), "approved: the signature is asked")
        assertEquals(1, links.runDue(), "signed: published")
        assertNull(links.pending(demand.toString()))
        val event = petitionsOf(demand).single()
        assertTrue(signer.trust.isPlatformContent(event, SigningUse.DECIDER_PACTE))
        val petition = assertNotNull(Petition.from(Article.from(event)!!))
        assertEquals(PetitionPlatform.ASSEMBLEE, petition.platform)
        assertEquals(url, petition.officialUrl)
        assertEquals(listOf(demand), petition.demands)
        assertEquals("n° 1234", petition.reference)
    }

    @Test
    fun onlyAnOfficialAddressOfAPublishedDemandIsAccepted() {
        val demand = publishedDemand("cantine-bio-3c4d")
        assertIs<PetitionLinks.Submit.Refused>(links.submit(demand, "https://exemple.org/petition", null))
        assertIs<PetitionLinks.Submit.Refused>(links.submit(demand, "http://petitions.senat.fr/x", null), "https only")
        assertIs<PetitionLinks.Submit.Refused>(links.submit(ContentAddress.parse("30023:${"c".repeat(64)}:inconnue"), "https://petitions.senat.fr/x", null))
        assertNull(links.pending(demand.toString()))
    }

    @Test
    fun theQueueListsTheDemandsInDebateAndAttachesTheAddressPasted() {
        val demand = publishedDemand("transports-5e6f")
        urnes.open(RevendicationUrnes.PREFIX + demand, demand.toString(), RevendicationUrnes.CHOICES, clock.instant, null, liveResults = true)
        assertEquals(listOf(demand.toString()), queue.page(null, 10).items.map { it.id })
        assertEquals(listOf(PetitionLinksQueue.ATTACH), queue.actions(demand.toString()).map { it.id })
        assertIs<ActionOutcome.Refused>(queue.perform(demand.toString(), PetitionLinksQueue.ATTACH, " ", admin))
        assertEquals(ActionOutcome.Done, queue.perform(demand.toString(), PetitionLinksQueue.ATTACH, "https://petitions.senat.fr/initiatives/i-99 n° 99", admin))
        assertTrue(queue.actions(demand.toString()).isEmpty())
        assertEquals(1, queue.pendingCount())
        assertTrue(createHTML().div { queue.render(demand.toString(), this) }.contains("Lire et approuver sur la page du service de signature"))
    }
}
