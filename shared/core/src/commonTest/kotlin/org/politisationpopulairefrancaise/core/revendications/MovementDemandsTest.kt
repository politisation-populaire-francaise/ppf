package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The demands already carried by the movement (MVP 1): the tags the team writes and what a reader keeps. */
class MovementDemandsTest {

    private fun demand(extra: List<List<String>>): Article {
        val tags = listOf(
            listOf("d", "smic"),
            listOf("title", "Une hausse du SMIC"),
            listOf("ppf-type", "revendication"),
            listOf("ppf-origin", "ai-assisted"),
        ) + extra
        val event = SignedEvent(id = "0".repeat(64), pubkey = "1".repeat(64), createdAt = 1_000, kind = Article.KIND, tags = tags, content = "## Sources", sig = "0".repeat(128))
        return Article.from(event)!!
    }

    @Test
    fun theTagsSayWhereTheDemandComesFromAndWhoCarriesItInTheOrderGiven() {
        val tags = MovementDemands.tags(listOf(" Intersyndicale, appel du 5 octobre 2026 ", "CGT"))
        assertEquals(
            listOf(listOf("ppf-from", "mouvement"), listOf("ppf-carried-by", "Intersyndicale, appel du 5 octobre 2026"), listOf("ppf-carried-by", "CGT")),
            tags,
        )
        val article = demand(tags)
        assertTrue(MovementDemands.isMarked(article))
        assertEquals(listOf("Intersyndicale, appel du 5 octobre 2026", "CGT"), MovementDemands.carriers(article))
    }

    @Test
    fun anOrdinaryDemandIsNotMarkedAndADemandOfTheMovementNamesWhoCarriesIt() {
        assertFalse(MovementDemands.isMarked(demand(emptyList())))
        assertFalse(MovementDemands.isMarked(demand(listOf(listOf("ppf-from", "ailleurs")))))
        assertFailsWith<IllegalArgumentException> { MovementDemands.tags(listOf(" ")) }
        assertFailsWith<IllegalArgumentException> { MovementDemands.tags(listOf("x".repeat(201))) }
    }
}
