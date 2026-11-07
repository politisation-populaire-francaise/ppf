package org.politisationpopulairefrancaise.server.tiles

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where the PMTiles files of the map backgrounds are (`PPF_TILES_DIR`), for the development server
 * and the preproduction without a reverse proxy. In production the reverse proxy (Caddy
 * `file_server`) serves the same files itself; without the variable, this route does not exist.
 */
class TilesConfig(val directory: Path?) {
    companion object {
        fun fromEnvironment(): TilesConfig =
            TilesConfig(System.getenv("PPF_TILES_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it).toAbsolutePath().normalize() })
    }
}

/** At most this many bytes in one answer: a tile or a directory is a few hundred kilobytes. */
private const val MAX_RANGE: Long = 8L * 1024 * 1024

private val FILE_NAME = Regex("^[a-z0-9][a-z0-9-]{0,62}\\.pmtiles$")

/**
 * `GET /tiles/<name>.pmtiles` with an HTTP range (RFC 9110, section 14): what the application reads
 * of the detailed background, a byte range at a time (`HttpRangeSource`). One range per request,
 * of [MAX_RANGE] at most; nothing is logged (the ranges asked tell which part of the map is looked
 * at, see THREAT_MODEL.md). A request without a range is refused: no client of ours asks for a
 * whole file, which may weigh a gigabyte.
 */
fun Route.tilesRoutes(config: TilesConfig) {
    val directory = config.directory ?: return
    get("/tiles/{name}") {
        val name = call.parameters["name"].orEmpty()
        val file = directory.resolve(name).normalize()
        if (!FILE_NAME.matches(name) || !file.startsWith(directory) || !Files.isRegularFile(file)) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val size = Files.size(file)
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        // The files change only when the data is prepared again (a new name then); a day of cache is safe.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
        val range = parseRange(call.request.headers[HttpHeaders.Range], size)
        when {
            range == null -> call.respond(HttpStatusCode.BadRequest)
            range.isEmpty() -> {
                call.response.header(HttpHeaders.ContentRange, "bytes */$size")
                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            }
            else -> {
                val bytes = RandomAccessFile(file.toFile(), "r").use { raf ->
                    ByteArray((range.last - range.first + 1).toInt()).also {
                        raf.seek(range.first)
                        raf.readFully(it)
                    }
                }
                call.response.header(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/$size")
                call.respondBytes(bytes, ContentType.Application.OctetStream, HttpStatusCode.PartialContent)
            }
        }
    }
}

/**
 * The single range of a `Range` header, within a file of [size] bytes: `bytes=10-20`, `bytes=10-`
 * or `bytes=-20` (the last 20 bytes). Null for a missing, malformed or multiple range, or one over
 * [MAX_RANGE]; an empty range when it starts beyond the end of the file (answered 416).
 */
internal fun parseRange(header: String?, size: Long): LongRange? {
    val spec = header?.trim()?.takeIf { it.startsWith("bytes=") }?.removePrefix("bytes=")?.trim() ?: return null
    if (',' in spec) return null
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val startText = spec.substring(0, dash).trim()
    val endText = spec.substring(dash + 1).trim()
    val range = when {
        startText.isEmpty() -> {
            val suffix = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
            maxOf(0, size - suffix) until size
        }
        else -> {
            val start = startText.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            if (start >= size) return LongRange.EMPTY
            val end = if (endText.isEmpty()) size - 1 else (endText.toLongOrNull()?.takeIf { it >= start } ?: return null)
            start..minOf(end, size - 1)
        }
    }
    if (range.isEmpty()) return LongRange.EMPTY
    return range.takeIf { it.last - it.first + 1 <= MAX_RANGE }
}
