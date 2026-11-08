package org.politisationpopulairefrancaise.core.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportsTest {

    private val frames = listOf("org.example.Screen.draw(Screen.kt:12)", "org.example.Main.run(Main.kt:3)")

    @Test
    fun reportHasVersionSystemDayAndFramesOnly() {
        val text = CrashReports.format(
            version = "0.1.0",
            system = "Android 14",
            writtenAtEpochSeconds = 1_794_096_000 + 3_725, // 2026-11-08T01:02:05Z
            chain = listOf(CrashedException("java.lang.IllegalStateException", frames), CrashedException("java.io.IOException", emptyList())),
        )
        assertTrue(text.contains("Version : 0.1.0\n"))
        assertTrue(text.contains("Système : Android 14\n"))
        assertTrue(text.contains("Jour (UTC) : 2026-11-08\n"))
        assertTrue(text.contains("Erreur : java.lang.IllegalStateException\n  at org.example.Screen.draw(Screen.kt:12)\n"))
        assertTrue(text.contains("Cause : java.io.IOException\n"))
        // The hour is not written: the day says which version ran, not what the person was doing.
        assertFalse(text.contains("01:02"))
    }

    @Test
    fun keysAndLongIdentifiersAreRemoved() {
        val text = CrashReports.format(
            "0.1.0",
            "Linux 6.8",
            0,
            listOf(CrashedException("x.Y", listOf("a.B.c(npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m)", "d.E.f(" + "ab".repeat(32) + ")"))),
        )
        assertFalse(text.contains("npub1sg6"))
        assertFalse(text.contains("ab".repeat(32)))
        assertTrue(text.contains("[clé retirée]"))
        assertTrue(text.contains("[valeur retirée]"))
    }

    @Test
    fun reportIsBounded() {
        val deep = List(500) { "a.B.c$it(B.kt:$it)" }
        val chain = List(20) { CrashedException("x.Y$it", deep) }
        val text = CrashReports.format("0.1.0", "Linux", 0, chain)
        assertTrue(text.length <= CrashReports.MAX_CHARS + 3)
        assertTrue(text.contains("de plus"))
        assertFalse(text.contains("x.Y${CrashReports.MAX_CAUSES}\n"))
    }

    @Test
    fun controlCharactersCannotForgeLines() {
        val text = CrashReports.format("0.1.0\nVersion : 9.9.9", "Linux", 0, emptyList())
        assertEquals(1, Regex("^Version : ", RegexOption.MULTILINE).findAll(text).count())
    }

    @Test
    fun aReportExpiresAfterAWeek() {
        val written = 1_000_000L
        assertFalse(CrashReports.isExpired(written, written + CrashReports.MAX_AGE_SECONDS))
        assertTrue(CrashReports.isExpired(written, written + CrashReports.MAX_AGE_SECONDS + 1))
        // A date far in the future (clock changed) is not trusted either.
        assertTrue(CrashReports.isExpired(written + 7200, written))
    }

    @Test
    fun utcDaysAreRight() {
        assertEquals("1970-01-01", CrashReports.utcDay(0))
        assertEquals("2000-02-29", CrashReports.utcDay(951_782_400))
        assertEquals("2026-11-08", CrashReports.utcDay(1_794_182_399))
        assertEquals("1969-12-31", CrashReports.utcDay(-1))
    }
}
