package org.politisationpopulairefrancaise.core.revendications

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevendicationViewsTest {

    private fun custom(name: String, query: RevendicationQuery = RevendicationQuery(text = name)) =
        CustomRevendicationView(RevendicationViews.newCustomId(), name, query)

    @Test
    fun builtInViewsHaveStableIdsAndTheirCriteria() {
        assertEquals(
            listOf("tout", "suivis", "mes-revendications", "nationales") + RevendicationViews.USEFUL.map { it.id } + RevendicationThemes.ALL.map { "theme:${it.id}" },
            RevendicationViews.BUILT_IN.map { it.id },
        )
        assertTrue(RevendicationViews.ALL.query.isUnconstrained)
        assertEquals(AuthorScope.FOLLOWED, RevendicationViews.FOLLOWED.query.authors)
        assertEquals(AuthorScope.MINE, RevendicationViews.MINE.query.authors)
        assertEquals(setOf("france"), RevendicationViews.NATIONAL.query.territories)
        assertEquals(setOf("sante"), RevendicationViews.theme("sante")?.query?.themes)
        assertEquals(setOf(RevendicationCriterion.THEMES), RevendicationViews.theme("sante")?.fixedCriteria)
        assertNull(RevendicationViews.builtIn("theme:inconnu"))
    }

    @Test
    fun theDefaultLayoutIsTheOwnersExamplesThenMyDemandsWithAnAccount() {
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:societe", "theme:sante"), RevendicationViews.defaultLayout(hasAccount = false).map { it.id })
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:societe", "theme:sante", "mes-revendications"), RevendicationViews.defaultLayout(hasAccount = true).map { it.id })
    }

    @Test
    fun normalizingPutsToutesFirstOnceAndCapsCustomViews() {
        val sante = RevendicationViews.theme("sante")!!
        val layout = listOf(sante, RevendicationViews.ALL, sante, RevendicationViews.FOLLOWED)
        assertEquals(listOf("tout", "theme:sante", "suivis"), RevendicationViews.normalize(layout).map { it.id })
        assertEquals(listOf("tout"), RevendicationViews.normalize(emptyList()).map { it.id })

        val many = (1..25).map { custom("Vue $it") }
        val normalized = RevendicationViews.normalize(many)
        assertEquals(1 + RevendicationViews.MAX_CUSTOM_VIEWS, normalized.size)
        assertEquals(many.take(RevendicationViews.MAX_CUSTOM_VIEWS).map { it.id }, normalized.drop(1).map { it.id })
    }

    @Test
    fun myDemandsAreShownAndOfferedOnlyWithAnAccount() {
        val layout = RevendicationViews.defaultLayout(hasAccount = true)
        assertEquals(layout.size - 1, RevendicationViews.visible(layout, hasAccount = false).size)
        assertEquals(layout, RevendicationViews.visible(layout, hasAccount = true))

        val shown = RevendicationViews.defaultLayout(hasAccount = false)
        val catalogWithout = RevendicationViews.catalog(shown, hasAccount = false).map { it.id }
        assertTrue("mes-revendications" !in catalogWithout)
        assertTrue("nationales" in catalogWithout)
        assertTrue("theme:sante" !in catalogWithout && "tout" !in catalogWithout)
        assertEquals(RevendicationViews.BUILT_IN.size - shown.size - 1, catalogWithout.size)
        assertTrue("mes-revendications" in RevendicationViews.catalog(shown, hasAccount = true).map { it.id })
    }

    @Test
    fun namesAreTrimmedAndLimited() {
        assertEquals("Santé à Lyon", RevendicationViews.normalizeName("  Santé   à Lyon "))
        assertNull(RevendicationViews.normalizeName("   "))
        assertEquals("a".repeat(40), RevendicationViews.normalizeName("a".repeat(40)))
        assertNull(RevendicationViews.normalizeName("a".repeat(41)))
        assertFailsWith<IllegalArgumentException> { CustomRevendicationView("custom:1", " espace", RevendicationQuery()) }
        assertFailsWith<IllegalArgumentException> { CustomRevendicationView("theme:sante", "Santé", RevendicationQuery()) }
    }

    @Test
    fun customIdsArePrefixedRandomHex() {
        val id = RevendicationViews.newCustomId(Random(1))
        assertTrue(id.startsWith("custom:"))
        assertEquals(16, id.removePrefix("custom:").length)
        assertTrue(id.removePrefix("custom:").all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(RevendicationViews.newCustomId(), RevendicationViews.newCustomId())
    }
}
