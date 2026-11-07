package org.politisationpopulairefrancaise.core.territoires

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The referential on the device: shipped national level, downloads, search, assemblies, updates. */
class TerritoiresTest {
    private val bundledText = TerritoiresFixtures.national()

    private fun territoires(server: TerritoiresFixtures.FakeServer? = null, storage: TerritoiresStorage = InMemoryTerritoiresStorage(), bundled: String? = bundledText) =
        Territoires(storage, server, bundled = { bundled }, clock = { 1_800_000_000 })

    private fun server(national: String = bundledText) = TerritoiresFixtures.FakeServer(
        mapOf(null to national, "69" to TerritoiresFixtures.rhonePackage, "communes" to TerritoiresFixtures.communesIndex),
    )

    @Test
    fun theShippedNationalLevelWorksWithoutServer() = runTest {
        val t = territoires()
        val national = assertNotNull(t.national())
        assertEquals(1_914_667, t.find(TerritoryCode.departement("69")!!)?.population?.municipale)
        assertEquals("Métropole de Lyon", t.resolve("ctcd:69M")?.name)
        assertNull(t.resolve("com:69123"))
        assertNull(t.resolve("n'importe quoi"))
        assertIs<TerritoiresDownload.NoServer>(t.download(t.plan("69001")))
        assertIs<TerritoiresUpdate.NoServer>(t.update())
        assertEquals(4, national.spreadUnits().size)
        assertNull(territoires(bundled = null).national())
    }

    @Test
    fun aPostalCodePlansItsDepartmentWithoutSendingIt() = runTest {
        val fake = server()
        val t = territoires(fake)
        val plan = t.plan("69400")
        assertEquals(listOf("69"), plan.missing.map { it.code })
        assertEquals(TerritoiresFixtures.rhonePackage.encodeToByteArray().size.toLong(), plan.bytes)
        // Nothing was asked to the server to plan: the national level is on the device.
        assertEquals(emptyList(), fake.asked)
        assertEquals(TerritoiresDownload.Done(listOf("69")), t.download(plan))
        assertEquals(listOf<String?>("69"), fake.asked)
        assertTrue(t.plan("69400").isEmpty)

        val villefranche = assertNotNull(t.find(TerritoryCode.commune("69264")!!))
        assertEquals(21_000, villefranche.electorate?.principale)
        assertEquals(300, villefranche.electorate?.complementaires)
        // The assemblies a resident can petition: commune, EPCI, departmental collectivity, region.
        assertEquals(
            listOf("com:69264", "epci:200040590", "ctcd:69D", "reg:84"),
            assertNotNull(t.assembliesFor(villefranche.code)).map { it.code.tag },
        )
        assertEquals(listOf("com:69123", "ctcd:69M", "reg:84"), assertNotNull(t.assembliesFor(TerritoryCode.commune("69123")!!)).map { it.code.tag })
        assertNull(t.assembliesFor(TerritoryCode.commune("2A004")!!))
        // A former code still leads to the commune.
        assertEquals("69114", t.resolve("com:69159")?.code?.code)
        assertEquals(listOf("Porte des Pierres Dorées", "Villefranche-sur-Saône"), assertNotNull(t.communesOf(TerritoryCode.collectivite("69D")!!)).map { it.name })
        assertEquals(3, assertNotNull(t.communesOf(TerritoryCode.departement("69")!!)).size)
        assertNull(t.communesOf(TerritoryCode.departement("2A")!!))
    }

    @Test
    fun aPackageThatDoesNotMatchItsFingerprintOrClaimsAnotherCodeIsRefused() = runTest {
        val tampered = TerritoiresFixtures.FakeServer(mapOf("69" to TerritoiresFixtures.rhonePackage.replace("519127", "519128")))
        val storage = InMemoryTerritoiresStorage()
        val t = territoires(tampered, storage)
        assertEquals(TerritoiresDownload.Refused("69"), t.download(t.plan("69001")))
        assertEquals(emptyList(), storage.codes())

        // The right bytes, announced for another code: refused too.
        val other = TerritoiresFixtures.national().replace("\"code\":\"69\",\"name\":\"Rhône\",\"sha256\"", "\"code\":\"2A\",\"name\":\"Rhône\",\"sha256\"")
        val confused = territoires(TerritoiresFixtures.FakeServer(mapOf("2A" to TerritoiresFixtures.rhonePackage)), bundled = other)
        assertEquals(TerritoiresDownload.Refused("2A"), confused.download(confused.planPackages(listOf("2A"))))

        val offline = TerritoiresFixtures.FakeServer(mapOf()).also { it.reachable = false }
        assertEquals(TerritoiresDownload.Unreachable, territoires(offline).download(territoires(offline).plan("69001")))
    }

    @Test
    fun searchStaysOnTheDeviceAndFindsNamesCodesAndPostalCodes() = runTest {
        val fake = server()
        val t = territoires(fake)
        assertEquals("dep:69", t.search("Rhône").first().code.tag)
        assertEquals("dep:69", t.search("69").first().code.tag)
        assertEquals("dep:2A", t.search("2a").first().code.tag)
        assertEquals("ctcd:69M", t.search("metropole de lyon").first().code.tag)
        // Communes of a package not on the device are not known yet.
        assertEquals(emptyList(), t.search("Villefranche", setOf(TerritoryType.COMMUNE)))
        // A postal code gives the department while the package is missing.
        assertEquals(listOf("dep:69"), t.search("69400").map { it.code.tag })

        t.download(t.plan("69400"))
        assertEquals("com:69264", t.search("villefranche sur saone").first().code.tag)
        assertEquals("com:69264", t.search("Villefranche-s").first().code.tag)
        // Communes first, the most populated first, then the department.
        assertEquals(listOf("com:69264", "com:69114", "dep:69"), t.search("69400").map { it.code.tag })
        assertEquals("com:69114", t.search("69159").firstOrNull()?.code?.tag)
        assertEquals(listOf("epci:200040590"), t.search("Beaujolais", setOf(TerritoryType.EPCI)).map { it.code.tag })

        // The index of the names finds every commune, without figures until its package is there.
        assertEquals(emptyList(), t.search("Ajaccio", setOf(TerritoryType.COMMUNE)))
        t.download(t.planPackages(listOf(TerritoiresReader.COMMUNES_CODE)))
        val ajaccio = t.search("ajaccio", setOf(TerritoryType.COMMUNE)).single()
        assertEquals("com:2A004", ajaccio.code.tag)
        assertNull(ajaccio.population)
        assertEquals(TerritoryCode.departement("2A"), ajaccio.departement)
        assertEquals(TerritoryCode.region("94"), ajaccio.region)
        assertEquals(listOf("Saint-Pierre-et-Miquelon"), t.search("st pierre").map { it.name })
        // Only the downloads went to the server, never a search.
        assertEquals(listOf<String?>("69", "communes"), fake.asked)
        assertEquals(emptyList(), t.search("   "))
    }

    @Test
    fun anUpdateTakesTheNewNationalLevelAndThePackagesThatChanged() = runTest {
        val storage = InMemoryTerritoiresStorage()
        val fake = server()
        val t = territoires(fake, storage)
        t.download(t.plan("69001"))
        // Same national level as the shipped one: 304, nothing else to do.
        assertEquals(TerritoiresUpdate.UpToDate, t.update())

        val newer = TerritoiresFixtures.rhonePackage.replace("519127", "520000")
        fake.documents = mapOf(
            null to TerritoiresFixtures.national(TerritoiresFixtures.sha(newer), newer.encodeToByteArray().size, builtOn = "2027-01-01"),
            "69" to newer,
        )
        assertEquals(TerritoiresUpdate.Updated(national = true, packages = listOf("69")), t.update())
        assertEquals(520_000, t.find(TerritoryCode.commune("69123")!!)?.population?.municipale)
        assertEquals("2027-01-01", t.national()?.builtOn?.toIso())

        // After a restart, the stored copy (newer than the shipped one) is used, offline.
        val restarted = territoires(null, storage)
        assertEquals("2027-01-01", restarted.national()?.builtOn?.toIso())
        assertEquals(520_000, restarted.find(TerritoryCode.commune("69123")!!)?.population?.municipale)

        // A damaged copy on the device is ignored.
        storage.save(storage.load("69")!!.copy(json = "{}"))
        assertNull(restarted.find(TerritoryCode.commune("69123")!!))

        fake.reachable = false
        assertEquals(TerritoiresUpdate.Unreachable, t.update())
        fake.reachable = true
        fake.documents = mapOf(null to "{\"format\":9}")
        assertEquals(TerritoiresUpdate.Unreadable, t.update())
    }

    @Test
    fun forgettingErasesTheDownloadsAndKeepsTheShippedLevel() = runTest {
        val storage = InMemoryTerritoiresStorage()
        val t = territoires(server(), storage)
        t.download(t.plan("69001"))
        t.forget()
        assertEquals(emptyList(), storage.codes())
        assertNull(t.find(TerritoryCode.commune("69123")!!))
        assertNotNull(t.national())
    }
}
