package org.politisationpopulairefrancaise.core.crash

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReportFileTest {

    private val directory: File = Files.createTempDirectory("ppf-crash").toFile()
    private val file = File(directory, "crash-report.txt")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun theChainKeepsClassesAndFramesButNeverMessages() {
        val error = IllegalStateException("le texte de la personne", IllegalArgumentException("nsec1secret"))
        val chain = error.crashChain()
        assertEquals(listOf("java.lang.IllegalStateException", "java.lang.IllegalArgumentException"), chain.map { it.className })
        assertTrue(chain.first().frames.isNotEmpty())
        val text = CrashReports.format("0.1.0", "Linux", 0, chain)
        assertFalse(text.contains("le texte de la personne"))
        assertFalse(text.contains("nsec1secret"))
    }

    @Test
    fun aCycleOfCausesEnds() {
        val first = RuntimeException("a")
        val second = RuntimeException("b", first)
        first.initCause(second)
        assertEquals(2, first.crashChain().size)
    }

    @Test
    fun aWrittenReportIsOfferedThenErased() {
        val reports = CrashReportFile(file)
        assertNull(reports.pending())
        reports.write("Rapport de plantage PPF\n")
        val now = file.lastModified() / 1000
        assertEquals("Rapport de plantage PPF\n", reports.pending(now))
        reports.delete()
        assertFalse(file.exists())
        assertNull(reports.pending(now))
    }

    @Test
    fun anOldReportIsErasedWithoutBeingShown() {
        val reports = CrashReportFile(file)
        reports.write("Rapport de plantage PPF\n")
        val now = file.lastModified() / 1000 + CrashReports.MAX_AGE_SECONDS + 60
        assertNull(reports.pending(now))
        assertFalse(file.exists())
    }

    @Test
    fun theHandlerWritesTheReportThenCallsThePreviousOne() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        var forwarded: Throwable? = null
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, error -> forwarded = error }
            CrashReportFile(file).install("0.1.0", "Linux 6.8") { 0 }
            val error = IllegalStateException("contenu privé")
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            assertEquals(error, forwarded)
            val text = file.readText()
            assertTrue(text.contains("Erreur : java.lang.IllegalStateException"))
            assertTrue(text.contains("Système : Linux 6.8"))
            assertFalse(text.contains("contenu privé"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }
}
