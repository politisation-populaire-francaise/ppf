package org.politisationpopulairefrancaise.core.agir.proches

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The list of « Mes proches à mobiliser » (AG-63) as the device keeps it: its JSON, its limits (twelve
 * relatives, forty characters of nickname, three subjects of the closed list), the ladder, the order
 * of the screen (reminders due first) and its store. Every nickname is an [EXEMPLE] of the tests.
 */
class ProchesDataTest {

    private val today = CivilDate(2026, 11, 9)

    private fun added(data: ProchesData, input: ProcheInput, random: Random = Random(1)): Pair<ProchesData, Proche> {
        val (next, change) = data.add(input, random)
        return next to assertIs<ProcheChange.Saved>(change).entry
    }

    @Test
    fun theListGoesThroughItsJsonUnchanged() {
        var data = ProchesData()
        data = added(data, ProcheInput("[EXEMPLE] Tante M.", listOf("sante", "logement"), ProcheStep.SHARE, CivilDate(2026, 11, 12))).first
        data = added(data, ProcheInput("[EXEMPLE] Voisin", emptyList(), ProcheStep.TALK, null), Random(2)).first
        val read = ProchesData.decode(data.encode())
        assertEquals(data, read)
        assertEquals(listOf("sante", "logement"), read.entries[0].subjects)
        assertEquals(ProcheStep.SHARE, read.entries[0].nextStep)
        assertEquals(CivilDate(2026, 11, 12), read.entries[0].reminderDate)
        assertNull(read.entries[1].reminder)
        // Nothing but what the person chose: no date of creation, no account, no contact.
        val json = data.encode()
        assertFalse("created" in json || "account" in json || "phone" in json || "owner" in json, json)
    }

    @Test
    fun aDamagedOrEmptyFileReadsAsAnEmptyList() {
        assertTrue(ProchesData.decode(null).isEmpty)
        assertTrue(ProchesData.decode("").isEmpty)
        assertTrue(ProchesData.decode("{ pas du json").isEmpty)
        assertTrue(ProchesData.decode("[1, 2, 3]").isEmpty)
    }

    @Test
    fun aFileWrittenByHandIsCleanedBeforeItIsShown() {
        val text = """
            {"version":1,"entries":[
              {"id":"a1","nickname":"  [EXEMPLE]\n  Cousine  ","subjects":["sante","opinion-libre","sante","logement","travail","culture"],"step":"inconnu","reminder":"2026-02-30"},
              {"id":"a1","nickname":"[EXEMPLE] Doublon"},
              {"id":"b2","nickname":"   "},
              {"id":"","nickname":"[EXEMPLE] Sans identifiant"}
            ]}
        """.trimIndent()
        val read = ProchesData.decode(text)
        assertEquals(1, read.entries.size)
        val entry = read.entries.single()
        assertEquals("[EXEMPLE] Cousine", entry.nickname)
        assertEquals(listOf("sante", "logement", "travail"), entry.subjects)
        assertEquals(ProcheStep.TALK.id, entry.step)
        assertNull(entry.reminder)
    }

    @Test
    fun theListHoldsTwelveRelativesAtMost() {
        var data = ProchesData()
        repeat(ProchesRules.MAX_ENTRIES) { index ->
            data = added(data, ProcheInput("[EXEMPLE] Proche $index"), Random(index)).first
        }
        assertTrue(data.isFull)
        val (same, change) = data.add(ProcheInput("[EXEMPLE] Un de trop"))
        assertEquals(ProcheChange.Full, change)
        assertEquals(data, same)
        assertEquals(12, data.entries.map { it.id }.distinct().size, "every id is different")
        // A file holding more is cut to twelve when read.
        val tooMany = data.copy(entries = data.entries + Proche("zz", "[EXEMPLE] Treizième"))
        assertEquals(12, ProchesData.decode(tooMany.encode()).entries.size)
    }

    @Test
    fun theNicknameIsOneLineOfFortyCharactersAtMost() {
        assertEquals(40, ProchesRules.MAX_NICKNAME)
        val long = "[EXEMPLE] " + "a".repeat(60)
        val (_, entry) = added(ProchesData(), ProcheInput(long))
        assertEquals(40, entry.nickname.length)
        assertEquals("[EXEMPLE] Mamie du sud", ProchesRules.typed("[EXEMPLE] Mamie\tdu sud"))
        assertEquals("[EXEMPLE] Mamie du sud", ProchesRules.cleanNickname("  [EXEMPLE]   Mamie\n\tdu   sud "))
        // Typing keeps one line and stops at forty characters.
        assertEquals("[EXEMPLE] A B", ProchesRules.typed("[EXEMPLE] A\nB"))
        assertEquals(40, ProchesRules.typed("x".repeat(80)).length)
        // An emoji is never cut in half.
        val emoji = "a".repeat(39) + "😀"
        assertEquals(39, ProchesRules.typed(emoji).length)
        // Nothing but spaces is no nickname: nothing is kept.
        val (same, change) = ProchesData().add(ProcheInput("   \n "))
        assertEquals(ProcheChange.NoNickname, change)
        assertTrue(same.isEmpty)
    }

    @Test
    fun theSubjectsComeFromTheClosedListOnlyThreeAtMost() {
        assertEquals(15, ProchesRules.SUBJECTS.size)
        assertTrue("sante" in ProchesRules.SUBJECTS && "logement" in ProchesRules.SUBJECTS)
        val (_, entry) = added(ProchesData(), ProcheInput("[EXEMPLE] Collègue", listOf("de-gauche", "travail", "Travail", "sante", "travail", "logement", "culture")))
        assertEquals(listOf("travail", "sante", "logement"), entry.subjects)
        // Free text about someone's opinions has no way in.
        assertEquals(emptyList<String>(), ProchesRules.cleanSubjects(listOf("vote à droite", "militant", "")))
    }

    @Test
    fun theLadderGoesUpOneRungAtATimeUntilItIsDone() {
        assertEquals(
            listOf("en-parler", "partager", "inviter", "y-aller-ensemble", "fait"),
            ProcheStep.entries.map { it.id },
        )
        val (start, entry) = added(ProchesData(), ProcheInput("[EXEMPLE] Ami"))
        var data = start
        assertEquals(ProcheStep.TALK, entry.nextStep)
        for (expected in listOf(ProcheStep.SHARE, ProcheStep.INVITE, ProcheStep.GO_TOGETHER, ProcheStep.DONE)) {
            val (next, change) = data.advance(entry.id)
            data = next
            assertEquals(expected, assertIs<ProcheChange.Saved>(change).entry.nextStep)
        }
        val (same, change) = data.advance(entry.id)
        assertEquals(data, same)
        assertEquals(ProcheStep.DONE, assertIs<ProcheChange.Saved>(change).entry.nextStep)
        assertNull(ProcheStep.DONE.next)
        assertEquals(ProcheChange.Unknown, data.advance("inconnu").second)
    }

    @Test
    fun anEntryIsUpdatedInPlaceAndRemoved() {
        var data = ProchesData()
        val (first, a) = added(data, ProcheInput("[EXEMPLE] A"), Random(1))
        data = first
        val (second, b) = added(data, ProcheInput("[EXEMPLE] B"), Random(2))
        data = second
        val (updated, change) = data.update(a.id, ProcheInput("[EXEMPLE] A bis", listOf("culture"), ProcheStep.INVITE, today))
        val saved = assertIs<ProcheChange.Saved>(change).entry
        assertEquals(a.id, saved.id)
        assertEquals(listOf("[EXEMPLE] A bis", "[EXEMPLE] B"), updated.entries.map { it.nickname })
        assertEquals("2026-11-09", saved.reminder)
        val (removed, gone) = updated.remove(a.id)
        assertEquals(ProcheChange.Removed, gone)
        assertEquals(listOf(b.id), removed.entries.map { it.id })
        assertEquals(ProcheChange.Unknown, removed.remove(a.id).second)
        assertEquals(ProcheChange.Unknown, removed.update(a.id, ProcheInput("[EXEMPLE] A")).second)
    }

    @Test
    fun theRemindersWhoseDayHasComeComeFirstThenTheListThenWhatIsDone() {
        val entries = listOf(
            Proche("1", "[EXEMPLE] Sans rappel"),
            Proche("2", "[EXEMPLE] Plus tard", reminder = "2026-11-20"),
            Proche("3", "[EXEMPLE] Fait", step = ProcheStep.DONE.id, reminder = "2026-11-01"),
            Proche("4", "[EXEMPLE] Aujourd'hui", reminder = "2026-11-09"),
            Proche("5", "[EXEMPLE] En retard", reminder = "2026-11-02"),
            Proche("6", "[EXEMPLE] Aussi fait", step = ProcheStep.DONE.id),
        )
        val ordered = ProchesData(entries = entries).ordered(today).map { it.id }
        assertEquals(listOf("5", "4", "1", "2", "3", "6"), ordered)
        assertTrue(entries[4].isDue(today))
        assertFalse(entries[1].isDue(today), "a reminder still to come")
        assertFalse(entries[2].isDue(today), "a relative done is never due")
    }

    @Test
    fun theStoreWritesOnlyWhatChangesAndClearsAnEmptyList() = runTest {
        val storage = InMemoryProchesStorage()
        val store = ProchesStore(storage)
        assertTrue(store.read().isEmpty)
        val (data, change) = store.update { it.add(ProcheInput("[EXEMPLE] Sœur", listOf("education"))) }
        val entry = assertIs<ProcheChange.Saved>(change).entry
        assertEquals(1, storage.writes)
        assertEquals(data, store.read())
        // A step already done does not write again.
        store.update { current -> current to Unit }
        assertEquals(1, storage.writes)
        // The last one removed: nothing is left in the storage, not even an empty list.
        store.update { it.remove(entry.id) }
        assertNull(storage.value)
        store.update { it.add(ProcheInput("[EXEMPLE] Frère")) }
        store.clear()
        assertNull(storage.value)
        assertTrue(store.read().isEmpty)
    }

    @Test
    fun nothingOfTheRelativeAppearsInTheLogs() {
        val entry = Proche("1", "[EXEMPLE] Nom secret", listOf("sante"), reminder = "2026-11-10")
        assertFalse("secret" in entry.toString())
        assertFalse("secret" in ProchesData(entries = listOf(entry)).toString())
        assertFalse("secret" in ProcheInput("[EXEMPLE] Nom secret").toString())
    }
}
