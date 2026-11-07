package org.politisationpopulairefrancaise.ui.agir.map

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.asBoolean
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.condition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.CameraConstraints
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.resource.MapRequestInterceptor
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import org.politisationpopulairefrancaise.core.agir.map.AgendaMapPoints
import org.politisationpopulairefrancaise.core.agir.map.GeoPoint
import org.politisationpopulairefrancaise.core.agir.map.MapFonts
import org.politisationpopulairefrancaise.core.agir.map.MapPointState
import org.politisationpopulairefrancaise.core.agir.map.MapResource
import org.politisationpopulairefrancaise.core.agir.map.MapResourceRouter
import org.politisationpopulairefrancaise.core.agir.map.MapUrls
import java.util.concurrent.atomic.AtomicBoolean

/** Android: MapLibre, through MapLibre Compose (BSD-3-Clause) and MapLibre Native (BSD-2-Clause). */
actual fun platformAgendaMapRenderer(): AgendaMapRenderer? = MaplibreAgendaMapRenderer

/**
 * Asks the detailed tiles of our server only with a network and outside the data saving mode of
 * the system ("Économiseur de données", Android 7 and later).
 */
@Composable
internal actual fun rememberServerTilesAllowed(): () -> Boolean {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            manager != null && manager.activeNetwork != null && !(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    manager.isActiveNetworkMetered &&
                    manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                )
        }
    }
}

/**
 * The map engine of the whole application, configured once, at the first display of the map
 * (never before): every URL the engine wants is rewritten to our private scheme unless it already
 * is one, then answered by the [MapResourceRouter] of the screen. The engine therefore never
 * reaches any server by itself; the only network request it can cause is the one our router makes
 * to our own server, for the detailed background.
 */
private object MaplibreSetup {
    private val configured = AtomicBoolean(false)

    @Volatile
    var router: MapResourceRouter? = null

    /** Ambient cache of the engine (the answers of the router): small, the bundled background needs none. */
    private const val CACHE_BYTES: Long = 30L * 1024 * 1024

    fun ensure(router: MapResourceRouter) {
        this.router = router
        if (!configured.compareAndSet(false, true)) return
        DefaultMapRuntime.configure {
            maximumCacheSizeBytes = CACHE_BYTES
            requestInterceptor = MapRequestInterceptor(rewriteUrl = { request -> if (MapUrls.isOwn(request.url)) null else MapUrls.REFUSED })
            resourceProvider = MapResourceProvider(
                accepts = { request -> MapUrls.isOwn(request.url) },
                load = { request -> load(request.url) },
            )
        }
    }

    private suspend fun load(url: String): MapResourceLoad {
        val router = router ?: return MapResourceLoad.Failed(MapResourceError.NotFound, "no map")
        return try {
            when (val answer = router.load(url)) {
                is MapResource.Bytes -> MapResourceLoad.Bytes(answer.bytes)
                MapResource.Empty -> MapResourceLoad.NoContent()
                MapResource.Refused -> MapResourceLoad.Failed(MapResourceError.NotFound, "not served by PPF")
                MapResource.Unavailable -> MapResourceLoad.Failed(MapResourceError.Connection, "unavailable")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MapResourceLoad.Failed(MapResourceError.Other, e::class.simpleName ?: "error")
        }
    }
}

private object MaplibreAgendaMapRenderer : AgendaMapRenderer {
    @Composable
    override fun MapView(request: MapViewRequest, modifier: Modifier) {
        remember(request.router) { MaplibreSetup.ensure(request.router) }
        val current = rememberUpdatedState(request)
        val colors = request.colors
        val mapState = rememberMapState(
            baseStyle = BaseStyle.Json(request.style),
            initialCameraPosition = CameraPosition(
                center = Position(longitude = request.camera.center.longitude, latitude = request.camera.center.latitude),
                zoom = request.camera.zoom,
            ),
        ) {
            // A new source each time the points change (filters, selection): sources are immutable.
            key(current.value.points) {
                val points = rememberGeoJsonSource(GeoJsonData.JsonString(current.value.points))
                val onClick: org.maplibre.compose.layers.FeaturesClickHandler = { features ->
                    val addresses = features.mapNotNull { it.properties?.get(AgendaMapPoints.PROPERTY_ADDRESS)?.jsonPrimitive?.content }
                    if (addresses.isEmpty()) ClickResult.Pass else ClickResult.Consume.also { current.value.onPoints(addresses) }
                }
                val state = feature[AgendaMapPoints.PROPERTY_STATE]
                val selected = feature[AgendaMapPoints.PROPERTY_SELECTED].asBoolean(const(false))
                CircleLayer(
                    id = "ppf-mobilisations",
                    source = points,
                    sortKey = feature[AgendaMapPoints.PROPERTY_ORDER].asNumber(),
                    color = switch(
                        state,
                        case(MapPointState.CANCELLED.tagValue, const(colors.cancelled)),
                        case(MapPointState.POSTPONED.tagValue, const(colors.postponed)),
                        case(MapPointState.ONGOING.tagValue, const(colors.ongoing)),
                        fallback = const(colors.planned),
                    ),
                    radius = switch(condition(selected, const(12.dp)), fallback = const(9.dp)),
                    strokeColor = switch(condition(selected, const(colors.selectedStroke)), fallback = const(colors.stroke)),
                    strokeWidth = switch(condition(selected, const(3.dp)), fallback = const(2.dp)),
                    onClick = onClick,
                    hitPadding = 14.dp,
                )
                SymbolLayer(
                    id = "ppf-mobilisations-noms",
                    source = points,
                    sortKey = feature[AgendaMapPoints.PROPERTY_ORDER].asNumber(),
                    textField = feature[AgendaMapPoints.PROPERTY_LABEL].asString().cast(),
                    textFont = const(listOf(MapFonts.BOLD)),
                    textSize = const(1.0.em),
                    textColor = const(colors.label),
                    textHaloColor = const(colors.labelHalo),
                    textHaloWidth = const(2.dp),
                    textAnchor = const(SymbolAnchor.Top),
                    textOffset = org.maplibre.compose.expressions.dsl.textOffset(0.em, 1.1.em),
                    textMaxWidth = const(9.em),
                    textOptional = const(true),
                    onClick = onClick,
                )
            }
        }
        // The camera is kept by the screen, so that the map comes back as it was after the detail.
        LaunchedEffect(mapState) {
            snapshotFlow { mapState.cameraPosition }.debounce(250).collect { position ->
                current.value.onCamera(MapCamera(GeoPoint(position.center.latitude.coerceIn(-90.0, 90.0), wrap(position.center.longitude)), position.zoom))
            }
        }
        LaunchedEffect(mapState) {
            current.value.commands.collect { command ->
                val r = current.value
                try {
                    when (command) {
                        is MapCommand.Show -> mapState.animateCamera(
                            CameraUpdate(center = Position(longitude = command.camera.center.longitude, latitude = command.camera.center.latitude), zoom = command.camera.zoom),
                        )
                        is MapCommand.ZoomBy -> mapState.animateCamera(
                            CameraUpdate(zoom = (mapState.cameraPosition.zoom + command.delta).coerceIn(r.minZoom, r.maxZoom)),
                        )
                        is MapCommand.Fit -> mapState.animateCameraToBounds(
                            BoundingBox(west = command.box.west, south = command.box.south, east = command.box.east, north = command.box.north),
                            CameraFit(fitPadding = DpPadding(48.dp, 48.dp, 48.dp, 48.dp)),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A move interrupted by the user's own gesture: nothing to do.
                }
            }
        }
        MaplibreMap(
            modifier = modifier.fillMaxSize().semantics { contentDescription = request.description },
            state = mapState,
            cameraConstraints = CameraConstraints {
                minZoom = request.minZoom
                maxZoom = request.maxZoom
                maxPitch = 0.0
            },
            interactions = MapInteractions {
                camera {
                    rotate { enabled = false }
                    pitch { enabled = false }
                }
                callbacks {
                    click {
                        onUnhandled {
                            current.value.onBackground()
                            ClickResult.Consume
                        }
                    }
                }
            },
            // Our own controls and attribution are drawn by the screen (common code): no default overlay.
            overlay = {},
        )
    }

    private fun wrap(longitude: Double): Double {
        var l = longitude
        while (l > 180.0) l -= 360.0
        while (l < -180.0) l += 360.0
        return l
    }
}
