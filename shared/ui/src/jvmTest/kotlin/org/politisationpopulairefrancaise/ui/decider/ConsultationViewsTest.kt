package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DE-27: the official consultations of the bundled examples ([EXEMPLE]), on 7 November 2026. */
@OptIn(ExperimentalTestApi::class)
class ConsultationViewsTest {
    private val database = JvmDatabase.inMemory()
    private val now = CivilDate(2026, 11, 7).toEpochDays() * 86_400 + 43_200
    private val store = ReminderStore(database) { now }

    private fun has(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

    private fun ComposeUiTest.waitFor(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.show() {
        val dependencies = AppDependencies(
            identityStore = InMemoryIdentityStore(),
            contentStore = LocalEventStore(database),
            editorialTrust = EditorialTrust.bundledExamples(),
            contentFollowStore = ContentFollowStore(database),
        )
        setContent {
            Box(Modifier.requiredSize(420.dp, 900.dp)) {
                val library = rememberContentLibrary(dependencies)
                val state = rememberDeciderState(dependencies)
                val scope = rememberCoroutineScope()
                val reminders = remember { DeciderRemindersState(store, settings = null, scope = scope, clock = { now }) }
                DeciderScreen(library, state, WindowClass.of(420f, 900f), now = { now }, dependencies = dependencies, reminders = reminders)
            }
        }
        waitFor(DeciderTags.card(OPEN))
    }

    @Test
    fun theHomeListsTheOpenConsultationFirstWithTheTimeLeftThenTheOneToCome() = runComposeUiTest {
        show()
        onNodeWithTag(DeciderTags.section("consultations")).performScrollTo()
        onNodeWithTag(DeciderTags.card(OPEN)).performScrollTo().assert(has("Encore 23 jours pour répondre")).assert(has("Organisée par Ministère d’exemple"))
        onNodeWithTag(DeciderTags.card(INQUIRY)).performScrollTo().assert(has("À venir")).assert(has("Ouvre le 1er décembre 2026"))
    }

    @Test
    fun thePageSendsToTheOfficialSiteSaysWhatTheLawSaysAndProgramsTheClosingReminder() = runComposeUiTest {
        show()
        onNodeWithTag(DeciderTags.card(OPEN)).performScrollTo().performClick()
        onNodeWithTag(ConsultationTags.page(OPEN)).assertExists()
        onNodeWithTag(ConsultationTags.DATES).assert(has("Du 2 novembre 2026 au 30 novembre 2026 inclus"))
        onNodeWithTag(ConsultationTags.BASIS).performScrollTo().assert(has("L132-1"))
        onNodeWithTag(ConsultationTags.PRIVACY).assert(has("PPF ne recueille aucune réponse"))
        // The official page opens only after « Vous quittez PPF ».
        onNodeWithTag(ConsultationTags.ANSWER).performScrollTo().assert(has("exemple.invalid")).performClick()
        onNodeWithTag(VoirTags.LINK_DIALOG).assertExists()
    }

    @Test
    fun theRemindersOfAConsultationToComeAreItsOpeningAndThreeDaysBeforeItsLastDay() = runComposeUiTest {
        show()
        onNodeWithTag(DeciderTags.card(INQUIRY)).performScrollTo().performClick()
        assertNull(store.plan(OWNER), "nothing until the user asks")
        onNodeWithTag(ElectionReminderTags.ASK).performScrollTo().performClick()
        waitFor(ElectionReminderTags.reminder("cloture"))
        assertEquals(listOf("ouverture", "cloture"), store.upcomingOf(OWNER).map { it.id.substringAfterLast('/') })
        onNodeWithTag(ElectionReminderTags.reminder("ouverture")).performScrollTo().assert(has("Consultation ouverte")).assert(has("1er décembre 2026 à 10 h"))
        onNodeWithTag(ElectionReminderTags.reminder("cloture")).performScrollTo().assert(has("Consultation bientôt close")).assert(has("5 janvier 2027 à 10 h"))
        assertEquals("ppf:decider/consultation/$INQUIRY", store.upcomingOf(OWNER).first().target)

        onNodeWithTag(ElectionReminderTags.STOP).performScrollTo().assert(has("cette consultation")).performClick()
        waitFor(ElectionReminderTags.ASK)
        assertNull(store.plan(OWNER))
    }


    private companion object {
        const val OPEN = "consultation-ouverte-exemple"
        const val INQUIRY = "enquete-publique-exemple"
        const val OWNER = "decider:consultation:$INQUIRY"
    }
}
