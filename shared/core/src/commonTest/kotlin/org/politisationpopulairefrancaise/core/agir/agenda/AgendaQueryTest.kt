package org.politisationpopulairefrancaise.core.agir.agenda

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AgendaQueryTest {
    private val fixtures = AgendaFixtures()
    private val now = AgendaTimes.NOW // Friday 13 November 2026, 10 h
    private val hour = AgendaTimes.HOUR
    private val day = AgendaTimes.DAY

    private suspend fun agenda(): List<Mobilization> = listOf(
        // This morning, already over.
        fixtures.mobilization("ce-matin", "[EXEMPLE] Piquet de grève", start = now - 3 * hour, end = now - hour, type = MobilizationType.GREVE, territory = "Villefictive"),
        // Under way now (no end: two hours).
        fixtures.mobilization("en-cours", "[EXEMPLE] Rassemblement devant la mairie", start = now - hour, end = null, territory = "Villefictive"),
        // Tomorrow, Saturday.
        fixtures.mobilization("samedi", "[EXEMPLE] Marche pour l'hôpital", start = AgendaTimes.SATURDAY_14H, end = AgendaTimes.SATURDAY_17H, type = MobilizationType.MANIFESTATION, territory = "Métropole de Bourg-Fictif", organizers = listOf("a".repeat(64))),
        // Monday 16, the following week.
        fixtures.mobilization("lundi", "[EXEMPLE] Assemblée générale des étudiants", start = now + 3 * day, end = now + 3 * day + 2 * hour, type = MobilizationType.ASSEMBLEE_GENERALE, territory = "France"),
        // In December.
        fixtures.mobilization("decembre", "[EXEMPLE] Blocage du dépôt", start = now + 20 * day, end = now + 20 * day + hour, type = MobilizationType.BLOCAGE, territory = "Villefictive"),
        // Last month.
        fixtures.mobilization("octobre", "[EXEMPLE] Réunion publique", start = now - 20 * day, end = now - 20 * day + hour, type = MobilizationType.REUNION, territory = "France"),
    )

    private fun ids(list: List<Mobilization>) = list.map { it.identifier }

    @Test
    fun upcomingAreSoonestFirstAndPastMostRecentFirst() = runTest {
        val all = agenda()
        assertEquals(listOf("en-cours", "samedi", "lundi", "decembre"), ids(AgendaQuery().apply(all, now)))
        assertEquals(listOf("ce-matin", "octobre"), ids(AgendaQuery(timing = AgendaTiming.PAST).apply(all, now)))
        assertEquals(listOf("octobre", "ce-matin", "en-cours", "samedi", "lundi", "decembre"), ids(AgendaQuery(timing = AgendaTiming.ALL).apply(all, now)))
    }

    @Test
    fun periodsFollowTheLocalCalendar() = runTest {
        val all = agenda()
        fun query(period: AgendaPeriod, timing: AgendaTiming = AgendaTiming.ALL) = ids(AgendaQuery(timing = timing, period = period).apply(all, now))
        assertEquals(listOf("ce-matin", "en-cours"), query(AgendaPeriod.Today))
        assertEquals(listOf("en-cours"), query(AgendaPeriod.Today, AgendaTiming.UPCOMING))
        // Monday 9 to Sunday 15 November.
        assertEquals(listOf("ce-matin", "en-cours", "samedi"), query(AgendaPeriod.ThisWeek))
        assertEquals(listOf("ce-matin", "en-cours", "samedi", "lundi"), query(AgendaPeriod.ThisMonth))
        assertEquals(listOf("samedi", "lundi"), query(AgendaPeriod.Range(CivilDate(2026, 11, 14), CivilDate(2026, 11, 16))))
        assertEquals(listOf("octobre"), query(AgendaPeriod.Range(null, CivilDate(2026, 11, 1))))
        assertEquals(listOf("decembre"), query(AgendaPeriod.Range(CivilDate(2026, 12, 1), null)))
        assertEquals(ParisTime.epochDayOf(CivilDate(2026, 11, 9))..ParisTime.epochDayOf(CivilDate(2026, 11, 15)), AgendaPeriod.ThisWeek.days(now))
    }

    @Test
    fun anEventOverSeveralDaysMatchesEachOfItsDays() = runTest {
        val strike = fixtures.mobilization("greve", start = AgendaTimes.SATURDAY_14H, end = AgendaTimes.SATURDAY_14H + 3 * day, type = MobilizationType.GREVE)
        val query = AgendaQuery(timing = AgendaTiming.ALL, period = AgendaPeriod.Range(CivilDate(2026, 11, 16), CivilDate(2026, 11, 16)))
        assertEquals(listOf("greve"), ids(query.apply(listOf(strike), now)))
    }

    @Test
    fun territoriesTypesOrganisersAndWordsNarrowTheList() = runTest {
        val all = agenda()
        fun query(q: AgendaQuery) = ids(q.copy(timing = AgendaTiming.ALL).apply(all, now))
        assertEquals(listOf("ce-matin", "en-cours", "decembre"), query(AgendaQuery(territories = setOf(AgendaQuery.territoryKey("VILLEFICTIVE")))))
        assertEquals(listOf("samedi"), query(AgendaQuery(territories = setOf(AgendaQuery.territoryKey("metropole de bourg fictif")))))
        assertEquals(listOf("ce-matin", "decembre"), query(AgendaQuery(types = setOf(MobilizationType.GREVE, MobilizationType.BLOCAGE))))
        assertEquals(listOf("samedi"), query(AgendaQuery(organizers = setOf("a".repeat(64)))))
        // The author hosts its events, whether they name other organisers or not.
        assertEquals(listOf("octobre", "ce-matin", "en-cours", "samedi", "lundi", "decembre"), query(AgendaQuery(organizers = setOf(fixtures.pubkey))))
        assertEquals(emptyList(), query(AgendaQuery(organizers = setOf("b".repeat(64)))))
        assertEquals(listOf("samedi"), query(AgendaQuery(text = "hopital")))
        assertEquals(listOf("lundi"), query(AgendaQuery(text = "assemblee etud")))
        assertEquals(emptyList(), query(AgendaQuery(text = "hopital", types = setOf(MobilizationType.BLOCAGE))))
    }

    @Test
    fun theFiltersAreCountedAndCleared() {
        val query = AgendaQuery(
            timing = AgendaTiming.PAST,
            period = AgendaPeriod.ThisWeek,
            territories = setOf("lyon", "paris"),
            types = setOf(MobilizationType.GREVE),
            organizers = setOf("a".repeat(64)),
            text = "hopital",
        )
        assertEquals(6, query.constraintCount)
        assertEquals(AgendaQuery(timing = AgendaTiming.PAST), query.cleared())
        assertEquals(0, AgendaQuery().constraintCount)
    }
}
