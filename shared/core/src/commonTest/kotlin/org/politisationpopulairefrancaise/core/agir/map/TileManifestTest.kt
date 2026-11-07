package org.politisationpopulairefrancaise.core.agir.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TileManifestTest {
    private val sha = "0123456789abcdef".repeat(4)

    @Test
    fun readsTheManifestOfTheServer() {
        val m = assertNotNull(
            TileManifest.parse(
                """{"version":1,"file":"france-20261107.pmtiles","attribution":"© contributeurs d'OpenStreetMap","generated":"2026-11-07","sha256":"$sha","bytes":4200000000,"future":"ignored"}""",
            ),
        )
        assertEquals("france-20261107.pmtiles", m.file)
        assertEquals(4_200_000_000L, m.bytes)
        assertEquals(sha, m.sha256)
    }

    @Test
    fun refusesWhatCouldPointElsewhere() {
        for (file in listOf("../secret.pmtiles", "https://ailleurs.example/x.pmtiles", "France.pmtiles", "france.mbtiles", "")) {
            assertNull(TileManifest.parse("""{"version":1,"file":"$file","attribution":"a","generated":"g"}"""), file)
        }
        assertNull(TileManifest.parse("""{"version":2,"file":"france.pmtiles","attribution":"a","generated":"g"}"""))
        assertNull(TileManifest.parse("""{"version":1,"file":"france.pmtiles","attribution":"a","generated":"g","sha256":"xyz"}"""))
        assertNull(TileManifest.parse("not json"))
    }
}
