package org.politisationpopulairefrancaise.tools.map

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.map.MapArea
import org.politisationpopulairefrancaise.core.agir.map.MapFonts
import java.io.File
import kotlin.system.exitProcess

/** Where the application reads the data of its map (Compose resources, bundled in every build). */
internal object MapDataPaths {
    const val RESOURCES = "shared/ui/src/commonMain/composeResources"
    const val BASEMAP = "$RESOURCES/files/carte/fond-france.pmtiles"
    const val GLYPHS = "$RESOURCES/files/carte/glyphes"
    val FONTS = mapOf(
        MapFonts.REGULAR to "$RESOURCES/font/atkinson_hyperlegible_regular.ttf",
        MapFonts.BOLD to "$RESOURCES/font/atkinson_hyperlegible_bold.ttf",
    )
}

/**
 * Prepares the data of the map, from the root of the repository (guide: tools/map-data/README.md):
 *
 * - `glyphes`: the glyphs of the labels, from the bundled fonts (nothing is downloaded);
 * - `region [exemples]`: the GeoJSON of the area of the detailed background (mainland France,
 *   Corsica and each overseas department; with `exemples`, only the towns of the examples, for the
 *   preproduction), for `pmtiles extract --region`;
 * - `fond <source> [--max-zoom N] [--essai]`: the low zoom background bundled with the
 *   application, from a Protomaps build (local file or https URL); `--essai` only prints the
 *   sizes, zoom by zoom, without writing anything.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "glyphes" -> glyphs()
        "region" -> println(regionGeoJson(args.getOrNull(1)))
        "fond" -> {
            val source = args.getOrNull(1) ?: usage()
            val maxZoom = args.indexOf("--max-zoom").takeIf { it >= 0 }?.let { args.getOrNull(it + 1)?.toIntOrNull() } ?: MapArea.BUNDLED_MAX_ZOOM
            basemap(source, maxZoom, write = "--essai" !in args)
        }
        else -> usage()
    }
}

private fun usage(): Nothing {
    System.err.println("usage: map-data glyphes | map-data fond <fichier.pmtiles | https://...> [--max-zoom N] [--essai]")
    exitProcess(2)
}

private fun glyphs() {
    for ((fontstack, path) in MapDataPaths.FONTS) {
        val folder = File(MapDataPaths.GLYPHS, MapFonts.FOLDERS.getValue(fontstack))
        folder.listFiles { f -> f.name.endsWith(".pbf") }?.forEach { it.delete() }
        val ranges = Glyphs.write(File(path), fontstack, folder)
        val size = folder.listFiles()?.sumOf { it.length() } ?: 0
        println("$fontstack: ${ranges.size} range(s) ${ranges.joinToString()}, ${size / 1024} KiB in $folder")
    }
}

private fun basemap(sourceName: String, maxZoom: Int, write: Boolean) = runBlocking {
    require(maxZoom in MapArea.WORLD_MAX_ZOOM..12) { "max zoom out of range" }
    val web = if (sourceName.startsWith("https://")) WebRangeSource(sourceName) else null
    val source = web ?: FileRangeSource(File(sourceName).also { require(it.isFile) { "no such file: $it" } })
    val started = System.currentTimeMillis()
    val report = Basemap(source, maxZoom).build(sourceName)
    for ((z, value) in report.perZoom) {
        val (count, bytes) = value
        println("zoom $z: $count tile(s), ${"%.1f".format(bytes / 1024.0)} KiB")
    }
    println("total: ${"%.2f".format(report.bytes.size / 1_048_576.0)} MiB (tiles of the source before filtering: ${"%.2f".format(report.originalBytes / 1_048_576.0)} MiB)")
    web?.let { println("downloaded: ${"%.2f".format(it.bytesRead / 1_048_576.0)} MiB in ${it.requests} range request(s)") }
    println("time: ${(System.currentTimeMillis() - started) / 1000} s")
    if (write) {
        val out = File(MapDataPaths.BASEMAP)
        out.parentFile.mkdirs()
        out.writeBytes(report.bytes)
        println("written: $out")
    }
}

/** The towns of the [EXEMPLE] mobilizations, for the small extract of the preproduction (about 20 km around each). */
internal val EXAMPLE_TOWNS: List<Pair<String, org.politisationpopulairefrancaise.core.agir.map.GeoBox>> = listOf(
    "paris" to org.politisationpopulairefrancaise.core.agir.map.GeoBox(48.70, 2.15, 49.05, 2.60),
    "lyon" to org.politisationpopulairefrancaise.core.agir.map.GeoBox(45.65, 4.70, 45.85, 5.00),
    "marseille" to org.politisationpopulairefrancaise.core.agir.map.GeoBox(43.20, 5.25, 43.42, 5.55),
    "lille" to org.politisationpopulairefrancaise.core.agir.map.GeoBox(50.55, 2.95, 50.72, 3.20),
    "toulouse" to org.politisationpopulairefrancaise.core.agir.map.GeoBox(43.52, 1.32, 43.70, 1.56),
    "rennes" to org.politisationpopulairefrancaise.core.agir.map.GeoBox(48.05, -1.78, 48.16, -1.58),
    "strasbourg" to org.politisationpopulairefrancaise.core.agir.map.GeoBox(48.50, 7.65, 48.65, 7.85),
)

/** A GeoJSON FeatureCollection of rectangles: the whole area, or the example towns. */
internal fun regionGeoJson(which: String?): String {
    val boxes = if (which == "exemples") EXAMPLE_TOWNS else listOf("metropole" to MapArea.FRANCE) + MapArea.OVERSEAS
    val features = boxes.joinToString(",") { (name, b) ->
        val ring = listOf(b.west to b.south, b.east to b.south, b.east to b.north, b.west to b.north, b.west to b.south)
            .joinToString(",") { (x, y) -> "[$x,$y]" }
        """{"type":"Feature","properties":{"name":"$name"},"geometry":{"type":"Polygon","coordinates":[[$ring]]}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}
