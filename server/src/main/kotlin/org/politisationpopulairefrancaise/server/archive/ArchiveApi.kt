package org.politisationpopulairefrancaise.server.archive

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.server.api.ApiError
import java.util.Base64

/** The answer of `GET /api/v1/archives/{sha256}` (docs/FORMATS.md, section 5). */
@Serializable
data class ArchiveProofView(
    /** The digest, 64 lowercase hexadecimal characters. */
    val digest: String,
    /** `queued`, `pending`, `anchored` or `verified`. */
    val state: String,
    /** The `.ots` proof in base64; null while the digest is queued. */
    val ots: String?,
    /** The Bitcoin block, only once verified against two explorers. */
    val bitcoin: BitcoinView?,
    /** The RFC 3161 token of the batch root, once received and checked. */
    val rfc3161: Rfc3161View?,
)

@Serializable
data class BitcoinView(val height: Long, val blockHash: String, val time: Long)

@Serializable
data class Rfc3161View(val token: String, val time: Long, val tsaCertSha256: String)

/**
 * The proofs of the timestamped archive, public and without account (D-111):
 *
 * - GET /api/v1/archives/{sha256}: where the proof of a digest stands, as [ArchiveProofView];
 * - GET /api/v1/archives/{sha256}.ots: the proof itself, `application/octet-stream`, readable by the
 *   official client (`ots verify`) and by `OtsFile.read`.
 *
 * `400 bad_parameter` for anything but 64 hexadecimal characters; `404 not_found` for an unknown
 * digest (and for the `.ots` of a digest still queued). Nothing about the request is kept.
 */
fun Route.archivesApi(archives: ArchiveService?) {
    // Without a database the 503 answer of eventsApi covers every /api/v1/ address.
    if (archives == null) return
    get("/api/v1/archives/{name}") {
        val name = call.parameters["name"].orEmpty()
        val binary = name.endsWith(OTS_SUFFIX)
        val hex = name.removeSuffix(OTS_SUFFIX).lowercase()
        if (!SHA256_HEX.matches(hex)) {
            return@get call.respond(HttpStatusCode.BadRequest, ApiError("bad_parameter", "A SHA-256 digest is 64 hexadecimal characters."))
        }
        val proof = withContext(Dispatchers.IO) { archives.find(hex.hexToByteArray()) }
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("not_found", "No proof for this digest."))
        // A proof changes until it is verified: any cache may keep it, provided it checks it is current.
        call.response.header(HttpHeaders.CacheControl, "public, no-cache")
        if (binary) {
            val ots = proof.ots ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("not_found", "This digest is not stamped yet."))
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "$hex$OTS_SUFFIX").toString(),
            )
            call.respondBytes(ots, ContentType.Application.OctetStream)
        } else {
            call.respond(view(hex, proof))
        }
    }
}

private fun view(hex: String, proof: StoredProof): ArchiveProofView {
    val base64 = Base64.getEncoder()
    return ArchiveProofView(
        digest = hex,
        state = proof.state.code,
        ots = proof.ots?.let(base64::encodeToString),
        bitcoin = proof.bitcoin?.let { BitcoinView(it.height, it.blockHash, it.time) },
        rfc3161 = proof.rfc3161?.let { Rfc3161View(base64.encodeToString(it.token), it.time.epochSecond, it.tsaCertSha256.toHexString()) },
    )
}

private const val OTS_SUFFIX = ".ots"
private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
