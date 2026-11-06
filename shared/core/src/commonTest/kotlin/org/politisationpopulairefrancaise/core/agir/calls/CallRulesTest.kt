package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.NOW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallRulesTest {

    private fun issues(draft: CallDraft, canFundraise: Boolean = false) = CallRules.check(draft, canFundraise, NOW)

    private fun kinds(draft: CallDraft, canFundraise: Boolean = false) = issues(draft, canFundraise).map { it.kind to it.field }.toSet()

    @Test
    fun aCompleteDraftOfEachTypePasses() {
        CallType.entries.forEach { type ->
            assertEquals(emptyList(), issues(CallFixtures.validDraft(type), canFundraise = true), type.tagValue)
        }
    }

    @Test
    fun anEmptyDraftNeedsATypeATitleATextAndATerritory() {
        val found = kinds(CallDraft())
        assertTrue((CallIssueKind.MISSING_TYPE to "type") in found)
        assertTrue((CallIssueKind.TOO_SHORT to "title") in found)
        assertTrue((CallIssueKind.TOO_SHORT to "text") in found)
        assertTrue((CallIssueKind.MISSING_TERRITORY to "territory") in found)
    }

    @Test
    fun eachTypeRequiresItsOwnFields() {
        // AP-10, one requirement at a time removed from a complete draft.
        CallType.entries.forEach { type ->
            type.requirements.forEach { requirement ->
                var draft = CallFixtures.validDraft(type)
                requirement.alternatives.forEach { tag ->
                    draft = when (tag) {
                        AgirFormats.TAG_LOCATION -> draft.copy(location = "")
                        AgirFormats.TAG_LINK -> draft.copy(link = "")
                        AgirFormats.TAG_GOAL -> draft.copy(goal = "")
                        in CallFields.DATE_TAGS -> draft.withDate(tag, DateTimeText())
                        else -> draft.withField(tag, "")
                    }
                }
                assertEquals(listOf(CallIssue(CallIssueKind.MISSING_FIELD, requirement.key)), issues(draft, canFundraise = true), "${type.tagValue} ${requirement.key}")
            }
        }
    }

    @Test
    fun aMeetingNeedsAPlaceOrALinkAndVolunteersAPlaceOrRemoteWork() {
        val meeting = CallFixtures.validDraft(CallType.SE_REUNIR).copy(location = "")
        assertEquals(setOf(CallIssueKind.MISSING_FIELD to "location|r"), kinds(meeting))
        assertEquals(emptySet(), kinds(meeting.copy(link = "https://visio.exemple.org/reunion")))

        val volunteers = CallFixtures.validDraft(CallType.BENEVOLES).copy(location = "")
        assertEquals(setOf(CallIssueKind.MISSING_FIELD to "location|ppf-remote"), kinds(volunteers))
        assertEquals(emptySet(), kinds(volunteers.copy(location = "Local fictif")))
        assertEquals(emptySet(), kinds(volunteers.withField(CallFields.REMOTE, CallFields.YES)))
    }

    @Test
    fun lengthsNumbersChoicesAndLinksAreChecked() {
        val base = CallFixtures.validDraft(CallType.PETITION)
        assertEquals(setOf(CallIssueKind.TOO_LONG to "title"), kinds(base.copy(title = "a".repeat(141))))
        assertEquals(setOf(CallIssueKind.TOO_SHORT to "title"), kinds(base.copy(title = "Court")))
        assertEquals(setOf(CallIssueKind.TOO_LONG to "text"), kinds(base.copy(text = "a ".repeat(4_001))))
        assertEquals(setOf(CallIssueKind.TOO_LONG to "summary"), kinds(base.copy(summary = "a".repeat(301))))
        assertEquals(setOf(CallIssueKind.BAD_NUMBER to "ppf-goal"), kinds(base.copy(goal = "beaucoup")))
        assertEquals(setOf(CallIssueKind.BAD_NUMBER to "ppf-goal"), kinds(base.copy(goal = "0")))
        assertEquals(setOf(CallIssueKind.BAD_NUMBER to "ppf-goal"), kinds(base.copy(goal = "1234567890")))
        assertEquals(setOf(CallIssueKind.BAD_CHOICE to "ppf-platform"), kinds(base.withField(CallFields.PLATFORM, "change-org")))
        assertEquals(setOf(CallIssueKind.BAD_LINK to "r"), kinds(base.copy(link = "http://exemple.org/petition")))
        assertEquals(setOf(CallIssueKind.BAD_LINK to "r"), kinds(base.copy(link = "exemple.org")))
        assertEquals(setOf(CallIssueKind.BAD_LINK to "r"), kinds(base.copy(link = "https://exemple org")))
        assertEquals(setOf(CallIssueKind.BAD_LINK to "r"), kinds(base.copy(link = "https://localhost/")))
        assertEquals(setOf(CallIssueKind.TOO_LONG to "territory"), kinds(base.copy(territory = "a".repeat(81))))

        assertTrue(CallRules.isValidLink("https://www.helloasso.com/associations/exemple/collectes/hiver"))
        assertEquals("www.helloasso.com", CallRules.hostOf("https://WWW.helloasso.com:443/associations?x=1"))
        assertEquals(null, CallRules.hostOf("javascript:alert(1)"))
    }

    @Test
    fun datesMustBeReadableInTheFutureAndInOrder() {
        val meeting = CallFixtures.validDraft(CallType.MANIFESTER)
        assertEquals(setOf(CallIssueKind.BAD_DATE to "start"), kinds(meeting.withDate("start", DateTimeText("31/02/2027", "18:00"))))
        assertEquals(setOf(CallIssueKind.BAD_DATE to "start"), kinds(meeting.withDate("start", DateTimeText("14/11/2026", ""))))
        assertEquals(setOf(CallIssueKind.PAST_DATE to "start"), kinds(meeting.withDate("start", DateTimeText("01/11/2026", "18:00"))))
        assertEquals(setOf(CallIssueKind.PAST_DATE to "ppf-deadline"), kinds(meeting.withDate("ppf-deadline", DateTimeText("05/11/2026", "12:00"))))
        assertEquals(setOf(CallIssueKind.BAD_DATE to "ppf-deadline"), kinds(meeting.withDate("ppf-deadline", DateTimeText("bientôt", ""))))
        // The current hour is accepted: the author is typing it.
        assertEquals(emptySet(), kinds(meeting.withDate("start", DateTimeText("06/11/2026", "09:30"))))

        // Witnesses look into facts already past, with an end after the start.
        val witnesses = CallFixtures.validDraft(CallType.TEMOINS)
        assertEquals(emptySet(), kinds(witnesses))
        assertEquals(setOf(CallIssueKind.END_BEFORE_START to "end"), kinds(witnesses.withDate("end", DateTimeText("01/11/2026", "13:00"))))
    }

    @Test
    fun personalDataInAnyTextBlocksTheSending() {
        val base = CallFixtures.validDraft(CallType.SE_REUNIR)
        assertEquals(setOf(CallIssueKind.PERSONAL_DATA to "text"), kinds(base.copy(text = base.text + " Écrivez à prenom.nom@exemple.fr")))
        assertEquals(setOf(CallIssueKind.PERSONAL_DATA to "ppf-organizer"), kinds(base.withField(CallFields.ORGANIZER, "Appelez le 06 12 34 56 78")))
        assertEquals(setOf(CallIssueKind.PERSONAL_DATA to "summary"), kinds(base.copy(summary = "IBAN FR76 3000 6000 0112 3456 7890 189")))
    }

    @Test
    fun onlyVerifiedOrganisationsRaiseFundsAndNeverForACandidateOrAParty() {
        val fundraising = CallFixtures.validDraft(CallType.COLLECTE)
        assertEquals(emptySet(), kinds(fundraising, canFundraise = true))
        assertEquals(setOf(CallIssueKind.FUNDRAISING_NOT_ALLOWED to "type"), kinds(fundraising, canFundraise = false))
        assertEquals(setOf(CallIssueKind.FUNDRAISING_PLEDGE to "pledge"), kinds(fundraising.copy(notForCandidateOrParty = false), canFundraise = true))
        // The pledge only concerns fundraising.
        assertEquals(emptySet(), kinds(CallFixtures.validDraft(CallType.SOUTENIR).copy(notForCandidateOrParty = false)))
    }

    @Test
    fun aReportIsShortAndWithoutPersonalData() {
        assertEquals(emptyList(), CallRules.checkReport(""))
        assertEquals(emptyList(), CallRules.checkReport("[EXEMPLE] 40 personnes sont venues, une pétition est lancée."))
        assertEquals(listOf(CallIssue(CallIssueKind.TOO_LONG, "ppf-report")), CallRules.checkReport("a".repeat(2_001)))
        assertEquals(listOf(CallIssue(CallIssueKind.PERSONAL_DATA, "ppf-report")), CallRules.checkReport("Contact : 06 12 34 56 78"))
    }
}
