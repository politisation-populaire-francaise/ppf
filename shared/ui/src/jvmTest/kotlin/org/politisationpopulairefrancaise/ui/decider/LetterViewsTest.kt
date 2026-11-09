package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
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
import org.politisationpopulairefrancaise.core.decider.LetterRecipient
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import kotlin.test.Test

/** DE-09: the example model letter ([EXEMPLE]), without a commune chosen on the device, on 7 November 2026. */
@OptIn(ExperimentalTestApi::class)
class LetterViewsTest {
    private val database = JvmDatabase.inMemory()
    private val now = CivilDate(2026, 11, 7).toEpochDays() * 86_400 + 43_200

    private fun has(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

    @Test
    fun theLetterIsFilledOnTheDeviceCopiedAndSentByThePersonThroughTheOfficialContacts() = runComposeUiTest {
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
                DeciderScreen(library, state, WindowClass.of(420f, 900f), now = { now }, dependencies = dependencies)
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(DeciderTags.card(SLUG)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(DeciderTags.card(SLUG)).performScrollTo().assert(has("Pour : Député, Sénateur")).performClick()

        onNodeWithTag(LetterTags.page(SLUG)).assertExists()
        // No commune chosen in « Mes élus »: the name stays to write, and the way to choose the commune is given.
        onNodeWithTag(LetterTags.NO_COMMUNE).performScrollTo().assertExists()
        onNodeWithTag(LetterTags.TEXT).performScrollTo().assert(has("[Nom de l’élu]")).assert(has("le 7 novembre 2026"))
        onNodeWithTag(LetterTags.COPY).performScrollTo().performClick()
        onNodeWithTag(LetterTags.COPIED).assertExists()
        onNodeWithTag(LetterTags.contact(LetterRecipient.DEPUTE)).performScrollTo().assertExists()
        onNodeWithTag(LetterTags.contact(LetterRecipient.SENATEUR)).performScrollTo().assertExists()
        onNodeWithTag(LetterTags.PRIVACY).performScrollTo().assert(has("PPF n’envoie rien et ne garde rien"))
    }

    private companion object {
        const val SLUG = "courrier-avant-un-vote-exemple"
    }
}
