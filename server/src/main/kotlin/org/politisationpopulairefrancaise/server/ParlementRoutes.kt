package org.politisationpopulairefrancaise.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndex
import org.politisationpopulairefrancaise.server.api.ApiError
import java.nio.file.Files
import java.nio.file.Path

/*
 * The packages of parliamentary open data (contract docs/integration/voir-donnees-ouvertes.md,
 * section 3; D-064). They are written by the separate import tool (tools/open-data-importer), which
 * alone holds the import key, into the folder PPF_OPEN_DATA_DIR; the server only reads that folder
 * (mounted read-only in production) and never signs anything. Without the variable, the routes do
 * not exist. Nothing is logged: the server only learns that some device fetched a package.
 * Compression is done by the reverse proxy (Caddy `encode zstd gzip`), as for every answer.
 */

/** Where the packages are (`PPF_OPEN_DATA_DIR`). */
class OpenDataConfig(val directory: Path?) {
    companion object {
        const val VARIABLE: String = "PPF_OPEN_DATA_DIR"
        const val INDEX_FILE: String = "index.event.json"

        fun fromEnvironment(): OpenDataConfig =
            OpenDataConfig(System.getenv(VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it).toAbsolutePath().normalize() })
    }
}

/** The biggest file served: a month of ballots weighs a few megabytes. */
private const val MAX_FILE_BYTES: Long = 64L * 1024 * 1024

/**
 * `GET /api/v1/parlement`: the signed index event, as the import tool wrote it.
 * `GET /api/v1/parlement/{name}`: one package named by the current index. Both answer with
 * `ETag` = the SHA-256 of the bytes and `304` to a matching `If-None-Match`; an unknown name is
 * `404 unknown_package`, and only names of the index are ever looked up on disk (no path is built
 * from anything else).
 */
fun Route.parlementRoutes(config: OpenDataConfig = OpenDataConfig.fromEnvironment()) {
    val directory = config.directory ?: return
    get("/api/v1/parlement") {
        val bytes = withContext(Dispatchers.IO) { readFile(directory.resolve(OpenDataConfig.INDEX_FILE)) }
            ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ApiError("no_open_data", "No package of open data has been imported yet."))
        call.answer(bytes)
    }
    get("/api/v1/parlement/{name}") {
        val name = call.parameters["name"].orEmpty()
        val bytes = if (ParliamentFormat.isPackageName(name)) withContext(Dispatchers.IO) { readPackage(directory, name) } else null
        if (bytes == null) return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_package", "No package with this name."))
        call.answer(bytes)
    }
}

private fun readFile(file: Path): ByteArray? {
    if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return null
    return runCatching { Files.readAllBytes(file) }.getOrNull()
}

/** The bytes of the package [name] of the current index (the checks of [readPackage]); null when absent. */
internal fun readOpenDataPackage(directory: Path, name: String): ByteArray? =
    if (ParliamentFormat.isPackageName(name)) readPackage(directory, name) else null

/** A package of the current index only; the file name is the checked package name. */
private fun readPackage(directory: Path, name: String): ByteArray? {
    val index = readFile(directory.resolve(OpenDataConfig.INDEX_FILE))?.let { bytes ->
        runCatching { ParliamentFormat.json.decodeFromString(ParliamentIndex.serializer(), SignedEvent.fromJson(bytes.decodeToString()).content) }.getOrNull()
    } ?: return null
    if (index.packageRef(name) == null) return null
    val file = directory.resolve("$name.json").normalize()
    if (!file.startsWith(directory)) return null
    return readFile(file)
}

private suspend fun ApplicationCall.answer(bytes: ByteArray) {
    val tag = ParliamentFormat.sha256Hex(bytes)
    response.header(HttpHeaders.ETag, "\"$tag\"")
    // Always revalidated: an unchanged file costs a 304 and no bytes.
    response.header(HttpHeaders.CacheControl, "public, no-cache")
    val asked = request.headers[HttpHeaders.IfNoneMatch]
    if (asked != null && etagMatches(asked, tag)) return respond(HttpStatusCode.NotModified)
    respondBytes(bytes, ContentType.Application.Json, HttpStatusCode.OK)
}

/** `If-None-Match`: a list of tags, quoted or not, weak or not, or `*`. */
internal fun etagMatches(header: String, tag: String): Boolean = header.split(',').map { it.trim() }.any { value ->
    value == "*" || value.removePrefix("W/").trim('"') == tag
}
