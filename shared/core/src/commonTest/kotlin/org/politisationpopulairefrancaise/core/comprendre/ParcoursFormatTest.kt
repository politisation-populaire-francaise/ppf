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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParcoursFormatTest {

    private val body = """
        ::: en-bref
        - Une loi passe par **les deux assemblées**.
        :::

        **[EXEMPLE] Brouillon.**

        ## Qui propose une loi ?

        Le gouvernement dépose un **projet**, les parlementaires une **proposition**.

        ::: attention
        - Un encart ordinaire reste dans le texte de l'étape.
        :::

        ::: quiz
        ? Qui peut déposer une proposition de loi ?
        - [ ] Le Conseil d'État
        - [x] Un député ou un sénateur
        - [ ] Le préfet
        > Constitution, article 39 : l'initiative appartient au Premier ministre
        > et aux membres du Parlement.
        :::

        ## La navette

        Le texte va d'une assemblée à l'autre.

        ::: quiz
        ? Qui a le dernier mot en cas de désaccord ?
        - [x] L'Assemblée nationale
        - [ ] Le Sénat
        > Constitution, article 45.
        :::

        ::: et-maintenant
        - [Voir les revendications](ppf:revendications)
        :::

        ## Sources

        - Constitution, article 39 : https://www.legifrance.gouv.fr
    """.trimIndent()

    @Test
    fun aCourseIsCutIntoStepsWithTheirQuiz() {
        val parsed = ParcoursFormat.parse(body)

        assertEquals(emptyList(), parsed.problems)
        assertEquals(listOf("Qui propose une loi ?", "La navette"), parsed.steps.map { it.title })
        assertEquals(listOf(0, 1), parsed.steps.map { it.index })
        assertTrue(parsed.introduction.startsWith("::: en-bref"))
        assertTrue(parsed.introduction.contains("[EXEMPLE]"))

        val first = parsed.steps[0]
        assertTrue(first.body.contains("::: attention"), "ordinary boxes stay in the step")
        assertTrue("::: quiz" !in first.body, "the quiz is taken out of the text")
        val quiz = assertNotNull(first.quiz)
        assertEquals("Qui peut déposer une proposition de loi ?", quiz.question)
        assertEquals(3, quiz.options.size)
        assertEquals(1, quiz.correctIndex)
        assertTrue(quiz.isCorrect(1))
        assertEquals(
            "Constitution, article 39 : l'initiative appartient au Premier ministre et aux membres du Parlement.",
            quiz.explanation,
        )

        assertEquals(0, assertNotNull(parsed.steps[1].quiz).correctIndex)
        assertEquals("- [Voir les revendications](ppf:revendications)", parsed.nextActions)
        assertEquals("- Constitution, article 39 : https://www.legifrance.gouv.fr", parsed.sources)
        assertTrue("::: et-maintenant" !in parsed.steps[1].body)
    }

    @Test
    fun headingsInsideABoxDoNotStartAStep() {
        val parsed = ParcoursFormat.parse(
            """
            ## Étape

            ::: conseil
            ## Pas une étape
            :::

            ::: quiz
            ? Question ?
            - [x] Oui
            - [ ] Non
            > Parce que.
            :::

            ::: et-maintenant
            - Agir
            :::

            ## Sources

            - Source
            """.trimIndent(),
        )
        assertEquals(listOf("Étape"), parsed.steps.map { it.title })
        assertEquals(emptyList(), parsed.problems)
    }

    @Test
    fun aBrokenQuizIsLeftOutAndReported() {
        val (none, problems) = ParcoursFormat.parseQuiz(listOf("? Question ?", "- [x] A", "- [x] B", "> Explication."))
        assertNull(none)
        assertTrue(problems.any { "exactly one right answer" in it }, problems.toString())

        val (alone, aloneProblems) = ParcoursFormat.parseQuiz(listOf("? Question ?", "- [x] A", "> Explication."))
        assertNull(alone)
        assertTrue(aloneProblems.any { "2 to 5 options" in it }, aloneProblems.toString())

        val (noWhy, noWhyProblems) = ParcoursFormat.parseQuiz(listOf("? Question ?", "- [x] A", "- [ ] B"))
        assertNull(noWhy)
        assertTrue(noWhyProblems.any { "explanation" in it }, noWhyProblems.toString())

        val (stray, strayProblems) = ParcoursFormat.parseQuiz(listOf("? Question ?", "- [x] A", "- [ ] B", "> Pourquoi.", "texte libre"))
        assertNull(stray)
        assertTrue(strayProblems.any { "unexpected line" in it }, strayProblems.toString())
    }

    @Test
    fun theSignerIsToldWhatIsMissing() {
        val parsed = ParcoursFormat.parse(
            """
            Introduction seule.

            ## Étape sans quiz

            Texte.

            ## Autre étape

            ::: quiz
            ? Q ?
            - [x] A
            - [ ] B
            > R.
            :::

            ::: quiz
            ? Q2 ?
            - [x] A
            - [ ] B
            > R.
            :::
            """.trimIndent(),
        )
        assertEquals(2, parsed.steps.size)
        assertNull(parsed.steps[0].quiz)
        assertNotNull(parsed.steps[1].quiz, "the first of two quizzes is kept")
        val problems = parsed.problems.joinToString("\n")
        assertTrue("has no quiz" in problems, problems)
        assertTrue("2 quizzes" in problems, problems)
        assertTrue("no \"## Sources\" section" in problems, problems)
        assertTrue("et-maintenant" in problems, problems)
    }

    @Test
    fun aStepAfterTheSourcesIsReported() {
        val parsed = ParcoursFormat.parse("## Sources\n\n- S\n\n## Trop tard\n\nTexte.")
        assertTrue(parsed.problems.any { "after the sources" in it }, parsed.problems.toString())
    }

    @Test
    fun aBodyWithoutHeadingHasNoStep() {
        val parsed = ParcoursFormat.parse("Juste un texte.")
        assertEquals(0, parsed.steps.size)
        assertTrue(parsed.problems.any { "no step" in it })
    }
}

class ParcoursTest {

    private val keys = IdentityKeys.generate()
    private val signer = LocalEventSigner(keys)

    private suspend fun article(type: ArticleType, body: String, duration: Int? = 5): Article = Article.from(
        signer.sign(
            Article.build(
                author = keys.publicKey,
                slug = "loi",
                title = "[EXEMPLE] Comment une loi est votée",
                summary = "Le chemin d'une loi.",
                topics = listOf("institutions"),
                type = type,
                origin = ContentOrigin.AI_ASSISTED,
                publishedAt = 1_794_000_000,
                updatedAt = 1_794_000_000,
                body = body,
                durationMinutes = duration,
            ),
        ),
    )!!

    private val body = "Intro.\n\n## Étape\n\nTexte.\n\n::: quiz\n? Q ?\n- [x] A\n- [ ] B\n> R.\n:::\n\n::: et-maintenant\n- Agir\n:::\n\n## Sources\n\n- S\n"

    @Test
    fun aCourseIsReadFromItsArticle() = runTest {
        val parcours = assertNotNull(Parcours.from(article(ArticleType.PARCOURS, body)))
        assertEquals(5, parcours.article.durationMinutes)
        assertEquals(1, parcours.steps.size)
        assertEquals(1, parcours.quizCount)
        assertEquals("30023:${keys.publicKey.hex}:loi", parcours.address)
        assertEquals("Intro.", parcours.introduction)
    }

    @Test
    fun onlyCoursesWithStepsAreCourses() = runTest {
        assertNull(Parcours.from(article(ArticleType.FICHE_PRATIQUE, body)))
        assertNull(Parcours.from(article(ArticleType.PARCOURS, "Pas d'étape.")))
    }

    @Test
    fun theDurationIsOptionalAndBounded() = runTest {
        assertNull(article(ArticleType.PARCOURS, body, duration = null).durationMinutes)
        assertFailsWith<IllegalArgumentException> {
            Article.build(
                author = keys.publicKey, slug = "x", title = "X", summary = null, topics = emptyList(),
                type = ArticleType.PARCOURS, origin = ContentOrigin.HUMAN,
                publishedAt = 1, updatedAt = 1, body = body, durationMinutes = 0,
            )
        }
    }
}
