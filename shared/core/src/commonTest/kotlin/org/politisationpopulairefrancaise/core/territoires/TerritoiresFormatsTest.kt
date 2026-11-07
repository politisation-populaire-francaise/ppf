package org.politisationpopulairefrancaise.core.territoires

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerritoiresFormatsTest {

    @Test
    fun codesAreParsedAndWrittenAsTags() {
        assertEquals(TerritoryCode.FRANCE, TerritoryCode.parse("fr"))
        assertEquals(TerritoryCode.FRANCE, TerritoryCode.parse(" FR "))
        assertEquals("com:69123", TerritoryCode.parse("COM:69123")?.tag)
        assertEquals("dep:2A", TerritoryCode.parse(" dep:2a ")?.tag)
        assertEquals("ctcd:69M", TerritoryCode.parse("ctcd:69m")?.tag)
        assertEquals("comer:987", TerritoryCode.parse("comer:987")?.tag)
        assertEquals("epci:200040590", TerritoryCode.parse("epci:200040590")?.tag)
        assertEquals("arm:75101", TerritoryCode.parse("arm:75101")?.tag)
        assertEquals("reg:84", TerritoryCode.parse("reg:84")?.tag)
        assertEquals("com:2A004", TerritoryCode.commune("2A004")?.tag)
        assertEquals("dep:974", TerritoryCode.departement("974")?.tag)
        for (wrong in listOf("dep:20", "dep:96", "com:2C001", "epci:12", "ctcd:01X", "ville:Lyon", "Lyon", "", "com:", ":69", "fr:x", "dep:69 ; drop")) {
            assertNull(TerritoryCode.parse(wrong), wrong)
        }
        assertNull(TerritoryCode.parse(null))
        assertEquals(TerritoryCode.commune("69123"), TerritoryCode.parse("com:69123"))
        val code = assertNotNull(TerritoryCode.parse("dep:69"))
        assertEquals(code, TerritoryCode.parse(code.tag))
    }

    @Test
    fun theNationalLevelIsReadStrictlyAndTolerantly() {
        val national = assertNotNull(TerritoiresReader.readNational(TerritoiresFixtures.national()))
        assertTrue(national.example)
        assertTrue(national.partial)
        assertEquals(2, national.regions.size)
        // The department "20" does not exist: left out, the others are kept.
        assertEquals(listOf("69", "2A", "976"), national.departements.map { it.code.code })
        val rhone = assertNotNull(national.find(TerritoryCode.departement("69")!!))
        assertEquals(1_914_667, rhone.population?.municipale)
        assertEquals(1_940_015, rhone.population?.totale)
        assertFalse(rhone.population!!.computed)
        assertEquals("popref-2023", rhone.population?.source?.id)
        assertEquals("reu-2026-02-11", rhone.electorate?.source?.id)
        assertEquals(SpreadRule.COUNTS, rhone.spread)
        assertEquals(listOf("reg:84", "fr"), rhone.parents.map { it.tag })
        assertEquals("69", rhone.packageCode)
        // Mayotte: no population in force here, registered voters known.
        val mayotte = assertNotNull(national.find(TerritoryCode.departement("976")!!))
        assertNull(mayotte.population)
        assertEquals(109_512, mayotte.electorate?.principale)

        val metropole = assertNotNull(national.find(TerritoryCode.collectivite("69M")!!))
        assertTrue(metropole.specialStatus)
        assertTrue(metropole.population!!.computed)
        assertNull(metropole.population!!.totale)
        assertEquals(TerritoryCode.collectivite("20R"), national.find(TerritoryCode.region("94")!!)?.heldBy)

        val spm = assertNotNull(national.find(TerritoryCode.outreMer("975")!!))
        assertEquals("insee-com-2023", spm.population?.source?.id)
        assertEquals(listOf("dep:69", "dep:2A", "dep:976", "comer:975"), national.spreadUnits().map { it.code.tag })
        assertEquals(5, national.spreadUnits(includeToConfirm = true).size)

        assertEquals(listOf("69"), national.packagesFor("69 400").map { it.code })
        assertEquals(emptyList(), national.packagesFor("6940"))
        assertEquals("France hors Mayotte et hors collectivités d'outre-mer", national.francePopulationPerimeter)
        assertEquals(68_094_280, national.france.population?.municipale)
    }

    @Test
    fun aDocumentOfAnotherFormatOrBrokenIsIgnored() {
        assertNull(TerritoiresReader.readNational(TerritoiresFixtures.national().replace("\"format\":1", "\"format\":2")))
        assertNull(TerritoiresReader.readNational("{"))
        assertNull(TerritoiresReader.readNational("[]"))
        assertNull(TerritoiresReader.readPackage(TerritoiresFixtures.rhonePackage.replace("\"code\":\"69\",\"name\":\"Rhône\"", "\"code\":\"FR\",\"name\":\"Rhône\"")))
        assertNull(TerritoiresReader.readCommunesIndex(TerritoiresFixtures.rhonePackage))
    }

    @Test
    fun aPackageGivesCommunesWithTheirParentsAndFormerCodes() {
        val pkg = assertNotNull(TerritoiresReader.readPackage(TerritoiresFixtures.rhonePackage))
        // Two broken communes left out.
        assertEquals(3, pkg.communes.size)
        val lyon = assertNotNull(pkg.find(TerritoryCode.commune("69123")!!))
        assertEquals(listOf("ctcd:69M", "dep:69", "reg:84", "fr"), lyon.parents.map { it.tag })
        assertEquals(TerritoryCode.collectivite("69M"), lyon.collectivite)
        assertEquals(2_445, lyon.electorate?.complementaires)
        val merged = assertNotNull(pkg.find(TerritoryCode.commune("69159")!!))
        assertEquals("69114", merged.code.code)
        assertTrue(merged.population!!.computed)
        val district = assertNotNull(pkg.find(TerritoryCode.arrondissement("69381")!!))
        assertEquals(TerritoryCode.commune("69123"), district.parents.first())
        val epci = assertNotNull(pkg.find(TerritoryCode.epci("200040590")!!))
        assertEquals(3, epci.members.size)
        assertEquals(listOf("01", "69"), epci.departements.map { it.code })

        val index = assertNotNull(TerritoiresReader.readCommunesIndex(TerritoiresFixtures.communesIndex))
        assertEquals(4, index.communes.size)
    }

    @Test
    fun theDigestIsTheSha256OfTheExactBytes() {
        val bytes = "{}".encodeToByteArray()
        assertEquals("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a", TerritoiresDigest.sha256Hex(bytes))
        assertTrue(TerritoiresDigest.matches(bytes, " 44136FA355B3678A1146AD16F7E8649E94FB4FC21FE77E8310C060F61CAAFF8A "))
    }

    @Test
    fun sharesAddUpExactlyToTheTotal() {
        val national = assertNotNull(TerritoiresReader.readNational(TerritoiresFixtures.national()))
        val shares = TerritoryShares.proportional(500_001, national.spreadUnits(includeToConfirm = true))
        assertEquals(500_001, shares.parts.sumOf { it.target })
        // Mayotte (no population here) and Nouvelle-Calédonie are left out, and said so.
        assertEquals(listOf("dep:976", "comer:988"), shares.missing.map { it.code.tag })
        val rhone = shares.parts.first { it.territory.code.code == "69" }
        assertEquals(1_914_667, rhone.weight)
        // 1 914 667 / (1 914 667 + 168 306 + 5 790) of 500 001.
        assertEquals(458_326, rhone.target)

        val voters = TerritoryShares.proportional(1_000, national.spreadUnits(), ShareBasis.ELECTORATE)
        assertEquals(1_000, voters.parts.sumOf { it.target })
        assertEquals(emptyList(), voters.missing)

        // Ties: the larger weight, then the smaller code, take the remainder; always the same result.
        val a = Territory(TerritoryCode.departement("01")!!, "A", "de A", emptyList(), Population(1, null, null, false), null)
        val b = a.copy(code = TerritoryCode.departement("02")!!)
        val c = a.copy(code = TerritoryCode.departement("03")!!)
        assertEquals(listOf(1L, 1L, 0L), TerritoryShares.proportional(2, listOf(c, b, a)).parts.sortedBy { it.territory.code.code }.map { it.target })
        assertEquals(listOf(0L, 0L, 0L), TerritoryShares.proportional(0, listOf(a, b, c)).parts.map { it.target })
        assertEquals(emptyList(), TerritoryShares.proportional(10, emptyList()).parts)
    }
}
