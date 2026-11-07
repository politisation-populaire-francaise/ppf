package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.BallotsPackage
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.IndexReading
import org.politisationpopulairefrancaise.core.voir.parlement.MembersPackage
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataKeys
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataTrust
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndexReader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A whole import, on the example files, into temporary folders; never the network. */
class ImporterTest {
    private val keys = IdentityKeys.generate()
    private val trust = OpenDataTrust.of(keys.publicKey.hex)

    private fun folder(): Path = Files.createTempDirectory("ppf-open-data-test")

    private fun import(out: Path, sources: ImportSources = ExampleSources(), only: Set<SourceId> = SourceId.entries.toSet()): Int =
        Importer(sources, keys, out, only, log = {}).run()

    private fun snapshot(out: Path): Map<String, ByteArray> = out.listDirectoryEntries().sortedBy { it.name }.associate { it.name to Files.readAllBytes(it) }

    private fun assertSame(expected: Map<String, ByteArray>, actual: Map<String, ByteArray>) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (name, bytes) -> assertContentEquals(bytes, actual.getValue(name), name) }
    }

    private fun index(out: Path) = (ParliamentIndexReader.read(SignedEvent.fromJson(out.resolve(Importer.INDEX_FILE).readText()), trust) as IndexReading.Accepted).index

    @Test
    fun theIndexIsSignedAndEveryPackageMatchesItsFingerprint() {
        val out = folder()
        assertEquals(ExitCodes.OK, import(out))
        val event = SignedEvent.fromJson(out.resolve(Importer.INDEX_FILE).readText())
        val reading = assertIs<IndexReading.Accepted>(ParliamentIndexReader.read(event, trust))
        assertEquals(ParliamentFormat.INDEX_KIND, event.kind)
        assertEquals(ParliamentFormat.INDEX_D, event.firstTagValue("d"))
        val index = reading.index
        assertTrue(index.example)
        assertEquals("2026-11-07", index.importedOn)
        assertEquals(listOf("an", "senat", "pe"), index.sources.map { it.id })
        assertEquals(
            listOf("membres", "scrutins-an-2026-09", "scrutins-an-2026-10", "semaine", "votes-pe-2026-09", "votes-pe-2026-10"),
            index.packages.map { it.name },
        )
        index.packages.forEach { ref -> assertTrue(ParliamentIndexReader.matches(ref, Files.readAllBytes(out.resolve(ref.name + ".json"))), ref.name) }
        // Another key is refused by the application.
        assertIs<IndexReading.Refused>(ParliamentIndexReader.read(event, OpenDataKeys.bundledExamples()))
        assertEquals(setOf(Importer.INDEX_FILE) + index.packages.map { it.name + ".json" }, out.listDirectoryEntries().map { it.name }.toSet())

        val members = ParliamentFormat.json.decodeFromString(MembersPackage.serializer(), out.resolve("membres.json").readText())
        assertEquals(mapOf(Chamber.AN to 12, Chamber.SENAT to 4, Chamber.PE to 3), members.members.groupingBy { it.chamber }.eachCount())
        assertEquals(members.members.sortedBy { it.id }, members.members)
        val september = ParliamentFormat.json.decodeFromString(BallotsPackage.serializer(), out.resolve("scrutins-an-2026-09.json").readText())
        assertEquals(listOf(9001, 9002, 9003), september.ballots.map { it.number })
        assertEquals("2026-09", september.month)
    }

    @Test
    fun nothingButTheFieldsOfTheContractIsKept() {
        val out = folder()
        assertEquals(ExitCodes.OK, import(out))
        val text = out.listDirectoryEntries().joinToString("\n") { it.readText() }
        // Values that only exist in forbidden fields of the example files.
        listOf(
            "1970-01-01", "Profession fictive", "Ville fictive", "Collaborateur", "example.invalid", "Catégorie fictive", "#123456",
            "numPlace", "placeHemicycle", "\"civ\"", "civilite", "urlAvatar", "bday", "hasGender", "citizenship", "\"img\"", "hasEmail", "siege", "tel:",
            "\"Mme\"", "mandatRef", "PM9",
        ).forEach { marker -> assertFalse(marker in text, "found $marker") }
        val allowed = setOf("id", "name", "chamber", "group", "territory", "territoryName", "since", "url", "nationalParty", "interestsUrl")
        val members = ParliamentFormat.json.parseToJsonElement(out.resolve("membres.json").readText()).jsonObject["members"] as JsonArray
        members.forEach { member -> assertTrue((member as JsonObject).keys.all { it in allowed }, member.keys.toString()) }
        assertTrue(members.all { (it as JsonObject)["name"].toString().startsWith("\"[EXEMPLE] ") })
    }

    @Test
    fun twoImportsGiveTheSameBytes() {
        val first = folder()
        val second = folder()
        assertEquals(ExitCodes.OK, import(first))
        assertEquals(ExitCodes.OK, import(second))
        val a = snapshot(first)
        val b = snapshot(second)
        // Same packages; the index differs only by its signature (randomised), so it is compared without it.
        assertSame(a - Importer.INDEX_FILE, b - Importer.INDEX_FILE)
        assertEquals(SignedEvent.fromJson(a.getValue(Importer.INDEX_FILE).decodeToString()).id, SignedEvent.fromJson(b.getValue(Importer.INDEX_FILE).decodeToString()).id)
        // Again in the same folder: nothing changes, not even the signature of the index.
        assertEquals(ExitCodes.OK, import(first))
        assertSame(a, snapshot(first))
    }

    @Test
    fun aTruncatedSetIsRefusedAndThePreviousOneKept() {
        val out = folder()
        assertEquals(ExitCodes.OK, import(out))
        val before = snapshot(out)
        // 3 deputies of 12 missing: more than 20 % lost.
        val fewerDeputies = ExampleSources { it !in setOf("an/amo10/json/acteur/PA900001.json", "an/amo10/json/acteur/PA900002.json", "an/amo10/json/acteur/PA900003.json") }
        assertEquals(ExitCodes.REFUSED_BY_GUARD, import(out, fewerDeputies))
        assertSame(before, snapshot(out))
        // 2 ballots of 6 missing.
        val fewerBallots = ExampleSources { it !in setOf("an/scrutins/json/VTANR5L17V9001.json", "an/scrutins/json/VTANR5L17V9002.json") }
        assertEquals(ExitCodes.REFUSED_BY_GUARD, import(out, fewerBallots))
        assertSame(before, snapshot(out))
        // One deputy of 12 missing stays within the limit.
        assertEquals(ExitCodes.OK, import(out, ExampleSources { it != "an/amo10/json/acteur/PA900001.json" }))
        assertEquals(11, ParliamentFormat.json.decodeFromString(MembersPackage.serializer(), out.resolve("membres.json").readText()).members.count { it.chamber == Chamber.AN })
    }

    @Test
    fun unreadableDataOrAFailedDownloadKeepThePreviousSet() {
        val out = folder()
        assertEquals(ExitCodes.UNREADABLE_SOURCE, import(out, ExampleSources { !it.startsWith("an/amo10/json/acteur/") }))
        assertTrue(out.listDirectoryEntries().isEmpty())
        assertEquals(ExitCodes.OK, import(out))
        val before = snapshot(out)
        val failing = object : ImportSources by ExampleSources() {
            override fun senators(): InputStream = throw DownloadException("www.senat.fr: HTTP 503")
        }
        assertEquals(ExitCodes.DOWNLOAD_FAILED, import(out, failing))
        assertSame(before, snapshot(out))
        val broken = object : ImportSources by ExampleSources() {
            override fun senators(): InputStream = "{\"not\": ".byteInputStream()
        }
        assertEquals(ExitCodes.UNREADABLE_SOURCE, import(out, broken))
        assertSame(before, snapshot(out))
    }

    @Test
    fun onlyTheSourcesAskedAreRefreshed() {
        val out = folder()
        assertEquals(ExitCodes.OK, import(out))
        val before = snapshot(out)
        // The Assemblée is not read at all: its files may be missing.
        val senatOnly = ExampleSources { !it.startsWith("an/") }
        assertEquals(ExitCodes.OK, import(out, senatOnly, only = setOf(SourceId.SENAT)))
        assertSame(before, snapshot(out))
        assertEquals(listOf("an", "senat", "pe"), index(out).sources.map { it.id })
    }

    @Test
    fun theCommandLine() {
        val out = folder()
        val key = Files.createTempFile("import", ".nsec").also { Files.writeString(it, keys.nsec + "\n") }
        val env = mapOf<String, String>()
        assertEquals(ExitCodes.USAGE, runCommandLine(listOf("--key", key.toString()), env::get))
        assertEquals(ExitCodes.USAGE, runCommandLine(listOf("--out", out.toString(), "--key", key.toString(), "--verbose", "x"), env::get))
        assertEquals(ExitCodes.USAGE, runCommandLine(listOf("--out", out.toString(), "--key", key.toString(), "--source", "real"), env::get))
        assertEquals(ExitCodes.USAGE, runCommandLine(listOf("--out", out.toString(), "--key", key.toString(), "--only", "an,hatvp"), env::get))
        assertEquals(ExitCodes.KEY_ERROR, runCommandLine(listOf("--out", out.toString(), "--key", out.resolve("missing.nsec").toString()), env::get))
        val wrong = Files.createTempFile("import", ".nsec").also { Files.writeString(it, "not a key") }
        assertEquals(ExitCodes.KEY_ERROR, runCommandLine(listOf("--out", out.toString(), "--key", wrong.toString()), env::get))
        assertTrue(out.listDirectoryEntries().isEmpty())
        // Environment variables instead of arguments.
        val variables = mapOf("PPF_OPEN_DATA_DIR" to out.toString(), "PPF_IMPORT_KEY_FILE" to key.toString(), "PPF_OPEN_DATA_SOURCE" to "example")
        assertEquals(ExitCodes.OK, runCommandLine(emptyList(), variables::get))
        assertTrue(index(out).example)
        val options = parseOptions(listOf("--only", "pe,an", "--out", "x", "--key", "k", "--source", "official"), env::get).getOrThrow()
        assertEquals(SourceMode.OFFICIAL, options.mode)
        assertEquals(setOf(SourceId.PE, SourceId.AN), options.only)
    }

    @Test
    fun officialModeContactsTheThreeHostsOnlyAndNeverInTests() {
        val downloader = Downloader()
        val file = folder().resolve("x")
        // Refused before any connection.
        assertFailsWith<DownloadException> { downloader.download("https://example.org/data.zip", file) }
        assertFailsWith<DownloadException> { downloader.download("http://data.assemblee-nationale.fr/x.zip", file) }
        assertFailsWith<DownloadException> { downloader.get("https://data.europarl.europa.eu.example.org/api", throttle = false) }
        assertEquals(setOf("data.assemblee-nationale.fr", "www.senat.fr", "data.europarl.europa.eu"), ALLOWED_HOSTS)
        listOf(OfficialSources.AMO10, OfficialSources.SCRUTINS, OfficialSources.DOSSIERS, OfficialSources.SENATEURS, OfficialSources.EUROPARL_API).forEach {
            assertTrue(java.net.URI.create(it).host in ALLOWED_HOSTS && it.startsWith("https://"))
        }
    }
}
