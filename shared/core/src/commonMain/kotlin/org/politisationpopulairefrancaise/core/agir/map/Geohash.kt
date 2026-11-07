package org.politisationpopulairefrancaise.core.agir.map

/** A point on the Earth, in decimal degrees (WGS 84, the system of OpenStreetMap and geohashes). */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}

/** A rectangle of latitudes and longitudes: the cell of a geohash, or the bounds of a map. */
data class GeoBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)

    operator fun contains(point: GeoPoint): Boolean =
        point.latitude in south..north && point.longitude in west..east
}

/**
 * Geohashes (the `g` tag of NIP-52 calendar events, contract of the Agir space, section 2): a
 * point written as 1 to 12 characters of a base 32 alphabet (no a, i, l, o), each character
 * halving the cell alternately in longitude and latitude. Nine characters, the longest the
 * contract accepts, locate a cell of about 5 m by 5 m; five characters, about 5 km.
 *
 * Pure Kotlin, identical on every platform: no library and no service is used, and nothing
 * leaves the device. The algorithm is the public one of Gustavo Niemeyer (2008).
 */
object Geohash {
    const val ALPHABET: String = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** The longest geohash this application writes or reads (the contract accepts 9). */
    const val MAX_LENGTH: Int = 12

    /** The precision of the `g` tag: 9 characters, as the contract allows. */
    const val TAG_LENGTH: Int = 9

    private val INDEX: IntArray = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { i, c -> table[c.code] = i }
    }

    /** True when [text] is a geohash of 1 to [maxLength] characters (lowercase; uppercase is accepted too). */
    fun isValid(text: String, maxLength: Int = MAX_LENGTH): Boolean =
        text.length in 1..maxLength && text.all { c -> c.lowercaseChar().code < 128 && INDEX[c.lowercaseChar().code] >= 0 }

    /** The geohash of [point] in [length] characters (1 to [MAX_LENGTH]). */
    fun encode(point: GeoPoint, length: Int = TAG_LENGTH): String = encode(point.latitude, point.longitude, length)

    fun encode(latitude: Double, longitude: Double, length: Int = TAG_LENGTH): String {
        require(length in 1..MAX_LENGTH) { "length out of range: $length" }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "point out of range" }
        var south = -90.0
        var north = 90.0
        var west = -180.0
        var east = 180.0
        var evenBit = true // the first bit halves the longitudes
        val out = StringBuilder(length)
        var bits = 0
        var value = 0
        while (out.length < length) {
            if (evenBit) {
                val middle = (west + east) / 2
                if (longitude >= middle) {
                    value = (value shl 1) or 1
                    west = middle
                } else {
                    value = value shl 1
                    east = middle
                }
            } else {
                val middle = (south + north) / 2
                if (latitude >= middle) {
                    value = (value shl 1) or 1
                    south = middle
                } else {
                    value = value shl 1
                    north = middle
                }
            }
            evenBit = !evenBit
            if (++bits == 5) {
                out.append(ALPHABET[value])
                bits = 0
                value = 0
            }
        }
        return out.toString()
    }

    /** The cell of [geohash]; null when it is not a valid geohash. */
    fun bounds(geohash: String): GeoBox? {
        if (!isValid(geohash)) return null
        var south = -90.0
        var north = 90.0
        var west = -180.0
        var east = 180.0
        var evenBit = true
        for (c in geohash) {
            val value = INDEX[c.lowercaseChar().code]
            for (shift in 4 downTo 0) {
                val bit = (value shr shift) and 1
                if (evenBit) {
                    val middle = (west + east) / 2
                    if (bit == 1) west = middle else east = middle
                } else {
                    val middle = (south + north) / 2
                    if (bit == 1) south = middle else north = middle
                }
                evenBit = !evenBit
            }
        }
        return GeoBox(south, west, north, east)
    }

    /** The center of the cell of [geohash]; null when it is not a valid geohash. */
    fun decode(geohash: String): GeoPoint? = bounds(geohash)?.center
}
