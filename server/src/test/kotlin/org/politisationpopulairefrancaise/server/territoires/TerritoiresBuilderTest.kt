package org.politisationpopulairefrancaise.server.territoires

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.politisationpopulairefrancaise.core.territoires.SpreadRule
import org.politisationpopulairefrancaise.core.territoires.TerritoiresReader
import org.politisationpopulairefrancaise.core.territoires.TerritoryCode
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The documents built from the example set, read back by the reader of the device. */
class TerritoiresBuilderTest {
    private val directories = ArrayList<Path>()

    @AfterTest
    fun removeDirectories() = directories.forEach { it.toFile().deleteRecursively() }

    private fun build(source: TerritoiresSource = ExampleTerritoiresSource()): BuiltTerritoires =
        source.use { TerritoiresBuilder(it.example, it.prepare()) { file -> it.open(file) }.build() }

    private fun document(built: BuiltTerritoires, code: String): String =
        assertNotNull(built.packages.firstOrNull { it.code == code }).body.toString(Charsets.UTF_8)

    /** A copy of the example set in a temporary directory, to change some files. */
    private fun exampleCopy(): Path {
        val dir = Files.createTempDirectory("ppf-territoires-test-").also { directories.add(it) }
        for (file in TerritoiresFile.entries) {
            ExampleTerritoiresSource::class.java.getResourceAsStream("${ExampleTerritoiresSource.RESOURCE_DIRECTORY}/${file.fileName}")!!.use { input ->
                Files.copy(input, dir.resolve(file.fileName))
            }
        }
        return dir
    }

    private val exampleDates = TerritoiresDates(
        geography = LocalDate.parse("2026-01-01"),
        statisticsGeography = LocalDate.parse("2025-01-01"),
        populationVintage = 2023,
        populationInForce = LocalDate.parse("2026-01-01"),
        electorateDate = LocalDate.parse("2026-02-11"),
    )

    @Test
    fun theNationalLevelHoldsEveryRegionDepartmentAndCollectivity() {
        val built = build()
        val national = assertNotNull(TerritoiresReader.readNational(built.national.toString(Charsets.UTF_8)))
        assertTrue(national.example)
        assertTrue(national.partial)
        assertEquals(LocalDate.parse("2026-02-11").toString(), national.builtOn?.toIso())
        assertEquals(18, national.regions.size)
        assertEquals(101, national.departements.size)
        assertEquals(100, national.collectivites.size)
        assertEquals(106, national.spreadUnits().size)
        assertEquals(SpreadRule.TO_CONFIRM, national.find(TerritoryCode.outreMer("988")!!)?.spread)

        val rhone = assertNotNull(national.find(TerritoryCode.departement("69")!!))
        assertEquals(1_914_667, rhone.population?.municipale)
        assertEquals(1_940_015, rhone.population?.totale)
        assertEquals(1_227_213, rhone.electorate?.principale)
        assertEquals(7_992, rhone.electorate?.complementaires)
        assertEquals("Populations de référence 2023 (Insee)", rhone.population?.source?.name)
        assertEquals(listOf("69D", "69M"), rhone.collectivites.map { it.code })
        assertEquals(TerritoryCode.region("84"), rhone.region)

        // The Rhône holds two collectivities whose figures add up to the department's.
        val metropole = assertNotNull(national.find(TerritoryCode.collectivite("69M")!!))
        val departement = assertNotNull(national.find(TerritoryCode.collectivite("69D")!!))
        assertTrue(metropole.specialStatus)
        assertFalse(departement.specialStatus)
        assertTrue(metropole.population!!.computed)
        assertNull(metropole.population!!.totale)
        assertEquals(1_914_667, metropole.population!!.municipale + departement.population!!.municipale)
        assertEquals(1_227_213, metropole.electorate!!.principale + departement.electorate!!.principale)

        // Corse: the collectivity holds the powers of the region, and adds up its two departments.
        val corse = assertNotNull(national.find(TerritoryCode.region("94")!!))
        assertEquals(TerritoryCode.collectivite("20R"), corse.heldBy)
        assertEquals(355_486, national.find(TerritoryCode.collectivite("20R")!!)?.population?.municipale)
        // A collectivity that covers exactly one department takes its published figure.
        val ain = assertNotNull(national.find(TerritoryCode.collectivite("01D")!!))
        assertFalse(ain.population!!.computed)
        assertEquals(national.find(TerritoryCode.departement("01")!!)?.population?.municipale, ain.population?.municipale)

        // Overseas collectivities: population from the supplements, voters from Melodi.
        val spm = assertNotNull(national.find(TerritoryCode.outreMer("975")!!))
        assertEquals(5_790, spm.population?.municipale)
        assertEquals("insee-com-2023", spm.population?.source?.id)
        assertEquals(5_059, spm.electorate?.principale)
        assertEquals(68_094_280, national.france.population?.municipale)
        assertEquals(48_154_748, national.france.electorate?.principale)
        assertNotNull(national.francePopulationPerimeter)

        val codes = national.packages.map { it.code }
        assertEquals(listOf("2A", "69", "75", "972", "975", "977", "978", "communes"), codes)
        assertTrue("69001" in assertNotNull(national.entry("69")).postalCodes)
    }

    @Test
    fun theRhonePackageGivesCommunesEpciAndCollectivities() {
        val built = build()
        val pkg = assertNotNull(TerritoiresReader.readPackage(document(built, "69")))
        assertEquals(266, pkg.communes.size)
        val lyon = assertNotNull(pkg.find(TerritoryCode.commune("69123")!!))
        assertEquals(519_127, lyon.population?.municipale)
        assertEquals(318_916, lyon.electorate?.principale)
        assertEquals(2_445, lyon.electorate?.complementaires)
        assertNull(lyon.epci)
        assertEquals(listOf("ctcd:69M", "dep:69", "reg:84", "fr"), lyon.parents.map { it.tag })
        assertEquals(9, pkg.arrondissements.size)

        val villefranche = assertNotNull(pkg.find(TerritoryCode.commune("69264")!!))
        assertEquals(listOf("epci:200040590", "ctcd:69D", "dep:69", "reg:84", "fr"), villefranche.parents.map { it.tag })
        assertEquals(listOf("69400"), villefranche.postalCodes)

        // An EPCI across two departments, with every commune and totals computed on all of them.
        val epci = assertNotNull(pkg.find(TerritoryCode.epci("200040590")!!))
        assertEquals(listOf("01", "69"), epci.departements.map { it.code })
        assertTrue(TerritoryCode.commune("01194") in epci.members)
        assertTrue(epci.population!!.computed)
        assertEquals("CA", epci.nature)
        // The Métropole de Lyon is never an EPCI.
        assertNull(pkg.find(TerritoryCode.epci("200046977")!!))

        val paris = assertNotNull(TerritoiresReader.readPackage(document(built, "75")))
        assertEquals(20, paris.arrondissements.size)
        assertEquals(15_114, paris.find(TerritoryCode.arrondissement("75101")!!)?.population?.municipale)
        assertEquals(listOf("epci:200054781", "ctcd:75C", "dep:75", "reg:11", "fr"), paris.communes.single().parents.map { it.tag })

        val spm = assertNotNull(TerritoiresReader.readPackage(document(built, "975")))
        assertEquals(listOf("comer:975", "fr"), spm.communes.first().parents.map { it.tag })

        val index = assertNotNull(TerritoiresReader.readCommunesIndex(document(built, "communes")))
        assertEquals(425 + 4, index.communes.size)
    }

    @Test
    fun theSameSourcesGiveTheSameBytes() {
        val first = build()
        val second = build()
        assertContentEquals(first.national, second.national)
        assertEquals(first.packages.map { it.sha256 }, second.packages.map { it.sha256 })
        // The national level announces the exact sha256 of each package.
        val national = assertNotNull(TerritoiresReader.readNational(first.national.toString(Charsets.UTF_8)))
        assertEquals(first.packages.map { it.sha256 }, national.packages.map { it.sha256 })
    }

    @Test
    fun aMergedCommuneAddsUpItsFormerCommunesAndASplitOneHasNoFigure() {
        val dir = exampleCopy()
        val communes = dir.resolve(TerritoiresFile.COG_COMMUNES.fileName)
        val lines = communes.readText().lines()
        // Albitreccia (2A008) absorbs Afa (2A001) in 2025; Ajaccio (2A004) is split into 2A004 and 2A999.
        val withoutAfa = lines.filterNot { it.startsWith("\"COM\",\"2A001\"") }
        val ajaccio = lines.first { it.startsWith("\"COM\",\"2A004\"") }
        communes.writeText((withoutAfa.filter { it.isNotEmpty() } + ajaccio.replace("2A004", "2A999").replace("Ajaccio", "Ajaccio Nord")).joinToString("\n") + "\n")
        val events = dir.resolve(TerritoiresFile.COG_EVENTS.fileName)
        events.writeText(
            events.readText().trimEnd() + "\n" +
                "\"32\",\"2025-06-01\",\"COM\",\"2A001\",\"0\",\"AFA\",\"Afa\",\"Afa\",\"COM\",\"2A008\",\"0\",\"ALBITRECCIA\",\"Albitreccia\",\"Albitreccia\"\n" +
                "\"21\",\"2025-06-01\",\"COM\",\"2A004\",\"0\",\"AJACCIO\",\"Ajaccio\",\"Ajaccio\",\"COM\",\"2A004\",\"0\",\"AJACCIO\",\"Ajaccio\",\"Ajaccio\"\n" +
                "\"21\",\"2025-06-01\",\"COM\",\"2A004\",\"0\",\"AJACCIO\",\"Ajaccio\",\"Ajaccio\",\"COM\",\"2A999\",\"0\",\"AJACCIO NORD\",\"Ajaccio Nord\",\"Ajaccio Nord\"\n",
        )
        val statistics = dir.resolve(TerritoiresFile.STATISTICS.fileName).readText().lines()
        fun pmun(code: String) = statistics.first { it.startsWith("COM;$code;PMUN;") }.substringAfterLast(';').toLong()

        val built = build(DirectoryTerritoiresSource(dir, exampleDates, example = true))
        val pkg = assertNotNull(TerritoiresReader.readPackage(document(built, "2A")))
        val albitreccia = assertNotNull(pkg.find(TerritoryCode.commune("2A008")!!))
        assertEquals(pmun("2A008") + pmun("2A001"), albitreccia.population?.municipale)
        assertTrue(albitreccia.population!!.computed)
        assertEquals(listOf("2A001"), albitreccia.formerCodes)
        // Read by its former code too.
        assertEquals(albitreccia, pkg.find(TerritoryCode.commune("2A001")!!))
        assertNull(pkg.find(TerritoryCode.commune("2A004")!!)?.population)
        assertNull(pkg.find(TerritoryCode.commune("2A999")!!)?.population)
    }

    @Test
    fun aFigureThatIsNotAWholeNumberIsRefused() {
        val dir = exampleCopy()
        val statistics = dir.resolve(TerritoiresFile.STATISTICS.fileName)
        statistics.writeText(statistics.readText() + "DEP;01;PMUN;-5\n")
        assertFailsWith<org.politisationpopulairefrancaise.server.elus.ElusFormatException> {
            build(DirectoryTerritoiresSource(dir, exampleDates, example = true))
        }
    }

    @Test
    fun theDocumentsHoldNoUnknownFigureAsZero() {
        val national = TerritoiresJson.parseToJsonElement(build().national.toString(Charsets.UTF_8)).jsonObject
        // Mayotte has no population in force in the supplements yet: the field is absent, never 0.
        val mayotte = national["departements"]!!.jsonArray.map { it.jsonObject }.first { it["code"]!!.jsonPrimitive.content == "976" }
        assertNull(mayotte["population"])
        assertEquals(109_512, (mayotte["inscrits"] as kotlinx.serialization.json.JsonPrimitive).longOrNull)
        assertTrue(national["sources"]!!.jsonArray.all { (it as JsonObject)["licence"] != null })
    }

    @Test
    fun theGuardRefusesATruncatedSource() {
        val previous = mapOf("communes" to 34_875L, "population" to 68_094_280L)
        assertNull(TerritoiresService.guard(null, previous))
        assertNull(TerritoiresService.guard(previous, previous))
        assertNotNull(TerritoiresService.guard(previous, mapOf("communes" to 30_000L, "population" to 68_094_280L)))
        assertNotNull(TerritoiresService.guard(previous, mapOf("communes" to 34_875L, "population" to 60_000_000L)))
    }

    @Test
    fun officialIsHonouredInProductionAndInPreproduction() {
        fun config(vararg pairs: Pair<String, String>) = TerritoiresConfig.fromEnvironment { pairs.toMap()[it] }
        assertEquals(TerritoiresSources.EXAMPLE, config().source)
        assertEquals(TerritoiresSources.OFFICIAL, config("PPF_TERRITOIRES_SOURCE" to "official", "PPF_ENVIRONMENT" to "production").source)
        assertEquals(TerritoiresSources.OFFICIAL, config("PPF_TERRITOIRES_SOURCE" to "official", "PPF_ENVIRONMENT" to "preproduction").source)
        val refused = config("PPF_TERRITOIRES_SOURCE" to "official", "PPF_ENVIRONMENT" to "development")
        assertEquals(TerritoiresSources.EXAMPLE, refused.source)
        assertTrue(refused.officialRefused)
        assertEquals(0, config("PPF_TERRITOIRES_REFRESH_DAYS" to "0").refreshDays)
        assertEquals(30, config("PPF_TERRITOIRES_REFRESH_DAYS" to "-1").refreshDays)
    }
}
