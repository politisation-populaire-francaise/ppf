package org.politisationpopulairefrancaise.core.agir.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapStyleTest {
    private val colors = MapColors(
        water = "#c9d3f0", land = "#f3f5fb", road = "#ffffff", boundary = "#8a91ad",
        regionBoundary = "#b4b9d0", label = "#14182b", labelMinor = "#4a5272", labelHalo = "#ffffff",
    )

    private fun strings(element: JsonElement): List<String> = when (element) {
        is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
        is JsonArray -> element.flatMap(::strings)
        is JsonObject -> element.values.flatMap(::strings)
    }

    @Test
    fun theStyleNamesNoServerAndNoThirdParty() {
        for (withServer in listOf(false, true)) {
            val json = MapStyle.json(colors, withServer)
            val all = strings(Json.parseToJsonElement(json))
            // Every address of the style uses the private scheme answered by the application.
            val addresses = all.filter { "://" in it || it.startsWith("//") || it.startsWith("mapbox:") }
            assertTrue(addresses.isNotEmpty())
            assertTrue(addresses.all { it.startsWith(MapUrls.SCHEME) }, "unexpected address in $addresses")
            assertFalse(json.contains("http", ignoreCase = true), "no http address in the style")
            assertFalse(json.contains("sprite"), "no remote sprite")
        }
    }

    @Test
    fun theBackgroundUsesTheColoursOfThePaletteAndTheFontsOfTheApplication() {
        val style = MapStyle.build(colors, withServer = false)
        assertEquals(8, style["version"]!!.jsonPrimitive.content.toInt())
        assertEquals(MapUrls.GLYPHS, style["glyphs"]!!.jsonPrimitive.content)
        val layers = style["layers"]!!.jsonArray.map { it.jsonObject }
        val background = layers.first()
        assertEquals("background", background["type"]!!.jsonPrimitive.content)
        assertEquals(colors.water, background["paint"]!!.jsonObject["background-color"]!!.jsonPrimitive.content)
        val fonts = layers.mapNotNull { it["layout"]?.jsonObject?.get("text-font") }.flatMap { it.jsonArray.map { f -> f.jsonPrimitive.content } }.toSet()
        assertEquals(setOf(MapFonts.REGULAR, MapFonts.BOLD), fonts)
        val used = strings(style).filter { it.startsWith("#") }.toSet()
        assertTrue(used.all { it in setOf(colors.water, colors.land, colors.road, colors.boundary, colors.regionBoundary, colors.label, colors.labelMinor, colors.labelHalo) })
        // The sources declare the OpenStreetMap attribution (ODbL).
        val source = style["sources"]!!.jsonObject[MapStyle.SOURCE_BUNDLED]!!.jsonObject
        assertEquals(MapStyle.ATTRIBUTION, source["attribution"]!!.jsonPrimitive.content)
        assertEquals(MapArea.BUNDLED_MAX_ZOOM, source["maxzoom"]!!.jsonPrimitive.content.toInt())
        // Without server, only the bundled background.
        assertEquals(setOf(MapStyle.SOURCE_BUNDLED), style["sources"]!!.jsonObject.keys)
        assertTrue(layers.drop(1).all { it["source"]!!.jsonPrimitive.content == MapStyle.SOURCE_BUNDLED })
    }

    @Test
    fun withTheServerTheDetailedBackgroundStartsWhereTheBundledOneStops() {
        val style = MapStyle.build(colors, withServer = true)
        val detail = style["sources"]!!.jsonObject[MapStyle.SOURCE_SERVER]!!.jsonObject
        assertEquals(MapArea.SERVER_MIN_ZOOM, detail["minzoom"]!!.jsonPrimitive.content.toInt())
        assertEquals(MapUrls.SERVER_TILES, detail["tiles"]!!.jsonArray.single().jsonPrimitive.content)
        val layers = style["layers"]!!.jsonArray.map { it.jsonObject }
        val ids = layers.map { it["id"]!!.jsonPrimitive.content }
        // Land and sea of the bundled background stay underneath at every zoom.
        val land = layers.first { it["id"]!!.jsonPrimitive.content == "terre-fond" }
        assertFalse("maxzoom" in land)
        val roads = layers.first { it["id"]!!.jsonPrimitive.content == "routes-fond" }
        assertEquals(MapArea.SERVER_MIN_ZOOM, roads["maxzoom"]!!.jsonPrimitive.content.toInt())
        // The labels of the detailed background come last: they win the collisions.
        assertTrue(ids.indexOf("noms-villes-detail") > ids.indexOf("noms-villes-fond"))
    }
}
