package org.politisationpopulairefrancaise.tools.opendata

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
import java.time.ZoneId

/** Which data: the fictional example set bundled with the importer, or the files of the institutions. */
enum class SourceMode { EXAMPLE, OFFICIAL }

/** The source files of one import. Each call reads (or downloads) one source; [close] removes what was downloaded. */
interface ImportSources : AutoCloseable {
    val example: Boolean
    /** The day of the import, AAAA-MM-JJ. */
    val today: String
    /** `created_at` of the index event. */
    val createdAt: Long
    fun anMembers(): EntrySource
    fun anBallots(): EntrySource
    fun anDossiers(): EntrySource
    fun senators(): InputStream
    fun europarl(): EuroparlApi
    override fun close() {}
}

/**
 * The fictional example files (`src/main/resources/example/`), in the very formats of the official
 * files and read by the same parsers. The day of the import is fixed by `example/manifest.json`, so
 * that two imports give the same bytes. [accept] lets the tests leave files out (`an/scrutins/…`).
 */
class ExampleSources(private val accept: (String) -> Boolean = { true }) : ImportSources {
    override val example: Boolean = true
    override val today: String = lenientJson.parseToJsonElement(ExampleFiles.open("manifest.json").use { it.readBytes().decodeToString() })["importedOn"].day()
        ?: throw UnreadableSourceException("example manifest without importedOn")
    // Noon UTC of the day of the import, as for the editorial contents.
    override val createdAt: Long = LocalDate.parse(today).atTime(12, 0).toEpochSecond(java.time.ZoneOffset.UTC)

    private fun entries(directory: String) = ResourceEntries(directory) { accept("$directory/$it") }
    override fun anMembers(): EntrySource = entries("an/amo10")
    override fun anBallots(): EntrySource = entries("an/scrutins")
    override fun anDossiers(): EntrySource = entries("an/dossiers")
    override fun senators(): InputStream = if (accept("senat/senateurs.json")) ExampleFiles.open("senat/senateurs.json") else "[]".byteInputStream()
    override fun europarl(): EuroparlApi = JsonEuroparlApi { path, offset, _ ->
        if (offset > 0) return@JsonEuroparlApi null
        val file = "pe/" + examplePath(path)
        if (accept(file) && ExampleFiles.exists(file)) ExampleFiles.open(file) else null
    }

    /** `meps/show-current?…` to `meps/show-current.json`, `meetings?year=2026` to `meetings-2026.json`. */
    private fun examplePath(path: String): String {
        val base = path.substringBefore('?')
        val year = Regex("year=(\\d{4})").find(path)?.groupValues?.get(1)
        return if (year != null) "$base-$year.json" else "$base.json"
    }
}

/**
 * The files of the institutions, downloaded over HTTPS into a private temporary folder, from three
 * hosts only ([ALLOWED_HOSTS]); redirections are not followed (a redirection elsewhere would leave
 * the list). Nothing is sent but the request itself: no cookie, no identifier.
 */
class OfficialSources(private val http: Downloader = Downloader()) : ImportSources {
    override val example: Boolean = false
    override val today: String = LocalDate.now(ZoneId.of("Europe/Paris")).toString()
    override val createdAt: Long = System.currentTimeMillis() / 1000
    private val folder: Path = Files.createTempDirectory("ppf-open-data-")

    override fun anMembers(): EntrySource = ZipEntries(http.download(AMO10, folder.resolve("amo10.zip")))
    override fun anBallots(): EntrySource = ZipEntries(http.download(SCRUTINS, folder.resolve("scrutins.zip")))
    override fun anDossiers(): EntrySource = ZipEntries(http.download(DOSSIERS, folder.resolve("dossiers.zip")))
    override fun senators(): InputStream = Files.newInputStream(http.download(SENATEURS, folder.resolve("senateurs.json")))
    override fun europarl(): EuroparlApi = JsonEuroparlApi { path, offset, limit ->
        val separator = if ('?' in path) '&' else '?'
        http.get("$EUROPARL_API$path${separator}format=application%2Fld%2Bjson&offset=$offset&limit=$limit", throttle = true)
    }

    override fun close() {
        Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    companion object {
        const val AMO10 = "https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip"
        const val SCRUTINS = "https://data.assemblee-nationale.fr/static/openData/repository/17/loi/scrutins/Scrutins.json.zip"
        const val DOSSIERS = "https://data.assemblee-nationale.fr/static/openData/repository/17/loi/dossiers_legislatifs/Dossiers_Legislatifs.json.zip"
        const val SENATEURS = "https://www.senat.fr/api-senat/senateurs.json"
        const val EUROPARL_API = "https://data.europarl.europa.eu/api/v2/"
    }
}

/**
 * Official files already on disk, read as they are (never executed): `amo10/`, `scrutins/` and
 * `dossiers/` hold the zip of the Assemblée nationale, `senat/` the file of the senators. The
 * European Parliament is an API, not a file: it is left out (its previous packages are kept).
 * Used to measure the memory of an import, or to import where the server has no outbound access.
 */
class LocalOfficialSources(private val folder: Path) : ImportSources {
    override val example: Boolean = false
    override val today: String = LocalDate.now(ZoneId.of("Europe/Paris")).toString()
    override val createdAt: Long = System.currentTimeMillis() / 1000

    private fun single(sub: String, suffix: String): Path {
        val dir = folder.resolve(sub)
        val files = if (Files.isDirectory(dir)) Files.list(dir).use { s -> s.filter { it.toString().endsWith(suffix) }.toList() } else emptyList()
        return files.singleOrNull() ?: throw UnreadableSourceException("expected one $suffix file in $sub/")
    }

    override fun anMembers(): EntrySource = ZipEntries(single("amo10", ".zip"))
    override fun anBallots(): EntrySource = ZipEntries(single("scrutins", ".zip"))
    override fun anDossiers(): EntrySource = ZipEntries(single("dossiers", ".zip"))
    override fun senators(): InputStream = Files.newInputStream(single("senat", ".json"))
    override fun europarl(): EuroparlApi = JsonEuroparlApi { _, _, _ -> null }
}

/** The only hosts the importer ever contacts, in `official` mode only. */
val ALLOWED_HOSTS: Set<String> = setOf("data.assemblee-nationale.fr", "www.senat.fr", "data.europarl.europa.eu")

/** HTTPS downloads from [ALLOWED_HOSTS], size-capped, without redirection. */
class Downloader(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(30))
        .build(),
    /** The European Parliament allows 500 requests per 5 minutes: one every 0.7 s stays well below. */
    private val pause: Duration = Duration.ofMillis(700),
) {
    private var lastThrottled = 0L

    private fun request(url: String): HttpRequest {
        val uri = URI.create(url)
        if (uri.scheme != "https" || uri.host !in ALLOWED_HOSTS) throw DownloadException("refused host ${uri.host}")
        return HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", "PPF-open-data-importer (+https://politisation-populaire-francaise.org)")
            .header("Accept", "application/json, application/ld+json, application/zip, */*")
            .GET()
            .build()
    }

    /** Downloads [url] to [file] (at most [MAX_FILE_BYTES]). */
    fun download(url: String, file: Path): Path {
        val response = try {
            client.send(request(url), HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: java.io.IOException) {
            throw DownloadException("${URI.create(url).host}: ${e::class.simpleName}")
        }
        response.body().use { body ->
            if (response.statusCode() != 200) throw DownloadException("${URI.create(url).host}: HTTP ${response.statusCode()}")
            val temporary = file.resolveSibling(file.fileName.toString() + ".part")
            try {
                Files.newOutputStream(temporary).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var total = 0L
                    while (true) {
                        val read = body.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) throw DownloadException("${URI.create(url).host}: file larger than $MAX_FILE_BYTES bytes")
                        out.write(buffer, 0, read)
                    }
                }
            } catch (e: java.io.IOException) {
                throw DownloadException("${URI.create(url).host}: ${e::class.simpleName}")
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
        return file
    }

    /** One API answer in memory (at most [SourceEntry.MAX_ENTRY_BYTES]), null for a 404. */
    fun get(url: String, throttle: Boolean): InputStream? {
        if (throttle) {
            val wait = lastThrottled + pause.toMillis() - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            lastThrottled = System.currentTimeMillis()
        }
        val response = try {
            client.send(request(url), HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: java.io.IOException) {
            throw DownloadException("${URI.create(url).host}: ${e::class.simpleName}")
        }
        return response.body().use { body ->
            when (response.statusCode()) {
                200 -> try {
                    readBounded(body, SourceEntry.MAX_ENTRY_BYTES * 4, URI.create(url).path).inputStream()
                } catch (e: java.io.IOException) {
                    throw DownloadException("${URI.create(url).host}: ${e::class.simpleName}")
                }
                204, 404 -> null
                else -> throw DownloadException("${URI.create(url).host}: HTTP ${response.statusCode()}")
            }
        }
    }

    companion object {
        /** The biggest file used, Scrutins.json.zip, weighs 30 MB. */
        const val MAX_FILE_BYTES: Long = 512L * 1024 * 1024
    }
}
