package org.politisationpopulairefrancaise.ui.agir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsTags
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Records the links the screens open instead of opening them. */
class RecordingUriHandler : UriHandler {
    val opened = mutableListOf<String>()

    override fun openUri(uri: String) {
        opened += uri
    }
}

/** "Agir" alone in a window of the given size, with what the tests look at. */
class AgirHarness(val dependencies: AppDependencies = AgirFixtures.dependencies()) {
    val uris = RecordingUriHandler()
    val navigations = mutableListOf<Space>()
    lateinit var library: ContentLibrary
    lateinit var organisations: OrganisationsLibrary

    @Composable
    fun Content(
        width: Int,
        height: Int,
        dark: Boolean = false,
        agenda: AgirSlot? = null,
        calls: AgirSlot? = null,
        organisationAgenda: (@Composable (String, Modifier) -> Unit)? = null,
    ) {
        Box(Modifier.requiredSize(width.dp, height.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalUriHandler provides uris) {
                    library = rememberContentLibrary(dependencies)
                    organisations = rememberOrganisationsLibrary(dependencies)
                    val session = rememberAccountSession(dependencies.identityStore)
                    AgirScreen(
                        dependencies = dependencies,
                        library = library,
                        session = session,
                        windowClass = WindowClass.of(width.toFloat(), height.toFloat()),
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = { navigations += it },
                        organisations = organisations,
                        agenda = agenda,
                        calls = calls,
                        organisationAgenda = organisationAgenda,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitTag(tag: String, unmerged: Boolean = false) =
    waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty() }

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.count(tag: String, unmerged: Boolean = false): Int = onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().size

/** Waits until the directory holds the card (it may lie below the visible part of the list), then shows it. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitCard(slug: String) {
    waitUntil(timeoutMillis = 10_000) {
        runCatching { onNodeWithTag(OrganisationsTags.LIST).performScrollToNode(hasTestTag(OrganisationsTags.CARD + slug)) }.isSuccess
    }
    waitForIdle()
}

/** Shows an item of the directory's list (the list is lazy: only what is near the screen exists). */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.scrollListTo(tag: String) {
    onNodeWithTag(OrganisationsTags.LIST).performScrollToNode(hasTestTag(tag))
    waitForIdle()
}
