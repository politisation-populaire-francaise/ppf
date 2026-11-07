package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.NOW
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.unsignedForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The answers to a call are erased seven days after its closing or its deadline (owner, 7 November 2026, question 8, option C). */
class CallAnswersTest {
    private val day = 86_400L

    private fun at(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int = 0) =
        ParisTime.toEpochSeconds(ParisDateTime(year, month, dayOfMonth, hour, minute))

    @Test
    fun aCallIsOverAtItsDeadlineOrElseAtWhatItAnnounces() {
        // A petition: its deadline (12 December, the whole day).
        assertEquals(at(2026, 12, 12, 23, 59), CallFixtures.call(CallFixtures.validDraft(CallType.PETITION), "petition").answersEndAt)
        // A meeting without deadline: its start.
        assertEquals(at(2026, 11, 14, 18, 30), CallFixtures.call(CallFixtures.validDraft(CallType.SE_REUNIR), "reunion").answersEndAt)
        // A call for witnesses dates past facts: without deadline nor closing, it is never over.
        assertNull(CallFixtures.call(CallFixtures.validDraft(CallType.TEMOINS), "temoins").answersEndAt)
        // Nothing dated, open: kept until the user withdraws it.
        assertNull(CallFixtures.call(CallFixtures.validDraft(CallType.AUTRE), "autre").answersEndAt)
    }

    @Test
    fun aClosedCallIsOverAtItsClosingWhenItComesFirst() {
        val open = CallFixtures.call(CallFixtures.validDraft(CallType.PETITION), "petition")
        val closedAt = NOW + day
        val closed = checkNotNull(Call.from(open.closedVersion("[EXEMPLE] Bilan.", closedAt).unsignedForTest()))
        assertEquals(closedAt, closed.answersEndAt)
        val witnesses = CallFixtures.call(CallFixtures.validDraft(CallType.TEMOINS), "temoins")
        assertEquals(closedAt, checkNotNull(Call.from(witnesses.closedVersion("", closedAt).unsignedForTest())).answersEndAt)
    }

    @Test
    fun answersAreErasedSevenDaysAfterTheEndOnly() {
        assertEquals(7 * day, CallResponseStore.KEPT_AFTER_END)
        val meeting = CallFixtures.call(CallFixtures.validDraft(CallType.SE_REUNIR), "reunion")
        val undated = CallFixtures.call(CallFixtures.validDraft(CallType.AUTRE), "autre")
        val answered = setOf(meeting.address.toString(), undated.address.toString(), "30023:${"a".repeat(64)}:inconnu")
        val end = checkNotNull(meeting.answersEndAt)
        val calls = listOf(meeting, undated)
        assertEquals(emptySet(), CallResponseStore.expired(answered, calls, end + 7 * day - 1))
        assertEquals(setOf(meeting.address.toString()), CallResponseStore.expired(answered, calls, end + 7 * day))
        // An unknown call keeps its answer.
        assertEquals(emptySet(), CallResponseStore.expired(answered, emptyList(), end + 365 * day))
    }
}
