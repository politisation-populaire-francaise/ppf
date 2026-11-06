package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.local.JvmDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallResponseStoreTest {
    private val first = "30023:${"a".repeat(64)}:assemblee-loyers"
    private val second = "30023:${"b".repeat(64)}:petition-bus"

    @Test
    fun answersAreKeptOnceWithdrawnAndCleared() {
        val store = CallResponseStore(JvmDatabase.inMemory())
        store.respond(first)
        store.respond(first)
        store.respond(second)
        assertEquals(setOf(first, second), store.all())
        store.withdraw(first)
        store.withdraw("inconnue")
        assertEquals(setOf(second), store.all())
        store.clear()
        assertEquals(0L, store.count())
        assertTrue(runCatching { store.respond(" ") }.isFailure)
    }

    @Test
    fun answersSurviveTheClosingOfTheApplication() {
        val directory = Files.createTempDirectory("ppf-answers")
        val file = directory.resolve("ppf.db")
        try {
            CallResponseStore(JvmDatabase.open(file)).respond(first)
            assertEquals(setOf(first), CallResponseStore(JvmDatabase.open(file)).all())
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
