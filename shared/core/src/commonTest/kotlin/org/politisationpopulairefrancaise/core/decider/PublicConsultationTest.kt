package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
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

/** DE-27: the official consultations, written by the team from their notice, answered only on the official page. */
class PublicConsultationTest {
    private val keys = IdentityKeys.generate()

    private fun article(slug: String, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, slug, "[EXEMPLE] Consultation $slug", null, emptyList(), ArticleType.CONSULTATION, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Guide.", extraTags = tags.toList())
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private fun consultation(
        slug: String = "c",
        kind: String = "consultation-ouverte",
        opens: String = "2026-11-02",
        closes: String = "2026-11-30",
        url: String = "https://www.exemple.gouv.fr/consultation",
        more: List<List<String>> = emptyList(),
    ): Article = article(
        slug,
        listOf(DeciderTags.CONSULTATION_KIND, kind),
        listOf(DeciderTags.ORGANIZER, "Ministère d’exemple"),
        listOf(DeciderTags.OFFICIAL_URL, url),
        listOf(DeciderTags.OPENED, opens),
        listOf(DeciderTags.CLOSES, closes),
        *more.toTypedArray(),
    )

    @Test
    fun aConsultationIsReadWithItsDatesAndSaysWhetherOneCanStillAnswer() {
        val c = assertNotNull(PublicConsultation.from(consultation()))
        assertEquals(ConsultationKind.OPEN_ONLINE, c.kind)
        assertEquals("Ministère d’exemple", c.organizer)
        assertEquals("www.exemple.gouv.fr", c.officialHost)
        assertEquals(29, c.durationDays)
        assertTrue(c.isUpcoming(CivilDate(2026, 11, 1)))
        assertTrue(c.isOpen(CivilDate(2026, 11, 2)) && c.isOpen(CivilDate(2026, 11, 30)), "the first and the last day included")
        assertTrue(c.isOver(CivilDate(2026, 12, 1)))
        assertEquals(23, c.daysLeft(CivilDate(2026, 11, 7)))
        assertEquals(0, c.daysLeft(CivilDate(2026, 12, 5)))
        assertEquals(emptyList(), c.warnings())
    }

    @Test
    fun aConsultationWithoutItsOfficialPageOrWithWrongDatesIsNotShown() {
        assertNull(PublicConsultation.from(consultation(url = "http://www.exemple.gouv.fr/c")), "https only")
        assertNull(PublicConsultation.from(consultation(url = "https://www.exemple.gouv.fr@pirate.example/c")), "no user name before the host")
        assertNull(PublicConsultation.from(consultation(url = "https://www.exemple.gouv.fr:8443/c")), "no port")
        assertNull(PublicConsultation.from(consultation(opens = "2026-11-30", closes = "2026-11-02")), "closes before it opens")
        assertNull(PublicConsultation.from(consultation(kind = "sondage")), "an unknown kind")
        assertNull(PublicConsultation.from(article("x", listOf(DeciderTags.CONSULTATION_KIND, "enquete-publique"))), "no organizer, no page, no dates")
    }

    @Test
    fun theSigningToolIsToldWhatLooksShorterThanTheLawOrOutsideAnOfficialSite() {
        // CRPA, L132-3: at least fifteen days.
        val short = assertNotNull(PublicConsultation.from(consultation(opens = "2026-11-02", closes = "2026-11-11")))
        assertEquals(1, short.warnings().size)
        // Code de l'environnement, L123-9: thirty days with an environmental assessment, fifteen otherwise.
        val inquiry = assertNotNull(PublicConsultation.from(consultation(kind = "enquete-publique", opens = "2026-12-01", closes = "2026-12-20")))
        assertEquals(1, inquiry.warnings().size, inquiry.warnings().toString())
        val elsewhere = assertNotNull(PublicConsultation.from(consultation(kind = "enquete-publique", opens = "2026-12-01", closes = "2027-01-08", url = "https://www.registre-exemple.fr/enquete")))
        assertEquals(1, elsewhere.warnings().size, "a hint, never a refusal")
    }

    @Test
    fun theHomeShowsTheOpenConsultationsFirstThenThoseToComeAndTheLocalOnesOnlyForTheTerritoriesOfTheDevice() {
        val catalog = DeciderCatalog.from(
            listOf(
                consultation(slug = "a-venir", opens = "2026-12-01", closes = "2026-12-20"),
                consultation(slug = "ouverte-tard", closes = "2026-12-31"),
                consultation(slug = "ouverte-tot", closes = "2026-11-20"),
                consultation(slug = "finie", opens = "2026-10-01", closes = "2026-10-31"),
                consultation(slug = "lyon", kind = "enquete-publique", opens = "2026-11-01", closes = "2026-12-15", more = listOf(listOf(DeciderTags.TERRITORY_CODE, "69123"))),
            ),
        )
        val today = CivilDate(2026, 11, 7)
        assertEquals(listOf("ouverte-tot", "ouverte-tard", "a-venir"), catalog.currentConsultations(today).map { it.article.slug })
        assertEquals(listOf("ouverte-tot", "lyon", "ouverte-tard", "a-venir"), catalog.currentConsultations(today, setOf("69123")).map { it.article.slug })
        assertNotNull(catalog.consultation(catalog.consultations.first().address))
    }

    @Test
    fun theRemindersComeAtTheOpeningAndThreeDaysBeforeTheLastDayAtTenParisTime() {
        val c = assertNotNull(PublicConsultation.from(consultation(opens = "2026-12-01", closes = "2027-01-08")))
        val beforeOpening = ParisTime.toEpochSeconds(CivilDate(2026, 11, 7), 12, 0)
        val plan = ConsultationReminders.plan(c, beforeOpening)
        assertEquals(listOf(ConsultationReminders.OPENING, ConsultationReminders.CLOSING), plan.map { it.first })
        assertEquals(listOf(CivilDate(2026, 12, 1), CivilDate(2027, 1, 5)), plan.map { ParisTime.moment(it.second).date })
        assertEquals(setOf(10), plan.map { ParisTime.moment(it.second).hour }.toSet())
        // Already open: the closing reminder only.
        val open = ParisTime.toEpochSeconds(CivilDate(2026, 12, 10), 12, 0)
        assertEquals(listOf(ConsultationReminders.CLOSING), ConsultationReminders.plan(c, open).map { it.first })
        // A local vote, on one day: the reminder of the day itself, never one before its opening.
        val vote = assertNotNull(PublicConsultation.from(consultation(kind = "consultation-locale", opens = "2027-02-14", closes = "2027-02-14")))
        assertEquals(listOf(ConsultationReminders.OPENING), ConsultationReminders.plan(vote, beforeOpening).map { it.first })
        assertEquals("decider:consultation:c/cloture", ConsultationReminders.reminderId(c, ConsultationReminders.CLOSING))
    }
}
