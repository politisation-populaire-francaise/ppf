package org.politisationpopulairefrancaise.ui.revendications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.revendications.AuthorScope
import org.politisationpopulairefrancaise.core.revendications.CustomRevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationQuery
import org.politisationpopulairefrancaise.core.revendications.RevendicationSort
import org.politisationpopulairefrancaise.core.revendications.RevendicationThemes
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules of the views state, without any screen. */
class RevendicationViewsStateTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var account = false

    private fun state(store: RevendicationViewStore? = null) = RevendicationViewsState(store, scope, hasAccount = { account })

    private fun RevendicationViewsState.ids() = views.map { it.id }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met in time" }
            Thread.sleep(10)
        }
    }

    @Test
    fun everyThemeHasALabel() {
        RevendicationThemes.ALL.forEach { assertNotNull(themeLabelResource(it.id), it.id) }
        assertNull(themeLabelResource("inconnu"))
    }

    @Test
    fun theDefaultLayoutFollowsTheAccountUntilTheUserChangesIt() {
        val state = state()
        assertTrue(state.loaded)
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:societe", "theme:sante"), state.ids())
        account = true
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:societe", "theme:sante", "mes-revendications"), state.ids())
        assertTrue(state.isDefault)
        state.moveUp("theme:sante")
        assertFalse(state.isDefault)
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:sante", "theme:societe", "mes-revendications"), state.ids())
        // Without the account, "Mes revendications" is hidden but keeps its place; moves skip it.
        account = false
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:sante", "theme:societe"), state.ids())
        assertFalse(state.canMoveDown("theme:societe"))
        state.moveUp("theme:societe")
        account = true
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:societe", "theme:sante", "mes-revendications"), state.ids())
    }

    @Test
    fun filtersArePerViewAndClearingKeepsTheSort() {
        val state = state()
        state.setFilters("tout", RevendicationQuery(themes = setOf("sante"), sort = RevendicationSort.POPULAR))
        assertEquals(RevendicationQuery(), state.filters("suivis"))
        state.clearFilters("tout")
        assertEquals(RevendicationQuery(sort = RevendicationSort.POPULAR), state.filters("tout"))
    }

    @Test
    fun createdViewsComeAfterTheirSourceAndRemovingTheCurrentViewShowsThePreviousOne() {
        val state = state()
        assertNull(state.createView("   ", RevendicationQuery()))
        val view = assertNotNull(state.createView(" Lyon ", RevendicationQuery(territories = setOf("metropole de lyon")), afterId = "suivis"))
        assertEquals("Lyon", view.name)
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", view.id, "theme:societe", "theme:sante"), state.ids())
        assertEquals(view.id, state.currentViewId)
        state.setFilters(view.id, RevendicationQuery(text = "nuit"))
        assertTrue(state.rename(view.id, "Lyon la nuit"))
        assertFalse(state.rename(view.id, ""))
        state.updateQuery(view.id, RevendicationQuery(authors = AuthorScope.FOLLOWED))
        assertEquals(RevendicationQuery(), state.filters(view.id))
        assertEquals("Lyon la nuit", (state.current as CustomRevendicationView).name)
        state.remove(view.id)
        assertEquals("suivis", state.currentViewId)
        state.remove("tout") // permanent
        assertEquals("tout", state.ids().first())
    }

    @Test
    fun atMostTwentyCustomViews() {
        val state = state()
        repeat(RevendicationViews.MAX_CUSTOM_VIEWS) { assertNotNull(state.createView("Vue $it", RevendicationQuery())) }
        assertFalse(state.canCreateCustom)
        assertNull(state.createView("Une de trop", RevendicationQuery()))
        assertEquals(RevendicationViews.MAX_CUSTOM_VIEWS, state.customCount)
    }

    @Test
    fun theLayoutIsSavedReadBackAndResetOnTheDevice() {
        val database = JvmDatabase.inMemory()
        val store = RevendicationViewStore(database)
        val first = state(store)
        first.start()
        waitFor { first.loaded }
        first.add(RevendicationViews.NATIONAL)
        first.remove("theme:societe")
        waitFor { store.layout()?.map { it.id } == listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:sante", "nationales") }

        val second = state(store)
        assertFalse(second.loaded)
        second.start()
        waitFor { second.loaded }
        assertEquals(listOf("tout", "plus-de-pour", "recentes", "du-mouvement", "au-parlement", "suivis", "theme:sante", "nationales"), second.ids())
        assertTrue(second.catalog.any { it.id == "theme:societe" })
        assertFalse(second.catalog.any { it.id == "nationales" })

        second.reset()
        assertTrue(second.isDefault)
        waitFor { store.layout() == null }
    }
}
