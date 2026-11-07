package org.politisationpopulairefrancaise.core.agir.map

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The colours of the background, as `#RRGGBB`, computed by the interface from the palette of PPF
 * (D-025) for the light or the dark mode: no colour is written here.
 */
data class MapColors(
    val water: String,
    val land: String,
    val road: String,
    val boundary: String,
    val regionBoundary: String,
    val label: String,
    val labelMinor: String,
    val labelHalo: String,
)

/** The URLs of the map: every one of them is answered by the application itself ([MapResourceRouter]). */
object MapUrls {
    /** A private scheme: no request of the map engine can leave the device without going through PPF. */
    const val SCHEME: String = "ppf-carte://"

    /** The bundled background, tile by tile. */
    const val BUNDLED_TILES: String = "${SCHEME}fond/{z}/{x}/{y}"

    /** The detailed background, read by the application from our server only. */
    const val SERVER_TILES: String = "${SCHEME}serveur/{z}/{x}/{y}"

    /** The glyphs of the labels, bundled (Atkinson Hyperlegible, SIL Open Font License). */
    const val GLYPHS: String = "${SCHEME}glyphes/{fontstack}/{range}.pbf"

    /** Where every other URL is sent: answered "not found", never fetched. */
    const val REFUSED: String = "${SCHEME}refuse"

    fun isOwn(url: String): Boolean = url.startsWith(SCHEME)
}

/** The fonts of the labels: the typeface of the application (D-025), regular and bold. */
object MapFonts {
    const val REGULAR: String = "Atkinson Hyperlegible Regular"
    const val BOLD: String = "Atkinson Hyperlegible Bold"

    /** The folder of the bundled glyphs of each font. */
    val FOLDERS: Map<String, String> = mapOf(REGULAR to "regular", BOLD to "bold")
}

/**
 * The MapLibre style of the background (style specification version 8), built on the device: a
 * sober map in the colours of PPF, readable in the light and the dark modes, with the layers of
 * the OpenStreetMap vector tiles of the Protomaps schema, version 4 (`earth`, `water`, `roads`,
 * `boundaries`, `places`). It names no server: tiles and glyphs use [MapUrls] only.
 */
object MapStyle {
    const val SOURCE_BUNDLED: String = "fond"
    const val SOURCE_SERVER: String = "detail"

    /** Shown by the interface on the map, and stored in the style for the record (ODbL). */
    const val ATTRIBUTION: String = "© contributeurs OpenStreetMap"

    /**
     * The style. [withServer] adds the detailed background of our server from
     * [MapArea.SERVER_MIN_ZOOM]: the bundled background stays underneath, so that a tile the
     * server cannot give still shows land and sea.
     */
    fun json(colors: MapColors, withServer: Boolean): String = build(colors, withServer).toString()

    fun build(colors: MapColors, withServer: Boolean): JsonObject = buildJsonObject {
        put("version", 8)
        put("name", "PPF")
        put("glyphs", MapUrls.GLYPHS)
        putJsonObject("sources") {
            put(SOURCE_BUNDLED, source(MapUrls.BUNDLED_TILES, 0, MapArea.BUNDLED_MAX_ZOOM))
            if (withServer) put(SOURCE_SERVER, source(MapUrls.SERVER_TILES, MapArea.SERVER_MIN_ZOOM, MapArea.SERVER_MAX_ZOOM))
        }
        putJsonArray("layers") {
            add(
                buildJsonObject {
                    put("id", "mer")
                    put("type", "background")
                    putJsonObject("paint") { put("background-color", colors.water) }
                },
            )
            val linesUntil = if (withServer) MapArea.SERVER_MIN_ZOOM else null
            geometry(SOURCE_BUNDLED, colors, minZoom = null, maxZoom = linesUntil).forEach(::add)
            if (withServer) geometry(SOURCE_SERVER, colors, minZoom = MapArea.SERVER_MIN_ZOOM, maxZoom = null).forEach(::add)
            labels(SOURCE_BUNDLED, colors, minZoom = null).forEach(::add)
            // Placed after: the labels of the detailed background win the collisions with the bundled ones.
            if (withServer) labels(SOURCE_SERVER, colors, minZoom = MapArea.SERVER_MIN_ZOOM).forEach(::add)
        }
    }

    private fun source(tiles: String, minZoom: Int, maxZoom: Int): JsonObject = buildJsonObject {
        put("type", "vector")
        putJsonArray("tiles") { add(JsonPrimitive(tiles)) }
        put("minzoom", minZoom)
        put("maxzoom", maxZoom)
        put("attribution", ATTRIBUTION)
    }

    /** Land, water (every zoom: the land and the sea never disappear), roads and borders. */
    private fun geometry(source: String, c: MapColors, minZoom: Int?, maxZoom: Int?): List<JsonObject> = listOf(
        layer("terre-$source", "fill", source, "earth", minZoom, null) {
            putJsonObject("paint") { put("fill-color", c.land) }
        },
        layer("eau-$source", "fill", source, "water", minZoom, null) {
            putJsonObject("paint") { put("fill-color", c.water) }
        },
        layer("routes-$source", "line", source, "roads", minZoom, maxZoom) {
            put("filter", expr("in", get("kind"), literal("highway", "major_road")))
            putJsonObject("layout") {
                put("line-join", "round")
                put("line-cap", "round")
            }
            putJsonObject("paint") {
                put("line-color", c.road)
                put("line-width", interpolateZoom(5 to 0.5, 9 to 1.2, 12 to 2.5, 16 to 6.0))
            }
        },
        layer("regions-$source", "line", source, "boundaries", maxOf(minZoom ?: 0, 4), maxZoom) {
            put("filter", expr("==", get("kind"), JsonPrimitive("region")))
            putJsonObject("paint") {
                put("line-color", c.regionBoundary)
                put("line-width", interpolateZoom(4 to 0.6, 10 to 1.2))
                putJsonArray("line-dasharray") { add(JsonPrimitive(3)); add(JsonPrimitive(2)) }
            }
        },
        layer("frontieres-$source", "line", source, "boundaries", minZoom, maxZoom) {
            put("filter", expr("==", get("kind"), JsonPrimitive("country")))
            putJsonObject("paint") {
                put("line-color", c.boundary)
                put("line-width", interpolateZoom(2 to 0.6, 6 to 1.2, 10 to 2.0))
            }
        },
    )

    /** Country, region and town names, in French when OpenStreetMap has the French name. */
    private fun labels(source: String, c: MapColors, minZoom: Int?): List<JsonObject> = listOf(
        layer("noms-regions-$source", "symbol", source, "places", minZoom ?: 5, 9) {
            put("filter", expr("==", get("kind"), JsonPrimitive("region")))
            putJsonObject("layout") {
                put("text-field", name())
                putJsonArray("text-font") { add(JsonPrimitive(MapFonts.REGULAR)) }
                put("text-size", interpolateZoom(5 to 11.0, 8 to 13.0))
                put("text-letter-spacing", 0.08)
                put("text-max-width", 7)
            }
            putJsonObject("paint") { text(c.labelMinor, c.labelHalo) }
        },
        layer("noms-villes-$source", "symbol", source, "places", minZoom, null) {
            put("filter", expr("==", get("kind"), JsonPrimitive("locality")))
            putJsonObject("layout") {
                put("text-field", name())
                putJsonArray("text-font") { add(JsonPrimitive(MapFonts.REGULAR)) }
                put("text-size", interpolateZoom(4 to 11.0, 8 to 13.0, 12 to 16.0))
                put("text-max-width", 8)
                put("symbol-sort-key", expr("coalesce", get("min_zoom"), JsonPrimitive(20)))
            }
            putJsonObject("paint") { text(c.label, c.labelHalo) }
        },
        layer("noms-pays-$source", "symbol", source, "places", minZoom, 7) {
            put("filter", expr("==", get("kind"), JsonPrimitive("country")))
            putJsonObject("layout") {
                put("text-field", name())
                putJsonArray("text-font") { add(JsonPrimitive(MapFonts.BOLD)) }
                put("text-size", interpolateZoom(2 to 11.0, 6 to 15.0))
                put("text-max-width", 6)
            }
            putJsonObject("paint") { text(c.labelMinor, c.labelHalo) }
        },
    )

    private fun kotlinx.serialization.json.JsonObjectBuilder.text(color: String, halo: String) {
        put("text-color", color)
        put("text-halo-color", halo)
        put("text-halo-width", 1.6)
    }

    private fun layer(
        id: String,
        type: String,
        source: String,
        sourceLayer: String,
        minZoom: Int?,
        maxZoom: Int?,
        body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("type", type)
        put("source", source)
        put("source-layer", sourceLayer)
        minZoom?.let { put("minzoom", it) }
        maxZoom?.let { put("maxzoom", it) }
        body()
    }

    private fun name(): JsonElement = expr("coalesce", get("name:fr"), get("name"))

    private fun get(property: String): JsonArray = buildJsonArray {
        add(JsonPrimitive("get"))
        add(JsonPrimitive(property))
    }

    private fun literal(vararg values: String): JsonArray = buildJsonArray {
        add(JsonPrimitive("literal"))
        add(buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private fun expr(operator: String, vararg args: JsonElement): JsonArray = buildJsonArray {
        add(JsonPrimitive(operator))
        args.forEach { add(it) }
    }

    private fun interpolateZoom(vararg stops: Pair<Int, Double>): JsonArray = buildJsonArray {
        add(JsonPrimitive("interpolate"))
        add(buildJsonArray { add(JsonPrimitive("linear")) })
        add(buildJsonArray { add(JsonPrimitive("zoom")) })
        stops.forEach { (zoom, value) ->
            add(JsonPrimitive(zoom))
            add(JsonPrimitive(value))
        }
    }
}
