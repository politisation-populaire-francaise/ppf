package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobilizationDraftTest {
    private val today = CivilDate(2026, 11, 13)
    private val author = IdentityKeys.generate().publicKey

    private val valid = MobilizationDraft(
        type = MobilizationType.MANIFESTATION,
        title = "[EXEMPLE] Marche pour l'hôpital public",
        startDate = "14/11/2026",
        startTime = "14 h",
        endTime = "17h00",
        location = "Place de la Gare, Villefictive",
        territory = "Villefictive",
        summary = "[EXEMPLE] Résumé",
        description = "[EXEMPLE] Description",
        link = "exemple.invalid/marche",
        declaration = DeclarationState.FAITE,
    )

    @Test
    fun datesAreReadInFrenchOrder() {
        assertEquals(CivilDate(2026, 11, 14), MobilizationDraft.parseDate("14/11/2026", today))
        assertEquals(CivilDate(2026, 11, 14), MobilizationDraft.parseDate(" 14-11-2026 ", today))
        assertEquals(CivilDate(2026, 11, 14), MobilizationDraft.parseDate("14.11.26", today))
        assertEquals(CivilDate(2026, 11, 14), MobilizationDraft.parseDate("2026-11-14", today))
        assertEquals(CivilDate(2026, 11, 14), MobilizationDraft.parseDate("14 11 2026", today))
        // Without year: this year, or the next one once the date has passed.
        assertEquals(CivilDate(2026, 12, 5), MobilizationDraft.parseDate("5/12", today))
        assertEquals(CivilDate(2027, 1, 9), MobilizationDraft.parseDate("9/1", today))
        assertEquals(CivilDate(2026, 11, 13), MobilizationDraft.parseDate("13/11", today))
        assertNull(MobilizationDraft.parseDate("31/11/2026", today))
        assertNull(MobilizationDraft.parseDate("29/02/2027", today))
        assertEquals(CivilDate(2028, 2, 29), MobilizationDraft.parseDate("29/02/2028", today))
        assertNull(MobilizationDraft.parseDate("demain", today))
        assertNull(MobilizationDraft.parseDate("", today))
        assertNull(MobilizationDraft.parseDate("1/2/3/4", today))
    }

    @Test
    fun hoursAreReadAsFrenchPeopleWriteThem() {
        assertEquals(14 to 30, MobilizationDraft.parseTime("14:30"))
        assertEquals(14 to 30, MobilizationDraft.parseTime("14h30"))
        assertEquals(14 to 30, MobilizationDraft.parseTime("14 h 30"))
        assertEquals(14 to 0, MobilizationDraft.parseTime("14H"))
        assertEquals(9 to 0, MobilizationDraft.parseTime("9"))
        assertEquals(9 to 5, MobilizationDraft.parseTime("9.05"))
        assertNull(MobilizationDraft.parseTime("24:00"))
        assertNull(MobilizationDraft.parseTime("14:3"))
        assertNull(MobilizationDraft.parseTime("14:60"))
        assertNull(MobilizationDraft.parseTime("midi"))
        assertNull(MobilizationDraft.parseTime(""))
    }

    @Test
    fun aCompleteDraftBecomesAValidEventInParisTime() {
        assertEquals(emptyList(), valid.problems(AgendaTimes.NOW))
        val event = assertNotNull(valid.toUnsignedEvent(author, AgendaTimes.NOW, "marche-hopital-0a1b2c3d"))
        assertEquals(emptyList(), MobilizationRules.check(event))
        fun tag(name: String) = event.tags.firstOrNull { it[0] == name }?.get(1)
        assertEquals(AgendaTimes.SATURDAY_14H.toString(), tag("start"))
        assertEquals(AgendaTimes.SATURDAY_17H.toString(), tag("end"))
        assertEquals("https://exemple.invalid/marche", tag("r"))
        assertEquals("faite", tag("ppf-declaration"))
        assertEquals("marche-hopital-0a1b2c3d", tag("d"))
        assertEquals(AgendaTimes.NOW, event.createdAt)
        // The declaration belongs to demonstrations only.
        val gathering = valid.copy(type = MobilizationType.RASSEMBLEMENT).toUnsignedEvent(author, AgendaTimes.NOW, "x")
        assertNull(assertNotNull(gathering).tags.firstOrNull { it[0] == "ppf-declaration" })
    }

    @Test
    fun anEmptyDraftListsWhatIsMissing() {
        val problems = MobilizationDraft().problems(AgendaTimes.NOW)
        assertEquals(
            listOf(
                DraftProblem.TYPE_MISSING, DraftProblem.TITLE_MISSING, DraftProblem.START_DATE_MISSING,
                DraftProblem.START_TIME_MISSING, DraftProblem.LOCATION_MISSING, DraftProblem.TERRITORY_MISSING,
            ),
            problems,
        )
        assertTrue(problems.all { it.missing })
        assertTrue(MobilizationDraft().isBlank)
        assertNull(MobilizationDraft().toUnsignedEvent(author, AgendaTimes.NOW, "x"))
    }

    @Test
    fun wrongValuesAreNamedWithTheServerCodes() {
        fun problems(draft: MobilizationDraft) = draft.problems(AgendaTimes.NOW)
        assertEquals(listOf(DraftProblem.START_DATE_INVALID), problems(valid.copy(startDate = "32/11")))
        assertEquals(listOf(DraftProblem.START_TIME_INVALID), problems(valid.copy(startTime = "25h")))
        assertEquals(listOf(DraftProblem.START_IN_PAST), problems(valid.copy(startDate = "12/11/2026", endTime = "")))
        assertEquals(listOf(DraftProblem.END_BEFORE_START), problems(valid.copy(endTime = "13h")))
        assertEquals(DraftProblem.END_BEFORE_START.error, MobilizationError.BAD_END)
        assertEquals(listOf(DraftProblem.END_TIME_MISSING), problems(valid.copy(endDate = "15/11/2026", endTime = "")))
        assertEquals(listOf(DraftProblem.END_DATE_INVALID), problems(valid.copy(endDate = "jamais")))
        assertEquals(listOf(DraftProblem.TITLE_TOO_LONG), problems(valid.copy(title = "a".repeat(141))))
        assertEquals(listOf(DraftProblem.LOCATION_TOO_LONG), problems(valid.copy(location = "a".repeat(201))))
        assertEquals(listOf(DraftProblem.TERRITORY_TOO_LONG), problems(valid.copy(territory = "a".repeat(101))))
        assertEquals(listOf(DraftProblem.SUMMARY_TOO_LONG), problems(valid.copy(summary = "a".repeat(281))))
        assertEquals(listOf(DraftProblem.DESCRIPTION_TOO_LONG), problems(valid.copy(description = "a".repeat(8001))))
        assertEquals(listOf(DraftProblem.LINK_INVALID), problems(valid.copy(link = "ftp://exemple.invalid")))
        assertEquals(MobilizationError.TOO_LONG, DraftProblem.TITLE_TOO_LONG.error)
    }

    @Test
    fun anEventOverSeveralDaysEndsOnItsOwnDate() {
        val strike = valid.copy(type = MobilizationType.GREVE, startDate = "14/11/2026", startTime = "6h", endDate = "16/11/2026", endTime = "22h")
        assertEquals(emptyList(), strike.problems(AgendaTimes.NOW))
        val end = assertNotNull(strike.end(today))
        assertEquals(CivilDate(2026, 11, 16), ParisTime.moment(end).date)
        assertEquals(22, ParisTime.moment(end).hour)
    }
}
