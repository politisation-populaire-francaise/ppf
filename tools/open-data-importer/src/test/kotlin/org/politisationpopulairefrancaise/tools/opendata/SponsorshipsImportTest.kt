package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.IndexReading
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataTrust
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndexReader
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorMandate
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsFormat
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The presentations of the presidential election (VO-37), from the file of the Conseil constitutionnel to the package; never the network. */
class SponsorshipsImportTest {
    private val keys = IdentityKeys.generate()
    private val trust = OpenDataTrust.of(keys.publicKey.hex)

    private fun folder(): Path = Files.createTempDirectory("ppf-sponsorships-test")

    private fun index(out: Path) = (ParliamentIndexReader.read(SignedEvent.fromJson(out.resolve(Importer.INDEX_FILE).readText()), trust) as IndexReading.Accepted).index

    @Test
    fun theOfficialFileBecomesAPackageWithoutCivilityAndWithNeutralMandates() {
        val pkg = CcSponsorships.read(ExampleFiles.open("cc/parrainages.json"), example = true)
        assertEquals(12, pkg.sponsorships.size)
        assertEquals("2027-02-19", pkg.publishedOn)
        val mayor = pkg.sponsorships.first { it.territory == "Lyon" }
        assertEquals(SponsorMandate.MAIRE, mayor.mandate)
        assertEquals("[EXEMPLE] EXEMPLAIRE", mayor.lastName)
        assertEquals("Camille", mayor.firstName)
        assertEquals("Rhône", mayor.department)
        assertEquals("2027-02-16", mayor.publishedOn)
        assertEquals(SponsorMandate.DEPUTE, pkg.sponsorships.first { it.lastName.endsWith("BROUILLON") }.mandate)
        assertEquals(SponsorMandate.SENATEUR, pkg.sponsorships.first { it.lastName.endsWith("ÉBAUCHE") }.mandate)
        assertEquals(SponsorMandate.DEPUTE_EUROPEEN, pkg.sponsorships.first { it.department == "Parlement européen" }.mandate)
        assertNull(pkg.sponsorships.first { it.lastName.endsWith("ÉBAUCHE") }.territory, "an empty constituency is no territory")
        val text = SponsorshipsFormat.write(pkg)
        assertFalse("Civilite" in text || "Mme" in text || "\"M.\"" in text, "the civility is never kept")
        assertFalse("Sénatrice" in text || "Députée" in text, "nor the gender of the official label")
        // Stable order: candidate, department, name.
        assertEquals(pkg.sponsorships.sortedWith(SponsorshipsFormat.ORDER), pkg.sponsorships)
    }

    @Test
    fun theDatesOfTheJsonAndOfTheCsvAreRead() {
        assertEquals("2022-02-01", CcSponsorships.day("2022-02-01T00:00:00"))
        assertEquals("2022-02-01", CcSponsorships.day("01/02/2022"))
        assertNull(CcSponsorships.day("1er février"))
    }

    @Test
    fun theImportAddsThePackageAndItsSourceThenKeepsItAndRefusesATruncatedFileWithoutStoppingTheParliament() {
        val out = folder()
        assertEquals(ExitCodes.OK, Importer(ExampleSources(), keys, out, log = {}, sponsorships = true).run())
        val first = index(out)
        assertTrue(first.packages.any { it.name == SponsorshipsFormat.PACKAGE_2027 })
        assertEquals("cc", first.sources.last().id)
        val bytes = Files.readAllBytes(out.resolve(SponsorshipsFormat.PACKAGE_2027 + ".json"))
        assertEquals(12, SponsorshipsFormat.read(bytes.decodeToString())?.sponsorships?.size)

        // Not asked: the previous package and its source are kept as they are.
        assertEquals(ExitCodes.OK, Importer(ExampleSources(), keys, out, log = {}).run())
        assertTrue(index(out).packages.any { it.name == SponsorshipsFormat.PACKAGE_2027 })
        assertEquals("cc", index(out).sources.last().id)

        // A file with far fewer presentations (truncated) is refused and changes nothing, but the Parliament is imported.
        val truncated = object : ImportSources by ExampleSources() {
            override fun sponsorships(): InputStream = """[{"Civilite":"M.","Nom":"[EXEMPLE] SEUL","Prenom":"Un","Mandat":"Maire","Circonscription":"Lyon","Departement":"Rhône","Candidat":"[EXEMPLE] CANDIDATE Alice","DatePublication":"2027-02-23T00:00:00"}]""".byteInputStream()
        }
        val lines = ArrayList<String>()
        assertEquals(ExitCodes.OK, Importer(truncated, keys, out, log = { lines += it }, sponsorships = true).run())
        assertTrue(bytes.contentEquals(Files.readAllBytes(out.resolve(SponsorshipsFormat.PACKAGE_2027 + ".json"))))
        assertTrue(lines.any { it.startsWith("sponsorships refused:") })
        assertEquals("cc", index(out).sources.last().id)
    }

    @Test
    fun aFailureOfThePresentationsNeverStopsTheImportOfTheParliament() {
        val out = folder()
        val unreachable = object : ImportSources by ExampleSources() {
            override fun sponsorships(): InputStream = throw DownloadException("www.data.gouv.fr: HTTP 503")
        }
        val lines = ArrayList<String>()
        assertEquals(ExitCodes.OK, Importer(unreachable, keys, out, log = { lines += it }, sponsorships = true).run())
        val index = index(out)
        assertTrue(index.packages.any { it.name == "membres" }, "the Parliament is imported")
        assertTrue(index.packages.none { it.name == SponsorshipsFormat.PACKAGE_2027 })
        assertTrue(index.sources.none { it.id == "cc" })
        assertTrue(lines.any { it.startsWith("sponsorships download failed:") })

        // An unexpected error says its kind only, never a value of the data.
        val broken = object : ImportSources by ExampleSources() {
            override fun sponsorships(): InputStream = "[{\"Nom\": \"[EXEMPLE] Secret\"".byteInputStream()
        }
        lines.clear()
        assertEquals(ExitCodes.OK, Importer(broken, keys, out, log = { lines += it }, sponsorships = true).run())
        assertTrue(lines.none { "Secret" in it })
    }

    @Test
    fun theOptionAsksThePresentationsByNameOrByTheDatasetInProduction() {
        val base = listOf("--out", "x", "--key", "k")
        assertTrue(parseOptions(listOf("--only", "an,cc") + base) { null }.getOrThrow().sponsorships)
        assertFalse(parseOptions(base) { null }.getOrThrow().sponsorships, "not by default in example mode")
        val env = mapOf("PPF_SPONSORSHIPS_DATASET" to "parrainages-2027")
        val official = parseOptions(base + listOf("--source", "official")) { env[it] }.getOrThrow()
        assertTrue(official.sponsorships)
        assertEquals("parrainages-2027", official.sponsorshipsDataset)
        assertTrue("www.data.gouv.fr" in ALLOWED_HOSTS && "static.data.gouv.fr" in ALLOWED_HOSTS)
    }
}
