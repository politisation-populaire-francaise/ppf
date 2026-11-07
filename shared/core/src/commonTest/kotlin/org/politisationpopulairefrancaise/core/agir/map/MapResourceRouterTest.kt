package org.politisationpopulairefrancaise.core.agir.map

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MapResourceRouterTest {
    private fun file(vararg tiles: Triple<Int, Int, Int>): ByteArray {
        val writer = PmtilesWriter(Pmtiles.Compression.NONE)
        tiles.forEach { (z, x, y) -> writer.add(z, x, y, "tile $z/$x/$y".encodeToByteArray()) }
        return writer.build(MapArea.FRANCE, MapArea.FRANCE_CENTER, 5, "{}")
    }

    private val bundledFile = file(Triple(0, 0, 0), Triple(5, 16, 11))
    private var bundledReads = 0
    private val glyphRequests = ArrayList<String>()

    private fun router(server: PmtilesReader? = null, serverAllowed: Boolean = false) = MapResourceRouter(
        bundled = {
            bundledReads++
            ByteArrayRangeSource(bundledFile)
        },
        glyphs = { folder, range ->
            glyphRequests += "$folder/$range"
            if (folder == "regular" && range == "0-255") byteArrayOf(42) else null
        },
        server = server,
        serverAllowed = { serverAllowed },
    )

    @Test
    fun theBundledBackgroundIsServedTileByTileAndReadOnce() = runTest {
        val router = router()
        assertContentEquals("tile 5/16/11".encodeToByteArray(), assertIs<MapResource.Bytes>(router.load("ppf-carte://fond/5/16/11")).bytes)
        assertContentEquals("tile 0/0/0".encodeToByteArray(), assertIs<MapResource.Bytes>(router.load("ppf-carte://fond/0/0/0.mvt")).bytes)
        // A tile outside the data is empty, not an error: the map draws the sea there.
        assertEquals(MapResource.Empty, router.load("ppf-carte://fond/5/0/0"))
        assertEquals(1, bundledReads)
        // A malformed tile address is refused.
        assertEquals(MapResource.Refused, router.load("ppf-carte://fond/5/99/0"))
        assertEquals(MapResource.Refused, router.load("ppf-carte://fond/a/b/c"))
    }

    @Test
    fun everyOtherAddressIsRefusedAndNeverFetched() = runTest {
        val router = router()
        for (url in listOf(
            "https://cartes.example.org/style.json",
            "https://tuiles.example.org/1/0/0.png",
            "http://localhost:8080/tiles/france.pmtiles",
            "mapbox://styles/x",
            "asset://style.json",
            "file:///etc/passwd",
            "ppf-carte://ailleurs/1/0/0",
        )) {
            assertEquals(MapResource.Refused, router.load(url), url)
        }
        assertFalse(router.accepts("https://example.org/"))
        assertTrue(router.accepts(MapUrls.REFUSED))
        assertEquals(MapResource.Refused, router.load(MapUrls.REFUSED))
    }

    @Test
    fun glyphsComeFromTheBundledFontsAndAMissingRangeIsEmpty() = runTest {
        val router = router()
        assertContentEquals(byteArrayOf(42), assertIs<MapResource.Bytes>(router.load("ppf-carte://glyphes/Atkinson%20Hyperlegible%20Regular/0-255.pbf")).bytes)
        val missing = assertIs<MapResource.Bytes>(router.load("ppf-carte://glyphes/Atkinson%20Hyperlegible%20Bold/8192-8447.pbf")).bytes
        // An empty set of glyphs: one font stack with its name and range, no glyph.
        assertEquals(0x0A, missing[0].toInt())
        assertTrue(missing.decodeToString().contains("Atkinson Hyperlegible Bold") && missing.decodeToString().contains("8192-8447"))
        // An unknown font falls back to the regular one; a malformed range is refused.
        router.load("ppf-carte://glyphes/Noto%20Sans%20Regular,Atkinson%20Hyperlegible%20Bold/256-511.pbf")
        assertEquals(listOf("regular/0-255", "bold/8192-8447", "bold/256-511"), glyphRequests)
        assertEquals(MapResource.Refused, router.load("ppf-carte://glyphes/Atkinson%20Hyperlegible%20Regular/0-300.pbf"))
        assertEquals("Atkinson Hyperlegible Regular", MapResourceRouter.percentDecode("Atkinson%20Hyperlegible%20Regular"))
        assertEquals("100%", MapResourceRouter.percentDecode("100%"))
    }

    @Test
    fun theDetailedBackgroundIsAskedOnlyWhenAllowed() = runTest {
        var reads = 0
        val serverFile = file(Triple(9, 259, 176))
        val server = PmtilesReader({ offset, length ->
            reads++
            ByteArrayRangeSource(serverFile).read(offset, length)
        })
        assertEquals(MapResource.Unavailable, router(server = null, serverAllowed = true).load("ppf-carte://serveur/9/259/176"))
        assertEquals(MapResource.Unavailable, router(server = server, serverAllowed = false).load("ppf-carte://serveur/9/259/176"))
        assertEquals(0, reads, "nothing is asked of the server while it is not allowed")
        val allowed = router(server = server, serverAllowed = true)
        assertContentEquals("tile 9/259/176".encodeToByteArray(), assertIs<MapResource.Bytes>(allowed.load("ppf-carte://serveur/9/259/176")).bytes)
        assertEquals(MapResource.Empty, allowed.load("ppf-carte://serveur/9/0/0"))
        val unreachable = PmtilesReader({ _, _ -> null })
        assertEquals(MapResource.Unavailable, router(server = unreachable, serverAllowed = true).load("ppf-carte://serveur/9/259/176"))
    }
}
