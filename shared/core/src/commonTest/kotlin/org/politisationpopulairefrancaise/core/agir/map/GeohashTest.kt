package org.politisationpopulairefrancaise.core.agir.map

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeohashTest {
    private fun assertNear(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, got $actual")

    @Test
    fun encodesTheReferenceExamples() {
        // The examples of the original description (Wikipedia, "Geohash").
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
        assertEquals("ezs42", Geohash.encode(42.605, -5.603, 5))
        // The corners of the world.
        assertEquals("0", Geohash.encode(-90.0, -180.0, 1))
        assertEquals("zzzzzzzzz", Geohash.encode(90.0, 180.0))
        assertEquals("s0000", Geohash.encode(0.0, 0.0, 5))
    }

    @Test
    fun decodesTheReferenceExamples() {
        val box = assertNotNull(Geohash.bounds("ezs42"))
        assertNear(42.583, box.south, 0.001)
        assertNear(42.627, box.north, 0.001)
        assertNear(-5.625, box.west, 0.001)
        assertNear(-5.581, box.east, 0.001)
        val point = assertNotNull(Geohash.decode("u4pruydqqvj"))
        assertNear(57.64911, point.latitude, 0.00001)
        assertNear(10.40744, point.longitude, 0.00001)
    }

    @Test
    fun aRoundTripStaysInTheCellAndNineCharactersAreAFewMetres() {
        // Town centres of mainland France and overseas, and the edges of the ranges.
        val points = listOf(
            GeoPoint(48.8675, 2.3637), // Paris, place de la République
            GeoPoint(43.2965, 5.3698), // Marseille
            GeoPoint(-21.1151, 55.5364), // La Réunion
            GeoPoint(14.6161, -61.0588), // Martinique
            GeoPoint(-89.9, 179.9),
            GeoPoint(0.0, 0.0),
        )
        for (point in points) {
            for (length in 1..Geohash.MAX_LENGTH) {
                val hash = Geohash.encode(point, length)
                assertEquals(length, hash.length)
                val box = assertNotNull(Geohash.bounds(hash))
                assertTrue(point in box, "$point outside of the cell of $hash")
                // A longer geohash is a cell inside the shorter one.
                if (length > 1) assertTrue(hash.startsWith(Geohash.encode(point, length - 1)))
            }
            val cell = assertNotNull(Geohash.bounds(Geohash.encode(point, Geohash.TAG_LENGTH)))
            // About 4.8 m by 4.8 m: 1e-4 degree is 11 m in latitude.
            assertTrue(cell.north - cell.south < 0.0001)
            assertTrue(cell.east - cell.west < 0.0001)
        }
    }

    @Test
    fun readsOnlyValidGeohashes() {
        assertTrue(Geohash.isValid("u09tvw0f6"))
        assertTrue(Geohash.isValid("U09TVW"))
        assertFalse(Geohash.isValid(""))
        assertFalse(Geohash.isValid("u09a")) // a is not in the alphabet
        assertFalse(Geohash.isValid("u09i"))
        assertFalse(Geohash.isValid("u09l"))
        assertFalse(Geohash.isValid("u09o"))
        assertFalse(Geohash.isValid("u09 tv"))
        assertFalse(Geohash.isValid("é"))
        assertFalse(Geohash.isValid("u".repeat(13)))
        assertFalse(Geohash.isValid("u".repeat(10), maxLength = 9))
        assertNull(Geohash.decode("not-a-hash"))
        assertEquals(Geohash.decode("u09tvw"), Geohash.decode("U09TVW"))
    }

    @Test
    fun refusesWhatIsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Geohash.encode(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { Geohash.encode(0.0, 181.0) }
        assertFailsWith<IllegalArgumentException> { Geohash.encode(0.0, 0.0, 0) }
        assertFailsWith<IllegalArgumentException> { Geohash.encode(0.0, 0.0, 13) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(-90.5, 0.0) }
    }
}
