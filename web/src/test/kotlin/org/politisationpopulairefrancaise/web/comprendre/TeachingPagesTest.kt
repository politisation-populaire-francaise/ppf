package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.comprendre.ComprendreSchemas
import org.politisationpopulairefrancaise.core.comprendre.TeachingLevel
import org.politisationpopulairefrancaise.core.comprendre.TeachingSheet
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDocument
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialMessages
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The teaching sheets for the classroom on the site (CO-13, D-096), rendered from the example contents bundled
 * with the applications and from the example sheet of content/enseignants, checked by its schema and built as
 * the content signer builds it (the bundled comprendre.json holds it once the contents are signed again).
 */
class TeachingPagesTest {
    private fun file(vararg parts: String): Path =
        listOf(Path.of("..", *parts), Path.of(parts.first(), *parts.drop(1).toTypedArray())).first(Files::exists)

    private val trusted: List<Article> = FILES
        .flatMap { Json.decodeFromString(ListSerializer(SignedEvent.serializer()), Files.readString(file(DIRECTORY_ROOT, *DIRECTORY, it))) }
        .filter { it.pubkey == EditorialTrust.EXAMPLE_EDITOR && it.verify() }
        .mapNotNull(Article::from)

    /** The example sheet of content/enseignants, checked by its schema against the bundled contents, then built. */
    private val example: Article = run {
        val path = file("content", "enseignants", SHEET_FILE)
        val document = ContentDocument.parse(SHEET_FILE, Files.readString(path))
        val schema = ComprendreSchemas.FICHE_PEDAGOGIQUE
        val draft = document.toDraft(schema.type)
        val context = CheckContext.of(EditorialTrust.EXAMPLE_EDITOR, trusted.associate { it.slug to it.type })
        val problems = EditorialEngine.check(schema, draft, context)
        assertTrue(problems.isEmpty(), problems.joinToString("\n") { EditorialMessages.french(it) })
        val unsigned = EditorialEngine.build(schema, draft, PublicKey.fromHex(EditorialTrust.EXAMPLE_EDITOR))
        // Not signed: the catalog is built from articles already read from trusted events.
        Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private val catalog = ComprendreCatalog.from(
        if (trusted.any { it.type == ArticleType.FICHE_PEDAGOGIQUE && it.slug == SHEET_SLUG }) trusted else trusted + example,
    )

    private fun render(document: HTML.() -> Unit): String = createHTML().html { document() }

    /** The text of [html] with the escapes of kotlinx.html undone, to look for the words of a content. */
    private fun text(html: String): String =
        html.replace("&#39;", "'").replace("&#x27;", "'").replace("&apos;", "'").replace("&quot;", "\"").replace("&amp;", "&")

    /** Every page of the classroom: no script at all (not even the worker), no inline style, no em dash, French. */
    private fun checkPage(html: String) {
        assertTrue(html.contains("<html lang=\"fr\">"))
        assertFalse(html.contains("<script"), "no script on a page of the classroom")
        assertFalse(html.contains("style=\""), "no inline style")
        assertFalse(Regex(" on[a-z]+=\"").containsMatchIn(html), "no event handler")
        assertFalse(html.contains("—"), "no em dash")
        assertFalse(html.contains("<form"), "nothing is ever sent from the classroom")
        assertFalse(html.contains("<input"))
        // The stylesheet of the site, with its print block.
        val stylesheet = Regex("<link[^>]*href=\"/static/site.css\"[^>]*>").find(html)?.value.orEmpty()
        assertTrue(stylesheet.contains("rel=\"stylesheet\""), stylesheet)
    }

    private val sheet: TeachingSheet get() = assertNotNull(catalog.teachingSheet(SHEET_SLUG))

    @Test
    fun theExampleSheetIsAnExampleOfTheLyceeLinkedToItsCourse() {
        assertTrue(sheet.article.title.startsWith("[EXEMPLE]"))
        assertEquals(ContentOrigin.AI_ASSISTED, sheet.article.origin)
        assertTrue(sheet.article.body.contains("rédigé avec l'aide d'une IA"))
        assertEquals(TeachingLevel.LYCEE, sheet.level)
        assertEquals(55, sheet.article.durationMinutes)
        assertEquals(55, sheet.totalMinutes, "the timed steps add up to the duration")
        assertTrue(sheet.objections.size >= 2, "objections from both sides")
        assertTrue(sheet.programme.contains("Bulletin officiel n° 24 du 13 juin 2024"))
        assertTrue(sheet.programme.contains("vérifié le 2026-11-08"))
        assertTrue(sheet.sources.orEmpty().contains("consulté le 2026-11-08"))
        val course = assertNotNull(catalog.courseOf(sheet))
        assertEquals("comment-une-loi-est-votee", course.article.slug)
        assertEquals("comment-une-loi-est-votee", sheet.courseSlug)
    }

    @Test
    fun theSheetPageShowsTheNoticeTheProgrammeTheCourseAndItsLicence() {
        val html = render { teachingSheetDocument(sheet, catalog) }
        checkPage(html)
        // The fixed notice of neutrality, word for word (docs/integration/comprendre-enseignants.md, section 1).
        assertTrue(html.contains("L’enseignant reste libre de ses choix pédagogiques. Cette fiche respecte la neutralité de l’enseignement public : elle présente des faits et des débats, jamais une consigne de vote ni une position partisane."))
        assertTrue(text(html).contains(sheet.programme))
        assertTrue(html.contains("href=\"/comprendre/parcours/comment-une-loi-est-votee\""))
        assertTrue(html.contains("Texte sous licence CC BY 4.0, Politisation Populaire Française"))
        assertTrue(html.contains("Contenu d’exemple"))
        assertTrue(html.contains("Rédigé avec l’aide d’une IA"))
        listOf("Objectifs", "Prérequis", "Déroulé", "Débat guidé", "Objections et réponses").forEach { assertTrue(html.contains(it), it) }
        assertTrue(html.contains("id=\"sources\""))
        assertTrue(html.contains("<a href=\"/comprendre/enseignants\">Pour la classe</a>"), "breadcrumb to the list")
    }

    @Test
    fun theQuizOfTheCourseIsListedWithItsAnswersApart() {
        val html = render { teachingSheetDocument(sheet, catalog) }
        val course = catalog.courseOf(sheet)!!
        val quizzes = course.steps.mapNotNull { it.quiz }
        assertTrue(quizzes.isNotEmpty())
        val questionsAt = html.indexOf("id=\"questions\"")
        val answersAt = html.indexOf("id=\"reponses\"")
        assertTrue(questionsAt > 0, "a section « Questions à main levée »")
        assertTrue(html.contains(">Questions à main levée</h2>"))
        assertTrue(answersAt > questionsAt, "the answers come after the questions")
        assertTrue(html.contains(">Réponses pour l’enseignant</h2>"))
        assertTrue(html.contains("<section class=\"teaching-answers\""), "the section the print stylesheet starts on a new page")
        val questions = text(html.substring(questionsAt, answersAt))
        val answers = text(html.substring(answersAt))
        quizzes.forEach { quiz ->
            assertTrue(questions.contains(quiz.question), quiz.question)
            quiz.options.forEach { assertTrue(questions.contains(it), it) }
            assertTrue(answers.contains(quiz.explanation), quiz.explanation)
        }
        assertFalse(questions.contains("La bonne réponse"), "no answer among the questions")
        assertFalse(questions.contains("<details"), "the answers are apart, not hidden under the questions")
        assertEquals(quizzes.size, Regex("La bonne réponse : [A-E]\\. ").findAll(answers).count())
        assertTrue(answers.contains("La bonne réponse : ${'A' + quizzes.first().correctIndex}. "))
    }

    @Test
    fun thePrintStylesheetHidesTheSiteAndStartsTheAnswersOnANewPage() {
        val css = assertNotNull(javaClass.getResource("/static/site.css")).readText()
        val print = css.substring(css.lastIndexOf("@media print"))
        listOf(".site-header", ".site-footer", "display: none", "--ink: #000000", "--surface-1: #ffffff", ".teaching-answers", "break-before: page")
            .forEach { assertTrue(print.contains(it), it) }
        assertFalse(css.contains("—"))
    }

    @Test
    fun theCoursePageLinksToItsSheetOnly() {
        val course = catalog.course("comment-une-loi-est-votee")!!
        val html = render { parcoursDocument(course, catalog) }
        assertTrue(html.contains("<a href=\"/comprendre/enseignants/$SHEET_SLUG\">Pour la classe : fiche pédagogique (lycée)</a>"))
        val other = render { parcoursDocument(catalog.course("le-49-3-et-la-motion-de-censure")!!, catalog) }
        assertFalse(other.contains("fiche pédagogique"), "no link when the course has no sheet")
    }

    @Test
    fun theListShowsTheSheetsByLevelAndTheHomeLeadsToIt() {
        val html = render { teachingSheetsDocument(catalog) }
        checkPage(html)
        assertTrue(html.contains("id=\"niveau-lycee\""))
        assertTrue(html.contains(">Lycée</h2>"))
        assertTrue(html.contains("href=\"/comprendre/enseignants/$SHEET_SLUG\""))
        assertTrue(html.contains("L’enseignant reste libre de ses choix pédagogiques."))
        assertFalse(html.contains("id=\"niveau-cycle-4\""), "no empty level")
        val empty = render { teachingSheetsDocument(ComprendreCatalog.EMPTY) }
        checkPage(empty)
        assertTrue(empty.contains("Rien de publié pour l’instant."))
        val home = render { comprendreHomeDocument(catalog) }
        assertTrue(home.contains("href=\"/comprendre/enseignants\""))
        assertTrue(home.contains("Pour la classe"))
    }

    private companion object {
        const val DIRECTORY_ROOT = "shared"
        val DIRECTORY = arrayOf("ui", "src", "commonMain", "composeResources", "files", "contents")
        val FILES = listOf("fiches.json", "parcours.json", "comprendre.json", "glossaire.json")
        const val SHEET_SLUG = "classe-comment-une-loi-est-votee-lycee"
        const val SHEET_FILE = "$SHEET_SLUG.md"
    }
}
