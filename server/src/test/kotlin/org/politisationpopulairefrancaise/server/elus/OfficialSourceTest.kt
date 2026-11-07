package org.politisationpopulairefrancaise.server.elus

import org.politisationpopulairefrancaise.server.TestDatabase
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The official source without network: data.gouv.fr is played by [FakeDataGouv], which describes the
 * datasets as the real API does and serves the files of the example set in place of the real ones.
 */
class OfficialSourceTest {
    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun resource(id: String, url: String) = """{"id": "$id", "title": "t", "url": "$url", "format": "csv"}"""

    /** The RNE and the Code officiel géographique (vintages 2025 and 2026, the latter incomplete for a test). */
    inner class FakeDataGouv(private val cogYears: List<Int> = listOf(2025, 2026)) : ElusHttp {
        val downloaded = ArrayList<String>()

        private val urls = HashMap<String, ElusFile>()

        init {
            for ((file, id) in OfficialSource.RNE_RESOURCES) urls["https://static.data.gouv.fr/resources/rne/$id/${file.fileName}"] = file
            urls[OfficialSource.stable(OfficialSource.POSTAL_CODES_RESOURCE)] = ElusFile.POSTAL_CODES
            urls[OfficialSource.stable(OfficialSource.LEGISLATIVES_RESOURCE)] = ElusFile.LEGISLATIVES
            urls[OfficialSource.stable(OfficialSource.DEPARTEMENTALES_RESOURCE)] = ElusFile.DEPARTEMENTALES
        }

        private fun cog(year: Int): Map<ElusFile, String> = mapOf(
            ElusFile.COG_COMMUNES to "v_commune_$year.csv",
            ElusFile.COG_CANTONS to "v_canton_$year.csv",
            ElusFile.COG_DEPARTEMENTS to "v_departement_$year.csv",
            ElusFile.COG_REGIONS to "v_region_$year.csv",
            ElusFile.COG_COMER to "v_comer_$year.csv",
            ElusFile.COG_COMMUNES_COMER to "v_commune_comer_$year.csv",
            ElusFile.COG_EVENTS to "v_mvt_commune_$year.csv",
        ).mapValues { "https://www.insee.fr/fr/statistiques/fichier/$year/${it.value}" }

        override fun text(url: String, maxBytes: Long): String = when (url) {
            "${OfficialSource.API}/datasets/${OfficialSource.RNE_DATASET}/" -> {
                val resources = OfficialSource.RNE_RESOURCES.map { (file, id) -> resource(id, "https://static.data.gouv.fr/resources/rne/$id/${file.fileName}") }
                """{"id": "${OfficialSource.RNE_DATASET}", "last_update": "2026-08-11T15:51:01.590000+00:00", "resources": [${resources.joinToString()}]}"""
            }
            "${OfficialSource.API}/datasets/${OfficialSource.COG_DATASET}/" -> {
                val resources = cogYears.flatMap { year ->
                    val files = cog(year).let { if (year == 2026) it - ElusFile.COG_EVENTS else it }
                    files.values.mapIndexed { i, u -> resource("cog-$year-$i", u) }
                } + resource("other", "https://www.insee.fr/fr/information/8740218")
                """{"id": "${OfficialSource.COG_DATASET}", "resources": [${resources.joinToString()}]}"""
            }
            else -> throw ElusDownloadException("unexpected $url")
        }

        override fun download(url: String, target: Path, maxBytes: Long) {
            downloaded += url
            val file = urls[url] ?: cogYears.firstNotNullOfOrNull { year -> cog(year).entries.firstOrNull { it.value == url }?.key }
                ?: throw ElusDownloadException("unexpected $url")
            ExampleSource().open(file).use { input -> Files.copy(input, target) }
        }
    }

    @Test
    fun theMostRecentCompleteVintageOfTheCodeOfficielGeographiqueIsChosen() {
        val http = FakeDataGouv()
        val source = OfficialSource(http)
        source.use {
            assertEquals(LocalDate.parse("2026-08-11"), it.prepare())
            ElusFile.entries.forEach { file -> it.open(file).close() }
        }
        // 2026 lacks the events: 2025 is used, complete.
        assertTrue(http.downloaded.filter { "insee.fr" in it }.all { "/2025/" in it }, http.downloaded.toString())
        assertEquals(ElusFile.entries.size, http.downloaded.size)
        assertFailsWith<ElusDownloadException> { source.open(ElusFile.MUNICIPAL) }
    }

    @Test
    fun anOfficialImportServesRealDataWithoutTheExampleFlag() {
        // The files are those of the example set; only the flag and the date come from the source.
        val store = ElusStore(TestDatabase.dataSource)
        assertIs<ImportOutcome.Imported>(ElusImporter(store).run(OfficialSource(FakeDataGouv())))
        val served = store.served()!!
        assertEquals(ElusSources.OFFICIAL, served.source)
        val index = ElusJson.decodeFromString(ElusIndex.serializer(), served.index.toString(Charsets.UTF_8))
        assertFalse(index.example)
        assertEquals("2026-08-11", index.source.updatedOn)
    }

    @Test
    fun aMissingResourceFailsTheDownload() {
        assertFailsWith<ElusDownloadException> { OfficialSource.cogUrls(emptyList()) }
        val http = object : ElusHttp {
            override fun text(url: String, maxBytes: Long) = """{"last_update": "2026-08-11", "resources": []}"""
            override fun download(url: String, target: Path, maxBytes: Long) = throw AssertionError("nothing to download")
        }
        val error = assertFailsWith<ElusDownloadException> { OfficialSource(http).use { it.prepare() } }
        assertTrue(error.message!!.contains("elus-conseillers-municipaux-cm.csv"))
    }

    @Test
    fun onlyHttpsTowardsTheOfficialHostsIsAllowed() {
        val http = JdkElusHttp()
        for (url in listOf("https://www.data.gouv.fr/api/1/datasets/x/", "https://static.data.gouv.fr/a.csv", "https://www.insee.fr/f.csv", "https://data.laposte.fr/raw")) {
            assertEquals(URI(url), http.checked(URI(url)))
        }
        for (url in listOf("http://www.data.gouv.fr/x", "https://evil.example/x", "https://data.gouv.fr.evil.example/x", "https://user@www.data.gouv.fr/x", "file:///etc/passwd")) {
            assertFailsWith<ElusDownloadException>(url) { http.checked(URI(url)) }
        }
    }
}
