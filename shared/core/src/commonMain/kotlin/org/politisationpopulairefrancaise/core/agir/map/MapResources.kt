package org.politisationpopulairefrancaise.core.agir.map

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the application answers to one request of the map engine. */
sealed interface MapResource {
    /** The resource (a tile, plain; a set of glyphs). */
    class Bytes(val bytes: ByteArray) : MapResource

    /** A tile outside the data: the engine draws nothing there and reports no error. */
    data object Empty : MapResource

    /** Not one of our URLs: never fetched (the engine is told "not found"). */
    data object Refused : MapResource

    /** One of our resources that cannot be had now: the server is off, not configured or not reachable. */
    data object Unavailable : MapResource
}

/**
 * Answers every request of the map engine, on the device: the bundled background, the glyphs of
 * the labels and, when allowed, the detailed background read from our own server. Any other URL
 * is refused, so that the engine never reaches a third party (cahier des charges, section 4:
 * OpenStreetMap through MapLibre, never Google Maps; CLAUDE.md, section 7: no third party).
 *
 * @param bundled the bundled background (read once, on the first request)
 * @param glyphs the bytes of a bundled glyph file, from its font folder and its range ("0-255")
 * @param server the detailed background of our server; null when the application has no server
 * @param serverAllowed read at every request: false without network or in the data saving mode,
 *   so that no detailed tile is asked for then
 */
class MapResourceRouter(
    private val bundled: suspend () -> RangeSource?,
    private val glyphs: suspend (folder: String, range: String) -> ByteArray?,
    private val server: PmtilesReader? = null,
    private val serverAllowed: () -> Boolean = { false },
) {
    private val lock = Mutex()
    private var bundledReader: PmtilesReader? = null
    private var bundledMissing = false

    /** True for the URLs this router answers; the others are rewritten to [MapUrls.REFUSED]. */
    fun accepts(url: String): Boolean = MapUrls.isOwn(url)

    suspend fun load(url: String): MapResource {
        if (!MapUrls.isOwn(url)) return MapResource.Refused
        val path = url.removePrefix(MapUrls.SCHEME).substringBefore('?').substringBefore('#')
        val parts = path.split('/')
        return when (parts.firstOrNull()) {
            "fond" -> {
                val (z, x, y) = tileOf(parts) ?: return MapResource.Refused
                val reader = bundledReader() ?: return MapResource.Unavailable
                reader.tile(z, x, y)?.let(MapResource::Bytes) ?: MapResource.Empty
            }
            "serveur" -> {
                val (z, x, y) = tileOf(parts) ?: return MapResource.Refused
                val reader = server ?: return MapResource.Unavailable
                if (!serverAllowed()) return MapResource.Unavailable
                if (reader.header() == null) return MapResource.Unavailable
                reader.tile(z, x, y)?.let(MapResource::Bytes) ?: MapResource.Empty
            }
            "glyphes" -> glyphsOf(parts)
            else -> MapResource.Refused
        }
    }

    private suspend fun bundledReader(): PmtilesReader? = lock.withLock {
        bundledReader?.let { return@withLock it }
        if (bundledMissing) return@withLock null
        val source = bundled()
        if (source == null) {
            bundledMissing = true
            return@withLock null
        }
        PmtilesReader(source).also { bundledReader = it }
    }

    private suspend fun glyphsOf(parts: List<String>): MapResource {
        if (parts.size != 3) return MapResource.Refused
        val range = parts[2].removeSuffix(".pbf")
        val (start, end) = range.split('-').takeIf { it.size == 2 }?.map { it.toIntOrNull() } ?: return MapResource.Refused
        if (start == null || end == null || start % 256 != 0 || end != start + 255 || start !in 0..65_280) return MapResource.Refused
        val stack = percentDecode(parts[1]).split(',').map { it.trim() }
        val font = stack.firstOrNull { it in MapFonts.FOLDERS } ?: MapFonts.REGULAR
        val folder = MapFonts.FOLDERS.getValue(font)
        val bytes = glyphs(folder, "$start-$end") ?: emptyGlyphs(stack.joinToString(","), "$start-$end")
        return MapResource.Bytes(bytes)
    }

    companion object {
        /** z/x/y of "fond/7/64/44" (an extension such as ".mvt" or ".pbf" is accepted on y). */
        fun tileOf(parts: List<String>): Triple<Int, Int, Int>? {
            if (parts.size != 4) return null
            val z = parts[1].toIntOrNull() ?: return null
            val x = parts[2].toIntOrNull() ?: return null
            val y = parts[3].substringBefore('.').toIntOrNull() ?: return null
            if (z !in 0..22 || x < 0 || y < 0 || x >= (1 shl z) || y >= (1 shl z)) return null
            return Triple(z, x, y)
        }

        /** A glyph file with no glyph: the labels of a range we do not bundle are simply not drawn. */
        fun emptyGlyphs(fontstack: String, range: String): ByteArray {
            val stack = field(1, fontstack.encodeToByteArray()) + field(2, range.encodeToByteArray())
            return field(1, stack)
        }

        private fun field(number: Int, payload: ByteArray): ByteArray = byteArrayOf((number shl 3 or 2).toByte()) + varint(payload.size) + payload

        private fun varint(value: Int): ByteArray {
            val out = ArrayList<Byte>()
            var v = value
            while (v >= 0x80) {
                out += ((v and 0x7F) or 0x80).toByte()
                v = v ushr 7
            }
            out += v.toByte()
            return out.toByteArray()
        }

        /** "Atkinson%20Hyperlegible%20Regular" read back; malformed escapes are kept as written. */
        fun percentDecode(text: String): String {
            if ('%' !in text) return text
            val bytes = ArrayList<Byte>()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '%' && i + 2 < text.length) {
                    val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) {
                        bytes += hex.toByte()
                        i += 3
                        continue
                    }
                }
                c.toString().encodeToByteArray().forEach { bytes += it }
                i++
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}
