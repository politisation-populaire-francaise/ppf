package org.politisationpopulairefrancaise.ui.agir.engagement

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.engagement.Engagement
import org.politisationpopulairefrancaise.core.agir.engagement.EngagementStep
import org.politisationpopulairefrancaise.core.agir.proches.InMemoryProchesStorage
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.time.CivilDate
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
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Agir" alone, with a recorded way out to the other spaces and to the web, and optionally an [EXEMPLE] guide. */
private class EngagementHarness(withPartyGuide: Boolean) {
    val dependencies: AppDependencies =
        AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory(), editorialTrust = AgirFixtures.trust)
    val uris = RecordingUriHandler()
    val navigations = CopyOnWriteArrayList<Space>()
    lateinit var library: ContentLibrary

    init {
        if (withPartyGuide) {
            val event = runBlocking {
                AgirFixtures.editor.sign(
                    Article.build(
                        author = AgirFixtures.editor.publicKey,
                        slug = Engagement.GUIDE_PARTY,
                        title = "[EXEMPLE] Créer ou rejoindre un parti",
                        summary = null,
                        topics = listOf("élections"),
                        type = ArticleType.FICHE_PRATIQUE,
                        origin = ContentOrigin.AI_ASSISTED,
                        publishedAt = 1_794_355_200,
                        updatedAt = 1_794_355_200,
                        body = "[EXEMPLE] Un texte de fiche.",
                    ),
                )
            }
            dependencies.contentStore!!.store(event)
        }
    }

    @Composable
    fun Content(width: Int, dark: Boolean = false) {
        Box(Modifier.requiredSize(width.dp, TEST_WINDOW_HEIGHT.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalUriHandler provides uris,
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
                        onNavigate = { navigations += it },
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

@OptIn(ExperimentalTestApi::class)
class EngagementScreenTest {

    @Test
    fun theHomePutsTheRubricForwardAndItsStepsFollowTheCalendarOnPhonesTabletsAndComputers() {
        for (width in listOf(360, 700, 1000)) {
            val harness = EngagementHarness(withPartyGuide = true)
            runComposeUiTest {
                setContent { harness.Content(width, dark = width == 700) }
                awaitTag(AgirTags.HOME)
                onNodeWithTag(EngagementFeaturedTags.CARD).performScrollTo()
                waitUntil(timeoutMillis = 60_000) { harness.library.guides.any { it.slug == Engagement.GUIDE_PARTY } }
                click(EngagementFeaturedTags.OPEN)
                awaitTag(EngagementTags.SCREEN)
                // Register first, then vote, then stand, then a party; the neutrality is said.
                EngagementStep.entries.forEach { step -> onNodeWithTag(EngagementTags.STEP + step.id).performScrollTo() }
                onNodeWithText("au plus tard le vendredi 12 mars 2027", substring = true).assertExists()
                onNodeWithTag(EngagementTags.NEUTRAL).performScrollTo()
                onNodeWithText("une association ne peut aider la campagne", substring = true).assertExists()

                // The official procedures open on their sites, the person's own gesture.
                click(EngagementTags.CHECK_REGISTRATION)
                click(EngagementTags.SOURCE + "l52-8")
                assertEquals(listOf(Engagement.CHECK_REGISTRATION, Engagement.L52_8), harness.uris.opened, "at $width dp")

                // A guide on the device opens inside « Agir »; one not yet there is greyed out and says so.
                // The guides of « Comprendre » are bundled; one not yet in the contents is greyed out.
                assertEquals(1, count(EngagementTags.GUIDE + Engagement.GUIDE_CITIZEN_LIST))
                assertEquals(1, count(EngagementTags.GUIDE_SOON + Engagement.GUIDE_LEGISLATIVES))
                click(EngagementTags.GUIDE + Engagement.GUIDE_PARTY)
                awaitTag(ManifestationTags.GUIDE)
                click(ManifestationTags.GUIDE_BACK)
                awaitTag(EngagementTags.STEP + EngagementStep.REGISTER.id)
            }
        }
    }

    @Test
    fun theStepsLeadToDeciderVoirComprendreAndTheCollectifsAndBack() = runComposeUiTest {
        val harness = EngagementHarness(withPartyGuide = false)
        setContent { harness.Content(360) }
        awaitTag(AgirTags.HOME)
        click(AgirTags.RUBRIC + AgirRubrics.ENGAGER.id)
        awaitTag(EngagementTags.SCREEN)
        click(EngagementTags.DECIDER)
        click(EngagementTags.PARTIES)
        click(EngagementTags.COURSE + Engagement.COURSE_REGISTER)
        assertEquals(listOf(Space.DECIDER, Space.VOIR, Space.COMPRENDRE), harness.navigations.toList())
        assertEquals(1, count(EngagementTags.GUIDE_SOON + Engagement.GUIDE_LEGISLATIVES))

        // Running mates: the directory of the collectifs, whose way back returns to the steps.
        click(EngagementTags.COLLECTIVES)
        awaitTag(AgirTags.COLLECTIVES)
        click(AgirTags.BACK)
        awaitTag(EngagementTags.SCREEN)
        click(EngagementTags.BACK)
        awaitTag(AgirTags.HOME)
    }

    @Test
    fun theHighlightCountsTheDaysToTheNextMilestoneAndKeepsOnlyTheInvitationOnceAllArePast() {
        runComposeUiTest {
            setContent { PpfTheme { EngagementFeatured(CivilDate(2026, 11, 11), onOpen = {}) } }
            onNodeWithTag(EngagementFeaturedTags.NEXT, useUnmergedTree = true)
                .assertTextContains("dernier jour pour s’inscrire sur les listes électorales, vendredi 12 mars 2027", substring = true)
                .assertTextContains("Dans 121 jours", substring = true)
        }
        runComposeUiTest {
            setContent { PpfTheme { EngagementFeatured(CivilDate(2027, 5, 3), onOpen = {}) } }
            awaitTag(EngagementFeaturedTags.OPEN)
            assertTrue(count(EngagementFeaturedTags.NEXT, unmerged = true) == 0)
        }
    }
}
