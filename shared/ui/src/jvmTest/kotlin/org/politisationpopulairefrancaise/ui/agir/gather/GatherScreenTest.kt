package org.politisationpopulairefrancaise.ui.agir.gather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.proches.InMemoryProchesStorage
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirRoute
import org.politisationpopulairefrancaise.ui.agir.AgirScreen
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.RecordingUriHandler
import org.politisationpopulairefrancaise.ui.agir.TEST_WINDOW_HEIGHT
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsTags
import org.politisationpopulairefrancaise.ui.agir.proches.LocalProchesStorage
import org.politisationpopulairefrancaise.ui.agir.proches.rememberProchesState
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertNull

/** "Agir" as the shell gives it, with the route request of the tutorial's « m'allier » (D-198). */
private class GatherHarness {
    val dependencies: AppDependencies =
        AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory(), editorialTrust = AgirFixtures.trust)
    lateinit var library: ContentLibrary
    var request by mutableStateOf<AgirRoute?>(null)

    @Composable
    fun Content(width: Int, dark: Boolean = false) {
        Box(Modifier.requiredSize(width.dp, TEST_WINDOW_HEIGHT.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalUriHandler provides RecordingUriHandler(),
                    LocalProchesStorage provides InMemoryProchesStorage(null),
                ) {
                    library = rememberContentLibrary(dependencies)
                    val session = rememberAccountSession(dependencies.identityStore)
                    AgirScreen(
                        dependencies = dependencies,
                        library = library,
                        session = session,
                        windowClass = WindowClass.of(width.toFloat(), TEST_WINDOW_HEIGHT.toFloat()),
                        modifier = Modifier.fillMaxSize(),
                        proches = rememberProchesState(dependencies, session),
                        requestedRoute = request,
                        onRouteRequestHandled = { request = null },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.click(tag: String) {
    onNodeWithTag(tag).performScrollTo().performClick()
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
class GatherScreenTest {

    @Test
    fun theTutorialOpensSeReunirWhoseDestinationsLeadBackToItOnPhonesTabletsAndComputers() {
        for (width in listOf(360, 700, 1000)) {
            val harness = GatherHarness()
            runComposeUiTest {
                setContent { harness.Content(width, dark = width == 700) }
                awaitTag(AgirTags.HOME)
                waitUntil(timeoutMillis = 60_000) { harness.library.guides.any { it.slug == GUIDE_CREATE_ASSOCIATION } }
                // « m'allier » of the tutorial: the shell asks for the route, « Agir » applies it once and forgets it.
                harness.request = AgirRoute.GATHER
                awaitTag(GatherTags.SCREEN)
                waitUntil { harness.request == null }
                assertNull(harness.request)

                click(GatherTags.COLLECTIVES)
                awaitTag(AgirTags.COLLECTIVES)
                click(AgirTags.BACK)
                awaitTag(GatherTags.SCREEN)

                click(GatherTags.ORGANISATIONS)
                awaitTag(OrganisationsTags.SCREEN)
                click(OrganisationsTags.BACK)
                awaitTag(GatherTags.SCREEN)

                // The bundled guide « Créer une association » is read inside « Agir ».
                click(GatherTags.ASSOCIATION)
                awaitTag(ManifestationTags.GUIDE)
                click(ManifestationTags.GUIDE_BACK)
                awaitTag(GatherTags.ROOMS)

                click(GatherTags.BACK)
                awaitTag(AgirTags.HOME)
            }
        }
    }
}
