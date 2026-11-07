package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.NOW
import org.politisationpopulairefrancaise.core.agir.calls.CallFixtures.unsignedForTest
import org.politisationpopulairefrancaise.core.text.FrenchText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CallQueryTest {
    private val day = 86_400L

    /** Sunday 8 November 2026, 20:00: still this week. */
    private val sunday = ParisTime.toEpochSeconds(ParisDateTime(2026, 11, 8, 20, 0))

    private val meeting = CallFixtures.call(
        CallFixtures.validDraft(CallType.SE_REUNIR).copy(title = "[EXEMPLE] Assemblée des locataires du quartier", territory = "Lyon")
            .withDate(AgirFormats.TAG_START, DateTimeText.of(sunday)),
        slug = "assemblee",
        createdAt = NOW - 3 * day,
    )
    private val petition = CallFixtures.call(
        CallFixtures.validDraft(CallType.PETITION).copy(title = "[EXEMPLE] Pétition pour la ligne de bus 12", territory = "Métropole de Lyon"),
        slug = "petition",
        createdAt = NOW - 2 * day,
    ) // deadline 12 December: not this month
    private val volunteers = CallFixtures.call(
        CallFixtures.validDraft(CallType.BENEVOLES).copy(title = "[EXEMPLE] Bénévoles pour la permanence", territory = "Nord")
            .withDate(AgirFormats.TAG_DEADLINE, DateTimeText("27/11/2026", "")),
        slug = "benevoles",
        createdAt = NOW - day,
    )
    private val information = CallFixtures.call(
        CallFixtures.validDraft(CallType.INFORMATION).copy(title = "[EXEMPLE] Faire connaître le rapport sur l'eau", territory = "France"),
        slug = "information",
        createdAt = NOW,
    ) // no date at all
    private val closed = run {
        val open = CallFixtures.call(
            CallFixtures.validDraft(CallType.SOUTENIR).copy(title = "[EXEMPLE] Soutien à la cantine du collège", territory = "Nord")
                .withDate(AgirFormats.TAG_DEADLINE, DateTimeText("30/12/2026", "")),
            slug = "cantine",
            createdAt = NOW - 10 * day,
        )
        assertNotNull(Call.from(open.closedVersion("[EXEMPLE] La cantine reste ouverte.", NOW - 5 * day).unsignedForTest()))
    }
    private val all = listOf(meeting, petition, volunteers, information, closed)

    private fun slugs(query: CallQuery) = query.apply(all, NOW).map { it.slug }

    @Test
    fun byDefaultTheOpenCallsAreShownNewestFirst() {
        assertEquals(listOf("information", "benevoles", "petition", "assemblee"), slugs(CallQuery()))
        assertEquals(listOf("cantine"), slugs(CallQuery(status = CallStatusFilter.CLOSED)))
        assertEquals(5, slugs(CallQuery(status = CallStatusFilter.ALL)).size)
    }

    @Test
    fun typesAndTerritoriesNarrowTheWall() {
        assertEquals(listOf("petition", "assemblee"), slugs(CallQuery(types = setOf(CallType.PETITION, CallType.SE_REUNIR))))
        assertEquals(listOf("assemblee"), slugs(CallQuery(territories = setOf(FrenchText.fold("Lyon")))))
        assertEquals(listOf("benevoles", "petition"), slugs(CallQuery(territories = setOf("nord", "metropole de lyon"))))
        assertEquals(
            mapOf("france" to "France", "lyon" to "Lyon", "metropole de lyon" to "Métropole de Lyon", "nord" to "Nord"),
            CallQuery.territoriesOf(all),
        )
    }

    @Test
    fun theDeadlineFilterReadsTheParisCalendar() {
        assertEquals(listOf("assemblee"), slugs(CallQuery(due = CallDue.THIS_WEEK)))
        assertEquals(listOf("benevoles", "assemblee"), slugs(CallQuery(due = CallDue.THIS_MONTH)))
        assertEquals(listOf("information"), slugs(CallQuery(due = CallDue.NONE)))
        // A due date already passed is not "this week".
        assertEquals(emptyList(), CallQuery(due = CallDue.THIS_WEEK).apply(listOf(meeting), sunday + day))
    }

    @Test
    fun wordsAreSearchedInEveryText() {
        assertEquals(listOf("petition"), slugs(CallQuery(text = "bus")))
        assertEquals(listOf("assemblee"), slugs(CallQuery(text = "locataires assemb")))
        // The report of a closed call is searched too.
        assertEquals(listOf("cantine"), slugs(CallQuery(status = CallStatusFilter.ALL, text = "reste ouverte")))
        // A specific field: the organiser.
        assertEquals(listOf("assemblee"), slugs(CallQuery(text = "collectif fictif")))
    }

    @Test
    fun theClosestDateComesFirstThenCallsWithoutDateThenPassedOnes() {
        val passed = CallFixtures.call(
            CallFixtures.validDraft(CallType.SOUTENIR).copy(title = "[EXEMPLE] Soutien passé")
                .withDate(AgirFormats.TAG_DEADLINE, DateTimeText("12/11/2026", "")),
            slug = "passe",
        )
        val sorted = CallQuery(sort = CallSort.DEADLINE).apply(all + passed, NOW + 7 * day).map { it.slug }
        assertEquals(listOf("benevoles", "petition", "information", "passe", "assemblee"), sorted)
    }

    @Test
    fun theFilterButtonCountsEachConstraint() {
        assertEquals(0, CallQuery().constraintCount)
        assertEquals(0, CallQuery(sort = CallSort.DEADLINE).constraintCount)
        val busy = CallQuery(types = setOf(CallType.PETITION, CallType.TEMOINS), territories = setOf("nord"), due = CallDue.NONE, status = CallStatusFilter.ALL, text = "eau")
        assertEquals(6, busy.constraintCount)
        assertEquals(CallQuery(sort = CallSort.DEADLINE), busy.copy(sort = CallSort.DEADLINE).cleared())
    }
}
