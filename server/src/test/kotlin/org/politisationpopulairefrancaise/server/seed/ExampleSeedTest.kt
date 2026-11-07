package org.politisationpopulairefrancaise.server.seed

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.BundledContents
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExampleSeedTest {

    private val repository = EventRepository(TestDatabase.dataSource)
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private lateinit var directory: Path
    private val logs = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        TestDatabase.reset()
        directory = createTempDirectory("ppf-examples")
    }

    @AfterTest
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    private fun load(from: Path = directory): ExampleSeedReport = ExampleSeed.load(from, repository, info = logs::add, warn = logs::add)

    private fun note(content: String, createdAt: Long = 1_000): SignedEvent =
        runBlocking { editor.sign(kind = EventKinds.TEXT_NOTE, content = content, createdAt = createdAt) }

    private fun article(identifier: String, content: String, createdAt: Long = 1_000): SignedEvent =
        runBlocking { editor.sign(kind = EventKinds.LONG_FORM_ARTICLE, content = content, tags = listOf(listOf("d", identifier)), createdAt = createdAt) }

    private fun write(name: String, events: List<SignedEvent>, into: Path = directory) {
        into.resolve(name).writeText(Json.encodeToString(ListSerializer(SignedEvent.serializer()), events))
    }

    @Test
    fun storesTheAuthenticEventsOfEveryJsonFileOfTheDirectory() {
        val first = note("[EXEMPLE] premier message", 1_000)
        val second = note("[EXEMPLE] second message", 2_000)
        val guide = article("fiche-exemple", "[EXEMPLE] une fiche pratique")
        write("fil.json", listOf(first, second))
        write("fiches.json", listOf(guide))
        // Neither another extension nor a subdirectory is read.
        write("notes.txt", listOf(note("[EXEMPLE] pas un fichier json", 3_000)))
        write("ailleurs.json", listOf(note("[EXEMPLE] dans un sous-dossier", 4_000)), into = directory.resolve("archives").createDirectory())

        val report = load()

        assertEquals(ExampleSeedReport(files = 2, authentic = 3, stored = 3), report)
        assertEquals(3, repository.count())
        assertEquals(setOf(first, second, guide), repository.query(EventFilter()).toSet())
        assertEquals(1, logs.size, "one summary line")
        assertTrue(logs.single().startsWith("Example contents: 2 file(s) read"))
    }

    @Test
    fun aSecondRunStoresNothingTwice() {
        val guide = article("fiche-exemple", "[EXEMPLE] version 1", 1_000)
        write("fiches.json", listOf(guide, note("[EXEMPLE] message")))
        assertEquals(2, load().stored)

        val again = load()

        assertEquals(ExampleSeedReport(files = 1, authentic = 2, alreadyStored = 2), again)
        assertEquals(2, repository.count())

        // A content updated on the preproduction since keeps its newer version at the next start.
        val updated = article("fiche-exemple", "[EXEMPLE] version 2", 2_000)
        repository.store(updated)
        val afterUpdate = load()
        assertEquals(1, afterUpdate.superseded)
        assertEquals(0, afterUpdate.stored)
        assertNull(repository.find(guide.id))
        assertNotNull(repository.find(updated.id))
    }

    @Test
    fun skipsAFileThatCannotBeDecodedAndNamesOnlyTheFile() {
        val good = note("[EXEMPLE] message lisible")
        write("bon.json", listOf(good))
        directory.resolve("casse.json").writeText("[{\"id\": \"[EXEMPLE] contenu confidentiel\", ")
        directory.resolve("objet.json").writeText("{\"content\": \"[EXEMPLE] contenu confidentiel\"}")

        val report = load()

        assertEquals(listOf("casse.json", "objet.json"), report.skippedFiles)
        assertEquals(1, report.files)
        assertEquals(1, report.stored)
        assertEquals(good, repository.find(good.id))
        assertTrue(logs.any { "casse.json" in it } && logs.any { "objet.json" in it })
        assertTrue(logs.none { "confidentiel" in it }, "the content of a file never reaches the log: $logs")
    }

    @Test
    fun ignoresAnEventWithABadSignature() {
        val authentic = note("[EXEMPLE] message authentique", 1_000)
        val other = note("[EXEMPLE] autre message", 2_000)
        // Content changed after signature (the id no longer matches), and the signature of another event.
        val tampered = authentic.copy(content = "[EXEMPLE] message modifié après signature")
        val wrongSignature = authentic.copy(sig = other.sig)
        write("fil.json", listOf(tampered, authentic, wrongSignature))

        val report = load()

        assertEquals(ExampleSeedReport(files = 1, authentic = 1, invalid = 2, stored = 1), report)
        assertEquals(listOf(authentic), repository.query(EventFilter()))
        assertTrue(logs.none { "modifié" in it })
    }

    @Test
    fun aMissingDirectoryLoadsNothing() {
        val report = load(directory.resolve("absent"))

        assertEquals(ExampleSeedReport(), report)
        assertEquals(0, repository.count())
        assertTrue(logs.single().contains(ExampleSeed.DIRECTORY_VARIABLE))
    }

    @Test
    fun loadsTheContentsBundledWithTheApplications() {
        val bundled = listOf(Path.of(BundledContents.DIRECTORY), Path.of("..", BundledContents.DIRECTORY)).first(Files::isDirectory)

        val report = load(bundled)

        assertTrue(report.files > 0 && report.stored > 0, "$report")
        assertEquals(emptyList(), report.skippedFiles)
        assertEquals(0, report.invalid)
        assertEquals(0, report.failed)
        assertEquals(report.authentic, report.stored + report.alreadyStored + report.superseded)
        assertEquals(0, load(bundled).stored, "nothing stored twice")
    }

    @Test
    fun readsTheDirectoryFromTheEnvironment() {
        val env = { values: Map<String, String> -> { name: String -> values[name] } }

        assertNull(ExampleSeed.fromEnvironment(env(emptyMap())))
        assertNull(ExampleSeed.fromEnvironment(env(mapOf(ExampleSeed.DIRECTORY_VARIABLE to "  "))))
        // Without examples, production starts as usual.
        assertNull(ExampleSeed.fromEnvironment(env(mapOf(ExampleSeed.ENVIRONMENT_VARIABLE to "production"))))
        assertEquals(
            Path.of("/srv/ppf/examples"),
            ExampleSeed.fromEnvironment(env(mapOf(ExampleSeed.DIRECTORY_VARIABLE to "/srv/ppf/examples", ExampleSeed.ENVIRONMENT_VARIABLE to "preproduction"))),
        )
        assertEquals(Path.of("examples"), ExampleSeed.fromEnvironment(env(mapOf(ExampleSeed.DIRECTORY_VARIABLE to "examples"))))
    }

    @Test
    fun refusesToStartInProductionWithExamples() {
        for (production in listOf("production", "Production", " PRODUCTION ")) {
            val error = assertFailsWith<IllegalStateException> {
                ExampleSeed.fromEnvironment { name ->
                    when (name) {
                        ExampleSeed.DIRECTORY_VARIABLE -> "/srv/ppf/examples"
                        ExampleSeed.ENVIRONMENT_VARIABLE -> production
                        else -> null
                    }
                }
            }
            assertTrue(error.message.orEmpty().contains(ExampleSeed.DIRECTORY_VARIABLE), error.message)
        }
    }
}
