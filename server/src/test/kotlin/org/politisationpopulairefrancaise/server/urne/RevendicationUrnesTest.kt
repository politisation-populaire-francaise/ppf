package org.politisationpopulairefrancaise.server.urne

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.revendications.StatusLabels
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The boxes of the demands wall follow the debate (DE-01, D-110): opened in debate, closed after. */
class RevendicationUrnesTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val events = EventRepository(TestDatabase.dataSource)
    private val clock = MutableClock(Instant.parse("2026-11-04T12:00:00Z"))
    private val service = UrneFixtures.service(clock)
    private val demands = RevendicationUrnes(TestDatabase.dataSource, service, { it == editor.publicKey.hex }, clock)

    @BeforeTest
    fun emptyTables() {
        TestDatabase.reset()
        UrneFixtures.reset()
    }

    private fun LocalEventSigner.demand(d: String, status: String, createdAt: Long = 1_794_000_000): SignedEvent = runBlocking {
        sign(
            kind = Article.KIND,
            content = "## Pourquoi\n\n[EXEMPLE] Texte.",
            tags = listOf(
                listOf("d", d),
                listOf("title", "[EXEMPLE] Une revendication"),
                listOf(Article.TAG_TYPE, "revendication"),
                listOf(Article.TAG_STATUS, status),
            ),
            createdAt = createdAt,
        )
    }

    private fun address(signer: LocalEventSigner, d: String) = "30023:${signer.publicKey.hex}:$d"

    @Test
    fun aDemandInDebateGetsItsBoxAndLosesItWhenItLeavesTheDebate() {
        events.store(editor.demand("hopital", "en-debat"))
        events.store(editor.demand("ecole", "nouvelle"))
        assertEquals(1 to 0, demands.reconcile())
        val box = service.list(0, 10).single()
        assertEquals(RevendicationUrnes.PREFIX + address(editor, "hopital"), box.id)
        assertEquals(address(editor, "hopital"), box.subject)
        assertEquals(listOf("pour", "contre", "blanc"), box.choices)
        assertTrue(box.liveResults)
        assertEquals(null, box.closesAt)
        // Nothing to do at the next pass.
        assertEquals(0 to 0, demands.reconcile())
        // Retained in the programme: the box closes, and stays closed if the demand comes back.
        events.store(editor.demand("hopital", "retenue", createdAt = 1_794_000_100))
        assertEquals(0 to 1, demands.reconcile())
        assertTrue(service.list(0, 10).single().closed)
        events.store(editor.demand("hopital", "en-debat", createdAt = 1_794_000_200))
        assertEquals(0 to 0, demands.reconcile())
        assertEquals(1, service.list(0, 10).size)
    }

    @Test
    fun onlyTheTeamPutsADemandInDebate() {
        // Stored directly (no API in between): a key outside the team cannot open a box.
        events.store(citizen.demand("vote", "en-debat"))
        assertEquals(0 to 0, demands.reconcile())
        assertTrue(service.list(0, 10).isEmpty())
    }

    private fun LocalEventSigner.label(signer: LocalEventSigner, d: String, status: String, createdAt: Long) = runBlocking {
        sign(
            kind = StatusLabels.KIND,
            tags = StatusLabels.tags(ContentAddress.parse(address(signer, d)), RevendicationStatus.fromTag(status)!!),
            createdAt = createdAt,
        )
    }

    @Test
    fun theTeamPutsACitizensDemandInDebateWithALabelOfItsOwn() {
        // A citizen's demand, published « nouvelle » after review: no box.
        events.store(citizen.demand("piscine", "nouvelle"))
        assertEquals(0 to 0, demands.reconcile())
        // A label signed by anyone else than the team changes nothing.
        events.store(citizen.label(citizen, "piscine", "en-debat", 1_794_000_050))
        assertEquals(0 to 0, demands.reconcile())
        // The team's label: in debate, the box opens; the demand keeps its author's signature.
        events.store(editor.label(citizen, "piscine", "en-debat", 1_794_000_100))
        assertEquals(1 to 0, demands.reconcile())
        assertEquals(address(citizen, "piscine"), service.list(0, 10).single().subject)
        // A later label of the team: retained, the box closes.
        events.store(editor.label(citizen, "piscine", "retenue", 1_794_000_200))
        assertEquals(0 to 1, demands.reconcile())
    }

    @Test
    fun aVeryWellSupportedDemandStaysOpenToVotes() {
        events.store(editor.demand("transports", "en-debat"))
        demands.reconcile()
        events.store(editor.demand("transports", "tres-soutenue", createdAt = 1_794_000_100))
        assertEquals(0 to 0, demands.reconcile())
        assertEquals(false, service.list(0, 10).single().closed)
    }
}
