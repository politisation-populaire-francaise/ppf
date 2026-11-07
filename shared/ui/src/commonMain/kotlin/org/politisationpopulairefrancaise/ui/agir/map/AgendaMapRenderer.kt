package org.politisationpopulairefrancaise.ui.agir.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import org.politisationpopulairefrancaise.core.agir.map.GeoBox
import org.politisationpopulairefrancaise.core.agir.map.GeoPoint
import org.politisationpopulairefrancaise.core.agir.map.MapResourceRouter

/** Where the camera looks: a centre and a zoom (MapLibre zoom levels, 512-pixel tiles). */
@Immutable
data class MapCamera(val center: GeoPoint, val zoom: Double)

/** What the screen asks of the map, one at a time, in order. */
sealed interface MapCommand {
    data class Show(val camera: MapCamera) : MapCommand

    data class Fit(val box: GeoBox, val maxZoom: Double) : MapCommand

    data class ZoomBy(val delta: Double) : MapCommand
}

/** The colours of the points, from the palette of PPF (D-025), for the light or the dark mode. */
@Immutable
data class MapPointColors(
    val planned: Color,
    val ongoing: Color,
    val postponed: Color,
    val cancelled: Color,
    val stroke: Color,
    val selectedStroke: Color,
    val label: Color,
    val labelHalo: Color,
)

/**
 * Everything a renderer needs to draw the map of the agenda. The style and the points are built
 * on the device in common code ([org.politisationpopulairefrancaise.core.agir.map.MapStyle],
 * [org.politisationpopulairefrancaise.core.agir.map.AgendaMapPoints]); every resource the engine
 * asks for goes through [router], which refuses anything that is not ours.
 *
 * @param style the MapLibre style of the background (JSON)
 * @param points the GeoJSON of the points, with their address, label, state and selection
 * @param onPoints the addresses of the points the user touched (several at one place)
 * @param onBackground a touch on the map outside any point
 * @param onCamera the camera after each move, so that it comes back after a visit to the detail
 */
class MapViewRequest(
    val style: String,
    val points: String,
    val pointCount: Int,
    val colors: MapPointColors,
    val minZoom: Double,
    val maxZoom: Double,
    val camera: MapCamera,
    val commands: Flow<MapCommand>,
    val router: MapResourceRouter,
    val description: String,
    val onPoints: (List<String>) -> Unit,
    val onBackground: () -> Unit,
    val onCamera: (MapCamera) -> Unit,
)

/**
 * Draws the map of the agenda. One implementation per platform that can ([platformAgendaMapRenderer]):
 * MapLibre on Android today; none on the computer (MapLibre Compose needs Java 25 there) nor on
 * iOS (not compiled yet), where the agenda keeps its list. Tests give a fake one through
 * [LocalAgendaMapRenderer]: the native map does not draw in JVM tests.
 */
interface AgendaMapRenderer {
    @Composable
    fun MapView(request: MapViewRequest, modifier: Modifier)
}

/** The renderer of this platform, or null where the map cannot be drawn yet. */
expect fun platformAgendaMapRenderer(): AgendaMapRenderer?

/** The renderer used by the agenda; tests provide a fake one. */
val LocalAgendaMapRenderer = staticCompositionLocalOf<AgendaMapRenderer?> { platformAgendaMapRenderer() }
