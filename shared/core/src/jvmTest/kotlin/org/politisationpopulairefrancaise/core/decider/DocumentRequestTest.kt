package org.politisationpopulairefrancaise.core.decider

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DE-31: a request for an administrative document, its deadlines counted on the device, and its reminders. */
class DocumentRequestTest {
    private val received = CivilDate(2026, 11, 9)
    private val note = DocumentRequestNote("1", "[EXEMPLE] Mairie", "[EXEMPLE] Délibération du 3 octobre 2026", DocumentAccessMode.EMAIL, received)

    @Test
    fun monthsRunFromDateToDateAndTheThirtyFirstBecomesTheLastDay() {
        assertEquals(CivilDate(2026, 12, 9), received.plusMonths(1))
        assertEquals(CivilDate(2027, 2, 28), CivilDate(2027, 1, 31).plusMonths(1))
        assertEquals(CivilDate(2028, 2, 29), CivilDate(2027, 12, 31).plusMonths(2))
        assertEquals(CivilDate(2027, 1, 1), CivilDate(2026, 12, 31).plusDays(1))
    }

    @Test
    fun silenceIsARefusalAfterAMonthThenTheCadaWithinTwo() {
        // R311-13: one month to answer.
        assertEquals(DocumentRequestStage.WAITING, DocumentRequestTimeline.stageOf(note, CivilDate(2026, 12, 9)))
        val waiting = DocumentRequestTimeline.of(note, CivilDate(2026, 12, 1))
        assertEquals(CivilDate(2026, 12, 9), waiting.answerBy)
        assertNull(waiting.refusal)
        // The day after, silence is a refusal (R*311-12); two months to ask the CADA (R343-1).
        val refused = DocumentRequestTimeline.of(note, CivilDate(2026, 12, 10))
        assertEquals(CivilDate(2026, 12, 10), refused.refusal)
        assertEquals(CivilDate(2027, 2, 10), refused.cadaBy)
        assertEquals(DocumentRequestStage.REFUSED, DocumentRequestTimeline.stageOf(note, CivilDate(2027, 2, 10)))
        assertEquals(DocumentRequestStage.CADA_TOO_LATE, DocumentRequestTimeline.stageOf(note, CivilDate(2027, 2, 11)))
        // A written refusal runs the two months from its own day.
        val written = note.copy(refusedOn = CivilDate(2026, 11, 20))
        assertEquals(CivilDate(2027, 1, 20), DocumentRequestTimeline.of(written, CivilDate(2026, 11, 21)).cadaBy)
        // The CADA: its opinion within a month (R343-3), the silence of the administration a refusal after two (R343-5).
        val cada = written.copy(cadaOn = CivilDate(2027, 1, 5))
        val withCada = DocumentRequestTimeline.of(cada, CivilDate(2027, 1, 6))
        assertEquals(CivilDate(2027, 2, 5), withCada.cadaOpinionBy)
        assertEquals(CivilDate(2027, 3, 5), withCada.refusedAfterCadaOn)
        assertEquals(DocumentRequestStage.WITH_CADA, DocumentRequestTimeline.stageOf(cada, CivilDate(2027, 3, 4)))
        assertEquals(DocumentRequestStage.REFUSED_AFTER_CADA, DocumentRequestTimeline.stageOf(cada, CivilDate(2027, 3, 5)))
        // The document arrived: nothing more counts.
        assertEquals(DocumentRequestStage.ANSWERED, DocumentRequestTimeline.stageOf(cada.copy(answeredOn = CivilDate(2027, 2, 1)), CivilDate(2027, 3, 5)))
    }

    @Test
    fun theRemindersComeAfterTheSilenceAndAWeekBeforeTheLastDayForTheCada() {
        val now = ParisTime.toEpochSeconds(received, 12, 0)
        val planned = DocumentRequestReminders.plan(note, now)
        assertEquals(listOf(DocumentRequestReminders.NO_ANSWER, DocumentRequestReminders.CADA), planned.map { it.first })
        assertEquals(ParisTime.toEpochSeconds(CivilDate(2026, 12, 10), 10, 0), planned[0].second)
        assertEquals(ParisTime.toEpochSeconds(CivilDate(2027, 2, 3), 10, 0), planned[1].second)
        // A written refusal: no « sans réponse », the CADA reminder from the refusal.
        val written = DocumentRequestReminders.plan(note.copy(refusedOn = CivilDate(2026, 11, 20)), now)
        assertEquals(listOf(DocumentRequestReminders.CADA to ParisTime.toEpochSeconds(CivilDate(2027, 1, 13), 10, 0)), written)
        // Nothing once the CADA is asked or the document arrived.
        assertTrue(DocumentRequestReminders.plan(note.copy(cadaOn = CivilDate(2027, 1, 5)), now).isEmpty())
        assertTrue(DocumentRequestReminders.plan(note.copy(answeredOn = CivilDate(2026, 11, 20)), now).isEmpty())
    }

    @Test
    fun theRequestsStayOnTheDeviceAndAreErasedForGood() = runBlocking {
        var next = 0
        val store = DocumentRequestStore(JvmDatabase.inMemory()) { "id-${next++}" }
        val first = store.add("  [EXEMPLE] Mairie  ", "[EXEMPLE] Délibération", DocumentAccessMode.COPY, received)
        store.add("[EXEMPLE] Préfecture", "[EXEMPLE] Arrêté", DocumentAccessMode.CONSULTATION, CivilDate(2026, 11, 12))
        assertEquals("[EXEMPLE] Mairie", first.administration)
        assertEquals(listOf("id-1", "id-0"), store.all().map { it.id }, "the latest received first")
        store.setRefused(first.id, CivilDate(2026, 11, 20))
        store.setCada(first.id, CivilDate(2026, 12, 1))
        store.setAnswered(first.id, CivilDate(2027, 1, 2))
        val read = store.request(first.id)!!
        assertEquals(DocumentAccessMode.COPY, read.mode)
        assertEquals(CivilDate(2026, 11, 20), read.refusedOn)
        assertEquals(CivilDate(2026, 12, 1), read.cadaOn)
        assertEquals(CivilDate(2027, 1, 2), read.answeredOn)
        store.setCada(first.id, null)
        assertNull(store.request(first.id)!!.cadaOn)
        store.remove(first.id)
        assertEquals(1, store.all().size)
        store.eraseFromDevice()
        assertTrue(store.all().isEmpty())
        assertEquals(DocumentRequestStore.ERASABLE_ID, store.erasableId)
    }
}
