package org.politisationpopulairefrancaise.server.elus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.LocalDate
import java.util.Comparator

/** A file could not be downloaded. The message names the host or the status, never a value of a file. */
class ElusDownloadException(message: String) : Exception(message)

/** What the official source needs from the network: an API answer as text, a file saved to disk. */
interface ElusHttp {
    fun text(url: String, maxBytes: Long): String

    fun download(url: String, target: Path, maxBytes: Long)
}

/**
 * HTTPS only, towards [allowedHosts] only: redirections are followed here, one by one, and a
 * redirection to any other host is refused. No cookie, no client identifier besides a plain
 * User-Agent; nothing about the readers of PPF is ever sent.
 */
class JdkElusHttp(
    private val allowedHosts: Set<String> = OfficialSource.HOSTS,
    private val timeout: Duration = Duration.ofMinutes(10),
    /** Value of the Accept header, for the APIs that require one (the Insee answers 406 without it). */
    private val accept: String? = null,
) : ElusHttp {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    override fun text(url: String, maxBytes: Long): String = fetch(url) { body ->
        val bytes = body.readNBytes((maxBytes + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (bytes.size > maxBytes) throw ElusDownloadException("The answer of ${URI(url).host} is larger than $maxBytes bytes.")
        bytes.toString(Charsets.UTF_8)
    }

    override fun download(url: String, target: Path, maxBytes: Long): Unit = fetch(url) { body ->
        val partial = target.resolveSibling(target.fileName.toString() + ".part")
        Files.newOutputStream(partial).use { out ->
            val buffer = ByteArray(1 shl 16)
            var total = 0L
            while (true) {
                val read = body.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw ElusDownloadException("A file of ${URI(url).host} is larger than $maxBytes bytes.")
                out.write(buffer, 0, read)
            }
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun <T> fetch(url: String, read: (InputStream) -> T): T {
        var uri = checked(URI(url))
        repeat(MAX_REDIRECTS + 1) {
            val request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .apply { accept?.let { header("Accept", it) } }
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val status = response.statusCode()
            if (status in REDIRECTS) {
                response.body().close()
                val location = response.headers().firstValue("Location").orElseThrow { ElusDownloadException("Redirection without address from ${uri.host}.") }
                uri = checked(uri.resolve(location))
                return@repeat
            }
            response.body().use { body ->
                if (status != 200) throw ElusDownloadException("HTTP $status from ${uri.host}.")
                return read(body)
            }
        }
        throw ElusDownloadException("Too many redirections from ${URI(url).host}.")
    }

    /** Refuses anything but HTTPS towards one of the allowed hosts. */
    internal fun checked(uri: URI): URI {
        if (uri.scheme != "https" || uri.host == null || uri.host.lowercase() !in allowedHosts || uri.userInfo != null) {
            throw ElusDownloadException("Address refused (only HTTPS towards ${allowedHosts.sorted().joinToString()}).")
        }
        return uri
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val USER_AGENT = "PPF-server (annuaire des elus; https://politisation-populaire-francaise.org)"
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * The real directory, downloaded from data.gouv.fr (production and preproduction, PPF_RNE_SOURCE=official): the
 * files of docs/integration/elus-serveur.md, section 1, saved in a temporary directory deleted by
 * [close]. The files of the RNE, of La Poste and of the elections have stable resource ids; those
 * of the Code officiel géographique change every year and are found in the API of their dataset.
 */
class OfficialSource(private val http: ElusHttp = JdkElusHttp()) : ElusSource {
    override val example: Boolean = false
    override val kind: String = ElusSources.OFFICIAL

    private var directory: Path? = null
    private val files = HashMap<ElusFile, Path>()

    override fun prepare(): LocalDate {
        val dir = Files.createTempDirectory("ppf-rne-")
        directory = dir
        val rne = dataset(RNE_DATASET)
        val updatedOn = rne["last_update"]?.jsonPrimitive?.contentOrNull?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: throw ElusDownloadException("The dataset of the RNE gives no valid date of update.")
        val rneResources = resources(rne)
        for ((file, id) in RNE_RESOURCES) {
            val url = rneResources.firstOrNull { it.id == id }?.url ?: throw ElusDownloadException("The resource of ${file.fileName} is no longer in the dataset of the RNE.")
            save(file, url)
        }
        for ((file, url) in cogUrls(resources(dataset(COG_DATASET)))) save(file, url)
        save(ElusFile.POSTAL_CODES, stable(POSTAL_CODES_RESOURCE))
        save(ElusFile.LEGISLATIVES, stable(LEGISLATIVES_RESOURCE))
        save(ElusFile.DEPARTEMENTALES, stable(DEPARTEMENTALES_RESOURCE))
        return updatedOn
    }

    override fun open(file: ElusFile): InputStream =
        Files.newInputStream(files[file] ?: throw ElusDownloadException("${file.fileName} was not downloaded."))

    override fun close() {
        directory?.let { dir ->
            Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
        directory = null
        files.clear()
    }

    private fun save(file: ElusFile, url: String) {
        val target = (directory ?: throw IllegalStateException("prepare() first")).resolve(file.fileName)
        http.download(url, target, MAX_FILE_BYTES)
        files[file] = target
    }

    private fun dataset(id: String): JsonObject =
        ElusJson.parseToJsonElement(http.text("$API/datasets/$id/", MAX_API_BYTES)) as? JsonObject
            ?: throw ElusDownloadException("The dataset $id is not described as expected.")

    data class Resource(val id: String, val url: String)

    companion object {
        /** The hosts contacted in official mode (docs/integration/elus-serveur.md, section 2). */
        val HOSTS: Set<String> = setOf("www.data.gouv.fr", "static.data.gouv.fr", "www.insee.fr", "data.laposte.fr")

        const val API: String = "https://www.data.gouv.fr/api/1"
        const val RNE_DATASET: String = "5c34c4d1634f4173183a64f1"
        const val COG_DATASET: String = "58c984b088ee386cdb1261f3"
        const val POSTAL_CODES_RESOURCE: String = "008a2dda-2c60-4b63-b910-998f6f818089"
        const val LEGISLATIVES_RESOURCE: String = "87d3701e-4d5d-4963-89f4-b402cb1c4403"
        const val DEPARTEMENTALES_RESOURCE: String = "f659a06c-c8a2-4226-aa85-2607abe2c1e1"

        /** The largest file accepted (the municipal councillors weigh 65 MB in 2026). */
        const val MAX_FILE_BYTES: Long = 400L * 1024 * 1024
        const val MAX_API_BYTES: Long = 5L * 1024 * 1024

        val RNE_RESOURCES: Map<ElusFile, String> = linkedMapOf(
            ElusFile.MUNICIPAL to "d5f400de-ae3f-4966-8cb6-a85c70c6c24a",
            ElusFile.COMMUNAUTAIRE to "41d95d7d-b172-4636-ac44-32656367cdc7",
            ElusFile.DEPARTEMENTAL to "601ef073-d986-4582-8e1a-ed14dc857fba",
            ElusFile.REGIONAL to "430e13f9-834b-4411-a1a8-da0b4b6e715c",
            ElusFile.COLLECTIVITE to "a595be27-cfab-4810-b9d4-22e193bffe35",
            ElusFile.DEPUTE to "1ac42ff4-1336-44f8-a221-832039dbc142",
            ElusFile.SENATEUR to "b78f8945-509f-4609-a4a7-3048b8370479",
            ElusFile.EUROPEEN to "70957bb0-f19f-40c5-b97b-90b3d4d71f9e",
        )

        /** The files of the Code officiel géographique, recognised by the end of their address and their year. */
        private val COG_FILES: Map<ElusFile, Regex> = linkedMapOf(
            ElusFile.COG_COMMUNES to Regex("/v_commune_(\\d{4})\\.csv$"),
            ElusFile.COG_CANTONS to Regex("/v_canton_(\\d{4})\\.csv$"),
            ElusFile.COG_DEPARTEMENTS to Regex("/v_departement_(\\d{4})\\.csv$"),
            ElusFile.COG_REGIONS to Regex("/v_region_(\\d{4})\\.csv$"),
            ElusFile.COG_COMER to Regex("/v_comer_(\\d{4})\\.csv$"),
            ElusFile.COG_COMMUNES_COMER to Regex("/v_commune_comer_(\\d{4})\\.csv$"),
            ElusFile.COG_EVENTS to Regex("/v_mvt_commune_(\\d{4})\\.csv$"),
        )

        /** The address of a resource by its stable id: data.gouv.fr redirects to its last version. */
        fun stable(resource: String): String = "$API/datasets/r/$resource"

        fun resources(dataset: JsonObject): List<Resource> =
            (dataset["resources"] as? JsonArray).orEmpty().mapNotNull { element ->
                val resource = element as? JsonObject ?: return@mapNotNull null
                val id = resource["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = resource["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Resource(id, url)
            }

        /** The files of the most recent vintage of the Code officiel géographique that has all of them. */
        fun cogUrls(resources: List<Resource>): Map<ElusFile, String> {
            val byYear = HashMap<Int, HashMap<ElusFile, String>>()
            for (resource in resources) {
                val path = runCatching { URI(resource.url).path }.getOrNull() ?: continue
                for ((file, pattern) in COG_FILES) {
                    val year = pattern.find(path)?.groupValues?.get(1)?.toInt() ?: continue
                    byYear.getOrPut(year) { HashMap() }.putIfAbsent(file, resource.url)
                }
            }
            val year = byYear.filterValues { it.size == COG_FILES.size }.keys.maxOrNull()
                ?: throw ElusDownloadException("No vintage of the Code officiel géographique has all its files.")
            return COG_FILES.keys.associateWith { byYear.getValue(year).getValue(it) }
        }
    }
}
