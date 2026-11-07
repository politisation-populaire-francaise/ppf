package org.politisationpopulairefrancaise.core.agir.map

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PmtilesTest {
    @Test
    fun tileIdsFollowTheSpecification() {
        // Values of the specification and of the reference implementations (go-pmtiles, pmtiles.js).
        assertEquals(0L, Pmtiles.tileId(0, 0, 0))
        assertEquals(1L, Pmtiles.tileId(1, 0, 0))
        assertEquals(2L, Pmtiles.tileId(1, 0, 1))
        assertEquals(3L, Pmtiles.tileId(1, 1, 1))
        assertEquals(4L, Pmtiles.tileId(1, 1, 0))
        assertEquals(5L, Pmtiles.tileId(2, 0, 0))
        assertEquals(19_078_479L, Pmtiles.tileId(12, 3423, 1763))
        assertEquals(Triple(12, 3423, 1763), Pmtiles.zxy(19_078_479L))
        assertFailsWith<IllegalArgumentException> { Pmtiles.tileId(2, 4, 0) }
    }

    @Test
    fun everyTileOfTheFirstZoomsRoundTrips() {
        val seen = HashSet<Long>()
        for (z in 0..7) {
            val n = 1 shl z
            for (x in 0 until n) for (y in 0 until n) {
                val id = Pmtiles.tileId(z, x, y)
                assertEquals(Triple(z, x, y), Pmtiles.zxy(id))
                assertTrue(seen.add(id), "duplicate id $id")
            }
        }
        // Ids of a zoom level are contiguous: 0 .. (4^8 - 1) / 3 - 1.
        assertEquals((0L until 21_845L).toSet(), seen)
    }

    @Test
    fun theHeaderIsWrittenAndReadBack() {
        val header = Pmtiles.Header(
            rootOffset = 127, rootLength = 300, metadataOffset = 427, metadataLength = 50,
            leafOffset = 477, leafLength = 0, tileDataOffset = 477, tileDataLength = 12_345,
            addressedTiles = 100, tileEntries = 80, tileContents = 70, clustered = true,
            internalCompression = Pmtiles.Compression.NONE, tileCompression = Pmtiles.Compression.GZIP,
            tileType = Pmtiles.TileType.MVT, minZoom = 0, maxZoom = 7,
            minLongitude = -5.25, minLatitude = 41.25, maxLongitude = 9.7, maxLatitude = 51.2,
            centerZoom = 5, centerLongitude = 2.4, centerLatitude = 46.6,
        )
        val bytes = Pmtiles.writeHeader(header)
        assertEquals(Pmtiles.HEADER_LENGTH, bytes.size)
        assertEquals("PMTiles", bytes.copyOfRange(0, 7).decodeToString())
        assertEquals(header, Pmtiles.readHeader(bytes))
        assertFailsWith<IllegalArgumentException> { Pmtiles.readHeader(ByteArray(127)) }
    }

    @Test
    fun directoriesRoundTripWithRunsAndSharedContents() {
        val entries = listOf(
            Pmtiles.Entry(0, 0, 10, 1),
            Pmtiles.Entry(1, 10, 5, 3), // contiguous with the previous one
            Pmtiles.Entry(5, 0, 10, 1), // the same content as the first tile
            Pmtiles.Entry(300_000, 15, 2_000_000, 1),
        )
        assertEquals(entries, Pmtiles.decodeDirectory(Pmtiles.encodeDirectory(entries)))
        assertEquals(entries[1], Pmtiles.find(entries, 3))
        assertNull(Pmtiles.find(entries, 4))
        assertEquals(entries[3], Pmtiles.find(entries, 300_000))
        assertNull(Pmtiles.find(entries, 300_001))
    }

    @Test
    fun aWrittenFileIsReadBackTileByTile() = runTest {
        val writer = PmtilesWriter(Pmtiles.Compression.NONE)
        val sea = byteArrayOf(1, 2, 3)
        for (z in 0..3) {
            val n = 1 shl z
            for (x in 0 until n) for (y in 0 until n) writer.add(z, x, y, if ((x + y) % 2 == 0) sea else "tile $z/$x/$y".encodeToByteArray())
        }
        val file = writer.build(MapArea.FRANCE, MapArea.FRANCE_CENTER, 3, """{"attribution":"test"}""")
        val reader = PmtilesReader(ByteArrayRangeSource(file))
        val header = assertNotNull(reader.header())
        assertEquals(0, header.minZoom)
        assertEquals(3, header.maxZoom)
        assertEquals(85L, header.addressedTiles)
        assertTrue(header.tileContents < 85, "the sea is stored once")
        assertContentEquals("tile 3/5/2".encodeToByteArray(), reader.tile(3, 5, 2))
        assertContentEquals(sea, reader.tile(3, 4, 2))
        assertNull(reader.tile(4, 0, 0))
        assertNull(reader.tile(3, 8, 0))
        assertNull(PmtilesReader(ByteArrayRangeSource("not a file".encodeToByteArray())).header())
    }

    @Test
    fun manyTilesUseLeafDirectoriesAndGzipTilesComeBackPlain() = runTest {
        if (Gzip.compress(byteArrayOf(1)) == null) return@runTest // a platform without gzip (iOS for now)
        val writer = PmtilesWriter(Pmtiles.Compression.GZIP)
        // 4^8 = 65,536 different tiles: the root directory cannot hold them all in 16 KiB.
        for (x in 0 until 256) for (y in 0 until 256) writer.add(8, x, y, assertNotNull(Gzip.compress("$x,$y".encodeToByteArray())))
        val file = writer.build(MapArea.FRANCE, MapArea.FRANCE_CENTER, 8, "{}")
        val header = Pmtiles.readHeader(file)
        assertTrue(header.leafLength > 0, "leaf directories expected")
        assertTrue(header.rootOffset + header.rootLength <= Pmtiles.ROOT_LENGTH_MAX)
        val reader = PmtilesReader(ByteArrayRangeSource(file))
        assertContentEquals("129,200".encodeToByteArray(), reader.tile(8, 129, 200))
        assertContentEquals("0,0".encodeToByteArray(), reader.tile(8, 0, 0))
        assertContentEquals("255,255".encodeToByteArray(), reader.tile(8, 255, 255))
        assertNull(reader.tile(7, 0, 0))
    }
}
