package org.politisationpopulairefrancaise.server.elus

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
import java.util.zip.GZIPInputStream

/**
 * The directory of elected officials, read only, without account (contract, section 1.4):
 *
 * - GET /api/v1/elus: the index (packages, their SHA-256, size and postal codes);
 * - GET /api/v1/elus/{code}: the package of a department, of an overseas collectivity, or `FR`;
 *   `404 unknown_package` otherwise.
 *
 * `ETag: "<sha256>"` (the SHA-256 of the exact uncompressed body, between quotes as HTTP wants);
 * `If-None-Match` with that value, quoted or not, answers `304`. The body is sent compressed with
 * gzip to a client that accepts it, as stored. `503 elus_unavailable` until the first import is served.
 * Nothing about the request is kept: the server does not know who downloads which department.
 */
fun Route.elusApi(elus: ElusService?) {
    // Without a database the 503 answer of eventsApi covers every /api/v1/ address.
    if (elus == null) return
    get("/api/v1/elus") {
        val served = elus.current() ?: return@get call.unavailable()
        call.answerJson(served.served.indexSha256, served.indexGzip) { served.served.index }
    }
    get("/api/v1/elus/{code}") {
        val code = call.parameters["code"].orEmpty()
        val served = elus.current() ?: return@get call.unavailable()
        val sha256 = served.served.packages[code]
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_package", "No package for this code."))
        if (call.matches(sha256)) return@get call.notModified(sha256)
        val gzip = withContext(Dispatchers.IO) { elus.packageGzip(code) }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("unknown_package", "No package for this code."))
        call.answerJson(sha256, gzip) { GZIPInputStream(gzip.inputStream()).use { it.readAllBytes() } }
    }
}

private suspend fun ApplicationCall.unavailable() {
    response.header(HttpHeaders.RetryAfter, "60")
    respond(HttpStatusCode.ServiceUnavailable, ApiError("elus_unavailable", "The directory of elected officials is being imported; try again later."))
}

private suspend fun ApplicationCall.notModified(sha256: String) {
    headers(sha256)
    respond(HttpStatusCode.NotModified)
}

private suspend fun ApplicationCall.answerJson(sha256: String, gzip: ByteArray, plain: () -> ByteArray) {
    if (matches(sha256)) return notModified(sha256)
    headers(sha256)
    if (acceptsGzip(request.headers[HttpHeaders.AcceptEncoding])) {
        respond(GzipJson(gzip))
    } else {
        respondBytes(plain(), JSON_UTF8)
    }
}

private fun ApplicationCall.headers(sha256: String) {
    response.header(HttpHeaders.ETag, "\"$sha256\"")
    // Public data, the same for everyone: any cache may keep it, provided it checks it is current.
    response.header(HttpHeaders.CacheControl, "public, no-cache")
    response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
}

private fun ApplicationCall.matches(sha256: String): Boolean = ifNoneMatch(request.headers[HttpHeaders.IfNoneMatch], sha256)

/** Whether an If-None-Match value names [sha256] (quoted or not, weak or not) or is `*`. */
internal fun ifNoneMatch(header: String?, sha256: String): Boolean {
    if (header.isNullOrBlank()) return false
    return header.split(',').any { raw ->
        val tag = raw.trim().removePrefix("W/").trim().removeSurrounding("\"")
        tag == "*" || tag == sha256
    }
}

/** Whether an Accept-Encoding value accepts gzip (by name or `*`, with a weight above zero). */
internal fun acceptsGzip(header: String?): Boolean {
    if (header.isNullOrBlank()) return false
    return header.split(',').any { raw ->
        val parts = raw.split(';').map { it.trim() }
        val name = parts.first().lowercase()
        val weight = parts.drop(1).firstOrNull { it.startsWith("q=") }?.removePrefix("q=")?.toDoubleOrNull() ?: 1.0
        (name == "gzip" || name == "x-gzip" || name == "*") && weight > 0.0
    }
}

private val JSON_UTF8: ContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

/** A stored gzip body sent as it is, with its encoding. */
private class GzipJson(private val body: ByteArray) : OutgoingContent.ByteArrayContent() {
    override val contentType: ContentType = JSON_UTF8
    override val contentLength: Long = body.size.toLong()
    override val headers: Headers = headersOf(HttpHeaders.ContentEncoding, "gzip")

    override fun bytes(): ByteArray = body
}
