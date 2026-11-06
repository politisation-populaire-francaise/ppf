package org.politisationpopulairefrancaise.ui.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals

/** The practical guides bundled with the application, read offline from the local database. */
@OptIn(ExperimentalTestApi::class)
class ComprendreScreenTest {

    private fun dependencies(trust: EditorialTrust = EditorialTrust.bundledExamples()) = AppDependencies(
        identityStore = InMemoryIdentityStore(),
        contentStore = LocalEventStore(JvmDatabase.inMemory()),
        editorialTrust = trust,
    )

    private fun ComposeUiTest.openComprendre(dependencies: AppDependencies, width: Int, height: Int) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) } }
        if (width < 600) {
            // Phone: the sections are in the menu.
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
        }
        onNodeWithText("Comprendre").performClick()
        waitForIdle()
        if (width < 600) {
            // Let the menu finish closing, otherwise its scrim swallows the next click.
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
        }
    }

    private fun ComposeUiTest.waitForGuides() {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(ContentTags.GUIDE_CARD + "garde-a-vue").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun bundledGuidesAreListedAndReadableOffline() = runComposeUiTest {
        val dependencies = dependencies()
        openComprendre(dependencies, width = 1000, height = 740)
        waitForGuides()
        assertEquals(3, dependencies.contentStore!!.byTag(listOf(30023), "ppf-type", "fiche-pratique").size)
        onNodeWithTag(ContentTags.GUIDE_CARD + "droits-en-manifestation").assertExists()
        onNodeWithTag(ContentTags.GUIDE_CARD + "filmer-les-forces-de-l-ordre").assertExists()
        onNodeWithText("Hors ligne", substring = true).assertExists()
        onNodeWithTag(ContentTags.REFRESH).assertDoesNotExist()

        // Wide window: the guide opens beside the list, no back button.
        onNodeWithTag(ContentTags.GUIDE_CARD + "garde-a-vue").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ContentTags.GUIDE_DETAIL).assertExists()
        onNodeWithTag(ContentTags.GUIDE_LIST).assertExists()
        onNodeWithTag(ContentTags.BACK).assertDoesNotExist()
        onNodeWithText("Durée").assertExists()
        onNodeWithTag(ContentTags.GUIDE_DETAIL).assert(hasAnyDescendant(hasText("Fiche pratique · Mis à jour le 6 novembre 2026 · Rédigé par un humain")))
        onNodeWithTag(ContentTags.GUIDE_DETAIL).assert(hasAnyDescendant(hasText("Garde à vue : vos droits")))
    }

    @Test
    fun phoneShowsOnePaneWithABackButton() = runComposeUiTest {
        openComprendre(dependencies(), width = 360, height = 700)
        waitForGuides()
        onNodeWithTag(ContentTags.GUIDE_CARD + "garde-a-vue").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ContentTags.GUIDE_DETAIL).assertExists()
        onNodeWithTag(ContentTags.GUIDE_LIST).assertDoesNotExist()
        onNodeWithTag(ContentTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(ContentTags.GUIDE_LIST).assertExists()
        onNodeWithTag(ContentTags.GUIDE_DETAIL).assertDoesNotExist()
    }

    @Test
    fun contentsFromUntrustedAuthorsAreNeverShown() = runComposeUiTest {
        val dependencies = dependencies(trust = EditorialTrust(emptySet()))
        openComprendre(dependencies, width = 1000, height = 740)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Aucune fiche disponible pour l’instant.").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, dependencies.contentStore!!.count())
        onNodeWithTag(ContentTags.GUIDE_CARD + "garde-a-vue").assertDoesNotExist()
    }
}
