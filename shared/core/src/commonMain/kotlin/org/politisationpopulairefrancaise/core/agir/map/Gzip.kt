package org.politisationpopulairefrancaise.core.agir.map

/**
 * Gzip (RFC 1952) for the map backgrounds: the tiles of a PMTiles file are usually stored
 * compressed, and the renderer needs them plain. Provided by the platform (`java.util.zip` on
 * Android and the JVM); iOS returns null until its application is compiled (a tile then reads as
 * missing, never as garbage).
 */
object Gzip {
    /** At most this many bytes are inflated from one tile, so that a malformed file cannot exhaust memory. */
    const val MAX_OUTPUT: Int = 16 * 1024 * 1024

    /** The uncompressed bytes, or null when [bytes] is not valid gzip (or the platform cannot decompress). */
    fun decompress(bytes: ByteArray): ByteArray? = platformGunzip(bytes, MAX_OUTPUT)

    /** The compressed bytes, or null when the platform cannot compress. */
    fun compress(bytes: ByteArray): ByteArray? = platformGzip(bytes)
}

internal expect fun platformGunzip(bytes: ByteArray, maxOutput: Int): ByteArray?

internal expect fun platformGzip(bytes: ByteArray): ByteArray?
