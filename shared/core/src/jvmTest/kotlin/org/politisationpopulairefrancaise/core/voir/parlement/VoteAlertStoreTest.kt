package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.local.JvmDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The storage of « Mes élus ont voté » in the local database (VO-34): the same rules as in memory, kept across restarts. */
class VoteAlertStoreTest {

    @Test
    fun theStoreBehavesLikeTheMemoryOneAndForgetsEverything() {
        val store = VoteAlertStore(JvmDatabase.inMemory())
        assertTrue(store.wifiOnly(), "« seulement en Wi-Fi » on by default")
        assertNull(store.startedAt())
        assertTrue(store.addWord(" Retraites ", 1))
        assertFalse(store.addWord("RETRAITES", 2), "accents and case ignored")
        assertFalse(store.addWord(" - ", 3))
        (2..VoteAlerts.MAX_FOLLOWED_WORDS).forEach { assertTrue(store.addWord("mot$it", 10L + it)) }
        assertFalse(store.addWord("un de trop", 99))
        assertEquals("Retraites", store.words().first())
        store.removeWord("retraites")
        assertEquals(VoteAlerts.MAX_FOLLOWED_WORDS - 1, store.words().size)

        store.markSeen(listOf("an:17:1", "an:17:2"), 5)
        store.markSeen(listOf("an:17:2", "an:17:3"), 6)
        assertEquals(setOf("an:17:1", "an:17:2", "an:17:3"), store.seen())
        store.start(5)
        store.start(9)
        assertEquals(5L, store.startedAt(), "the first round only")
        store.setEuropeans(setOf("pe:1", "pe:2"))
        store.setEuropeans(setOf("pe:2"))
        assertEquals(setOf("pe:2"), store.europeans())
        store.setWifiOnly(false)
        assertFalse(store.wifiOnly())

        store.forget()
        assertTrue(store.words().isEmpty() && store.seen().isEmpty() && store.europeans().isEmpty())
        assertNull(store.startedAt())
        assertTrue(store.wifiOnly())
    }

    @Test
    fun theFollowedWordsSurviveARestart() {
        val directory = Files.createTempDirectory("ppf-vote-alerts")
        val file = directory.resolve("ppf.db")
        try {
            VoteAlertStore(JvmDatabase.open(file)).apply {
                addWord("logement", 1)
                setWifiOnly(false)
            }
            val again = VoteAlertStore(JvmDatabase.open(file))
            assertEquals(listOf("logement"), again.words())
            assertFalse(again.wifiOnly())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
