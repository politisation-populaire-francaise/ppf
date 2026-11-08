package org.politisationpopulairefrancaise.server.urne

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.decider.DeliberationFormats
import org.politisationpopulairefrancaise.core.decider.DeliberationRules
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ballot boxes of a citizen deliberation (DE-25), opened phase after phase from the published events. */
class DeliberationUrnesTest {
    private val start = Instant.parse("2026-12-07T00:00:00Z")
    private val week = 7 * 86_400L
    private val clock = MutableClock(start.minusSeconds(3_600))
    private val team = LocalEventSigner(IdentityKeys.generate())
    private val events = EventRepository(TestDatabase.dataSource)
    private val service = UrneFixtures.service(clock)
    private val urnes = DeliberationUrnes(events, service, { it == team.publicKey.hex }, clock)

    @BeforeTest
    fun emptyTables() {
        TestDatabase.reset()
        UrneFixtures.reset()
    }

    private fun publish(signer: LocalEventSigner, d: String, type: String, link: String?, tags: List<List<String>> = emptyList(), at: Long = clock.instant.epochSecond): SignedEvent {
        val event = runBlocking {
            signer.sign(
                kind = EventKinds.LONG_FORM_ARTICLE,
                tags = listOfNotNull(listOf("d", d), listOf(DeliberationFormats.TAG_TYPE, type), listOf("title", "[EXEMPLE] $d"), link?.let { listOf("a", it) }) + tags,
                content = "[EXEMPLE] Texte de $d",
                createdAt = at,
            )
        }
        events.store(event)
        return event
    }

    private fun deliberation(signer: LocalEventSigner = team): ContentAddress {
        val s = start.epochSecond
        val event = publish(
            signer, "pacte-2027", DeliberationFormats.TYPE_DELIBERATION, null,
            listOf(
                listOf("ppf-measures", "5", "10"),
                listOf("ppf-phase", "propositions", "$s", "${s + 3 * week}"),
                listOf("ppf-phase", "amendements", "${s + 3 * week}", "${s + 5 * week}"),
                listOf("ppf-phase", "vote", "${s + 5 * week}", "${s + 7 * week}"),
            ),
        )
        return ContentAddress.of(event)!!
    }

    private fun at(seconds: Long) {
        clock.instant = start.plusSeconds(seconds)
    }

    @Test
    fun theBoxesOpenPhaseAfterPhase() {
        val deliberation = deliberation().toString()
        val alice = LocalEventSigner(IdentityKeys.generate())
        val bob = LocalEventSigner(IdentityKeys.generate())
        val departure = ContentAddress.of(publish(team, "depart", DeliberationFormats.TYPE_PROPOSITION, deliberation, listOf(listOf("ppf-origin", "depart"))))!!
        // Before the first phase: nothing.
        assertEquals(0, urnes.reconcile())
        at(60)
        val first = ContentAddress.of(publish(alice, "alice-1", DeliberationFormats.TYPE_PROPOSITION, deliberation))!!
        publish(alice, "alice-2", DeliberationFormats.TYPE_PROPOSITION, deliberation)
        // A citizen cannot pass a proposal off as PPF's starting one.
        val fake = ContentAddress.of(publish(bob, "faux-depart", DeliberationFormats.TYPE_PROPOSITION, deliberation, listOf(listOf("ppf-origin", "depart"))))!!
        assertEquals(2, urnes.reconcile(), "the starting proposal and Alice's first one")
        assertEquals(0, urnes.reconcile(), "idempotent")
        val support = service.byId(DeliberationRules.supportUrneId(first))!!
        assertEquals(listOf(DeliberationFormats.SUPPORT), support.choices)
        assertEquals(start.plusSeconds(3 * week), support.closesAt)
        assertNull(service.byId(DeliberationRules.supportUrneId(fake)))
        // Phase of amendments: a box per amendment of a proposition that goes on, none for a late proposition.
        at(3 * week + 60)
        assertEquals(2, service.closeDue())
        val amendment = ContentAddress.of(publish(bob, "bob-a1", DeliberationFormats.TYPE_AMENDMENT, first.toString()))!!
        publish(bob, "late", DeliberationFormats.TYPE_PROPOSITION, deliberation)
        assertEquals(1, urnes.reconcile())
        val box = service.byId(DeliberationRules.amendmentUrneId(amendment))!!
        assertEquals(listOf("pour", "contre", "blanc"), box.choices)
        assertEquals(start.plusSeconds(5 * week), box.closesAt)
        assertTrue(!box.liveResults, "results after the closing")
        // Final vote: one box per proposition going on; without votes, the original wording is put to the vote.
        at(5 * week + 60)
        assertEquals(1, service.closeDue())
        assertEquals(2, urnes.reconcile())
        assertEquals(first.toString(), service.byId(DeliberationRules.finalUrneId(first))!!.subject)
        assertEquals(departure.toString(), service.byId(DeliberationRules.finalUrneId(departure))!!.subject)
        // After the vote: nothing more opens.
        at(7 * week + 60)
        assertEquals(0, urnes.reconcile())
    }

    @Test
    fun aDeliberationOfAnotherKeyOpensNothing() {
        val deliberation = deliberation(LocalEventSigner(IdentityKeys.generate())).toString()
        at(60)
        publish(LocalEventSigner(IdentityKeys.generate()), "p1", DeliberationFormats.TYPE_PROPOSITION, deliberation)
        assertEquals(0, urnes.reconcile())
    }
}
