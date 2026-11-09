package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.application.ApplicationFormat
import org.politisationpopulairefrancaise.core.voir.application.MeasureState
import org.politisationpopulairefrancaise.core.voir.parlement.IndexReading
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataTrust
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndexReader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The application of the laws (VO-36), from the two files of the baromètre of the Assemblée nationale to one package per legislature; never the network. */
class ApplicationImportTest {
    private val keys = IdentityKeys.generate()
    private val trust = OpenDataTrust.of(keys.publicKey.hex)

    private fun folder(): Path = Files.createTempDirectory("ppf-application-test")

    private fun index(out: Path) = (ParliamentIndexReader.read(SignedEvent.fromJson(out.resolve(Importer.INDEX_FILE).readText()), trust) as IndexReading.Accepted).index

    private fun packages() = BarometerCsv.packages(ExampleFiles.open("an-barometre/lois.csv"), ExampleFiles.open("an-barometre/mesures.csv"), example = true, today = "2026-11-07")

    @Test
    fun theOfficialFilesBecomeOnePackagePerLegislatureWithTheMeasuresOfEachLaw() {
        val packages = packages()
        assertEquals(setOf(16, 17), packages.keys, "the legislature 14 is not imported")
        val housing = packages.getValue(17).laws.last()
        assertEquals("JORFTEXT000099000001", housing.id)
        assertEquals("[EXEMPLE] LOI n° 2026-9001 du 20 octobre 2026 relative au logement abordable", housing.title)
        assertEquals("2026-10-20", housing.publishedOn)
        assertEquals("an-dossier:DLR5L17N90001", housing.dossier, "the key of the ballots and of the bills of Voir")
        assertEquals("https://www.assemblee-nationale.fr/dyn/17/dossiers/exemple_logement_abordable", housing.dossierUrl)
        assertEquals("[EXEMPLE] Commission des affaires économiques", housing.committee)
        assertEquals(4 to 1, housing.toApply to housing.applied)
        assertEquals(listOf(1, 2, 3, 4, 5), housing.measures.map { it.order })
        assertEquals(listOf(MeasureState.APPLIED, MeasureState.PENDING, MeasureState.PENDING, MeasureState.PENDING, MeasureState.NOT_NEEDED), housing.measures.map { it.state })
        assertEquals("JORFTEXT000099100001", housing.measures[0].decree)
        assertEquals(15, housing.measures[0].delayDays)
        assertEquals(listOf(null, "2026-10", "2027-03", null, null), housing.measures.map { it.plannedMonth })
        assertEquals("Article 3, I", housing.measures[1].articles)

        // Newest first; a law of direct application has no measure and no rate.
        val transparency = packages.getValue(17).laws.first()
        assertTrue(transparency.directApplication)
        assertNull(transparency.percent())

        // A semicolon inside a text does not shift the columns.
        val services = packages.getValue(16).laws.single()
        assertEquals("[EXEMPLE] Liste des services publics; communes concernées", services.measures[1].subject)
        assertEquals("JORFTEXT000099100004", services.measures[1].decree)
        assertEquals(238, services.measures[1].delayDays)

        val text = ApplicationFormat.write(packages.getValue(17))
        assertFalse("Présidence" in text || "Gouvernement" in text || "LOGEMENT" in text, "neither the government of the day nor the keywords")
    }

    @Test
    fun theImportAddsThePackagesAndTheirSourceThenKeepsThemAndRefusesATruncatedFileWithoutStoppingTheParliament() {
        val out = folder()
        assertEquals(ExitCodes.OK, Importer(ExampleSources(), keys, out, log = {}, application = true).run())
        val first = index(out)
        assertEquals(listOf("application-lois-16", "application-lois-17"), first.packages.map { it.name }.filter { it.startsWith(ApplicationFormat.PACKAGE_PREFIX) })
        assertEquals(ApplicationFormat.SOURCE_ID, first.sources.last().id)
        assertEquals("Licence Ouverte 2.0", first.sources.last().licence)
        val bytes = Files.readAllBytes(out.resolve("application-lois-17.json"))
        assertEquals(2, ApplicationFormat.read(bytes.decodeToString())?.laws?.size)

        // Not asked: the previous packages and their source are kept as they are.
        assertEquals(ExitCodes.OK, Importer(ExampleSources(), keys, out, log = {}).run())
        assertTrue(index(out).packages.any { it.name == "application-lois-17" })
        assertEquals(ApplicationFormat.SOURCE_ID, index(out).sources.last().id)

        // A file of the laws with far fewer laws (truncated) is refused and changes nothing.
        val truncated = object : ImportSources by ExampleSources() {
            override fun applicationLaws(): InputStream = ExampleFiles.open("an-barometre/lois.csv").use { input ->
                input.readBytes().decodeToString().lines().filterNot { "JORFTEXT000099000002" in it }.joinToString("\n").byteInputStream()
            }
        }
        val lines = ArrayList<String>()
        assertEquals(ExitCodes.OK, Importer(truncated, keys, out, log = { lines += it }, application = true).run(), "never a reason to stop the import of the Parliament")
        assertTrue(bytes.contentEquals(Files.readAllBytes(out.resolve("application-lois-17.json"))))
        assertTrue(lines.any { it.startsWith("application of the laws refused:") })
    }

    @Test
    fun anUnknownStateOfAMeasureIsRefusedRatherThanGuessed() {
        val strange = "Titre loi;Identifiant loi;N° ordre;Articles;Article 1;Identifiant article 1;Article 2;Identifiant article 2;Article 3;Identifiant article 3;Base légale;Objet;Objectif initial de publication, observations;Identifiant décret;Date prévue;État;Délai d'application (en jours)\n" +
            "[EXEMPLE] LOI;JORFTEXT000099000001;1;Article 1er;;;;;;;;[EXEMPLE] Objet;;;;abrogé;\n"
        val failure = runCatching { BarometerCsv.measures(strange.byteInputStream()) }.exceptionOrNull()
        assertTrue(failure is UnreadableSourceException)
    }

    @Test
    fun theApplicationIsAskedByNameOnly() {
        val base = listOf("--out", "x", "--key", "k")
        assertTrue(parseOptions(listOf("--only", "an,an-barometre") + base) { null }.getOrThrow().application)
        assertFalse(parseOptions(base) { null }.getOrThrow().application)
        assertFalse(parseOptions(base + listOf("--source", "official")) { null }.getOrThrow().application, "asked by name only: the daily timer lists it")
        assertTrue("barometre.assemblee-nationale.fr" in ALLOWED_HOSTS)
        assertTrue(OfficialSources.BAROMETER_LAWS.startsWith("https://barometre.assemblee-nationale.fr/") && OfficialSources.BAROMETER_MEASURES.startsWith("https://barometre.assemblee-nationale.fr/"))
        assertTrue(OfficialSources.BAROMETER_LAWS_MAX_BYTES < Downloader.MAX_FILE_BYTES && OfficialSources.BAROMETER_MEASURES_MAX_BYTES < Downloader.MAX_FILE_BYTES, "a cap of its own per file")
    }
}
