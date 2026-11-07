package org.politisationpopulairefrancaise.tools.map

import java.io.ByteArrayOutputStream

/** The few Protocol Buffers primitives the tool needs (vector tiles and glyphs), without a library. */
internal object Protobuf {
    const val VARINT = 0
    const val FIXED64 = 1
    const val LENGTH = 2
    const val FIXED32 = 5

    /** One field of a message, with its raw encoding (key included) so that it can be copied as is. */
    class Field(val number: Int, val wireType: Int, val raw: ByteArray, val payload: ByteArray?, val value: Long)

    fun fields(bytes: ByteArray): List<Field> {
        val out = ArrayList<Field>()
        var i = 0
        while (i < bytes.size) {
            val start = i
            val (key, afterKey) = varint(bytes, i)
            i = afterKey
            val number = (key ushr 3).toInt()
            val wireType = (key and 7).toInt()
            var payload: ByteArray? = null
            var value = 0L
            when (wireType) {
                VARINT -> {
                    val (v, next) = varint(bytes, i)
                    value = v
                    i = next
                }
                FIXED64 -> i += 8
                LENGTH -> {
                    val (length, next) = varint(bytes, i)
                    require(length >= 0 && next + length <= bytes.size) { "truncated message" }
                    payload = bytes.copyOfRange(next, next + length.toInt())
                    i = next + length.toInt()
                }
                FIXED32 -> i += 4
                else -> error("unsupported wire type $wireType")
            }
            require(i <= bytes.size) { "truncated message" }
            out += Field(number, wireType, bytes.copyOfRange(start, i), payload, value)
        }
        return out
    }

    fun varint(bytes: ByteArray, from: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = from
        while (true) {
            require(i < bytes.size && shift < 64) { "truncated varint" }
            val b = bytes[i++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b < 0x80) return result to i
            shift += 7
        }
    }

    /** Builds a message field by field. */
    class Writer {
        private val out = ByteArrayOutputStream()

        fun raw(bytes: ByteArray) = apply { out.write(bytes) }

        fun uint(number: Int, value: Long) = apply {
            key(number, VARINT)
            writeVarint(value)
        }

        fun sint(number: Int, value: Int) = apply { uint(number, ((value shl 1) xor (value shr 31)).toLong() and 0xFFFFFFFFL) }

        fun bytes(number: Int, value: ByteArray) = apply {
            key(number, LENGTH)
            writeVarint(value.size.toLong())
            out.write(value)
        }

        fun string(number: Int, value: String) = bytes(number, value.encodeToByteArray())

        private fun key(number: Int, wireType: Int) = writeVarint(((number shl 3) or wireType).toLong())

        private fun writeVarint(value: Long) {
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) {
                    out.write(v.toInt())
                    return
                }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }

        fun toByteArray(): ByteArray = out.toByteArray()
    }
}

/**
 * Keeps only what the map of PPF draws in a vector tile (Mapbox Vector Tile 2.1): the layers named
 * in [keep], and in each of them the features whose `kind` is accepted. Features are copied byte
 * for byte (geometry untouched); only the dropped ones disappear, which makes the bundled
 * background several times smaller than the original tiles.
 */
internal class TileFilter(private val keep: Map<String, Set<String>?>) {
    fun filter(tile: ByteArray): ByteArray {
        val out = Protobuf.Writer()
        for (field in Protobuf.fields(tile)) {
            if (field.number != 3 || field.wireType != Protobuf.LENGTH) continue // only layers matter
            val layer = field.payload ?: continue
            val name = layerName(layer) ?: continue
            if (name !in keep) continue
            val kinds = keep[name]
            val filtered = if (kinds == null) layer else filterFeatures(layer, kinds) ?: continue
            out.bytes(3, filtered)
        }
        return out.toByteArray()
    }

    private fun layerName(layer: ByteArray): String? =
        Protobuf.fields(layer).firstOrNull { it.number == 1 && it.wireType == Protobuf.LENGTH }?.payload?.decodeToString()

    /** The layer without the features whose `kind` is not in [kinds]; null when no feature is left. */
    private fun filterFeatures(layer: ByteArray, kinds: Set<String>): ByteArray? {
        val fields = Protobuf.fields(layer)
        val keys = fields.filter { it.number == 3 }.map { it.payload!!.decodeToString() }
        val values = fields.filter { it.number == 4 }.map { stringValue(it.payload!!) }
        val kindKey = keys.indexOf("kind")
        val out = Protobuf.Writer()
        var kept = 0
        for (field in fields) {
            if (field.number == 2) {
                val kind = if (kindKey < 0) null else kindOf(field.payload!!, kindKey, values)
                if (kind == null || kind !in kinds) continue
                kept++
            }
            out.raw(field.raw)
        }
        return if (kept == 0) null else out.toByteArray()
    }

    private fun stringValue(value: ByteArray): String? =
        Protobuf.fields(value).firstOrNull { it.number == 1 && it.wireType == Protobuf.LENGTH }?.payload?.decodeToString()

    private fun kindOf(feature: ByteArray, kindKey: Int, values: List<String?>): String? {
        val tags = Protobuf.fields(feature).firstOrNull { it.number == 2 && it.wireType == Protobuf.LENGTH }?.payload ?: return null
        var i = 0
        while (i < tags.size) {
            val (key, afterKey) = Protobuf.varint(tags, i)
            val (value, afterValue) = Protobuf.varint(tags, afterKey)
            i = afterValue
            if (key.toInt() == kindKey) return values.getOrNull(value.toInt())
        }
        return null
    }

    companion object {
        /**
         * The layers and kinds of the Protomaps basemap, version 4, that the style of the
         * application draws (MapStyle): land, water, motorways and main roads, country and region
         * borders, country, region and town names. Land cover, land use, buildings, points of
         * interest and transit are left out.
         */
        val PPF: TileFilter = TileFilter(
            mapOf(
                "earth" to null,
                "water" to null,
                "roads" to setOf("highway", "major_road"),
                "boundaries" to setOf("country", "region"),
                "places" to setOf("country", "region", "locality"),
            ),
        )
    }
}
