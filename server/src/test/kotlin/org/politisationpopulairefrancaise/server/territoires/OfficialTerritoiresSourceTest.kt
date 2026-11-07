package org.politisationpopulairefrancaise.server.territoires

import org.politisationpopulairefrancaise.core.territoires.TerritoiresReader
import org.politisationpopulairefrancaise.core.territoires.TerritoryCode
import org.politisationpopulairefrancaise.server.elus.ElusDownloadException
import org.politisationpopulairefrancaise.server.elus.ElusHttp
import org.politisationpopulairefrancaise.server.elus.JdkElusHttp
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The official source against a simulated data.gouv.fr and Insee (no network). */
class OfficialTerritoiresSourceTest {

    private fun example(file: TerritoiresFile): ByteArray =
        ExampleTerritoiresSource::class.java.getResourceAsStream("${ExampleTerritoiresSource.RESOURCE_DIRECTORY}/${file.fileName}")!!.readBytes()

    /** Answers like data.gouv.fr and the Insee would, from the example set. */
    private inner class FakeInternet : ElusHttp {
        val asked = ArrayList<String>()
        private val statistics = example(TerritoiresFile.STATISTICS).toString(Charsets.UTF_8).lines().drop(1).filter { it.isNotBlank() }.map { it.split(';') }
        private val epci = example(TerritoiresFile.EPCI).toString(Charsets.UTF_8).lines().drop(1).filter { it.isNotBlank() }.map { it.split(';') }

        override fun text(url: String, maxBytes: Long): String {
            asked += url
            val uri = URI(url)
            val query = uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
            return when {
                uri.host == "www.data.gouv.fr" -> cogDataset()
                uri.path == "/metadonnees/geo/intercommunalites" ->
                    // The Métropole de Lyon is listed too, and must be left aside.
                    (epci.map { it[0] to it[1] }.distinct() + ("200046977" to "(Autre) Collectivité territoriale"))
                        .joinToString(",", "[", "]") { (siren, category) -> """{"code":"$siren","categorieJuridique":"$category","intitule":"EPCI $siren"}""" }
                uri.path.startsWith("/metadonnees/geo/intercommunalite/") -> {
                    val siren = uri.path.split('/')[4]
                    epci.filter { it[0] == siren }.joinToString(",", "[", "]") { """{"code":"${it[3]}","type":"Commune"}""" }
                }
                uri.path.startsWith("/melodi/data/") -> melodi(query)
                else -> throw ElusDownloadException("unexpected $url")
            }
        }

        private fun melodi(query: Map<String, String>): String {
            val level = query.getValue("GEO")
            val measure = query["POPREF_MEASURE"] ?: query.getValue("REU_LIST_TYPE")
            val period = if (query.containsKey("POPREF_MEASURE")) "2023" else "2026-02-11"
            val rows = statistics.filter { it[0] == level && it[2] == measure }
            // An older vintage too, which must be left aside, and a vintage not yet in force.
            val older = rows.take(1).map { listOf(it[0], it[1], it[2], "1") }
            fun obs(row: List<String>, p: String) = """{"dimensions":{"GEO":"2025-${row[0]}-${row[1]}","TIME_PERIOD":"$p"},"measures":{"OBS_VALUE_NIVEAU":{"value":${row[3]}.0}}}"""
            val extra = if (query.containsKey("POPREF_MEASURE")) older.map { obs(it, "2022") } + older.map { obs(it, "2030") } else older.map { obs(it, "2024-05-08") }
            val all = rows.map { obs(it, period) } + extra
            return """{"observations":[${all.joinToString(",")}],"paging":{"isLast":true}}"""
        }

        private fun cogDataset(): String {
            val names = listOf("v_commune", "v_departement", "v_region", "v_ctcd", "v_comer", "v_commune_comer", "v_mvt_commune")
            val resources = names.flatMap { name -> listOf(2025, 2026).map { year -> """{"id":"$name-$year","url":"https://www.insee.fr/fr/statistiques/fichier/1/${name}_$year.csv"}""" } }
            return """{"resources":[${resources.joinToString(",")}]}"""
        }

        override fun download(url: String, target: Path, maxBytes: Long) {
            asked += url
            val name = URI(url).path.substringAfterLast('/')
            val file = when {
                "datasets/r/" in url -> TerritoiresFile.POSTAL_CODES
                else -> TerritoiresFile.entries.first { name == it.fileName.removeSuffix(".csv") + "_2026.csv" }
            }
            Files.write(target, example(file))
        }
    }

    @Test
    fun theOfficialSourceGivesTheSameReferentialFromTheApis() {
        val internet = FakeInternet()
        val source = OfficialTerritoiresSource(internet, internet, today = { LocalDate.parse("2026-11-07") }, pause = {})
        val built = source.use { s ->
            val dates = s.prepare()
            assertEquals(LocalDate.parse("2026-01-01"), dates.geography)
            assertEquals(LocalDate.parse("2025-01-01"), dates.statisticsGeography)
            assertEquals(2023, dates.populationVintage)
            assertEquals(LocalDate.parse("2026-01-01"), dates.populationInForce)
            assertEquals(LocalDate.parse("2026-02-11"), dates.electorateDate)
            TerritoiresBuilder(s.example, dates) { s.open(it) }.build()
        }
        val national = assertNotNull(TerritoiresReader.readNational(built.national.toString(Charsets.UTF_8)))
        assertFalse(national.example)
        assertEquals(1_914_667, national.find(TerritoryCode.departement("69")!!)?.population?.municipale)
        assertEquals(1_227_213, national.find(TerritoryCode.departement("69")!!)?.electorate?.principale)
        val pkg = assertNotNull(TerritoiresReader.readPackage(built.packages.first { it.code == "69" }.body.toString(Charsets.UTF_8)))
        assertEquals(519_127, pkg.find(TerritoryCode.commune("69123")!!)?.population?.municipale)
        assertNotNull(pkg.find(TerritoryCode.epci("200040590")!!))
        // Only HTTPS hosts of the list were asked.
        assertTrue(internet.asked.all { URI(it).host in OfficialTerritoiresSource.HOSTS })
    }

    @Test
    fun onlyTheAllowedHostsAreContacted() {
        assertTrue("api.insee.fr" in OfficialTerritoiresSource.HOSTS)
        val client = JdkElusHttp(allowedHosts = OfficialTerritoiresSource.HOSTS)
        assertFailsWith<ElusDownloadException> { client.checked(URI("https://example.org/data")) }
        assertFailsWith<ElusDownloadException> { client.checked(URI("http://api.insee.fr/melodi")) }
        assertEquals("api.insee.fr", client.checked(URI("https://api.insee.fr/melodi/data/X")).host)
    }

    @Test
    fun melodiObservationsAreReadTolerantly() {
        val page = TerritoiresJson.parseToJsonElement(
            """{"observations":[
                {"dimensions":{"GEO":"2025-COM-69123","TIME_PERIOD":"2023"},"measures":{"OBS_VALUE_NIVEAU":{"value":519127.0}}},
                {"dimensions":{"GEO":"COM-69123","TIME_PERIOD":"2023"},"measures":{"OBS_VALUE_NIVEAU":{"value":1.0}}},
                {"dimensions":{"GEO":"2025-COM-69124","TIME_PERIOD":"2023"},"measures":{"OBS_VALUE_NIVEAU":{"value":-3.0}}},
                {"dimensions":{"GEO":"2025-DEP-2A","TIME_PERIOD":"2023"},"measures":{}}
            ]}""",
        ) as kotlinx.serialization.json.JsonObject
        val read = OfficialTerritoiresSource.observations(page, "PMUN")
        assertEquals(listOf(OfficialTerritoiresSource.Observation(2025, "COM", "69123", "2023", "PMUN", 519_127)), read)
    }
}
