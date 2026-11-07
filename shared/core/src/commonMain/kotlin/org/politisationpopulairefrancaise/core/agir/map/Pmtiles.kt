package org.politisationpopulairefrancaise.core.agir.map

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * PMTiles version 3 (https://github.com/protomaps/PMTiles, specification v3): one file holding
 * every tile of a map, read by byte ranges. It is the format of the map backgrounds of the
 * application: the low zoom background of France bundled with it, and the detailed background
 * served later by our own server as a plain file (Caddy `file_server` or the development route,
 * with HTTP range requests), with no tile server and no third party.
 *
 * Pure Kotlin, identical on every platform. Directories may be uncompressed or gzip-compressed;
 * gzip goes through [Gzip], which every platform but iOS (not compiled yet) provides.
 */
object Pmtiles {
    const val HEADER_LENGTH: Int = 127

    /** The first 16 KiB hold the header and the root directory, so that one request reads both. */
    const val ROOT_LENGTH_MAX: Int = 16_384

    private val MAGIC = "PMTiles".encodeToByteArray()

    /** Compression of the directories and of the tiles. */
    enum class Compression(val code: Int) {
        UNKNOWN(0), NONE(1), GZIP(2), BROTLI(3), ZSTD(4);

        companion object {
            fun of(code: Int): Compression = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    /** What the tiles are: vector tiles (MVT) for every background of the application. */
    enum class TileType(val code: Int) {
        UNKNOWN(0), MVT(1), PNG(2), JPEG(3), WEBP(4), AVIF(5);

        companion object {
            fun of(code: Int): TileType = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    data class Header(
        val rootOffset: Long,
        val rootLength: Long,
        val metadataOffset: Long,
        val metadataLength: Long,
        val leafOffset: Long,
        val leafLength: Long,
        val tileDataOffset: Long,
        val tileDataLength: Long,
        val addressedTiles: Long,
        val tileEntries: Long,
        val tileContents: Long,
        val clustered: Boolean,
        val internalCompression: Compression,
        val tileCompression: Compression,
        val tileType: TileType,
        val minZoom: Int,
        val maxZoom: Int,
        val minLongitude: Double,
        val minLatitude: Double,
        val maxLongitude: Double,
        val maxLatitude: Double,
        val centerZoom: Int,
        val centerLongitude: Double,
        val centerLatitude: Double,
    )

    /**
     * One directory entry: [runLength] consecutive tiles from [tileId] share the data at [offset]
     * (relative to the tile data); a run length of 0 points to a leaf directory instead (offset
     * relative to the leaf directories).
     */
    data class Entry(val tileId: Long, val offset: Long, val length: Int, val runLength: Int)

    /** The number of tiles of the zoom levels below [z]: (4^z - 1) / 3. */
    private fun zoomBase(z: Int): Long = ((1L shl (2 * z)) - 1) / 3

    /** The tile id of z/x/y: the zoom levels below, then the position on the Hilbert curve. */
    fun tileId(z: Int, x: Int, y: Int): Long {
        require(z in 0..26) { "zoom out of range: $z" }
        val n = 1L shl z
        require(x >= 0 && y >= 0 && x < n && y < n) { "tile out of range: $z/$x/$y" }
        var tx = x.toLong()
        var ty = y.toLong()
        var d = 0L
        var s = n / 2
        while (s > 0) {
            val rx = if ((tx and s) > 0) 1L else 0L
            val ry = if ((ty and s) > 0) 1L else 0L
            d += s * s * ((3 * rx) xor ry)
            if (ry == 0L) {
                if (rx == 1L) {
                    tx = s - 1 - tx
                    ty = s - 1 - ty
                }
                val t = tx
                tx = ty
                ty = t
            }
            s /= 2
        }
        return zoomBase(z) + d
    }

    /** The z/x/y of a tile id, as `Triple(z, x, y)`. */
    fun zxy(tileId: Long): Triple<Int, Int, Int> {
        require(tileId >= 0) { "negative tile id" }
        var z = 0
        while (z < 27 && zoomBase(z + 1) <= tileId) z++
        require(z <= 26) { "tile id out of range: $tileId" }
        val n = 1L shl z
        var t = tileId - zoomBase(z)
        var tx = 0L
        var ty = 0L
        var s = 1L
        while (s < n) {
            val rx = 1L and (t / 2)
            val ry = 1L and (t xor rx)
            if (ry == 0L) {
                if (rx == 1L) {
                    tx = s - 1 - tx
                    ty = s - 1 - ty
                }
                val tmp = tx
                tx = ty
                ty = tmp
            }
            tx += s * rx
            ty += s * ry
            t /= 4
            s *= 2
        }
        return Triple(z, tx.toInt(), ty.toInt())
    }

    fun readHeader(bytes: ByteArray): Header {
        require(bytes.size >= HEADER_LENGTH) { "not a PMTiles file: too short" }
        require(bytes.copyOfRange(0, 7).contentEquals(MAGIC)) { "not a PMTiles file" }
        require(bytes[7].toInt() == 3) { "unsupported PMTiles version ${bytes[7]}" }
        fun u64(at: Int): Long {
            var v = 0L
            for (i in 7 downTo 0) v = (v shl 8) or (bytes[at + i].toLong() and 0xFF)
            return v
        }
        fun i32(at: Int): Int {
            var v = 0
            for (i in 3 downTo 0) v = (v shl 8) or (bytes[at + i].toInt() and 0xFF)
            return v
        }
        fun u8(at: Int): Int = bytes[at].toInt() and 0xFF
        return Header(
            rootOffset = u64(8), rootLength = u64(16),
            metadataOffset = u64(24), metadataLength = u64(32),
            leafOffset = u64(40), leafLength = u64(48),
            tileDataOffset = u64(56), tileDataLength = u64(64),
            addressedTiles = u64(72), tileEntries = u64(80), tileContents = u64(88),
            clustered = u8(96) == 1,
            internalCompression = Compression.of(u8(97)),
            tileCompression = Compression.of(u8(98)),
            tileType = TileType.of(u8(99)),
            minZoom = u8(100), maxZoom = u8(101),
            minLongitude = i32(102) / 1e7, minLatitude = i32(106) / 1e7,
            maxLongitude = i32(110) / 1e7, maxLatitude = i32(114) / 1e7,
            centerZoom = u8(118),
            centerLongitude = i32(119) / 1e7, centerLatitude = i32(123) / 1e7,
        )
    }

    fun writeHeader(header: Header): ByteArray {
        val out = ByteArray(HEADER_LENGTH)
        MAGIC.copyInto(out)
        out[7] = 3
        fun u64(at: Int, value: Long) {
            for (i in 0 until 8) out[at + i] = (value ushr (8 * i)).toByte()
        }
        fun i32(at: Int, value: Int) {
            for (i in 0 until 4) out[at + i] = (value ushr (8 * i)).toByte()
        }
        fun e7(value: Double): Int = kotlin.math.round(value * 1e7).toInt()
        with(header) {
            u64(8, rootOffset); u64(16, rootLength)
            u64(24, metadataOffset); u64(32, metadataLength)
            u64(40, leafOffset); u64(48, leafLength)
            u64(56, tileDataOffset); u64(64, tileDataLength)
            u64(72, addressedTiles); u64(80, tileEntries); u64(88, tileContents)
            out[96] = if (clustered) 1 else 0
            out[97] = internalCompression.code.toByte()
            out[98] = tileCompression.code.toByte()
            out[99] = tileType.code.toByte()
            out[100] = minZoom.toByte(); out[101] = maxZoom.toByte()
            i32(102, e7(minLongitude)); i32(106, e7(minLatitude))
            i32(110, e7(maxLongitude)); i32(114, e7(maxLatitude))
            out[118] = centerZoom.toByte()
            i32(119, e7(centerLongitude)); i32(123, e7(centerLatitude))
        }
        return out
    }

    /** A directory, uncompressed: the count, then the id deltas, run lengths, lengths and offsets. */
    fun encodeDirectory(entries: List<Entry>): ByteArray {
        val out = VarintWriter()
        out.write(entries.size.toLong())
        var last = 0L
        for (e in entries) {
            out.write(e.tileId - last)
            last = e.tileId
        }
        for (e in entries) out.write(e.runLength.toLong())
        for (e in entries) out.write(e.length.toLong())
        entries.forEachIndexed { i, e ->
            val previous = if (i > 0) entries[i - 1] else null
            if (previous != null && e.offset == previous.offset + previous.length) out.write(0) else out.write(e.offset + 1)
        }
        return out.toByteArray()
    }

    fun decodeDirectory(bytes: ByteArray): List<Entry> {
        val reader = VarintReader(bytes)
        val count = reader.read().toInt()
        require(count in 0..10_000_000) { "invalid directory" }
        val ids = LongArray(count)
        var last = 0L
        for (i in 0 until count) {
            last += reader.read()
            ids[i] = last
        }
        val runs = IntArray(count) { reader.read().toInt() }
        val lengths = IntArray(count) { reader.read().toInt() }
        val offsets = LongArray(count)
        for (i in 0 until count) {
            val v = reader.read()
            offsets[i] = if (v == 0L && i > 0) offsets[i - 1] + lengths[i - 1] else v - 1
        }
        return List(count) { Entry(ids[it], offsets[it], lengths[it], runs[it]) }
    }

    /** The entry that holds [tileId] in a sorted directory (or the leaf that may hold it), or null. */
    fun find(entries: List<Entry>, tileId: Long): Entry? {
        var low = 0
        var high = entries.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val c = entries[middle].tileId
            when {
                c < tileId -> low = middle + 1
                c > tileId -> high = middle - 1
                else -> return entries[middle]
            }
        }
        // The entry just before: a run that may cover the tile, or the leaf directory that does.
        if (high >= 0) {
            val e = entries[high]
            if (e.runLength == 0) return e
            if (tileId - e.tileId < e.runLength) return e
        }
        return null
    }

    private class VarintWriter {
        private var buffer = ByteArray(256)
        private var size = 0

        fun write(value: Long) {
            require(value >= 0) { "negative varint" }
            var v = value
            while (true) {
                if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
                if (v < 0x80) {
                    buffer[size++] = v.toByte()
                    return
                }
                buffer[size++] = ((v and 0x7F) or 0x80).toByte()
                v = v ushr 7
            }
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }

    private class VarintReader(private val bytes: ByteArray) {
        private var position = 0

        fun read(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(position < bytes.size && shift < 64) { "invalid directory" }
                val b = bytes[position++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) return result
                shift += 7
            }
        }
    }
}

/** Reads byte ranges of a PMTiles file: the bundled bytes, or our server by HTTP range requests. */
fun interface RangeSource {
    /** [length] bytes from [offset]; fewer at the end of the file; null when the source cannot be read. */
    suspend fun read(offset: Long, length: Int): ByteArray?
}

/** A whole file in memory (the bundled background, read once from the application's resources). */
class ByteArrayRangeSource(private val bytes: ByteArray) : RangeSource {
    override suspend fun read(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || offset > bytes.size || length < 0) return null
        val end = minOf(bytes.size.toLong(), offset + length).toInt()
        return bytes.copyOfRange(offset.toInt(), end)
    }
}

/**
 * Reads the tiles of a PMTiles file. The header and the root directory are read once (the first
 * 16 KiB); leaf directories are kept in a small cache. Tiles are returned uncompressed: the
 * renderer receives plain vector tiles whatever the file holds.
 *
 * @param maxCachedLeaves how many leaf directories are kept in memory
 */
class PmtilesReader(private val source: RangeSource, private val maxCachedLeaves: Int = 16) {
    // The map engine asks for tiles from several threads at once: the header and the cache of leaf
    // directories are guarded, the reads of the source are not (a slow network read never blocks
    // the tiles already known).
    private val lock = Mutex()
    private var header: Pmtiles.Header? = null
    private var root: List<Pmtiles.Entry>? = null
    private val leaves = LinkedHashMap<Long, List<Pmtiles.Entry>>()

    /** The header, read on first use; null when the file cannot be read or is not a PMTiles file. */
    suspend fun header(): Pmtiles.Header? = lock.withLock {
        header?.let { return@withLock it }
        val start = source.read(0, Pmtiles.ROOT_LENGTH_MAX) ?: return@withLock null
        val parsed = runCatching { Pmtiles.readHeader(start) }.getOrNull() ?: return@withLock null
        val rootBytes = if (parsed.rootOffset + parsed.rootLength <= start.size) {
            start.copyOfRange(parsed.rootOffset.toInt(), (parsed.rootOffset + parsed.rootLength).toInt())
        } else {
            source.read(parsed.rootOffset, parsed.rootLength.toInt()) ?: return@withLock null
        }
        root = directory(rootBytes, parsed) ?: return@withLock null
        header = parsed
        parsed
    }

    /** The tile z/x/y, uncompressed; null when the file has no such tile (or cannot be read). */
    suspend fun tile(z: Int, x: Int, y: Int): ByteArray? {
        val h = header() ?: return null
        if (z < h.minZoom || z > h.maxZoom) return null
        if (x < 0 || y < 0 || x >= (1 shl z) || y >= (1 shl z)) return null
        val id = Pmtiles.tileId(z, x, y)
        var entries = lock.withLock { root } ?: return null
        repeat(4) {
            val entry = Pmtiles.find(entries, id) ?: return null
            if (entry.runLength > 0) {
                val data = source.read(h.tileDataOffset + entry.offset, entry.length) ?: return null
                if (data.size != entry.length) return null
                return when (h.tileCompression) {
                    Pmtiles.Compression.NONE, Pmtiles.Compression.UNKNOWN -> data
                    Pmtiles.Compression.GZIP -> Gzip.decompress(data)
                    else -> null
                }
            }
            entries = leaf(h, entry) ?: return null
        }
        return null
    }

    private suspend fun leaf(h: Pmtiles.Header, entry: Pmtiles.Entry): List<Pmtiles.Entry>? {
        val at = h.leafOffset + entry.offset
        lock.withLock { leaves[at] }?.let { return it }
        val bytes = source.read(at, entry.length) ?: return null
        val entries = directory(bytes, h) ?: return null
        lock.withLock {
            leaves[at] = entries
            while (leaves.size > maxCachedLeaves) leaves.remove(leaves.keys.first())
        }
        return entries
    }

    private fun directory(bytes: ByteArray, h: Pmtiles.Header): List<Pmtiles.Entry>? {
        val plain = when (h.internalCompression) {
            Pmtiles.Compression.NONE, Pmtiles.Compression.UNKNOWN -> bytes
            Pmtiles.Compression.GZIP -> Gzip.decompress(bytes) ?: return null
            else -> return null
        }
        return runCatching { Pmtiles.decodeDirectory(plain) }.getOrNull()
    }
}

/**
 * Writes a PMTiles file from tiles given in any order (used by the data preparation tool and the
 * tests). Identical tiles (the open sea, for instance) are stored once. Directories are written
 * uncompressed, so that every reader can use them; tiles are stored as given, compressed or not
 * as [tileCompression] says.
 */
class PmtilesWriter(
    private val tileCompression: Pmtiles.Compression,
    private val tileType: Pmtiles.TileType = Pmtiles.TileType.MVT,
) {
    private val tiles = HashMap<Long, ByteArray>()

    fun add(z: Int, x: Int, y: Int, data: ByteArray) {
        tiles[Pmtiles.tileId(z, x, y)] = data
    }

    val size: Int get() = tiles.size

    /**
     * The bytes of the file. [bounds] and [center] describe the area; [metadata] is a JSON object
     * (attribution, layers), stored uncompressed.
     */
    fun build(bounds: GeoBox, center: GeoPoint, centerZoom: Int, metadata: String): ByteArray {
        require(tiles.isNotEmpty()) { "no tile" }
        val ids = tiles.keys.sorted()
        val data = ByteSink()
        // Identical contents are stored once: buckets by hash, then compared byte by byte.
        val seen = HashMap<Int, MutableList<Triple<ByteArray, Long, Int>>>()
        val entries = ArrayList<Pmtiles.Entry>()
        var contents = 0L
        for (id in ids) {
            val bytes = tiles.getValue(id)
            val bucket = seen.getOrPut(bytes.contentHashCode()) { ArrayList(1) }
            val known = bucket.firstOrNull { it.first.contentEquals(bytes) }
            val offset: Long
            val length: Int
            if (known != null) {
                offset = known.second
                length = known.third
            } else {
                contents++
                offset = data.size.toLong()
                length = bytes.size
                data.write(bytes)
                bucket += Triple(bytes, offset, length)
            }
            val last = entries.lastOrNull()
            if (last != null && last.offset == offset && last.length == length && last.tileId + last.runLength == id) {
                entries[entries.size - 1] = last.copy(runLength = last.runLength + 1)
            } else {
                entries += Pmtiles.Entry(id, offset, length, 1)
            }
        }
        val (rootBytes, leafBytes) = directories(entries)
        val metadataBytes = metadata.encodeToByteArray()
        val rootOffset = Pmtiles.HEADER_LENGTH.toLong()
        val metadataOffset = rootOffset + rootBytes.size
        val leafOffset = metadataOffset + metadataBytes.size
        val tileDataOffset = leafOffset + leafBytes.size
        val zooms = ids.map { Pmtiles.zxy(it).first }
        val header = Pmtiles.Header(
            rootOffset = rootOffset, rootLength = rootBytes.size.toLong(),
            metadataOffset = metadataOffset, metadataLength = metadataBytes.size.toLong(),
            leafOffset = leafOffset, leafLength = leafBytes.size.toLong(),
            tileDataOffset = tileDataOffset, tileDataLength = data.size.toLong(),
            addressedTiles = ids.size.toLong(), tileEntries = entries.size.toLong(), tileContents = contents,
            clustered = true,
            internalCompression = Pmtiles.Compression.NONE,
            tileCompression = tileCompression,
            tileType = tileType,
            minZoom = zooms.min(), maxZoom = zooms.max(),
            minLongitude = bounds.west, minLatitude = bounds.south,
            maxLongitude = bounds.east, maxLatitude = bounds.north,
            centerZoom = centerZoom, centerLongitude = center.longitude, centerLatitude = center.latitude,
        )
        val out = ByteSink()
        out.write(Pmtiles.writeHeader(header))
        out.write(rootBytes)
        out.write(metadataBytes)
        out.write(leafBytes)
        out.write(data.toByteArray())
        return out.toByteArray()
    }

    /** The root directory alone when it fits in the first 16 KiB, otherwise a root of leaf directories. */
    private fun directories(entries: List<Pmtiles.Entry>): Pair<ByteArray, ByteArray> {
        val single = Pmtiles.encodeDirectory(entries)
        if (single.size + Pmtiles.HEADER_LENGTH <= Pmtiles.ROOT_LENGTH_MAX) return single to ByteArray(0)
        var leafSize = 4_096
        while (true) {
            val leaves = ByteSink()
            val rootEntries = entries.chunked(leafSize).map { chunk ->
                val bytes = Pmtiles.encodeDirectory(chunk)
                val at = leaves.size.toLong()
                leaves.write(bytes)
                Pmtiles.Entry(chunk.first().tileId, at, bytes.size, 0)
            }
            val root = Pmtiles.encodeDirectory(rootEntries)
            if (root.size + Pmtiles.HEADER_LENGTH <= Pmtiles.ROOT_LENGTH_MAX) return root to leaves.toByteArray()
            leafSize *= 2
        }
    }

    private class ByteSink {
        private var buffer = ByteArray(1 shl 16)
        var size = 0
            private set

        fun write(bytes: ByteArray) {
            if (size + bytes.size > buffer.size) {
                var next = buffer.size
                while (size + bytes.size > next) next *= 2
                buffer = buffer.copyOf(next)
            }
            bytes.copyInto(buffer, size)
            size += bytes.size
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }
}
