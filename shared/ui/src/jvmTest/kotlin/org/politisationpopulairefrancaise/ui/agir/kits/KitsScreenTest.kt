package org.politisationpopulairefrancaise.ui.agir.kits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.kits.KitGoal
import org.politisationpopulairefrancaise.core.agir.proches.InMemoryProchesStorage
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.AgirScreen
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.RecordingUriHandler
import org.politisationpopulairefrancaise.ui.agir.TEST_WINDOW_HEIGHT
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.agir.proches.LocalProchesStorage
import org.politisationpopulairefrancaise.ui.agir.proches.rememberProchesState
import org.politisationpopulairefrancaise.ui.agir.strike.StrikeTags
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Agir" as the shell gives it, with the guides bundled with the application. */
private class KitsHarness {
    val dependencies: AppDependencies =
        AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory(), editorialTrust = AgirFixtures.trust)
    lateinit var library: ContentLibrary

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

/**
 * « Kits d'action citoyenne » (D-232): opened from the home of "Agir", filtered by goal and by use without network,
 * a guide read inside "Agir" and a tool opened in its rubric, on phones, tablets and computers.
 */
@OptIn(ExperimentalTestApi::class)
class KitsScreenTest {

    @Test
    fun theKitsAreFilteredByGoalAndWithoutNetworkAndOpenTheirGuideOrTheirTool() {
        for (width in listOf(360, 700, 1000)) {
            val harness = KitsHarness()
            runComposeUiTest {
                setContent { harness.Content(width, dark = width == 700) }
                awaitTag(AgirTags.HOME)
                waitUntil(timeoutMillis = 60_000) { harness.library.guides.any { it.slug == "faire-greve-vos-droits" } }
                click(AgirTags.RUBRIC + AgirRubrics.KITS.id)
                awaitTag(KitsTags.SCREEN)

                // « Faire grève »: the simulator, the funds, the guide of the striker's rights, collecting money.
                click(KitsTags.GOAL + KitGoal.GREVE.id)
                onNodeWithTag(KitsTags.COUNT).assertTextContains("4 kits")
                listOf("simulateur-greve", "solidarite", "faire-greve-vos-droits", "collecter-de-l-argent-pour-une-cause")
                    .forEach { id -> onNodeWithTag(KitsTags.KIT + id).performScrollTo() }
                assertEquals(0, count(KitsTags.KIT + "creer-une-association"))

                // « M'organiser » without network: the collectifs need the server, the guides do not.
                click(KitsTags.GOAL + KitGoal.ORGANISER.id)
                click(KitsTags.OFFLINE)
                assertEquals(0, count(KitsTags.KIT + "collectifs"))
                onNodeWithTag(KitsTags.KIT + "creer-une-association").performScrollTo()

                // A guide opens inside "Agir", with a way back to the kits.
                click(KitsTags.KIT + "creer-une-association")
                awaitTag(ManifestationTags.GUIDE)
                click(ManifestationTags.GUIDE_BACK)
                awaitTag(KitsTags.CLEAR)

                // A tool opens its rubric.
                click(KitsTags.CLEAR)
                click(KitsTags.GOAL + KitGoal.GREVE.id)
                click(KitsTags.KIT + "simulateur-greve")
                awaitTag(StrikeTags.SCREEN)
            }
        }
    }
}
