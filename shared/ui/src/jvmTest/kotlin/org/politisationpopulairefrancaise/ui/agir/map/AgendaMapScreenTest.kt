package org.politisationpopulairefrancaise.ui.agir.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.material3.Button
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaQuery
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.map.MapArea
import org.politisationpopulairefrancaise.core.agir.map.MapResource
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaClock
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaKit
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaServer
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaTags
import org.politisationpopulairefrancaise.ui.agir.agenda.cardCount
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A map that draws nothing: it shows its points as buttons (as a touch on them would) and records
 * what the screen asked of it. The native map does not draw in JVM tests.
 */
internal class FakeMapRenderer : AgendaMapRenderer {
    var last: MapViewRequest? = null
    val commands = mutableListOf<MapCommand>()

    @Composable
    override fun MapView(request: MapViewRequest, modifier: Modifier) {
        last = request
        LaunchedEffect(request.commands) { request.commands.collect { commands += it } }
        val features = Json.parseToJsonElement(request.points).jsonObject["features"]!!.jsonArray.map { it.jsonObject["properties"]!!.jsonObject }
        Column(modifier) {
            features.forEach { p ->
                val address = p["address"]!!.jsonPrimitive.content
                Button(onClick = { request.onPoints(listOf(address)) }, modifier = Modifier.testTag("fake-point-" + address.substringAfterLast(':'))) {
                    Text(p["label"]!!.jsonPrimitive.content)
                }
            }
            Button(onClick = request.onBackground, modifier = Modifier.testTag("fake-background")) { Text("fond") }
        }
    }

    /** The labels of the points, by identifier of mobilization. */
    fun labels(): Map<String, String> = Json.parseToJsonElement(assertNotNull(last).points).jsonObject["features"]!!.jsonArray.associate {
        val p = it.jsonObject["properties"]!!.jsonObject
        p["address"]!!.jsonPrimitive.content.substringAfterLast(':') to p["label"]!!.jsonPrimitive.content
    }
}

/**
 * A device of the map tests: like the agenda's, but on a database file, so that every thread of
 * the library sees the same data (an in-memory SQLite database is per connection, hence per thread).
 */
internal class MapDevice(val server: AgendaServer? = null) {
    val database = org.politisationpopulairefrancaise.core.local.JvmDatabase.open(
        java.nio.file.Files.createTempDirectory("ppf-map-test").resolve("ppf.db"),
    )
    val store = org.politisationpopulairefrancaise.core.local.LocalEventStore(database)
    val state = org.politisationpopulairefrancaise.core.agir.agenda.AgendaStore(database)
    val identityStore = org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore()
    val dependencies = org.politisationpopulairefrancaise.ui.AppDependencies(identityStore = identityStore, contentStore = store, remote = server, database = database)
}

@Composable
internal fun MapTestAgenda(device: MapDevice, width: Int, height: Int, onReady: (AgendaLibrary) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val agenda = androidx.compose.runtime.remember {
        AgendaLibrary(device.store, device.state, org.politisationpopulairefrancaise.core.editorial.EditorialTrust.bundledExamples(), device.server, scope) { AgendaClock.NOW }.also { it.start() }
    }
    val session = androidx.compose.runtime.remember { org.politisationpopulairefrancaise.ui.account.AccountSession(device.identityStore, scope).also { it.start() } }
    androidx.compose.runtime.remember { onReady(agenda) }
    org.politisationpopulairefrancaise.ui.theme.PpfTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Box(Modifier.requiredSize(width.dp, height.dp)) {
            org.politisationpopulairefrancaise.ui.agir.agenda.AgendaScreen(
                dependencies = device.dependencies,
                session = session,
                windowClass = org.politisationpopulairefrancaise.core.WindowClass.of(widthDp = width.toFloat(), heightDp = height.toFloat()),
                agenda = agenda,
            )
        }
    }
}

/**
 * The map view of the agenda on the bundled examples (each placed in a town centre), on Friday 13
 * November 2026 at 10 h, with a fake renderer: the choice "Liste" / "Carte", the points and their
 * labels, the filters, the place touched and the detail, the controls, the attribution, the three
 * widths, and the honest sentence where no map can be drawn.
 */
@OptIn(ExperimentalTestApi::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class AgendaMapScreenTest {
    /**
     * Runs first (names in order): the first composition of a test run also loads Compose, Skiko
     * and the resources, which on a busy computer can take longer than a test's own timeout.
     */
    @Test
    fun a0WarmUp() = runComposeUiTest {
        setContent { MapTestAgenda(MapDevice(), 360, 740) {} }
        try {
            waitUntil(timeoutMillis = 50_000) { cardCount() >= 7 }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            // Only a warm-up: the real checks follow.
        }
    }

    private val paris = "rassemblement-services-publics-paris-2026-11-14"
    private val strike = "greve-transports-2026-11-26"
    private val cancelled = "rassemblement-maternite-seine-saint-denis-2026-12-05"
    private val rennes = "projection-debat-rennes-2026-11-03"

    private fun ComposeUiTest.showMap(renderer: AgendaMapRenderer?, width: Int, height: Int = 740, device: MapDevice = MapDevice(), onReady: (AgendaLibrary) -> Unit = {}) {
        setContent {
            CompositionLocalProvider(LocalAgendaMapRenderer provides renderer) {
                MapTestAgenda(device, width, height, onReady)
            }
        }
        // Generous: the first test of the run also pays for the start of Compose and of the database.
        waitUntil(timeoutMillis = 50_000) { cardCount() >= 7 }
    }

    private fun ComposeUiTest.chooseMap() {
        onNodeWithTag(AgendaMapTags.VIEW + "map").performClick()
        waitForIdle()
        onNodeWithTag(AgendaMapTags.PANE).assertExists()
    }

    @Test
    fun thePhoneOffersTheMapAndShowsEveryPlacedMobilization() = runComposeUiTest {
        val renderer = FakeMapRenderer()
        showMap(renderer, 360)
        // The list stays the default view; the choice is a group of two radio buttons.
        onNodeWithTag(AgendaMapTags.VIEW + "list").assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        onNodeWithTag(AgendaMapTags.VIEW_CHOICE).assert(hasContentDescription("Affichage de l’agenda"))
        onNodeWithTag(AgendaMapTags.UNAVAILABLE).assertDoesNotExist()
        chooseMap()
        onNodeWithTag(AgendaMapTags.VIEW + "map").assertIsSelected()
        onNodeWithTag(AgendaMapTags.COUNT).assertTextContains("7 mobilisations sur la carte")
        onNodeWithTag(AgendaMapTags.WITHOUT_POSITION).assertDoesNotExist()
        val request = assertNotNull(renderer.last)
        assertEquals(7, request.pointCount)
        // Without server, the bundled background only, and no address of another server.
        assertFalse(request.style.contains("http"), "no remote address in the style")
        assertEquals(MapArea.MAX_ZOOM_BUNDLED, request.maxZoom)
        assertEquals(AgendaMapState.FRANCE, request.camera)
        // Labels: the type and the day, the status in words.
        val labels = renderer.labels()
        assertEquals("Rassemblement\nDemain", labels[paris])
        assertTrue(labels.getValue(cancelled).startsWith("Annulé : Rassemblement"), labels.getValue(cancelled))
        assertTrue(rennes !in labels, "a past event is not on the map of the coming ones")
        // The attribution of OpenStreetMap is always visible and opens the sources of the map.
        onNodeWithTag(AgendaMapTags.ATTRIBUTION).assertTextContains("© contributeurs OpenStreetMap")
        onNodeWithTag(AgendaMapTags.ATTRIBUTION).performClick()
        waitForIdle()
        onNodeWithText("ODbL", substring = true).assertExists()
        onNodeWithText("Fermer").performClick()
        waitForIdle()
    }

    @Test
    fun aTouchedPointOpensTheDetailAndTheMapComesBack() = runComposeUiTest {
        val renderer = FakeMapRenderer()
        showMap(renderer, 360)
        chooseMap()
        onNodeWithTag("fake-point-$paris").performClick()
        waitForIdle()
        onNodeWithTag(AgendaMapTags.SELECTION).assertExists()
        onNodeWithTag(AgendaMapTags.SELECTED + paris).assertExists()
        onNodeWithText("Place de la République, Paris", substring = true).assertExists()
        onNodeWithText("1 mobilisation à cet endroit").assertExists()
        // The selected point is drawn as such.
        assertTrue(assertNotNull(renderer.last).points.contains("\"selected\":true"))
        onNodeWithTag(AgendaMapTags.OPEN + paris).performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.DETAIL).assertExists()
        onNodeWithTag(AgendaTags.BACK).performScrollTo().performClick()
        waitForIdle()
        // Back on the map, as it was.
        onNodeWithTag(AgendaMapTags.PANE).assertExists()
        onNodeWithTag(AgendaMapTags.SELECTION).assertExists()
        // A touch on the map outside any point closes it (the fake map is under the box: call it directly).
        runOnIdle { assertNotNull(renderer.last).onBackground() }
        waitForIdle()
        onNodeWithTag(AgendaMapTags.SELECTION).assertDoesNotExist()
        // The list is one touch away.
        onNodeWithTag(AgendaMapTags.VIEW + "list").performClick()
        waitForIdle()
        onNodeWithTag(AgendaMapTags.PANE).assertDoesNotExist()
        assertTrue(cardCount() >= 7)
    }

    @Test
    fun theFiltersOfTheAgendaApplyToTheMap() = runComposeUiTest {
        val renderer = FakeMapRenderer()
        var library: AgendaLibrary? = null
        showMap(renderer, 360) { library = it }
        chooseMap()
        assertNotNull(library).filters = AgendaQuery(types = setOf(MobilizationType.GREVE))
        waitForIdle()
        onNodeWithTag(AgendaMapTags.COUNT).assertTextContains("1 mobilisation sur la carte")
        assertEquals(setOf(strike), renderer.labels().keys)
        onNodeWithTag(AgendaMapTags.FILTERS).assert(hasContentDescription("1 filtre actif", substring = true))
        // A filter with no result: the map says so and offers the list.
        library!!.filters = AgendaQuery(types = setOf(MobilizationType.GREVE), text = "introuvable")
        waitForIdle()
        onNodeWithTag(AgendaMapTags.EMPTY).assertExists()
    }

    @Test
    fun theButtonsMoveTheMapWithoutAnyGesture() = runComposeUiTest {
        val renderer = FakeMapRenderer()
        showMap(renderer, 360)
        chooseMap()
        onNodeWithContentDescription("Zoomer").performClick()
        onNodeWithContentDescription("Dézoomer").performClick()
        onNodeWithContentDescription("Recentrer sur la France").performClick()
        waitForIdle()
        assertEquals(listOf(MapCommand.ZoomBy(1.0), MapCommand.ZoomBy(-1.0), MapCommand.Show(AgendaMapState.FRANCE)), renderer.commands)
    }

    @Test
    fun aMobilizationWithoutPositionIsCountedAndStaysInTheList() = runComposeUiTest {
        val server = AgendaServer()
        val kit = AgendaKit()
        server.verified += kit.pubkey
        server.seed(kit.mobilization("sans-lieu", "[EXEMPLE] Réunion sans lieu sur la carte", start = AgendaClock.at(2026, 11, 20, 18)))
        val renderer = FakeMapRenderer()
        showMap(renderer, 360, device = MapDevice(server))
        waitUntil(timeoutMillis = 20_000) { cardCount() >= 8 }
        chooseMap()
        onNodeWithTag(AgendaMapTags.COUNT).assertTextContains("7 mobilisations sur la carte")
        onNodeWithTag(AgendaMapTags.WITHOUT_POSITION).assertTextContains("1 sans lieu placé sur la carte : elle est dans la liste.")
        onNodeWithTag(AgendaMapTags.SHOW_LIST).performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.CARD + "sans-lieu").performScrollTo().assertExists()
    }

    private fun ComposeUiTest.detailFromTheMap(width: Int) {
        val renderer = FakeMapRenderer()
        showMap(renderer, width)
        chooseMap()
        onNodeWithTag("fake-point-$strike").performClick()
        waitForIdle()
        onNodeWithTag(AgendaMapTags.OPEN + strike).performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.DETAIL).assertExists()
        if (width >= 1000) {
            // Two panes: the map stays on the left, the detail opens on the right.
            onNodeWithTag(AgendaMapTags.PANE).assertExists()
            onNodeWithTag(AgendaTags.BACK).assertDoesNotExist()
        } else {
            onNodeWithTag(AgendaMapTags.PANE).assertDoesNotExist()
            onNodeWithTag(AgendaTags.BACK).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(AgendaMapTags.PANE).assertExists()
        }
    }

    @Test
    fun phoneWidthTheDetailReplacesTheMapAndGivesItBack() = runComposeUiTest { detailFromTheMap(360) }

    @Test
    fun tabletWidthTheDetailReplacesTheMapAndGivesItBack() = runComposeUiTest { detailFromTheMap(700) }

    @Test
    fun wideScreenTheDetailOpensNextToTheMap() = runComposeUiTest { detailFromTheMap(1000) }

    @Test
    fun withoutRendererTheListStaysWithAnHonestSentence() = runComposeUiTest {
        showMap(renderer = null, width = 1000)
        onNodeWithTag(AgendaMapTags.VIEW_CHOICE).assertDoesNotExist()
        onNodeWithTag(AgendaMapTags.UNAVAILABLE).assertExists()
        onNodeWithText("Sur cet appareil, la carte n’est pas encore disponible", substring = true).assertExists()
    }

    @Test
    fun theBundledDataOfTheMapIsInTheApplication() = runBlocking {
        // The background and the glyphs are Compose resources of the application: no network at all.
        val router = BundledMap.router()
        val tile = router.load("ppf-carte://fond/0/0/0")
        assertIs<MapResource.Bytes>(tile)
        assertTrue(tile.bytes.isNotEmpty())
        // Paris at zoom 7 (64/44) is in the bundled background of France.
        assertIs<MapResource.Bytes>(router.load("ppf-carte://fond/${MapArea.BUNDLED_MAX_ZOOM}/64/44"))
        val glyphs = router.load("ppf-carte://glyphes/Atkinson%20Hyperlegible%20Bold/0-255.pbf")
        assertIs<MapResource.Bytes>(glyphs)
        assertTrue(glyphs.bytes.size > 10_000, "the glyphs of the Latin letters are bundled")
        assertEquals(MapResource.Refused, router.load("https://tuiles.example.org/0/0/0.png"))
    }
}
