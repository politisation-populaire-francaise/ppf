package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A deliberation as a device shows it (MVP 1): who may publish what, and the order of the propositions. */
class DeliberationBoardTest {
    private val team = "a".repeat(64)
    private val alice = "b".repeat(64)
    private val bob = "c".repeat(64)
    private var counter = 0

    private fun event(pubkey: String, createdAt: Long, tags: List<List<String>>, content: String = "Texte"): SignedEvent {
        counter++
        return SignedEvent(id = counter.toString(16).padStart(64, '0'), pubkey = pubkey, createdAt = createdAt, kind = EventKinds.LONG_FORM_ARTICLE, tags = tags, content = content, sig = "0".repeat(128))
    }

    private fun deliberation(pubkey: String = team, slug: String = "pacte-2027") = event(
        pubkey, 100,
        listOf(
            listOf("d", slug), listOf("title", "[EXEMPLE] Les mesures du Pacte"), listOf("ppf-type", "deliberation"),
            listOf("ppf-phase", "propositions", "1000", "2000"), listOf("ppf-phase", "amendements", "2000", "3000"), listOf("ppf-phase", "vote", "3000", "4000"),
            listOf("ppf-measures", "5", "10"),
        ),
    )

    private fun address(event: SignedEvent) = ContentAddress.of(event)!!

    private fun proposition(pubkey: String, deliberation: SignedEvent, slug: String, createdAt: Long, start: Boolean = false) = event(
        pubkey, createdAt,
        buildList {
            add(listOf("d", slug)); add(listOf("title", "[EXEMPLE] $slug")); add(listOf("ppf-type", "proposition")); add(listOf("a", address(deliberation).toString()))
            if (start) add(listOf("ppf-origin", "depart"))
        },
    )

    private fun amendment(pubkey: String, proposition: SignedEvent, slug: String, createdAt: Long) = event(
        pubkey, createdAt, listOf(listOf("d", slug), listOf("ppf-type", "amendement"), listOf("a", address(proposition).toString())), "Nouvelle rédaction",
    )

    @Test
    fun onlyTheTeamOpensADeliberationAndItsStartingProposalsComeFirst() {
        val open = deliberation()
        val fake = deliberation(pubkey = alice, slug = "faux")
        val fromAlice = proposition(alice, open, "alice", 1100)
        val start = proposition(team, open, "depart", 1200, start = true)
        val fakeStart = proposition(bob, open, "faux-depart", 1050, start = true)
        val amend = amendment(bob, fromAlice, "amendement", 2100)

        val boards = DeliberationBoard.from(listOf(open, fake, fromAlice, start, fakeStart, amend)) { it == team }
        assertEquals(1, boards.size)
        val board = boards.single()
        assertEquals(listOf("depart", "alice"), board.entries.map { it.proposition.address.identifier })
        assertEquals(listOf("amendement"), board.entry(address(fromAlice))!!.amendments.map { it.address.identifier })
        assertEquals(DeliberationPhase.PROPOSITIONS, board.phaseAt(1500))
        assertNull(board.phaseAt(5000))
    }

    @Test
    fun theDraftsFollowTheFormatTheServerReviews() {
        val open = deliberation()
        val key = org.politisationpopulairefrancaise.core.identity.PublicKey.fromHex(alice)
        val draft = DeliberationDrafts.proposition(key, address(open), " Un titre ", "Le texte", "Parce que", 1500, "ma-proposition")
        val signed = SignedEvent(id = "f".repeat(64), pubkey = alice, createdAt = draft.createdAt, kind = draft.kind, tags = draft.tags, content = draft.content, sig = "0".repeat(128))
        val parsed = Proposition.parse(signed)!!
        assertEquals("Un titre", parsed.title)
        assertEquals("Parce que", parsed.justification)
        assertEquals(address(open), parsed.deliberation)

        val amend = DeliberationDrafts.amendment(key, parsed.address, "Une autre rédaction", null, 2500, "mon-amendement")
        assertTrue(amend.tags.contains(listOf("a", parsed.address.toString())))
        assertTrue(amend.tags.none { it.first() == DeliberationFormats.TAG_JUSTIFICATION })
    }
}
