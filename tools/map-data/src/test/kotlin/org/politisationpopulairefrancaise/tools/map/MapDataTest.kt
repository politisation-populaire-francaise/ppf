package org.politisationpopulairefrancaise.tools.map

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.map.ByteArrayRangeSource
import org.politisationpopulairefrancaise.core.agir.map.Gzip
import org.politisationpopulairefrancaise.core.agir.map.MapArea
import org.politisationpopulairefrancaise.core.agir.map.Pmtiles
import org.politisationpopulairefrancaise.core.agir.map.PmtilesReader
import org.politisationpopulairefrancaise.core.agir.map.PmtilesWriter
import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapDataTest {
    /** A vector tile layer: name, keys, values and one feature per kind (geometry left empty). */
    private fun layer(name: String, kinds: List<String>): ByteArray {
        val w = Protobuf.Writer().uint(15, 2).string(1, name)
        kinds.forEachIndexed { i, _ ->
            // tags: key 0 ("kind") = value i
            val tags = Protobuf.Writer().raw(byteArrayOf(0, i.toByte())).toByteArray()
            w.bytes(2, Protobuf.Writer().uint(1, i.toLong()).bytes(2, tags).uint(3, 1).toByteArray())
        }
        w.string(3, "kind")
        kinds.forEach { w.bytes(4, Protobuf.Writer().string(1, it).toByteArray()) }
        return w.uint(5, 4096).toByteArray()
    }

    private fun tile(vararg layers: ByteArray): ByteArray = Protobuf.Writer().apply { layers.forEach { bytes(3, it) } }.toByteArray()

    private fun layersOf(tile: ByteArray): Map<String, Int> = Protobuf.fields(tile).filter { it.number == 3 }.associate { field ->
        val fields = Protobuf.fields(field.payload!!)
        fields.first { it.number == 1 }.payload!!.decodeToString() to fields.count { it.number == 2 }
    }

    @Test
    fun theFilterKeepsWhatTheStyleDrawsOnly() {
        val original = tile(
            layer("earth", listOf("earth")),
            layer("roads", listOf("highway", "minor_road", "major_road", "path")),
            layer("pois", listOf("restaurant")),
            layer("buildings", listOf("building")),
            layer("places", listOf("neighbourhood")),
        )
        val filtered = TileFilter.PPF.filter(original)
        // Roads keep two features of four; a layer left with no feature disappears.
        assertEquals(mapOf("earth" to 1, "roads" to 2), layersOf(filtered))
        assertTrue(filtered.size < original.size)
        assertEquals(0, TileFilter.PPF.filter(tile(layer("pois", listOf("cafe")))).size)
    }

    @Test
    fun theTilesCoverTheWorldThenFranceAndTheOverseasDepartments() {
        val tiles = Basemap.tiles(7)
        assertEquals((0..MapArea.WORLD_MAX_ZOOM).sumOf { 1 shl (2 * it) }, tiles.count { it.first <= MapArea.WORLD_MAX_ZOOM })
        // Paris (48.86 N, 2.35 E) at zoom 7 is tile 64/44; Saint-Denis de La Réunion at zoom 7 is 83/72.
        assertTrue(Triple(7, 64, 44) in tiles)
        assertTrue(Triple(7, 83, 71) in tiles || Triple(7, 83, 72) in tiles)
        // Not New York.
        assertTrue(Triple(7, 37, 48) !in tiles)
        assertEquals(tiles.size, tiles.toSet().size)
    }

    @Test
    fun aBasemapIsBuiltFromASourceFile() = runBlocking {
        val writer = PmtilesWriter(Pmtiles.Compression.GZIP)
        val land = tile(layer("earth", listOf("earth")), layer("pois", listOf("cafe")))
        for ((z, x, y) in Basemap.tiles(5)) writer.add(z, x, y, Gzip.compress(land)!!)
        val source = writer.build(MapArea.FRANCE, MapArea.FRANCE_CENTER, 5, "{}")
        val report = Basemap(ByteArrayRangeSource(source), 5).build("test")
        val reader = PmtilesReader(ByteArrayRangeSource(report.bytes))
        val header = assertNotNull(reader.header())
        assertEquals(5, header.maxZoom)
        assertEquals(Pmtiles.Compression.GZIP, header.tileCompression)
        assertEquals(mapOf("earth" to 1), layersOf(assertNotNull(reader.tile(5, 16, 11))))
        assertNull(reader.tile(6, 32, 22))
    }

    @Test
    fun glyphsAreSignedDistanceFieldsWithTheMetricsOfTheReferenceTool() {
        val font = Font.createFont(Font.TRUETYPE_FONT, File("../../${MapDataPaths.FONTS.values.first()}")).deriveFont(Glyphs.SIZE)
        val a = assertNotNull(Glyphs.glyph(font, 'A'.code))
        assertTrue(a.width in 12..20 && a.height in 15..20, "a capital A of a 24 px font: ${a.width} x ${a.height}")
        val bitmap = assertNotNull(a.bitmap)
        assertEquals((a.width + 6) * (a.height + 6), bitmap.size)
        // The corner is far outside (0), some pixel is well inside the stroke (over the edge value 191).
        assertEquals(0, bitmap[0].toInt() and 0xFF)
        assertTrue(bitmap.any { (it.toInt() and 0xFF) > 191 })
        // The capital sits on the baseline: its top is above, near the ascender (top is negative or near 0).
        assertTrue(a.top in -12..2, "top ${a.top}")
        val space = assertNotNull(Glyphs.glyph(font, ' '.code))
        assertNull(space.bitmap)
        assertTrue(space.advance > 0)
        val file = Glyphs.encode("Atkinson Hyperlegible Regular", "0-255", listOf(a, space))
        val stack = Protobuf.fields(file).single().payload!!
        val fields = Protobuf.fields(stack)
        assertEquals("Atkinson Hyperlegible Regular", fields.first { it.number == 1 }.payload!!.decodeToString())
        assertEquals("0-255", fields.first { it.number == 2 }.payload!!.decodeToString())
        assertEquals(2, fields.count { it.number == 3 })
        val first = Protobuf.fields(fields.first { it.number == 3 }.payload!!)
        assertEquals('A'.code.toLong(), first.first { it.number == 1 }.value)
        assertContentEquals(bitmap, first.first { it.number == 2 }.payload)
    }
}
