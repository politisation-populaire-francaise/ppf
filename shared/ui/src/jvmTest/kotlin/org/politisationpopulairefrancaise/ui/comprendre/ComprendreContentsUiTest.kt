package org.politisationpopulairefrancaise.ui.comprendre

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.comprendre.ContentBlocks
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import org.politisationpopulairefrancaise.core.comprendre.Decryptage
import org.politisationpopulairefrancaise.core.comprendre.HistoryStory
import org.politisationpopulairefrancaise.core.comprendre.QuiDecide
import org.politisationpopulairefrancaise.core.comprendre.Verdict
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.content.ComprendreTags
import org.politisationpopulairefrancaise.ui.content.ContentTags
import org.politisationpopulairefrancaise.ui.content.MarkdownTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The fact-checks, the stories of the history of social movements and the « Qui décide de ça ? »
 * sheets bundled with the application (comprendre.json): listed on the home of "Comprendre", each
 * opened with its own layout (verdict, chronology, box of the elected officials concerned), with
 * objections and their answers (pluralism).
 */
@OptIn(ExperimentalTestApi::class)
class ComprendreContentsUiTest {

    private val articles: List<Article> = Json { ignoreUnknownKeys = true }
        .decodeFromString(ListSerializer(SignedEvent.serializer()), File("src/commonMain/composeResources/files/contents/comprendre.json").readText())
        .mapNotNull(Article::from)

    private val checks = articles.mapNotNull(Decryptage::from)
    private val stories = articles.mapNotNull(HistoryStory::from)
    private val sheets = articles.mapNotNull(QuiDecide::from)

    private fun ComposeUiTest.openComprendre(width: Int, height: Int) {
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory())
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) } }
        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
        }
        onNodeWithText("Comprendre").performClick()
        waitForIdle()
        if (width < 600) {
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ComprendreTags.CHECK_LIST).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.open(slug: String) {
        onNodeWithTag(ComprendreTags.CARD + slug).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun theBundledContentsAreCompleteAndPluralist() {
        assertTrue(checks.size >= 4, "fact-checks: ${checks.size}")
        assertTrue(checks.map { it.verdict }.toSet().size >= 2, "several verdicts are shown")
        assertTrue(stories.size >= 3, "stories: ${stories.size}")
        assertTrue(sheets.size >= 4, "sheets: ${sheets.size}")
        (checks.map { it.article } + stories.map { it.article }).forEach { article ->
            assertTrue(ContentBlocks.hasObjections(article.body), "${article.slug} presents objections and answers")
        }
        (checks.map { it.article } + stories.map { it.article } + sheets.map { it.article }).forEach { article ->
            assertTrue(article.title.startsWith("[EXEMPLE]"), article.slug)
            assertEquals(emptyList(), ContentBlocks.problems(article.body), article.slug)
        }
        assertNotNull(articles.firstOrNull { it.slug == METHOD_SLUG }, "the method page explains the verdicts")
    }

    @Test
    fun theHomeListsEverySection() = runComposeUiTest {
        openComprendre(width = 400, height = 800)
        onNodeWithTag(ComprendreTags.WHO_LIST).performScrollTo().assertExistsOnce()
        onNodeWithTag(ComprendreTags.CHECK_LIST).performScrollTo()
        onNodeWithTag(ComprendreTags.HISTORY_LIST).performScrollTo()
        onNodeWithTag(ContentTags.GUIDE_LIST).performScrollTo()
    }

    @Test
    fun aFactCheckShowsTheClaimItsVerdictAndTheMethod() = runComposeUiTest {
        openComprendre(width = 400, height = 800)
        val check = checks.first { it.verdict == Verdict.CONTRADICTED }
        open(check.article.slug)
        onNodeWithTag(ComprendreDetailTags.DECRYPTAGE).assertExistsOnce()
        onNodeWithTag(ComprendreDetailTags.CLAIM).assert(hasAnyDescendant(hasText("« ${check.claim} »")))
        onNodeWithTag(ComprendreDetailTags.VERDICT).assert(hasAnyDescendant(hasText("Verdict : Contredit par les faits")))
        onNodeWithTag(MarkdownTags.OBJECTIONS).performScrollTo()

        // The method page explains how the verdict was chosen.
        onNodeWithTag(ComprendreDetailTags.METHOD).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ContentTags.GUIDE_DETAIL).assertExistsOnce()
        onNodeWithText("Les cinq verdicts", substring = true).performScrollTo()
    }

    @Test
    fun aStoryShowsWhatWasAskedWhatWasWonAndItsChronology() = runComposeUiTest {
        openComprendre(width = 400, height = 800)
        val story = stories.first()
        open(story.article.slug)
        onNodeWithTag(ComprendreDetailTags.HISTORY).assertExistsOnce()
        onNodeWithText("${story.period} · ${story.country}").assertExistsOnce()
        onNodeWithText("Résultats obtenus").performScrollTo()
        onNodeWithTag(MarkdownTags.TIMELINE).performScrollTo()
        onNodeWithTag(MarkdownTags.OBJECTIONS).performScrollTo()
    }

    @Test
    fun aWhoDecidesSheetStartsWithTheElectedOfficialsConcerned() = runComposeUiTest {
        openComprendre(width = 400, height = 800)
        val sheet = sheets.first { it.persons.isNotEmpty() }
        open(sheet.article.slug)
        onNodeWithTag(ComprendreDetailTags.WHO).assertExistsOnce()
        onNodeWithTag(ComprendreDetailTags.ELUS).assertExistsOnce()
        sheet.steps.forEach { step -> onNodeWithTag(ComprendreDetailTags.level(step.level)).performScrollTo() }
        // The person linked by the editorial team is named under the level of the State.
        onNodeWithTag(ComprendreDetailTags.level(DecisionLevel.ETAT)).assert(hasAnyDescendant(hasText("Dominique Fictif")))
        if (sheet.steps.any { it.level != DecisionLevel.ETAT && it.level != DecisionLevel.UNION_EUROPEENNE }) {
            onNodeWithText("annuaire officiel des élus", substring = true).performScrollTo()
        }
        // « Et maintenant ? » leads to the demands wall.
        onNodeWithTag(CourseTags.NEXT_ACTIONS).performScrollTo()
        onNodeWithTag(CourseTags.action("revendications")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ComprendreDetailTags.WHO).assertDoesNotExist()
    }

    @Test
    fun aWideWindowKeepsTheHomeBesideTheContent() = runComposeUiTest {
        openComprendre(width = 1000, height = 740)
        open(sheets.first().article.slug)
        onNodeWithTag(ComprendreDetailTags.WHO).assertExistsOnce()
        onNodeWithTag(ComprendreTags.HOME).assertExistsOnce()
        onNodeWithTag(ComprendreDetailTags.BACK).assertDoesNotExist()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsOnce() {
    assertExists()
}
