package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.revendications.BuiltInRevendicationView
import org.politisationpopulairefrancaise.core.revendications.CustomRevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationQuery
import org.politisationpopulairefrancaise.core.revendications.RevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import kotlin.random.Random

/**
 * The views of the demands wall and their filters, for one run of the application.
 *
 * - The layout (which views, in which order) is saved on the device through [store] as soon as it
 *   changes, and read once at start; without a store (tests, previews, no database) it lives in
 *   memory. While the user never changed it, the default layout applies (and follows the account:
 *   "Mes revendications" appears once there is one).
 * - The filters are temporary: one set per view, kept while the application runs, never stored.
 *
 * Views and filters reveal political interests (sensitive data, GDPR article 9): nothing here ever
 * leaves the device.
 */
@Stable
class RevendicationViewsState(
    private val store: RevendicationViewStore?,
    private val scope: CoroutineScope,
    /** Whether the user has an account; read in composition, so it may be backed by snapshot state. */
    private val hasAccount: () -> Boolean = { false },
    private val random: Random = Random.Default,
) {
    /** Writes to the device happen one at a time, in the order the user acted. */
    private val writes = Mutex()

    /** The saved layout; null while the user never changed it. */
    private var saved: List<RevendicationView>? by mutableStateOf(null)

    /** False until the saved layout has been read (always true without a store). */
    var loaded: Boolean by mutableStateOf(store == null)
        private set

    /** The id of the view shown (its tab is selected). */
    var currentViewId: String by mutableStateOf(RevendicationViews.ALL_ID)
        private set

    private var filtersByView: Map<String, RevendicationQuery> by mutableStateOf(emptyMap())

    /** Reads the saved layout once; the screen waits for it before showing the tabs. */
    fun start() {
        val store = store ?: return
        scope.launch {
            val layout = withContext(Dispatchers.Default) { runCatching { store.layout() }.getOrNull() }
            saved = layout?.let(RevendicationViews::normalize)
            loaded = true
        }
    }

    /** Every view of the layout, "Mes revendications" included even when it is hidden for lack of an account. */
    val layout: List<RevendicationView>
        get() = saved ?: RevendicationViews.defaultLayout(hasAccount())

    /** The views shown as tabs, in order; "Toutes" is always first. */
    val views: List<RevendicationView>
        get() = RevendicationViews.visible(layout, hasAccount())

    /** True while the user never changed the layout. */
    val isDefault: Boolean get() = saved == null

    /** The view shown; "Toutes" when the current one is gone. */
    val current: RevendicationView
        get() = views.firstOrNull { it.id == currentViewId } ?: RevendicationViews.ALL

    val customCount: Int get() = layout.count { it is CustomRevendicationView }

    /** False once [RevendicationViews.MAX_CUSTOM_VIEWS] custom views exist. */
    val canCreateCustom: Boolean get() = customCount < RevendicationViews.MAX_CUSTOM_VIEWS

    /** The built-in views not shown yet, for "Ajouter une vue". */
    val catalog: List<BuiltInRevendicationView>
        get() = RevendicationViews.catalog(layout, hasAccount())

    fun select(viewId: String) {
        currentViewId = viewId
    }

    /** The temporary filters of a view (empty when none). */
    fun filters(viewId: String): RevendicationQuery = filtersByView[viewId] ?: RevendicationQuery()

    fun setFilters(viewId: String, filters: RevendicationQuery) {
        filtersByView = filtersByView + (viewId to filters)
    }

    /** Removes every filter of a view; the chosen sort stays. */
    fun clearFilters(viewId: String) {
        val sort = filters(viewId).sort
        filtersByView = if (sort == null) filtersByView - viewId else filtersByView + (viewId to RevendicationQuery(sort = sort))
    }

    /**
     * Creates a custom view named [name] with [query], right after the view [afterId] (at the end
     * when it is unknown), and shows it. Null when the name is not valid or the limit is reached.
     */
    fun createView(name: String, query: RevendicationQuery, afterId: String? = null): CustomRevendicationView? {
        val clean = RevendicationViews.normalizeName(name) ?: return null
        if (!canCreateCustom) return null
        val view = CustomRevendicationView(RevendicationViews.newCustomId(random), clean, query)
        val list = layout.toMutableList()
        val index = list.indexOfFirst { it.id == afterId }
        list.add(if (index < 0) list.size else index + 1, view)
        persist(list)
        currentViewId = view.id
        return view
    }

    /** Adds a built-in view at the end of the tabs. */
    fun add(view: BuiltInRevendicationView) {
        if (layout.any { it.id == view.id }) return
        persist(layout + view)
    }

    /** Renames a custom view; false when the name is not valid. */
    fun rename(viewId: String, name: String): Boolean {
        val clean = RevendicationViews.normalizeName(name) ?: return false
        persist(layout.map { if (it.id == viewId && it is CustomRevendicationView) it.copy(name = clean) else it })
        return true
    }

    /** Replaces the criteria of a custom view; its filters are cleared, they were set for the old criteria. */
    fun updateQuery(viewId: String, query: RevendicationQuery) {
        persist(layout.map { if (it.id == viewId && it is CustomRevendicationView) it.copy(query = query) else it })
        filtersByView = filtersByView - viewId
    }

    /** Removes a view from the tabs ("Toutes" stays); the view before it becomes the current one if needed. */
    fun remove(viewId: String) {
        if (viewId == RevendicationViews.ALL_ID) return
        val visible = views
        val index = visible.indexOfFirst { it.id == viewId }
        if (index < 0) return
        if (currentViewId == viewId) currentViewId = visible[index - 1].id
        filtersByView = filtersByView - viewId
        persist(layout.filter { it.id != viewId })
    }

    fun canMoveUp(viewId: String): Boolean = views.indexOfFirst { it.id == viewId } > 1

    fun canMoveDown(viewId: String): Boolean {
        val index = views.indexOfFirst { it.id == viewId }
        return index in 1 until views.lastIndex
    }

    /** Moves a view one tab to the left ("Toutes" stays first). */
    fun moveUp(viewId: String) {
        if (canMoveUp(viewId)) swapWithNeighbour(viewId, -1)
    }

    /** Moves a view one tab to the right. */
    fun moveDown(viewId: String) {
        if (canMoveDown(viewId)) swapWithNeighbour(viewId, +1)
    }

    /** Forgets the saved layout: the default views come back, custom views are deleted. */
    fun reset() {
        saved = null
        val kept = views.map { it.id }.toSet()
        filtersByView = filtersByView.filterKeys { it in kept }
        if (currentViewId !in kept) currentViewId = RevendicationViews.ALL_ID
        val store = store ?: return
        scope.launch { writes.withLock { withContext(Dispatchers.Default) { store.reset() } } }
    }

    /** Swaps a view with its visible neighbour; hidden views ("Mes revendications" without an account) keep their place. */
    private fun swapWithNeighbour(viewId: String, direction: Int) {
        val visible = views
        val index = visible.indexOfFirst { it.id == viewId }
        val other = visible[index + direction].id
        val list = layout.toMutableList()
        val a = list.indexOfFirst { it.id == viewId }
        val b = list.indexOfFirst { it.id == other }
        val moved = list[a]
        list[a] = list[b]
        list[b] = moved
        persist(list)
    }

    private fun persist(layout: List<RevendicationView>) {
        val normalized = RevendicationViews.normalize(layout)
        saved = normalized
        val store = store ?: return
        scope.launch { writes.withLock { withContext(Dispatchers.Default) { store.save(normalized) } } }
    }
}

/**
 * The views of the wall for the life of the composition that remembers it: the shell keeps one
 * for the whole run, so that filters survive a visit to another space. [database] null keeps
 * everything in memory.
 */
@Composable
fun rememberRevendicationViewsState(database: PpfDatabase?, session: AccountSession): RevendicationViewsState {
    val scope = rememberCoroutineScope()
    return remember(database, session) {
        RevendicationViewsState(
            store = database?.let(::RevendicationViewStore),
            scope = scope,
            hasAccount = { session.state is AccountUiState.Ready },
        ).also { it.start() }
    }
}
