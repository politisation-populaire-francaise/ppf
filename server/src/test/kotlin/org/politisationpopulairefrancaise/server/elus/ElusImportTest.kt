package org.politisationpopulairefrancaise.server.elus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Imports of the directory into PostgreSQL: the example set, determinism, the guards. */
class ElusImportTest {
    private val clock = MutableClock(Instant.parse("2026-11-07T08:00:00Z"))
    private val store = ElusStore(TestDatabase.dataSource)
    private val service = ElusService(store, ElusSetup(ElusConfig(refreshDays = 0), clock))
    private val directories = ArrayList<Path>()

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    @AfterTest
    fun removeDirectories() = directories.forEach { dir -> dir.toFile().deleteRecursively() }

    private fun importExample(): ImportOutcome.Imported = assertIs<ImportOutcome.Imported>(service.importNow(ExampleSource()))

    private fun packageJson(code: String): JsonObject = ElusJson.parseToJsonElement(assertNotNull(service.packageBody(code)).toString(Charsets.UTF_8)).jsonObject

    /** The example set, with [file] replaced by [text]. */
    private fun exampleWith(file: ElusFile, text: String): ElusSource {
        // A Path is also an Iterable<Path>: add() rather than +=.
        val dir = Files.createTempDirectory("ppf-elus-test-").also { directories.add(it) }
        dir.resolve(file.fileName).writeText(text, Charsets.UTF_8)
        return DirectorySource(dir, LocalDate.parse("2026-10-01"), example = true, fallback = ExampleSource())
    }

    private fun exampleText(file: ElusFile): String =
        ExampleSource::class.java.getResourceAsStream("${ExampleSource.RESOURCE_DIRECTORY}/${file.fileName}")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun theExampleSetGivesOnePackagePerDepartmentAndTheNationalOne() {
        val outcome = importExample()
        assertEquals(
            mapOf(
                Mandate.MUNICIPAL to 55, Mandate.COMMUNAUTAIRE to 14, Mandate.DEPARTEMENTAL to 26, Mandate.REGIONAL to 19,
                Mandate.COLLECTIVITE to 4, Mandate.DEPUTE to 16, Mandate.SENATEUR to 10, Mandate.EUROPEEN to 3,
            ),
            outcome.counts,
        )
        val served = assertNotNull(service.current()).served
        assertEquals(listOf("13", "35", "59", "69", "75", "FR"), served.packages.keys.sorted())
        val index = ElusJson.decodeFromString(ElusIndex.serializer(), served.index.toString(Charsets.UTF_8))
        assertEquals(true, index.example)
        assertEquals(ELUS_FORMAT, index.format)
        assertEquals(ElusSourceInfo(ElusSourceDescription.NAME, ElusSourceDescription.URL, ElusSourceDescription.LICENCE, "2026-10-01"), index.source)
        assertEquals("2026-11-07", index.importedOn)
        assertEquals(listOf("13", "35", "59", "69", "75", "FR"), index.packages.map { it.code })
        for (entry in index.packages) {
            val body = assertNotNull(service.packageBody(entry.code))
            assertEquals(entry.sha256, sha256Hex(body), entry.code)
            assertEquals(entry.bytes, body.size.toLong(), entry.code)
        }
        assertEquals(listOf("69001", "69002", "69003", "69004", "69005", "69006", "69007", "69008", "69009", "69100", "69400"), index.packages.first { it.code == "69" }.postalCodes)
        assertEquals(emptyList(), index.packages.first { it.code == "FR" }.postalCodes)
    }

    @Test
    fun theRhonePackageFollowsTheContract() {
        importExample()
        val pack = ElusJson.decodeFromString(ElusPackage.serializer(), assertNotNull(service.packageBody("69")).toString(Charsets.UTF_8))
        assertEquals("Rhône", pack.name)
        assertEquals(ElusRegion("84", "Auvergne-Rhône-Alpes"), pack.region)
        assertEquals("2026-10-01", pack.sourceUpdatedOn)
        assertEquals(listOf("Lyon", "Villefranche-sur-Saône", "Villeurbanne"), pack.communes.map { it.name })
        val lyon = pack.communes.first { it.code == "69123" }
        // Lyon: postal codes of its districts, no intercommunality nor canton (Métropole de Lyon),
        // and shared between four constituencies, all kept.
        assertEquals(ElusCommune("69123", "Lyon", (1..9).map { "6900$it" }, null, emptyList(), listOf("6901", "6902", "6903", "6904"), collectivite = "69M"), lyon)
        // Its departmental level is the assembly of the Métropole, named in the package.
        assertEquals(listOf(ElusNamed("69M", "Métropole de Lyon")), pack.collectivites)
        assertEquals(ElusCommune("69264", "Villefranche-sur-Saône", listOf("69400"), "200040590", listOf("6913"), listOf("6909")), pack.communes.first { it.code == "69264" })
        assertEquals(listOf(ElusNamed("200040590", "Ca Villefranche Beaujolais Saône")), pack.epcis)
        assertEquals(listOf(ElusNamed("6913", "Villefranche-sur-Saône")), pack.cantons)
        assertEquals(ElusNamed("6901", "1re circonscription du Rhône"), pack.circonscriptions.first())
        assertEquals(listOf("6901", "6902", "6903", "6904", "6906", "6909"), pack.circonscriptions.map { it.code })

        val byMandate = pack.officials.groupBy { it.mandate }
        assertEquals(listOf("municipal", "communautaire", "departemental", "regional", "collectivite", "depute", "senateur"), byMandate.keys.toList())
        // The council of Lyon in the order of the table: mayor, deputies, then members by name.
        val lyonCouncil = pack.officials.filter { it.mandate == "municipal" && it.territory == "69123" }
        assertEquals(listOf("Maire", "1er adjoint au Maire", "2ème adjoint au Maire", null, null), lyonCouncil.map { it.function })
        assertTrue(lyonCouncil.drop(3).let { it[0].name.lowercase() <= it[1].name.lowercase() })
        assertTrue(pack.officials.all { it.name.startsWith("[EXEMPLE] ") })
        assertEquals("[EXEMPLE] Camille Exemplaire", lyonCouncil.first().name)
        // A community councillor names the commune they represent; nobody else has `commune`.
        assertTrue(byMandate.getValue("communautaire").all { it.commune == "69264" && it.territory == "200040590" })
        assertTrue(pack.officials.filter { it.mandate != "communautaire" }.all { it.commune == null })
        // The president of the regional council sits for the Isère: still in every package of the region.
        val regional = byMandate.getValue("regional")
        assertEquals(5, regional.size)
        assertEquals("Président du conseil régional", regional.first().function)
        assertTrue(regional.all { it.territory == "84" })
        assertEquals(setOf("69M"), byMandate.getValue("collectivite").map { it.territory }.toSet())
        assertEquals(listOf("6901", "6902", "6906", "6909"), byMandate.getValue("depute").map { it.territory })
        assertEquals(listOf("69", "69"), byMandate.getValue("senateur").map { it.territory })
    }

    @Test
    fun theJsonHasExactlyTheFieldsOfTheContract() {
        importExample()
        val json = packageJson("69")
        assertEquals(listOf("format", "example", "code", "name", "region", "sourceUpdatedOn", "communes", "epcis", "cantons", "circonscriptions", "officials", "collectivites"), json.keys.toList())
        assertEquals(listOf("code", "name", "postalCodes", "epci", "cantons", "circonscriptions", "collectivite"), json["communes"]!!.jsonArray.first().jsonObject.keys.toList())
        // A commune of an ordinary department has no `collectivite`.
        assertEquals(listOf("code", "name", "postalCodes", "epci", "cantons", "circonscriptions"), json["communes"]!!.jsonArray.first { it.jsonObject["code"]!!.jsonPrimitive.content == "69264" }.jsonObject.keys.toList())
        val officials = json["officials"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("mandate", "territory", "name", "function", "since", "functionSince"), officials.first().keys.toList())
        assertEquals(listOf("mandate", "territory", "commune", "name", "function", "since", "functionSince"), officials.first { it["mandate"]!!.jsonPrimitive.content == "communautaire" }.keys.toList())
        // `function` and `functionSince` are written as null when the RNE gives none.
        val member = officials.first { it["function"] is kotlinx.serialization.json.JsonNull }
        assertTrue(member.containsKey("functionSince"))
        val national = packageJson("FR")
        assertEquals("France", national["name"]!!.jsonPrimitive.content)
        assertEquals(kotlinx.serialization.json.JsonNull, national["region"])
        assertEquals(3, (national["officials"] as JsonArray).size)
        assertTrue((national["officials"] as JsonArray).all { it.jsonObject["mandate"]!!.jsonPrimitive.content == "europeen" && it.jsonObject["territory"]!!.jsonPrimitive.content == NATIONAL_PACKAGE })
    }

    @Test
    fun noPersonalColumnBeyondTheContractReachesAPackage() {
        importExample()
        val allowed = setOf("mandate", "territory", "commune", "name", "function", "since", "functionSince")
        for (code in listOf("13", "35", "59", "69", "75", "FR")) {
            val text = assertNotNull(service.packageBody(code)).toString(Charsets.UTF_8)
            // The example files give everyone a fictitious occupation, sex, nationality and a birth date in the 20th century.
            assertTrue("Profession fictive" !in text, code)
            assertNull(Regex("\"19\\d\\d-\\d\\d-\\d\\d\"").find(text), code)
            for (official in packageJson(code)["officials"]!!.jsonArray) {
                assertTrue(official.jsonObject.keys.all { it in allowed }, code)
            }
        }
        // The deputy of the French abroad has no package.
        for (code in listOf("13", "35", "59", "69", "75", "FR")) assertTrue("ZZ01" !in assertNotNull(service.packageBody(code)).toString(Charsets.UTF_8), code)
    }

    @Test
    fun theSameDataGiveTheSameBytes() {
        importExample()
        val first = assertNotNull(service.current()).served
        val bodies = first.packages.keys.associateWith { assertNotNull(service.packageBody(it)) }
        clock.advanceSeconds(java.time.Duration.ofDays(8).seconds)
        importExample()
        val second = assertNotNull(service.current()).served
        assertTrue(second.id != first.id)
        assertEquals(first.packages, second.packages)
        first.packages.keys.forEach { assertContentEquals(bodies.getValue(it), service.packageBody(it), it) }
        // Only the import served remains.
        assertEquals("ready", store.lastAttempt()?.status)
    }

    @Test
    fun anImportLosingMoreThanATwentiethOfAMandateIsRefusedAndThePreviousOneKept() {
        importExample()
        val served = assertNotNull(service.current()).served
        val municipal = exampleText(ElusFile.MUNICIPAL).lines().filter { it.isNotBlank() }
        // 40 of 55 municipal councillors: 27 % fewer, a truncated file.
        val truncated = exampleWith(ElusFile.MUNICIPAL, municipal.take(41).joinToString("\r\n"))
        val refused = assertIs<ImportOutcome.Refused>(service.importNow(truncated))
        assertEquals("drop:municipal:55:40", refused.reason)
        assertEquals(served.id, assertNotNull(service.reload()).served.id)
        assertEquals("failed", store.lastAttempt()?.status)
        assertEquals("drop:municipal:55:40", store.lastFailure())
        // 45 of 55 (18 % fewer) is accepted: councils change.
        assertIs<ImportOutcome.Imported>(service.importNow(exampleWith(ElusFile.MUNICIPAL, municipal.take(46).joinToString("\r\n"))))
    }

    @Test
    fun anImportThatFailsLeavesThePreviousOneServed() {
        importExample()
        val served = assertNotNull(service.current()).served
        val broken = exampleText(ElusFile.DEPUTE).replace("Date de début du mandat", "Début")
        val failed = assertIs<ImportOutcome.Failed>(service.importNow(exampleWith(ElusFile.DEPUTE, broken)))
        assertEquals("format: elus-deputes-dep.csv lacks the column(s) \"Date de début du mandat\".", failed.reason)
        val again = assertNotNull(service.reload()).served
        assertEquals(served.id, again.id)
        assertEquals(served.packages, again.packages)
        assertNotNull(service.packageBody("69"))
    }

    @Test
    fun aPersonOfTheExampleSetMustBeMarked() {
        val unmarked = exampleText(ElusFile.SENATEUR).replaceFirst("[EXEMPLE] ", "")
        val failed = assertIs<ImportOutcome.Failed>(service.importNow(exampleWith(ElusFile.SENATEUR, unmarked)))
        assertTrue(failed.reason.contains("not marked [EXEMPLE]"), failed.reason)
        assertNull(service.reload())
    }

    @Test
    fun theScheduleImportsWhenNothingIsServedThenEveryPeriod() {
        val weekly = ElusService(store, ElusSetup(ElusConfig(refreshDays = 7), clock))
        assertEquals(java.time.Duration.ZERO, weekly.untilNextImport(clock.instant()))
        assertIs<ImportOutcome.Imported>(weekly.importNow(ExampleSource()))
        assertEquals(java.time.Duration.ofDays(7), weekly.untilNextImport(clock.instant()))
        clock.advanceSeconds(java.time.Duration.ofDays(2).seconds)
        assertEquals(java.time.Duration.ofDays(5), weekly.untilNextImport(clock.instant()))
        // A failed attempt is tried again after six hours, not after a week.
        assertIs<ImportOutcome.Failed>(weekly.importNow(exampleWith(ElusFile.DEPUTE, "nothing")))
        assertEquals(ElusService.RETRY, weekly.untilNextImport(clock.instant()))
        // Another source than the one served: imported at once.
        val official = ElusService(store, ElusSetup(ElusConfig(source = ElusSources.OFFICIAL, refreshDays = 7), clock))
        official.reload()
        clock.advanceSeconds(ElusService.RETRY.seconds)
        assertEquals(java.time.Duration.ZERO, official.untilNextImport(clock.instant()))
    }
}
