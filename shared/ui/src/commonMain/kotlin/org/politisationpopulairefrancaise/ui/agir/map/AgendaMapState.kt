package org.politisationpopulairefrancaise.ui.agir.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.politisationpopulairefrancaise.core.agir.map.GeoBox
import org.politisationpopulairefrancaise.core.agir.map.GeoPoint
import org.politisationpopulairefrancaise.core.agir.map.MapArea
import org.politisationpopulairefrancaise.core.agir.map.MapColors
import org.politisationpopulairefrancaise.ui.theme.PpfPalette

/** The two ways of showing the agenda: the list (the main path, always there) and the map. */
enum class AgendaView { LIST, MAP }

/**
 * The state of the map of the agenda, kept by the screen while the detail of a mobilization is
 * open, so that the map comes back as it was: the chosen view, the camera, the place touched.
 * In memory only: nothing of it is saved or sent (the camera says where the user looks).
 *
 * @param tilesUrl the tiles folder of our server (`https://<domaine>/tiles/`, read through its
 *   manifest `current.json`); null (today) keeps
 *   the bundled background only
 */
@Stable
class AgendaMapState(val tilesUrl: String? = null) {
    var view: AgendaView by mutableStateOf(AgendaView.LIST)

    /** The last camera of the map (not observed: the renderer moves it itself). */
    var camera: MapCamera = FRANCE
        internal set

    /** The addresses of the mobilizations at the place the user touched; empty when none is chosen. */
    var selected: List<String> by mutableStateOf(emptyList())

    private val pending = MutableSharedFlow<MapCommand>(extraBufferCapacity = 16)

    /** The moves asked by the buttons; a renderer collects them while the map is shown. */
    val commands: SharedFlow<MapCommand> get() = pending

    fun recenter() {
        pending.tryEmit(MapCommand.Show(FRANCE))
    }

    fun zoomIn() {
        pending.tryEmit(MapCommand.ZoomBy(1.0))
    }

    fun zoomOut() {
        pending.tryEmit(MapCommand.ZoomBy(-1.0))
    }

    fun show(point: GeoPoint) {
        pending.tryEmit(MapCommand.Show(MapCamera(point, MapArea.POINT_ZOOM)))
    }

    fun fit(box: GeoBox) {
        pending.tryEmit(MapCommand.Fit(box, MapArea.POINT_ZOOM))
    }

    companion object {
        val FRANCE: MapCamera = MapCamera(MapArea.FRANCE_CENTER, MapArea.FRANCE_ZOOM)
    }
}

@Composable
fun rememberAgendaMapState(tilesUrl: String? = null): AgendaMapState = remember(tilesUrl) { AgendaMapState(tilesUrl) }

/** The colours of the background from the palette: soft blue sea, page-coloured land, discreet roads and borders. */
internal fun PpfPalette.mapColors(dark: Boolean): MapColors {
    val land = if (dark) surface1 else surface0
    val water = if (dark) lerp(surface0, brand, 0.14f) else lerp(surface1, brand, 0.16f)
    return MapColors(
        water = water.hex(),
        land = land.hex(),
        road = lerp(border, borderStrong, if (dark) 0.35f else 0.25f).hex(),
        boundary = borderStrong.hex(),
        regionBoundary = (if (dark) lerp(border, borderStrong, 0.6f) else border).hex(),
        label = ink.hex(),
        labelMinor = inkMuted.hex(),
        labelHalo = land.hex(),
    )
}

/** The colours of the points: the state changes the colour and the label, never the colour alone. */
internal fun PpfPalette.pointColors(dark: Boolean): MapPointColors = MapPointColors(
    planned = brand,
    ongoing = accent,
    postponed = warning,
    cancelled = inkSubtle,
    stroke = surface0,
    selectedStroke = focus,
    label = ink,
    labelHalo = if (dark) surface1 else surface0,
)

/** `#rrggbb`, the form of the colours of a MapLibre style. */
internal fun Color.hex(): String {
    val argb = toArgb()
    return "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
}
