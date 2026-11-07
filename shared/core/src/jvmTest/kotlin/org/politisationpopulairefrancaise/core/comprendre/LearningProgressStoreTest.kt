package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningProgressStoreTest {

    private val store = LearningProgressStore(JvmDatabase.inMemory())
    private val loi = "30023:abc:comment-une-loi-est-votee"
    private val dissolution = "30023:abc:la-dissolution"

    @Test
    fun nothingIsKnownOfACourseNeverOpened() {
        assertNull(store.progress(loi))
        assertEquals(emptyMap(), store.all())
        assertEquals(0, store.count())
    }

    @Test
    fun theFurthestStepNeverGoesBack() {
        store.reachStep(loi, 0, now = 100)
        store.reachStep(loi, 2, now = 200)
        store.reachStep(loi, 1, now = 300)
        val progress = assertNotNull(store.progress(loi))
        assertEquals(2, progress.furthestStep)
        assertEquals(300, progress.updatedAt)
        assertFalse(progress.completed)
    }

    @Test
    fun answersAreKeptByStepAndANewAnswerReplacesTheOldOne() {
        store.answer(loi, 0, choice = 2, correct = false, now = 100)
        store.answer(loi, 1, choice = 0, correct = true, now = 110)
        store.answer(loi, 0, choice = 1, correct = true, now = 120)
        val progress = assertNotNull(store.progress(loi))
        assertEquals(QuizAnswer(0, 1, true), progress.answers[0])
        assertEquals(2, progress.correctCount(stepCount = 2))
        assertEquals(1, progress.correctCount(stepCount = 1), "a step removed by an update no longer counts")
        assertEquals(1, progress.furthestStep, "answering a step reaches it")

        store.forgetAnswer(loi, 0)
        assertEquals(setOf(1), assertNotNull(store.progress(loi)).answers.keys, "trying again forgets that answer only")
    }

    @Test
    fun completingRestartingAndClearing() {
        store.answer(loi, 0, choice = 0, correct = true, now = 100)
        store.complete(loi, now = 150)
        store.reachStep(dissolution, 0, now = 160)
        assertEquals(150, assertNotNull(store.progress(loi)).completedAt)
        assertEquals(setOf(loi, dissolution), store.all().keys)
        assertEquals(1, store.all().getValue(loi).answers.size)

        store.restart(loi)
        assertNull(store.progress(loi))
        assertNotNull(store.progress(dissolution), "restarting one course keeps the others")

        store.answer(loi, 0, choice = 0, correct = true, now = 200)
        store.clear()
        assertEquals(0, store.count())
        assertEquals(emptyMap(), store.all())
    }

    @Test
    fun invalidNumbersAreRefused() {
        assertFailsWith<IllegalArgumentException> { store.reachStep(loi, -1, now = 1) }
        assertFailsWith<IllegalArgumentException> { store.answer(loi, 0, choice = -1, correct = false, now = 1) }
        assertNull(store.progress(loi), "a refused write leaves nothing behind")
    }

    /** A device on schema version 6 (before the courses) gets the progress tables and keeps its contents. */
    @Test
    fun aVersionSixDatabaseGainsTheProgressTables() {
        val directory = Files.createTempDirectory("ppf-learning")
        val file = directory.resolve("ppf.db")
        try {
            // A real version 6 database: the current schema, minus what the later versions added
            // (7: courses, 8: followed contents of Voir, 9: agenda of Agir, 10: answered calls,
            // 11: directory of elected officials, 12: parliamentary open data, 13: referential of territories,
            // 14: testimonies of Agir).
            JvmDatabase.open(file)
            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("DROP TABLE testimony_file")
                    statement.executeUpdate("DROP TABLE testimony")
                    statement.executeUpdate("DROP TABLE territoire_package")
                    statement.executeUpdate("DROP TABLE parliament_package")
                    statement.executeUpdate("DROP TABLE parliament_index")
                    statement.executeUpdate("DROP TABLE elus_package")
                    statement.executeUpdate("DROP TABLE elus_commune")
                    statement.executeUpdate("DROP TABLE call_response")
                    statement.executeUpdate("DROP TABLE agenda_proposal")
                    statement.executeUpdate("DROP TABLE agenda_response")
                    statement.executeUpdate("DROP TABLE content_follow")
                    statement.executeUpdate("DROP TABLE learning_answer")
                    statement.executeUpdate("DROP TABLE learning_progress")
                    statement.executeUpdate("PRAGMA user_version = 6")
                }
            }
            val database: PpfDatabase = JvmDatabase.open(file)
            val migrated = LearningProgressStore(database)
            migrated.answer(loi, 0, choice = 1, correct = true, now = 10)
            assertEquals(QuizAnswer(0, 1, true), assertNotNull(migrated.progress(loi)).answers[0])
            assertEquals(0, LocalEventStore(database).count())
            DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    val version = statement.executeQuery("PRAGMA user_version").use { rows -> rows.next(); rows.getLong(1) }
                    assertEquals(PpfDatabase.Schema.version, version)
                    assertTrue(version >= 7, "schema version $version")
                }
            }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
