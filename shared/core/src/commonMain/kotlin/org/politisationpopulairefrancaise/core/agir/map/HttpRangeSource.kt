package org.politisationpopulairefrancaise.core.agir.map

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException

/**
 * Reads a PMTiles file of our own server by HTTP range requests (RFC 9110, section 14): the
 * detailed background of France is a plain file served by Caddy (`file_server`) or by the
 * development route `/tiles/`, without a tile server. Only this URL is ever asked for, and the
 * request carries nothing but the range: no cookie, no identifier, no position. The server sees
 * which part of the map is looked at, which is why it keeps no log (THREAT_MODEL.md).
 *
 * @param url the address of the file on our server, such as `https://<domaine>/tiles/france.pmtiles`
 */
class HttpRangeSource(private val client: HttpClient, private val url: String) : RangeSource {
    init {
        require(url.startsWith("https://") || url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) {
            "the tiles are read from our server over HTTPS (plain HTTP only on this computer)"
        }
    }

    override suspend fun read(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length <= 0) return null
        return try {
            // Streamed: the body is read only for a partial answer. A server that ignored the range
            // would send the whole file (hundreds of megabytes on a phone): any answer but 206 counts
            // as unavailable and its body is never downloaded.
            client.prepareGet(url) { header(HttpHeaders.Range, "bytes=$offset-${offset + length - 1}") }.execute { response ->
                if (response.status.value != 206) null else response.readRawBytes().takeIf { it.size <= length }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
