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
    /** History: AMO30, every deputy since 1997 with dated mandates (groups and parties). */
    fun anHistoryActors(): EntrySource
    /** History: the ballots of one legislature of [AssembleeHistory.LEGISLATURES]. */
    fun anHistoryBallots(legislature: Int): EntrySource
    /** History: `dosleg.zip`, the copy of the Dosleg database of the Sénat (one `.sql` entry, read as text). */
    fun senatDosleg(): EntrySource
    /** History: `ODSEN_HISTOGROUPES.csv`, the groups of the senators over time. */
    fun senatHistoGroups(): InputStream
    /** The presentations of the presidential election published by the Conseil constitutionnel (VO-37); null when there is none to read this time. */
    fun sponsorships(): InputStream? = null
    /** The application of the laws (VO-36): the file of the laws of the baromètre of the Assemblée nationale; null when there is none to read this time. */
    fun applicationLaws(): InputStream? = null
    /** The application of the laws (VO-36): the file of the measures of the same baromètre. */
    fun applicationMeasures(): InputStream? = null
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
    override fun anHistoryActors(): EntrySource = entries("an-historique/amo30")
    override fun anHistoryBallots(legislature: Int): EntrySource = entries("an-historique/scrutins-$legislature")
    override fun senatDosleg(): EntrySource = entries("senat-historique/dosleg")
    override fun senatHistoGroups(): InputStream = if (accept("senat-historique/ODSEN_HISTOGROUPES.csv")) ExampleFiles.open("senat-historique/ODSEN_HISTOGROUPES.csv") else ByteArray(0).inputStream()
    override fun sponsorships(): InputStream? = if (accept("cc/parrainages.json")) ExampleFiles.open("cc/parrainages.json") else null
    override fun applicationLaws(): InputStream? = if (accept("an-barometre/lois.csv")) ExampleFiles.open("an-barometre/lois.csv") else null
    override fun applicationMeasures(): InputStream? = if (accept("an-barometre/mesures.csv")) ExampleFiles.open("an-barometre/mesures.csv") else null
    override fun europarl(): EuroparlApi = JsonEuroparlApi { path, offset, _ ->
        if (offset > 0) return@JsonEuroparlApi null
        val file = "pe/" + examplePath(path)
        if (accept(file) && ExampleFiles.exists(file)) ExampleFiles.open(file) else null
    }

    /** `meps/show-current?…` to `meps/show-current.json`, `meetings?year=2026` to `meetings-2026.json`. */
    private fun examplePath(path: String): String {
        val base = path.substringBefore('?')
        val year = Regex("year=(\\d{4})").find(path)?.groupValues?.get(1)
        val term = Regex("parliamentary-term=(\\d{1,2})").find(path)?.groupValues?.get(1)
        return when {
            year != null -> "$base-$year.json"
            term != null -> "$base-term-$term.json"
            else -> "$base.json"
        }
    }
}

/**
 * The files of the institutions, downloaded over HTTPS into a private temporary folder, from four
 * hosts only ([ALLOWED_HOSTS]); redirections are not followed (a redirection elsewhere would leave
 * the list). Nothing is sent but the request itself: no cookie, no identifier.
 */
class OfficialSources(
    private val http: Downloader = Downloader(),
    /** The data.gouv.fr dataset of the presentations of 2027 (`PPF_SPONSORSHIPS_DATASET`), null until the Conseil constitutionnel creates it. */
    private val sponsorshipsDataset: String? = null,
) : ImportSources {
    override val example: Boolean = false
    override val today: String = LocalDate.now(ZoneId.of("Europe/Paris")).toString()
    override val createdAt: Long = System.currentTimeMillis() / 1000
    private val folder: Path = Files.createTempDirectory("ppf-open-data-")

    override fun anMembers(): EntrySource = ZipEntries(http.download(AMO10, folder.resolve("amo10.zip")))
    override fun anBallots(): EntrySource = ZipEntries(http.download(SCRUTINS, folder.resolve("scrutins.zip")))
    override fun anDossiers(): EntrySource = ZipEntries(http.download(DOSSIERS, folder.resolve("dossiers.zip")))
    override fun senators(): InputStream = Files.newInputStream(http.download(SENATEURS, folder.resolve("senateurs.json")))
    override fun anHistoryActors(): EntrySource = ZipEntries(http.download(AMO30, folder.resolve("amo30.zip")))
    override fun anHistoryBallots(legislature: Int): EntrySource {
        val url = AN_HISTORY_BALLOTS[legislature] ?: throw UnreadableSourceException("no ballots file for legislature $legislature")
        return ZipEntries(http.download(url, folder.resolve("scrutins-$legislature.zip")))
    }
    override fun senatDosleg(): EntrySource = ZipEntries(http.download(DOSLEG, folder.resolve("dosleg.zip")))
    override fun senatHistoGroups(): InputStream = Files.newInputStream(http.download(HISTOGROUPES, folder.resolve("histogroupes.csv")))

    /**
     * The newest JSON file of the dataset of the presentations: the API of data.gouv.fr gives its resources, the
     * file comes from static.data.gouv.fr (both in [ALLOWED_HOSTS]); nothing is asked without a dataset.
     */
    override fun sponsorships(): InputStream? {
        val dataset = sponsorshipsDataset?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!DATASET_ID.matches(dataset)) throw UnreadableSourceException("sponsorships: not a dataset identifier")
        val answer = http.get("$DATA_GOUV_API$dataset/", throttle = false) ?: throw DownloadException("www.data.gouv.fr: no such dataset")
        val resources = answer.use { lenientJson.parseToJsonElement(it.readBytes().decodeToString()) }["resources"].items()
        val newest = resources
            .filter { it["format"].text()?.lowercase() == "json" && it["url"].text()?.startsWith("https://static.data.gouv.fr/") == true }
            .maxByOrNull { it["last_modified"].text().orEmpty() }
            ?: throw UnreadableSourceException("sponsorships: no JSON file in the dataset")
        return Files.newInputStream(http.download(newest["url"].text()!!, folder.resolve("parrainages.json")))
    }

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

        // History (voir-historique-sources.md); the address of AMO30 follows the repository of the
        // 17th legislature, to be confirmed at the first real import.
        const val AMO30 = "https://data.assemblee-nationale.fr/static/openData/repository/17/amo/tous_acteurs_mandats_organes_xi_legislature/AMO30_tous_acteurs_tous_mandats_tous_organes_historique.json.zip"
        val AN_HISTORY_BALLOTS: Map<Int, String> = mapOf(
            14 to "https://data.assemblee-nationale.fr/static/openData/repository/14/loi/scrutins/Scrutins_XIV.json.zip",
            15 to "https://data.assemblee-nationale.fr/static/openData/repository/15/loi/scrutins/Scrutins_XV.json.zip",
            16 to "https://data.assemblee-nationale.fr/static/openData/repository/16/loi/scrutins/Scrutins.json.zip",
            17 to SCRUTINS,
        )
        const val DOSLEG = "https://data.senat.fr/data/dosleg/dosleg.zip"
        const val HISTOGROUPES = "https://data.senat.fr/data/senateurs/ODSEN_HISTOGROUPES.csv"

        // The presentations of the presidential election (VO-37): the dataset of the Conseil constitutionnel on data.gouv.fr.
        const val DATA_GOUV_API = "https://www.data.gouv.fr/api/1/datasets/"
        val DATASET_ID = Regex("^[a-z0-9][a-z0-9-]{0,99}$")
    }
}

/**
 * Official files already on disk, read as they are (never executed): `amo10/`, `scrutins/` and
 * `dossiers/` hold the zip of the Assemblée nationale, `senat/` the file of the senators; for the
 * history, `amo30/` and `scrutins-14/` to `scrutins-17/` (zips of the Assemblée), `dosleg/` (zip)
 * and `histogroupes/` (CSV) of the Sénat. The
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
    override fun anHistoryActors(): EntrySource = ZipEntries(single("amo30", ".zip"))
    override fun anHistoryBallots(legislature: Int): EntrySource = ZipEntries(single("scrutins-$legislature", ".zip"))
    override fun senatDosleg(): EntrySource = ZipEntries(single("dosleg", ".zip"))
    override fun senatHistoGroups(): InputStream = Files.newInputStream(single("histogroupes", ".csv"))
    override fun sponsorships(): InputStream? = folder.resolve("cc").takeIf { Files.isDirectory(it) }?.let { Files.newInputStream(single("cc", ".json")) }
    // `an-barometre/` holds the two files under their official names.
    override fun applicationLaws(): InputStream? = folder.resolve("an-barometre/barometre_application_lois_assemblee.csv").takeIf { Files.isRegularFile(it) }?.let { Files.newInputStream(it) }
    override fun applicationMeasures(): InputStream? = folder.resolve("an-barometre/barometre_application_mesures_assemblee.csv").takeIf { Files.isRegularFile(it) }?.let { Files.newInputStream(it) }
}

/** The only hosts the importer ever contacts, in `official` mode only. */
val ALLOWED_HOSTS: Set<String> = setOf("data.assemblee-nationale.fr", "www.senat.fr", "data.senat.fr", "data.europarl.europa.eu", "www.data.gouv.fr", "static.data.gouv.fr")

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
