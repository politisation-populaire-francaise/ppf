package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.revendications.CustomRevendicationView
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.feed.FeedTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.shell.openSpace
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Views and filters of the demands wall (owner's request of 2026-11-06), on the eight bundled
 * example demands: Lyon (transports, très soutenue), transparency (démocratie, en débat), school
 * canteens (éducation and pouvoir d'achat, nouvelle), on-call doctors (santé, nouvelle), digital
 * workshops (numérique, en débat), nurseries (société, nouvelle), schools of the Nord (éducation,
 * retenue) and student housing in Marseille (logement and éducation, campagne).
 */
@OptIn(ExperimentalTestApi::class)
class RevendicationViewsScreenTest {

    private val slugs = listOf(
        "lyon-transports-nuit", "transparence-agenda-elus", "cantines-tarif-social", "medecin-de-garde",
        "ateliers-numeriques-mairies", "creches-horaires-decales", "ecoles-chaleur-nord", "logements-etudiants-marseille",
    )

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.inPage(viewId: String, tag: String): SemanticsNodeInteraction =
        onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(RevendicationTags.PAGE + viewId)))

    /** The demands shown by the page of a view. */
    private fun ComposeUiTest.slugsIn(viewId: String): Set<String> = slugs.filter { slug ->
        onAllNodes(hasTestTag(RevendicationTags.CARD + slug) and hasAnyAncestor(hasTestTag(RevendicationTags.PAGE + viewId))).fetchSemanticsNodes().isNotEmpty()
    }.toSet()

    /** The demands of a view, top to bottom. */
    private fun ComposeUiTest.orderIn(viewId: String): List<String> =
        slugsIn(viewId).sortedBy { inPage(viewId, RevendicationTags.CARD + it).fetchSemanticsNode().positionInRoot.y }

    /** The demands already carried by the movement (MVP 1), bundled beside the examples. */
    private val movement = listOf(
        "aides-sociales", "carburants-energie", "demission-du-president", "democratie", "droit-de-manifester", "etudiants",
        "fin-de-parcoursup", "hausse-des-pensions", "hausse-du-smic", "justice-fiscale", "lycees", "negociations-salariales",
        "planification", "point-d-indice", "services-publics",
    ).map { "mouvement-$it" } + "demission-du-gouvernement"

    /**
     * [text] is what the examples alone give ("3 revendications"); the demands of the movement sit beside them.
     * The examples are counted among the cards, and the number shown counts every card of the page.
     */
    private fun ComposeUiTest.assertCount(viewId: String, text: String) {
        assertEquals(text.substringBefore(' ').toInt(), slugsIn(viewId).size, text)
        val cards = (slugs + movement).count { slug ->
            onAllNodes(hasTestTag(RevendicationTags.CARD + slug) and hasAnyAncestor(hasTestTag(RevendicationTags.PAGE + viewId))).fetchSemanticsNodes().isNotEmpty()
        }
        inPage(viewId, RevendicationTags.COUNT).assertTextEquals(if (cards <= 1) "$cards revendication" else "$cards revendications")
    }

    /** MVP 1: a demand of the movement says so, and who carries it; an example does not. */
    @Test
    fun aDemandOfTheMovementSaysWhoCarriesIt() = runComposeUiTest {
        openWall(memory())
        inPage("tout", RevendicationTags.CARD + "mouvement-hausse-des-pensions").performScrollTo()
            .assert(hasAnyDescendant(hasText("Du mouvement")))
            .assert(hasAnyDescendant(hasText("Portée par :", substring = true)))
        assertTrue(onAllNodes(hasTestTag(RevendicationTags.MOVEMENT) and hasAnyAncestor(hasTestTag(RevendicationTags.CARD + "medecin-de-garde"))).fetchSemanticsNodes().isEmpty())
    }

    /** D-152: a citizen demand names who else publicly supports it, without the chip of the movement. */
    @Test
    fun aCitizenDemandSaysWhoElseSupportsItPublicly() = runComposeUiTest {
        openWall(memory())
        val card = RevendicationTags.CARD + "demission-du-gouvernement"
        inPage("tout", card).performScrollTo()
        inPage("tout", RevendicationTags.SUPPORTERS).assertTextEquals("Également portée par : NPA-L’Anticapitaliste (parti politique), 4 novembre 2026")
        assertTrue(onAllNodes(hasTestTag(RevendicationTags.MOVEMENT) and hasAnyAncestor(hasTestTag(card))).fetchSemanticsNodes().isEmpty())
    }

    private fun ComposeUiTest.waitForDemands() {
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { has(RevendicationTags.CARD + "transparence-agenda-elus") }
    }

    private fun ComposeUiTest.openWall(dependencies: AppDependencies, width: Int = 1000, height: Int = 740) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) } }
        openSpace(Space.REVENDICATIONS)
        waitForDemands()
    }

    private fun ComposeUiTest.openView(viewId: String) {
        onNodeWithTag(RevendicationTags.TAB + viewId).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.TAB + viewId).assertIsSelected()
    }

    private fun ComposeUiTest.closeModal() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun ComposeUiTest.openFilters(viewId: String) {
        inPage(viewId, RevendicationTags.FILTERS).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(FilterPanelTags.PANEL).assertExists()
    }

    /** The button inside a [org.politisationpopulairefrancaise.ui.components.PpfIconButton] (its tag sits on the frame around it). */
    private fun ComposeUiTest.iconButton(tag: String): SemanticsNodeInteraction = onNodeWithTag(tag).onChildAt(0)

    private fun ComposeUiTest.openCustomize(viewId: String) {
        inPage(viewId, RevendicationTags.CUSTOMIZE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ManageViewsTags.DIALOG).assertExists()
    }

    /** The ids of the tabs, left to right. */
    private fun ComposeUiTest.tabIds(): List<String> = onAllNodes(
        SemanticsMatcher("is a tab of the wall") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(RevendicationTags.TAB) == true
        },
    ).fetchSemanticsNodes()
        .sortedBy { it.positionInRoot.x }
        .map { it.config[SemanticsProperties.TestTag].removePrefix(RevendicationTags.TAB) }

    private fun memory() = AppDependencies(identityStore = InMemoryIdentityStore(), contentStore = LocalEventStore(JvmDatabase.inMemory()))

    private fun persistent(database: PpfDatabase, identity: IdentityStore = InMemoryIdentityStore()) = AppDependencies.withDatabase(identity, database)

    /** MVP 1 (Galaxy A34, 360 dp): one row of tools, the explanation folded into one line that unfolds on a touch. */
    @Test
    fun onAPhoneTheToolsShareOneRowAndTheExplanationIsFolded() = runComposeUiTest {
        openWall(memory(), width = 360, height = 780)
        val filters = inPage("tout", RevendicationTags.FILTERS).fetchSemanticsNode().boundsInRoot
        val customize = inPage("tout", RevendicationTags.CUSTOMIZE).fetchSemanticsNode().boundsInRoot
        assertEquals(filters.top, customize.top, "Filtres and Personnaliser share one row")
        assertFalse(has(RevendicationTags.HOW_IT_WORKS_TEXT), "the explanation starts folded")

        inPage("tout", RevendicationTags.HOW_IT_WORKS).performScrollTo().performClick()
        waitForIdle()
        assertTrue(has(RevendicationTags.HOW_IT_WORKS_TEXT), "a touch unfolds the explanation")
        inPage("tout", RevendicationTags.HOW_IT_WORKS).performScrollTo().performClick()
        waitForIdle()
        assertFalse(has(RevendicationTags.HOW_IT_WORKS_TEXT), "a second touch folds it again")
    }

    @Test
    fun theDefaultViewsAreTabsAndAThemeViewShowsOnlyItsDemands() = runComposeUiTest {
        openWall(memory())
        assertEquals(listOf("tout", "suivis", "theme:societe", "theme:sante"), tabIds())
        onNodeWithTag(RevendicationTags.TAB + "tout").assertIsSelected()
        assertCount("tout", "8 revendications")
        assertEquals(slugs.toSet(), slugsIn("tout"))

        openView("theme:sante")
        assertCount("theme:sante", "1 revendication")
        assertEquals(setOf("medecin-de-garde"), slugsIn("theme:sante"))

        // "Famille" is a topic of the Société theme.
        openView("theme:societe")
        assertEquals(setOf("creches-horaires-decales"), slugsIn("theme:societe"))

        // Cards name their themes with their labels, not their raw topics.
        inPage("theme:societe", RevendicationTags.CARD + "creches-horaires-decales").assertTextContains("Société")
        assertTrue(onAllNodesWithText("#famille", substring = true).fetchSemanticsNodes().isEmpty())

        openView("tout")
        assertCount("tout", "8 revendications")
    }

    @Test
    fun aFilterNarrowsTheListAndItsChipRemovesIt() = runComposeUiTest {
        openWall(memory())
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.THEME + "education").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.THEME + "education").assertIsOn()
        // The main button counts live.
        onNodeWithTag(FilterPanelTags.APPLY).assertTextContains("Afficher 3 revendications")
        onNodeWithTag(FilterPanelTags.APPLY).performClick()
        closeModal()
        onNodeWithTag(FilterPanelTags.PANEL).assertDoesNotExist()

        assertCount("tout", "3 revendications")
        assertEquals(setOf("cantines-tarif-social", "ecoles-chaleur-nord", "logements-etudiants-marseille"), slugsIn("tout"))
        inPage("tout", RevendicationTags.FILTERS).assert(hasContentDescription("Filtres, 1 filtre actif"))

        // A second filter, on the territory: both apply.
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.THEME + "education").performScrollTo().assertIsOn()
        onNodeWithTag(FilterPanelTags.TERRITORY + "nord").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.APPLY).performClick()
        closeModal()
        assertEquals(setOf("ecoles-chaleur-nord"), slugsIn("tout"))
        inPage("tout", RevendicationTags.CHIP + "territory:nord").assertExists()

        // Each chip removes its filter; "Tout effacer" removes the rest.
        inPage("tout", RevendicationTags.CHIP + "theme:education").performScrollTo().performClick()
        waitForIdle()
        inPage("tout", RevendicationTags.CHIP + "theme:education").assertDoesNotExist()
        assertCount("tout", "1 revendication")
        inPage("tout", RevendicationTags.CLEAR_ALL).performScrollTo().performClick()
        waitForIdle()
        assertCount("tout", "8 revendications")
        inPage("tout", RevendicationTags.CLEAR_ALL).assertDoesNotExist()

        // Filters belong to their view: the Santé view is untouched by those of Toutes.
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.STATUS + "campagne").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.APPLY).performClick()
        closeModal()
        assertCount("tout", "1 revendication")
        openView("theme:sante")
        assertCount("theme:sante", "1 revendication")
        openView("tout")
        assertCount("tout", "1 revendication")
    }

    @Test
    fun keywordsAndAnEmptyResultOfferToClearTheFilters() = runComposeUiTest {
        openWall(memory())
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.TEXT).performScrollTo().performTextInput("crèches")
        onNodeWithTag(FilterPanelTags.APPLY).assertTextContains("Afficher 1 revendication")
        onNodeWithTag(FilterPanelTags.TEXT).performTextReplacement("introuvable")
        onNodeWithTag(FilterPanelTags.APPLY).performClick()
        closeModal()
        assertCount("tout", "0 revendication")
        inPage("tout", RevendicationTags.CHIP + "text").assertExists()
        inPage("tout", RevendicationTags.EMPTY).assertExists()
        assertTrue(onAllNodesWithText("Aucune revendication ne correspond à ces filtres.").fetchSemanticsNodes().isNotEmpty())
        inPage("tout", RevendicationTags.EMPTY_CLEAR).performScrollTo().performClick()
        waitForIdle()
        assertCount("tout", "8 revendications")
    }

    @Test
    fun aThemeViewLocksItsThemeInTheFilterPanel() = runComposeUiTest {
        openWall(memory())
        openView("theme:sante")
        openFilters("theme:sante")
        onNodeWithTag(FilterPanelTags.LOCKED + "themes").performScrollTo().assertExists()
        assertTrue(onAllNodesWithText("Défini par la vue « Santé »").fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag(FilterPanelTags.THEME + "sante").performScrollTo().assertIsOn().assertIsNotEnabled()
        onNodeWithTag(FilterPanelTags.THEME + "logement").assertIsOff().assertIsNotEnabled()
        // Other criteria stay free: the status narrows the view's demands.
        onNodeWithTag(FilterPanelTags.STATUS + "en-debat").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.APPLY).assertTextContains("Afficher 0 revendication")
        onNodeWithTag(FilterPanelTags.STATUS + "en-debat").performClick()
        onNodeWithTag(FilterPanelTags.STATUS + "nouvelle").performClick()
        onNodeWithTag(FilterPanelTags.APPLY).performClick()
        closeModal()
        assertEquals(setOf("medecin-de-garde"), slugsIn("theme:sante"))
        inPage("theme:sante", RevendicationTags.CHIP + "status:nouvelle").assertExists()
        // The theme of the view is not a removable filter.
        inPage("theme:sante", RevendicationTags.CHIP + "theme:sante").assertDoesNotExist()
    }

    @Test
    fun sortingChangesTheOrder() = runComposeUiTest {
        openWall(memory())
        // Newest first, then by title.
        assertEquals(
            listOf("lyon-transports-nuit", "transparence-agenda-elus", "cantines-tarif-social", "medecin-de-garde", "ateliers-numeriques-mairies", "creches-horaires-decales", "ecoles-chaleur-nord", "logements-etudiants-marseille"),
            orderIn("tout"),
        )
        inPage("tout", RevendicationTags.SORT).assertTextContains("Tri : Nouveauté")

        // Popularity: the status until votes open (campagne, retenue, très soutenue, en débat, nouvelle), then the last update.
        inPage("tout", RevendicationTags.SORT).performScrollTo().performClick()
        waitForIdle()
        assertTrue(onAllNodesWithText("Provisoire", substring = true).fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag(RevendicationTags.SORT_OPTION + "popular").performClick()
        closeModal()
        assertEquals(
            listOf("logements-etudiants-marseille", "ecoles-chaleur-nord", "lyon-transports-nuit", "transparence-agenda-elus", "ateliers-numeriques-mairies", "cantines-tarif-social", "medecin-de-garde", "creches-horaires-decales"),
            orderIn("tout"),
        )
        inPage("tout", RevendicationTags.SORT).assertTextContains("Tri : Popularité")

        // Recent updates, chosen in the filter panel this time.
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.SORT + "popular").assertIsSelected()
        onNodeWithTag(FilterPanelTags.SORT + "updated").performClick()
        onNodeWithTag(FilterPanelTags.APPLY).performClick()
        closeModal()
        assertEquals("logements-etudiants-marseille", orderIn("tout")[5])
        assertEquals("creches-horaires-decales", orderIn("tout").last())
    }

    @Test
    fun savingFiltersAsAViewAddsATabThatSurvivesANewScreen() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        val dependencies = persistent(database)
        var generation by mutableStateOf(0)
        setContent { key(generation) { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } } }
        openSpace(Space.REVENDICATIONS)
        waitForDemands()

        openView("suivis")
        openView("tout")
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.THEME + "transports").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.SAVE_AS_VIEW).performClick()
        waitForIdle()
        onNodeWithTag(FilterPanelTags.NAME_CONFIRM).assertIsNotEnabled() // no name yet
        onNodeWithTag(FilterPanelTags.NAME_FIELD).performTextInput("  Lyon la nuit  ")
        onNodeWithTag(FilterPanelTags.NAME_CONFIRM).performClick()
        closeModal()

        // The new tab comes right after the view it was made from, and is shown.
        val store = RevendicationViewStore(database)
        waitUntil(timeoutMillis = 5_000) { store.layout()?.any { it is CustomRevendicationView } == true }
        val custom = store.layout()!!.filterIsInstance<CustomRevendicationView>().single()
        assertEquals("Lyon la nuit", custom.name)
        assertEquals(listOf("tout", custom.id, "suivis", "theme:societe", "theme:sante"), tabIds())
        onNodeWithTag(RevendicationTags.TAB + custom.id).assertIsSelected().assertTextEquals("Lyon la nuit")
        assertEquals(setOf("lyon-transports-nuit"), slugsIn(custom.id))
        // The filters became the view: Toutes has none left, and the new view has no filter of its own.
        inPage(custom.id, RevendicationTags.CHIP + "theme:transports").assertDoesNotExist()
        openView("tout")
        assertCount("tout", "8 revendications")

        // A new screen on the same database finds the view again.
        runOnIdle { generation++ }
        waitForIdle()
        openSpace(Space.REVENDICATIONS)
        waitForDemands()
        assertEquals(listOf("tout", custom.id, "suivis", "theme:societe", "theme:sante"), tabIds())
        openView(custom.id)
        assertEquals(setOf("lyon-transports-nuit"), slugsIn(custom.id))
    }

    @Test
    fun personalizingRenamesRemovesReordersAddsAndResets() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        val store = RevendicationViewStore(database)
        openWall(persistent(database))

        // Create a custom view from "Ajouter une vue".
        openCustomize("tout")
        onNodeWithTag(ManageViewsTags.RESET).assertIsNotEnabled() // nothing to reset yet
        onNodeWithTag(ManageViewsTags.ADD).performScrollTo().performClick()
        onNodeWithTag(ManageViewsTags.CREATE).performScrollTo().performClick()
        closeModal()
        onNodeWithTag(FilterPanelTags.NAME_FIELD).performTextInput("Santé publique")
        onNodeWithTag(FilterPanelTags.THEME + "sante").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.THEME + "services-publics").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.NAME_CONFIRM).performClick()
        closeModal()
        waitUntil(timeoutMillis = 5_000) { store.layout()?.any { it is CustomRevendicationView } == true }
        val custom = store.layout()!!.filterIsInstance<CustomRevendicationView>().single()
        assertEquals(listOf("tout", "suivis", "theme:societe", "theme:sante", custom.id), tabIds())
        onNodeWithTag(RevendicationTags.TAB + custom.id).assertIsSelected()
        assertEquals(setOf("medecin-de-garde", "ateliers-numeriques-mairies"), slugsIn(custom.id))

        // Rename it.
        openCustomize(custom.id)
        onNodeWithTag(ManageViewsTags.RENAME + custom.id).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(FilterPanelTags.NAME_FIELD).performTextReplacement("Médecins")
        onNodeWithTag(FilterPanelTags.NAME_CONFIRM).performClick()
        closeModal()
        onNodeWithTag(RevendicationTags.TAB + custom.id).assertTextEquals("Médecins")

        // Move Santé before Société, then the custom view up once.
        iconButton(ManageViewsTags.MOVE_UP + "suivis").assertIsNotEnabled() // Toutes stays first
        iconButton(ManageViewsTags.MOVE_DOWN + custom.id).assertIsNotEnabled()
        iconButton(ManageViewsTags.MOVE_DOWN + "suivis").assertIsEnabled()
        onNodeWithTag(ManageViewsTags.MOVE_UP + "theme:sante").performScrollTo().performClick()
        onNodeWithTag(ManageViewsTags.MOVE_UP + custom.id).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("tout", "suivis", "theme:sante", custom.id, "theme:societe"), tabIds())

        // Remove a built-in view at once, and a custom view after its warning (the safe choice first).
        onNodeWithTag(ManageViewsTags.REMOVE + "theme:societe").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ManageViewsTags.REMOVE + custom.id).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ManageViewsTags.KEEP).performClick()
        closeModal()
        assertEquals(listOf("tout", "suivis", "theme:sante", custom.id), tabIds())
        onNodeWithTag(ManageViewsTags.REMOVE + custom.id).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ManageViewsTags.CONFIRM).performClick()
        closeModal()
        assertEquals(listOf("tout", "suivis", "theme:sante"), tabIds())

        // Add a built-in view from the catalog.
        onNodeWithTag(ManageViewsTags.ADD).performScrollTo().performClick()
        onNodeWithTag(ManageViewsTags.CATALOG + "nationales").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ManageViewsTags.CATALOG + "nationales").assertDoesNotExist()
        assertEquals(listOf("tout", "suivis", "theme:sante", "nationales"), tabIds())
        waitUntil(timeoutMillis = 5_000) { store.layout()?.map { it.id } == listOf("tout", "suivis", "theme:sante", "nationales") }

        // Reset asks first; keeping changes nothing, confirming brings the defaults back.
        onNodeWithTag(ManageViewsTags.RESET).performClick()
        waitForIdle()
        onNodeWithTag(ManageViewsTags.KEEP).performClick()
        closeModal()
        assertEquals(listOf("tout", "suivis", "theme:sante", "nationales"), tabIds())
        onNodeWithTag(ManageViewsTags.RESET).performClick()
        waitForIdle()
        onNodeWithTag(ManageViewsTags.CONFIRM).performClick()
        closeModal()
        assertEquals(listOf("tout", "suivis", "theme:societe", "theme:sante"), tabIds())
        waitUntil(timeoutMillis = 5_000) { store.layout() == null }
        onNodeWithTag(ManageViewsTags.DONE).performClick()
        closeModal()
        onNodeWithTag(ManageViewsTags.DIALOG).assertDoesNotExist()

        // Nationales: the national demands only.
        openView("tout")
        openCustomize("tout")
        onNodeWithTag(ManageViewsTags.ADD).performScrollTo().performClick()
        onNodeWithTag(ManageViewsTags.CATALOG + "nationales").performScrollTo().performClick()
        onNodeWithTag(ManageViewsTags.DONE).performClick()
        closeModal()
        openView("nationales")
        assertEquals(setOf("transparence-agenda-elus", "cantines-tarif-social", "medecin-de-garde", "ateliers-numeriques-mairies", "creches-horaires-decales"), slugsIn("nationales"))
    }

    @Test
    fun theFollowedViewShowsTheDemandsOfTheAccountsFollowedFromTheFeed() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        val dependencies = persistent(database)
        openWall(dependencies)
        openView("suivis")
        inPage("suivis", RevendicationTags.EMPTY).assertExists()
        assertTrue(onAllNodesWithText("Vous ne suivez encore aucun compte", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertCount("suivis", "0 revendication")

        // Follow the example editorial account from a card of the feed, then come back.
        onNodeWithTag(ShellTags.navItem(Space.FIL)).performClick()
        waitForIdle()
        val store = dependencies.contentStore!!
        waitUntil(timeoutMillis = 10_000) { store.recent(kinds = listOf(1)).size == 5 }
        val suffix = store.recent(kinds = listOf(1)).first().id.take(8)
        waitUntil(timeoutMillis = 10_000) { has(FeedTags.FOLLOW + suffix) }
        onAllNodesWithTag(FeedTags.FOLLOW + suffix).onFirst().performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { dependencies.followStore!!.all() == listOf(EditorialTrust.EXAMPLE_EDITOR) }

        openSpace(Space.REVENDICATIONS)
        waitForDemands()
        // The view shown before leaving is shown again.
        onNodeWithTag(RevendicationTags.TAB + "suivis").assertIsSelected()
        assertCount("suivis", "8 revendications")
        assertEquals(slugs.toSet(), slugsIn("suivis"))
    }

    @Test
    fun myDemandsAreAViewOnceThereIsAnAccount() = runComposeUiTest {
        val phrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")
        val identity = InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase))
        openWall(AppDependencies(identity, contentStore = LocalEventStore(JvmDatabase.inMemory())))
        waitUntil(timeoutMillis = 5_000) { has(RevendicationTags.TAB + "mes-revendications") }
        assertEquals(listOf("tout", "suivis", "theme:societe", "theme:sante", "mes-revendications"), tabIds())
        openView("mes-revendications")
        assertCount("mes-revendications", "0 revendication")
        assertTrue(onAllNodesWithText("Vous n’avez pas encore proposé de revendication.").fetchSemanticsNodes().isNotEmpty())
        // The shell wires the proposal form: the button is offered in the view.
        inPage("mes-revendications", RevendicationTags.PROPOSE).assertExists()
        // The authors filter offers "Mes revendications" too.
        openView("tout")
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.AUTHORS + "mine").performScrollTo().assertExists()
    }

    @Test
    fun withoutAccountMyDemandsAreNeitherATabNorAChoice() = runComposeUiTest {
        openWall(memory())
        onNodeWithTag(RevendicationTags.TAB + "mes-revendications").assertDoesNotExist()
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.AUTHORS + "followed").performScrollTo().assertExists()
        onNodeWithTag(FilterPanelTags.AUTHORS + "mine").assertDoesNotExist()
        onNodeWithTag(FilterPanelTags.CLOSE).performClick()
        closeModal()
        openCustomize("tout")
        onNodeWithTag(ManageViewsTags.ADD).performScrollTo().performClick()
        onNodeWithTag(ManageViewsTags.CATALOG + "nationales").performScrollTo().assertExists()
        onNodeWithTag(ManageViewsTags.CATALOG + "mes-revendications").assertDoesNotExist()
    }

    @Test
    fun onPhonesFiltersOpenInASheetAndViewsAreSwiped() = runComposeUiTest {
        openWall(memory(), width = 360, height = 700)
        assertEquals("tout", tabIds().first())
        openFilters("tout")
        onNodeWithTag(FilterPanelTags.THEME + "sante").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.APPLY).performClick()
        closeModal()
        onNodeWithTag(FilterPanelTags.PANEL).assertDoesNotExist()
        assertCount("tout", "1 revendication")
        inPage("tout", RevendicationTags.CHIP + "theme:sante").assertExists()

        // A swipe to the left shows the next view.
        onNodeWithTag(RevendicationTags.PAGE + "tout").performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag(RevendicationTags.TAB + "suivis").assertIsSelected()

        // The detail of a demand opens alone, with a back button.
        openView("tout")
        inPage("tout", RevendicationTags.CARD + "medecin-de-garde").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.DETAIL).assertExists()
        onNodeWithTag(RevendicationTags.LIST).assertDoesNotExist()
        onNodeWithTag(RevendicationTags.BACK).performClick()
        waitForIdle()
        // Back on the list, the view and its filter are still there.
        onNodeWithTag(RevendicationTags.TAB + "tout").assertIsSelected()
        assertCount("tout", "1 revendication")
    }

    @Test
    fun tabletShowsOnePaneWithTheTitleAndTheViews() = runComposeUiTest {
        openWall(memory(), width = 800, height = 740)
        assertEquals(listOf("tout", "suivis", "theme:societe", "theme:sante"), tabIds())
        openView("theme:sante")
        inPage("theme:sante", RevendicationTags.CARD + "medecin-de-garde").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.DETAIL).assertExists()
        onNodeWithTag(RevendicationTags.BACK).assertExists()
    }

    @Test
    fun theProposeButtonAppearsOnlyWhenTheScreenOffersIt() = runComposeUiTest {
        var proposed = 0
        var offer by mutableStateOf(false)
        val store = LocalEventStore(JvmDatabase.inMemory())
        setContent {
            val scope = rememberCoroutineScope()
            val library = remember { ContentLibrary(store, EditorialTrust.bundledExamples(), null, scope).also { it.start() } }
            val session = remember { AccountSession(InMemoryIdentityStore(), scope).also { it.start() } }
            PpfTheme(darkTheme = false) {
                Box(Modifier.requiredSize(1000.dp, 740.dp)) {
                    RevendicationsScreen(library, session, WindowClass.of(widthDp = 1000f, heightDp = 740f), onPropose = if (offer) ({ proposed++ }) else null)
                }
            }
        }
        waitForDemands()
        onNodeWithTag(RevendicationTags.PROPOSE).assertDoesNotExist()
        runOnIdle { offer = true }
        waitForIdle()
        inPage("tout", RevendicationTags.PROPOSE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, proposed)
    }
}
