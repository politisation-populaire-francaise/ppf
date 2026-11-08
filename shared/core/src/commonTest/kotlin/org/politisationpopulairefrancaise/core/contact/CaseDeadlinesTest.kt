package org.politisationpopulairefrancaise.core.contact

import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaseDeadlinesTest {

    private fun utc(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        CivilDate(year, month, day).toEpochDays() * 86_400 + hour * 3_600L + minute * 60L

    private val dataRights = CaseNature.Claim(ClaimMotif.DATA_RIGHTS)
    private val rightOfReply = CaseNature.Claim(ClaimMotif.RIGHT_OF_REPLY)

    @Test
    fun parisIsOneHourAheadInWinterAndTwoInSummer() {
        // 2026: from Sunday 29 March to Sunday 25 October, at 01:00 UTC.
        assertEquals(3_600, ParisTime.offsetSeconds(utc(2026, 3, 29, 0, 59)))
        assertEquals(7_200, ParisTime.offsetSeconds(utc(2026, 3, 29, 1)))
        assertEquals(7_200, ParisTime.offsetSeconds(utc(2026, 10, 25, 0, 59)))
        assertEquals(3_600, ParisTime.offsetSeconds(utc(2026, 10, 25, 1)))
        assertEquals(3_600, ParisTime.offsetSeconds(utc(2027, 1, 15, 12)))
        assertEquals(7_200, ParisTime.offsetSeconds(utc(2027, 7, 14, 12)))
        // The repeated hour of October gives the earlier instant, the skipped hour of March too.
        assertEquals(utc(2026, 10, 25, 0, 30), ParisTime.fromLocal(utc(2026, 10, 25, 2, 30)))
        assertEquals(utc(2026, 3, 29, 0, 30), ParisTime.fromLocal(utc(2026, 3, 29, 2, 30)))
    }

    @Test
    fun aRequestOnOnesDataIsAnsweredWithinOneCalendarMonth() {
        // 31 January 2027, 10:00 in Paris: 28 February, same hour.
        assertEquals(Deadline(utc(2027, 2, 28, 9), DeadlineKind.LEGAL), CaseDeadlines.of(dataRights, utc(2027, 1, 31, 9)))
        // Leap year.
        assertEquals(utc(2028, 2, 29, 9), CaseDeadlines.of(dataRights, utc(2028, 1, 31, 9))!!.at)
        // Across the change of hour: 15 March 10:00 (UTC+1), then 15 April 10:00 (UTC+2).
        assertEquals(utc(2027, 4, 15, 8), CaseDeadlines.of(dataRights, utc(2027, 3, 15, 9))!!.at)
        // The date counts in Paris: 30 January at 23:30 UTC is already 31 January in Paris, hence 28 February.
        assertEquals(utc(2027, 2, 27, 23, 30), CaseDeadlines.of(dataRights, utc(2027, 1, 30, 23, 30))!!.at)
        // December to January.
        assertEquals(utc(2027, 1, 15, 9), CaseDeadlines.of(dataRights, utc(2026, 12, 15, 9))!!.at)
    }

    @Test
    fun anExtensionGivesThreeMonthsAndIsPossibleOnlyWithinTheFirstMonth() {
        val received = utc(2027, 1, 31, 9)
        assertEquals(utc(2027, 4, 30, 8), CaseDeadlines.extendedDataRights(received))
        assertTrue(CaseDeadlines.canExtend(dataRights, received, received + 86_400, alreadyExtended = false))
        assertTrue(CaseDeadlines.canExtend(dataRights, received, utc(2027, 2, 28, 8, 59), alreadyExtended = false))
        assertFalse(CaseDeadlines.canExtend(dataRights, received, utc(2027, 2, 28, 9), alreadyExtended = false))
        assertFalse(CaseDeadlines.canExtend(dataRights, received, received + 86_400, alreadyExtended = true))
        assertFalse(CaseDeadlines.canExtend(rightOfReply, received, received + 86_400, alreadyExtended = false))
    }

    @Test
    fun aRightOfReplyIsDueThreeDaysAfterTheReception() {
        val received = utc(2026, 11, 8, 11)
        assertEquals(Deadline(received + 3 * 86_400, DeadlineKind.LEGAL), CaseDeadlines.of(rightOfReply, received))
        // Friday 23 October 2026, 12:00 in Paris: Monday 26, 12:00 in Paris, one hour later in UTC terms.
        assertEquals(utc(2026, 10, 26, 11), CaseDeadlines.of(rightOfReply, utc(2026, 10, 23, 10))!!.at)
    }

    @Test
    fun theOtherCasesHaveInternalGoalsThatTheServerCanChange() {
        val received = utc(2026, 11, 8, 11)
        fun days(n: Int) = Deadline(received + n * 86_400L, DeadlineKind.GOAL)
        assertEquals(days(15), CaseDeadlines.of(CaseNature.Contact(ContactCategory.JOURNALIST), received))
        assertEquals(days(15), CaseDeadlines.of(CaseNature.Claim(ClaimMotif.MODERATION_DECISION), received))
        assertEquals(days(30), CaseDeadlines.of(CaseNature.Claim(ClaimMotif.FUNDS), received))
        assertEquals(days(30), CaseDeadlines.of(CaseNature.Claim(ClaimMotif.TEAM_MEMBER), received))
        assertEquals(days(30), CaseDeadlines.of(CaseNature.Claim(ClaimMotif.OTHER), received))
        listOf(TicketSubtype.BUG, TicketSubtype.SUGGESTION, TicketSubtype.REPORT).forEach { assertNull(CaseDeadlines.of(CaseNature.Ticket(it), received), it.wire) }
        val goals = CaseGoals(contactDays = 10, bugDays = 20, moderationDecisionDays = 7)
        assertEquals(days(10), CaseDeadlines.of(CaseNature.Contact(ContactCategory.CITIZEN), received, goals))
        assertEquals(days(20), CaseDeadlines.of(CaseNature.Ticket(TicketSubtype.BUG), received, goals))
        assertEquals(days(7), CaseDeadlines.of(CaseNature.Claim(ClaimMotif.MODERATION_DECISION), received, goals))
        // The legal deadlines are never configurable.
        assertEquals(DeadlineKind.LEGAL, CaseDeadlines.of(dataRights, received, goals)!!.kind)
        assertFailsWith<IllegalArgumentException> { CaseGoals(contactDays = 0) }
        assertFailsWith<IllegalArgumentException> { CaseGoals(fundsDays = 400) }
    }

    @Test
    fun aRequestOfVerificationHasAGoalOfFifteenDays() {
        // CO-18 (D-143): a ticket for the fact-checking team of Comprendre, an internal goal, never a legal deadline.
        val verification = CaseNature.Ticket(TicketSubtype.VERIFICATION)
        assertEquals("verification", TicketSubtype.VERIFICATION.wire)
        assertEquals(TicketSubtype.VERIFICATION, TicketSubtype.fromWire("verification"))
        assertEquals(verification, CaseNature.fromWire("ticket", "verification", null, null))
        assertNull(CaseNature.fromWire("claim", "verification", null, null))
        // Friday 23 October 2026, 12:00 in Paris: Saturday 7 November, 12:00 in Paris (one hour later in UTC terms, past the change of hour).
        assertEquals(Deadline(utc(2026, 11, 7, 11), DeadlineKind.GOAL), CaseDeadlines.of(verification, utc(2026, 10, 23, 10)))
        val received = utc(2026, 11, 8, 11)
        assertEquals(Deadline(received + 15 * 86_400L, DeadlineKind.GOAL), CaseDeadlines.of(verification, received))
        assertEquals(received + 5 * 86_400L, CaseDeadlines.of(verification, received, CaseGoals(verificationDays = 5))!!.at)
        assertNull(CaseDeadlines.of(verification, received, CaseGoals(verificationDays = null)))
        assertFalse(CaseDeadlines.canExtend(verification, received, received + 86_400, alreadyExtended = false))
        assertFailsWith<IllegalArgumentException> { CaseGoals(verificationDays = 0) }
    }

    @Test
    fun aModerationDecisionMayBeContestedUntilSixMonthsAfterIt() {
        // 10 May 2026, 14:00 in Paris (12:00 UTC): until 10 November, 14:00 in Paris (13:00 UTC).
        val decision = utc(2026, 5, 10, 12)
        assertEquals(utc(2026, 11, 10, 13), CaseDeadlines.moderationClaimLimit(decision))
        assertTrue(CaseDeadlines.moderationClaimOpen(decision, decision))
        assertTrue(CaseDeadlines.moderationClaimOpen(decision, utc(2026, 11, 10, 13)))
        assertFalse(CaseDeadlines.moderationClaimOpen(decision, utc(2026, 11, 10, 13, 1)))
        // A decision dated after the claim: a few minutes of clock difference only.
        assertTrue(CaseDeadlines.moderationClaimOpen(decision + 600, decision))
        assertFalse(CaseDeadlines.moderationClaimOpen(decision + 601, decision))
        assertFalse(CaseDeadlines.moderationClaimOpen(-1, decision))
    }
}
