package org.politisationpopulairefrancaise.server.tiles

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.agir.map.HttpRangeSource
import org.politisationpopulairefrancaise.core.agir.map.MapArea
import org.politisationpopulairefrancaise.core.agir.map.Pmtiles
import org.politisationpopulairefrancaise.core.agir.map.PmtilesReader
import org.politisationpopulairefrancaise.core.agir.map.PmtilesWriter
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The development route of the map backgrounds: byte ranges of a PMTiles file, as the application reads them. */
class TilesRoutesTest {
    private val directory = Files.createTempDirectory("ppf-tiles").also { dir ->
        val writer = PmtilesWriter(Pmtiles.Compression.NONE)
        writer.add(9, 259, 176, "tile of Paris".encodeToByteArray())
        writer.add(9, 268, 187, "tile of Lyon".encodeToByteArray())
        Files.write(dir.resolve("france.pmtiles"), writer.build(MapArea.FRANCE, MapArea.FRANCE_CENTER, 9, "{}"))
        Files.writeString(dir.resolve("secret.txt"), "not a tile file")
    }

    private fun app(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(policy = NO_EDITORS, relay = TEST_RELAY, revendications = testRevendications(), tiles = TilesConfig(directory)) }
        block()
    }

    @Test
    fun theApplicationReadsTilesByRangeFromTheServer() = app {
        // The very reader of the application, over the test client.
        val reader = PmtilesReader(HttpRangeSource(client, "http://localhost/tiles/france.pmtiles"))
        assertEquals(9, assertNotNull(reader.header()).maxZoom)
        assertContentEquals("tile of Paris".encodeToByteArray(), reader.tile(9, 259, 176))
        assertContentEquals("tile of Lyon".encodeToByteArray(), reader.tile(9, 268, 187))
        assertNull(reader.tile(9, 0, 0))
    }

    @Test
    fun answersRangesOnlyForTileFiles() = app {
        val partial = client.get("/tiles/france.pmtiles") { header(HttpHeaders.Range, "bytes=0-6") }
        assertEquals(HttpStatusCode.PartialContent, partial.status)
        assertEquals("PMTiles", partial.readRawBytes().decodeToString())
        assertEquals("bytes", partial.headers[HttpHeaders.AcceptRanges])
        val size = Files.size(directory.resolve("france.pmtiles"))
        assertEquals("bytes 0-6/$size", partial.headers[HttpHeaders.ContentRange])
        // No range: refused (no client of ours downloads a whole background).
        assertEquals(HttpStatusCode.BadRequest, client.get("/tiles/france.pmtiles").status)
        // Beyond the end of the file.
        val beyond = client.get("/tiles/france.pmtiles") { header(HttpHeaders.Range, "bytes=${size + 10}-${size + 20}") }
        assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, beyond.status)
        // Only PMTiles files of the folder, never anything else.
        assertEquals(HttpStatusCode.NotFound, client.get("/tiles/secret.txt") { header(HttpHeaders.Range, "bytes=0-3") }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/tiles/..%2Fsecret.pmtiles") { header(HttpHeaders.Range, "bytes=0-3") }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/tiles/absent.pmtiles") { header(HttpHeaders.Range, "bytes=0-3") }.status)
    }

    @Test
    fun withoutTheFolderThereIsNoRoute() = testApplication {
        application { module(policy = NO_EDITORS, relay = TEST_RELAY, revendications = testRevendications(), tiles = TilesConfig(null)) }
        assertEquals(HttpStatusCode.NotFound, client.get("/tiles/france.pmtiles") { header(HttpHeaders.Range, "bytes=0-6") }.status)
    }

    @Test
    fun rangesAreReadLikeTheStandardSays() {
        assertEquals(0L..9L, parseRange("bytes=0-9", 100))
        assertEquals(90L..99L, parseRange("bytes=90-", 100))
        assertEquals(80L..99L, parseRange("bytes=-20", 100))
        assertEquals(95L..99L, parseRange("bytes=95-200", 100))
        assertEquals(LongRange.EMPTY, parseRange("bytes=100-120", 100))
        assertNull(parseRange(null, 100))
        assertNull(parseRange("bytes=0-1,5-6", 100))
        assertNull(parseRange("bytes=9-3", 100))
        assertNull(parseRange("items=0-9", 100))
        assertNull(parseRange("bytes=0-${9L * 1024 * 1024}", 20L * 1024 * 1024), "over the limit of one answer")
    }
}
