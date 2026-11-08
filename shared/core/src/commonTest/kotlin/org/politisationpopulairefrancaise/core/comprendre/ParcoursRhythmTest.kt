package org.politisationpopulairefrancaise.core.comprendre

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Je découvre » (CO-19): a course of one lesson a day, a suggestion only, never a lock. */
class ParcoursRhythmTest {
    private val keys = IdentityKeys.generate()
    private val signer = LocalEventSigner(keys)

    /** Three lessons: the first two with a quiz, the last without. */
    private val body = """
        Intro.

        ## Jour 1 : qui décide

        Texte.

        ::: quiz
        ? Q1 ?
        - [x] A
        - [ ] B
        > R.
        :::

        ## Jour 2 : la loi

        Texte.

        ::: quiz
        ? Q2 ?
        - [x] A
        - [ ] B
        > R.
        :::

        ## Jour 3 : et maintenant

        Texte sans question.

        ::: et-maintenant
        - Agir
        :::

        ## Sources

        - S
    """.trimIndent()

    private suspend fun course(rhythm: String?): Parcours {
        val unsigned = Article.build(
            author = keys.publicKey,
            slug = "je-decouvre",
            title = "[EXEMPLE] Je découvre",
            summary = "Sept jours.",
            topics = listOf("institutions"),
            type = ArticleType.PARCOURS,
            origin = ContentOrigin.AI_ASSISTED,
            publishedAt = 1_794_000_000,
            updatedAt = 1_794_000_000,
            body = body,
            durationMinutes = 6,
        )
        val tagged = if (rhythm == null) unsigned else unsigned.copy(tags = unsigned.tags + listOf(listOf(ComprendreTags.RHYTHM, rhythm)))
        return assertNotNull(Parcours.from(assertNotNull(Article.from(signer.sign(tagged)))))
    }

    private fun progress(furthest: Int, answered: List<Int> = emptyList(), completed: Boolean = false) = ParcoursProgress(
        address = "x",
        furthestStep = furthest,
        answers = answered.associateWith { QuizAnswer(it, 0, true) },
        completedAt = if (completed) 1L else null,
        updatedAt = 1L,
    )

    @Test
    fun theRhythmIsReadFromItsTag() = runTest {
        assertTrue(course("quotidien").daily)
        assertEquals(CourseRhythm.DAILY, course("quotidien").rhythm)
        assertFalse(course(null).daily, "a course without rhythm is read at once")
        assertNull(course("hebdomadaire").rhythm, "an unknown rhythm is ignored, the course stays readable")
    }

    @Test
    fun theLessonOfTheDayFollowsTheProgressKeptOnTheDevice() = runTest {
        val daily = course("quotidien")
        assertEquals(0, daily.nextLesson(null), "a newcomer starts with day 1")
        assertEquals(0, daily.nextLesson(progress(0)), "day 1 opened, its question not answered yet")
        assertEquals(1, daily.nextLesson(progress(0, answered = listOf(0))), "day 1 done: day 2")
        assertEquals(2, daily.nextLesson(progress(1, answered = listOf(0, 1))))
        assertEquals(2, daily.nextLesson(progress(2, answered = listOf(0, 1))), "the last lesson has no question: it stays the lesson to read until the course is finished")
        assertNull(daily.nextLesson(progress(2, answered = listOf(0, 1), completed = true)), "nothing left once completed")
        assertEquals(2, daily.nextLesson(progress(9)), "a shortened course never points beyond its last lesson")
    }
}
