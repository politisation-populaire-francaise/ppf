package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DE-28: the elections of the personal calendar, their dates written from the sources, checked for consistency only. */
class ElectionCalendarTest {
    private val keys = IdentityKeys.generate()

    private fun election(vararg tags: List<String>, slug: String = "election"): Article {
        val all = listOf(listOf(DeciderTags.ELECTION, "presidentielle-2027", "[EXEMPLE] Élection présidentielle", "")) + tags.toList()
        val unsigned = Article.build(keys.publicKey, slug, "[EXEMPLE] Élection", null, emptyList(), ArticleType.ELECTION, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = all)
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private val announced = listOf(DeciderTags.ELECTION_STATUS, "annoncee")
    private val round1 = listOf(DeciderTags.ROUND, "1", "2027-04-18", "2027-04-17", "20:00")
    private val round2 = listOf(DeciderTags.ROUND, "2", "2027-05-02", "2027-05-01", "20:00")
    private fun deadline(kind: String, date: String) = listOf(DeciderTags.DEADLINE, kind, date, "https://exemple.invalid/source")

    @Test
    fun theSixthFridayBeforeTheVoteIsTheGeneralRuleOfRegistration() {
        // Code électoral, article L17: for a vote on Sunday 18 April 2027, Friday 12 March 2027.
        assertEquals(CivilDate(2027, 3, 12), ElectionCalendar.sixthFridayBefore(CivilDate(2027, 4, 18)))
        assertEquals(CivilDate(2022, 3, 4), ElectionCalendar.sixthFridayBefore(CivilDate(2022, 4, 10)))
        // A vote on a Friday: the Friday before does not count the day itself.
        assertEquals(CivilDate(2027, 3, 12), ElectionCalendar.sixthFridayBefore(CivilDate(2027, 4, 23)))
    }

    @Test
    fun anAnnouncedElectionIsReadWithItsRoundsAndDeadlinesAndADateThatLooksOddIsSaid() {
        val calendar = assertNotNull(ElectionCalendar.from(election(announced, round2, round1, deadline("inscription-mairie", "2027-03-12"), deadline("procuration-conseillee", "2027-04-08"))))
        assertEquals(ElectionStatus.ANNOUNCED, calendar.status)
        assertNull(calendar.decree)
        assertEquals(listOf(1, 2), calendar.rounds.map { it.number })
        assertEquals(CivilDate(2027, 4, 18), calendar.firstDay)
        assertEquals(emptyList(), calendar.warnings())
        assertEquals(listOf(DeadlineKind.PROXY), calendar.deadlinesAhead(CivilDate(2027, 3, 20)).map { it.kind })
        assertTrue(calendar.isUpcoming(CivilDate(2027, 5, 2)))
        assertTrue(!calendar.isUpcoming(CivilDate(2027, 5, 3)))

        val odd = assertNotNull(ElectionCalendar.from(election(announced, round1, deadline("inscription-mairie", "2027-03-13"))))
        assertEquals(1, odd.warnings().size, "not the sixth Friday: the tool asks to check the source, it never decides the law")
    }

    @Test
    fun aConvenedElectionNamesItsDecreeAndAnAnnouncedOneHasNone() {
        val decree = listOf(DeciderTags.DECREE, "2027-00", "2027-01-28", "https://www.legifrance.gouv.fr/jorf/id/EXEMPLE")
        assertNotNull(ElectionCalendar.from(election(listOf(DeciderTags.ELECTION_STATUS, "convoquee"), decree, round1)))
        assertNull(ElectionCalendar.from(election(listOf(DeciderTags.ELECTION_STATUS, "convoquee"), round1)), "convened without its decree")
        assertNull(ElectionCalendar.from(election(announced, decree, round1)), "announced with a decree")
        assertNull(ElectionCalendar.from(election(announced)), "no round")
        assertNull(ElectionCalendar.from(election(announced, round1, deadline("inscription-mairie", "2027-03-12"), deadline("inscription-mairie", "2027-03-05"))), "the same deadline twice")
        assertNull(ElectionCalendar.from(election(announced, round1, listOf(DeciderTags.DEADLINE, "inscription-mairie", "2027-03-12", "pas une adresse"))), "a deadline without its source")
    }

    @Test
    fun theLocalElectionsAreShownOnlyForTheTerritoriesKeptOnTheDevice() {
        val national = election(announced, round1, slug = "nationale")
        val local = election(announced, round1, listOf(DeciderTags.TERRITORY_CODE, "69123"), slug = "municipale-lyon")
        val catalog = DeciderCatalog.from(listOf(national, local))
        assertEquals(listOf("nationale"), catalog.upcomingElections(CivilDate(2027, 1, 1)).map { it.article.slug })
        assertEquals(2, catalog.upcomingElections(CivilDate(2027, 1, 1), setOf("69123")).size)
    }
}
