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

/** The reminders of an official petition (MVP 1): a week before its closing and the day before, at 10 h, Paris time. */
class PetitionRemindersTest {
    private val keys = IdentityKeys.generate()

    private fun petition(closes: String?): Petition {
        val tags = buildList {
            add(listOf(DeciderTags.PLATFORM, "assemblee"))
            add(listOf(DeciderTags.OFFICIAL_URL, "https://petitions.assemblee-nationale.fr/initiatives/i-1234"))
            add(listOf(DeciderTags.PETITION_STATUS, "ouverte"))
            closes?.let { add(listOf(DeciderTags.CLOSES, it)) }
        }
        val unsigned = Article.build(keys.publicKey, "p", "[EXEMPLE] Pétition", null, emptyList(), ArticleType.PETITION, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = tags)
        return Petition.from(Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!)!!
    }

    private fun tenOn(year: Int, month: Int, day: Int) = ParisTime.toEpochSeconds(CivilDate(year, month, day), 10, 0)

    @Test
    fun aWeekBeforeTheClosingAndTheDayBeforeAtTenParisTime() {
        val petition = petition("2027-02-02")
        val plan = PetitionReminders.plan(petition, now = tenOn(2026, 11, 11))
        assertEquals(listOf(PetitionReminders.WEEK_BEFORE to tenOn(2027, 1, 26), PetitionReminders.EVE to tenOn(2027, 2, 1)), plan)
        assertEquals("decider:petition:p/veille", PetitionReminders.reminderId(petition, PetitionReminders.EVE))
    }

    @Test
    fun onlyTheRemindersStillToComeAndNoneWithoutAClosingDate() {
        assertEquals(listOf(PetitionReminders.EVE to tenOn(2027, 2, 1)), PetitionReminders.plan(petition("2027-02-02"), now = tenOn(2027, 1, 30)))
        assertEquals(emptyList(), PetitionReminders.plan(petition("2027-02-02"), now = tenOn(2027, 2, 1)))
        assertEquals(emptyList(), PetitionReminders.plan(petition(null), now = tenOn(2026, 11, 11)))
    }
}
