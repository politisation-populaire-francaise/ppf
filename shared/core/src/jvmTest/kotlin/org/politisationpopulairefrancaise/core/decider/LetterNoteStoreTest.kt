package org.politisationpopulairefrancaise.core.decider

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.Mandate
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DE-09, option A of D-119: the private notes of the letters sent, on the device only. */
class LetterNoteStoreTest {
    private val store = LetterNoteStore(JvmDatabase.inMemory())
    private val template = ContentAddress.parse("30023:${IdentityKeys.generate().publicKey.hex}:courrier-avant-un-vote-exemple")
    private val depute = ElectedOfficial(Mandate.DEPUTE, "6901", null, "[EXEMPLE] Camille Martin", null, null, null)

    @Test
    fun aLetterSentIsNotedOncePerOfficialWithItsAnswerAndErasedForGood() = runBlocking {
        val note = store.markSent(template, depute, CivilDate(2026, 11, 9))
        assertEquals("Camille Martin", note.official)
        store.markSent(template, null, CivilDate(2026, 11, 9))
        store.setAnswered(note.id, CivilDate(2026, 11, 20))
        // Sending again to the same official only moves the day, and keeps the answer.
        store.markSent(template, depute, CivilDate(2026, 11, 21))
        val notes = store.notesOf(template)
        assertEquals(2, notes.size)
        val again = notes.first { it.id == note.id }
        assertEquals(CivilDate(2026, 11, 21), again.sentOn)
        assertEquals(CivilDate(2026, 11, 20), again.answeredOn)
        assertEquals(Mandate.DEPUTE.key, again.mandate)
        assertNull(notes.first { it.id != note.id }.official, "a name the person wrote: nothing kept of it")

        store.setAnswered(note.id, null)
        assertNull(store.notesOf(template).first { it.id == note.id }.answeredOn)
        store.remove(note.id)
        assertEquals(1, store.notesOf(template).size)

        assertEquals(LetterNoteStore.ERASABLE_ID, store.erasableId)
        store.eraseFromDevice()
        assertTrue(store.notesOf(template).isEmpty())
        store.eraseFromDevice()
    }
}
