package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The debate of a demand (DE-20): the comments and what a reader keeps of them. */
class DemandDebateTest {
    private val demand = ContentAddress(30023, "a".repeat(64), "hopital")
    private val other = ContentAddress(30023, "a".repeat(64), "ecole")
    private val alice = PublicKey.fromHex("b".repeat(64))
    private var counter = 0

    private fun signed(unsigned: UnsignedEvent): SignedEvent {
        counter++
        return SignedEvent(counter.toString(16).padStart(64, '0'), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
    }

    @Test
    fun aCommentNamesItsDemandAndItsStance() {
        val unsigned = DemandDebate.build(alice, demand, DebateStance.MITIGE, "  [EXEMPLE] Oui, mais avec un budget.  ", 100)
        assertTrue(unsigned.tags.contains(listOf("A", demand.toString())))
        assertTrue(unsigned.tags.contains(listOf("ppf-stance", "mitige")))
        val comment = DemandDebate.parse(signed(unsigned))!!
        assertEquals(demand, comment.demand)
        assertEquals(DebateStance.MITIGE, comment.stance)
        assertEquals("[EXEMPLE] Oui, mais avec un budget.", comment.text)
        assertFailsWith<IllegalArgumentException> { DemandDebate.build(alice, demand, DebateStance.POUR, " ", 100) }
        assertFailsWith<IllegalArgumentException> { DemandDebate.build(alice, demand, DebateStance.POUR, "x".repeat(DemandDebate.MAX_TEXT + 1), 100) }
    }

    @Test
    fun theDebateOfADemandKeepsItsOwnCommentsOnceTheNewestFirstAndCountsThem() {
        val first = signed(DemandDebate.build(alice, demand, DebateStance.POUR, "[EXEMPLE] Pour", 100))
        val second = signed(DemandDebate.build(alice, demand, DebateStance.CONTRE, "[EXEMPLE] Contre", 200))
        val elsewhere = signed(DemandDebate.build(alice, other, DebateStance.POUR, "[EXEMPLE] Ailleurs", 300))
        val noStance = signed(UnsignedEvent.create(alice, DemandDebate.KIND, 400, listOf(listOf("A", demand.toString())), "[EXEMPLE] Sans position"))
        val comments = DemandDebate.of(listOf(first, second, elsewhere, noStance, first), demand)
        assertEquals(listOf(second.id, first.id), comments.map { it.id })
        assertEquals(mapOf(DebateStance.POUR to 1, DebateStance.CONTRE to 1, DebateStance.MITIGE to 0), DemandDebate.counts(comments))
        assertNull(DemandDebate.parse(noStance))
    }
}
