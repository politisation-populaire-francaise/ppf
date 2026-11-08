package org.politisationpopulairefrancaise.server.export

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.elus.ifNoneMatch
import org.politisationpopulairefrancaise.web.OpenDataFileView
import org.politisationpopulairefrancaise.web.OpenDataSetView
import org.politisationpopulairefrancaise.web.OpenDataSource
import org.politisationpopulairefrancaise.web.OpenDataView

/**
 * The open export, read only, without account (M34, S-48; docs/FORMATS.md section 5 bis):
 *
 * - `GET /api/v1/export`: the catalog (licence, editorial keys, datasets and their files);
 * - `GET /api/v1/export/<file>`: `contenus.json` (the signed contents) and `<dataset>.json` / `.csv`;
 * - `GET /api/v1/export/schemas/<dataset>-<version>.schema.json`: the JSON Schema of a dataset.
 *
 * `ETag: "<sha256>"` of the exact body, `If-None-Match` answers `304`; `404 unknown_file` for anything else.
 * Nothing about the request is kept. Mounted in the public port only.
 */
fun Route.exportApi(export: OpenExport?) {
    // Without a database the 503 answer of eventsApi covers every /api/v1/ address.
    if (export == null) return
    get(OpenExport.PATH) {
        val build = export.current()
        call.answer(build.catalogFile)
    }
    get("${OpenExport.PATH}/{file}") {
        val file = export.current().files[call.parameters["file"].orEmpty()] ?: return@get call.unknown()
        call.answer(file)
    }
    get("${OpenExport.PATH}/schemas/{file}") {
        val file = export.current().files["schemas/${call.parameters["file"].orEmpty()}"] ?: return@get call.unknown()
        call.answer(file)
    }
}

private suspend fun ApplicationCall.unknown() =
    respond(HttpStatusCode.NotFound, ApiError("unknown_file", "No such file in the open export; see GET /api/v1/export."))

private suspend fun ApplicationCall.answer(file: ServedFile) {
    response.header(HttpHeaders.ETag, "\"${file.sha256}\"")
    // Public data, the same for everyone: any cache may keep it, provided it checks it is current.
    response.header(HttpHeaders.CacheControl, "public, no-cache")
    file.downloadName?.let {
        response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, it).toString())
    }
    if (ifNoneMatch(request.headers[HttpHeaders.IfNoneMatch], file.sha256)) {
        respond(HttpStatusCode.NotModified)
        return
    }
    respondBytes(file.bytes, ContentType.parse(file.contentType))
}

/** The page `/donnees` of the site reads the same catalog as the API. */
fun OpenExport.pageSource(): OpenDataSource = OpenDataSource {
    val catalog = current().catalog
    OpenDataView(
        updatedAt = catalog.updatedAt,
        licenseChosen = catalog.license.status != ExportLicense.PENDING.status,
        licenseText = catalog.license.text,
        licenseUrl = catalog.license.url,
        catalogUrl = OpenExport.PATH,
        datasets = catalog.datasets.map { dataset ->
            OpenDataSetView(
                dataset.id, dataset.title, dataset.description, dataset.files.firstOrNull()?.rows ?: 0, dataset.schema,
                dataset.files.map { OpenDataFileView(it.format, it.url, it.bytes) },
            )
        },
    )
}
