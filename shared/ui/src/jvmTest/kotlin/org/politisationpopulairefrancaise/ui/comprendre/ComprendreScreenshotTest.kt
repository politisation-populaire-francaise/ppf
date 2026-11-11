package org.politisationpopulairefrancaise.ui.comprendre

import org.politisationpopulairefrancaise.ui.shell.openSpace
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.LearningProgressStore
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.ComprendreTags
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for "Comprendre", like ScreenshotTest. With PPF_SCREENSHOTS_DIR set, it
 * writes the home of the space, the introduction of a course, a step with its quiz answered and the
 * end page, on a phone (light and dark) and on a computer. One course is shown half done.
 */
@OptIn(ExperimentalTestApi::class)
class ComprendreScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    private val loi: Parcours = Json { ignoreUnknownKeys = true }
        .decodeFromString(ListSerializer(SignedEvent.serializer()), File("src/commonMain/composeResources/files/contents/parcours.json").readText())
        .mapNotNull(Article::from)
        .mapNotNull(Parcours::from)
        .first { it.article.slug == "comment-une-loi-est-votee" }

    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun capture(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val database = JvmDatabase.inMemory()
        // Another course half done, so that the home shows the three states of a card.
        val other = "30023:23484985405d16929a93f9aea29135678ad0ced909bf5a264f7b923d619b012f:la-dissolution"
        LearningProgressStore(database).apply {
            answer(other, 0, choice = 1, correct = true, now = 1_794_000_000)
            reachStep(other, 2, now = 1_794_000_100)
        }
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), database)
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                PpfTheme(darkTheme = dark) { AdaptiveShell(dependencies) }
            }
        }
        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
        }
        openSpace(Space.COMPRENDRE)
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ComprendreTags.COURSE_CARD + loi.article.slug).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        save("$prefix-comprendre-accueil")

        onNodeWithTag(ComprendreTags.COURSE_CARD + loi.article.slug).performScrollTo().performClick()
        waitForIdle()
        save("$prefix-parcours-introduction")

        onNodeWithTag(CourseTags.START).performScrollTo().performClick()
        waitForIdle()
        val quiz = loi.steps[0].quiz!!
        onNodeWithTag(CourseTags.option(quiz.options.indices.first { it != quiz.correctIndex })).performScrollTo().performClick()
        onNodeWithTag(CourseTags.VALIDATE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.FEEDBACK).performScrollTo()
        waitForIdle()
        save("$prefix-parcours-question")

        repeat(loi.steps.size) {
            onNodeWithTag(CourseTags.NEXT).performScrollTo().performClick()
            waitForIdle()
        }
        save("$prefix-parcours-fin")
    }

    @Test
    fun phoneLight() = capture(400, 800, dark = false, prefix = "telephone-clair")

    @Test
    fun phoneDark() = capture(400, 800, dark = true, prefix = "telephone-sombre")

    @Test
    fun computer() = capture(1000, 740, dark = false, prefix = "ordinateur")

    /** A fact-check, a story and a « Qui décide de ça ? » sheet, each opened from the home. */
    private fun captureContents(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory())
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                PpfTheme(darkTheme = dark) { AdaptiveShell(dependencies) }
            }
        }
        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
        }
        openSpace(Space.COMPRENDRE)
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ComprendreTags.CHECK_LIST).fetchSemanticsNodes().isNotEmpty() }
        listOf(
            "vote-blanc-suffrages-exprimes" to "decryptage",
            "front-populaire-1936" to "histoire",
            "manifester-qui-autorise" to "qui-decide",
        ).forEach { (slug, name) ->
            onNodeWithTag(ComprendreTags.CARD + slug).performScrollTo().performClick()
            waitForIdle()
            save("$prefix-$name")
            if (width < 840) {
                onNodeWithTag(ComprendreDetailTags.BACK).performClick()
                waitForIdle()
            }
        }
    }

    @Test
    fun contentsOnAPhone() = captureContents(400, 800, dark = false, prefix = "telephone-clair")

    @Test
    fun contentsOnAPhoneInTheDark() = captureContents(400, 800, dark = true, prefix = "telephone-sombre")

    @Test
    fun contentsOnAComputer() = captureContents(1000, 740, dark = false, prefix = "ordinateur")
}
