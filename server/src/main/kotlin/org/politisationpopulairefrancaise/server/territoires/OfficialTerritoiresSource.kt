package org.politisationpopulairefrancaise.server.territoires

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.server.elus.ElusDownloadException
import org.politisationpopulairefrancaise.server.elus.ElusHttp
import org.politisationpopulairefrancaise.server.elus.JdkElusHttp
import org.politisationpopulairefrancaise.server.elus.OfficialSource
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Comparator

/**
 * The real referential (PPF_TERRITOIRES_SOURCE=official): the Code officiel géographique and the
 * postal codes from data.gouv.fr, the populations and registered voters from the API Melodi of the
 * Insee, the intercommunalities from its API Métadonnées (docs/integration/territoires.md,
 * section 1). Everything is written in a temporary directory deleted by [close], in the same
 * files as the example set. HTTPS only, towards [HOSTS] only; nothing about the readers is sent.
 */
class OfficialTerritoiresSource(
    private val http: ElusHttp = JdkElusHttp(allowedHosts = HOSTS),
    private val insee: ElusHttp = JdkElusHttp(allowedHosts = HOSTS, accept = "application/json"),
    private val today: () -> LocalDate = LocalDate::now,
    /** Waits between two requests to the Insee (30 per minute for Melodi); tests pass a function that does not wait. */
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) : TerritoiresSource {
    override val example: Boolean = false
    override val kind: String = TerritoiresSources.OFFICIAL

    private var directory: Path? = null

    override fun prepare(): TerritoiresDates {
        val dir = Files.createTempDirectory("ppf-territoires-")
        directory = dir
        val dataset = json(http.text("${OfficialSource.API}/datasets/${OfficialSource.COG_DATASET}/", MAX_API_BYTES)) as? JsonObject
            ?: throw ElusDownloadException("The dataset of the Code officiel géographique is not described as expected.")
        val cog = cogUrls(OfficialSource.resources(dataset))
        for ((file, url) in cog.second) http.download(url, dir.resolve(file.fileName), MAX_FILE_BYTES)
        http.download(OfficialSource.stable(OfficialSource.POSTAL_CODES_RESOURCE), dir.resolve(TerritoiresFile.POSTAL_CODES.fileName), MAX_FILE_BYTES)
        val geography = LocalDate.of(cog.first, 1, 1)

        val lines = ArrayList<String>()
        // Populations: the most recent vintage already in force (vintage + 3, on 1 January).
        val populations = ArrayList<Observation>()
        for (level in POPULATION_LEVELS) for (measure in listOf("PMUN", "PTOT")) {
            populations += melodi("DS_POPULATIONS_REFERENCE?GEO=$level&POPREF_MEASURE=$measure", measure)
        }
        val vintage = populations.mapNotNull { it.period.toIntOrNull() }.filter { it + 3 <= today().year }.maxOrNull()
            ?: throw ElusDownloadException("Melodi gives no population vintage in force.")
        val kept = populations.filter { it.period == vintage.toString() }
        val statisticsGeography = kept.map { it.geographyYear }.distinct().singleOrNull()
            ?: throw ElusDownloadException("The populations of Melodi mix several geographies.")
        kept.forEach { lines += it.line() }

        // Registered voters: the most recent extraction of the liste principale, and the listes complémentaires of that day.
        val voters = ArrayList<Observation>()
        for (level in ELECTORATE_LEVELS) {
            voters += melodi("DS_ELECTORAL?GEO=$level&REU_LIST_TYPE=LP&NATIONALITY=250&REU_MEASURE=VOTERS&SEX=_T&AGE=_T&REU_REASON_REG=_Z&REU_REASON_RMV=_Z", "LP")
            voters += melodi("DS_ELECTORAL?GEO=$level&REU_LIST_TYPE=LC&NATIONALITY=150&REU_MEASURE=VOTERS&SEX=_T&AGE=_T&REU_REASON_REG=_Z&REU_REASON_RMV=_Z", "LC")
        }
        val extraction = voters.filter { it.measure == "LP" && it.level == "COM" }.maxOfOrNull { it.period }
            ?: throw ElusDownloadException("Melodi gives no registered voters.")
        voters.filter { it.period == extraction && it.geographyYear == statisticsGeography }.forEach { lines += it.line() }
        Files.write(dir.resolve(TerritoiresFile.STATISTICS.fileName), (listOf("niveau;code;grandeur;valeur") + lines.distinct().sorted()).joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))

        // Intercommunalities and their communes, on the day of the geography.
        val epciLines = ArrayList<String>()
        val day = geography.toString()
        val list = json(insee.text("$GEO_API/intercommunalites?date=$day", MAX_API_BYTES)) as? JsonArray ?: throw ElusDownloadException("The list of the intercommunalities is not an array.")
        for (element in list) {
            val epci = element as? JsonObject ?: continue
            val code = epci.string("code") ?: continue
            if (!SIREN.matches(code) || code == TerritoiresBuilder.METROPOLE_DE_LYON_SIREN) continue
            val category = epci.string("categorieJuridique") ?: continue
            val name = (epci.string("intituleComplet") ?: epci.string("intitule") ?: continue).replace(';', ',')
            pause(INSEE_PAUSE_MS)
            val members = json(insee.text("$GEO_API/intercommunalite/$code/descendants?type=Commune&date=$day", MAX_API_BYTES)) as? JsonArray ?: continue
            for (member in members) {
                val commune = (member as? JsonObject)?.string("code") ?: continue
                epciLines += "$code;$category;$name;$commune"
            }
        }
        if (epciLines.isEmpty()) throw ElusDownloadException("The API of the Insee gives no intercommunality.")
        Files.write(dir.resolve(TerritoiresFile.EPCI.fileName), (listOf("siren;categorie;nom;commune") + epciLines.sorted()).joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))

        return TerritoiresDates(
            geography = geography,
            statisticsGeography = LocalDate.of(statisticsGeography, 1, 1),
            populationVintage = vintage,
            populationInForce = LocalDate.of(vintage + 3, 1, 1),
            electorateDate = LocalDate.parse(extraction),
        )
    }

    override fun open(file: TerritoiresFile): InputStream {
        val path = (directory ?: throw IllegalStateException("prepare() first")).resolve(file.fileName)
        if (!Files.exists(path)) throw ElusDownloadException("${file.fileName} was not downloaded.")
        return Files.newInputStream(path)
    }

    override fun close() {
        directory?.let { dir -> Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
        directory = null
    }

    /** One figure of Melodi: `2025-COM-69123`, period, measure, value. */
    internal data class Observation(val geographyYear: Int, val level: String, val code: String, val period: String, val measure: String, val value: Long) {
        fun line(): String = "$level;$code;$measure;$value"
    }

    /** Every page of a Melodi query (at most [MAX_PAGES] pages of 10 000 observations). */
    private fun melodi(query: String, measure: String): List<Observation> {
        val result = ArrayList<Observation>()
        var url: String? = "$MELODI/$query&maxResult=10000&page=1"
        var pages = 0
        while (url != null) {
            if (++pages > MAX_PAGES) throw ElusDownloadException("Melodi gives too many pages.")
            pause(MELODI_PAUSE_MS)
            val page = json(insee.text(url, MAX_PAGE_BYTES)) as? JsonObject ?: throw ElusDownloadException("A page of Melodi is not an object.")
            result += observations(page, measure)
            val paging = page["paging"] as? JsonObject
            val last = (paging?.get("isLast") as? JsonPrimitive)?.contentOrNull != "false"
            url = if (last) null else paging?.string("next")?.takeIf { URI(it).host == MELODI_HOST }
        }
        return result
    }

    private fun json(text: String) = runCatching { TerritoiresJson.parseToJsonElement(text) }.getOrElse { throw ElusDownloadException("An answer is not JSON.") }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val MELODI_HOST: String = "api.insee.fr"
        const val MELODI: String = "https://api.insee.fr/melodi/data"
        const val GEO_API: String = "https://api.insee.fr/metadonnees/geo"

        /** The hosts contacted in official mode: those of the directory of elected officials, and the APIs of the Insee. */
        val HOSTS: Set<String> = OfficialSource.HOSTS + MELODI_HOST

        const val MAX_FILE_BYTES: Long = 100L * 1024 * 1024
        const val MAX_API_BYTES: Long = 5L * 1024 * 1024
        const val MAX_PAGE_BYTES: Long = 20L * 1024 * 1024
        const val MAX_PAGES: Int = 30
        const val MELODI_PAUSE_MS: Long = 2_500
        const val INSEE_PAUSE_MS: Long = 500

        private val SIREN = Regex("^[0-9]{9}$")
        private val GEO = Regex("^([0-9]{4})-(COM|ARM|DEP|REG|COMER|FRANCE)-([0-9A-Z_]{1,12})$")
        private val POPULATION_LEVELS = listOf("COM", "ARM", "DEP", "REG", "FRANCE")
        private val ELECTORATE_LEVELS = listOf("COM", "ARM", "DEP", "REG", "COMER", "FRANCE")

        private val COG_FILES: Map<TerritoiresFile, Regex> = linkedMapOf(
            TerritoiresFile.COG_COMMUNES to Regex("/v_commune_(\\d{4})\\.csv$"),
            TerritoiresFile.COG_DEPARTEMENTS to Regex("/v_departement_(\\d{4})\\.csv$"),
            TerritoiresFile.COG_REGIONS to Regex("/v_region_(\\d{4})\\.csv$"),
            TerritoiresFile.COG_CTCD to Regex("/v_ctcd_(\\d{4})\\.csv$"),
            TerritoiresFile.COG_COMER to Regex("/v_comer_(\\d{4})\\.csv$"),
            TerritoiresFile.COG_COMMUNES_COMER to Regex("/v_commune_comer_(\\d{4})\\.csv$"),
            TerritoiresFile.COG_EVENTS to Regex("/v_mvt_commune_(\\d{4})\\.csv$"),
        )

        /** The year and the files of the most recent vintage of the Code officiel géographique that has all of them. */
        internal fun cogUrls(resources: List<OfficialSource.Resource>): Pair<Int, Map<TerritoiresFile, String>> {
            val byYear = HashMap<Int, HashMap<TerritoiresFile, String>>()
            for (resource in resources) {
                val path = runCatching { URI(resource.url).path }.getOrNull() ?: continue
                for ((file, pattern) in COG_FILES) {
                    val year = pattern.find(path)?.groupValues?.get(1)?.toInt() ?: continue
                    byYear.getOrPut(year) { HashMap() }.putIfAbsent(file, resource.url)
                }
            }
            val year = byYear.filterValues { it.size == COG_FILES.size }.keys.maxOrNull()
                ?: throw ElusDownloadException("No vintage of the Code officiel géographique has all its files.")
            return year to COG_FILES.keys.associateWith { byYear.getValue(year).getValue(it) }
        }

        /** The observations of one page of Melodi, read tolerantly (an observation without a valid code or value is left out). */
        internal fun observations(page: JsonObject, measure: String): List<Observation> =
            (page["observations"] as? JsonArray).orEmpty().mapNotNull { element ->
                val observation = element as? JsonObject ?: return@mapNotNull null
                val dimensions = observation["dimensions"] as? JsonObject ?: return@mapNotNull null
                val geo = (dimensions["GEO"] as? JsonPrimitive)?.contentOrNull?.let(GEO::matchEntire) ?: return@mapNotNull null
                val period = (dimensions["TIME_PERIOD"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val value = ((observation["measures"] as? JsonObject)?.get("OBS_VALUE_NIVEAU") as? JsonObject)?.get("value")?.jsonPrimitive?.doubleOrNull
                    ?.takeIf { it >= 0 && it < 2.0e8 } ?: return@mapNotNull null
                Observation(geo.groupValues[1].toInt(), geo.groupValues[2], geo.groupValues[3], period, measure, Math.round(value))
            }
    }
}
