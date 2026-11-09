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
import org.politisationpopulairefrancaise.core.decider.ElectionReminderKind
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DE-28, step 3: the reminders of the example election, asked on its page, kept on the device, each one removable. */
@OptIn(ExperimentalTestApi::class)
class ElectionRemindersViewsTest {
    // The fictional petitions, election and letter of the tests (the application bundles real ones).
    private val database = JvmDatabase.inMemory().also { DeciderTestContents.seed(LocalEventStore(it)) }
    private val now = CivilDate(2026, 11, 7).toEpochDays() * 86_400 + 43_200
    private val store = ReminderStore(database) { now }

    private fun has(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

    private fun ComposeUiTest.waitFor(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    @Test
    fun theRemindersAreAskedOnThePageOfTheElectionChosenByKindAndRemovedOneByOne() = runComposeUiTest {
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
        waitFor(DeciderTags.card("election-exemple"))
        onNodeWithTag(DeciderTags.card("election-exemple")).performScrollTo().performClick()

        // Nothing is programmed until the user asks (principle 10).
        onNodeWithTag(ElectionReminderTags.SECTION).performScrollTo().assert(has("PPF ne sait pas que vous les avez demandés"))
        assertNull(store.plan(OWNER))
        onNodeWithTag(ElectionReminderTags.ASK).performScrollTo().performClick()
        waitFor(ElectionReminderTags.reminder("veille-2"))

        assertEquals(setOf("inscription", "procuration", "veille"), store.plan(OWNER)?.kinds)
        assertEquals(
            listOf("inscription-en-ligne", "inscription-mairie", "procuration-1", "veille-1", "procuration-2", "veille-2"),
            store.upcomingOf(OWNER).map { it.id.substringAfterLast('/') },
        )
        // Round 1 on Sunday 7 March 2027: the eve's reminder on 6 March at 10 h, written in French on the device.
        onNodeWithTag(ElectionReminderTags.reminder("veille-1")).performScrollTo().assert(has("Veille du scrutin")).assert(has("6 mars 2027 à 10 h"))
        val eve = store.upcomingOf(OWNER).first { it.id.endsWith("/veille-1") }
        assertEquals(NotificationCategory.VOTES, eve.category)
        assertEquals("ppf:decider/election/election-exemple", eve.target)
        assertTrue("[EXEMPLE]" in eve.detail.orEmpty() && "20 h" in eve.detail.orEmpty(), eve.detail)

        // The eve of each round, switched off: its reminders go.
        onNodeWithTag(ElectionReminderTags.kind(ElectionReminderKind.EVE)).performScrollTo().performClick()
        waitFor(ElectionReminderTags.reminder("veille-1"), present = false)
        assertEquals(setOf("inscription", "procuration"), store.plan(OWNER)?.kinds)

        // One reminder removed: noted in the plan, so a new planning never brings it back (ReminderStoreTest).
        onNodeWithTag(ElectionReminderTags.remove("procuration-1")).performScrollTo().performClick()
        waitFor(ElectionReminderTags.reminder("procuration-1"), present = false)
        assertEquals(setOf("$OWNER/procuration-1"), store.plan(OWNER)?.removed)
        assertEquals(listOf("inscription-en-ligne", "inscription-mairie", "procuration-2"), store.upcomingOf(OWNER).map { it.id.substringAfterLast('/') })

        // « Ne plus me rappeler cette élection »: everything is forgotten, the offer comes back.
        onNodeWithTag(ElectionReminderTags.STOP).performScrollTo().performClick()
        waitFor(ElectionReminderTags.ASK)
        assertNull(store.plan(OWNER))
        assertTrue(store.upcoming().isEmpty())
    }

    private companion object {
        const val OWNER = "decider:election:election-exemple"
    }
}
