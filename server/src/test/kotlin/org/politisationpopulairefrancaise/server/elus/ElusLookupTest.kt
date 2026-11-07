package org.politisationpopulairefrancaise.server.elus

import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The read-only lookup of the directory for the back-office (ElusLookup), on the fictitious set. */
class ElusLookupTest {
    private val clock = MutableClock(Instant.parse("2026-11-07T08:00:00Z"))
    private val service = ElusService(ElusStore(TestDatabase.dataSource), ElusSetup(ElusConfig(refreshDays = 0), clock))
    private val lookup = ElusLookup(service)
    private val directories = ArrayList<Path>()
    private val sourceDate = LocalDate.parse("2026-10-01")

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    @AfterTest
    fun removeDirectories() = directories.forEach { dir -> dir.toFile().deleteRecursively() }

    private fun importExample() = assertIs<ImportOutcome.Imported>(service.importNow(ExampleSource()))

    private fun exampleText(file: ElusFile): String =
        ExampleSource::class.java.getResourceAsStream("${ExampleSource.RESOURCE_DIRECTORY}/${file.fileName}")!!.readBytes().toString(Charsets.UTF_8)

    private fun exampleWith(file: ElusFile, text: String): ElusSource {
        val dir = Files.createTempDirectory("ppf-elus-lookup-").also { directories.add(it) }
        dir.resolve(file.fileName).writeText(text, Charsets.UTF_8)
        return DirectorySource(dir, sourceDate, example = true, fallback = ExampleSource())
    }

    @Test
    fun theIdentifierIsTheTruncatedHashOfTheCanonicalFields() {
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("municipal\n69123\n[exemple] claude demonstration\n2026-03-15".toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it) }
        val id = ElusLookup.mandateId(Mandate.MUNICIPAL, "69123", "[EXEMPLE]  Claude DÉMONSTRATION ", "2026-03-15")
        assertEquals("rne1:$expected", id)
        assertTrue(ElusLookup.isValidId(id))
        // Accents and case aside, the same; another start date, another mandate.
        assertEquals(id, ElusLookup.mandateId(Mandate.MUNICIPAL, "69123", "[EXEMPLE] Claude Démonstration", "2026-03-15"))
        assertNotEquals(id, ElusLookup.mandateId(Mandate.MUNICIPAL, "69123", "[EXEMPLE] Claude Démonstration", "2020-07-03"))
        assertNotEquals(id, ElusLookup.mandateId(Mandate.COMMUNAUTAIRE, "69123", "[EXEMPLE] Claude Démonstration", "2026-03-15"))
    }

    @Test
    fun nothingIsAnsweredBeforeTheFirstImport() {
        assertEquals(emptyList(), lookup.find("Démonstration"))
        assertEquals(MandateStatus.Unavailable, lookup.mandateStatus(ElusLookup.mandateId(Mandate.MUNICIPAL, "69123", "x", "2026-03-15")))
        assertEquals(null, lookup.sourceUpdatedOn())
    }

    @Test
    fun theSearchIgnoresAccentsCaseAndOrder() {
        importExample()
        val claude = lookup.find("claude demonstration").single()
        assertEquals("[EXEMPLE] Claude Démonstration", claude.name)
        assertEquals(Mandate.MUNICIPAL, claude.mandate)
        assertEquals("2ème adjoint au Maire", claude.function)
        assertEquals("69123", claude.territory)
        assertEquals("Lyon", claude.territoryName)
        assertEquals("2026-03-15", claude.since)
        assertEquals(sourceDate, claude.sourceUpdatedOn)
        assertEquals(ElusLookup.mandateId(Mandate.MUNICIPAL, "69123", claude.name, claude.since), claude.id)
        assertEquals(listOf(claude), lookup.find("DÉMONSTRATION  Claude"))
        assertEquals(listOf(claude), lookup.find("Démonstration", Mandate.MUNICIPAL, "69123"))

        val all = lookup.find("Demonstration")
        assertEquals(7, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(3, lookup.find("démonstration", Mandate.MUNICIPAL).size)
        assertEquals(listOf("[EXEMPLE] Andréa Démonstration"), lookup.find("demonstration", Mandate.MUNICIPAL, "59512").map { it.name })
        assertEquals("Roubaix", lookup.find("andrea demonstration").single().territoryName)
        // The regional president, in the package of the region, with the name of the region.
        val noa = lookup.find("noa demonstration", Mandate.REGIONAL).single()
        assertEquals("Président du conseil régional", noa.function)
        assertEquals("11", noa.territory)
        // A word of one letter, or nothing at all, finds nothing; the limit holds.
        assertEquals(emptyList(), lookup.find("c"))
        assertEquals(emptyList(), lookup.find("  "))
        assertEquals(2, lookup.find("exemple", limit = 2).size)
    }

    @Test
    fun theIdentifierStaysTheSameBetweenTwoIdenticalImports() {
        importExample()
        val before = lookup.find("exemple", limit = 500)
        assertTrue(before.size > 100)
        val first = service.current()!!.served.id
        importExample()
        assertNotEquals(first, service.current()!!.served.id)
        val after = lookup.find("exemple", limit = 500)
        assertEquals(before.map { it.id }.sorted(), after.map { it.id }.sorted())
        after.forEach { match -> assertEquals(MandateStatus.Current(sourceDate), lookup.mandateStatus(match.id), match.name) }
    }

    @Test
    fun aMandateRemovedByTheNextImportIsNoLongerCurrent() {
        importExample()
        val claude = lookup.find("claude demonstration").single()
        val camille = lookup.find("camille exemplaire", Mandate.MUNICIPAL).single()
        assertEquals(MandateStatus.Current(sourceDate), lookup.mandateStatus(claude.id))

        val without = exampleText(ElusFile.MUNICIPAL).lines().filterNot { it.contains("Démonstration;[EXEMPLE] Claude;") }.joinToString("\r\n")
        assertIs<ImportOutcome.Imported>(service.importNow(exampleWith(ElusFile.MUNICIPAL, without)))
        assertEquals(emptyList(), lookup.find("claude demonstration"))
        assertEquals(MandateStatus.NotFound(sourceDate), lookup.mandateStatus(claude.id))
        assertEquals(MandateStatus.Current(sourceDate), lookup.mandateStatus(camille.id))
        // A malformed identifier is simply not found.
        assertEquals(MandateStatus.NotFound(sourceDate), lookup.mandateStatus("rne1:XYZ"))
        assertEquals(MandateStatus.NotFound(sourceDate), lookup.mandateStatus("rne2:" + claude.id.removePrefix("rne1:")))
    }

    @Test
    fun theFragmentShowsTheMatchesAndTheDateOfTheSource() {
        importExample()
        val matches = lookup.find("claude demonstration")
        val html = createHTML().html { body { elusMatches(matches, lookup.sourceUpdatedOn()) } }
        assertTrue(html.contains("<caption>${ElusLookupTexts.CAPTION}</caption>"), html)
        assertTrue(html.contains("[EXEMPLE] Claude Démonstration"))
        assertTrue(html.contains("Lyon (69123)"))
        assertTrue(html.contains("Conseil municipal"))
        assertTrue(html.contains("<time datetime=\"2026-10-01\">1 octobre 2026</time>"), html)
        assertTrue(html.contains(matches.single().id))
        assertTrue(html.contains("scope=\"col\""))

        val none = createHTML().html { body { elusMatches(emptyList(), sourceDate) } }
        assertTrue(none.contains(ElusLookupTexts.NONE))
        val unavailable = createHTML().html { body { elusMatches(emptyList(), null) } }
        assertTrue(unavailable.contains(ElusLookupTexts.UNAVAILABLE))
    }
}
