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

/** DE-28, step 3: the reminders of an election follow its current dates, with the same delays for everyone. */
class ElectionRemindersTest {
    private val keys = IdentityKeys.generate()

    private fun election(vararg tags: List<String>): ElectionCalendar {
        val all = listOf(listOf(DeciderTags.ELECTION, "presidentielle-2027", "[EXEMPLE] Élection présidentielle", ""), listOf(DeciderTags.ELECTION_STATUS, "annoncee")) + tags.toList()
        val unsigned = Article.build(keys.publicKey, "election", "[EXEMPLE] Élection", null, emptyList(), ArticleType.ELECTION, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = all)
        val article = Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
        return assertNotNull(ElectionCalendar.from(article))
    }

    private val round1 = listOf(DeciderTags.ROUND, "1", "2027-04-18", "2027-04-17", "20:00")
    private val round2 = listOf(DeciderTags.ROUND, "2", "2027-05-02", "", "20:00")
    private fun deadline(kind: String, date: String) = listOf(DeciderTags.DEADLINE, kind, date, "https://exemple.invalid/source")

    private val newYear = ParisTime.toEpochSeconds(CivilDate(2027, 1, 1), 0, 0)

    @Test
    fun everyReminderComesAtTenParisTimeAWeekBeforeRegistrationTenDaysBeforeEachRoundAndOnItsEve() {
        val calendar = election(round2, round1, deadline("inscription-mairie", "2027-03-12"), deadline("inscription-en-ligne", "2027-03-10"), deadline("candidatures", "2027-03-01"))
        val plan = ElectionReminders.plan(calendar, ElectionReminders.DEFAULT_KINDS, newYear)
        assertEquals(
            listOf("inscription-en-ligne", "inscription-mairie", "procuration-1", "veille-1", "procuration-2", "veille-2"),
            plan.map { it.key },
            "the nearest first; no reminder for the candidacies",
        )
        val moments = plan.map { ParisTime.moment(it.dueAt) }
        assertEquals(
            listOf(CivilDate(2027, 3, 3), CivilDate(2027, 3, 5), CivilDate(2027, 4, 8), CivilDate(2027, 4, 17), CivilDate(2027, 4, 22), CivilDate(2027, 5, 1)),
            moments.map { it.date },
        )
        assertEquals(setOf(10 to 0), moments.map { it.hour to it.minute }.toSet(), "in winter time as in summer time")
        // Each reminder says what it is about: the deadline, or the round and its day.
        assertEquals(DeadlineKind.REGISTRATION_ONLINE, plan[0].deadline)
        assertEquals(CivilDate(2027, 3, 10), plan[0].date)
        assertEquals(2 to CivilDate(2027, 5, 2), plan.last().round to plan.last().date)
        assertEquals("decider:election:presidentielle-2027", ElectionReminders.owner(calendar))
        assertEquals("decider:election:presidentielle-2027/veille-1", ElectionReminders.reminderId(calendar, "veille-1"))
    }

    @Test
    fun onlyTheKindsChosenAndOnlyTheRemindersStillToCome() {
        val calendar = election(round1, round2, deadline("inscription-mairie", "2027-03-12"))
        assertEquals(listOf("veille-1", "veille-2"), ElectionReminders.plan(calendar, setOf(ElectionReminderKind.EVE), newYear).map { it.key })
        assertEquals(emptyList(), ElectionReminders.plan(calendar, emptySet(), newYear))
        // Asked on 9 April: the registration and the first proxy reminders are past, they are not programmed.
        val april9 = ParisTime.toEpochSeconds(CivilDate(2027, 4, 9), 12, 0)
        assertEquals(listOf("veille-1", "procuration-2", "veille-2"), ElectionReminders.plan(calendar, ElectionReminders.DEFAULT_KINDS, april9).map { it.key })
    }

    @Test
    fun theKeysOfTheKindsNeverChange() {
        // Stored in the plans of the devices: renaming one would forget the choices of the users.
        assertEquals(listOf("inscription", "procuration", "veille"), ElectionReminderKind.entries.map { it.key })
        assertEquals(ElectionReminderKind.PROXY, ElectionReminderKind.fromKey("procuration"))
    }
}
