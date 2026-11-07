package org.politisationpopulairefrancaise.core.elus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.politisationpopulairefrancaise.core.publication.ElusDownload
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The index and the packages are read strictly (the document) and tolerantly (its entries): never a crash. */
class ElusReaderTest {

    @Test
    fun aPackageOfTheContractIsRead() {
        val pkg = assertNotNull(ElusReader.readPackage(ElusFixtures.text(ElusFixtures.rhone)))
        assertEquals("69", pkg.code)
        assertEquals("Rhône", pkg.name)
        assertTrue(pkg.example)
        assertEquals(Territory("84", "Auvergne-Rhône-Alpes"), pkg.region)
        assertEquals(CivilDate(2026, 10, 1), pkg.sourceUpdatedOn)
        assertEquals(4, pkg.communes.size)
        val lyon = assertNotNull(pkg.commune("69123"))
        assertEquals(listOf("6901", "6902"), lyon.circonscriptions)
        assertEquals("Métropole de Lyon", pkg.epciName("200046977"))
        val mayor = pkg.officials.first { it.function == "Maire" && it.territory == "69123" }
        assertEquals(Mandate.MUNICIPAL, mayor.mandate)
        assertTrue(mayor.isExample)
        assertEquals("Noa Maire", mayor.displayName)
        assertEquals(CivilDate(2026, 3, 22), mayor.since)
        assertEquals(CivilDate(2026, 3, 28), mayor.functionSince)
        assertEquals(listOf("Albigny-sur-Saône", "Neuville-sur-Saône"), pkg.communesFor(" 69 250 ").map { it.name })
    }

    @Test
    fun anUnreadableDocumentIsIgnoredWithoutCrashing() {
        listOf(
            "",
            "not json",
            "[]",
            "{\"format\":1}",
            "{\"format\":2,\"code\":\"69\",\"name\":\"Rhône\"}",
            "{\"format\":\"1\",\"code\":\"69\",\"name\":\"Rhône\"}",
            "{\"format\":1,\"code\":\"../etc\",\"name\":\"Rhône\"}",
            "{\"format\":1,\"code\":\"69\",\"name\":\"\"}",
            "{\"format\":1,\"code\":\"69\",\"name\":\"Rhône\",\"officials\":", // truncated
        ).forEach { text -> assertNull(ElusReader.readPackage(text), text) }
        listOf("", "{}", "{\"format\":1}", "{\"format\":3,\"packages\":[]}", "{\"format\":1,\"packages\":{}}").forEach { text ->
            assertNull(ElusReader.readIndex(text), text)
        }
    }

    @Test
    fun aBadEntryIsLeftOutAndTheOthersAreKept() {
        val officials = ElusFixtures.rhone["officials"]!!.jsonArray
        val broken = JsonObject(
            ElusFixtures.rhone + mapOf(
                "officials" to JsonArray(
                    officials + listOf(
                        ElusFixtures.official("conseiller-galactique", "69123", "Inconnu Mandat"),
                        JsonObject(ElusFixtures.official("municipal", "69123", "Sans Nom") + mapOf("name" to JsonPrimitive(""))),
                        JsonObject(ElusFixtures.official("municipal", "69123", "Nom Nombre") + mapOf("name" to JsonPrimitive(42))),
                        JsonObject(ElusFixtures.official("municipal", "69123", "Date Fausse") + mapOf("since" to JsonPrimitive("2026-02-30"))),
                        JsonPrimitive("not an official"),
                    ),
                ),
                "communes" to JsonArray(ElusFixtures.rhone["communes"]!!.jsonArray + JsonObject(mapOf("code" to JsonPrimitive("abc"), "name" to JsonPrimitive("Nulle part")))),
                "unknownField" to JsonPrimitive("ignored"),
            ),
        )
        val pkg = assertNotNull(ElusReader.readPackage(broken.toString()))
        assertEquals(officials.size + 1, pkg.officials.size)
        val wrongDate = pkg.officials.single { it.displayName == "Date Fausse" }
        assertNull(wrongDate.since)
        assertEquals(4, pkg.communes.size)
    }

    @Test
    fun theIndexGivesTheDepartmentsOfAPostalCode() {
        val server = ElusFixtures.server()
        val index = assertNotNull(ElusReader.readIndex(server.indexText))
        assertTrue(index.example)
        assertEquals("Licence Ouverte 2.0", index.source?.licence)
        assertEquals(CivilDate(2026, 11, 7), index.importedOn)
        assertEquals(listOf("69"), index.departmentsFor("69250").map { it.code })
        assertEquals(listOf("29"), index.departmentsFor("29990").map { it.code })
        assertEquals(emptyList(), index.departmentsFor("75001"))
        assertEquals(emptyList(), index.departmentsFor("6925"))
        assertEquals("FR", index.national?.code)
        // The national package is never the department of a postal code.
        assertTrue(index.departmentsFor("69001").none { it.isNational })
    }

    @Test
    fun anIndexEntryWithoutAValidFingerprintIsLeftOut() {
        val server = ElusFixtures.server()
        val root = kotlinx.serialization.json.Json.parseToJsonElement(server.indexText).jsonObject
        val packages = root["packages"]!!.jsonArray
        val bad = JsonObject(packages[0].jsonObject + mapOf("sha256" to JsonPrimitive("1234")))
        val tooBig = JsonObject(packages[1].jsonObject + mapOf("bytes" to JsonPrimitive(ElusReader.MAX_PACKAGE_BYTES + 1)))
        val index = assertNotNull(ElusReader.readIndex(JsonObject(root + mapOf("packages" to JsonArray(listOf(bad, tooBig, packages[2])))).toString()))
        assertEquals(listOf("FR"), index.packages.map { it.code })
    }

    @Test
    fun theFingerprintIsTheSha256OfTheExactBytes() {
        // SHA-256 of "abc" (FIPS 180-2, appendix B.1).
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ElusDigest.sha256Hex("abc".encodeToByteArray()))
        assertTrue(ElusDigest.matches("abc".encodeToByteArray(), "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"))
        assertFalse(ElusDigest.matches("abd".encodeToByteArray(), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
    }

    @Test
    fun packageCodesAndPostalCodes() {
        listOf("01", "69", "2A", "2B", "971", "976", "988", "FR").forEach { assertTrue(ElusReader.isPackageCode(it), it) }
        listOf("", "1", "2C", "100", "fr", "69/..", "../69", "6 9").forEach { assertFalse(ElusReader.isPackageCode(it), it) }
        assertEquals("69001", ElusReader.normalizePostalCode(" 69 001 "))
        assertNull(ElusReader.normalizePostalCode("6900"))
        assertNull(ElusReader.normalizePostalCode("69OO1"))
    }

    @Test
    fun theServerAnswersBecomeDownloads() {
        assertEquals("https://example.test/api/v1/elus", ServerPublicationPort.elusUrl("https://example.test", null))
        assertEquals("https://example.test/api/v1/elus/2A", ServerPublicationPort.elusUrl("https://example.test", "2A"))
        val body = assertIs<ElusDownload.Body>(ServerPublicationPort.elusAnswer(200, byteArrayOf(1, 2), "\"abc\"", isPackage = true))
        assertEquals("abc", body.etag)
        assertEquals(ElusDownload.NotModified, ServerPublicationPort.elusAnswer(304, null, null, isPackage = false))
        assertEquals(ElusDownload.UnknownPackage, ServerPublicationPort.elusAnswer(404, null, null, isPackage = true))
        // A server without the directory answers 404 to the index: unavailable, not an unknown package.
        assertIs<ElusDownload.Unavailable>(ServerPublicationPort.elusAnswer(404, null, null, isPackage = false))
        assertIs<ElusDownload.Unavailable>(ServerPublicationPort.elusAnswer(503, null, null, isPackage = true))
    }
}
