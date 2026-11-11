package org.politisationpopulairefrancaise.core.agir.calls

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.NOW
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.unsignedForTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Slots of volunteering (AP-11): written in the call, checked by the form, signups readable by the organizer only. */
class CallSlotsTest {
    private val organizer = IdentityKeys.generate()

    private fun draft(vararg slots: SlotDraft) = CallFixtures.validDraft(CallType.BENEVOLES).copy(slots = slots.toList())

    private fun call(draft: CallDraft): Call = requireNotNull(Call.from(draft.toUnsignedEvent(organizer.publicKey, NOW, "piquet-1a2b").unsignedForTest()))

    @Test
    fun theSlotsAreWrittenInTheCallAndReadBackANightSlotEndingTheNextDay() {
        val read = call(draft(SlotDraft("Piquet du matin", "14/11/2026", "6h", "9h", "4"), SlotDraft("Piquet de nuit", "14/11/2026", "22:00", "06:00", "2"))).slots
        assertEquals(listOf("c1", "c2"), read.map { it.id })
        assertEquals(3 * 3600L, read[0].end - read[0].start)
        assertEquals(8 * 3600L, read[1].end - read[1].start, "the end before the start is the next morning")
        assertEquals(4, read[0].places)
        // A type that is not cut into slots writes none, and a reader ignores them.
        val petition = CallFixtures.validDraft(CallType.PETITION).copy(slots = listOf(SlotDraft("Accueil", "14/11/2026", "6h", "9h", "4")))
        assertTrue(petition.toUnsignedEvent(organizer.publicKey, NOW).tags.none { it[0] == CallSlots.TAG })
    }

    @Test
    fun theFormSaysWhatIsWrongWithEachSlot() {
        val issues = CallRules.check(
            draft(
                SlotDraft("", "14/11/2026", "6h", "9h", "4"),
                SlotDraft("Accueil", "31/02/2026", "6h", "9h", "4"),
                SlotDraft("Accueil", "01/11/2026", "6h", "9h", "4"),
                SlotDraft("Accueil", "14/11/2026", "6h", "9h", "0"),
                SlotDraft("Appelez le 06 12 34 56 78", "14/11/2026", "6h", "9h", "4"),
                SlotDraft("Accueil", "14/11/2026", "6h", "9h", "4"),
            ),
            canFundraise = false,
            now = NOW,
        ).filter { it.field.startsWith("slot-") }
        assertEquals(
            listOf(
                CallIssue(CallIssueKind.MISSING_FIELD, "slot-0"),
                CallIssue(CallIssueKind.BAD_DATE, "slot-1"),
                CallIssue(CallIssueKind.PAST_DATE, "slot-2"),
                CallIssue(CallIssueKind.BAD_NUMBER, "slot-3"),
                CallIssue(CallIssueKind.PERSONAL_DATA, "slot-4"),
            ),
            issues,
        )
    }

    @Test
    fun aSignupIsReadByTheOrganizerOnlyAndNeverNamesTheAccount() = runTest {
        val call = call(draft(SlotDraft("Piquet du matin", "14/11/2026", "6h", "9h", "4")))
        val prepared = SlotSignups.prepare(call, call.slots.single(), "  Camille  ", work = 0, now = NOW)
        assertEquals(call.address.toString(), prepared.request.call)
        assertEquals("c1", prepared.request.slot)
        assertEquals(SlotSignups.hashToken(prepared.token), prepared.request.withdrawHash)
        assertFalse("Camille" in prepared.request.envelope.content)
        val opened = assertNotNull(SlotSignups.open(prepared.request.envelope, LocalMessagingSigner(organizer), call.address.toString()))
        assertEquals("Camille", opened.name)
        assertEquals("c1", opened.slot)
        // Someone else cannot open it, and a signup to another call is not shown.
        assertNull(SlotSignups.open(prepared.request.envelope, LocalMessagingSigner(IdentityKeys.generate()), call.address.toString()))
        assertNull(SlotSignups.open(prepared.request.envelope, LocalMessagingSigner(organizer), "30023:${organizer.publicKey.hex}:autre"))
        // Without a name, the organizer reads that none was given.
        assertEquals(SlotApi.NO_NAME, SlotSignups.cleanName("   "))
    }
}
