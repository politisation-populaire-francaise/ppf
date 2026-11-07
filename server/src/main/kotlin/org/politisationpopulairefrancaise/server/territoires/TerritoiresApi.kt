package org.politisationpopulairefrancaise.server.territoires

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.elus.acceptsGzip
import org.politisationpopulairefrancaise.server.elus.ifNoneMatch
import java.util.zip.GZIPInputStream

/**
 * The referential of territories, read only, without account (docs/FORMATS.md, section 4 nonies):
 *
 * - GET /api/v1/territoires: the national level (regions, departments, collectivities, overseas,
 *   and the index of the packages);
 * - GET /api/v1/territoires/{code}: the package of a department or overseas collectivity, or
 *   `communes` (the names of every commune); `404 unknown_package` otherwise.
 *
 * Same HTTP rules as the directory of elected officials: `ETag: "<sha256>"`, `304`, gzip as stored,
 * `503 territoires_unavailable` until the first import is served. Nothing about the request is kept.
 */
fun Route.territoiresApi(territoires: TerritoiresService?) {
    if (territoires == null) return
    get("/api/v1/territoires") {
        val served = territoires.current() ?: return@get call.unavailable()
        call.answer(served.nationalSha256, served.nationalGzip) { served.national }
    }
    get("/api/v1/territoires/{code}") {
        val code = call.parameters["code"].orEmpty()
        val served = territoires.current() ?: return@get call.unavailable()
        val sha256 = served.packages[code] ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_package", "No package for this code."))
        if (ifNoneMatch(call.request.headers[HttpHeaders.IfNoneMatch], sha256)) return@get call.notModified(sha256)
        val gzip = withContext(Dispatchers.IO) { territoires.packageGzip(code) }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_package", "No package for this code."))
        call.answer(sha256, gzip) { GZIPInputStream(gzip.inputStream()).use { it.readAllBytes() } }
    }
}

private suspend fun ApplicationCall.unavailable() {
    response.header(HttpHeaders.RetryAfter, "60")
    respond(HttpStatusCode.ServiceUnavailable, ApiError("territoires_unavailable", "The referential of territories is being imported; try again later."))
}

private suspend fun ApplicationCall.notModified(sha256: String) {
    headers(sha256)
    respond(HttpStatusCode.NotModified)
}

private suspend fun ApplicationCall.answer(sha256: String, gzip: ByteArray, plain: () -> ByteArray) {
    if (ifNoneMatch(request.headers[HttpHeaders.IfNoneMatch], sha256)) return notModified(sha256)
    headers(sha256)
    if (acceptsGzip(request.headers[HttpHeaders.AcceptEncoding])) respond(Gzipped(gzip)) else respondBytes(plain(), JSON_UTF8)
}

private fun ApplicationCall.headers(sha256: String) {
    response.header(HttpHeaders.ETag, "\"$sha256\"")
    response.header(HttpHeaders.CacheControl, "public, no-cache")
    response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
}

private val JSON_UTF8: ContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

private class Gzipped(private val body: ByteArray) : OutgoingContent.ByteArrayContent() {
    override val contentType: ContentType = JSON_UTF8
    override val contentLength: Long = body.size.toLong()
    override val headers: Headers = headersOf(HttpHeaders.ContentEncoding, "gzip")

    override fun bytes(): ByteArray = body
}
