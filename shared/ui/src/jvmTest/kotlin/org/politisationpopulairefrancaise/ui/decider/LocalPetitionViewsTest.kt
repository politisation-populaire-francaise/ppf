package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
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
import org.politisationpopulairefrancaise.core.elus.ChosenCommune
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import kotlin.test.Test

/** DE-26: the example local petition ([EXEMPLE], commune 69123), shown only for the commune chosen on the device. */
@OptIn(ExperimentalTestApi::class)
class LocalPetitionViewsTest {
    private val database = JvmDatabase.inMemory()
    private val now = CivilDate(2026, 11, 9).toEpochDays() * 86_400 + 43_200

    private fun has(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

    private fun ComposeUiTest.show() {
        val dependencies = AppDependencies(
            identityStore = InMemoryIdentityStore(),
            contentStore = LocalEventStore(database),
            editorialTrust = EditorialTrust.bundledExamples(),
            contentFollowStore = ContentFollowStore(database),
            database = database,
        )
        setContent {
            Box(Modifier.requiredSize(420.dp, 760.dp)) {
                val library = rememberContentLibrary(dependencies)
                val state = rememberDeciderState(dependencies)
                DeciderScreen(library, state, WindowClass.of(420f, 760f), now = { now }, dependencies = dependencies)
            }
        }
    }

    @Test
    fun withTheCommuneChosenTheLocalPetitionShowsItsCountAgainstATenthOfTheVotersAndHowToSign() = runComposeUiTest {
        ElusStore(database).saveCommune(ChosenCommune("69123", "69"))
        show()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(DeciderTags.card(SLUG)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(DeciderTags.card(SLUG)).performScrollTo().assert(has("640 signatures sur")).assert(has("En collecte")).performClick()

        onNodeWithTag(LocalPetitionTags.page(SLUG)).assertExists()
        onNodeWithTag(LocalPetitionTags.COUNT).performScrollTo().assert(has("640 signatures sur"))
        onNodeWithTag(LocalPetitionTags.HOW_TO_SIGN).performScrollTo().assert(has("devant le marché"))
        onNodeWithTag(LocalPetitionTags.LAW).performScrollTo().assert(has("L1112-16")).assert(has("Chaque trimestre"))
        onNodeWithTag(LocalPetitionTags.PRIVACY).performScrollTo().assert(has("PPF ne recueille aucune signature"))
    }

    @Test
    fun withoutACommuneChosenNoLocalPetitionIsShown() = runComposeUiTest {
        show()
        // The other contents of « Décider » are read: the local petition is not among them.
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(DeciderTags.card("petition-an-ordre-public")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(DeciderTags.card(SLUG)).assertDoesNotExist()
        onNodeWithTag(DeciderTags.section("petitions-locales")).assertDoesNotExist()
    }

    private companion object {
        const val SLUG = "petition-locale-exemple"
    }
}
