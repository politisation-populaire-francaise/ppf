package org.politisationpopulairefrancaise.tools.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.map.GeoBox
import org.politisationpopulairefrancaise.core.agir.map.Gzip
import org.politisationpopulairefrancaise.core.agir.map.MapArea
import org.politisationpopulairefrancaise.core.agir.map.PmtilesReader
import org.politisationpopulairefrancaise.core.agir.map.PmtilesWriter
import org.politisationpopulairefrancaise.core.agir.map.Pmtiles
import org.politisationpopulairefrancaise.core.agir.map.RangeSource
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * The low zoom background of France bundled with the application: the whole world very roughly
 * (up to [MapArea.WORLD_MAX_ZOOM]), then mainland France, Corsica and the overseas departments up
 * to [maxZoom], keeping only what the style draws ([TileFilter.PPF]). Tiles are gzip-compressed,
 * directories are plain (every reader of the application can use them).
 *
 * The source is a PMTiles build of the OpenStreetMap basemap of Protomaps (schema version 4,
 * ODbL): a local file, or the URL of a daily build, read by HTTP range requests (only the tiles
 * needed are downloaded, a few megabytes). This runs on the computer of a developer preparing the
 * data, never in the application nor in continuous integration.
 */
internal class Basemap(private val source: RangeSource, private val maxZoom: Int, private val filter: TileFilter = TileFilter.PPF) {
    class Report(val bytes: ByteArray, val perZoom: Map<Int, Pair<Int, Long>>, val originalBytes: Long)

    suspend fun build(sourceName: String): Report = coroutineScope {
        val reader = PmtilesReader(source, maxCachedLeaves = 64)
        val header = requireNotNull(reader.header()) { "the source is not a PMTiles file" }
        require(header.tileType == Pmtiles.TileType.MVT) { "the source must hold vector tiles" }
        val wanted = tiles(maxZoom)
        val gate = Semaphore(8)
        val results = wanted.map { (z, x, y) ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val tile = reader.tile(z, x, y) ?: return@withPermit null
                    val filtered = filter.filter(tile)
                    Triple(Triple(z, x, y), tile.size.toLong(), filtered)
                }
            }
        }.awaitAll().filterNotNull()
        val writer = PmtilesWriter(Pmtiles.Compression.GZIP)
        val perZoom = HashMap<Int, Pair<Int, Long>>()
        var original = 0L
        for ((zxy, originalSize, filtered) in results) {
            if (filtered.isEmpty()) continue // nothing the map draws: the sea is the background colour
            val compressed = requireNotNull(Gzip.compress(filtered))
            writer.add(zxy.first, zxy.second, zxy.third, compressed)
            val (count, size) = perZoom[zxy.first] ?: (0 to 0L)
            perZoom[zxy.first] = (count + 1) to (size + compressed.size)
            original += originalSize
        }
        val metadata = """{"name":"PPF : fond de carte de la France, faible zoom","attribution":"© contributeurs OpenStreetMap",""" +
            """"licence":"ODbL 1.0 (https://opendatacommons.org/licenses/odbl/1-0/)","description":"Extrait filtré du fond OpenStreetMap de Protomaps (schéma 4)",""" +
            """"source":"${sourceName.replace("\"", "")}","maxzoom":$maxZoom}"""
        val bytes = withContext(Dispatchers.Default) {
            writer.build(MapArea.FRANCE, MapArea.FRANCE_CENTER, 5, metadata)
        }
        Report(bytes, perZoom.toSortedMap(), original)
    }

    companion object {
        /** Every tile of the bundled background: the world up to WORLD_MAX_ZOOM, then France and the overseas departments. */
        fun tiles(maxZoom: Int): List<Triple<Int, Int, Int>> {
            val out = LinkedHashSet<Triple<Int, Int, Int>>()
            for (z in 0..minOf(MapArea.WORLD_MAX_ZOOM, maxZoom)) {
                val n = 1 shl z
                for (x in 0 until n) for (y in 0 until n) out += Triple(z, x, y)
            }
            val areas = listOf(MapArea.FRANCE) + MapArea.OVERSEAS.map { it.second }
            for (z in MapArea.WORLD_MAX_ZOOM + 1..maxZoom) for (box in areas) out += cover(box, z)
            return out.toList()
        }

        /** The tiles of zoom [z] that intersect [box] (Web Mercator). */
        fun cover(box: GeoBox, z: Int): List<Triple<Int, Int, Int>> {
            val n = 1 shl z
            fun x(lon: Double) = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            fun y(lat: Double): Int {
                val r = lat * PI / 180.0
                return floor((1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            }
            val out = ArrayList<Triple<Int, Int, Int>>()
            for (tx in x(box.west)..x(box.east)) for (ty in y(box.north)..y(box.south)) out += Triple(z, tx, ty)
            return out
        }
    }
}

/** A local PMTiles file. */
internal class FileRangeSource(private val file: File) : RangeSource {
    override suspend fun read(offset: Long, length: Int): ByteArray? = withContext(Dispatchers.IO) {
        RandomAccessFile(file, "r").use { raf ->
            if (offset >= raf.length()) return@use null
            val size = minOf(length.toLong(), raf.length() - offset).toInt()
            ByteArray(size).also {
                raf.seek(offset)
                raf.readFully(it)
            }
        }
    }
}

/** A PMTiles file on the web, read by HTTP range requests (JDK client: no library). */
internal class WebRangeSource(private val url: String) : RangeSource {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build()
    var bytesRead = 0L
        private set
    var requests = 0
        private set

    init {
        require(url.startsWith("https://")) { "only https sources" }
    }

    override suspend fun read(offset: Long, length: Int): ByteArray? = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Range", "bytes=$offset-${offset + length - 1}")
            .header("User-Agent", "ppf-map-data (preparation of the bundled map of Politisation Populaire Francaise)")
            .timeout(Duration.ofSeconds(60))
            .GET().build()
        repeat(3) { attempt ->
            // Streamed: only a partial answer (206) is read, never a whole file that ignored the range.
            val response = runCatching { client.send(request, HttpResponse.BodyHandlers.ofInputStream()) }.getOrNull()
            if (response != null) {
                response.body().use { body ->
                    if (response.statusCode() == 206) {
                        val bytes = body.readNBytes(length)
                        synchronized(this@WebRangeSource) {
                            bytesRead += bytes.size
                            requests++
                        }
                        return@withContext bytes
                    }
                }
                if (response.statusCode() in 400..499) return@withContext null
            }
            Thread.sleep(1_000L * (attempt + 1))
        }
        null
    }
}
