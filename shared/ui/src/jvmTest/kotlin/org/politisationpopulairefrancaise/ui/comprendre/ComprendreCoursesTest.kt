package org.politisationpopulairefrancaise.ui.comprendre

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.comprendre.LearningProgressStore
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.editorial.ContentCheck
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.content.ComprendreTags
import org.politisationpopulairefrancaise.ui.content.ContentTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The courses "Comprendre en 5 minutes" bundled with the application: listed offline, read step by
 * step, their quizzes answered, the progress kept on the device only and erased on demand.
 */
@OptIn(ExperimentalTestApi::class)
class ComprendreCoursesTest {

    /** The bundled courses, read from the file the application ships (so the test follows content edits). */
    private val bundled: List<Parcours> = Json { ignoreUnknownKeys = true }
        .decodeFromString(ListSerializer(SignedEvent.serializer()), File("src/commonMain/composeResources/files/contents/parcours.json").readText())
        .mapNotNull(Article::from)
        .mapNotNull(Parcours::from)

    private val loi: Parcours = bundled.first { it.article.slug == "comment-une-loi-est-votee" }

    private fun ComposeUiTest.openComprendre(database: PpfDatabase, width: Int, height: Int) {
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), database)
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) } }
        if (width < 600) {
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
        waitUntil(timeoutMillis = 60_000) {
            onAllNodesWithTag(ComprendreTags.COURSE_CARD + loi.article.slug).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theExampleCoursesAreValidAndBundled() {
        assertEquals(
            setOf(
                "comment-une-loi-est-votee", "le-49-3-et-la-motion-de-censure", "la-dissolution", "l-election-presidentielle",
                "le-budget-de-l-etat", "le-role-de-chaque-elu", "le-referendum", "la-revision-de-la-constitution",
                // Third wave (D-049).
                "s-inscrire-et-voter", "le-droit-de-greve", "le-conseil-constitutionnel", "la-separation-des-pouvoirs",
                "l-union-europeenne-qui-decide-quoi", "commune-departement-region", "se-presenter-a-une-election", "les-petitions-officielles",
                // Laicite and fascism (CO-21, CO-22).
                "la-laicite", "comprendre-le-fascisme",
                // One lesson a day for newcomers (CO-19).
                "je-decouvre",
            ),
            bundled.map { it.article.slug }.toSet(),
        )
        bundled.forEach { course ->
            // Real contents written with an AI (decision of the owner, 9 November 2026): no marker, origin shown.
            assertFalse(course.article.title.startsWith("[EXEMPLE]"), course.article.slug)
            assertEquals(ContentOrigin.AI_ASSISTED, course.article.origin, course.article.slug)
            assertTrue(course.steps.isNotEmpty() && course.steps.all { it.quiz != null }, course.article.slug)
            assertNotNull(course.nextActions, course.article.slug)
            assertNotNull(course.sources, course.article.slug)
        }
    }

    @Test
    fun jeDecouvreProposesTheLessonOfTheDayWithoutEverLocking() = runComposeUiTest {
        val discover = bundled.first { it.article.slug == "je-decouvre" }
        assertTrue(discover.daily, "« Je découvre » is read one lesson a day (CO-19)")
        val database = JvmDatabase.inMemory()
        openComprendre(database, width = 400, height = 800)
        // « Nouveau ici ? » on the home, at day 1 for a newcomer.
        // The card is one clickable element: its texts are merged into it.
        onNodeWithTag(ComprendreTags.DISCOVER).performScrollTo().assert(hasText("Jour 1 sur ${discover.steps.size}", substring = true))
        onNodeWithTag(ComprendreTags.DISCOVER).performClick()
        waitForIdle()
        // The card opens the lesson itself, not the introduction.
        onNodeWithTag(CourseTags.STEP_TITLE).assert(hasText(discover.steps[0].title))
        onNodeWithTag(CourseTags.DAILY_NOTE).assertDoesNotExist()
        val quiz = assertNotNull(discover.steps[0].quiz)
        onNodeWithTag(CourseTags.option(quiz.correctIndex)).performScrollTo().performClick()
        onNodeWithTag(CourseTags.VALIDATE).performScrollTo().performClick()
        waitForIdle()
        // Once read: a suggestion to come back tomorrow, and the next lesson stays open.
        onNodeWithTag(CourseTags.DAILY_NOTE).performScrollTo().assert(hasText("Revenez demain pour le jour 2", substring = true))
        onNodeWithTag(CourseTags.NEXT).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.STEP_TITLE).assert(hasText(discover.steps[1].title))
        // Back home, the card points at day 2: the progress stayed on the device.
        onNodeWithTag(CourseTags.BACK).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ComprendreTags.DISCOVER).performScrollTo().assert(hasText("Jour 2 sur ${discover.steps.size}", substring = true))
    }

    @Test
    fun aCourseLeadsToItsTeachingSheetWithTheAnswersFoldedApart() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        openComprendre(database, width = 1000, height = 800)
        onNodeWithTag(ComprendreTags.COURSE_CARD + loi.article.slug).performScrollTo().performClick()
        waitForIdle()
        // « Pour la classe » (CO-13): the sheet of the course, opened from its introduction.
        waitUntil(timeoutMillis = 60_000) { onAllNodesWithTag(CourseTags.TEACHING).fetchSemanticsNodes().isNotEmpty() }
        // The end of the introduction: licence, code of this version and « Citer ce contenu » (D-103, S-141, S-144).
        onNodeWithText("Code de vérification : ${ContentCheck.codeOf(loi.article.event)}", substring = true).performScrollTo().assertExists()
        onNodeWithText("Texte sous licence CC BY 4.0, Politisation Populaire Française.").assertExists()
        onNodeWithTag(ComprendreCiteTags.CITE).performScrollTo().assert(hasText("Citer ce contenu"))
        onNodeWithTag(CourseTags.TEACHING).performScrollTo().assert(hasText("Pour la classe", substring = true)).performClick()
        waitForIdle()
        onNodeWithTag(ComprendreDetailTags.TEACHING).assertExists()
        // The fixed neutrality notice, never the writer's text.
        onNodeWithTag(ComprendreDetailTags.TEACHING_NEUTRALITY).performScrollTo().assert(hasText("neutralité de l’enseignement public", substring = true))
        // The questions of the course for a show of hands; the answers stay folded until asked for.
        onNodeWithTag(ComprendreDetailTags.TEACHING_QUESTIONS).performScrollTo().assertExists()
        onAllNodesWithTag(ComprendreDetailTags.TEACHING_ANSWERS).fetchSemanticsNodes().let { assertTrue(it.isEmpty(), "answers folded") }
        onNodeWithText("Afficher les réponses").performScrollTo().performClick()
        waitForIdle()
        assertEquals(loi.quizCount, onAllNodesWithTag(ComprendreDetailTags.TEACHING_ANSWERS).fetchSemanticsNodes().size)
        // The sheet ends with its licence and its reference too, from the common mechanism of "Voir".
        assertTrue(onAllNodesWithTag(ComprendreCiteTags.LICENSE).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithTag(ComprendreCiteTags.CITE).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aCourseLeadsToItsWorkshopSheetWithItsOwnNoticeAndNoProgramme() = runComposeUiTest {
        val petitions = bundled.first { it.article.slug == "les-petitions-officielles" }
        val database = JvmDatabase.inMemory()
        openComprendre(database, width = 1000, height = 800)
        onNodeWithTag(ComprendreTags.COURSE_CARD + petitions.article.slug).performScrollTo().performClick()
        waitForIdle()
        // « Pour un atelier » (CO-24): the animation sheet of the course, with its duration.
        waitUntil(timeoutMillis = 60_000) { onAllNodesWithTag(CourseTags.WORKSHOP).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithTag(CourseTags.TEACHING).fetchSemanticsNodes().isEmpty(), "no sheet for the classroom on this course")
        onNodeWithTag(CourseTags.WORKSHOP).performScrollTo().assert(hasText("Pour un atelier : fiche d’animation (90 minutes)")).performClick()
        waitForIdle()
        onNodeWithTag(ComprendreDetailTags.TEACHING).assertExists()
        assertTrue(onAllNodesWithText("Fiche d’animation", substring = true).fetchSemanticsNodes().isNotEmpty(), "the kind of the sheet")
        // Its own fixed notice, and no official programme.
        onNodeWithTag(ComprendreDetailTags.TEACHING_NEUTRALITY).performScrollTo().assert(hasText("L’animatrice ou l’animateur reste libre de ses choix.", substring = true))
        assertTrue(onAllNodesWithText("Programme officiel").fetchSemanticsNodes().isEmpty(), "a workshop serves no programme")
        // The questions of the course, the answers folded as for the classroom.
        onNodeWithTag(ComprendreDetailTags.TEACHING_QUESTIONS).performScrollTo().assertExists()
        onNodeWithText("Afficher les réponses").performScrollTo().performClick()
        waitForIdle()
        assertEquals(petitions.quizCount, onAllNodesWithTag(ComprendreDetailTags.TEACHING_ANSWERS).fetchSemanticsNodes().size)
    }

    @Test
    fun aCourseIsReadStepByStepOnAPhoneAndItsAnswersStayOnTheDevice() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        openComprendre(database, width = 400, height = 800)
        // The guides are still there, below the courses (imported after them on a loaded machine).
        waitUntil(timeoutMillis = 60_000) { onAllNodesWithTag(ContentTags.GUIDE_CARD + "garde-a-vue").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag(ComprendreTags.COURSE_CARD + loi.article.slug).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.COURSE).assertExists()
        onNodeWithTag(ComprendreTags.HOME).assertDoesNotExist()
        onNodeWithText("Pas encore commencé").assertExists()

        onNodeWithTag(CourseTags.START).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Étape 1 sur ${loi.steps.size}").assertExists()
        onNodeWithTag(CourseTags.STEP_TITLE).assert(hasText(loi.steps[0].title))

        // Validating needs a choice; the right answer is announced with its explanation.
        val quiz = assertNotNull(loi.steps[0].quiz)
        onNodeWithTag(CourseTags.VALIDATE).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CourseTags.option(quiz.correctIndex)).performScrollTo().performClick()
        onNodeWithTag(CourseTags.VALIDATE).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.FEEDBACK).performScrollTo().assert(hasAnyDescendant(hasText("Bonne réponse.")))
        onNodeWithTag(CourseTags.RETRY).assertDoesNotExist()

        // Written on the device, under the course's stable address.
        val store = LearningProgressStore(database)
        waitUntil(timeoutMillis = 5_000) { store.progress(loi.address)?.answers?.get(0) != null }
        assertEquals(true, store.progress(loi.address)?.answers?.get(0)?.correct)

        // A wrong answer can be tried again.
        onNodeWithTag(CourseTags.NEXT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Étape 2 sur ${loi.steps.size}").assertExists()
        val second = assertNotNull(loi.steps[1].quiz)
        val wrong = second.options.indices.first { it != second.correctIndex }
        onNodeWithTag(CourseTags.option(wrong)).performScrollTo().performClick()
        onNodeWithTag(CourseTags.VALIDATE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.FEEDBACK).performScrollTo().assert(hasAnyDescendant(hasText("Ce n’est pas la bonne réponse.")))
        onNodeWithTag(CourseTags.RETRY).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.VALIDATE).performScrollTo().assertIsNotEnabled()

        // Back to the home: the card says where the reader stopped.
        onNodeWithTag(CourseTags.BACK).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("En cours : étape 2 sur ${loi.steps.size}").performScrollTo().assertExists()
    }

    @Test
    fun theEndPageGivesTheScoreAndTheNextActions() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        openComprendre(database, width = 400, height = 800)
        onNodeWithTag(ComprendreTags.COURSE_CARD + loi.article.slug).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.START).performScrollTo().performClick()
        waitForIdle()
        loi.steps.forEach { step ->
            val quiz = assertNotNull(step.quiz)
            onNodeWithTag(CourseTags.option(quiz.correctIndex)).performScrollTo().performClick()
            onNodeWithTag(CourseTags.VALIDATE).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(CourseTags.NEXT).performScrollTo().performClick()
            waitForIdle()
        }
        onNodeWithTag(CourseTags.END).assertExists()
        onNodeWithTag(CourseTags.SCORE).assert(hasText("Bonnes réponses : ${loi.steps.size} sur ${loi.steps.size}."))
        onNodeWithTag(CourseTags.NEXT_ACTIONS).performScrollTo().assertExists()
        val store = LearningProgressStore(database)
        waitUntil(timeoutMillis = 5_000) { store.progress(loi.address)?.completed == true }

        // « Et maintenant ? » leads to the demands wall.
        onNodeWithTag(CourseTags.action("revendications")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.COURSE).assertDoesNotExist()
    }

    @Test
    fun theProgressSurvivesARestartAndIsErasedOnDemand() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        LearningProgressStore(database).apply {
            answer(loi.address, 0, choice = 0, correct = true, now = 1_794_000_000)
            reachStep(loi.address, 2, now = 1_794_000_100)
        }
        openComprendre(database, width = 400, height = 800)
        onNodeWithText("En cours : étape 3 sur ${loi.steps.size}").performScrollTo().assertExists()

        // The course resumes where the reader stopped.
        onNodeWithTag(ComprendreTags.COURSE_CARD + loi.article.slug).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Reprendre à l’étape 3").performScrollTo().assertExists()
        onNodeWithTag(CourseTags.BACK).performScrollTo().performClick()
        waitForIdle()

        // Erasing asks first; cancelling keeps everything.
        onNodeWithTag(ComprendreTags.CLEAR_PROGRESS).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ComprendreTags.CLEAR_CANCEL).performClick()
        waitForIdle()
        assertNotNull(LearningProgressStore(database).progress(loi.address))

        onNodeWithTag(ComprendreTags.CLEAR_PROGRESS).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ComprendreTags.CLEAR_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { LearningProgressStore(database).count() == 0L }
        assertNull(LearningProgressStore(database).progress(loi.address))
        onNodeWithTag(ComprendreTags.CLEAR_PROGRESS).assertDoesNotExist()
    }

    @Test
    fun aWideWindowKeepsTheHomeBesideTheCourse() = runComposeUiTest {
        openComprendre(JvmDatabase.inMemory(), width = 1000, height = 740)
        onNodeWithTag(ComprendreTags.COURSE_CARD + loi.article.slug).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CourseTags.COURSE).assertExists()
        onNodeWithTag(ComprendreTags.HOME).assertExists()
        onNodeWithTag(CourseTags.BACK).assertDoesNotExist()
        onNodeWithTag(CourseTags.START).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Étape 1 sur ${loi.steps.size}").assertExists()
    }
}
