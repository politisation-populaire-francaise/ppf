package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.comprendre.QuizQuestion
import org.politisationpopulairefrancaise.core.comprendre.TeachingLevel
import org.politisationpopulairefrancaise.core.comprendre.TeachingSheet
import org.politisationpopulairefrancaise.web.siteLayout

/*
 * The teaching sheets for the classroom on the web site (CO-13, D-096, docs/integration/comprendre-enseignants.md):
 * the list by level and the page of each sheet, readable, projectable and printable without any script (not even
 * the registration of the offline worker). The quiz « à main levée » is the one of the linked course, its answers
 * in a section of their own that the print stylesheet starts on a new page. No account, nothing is ever sent.
 */

private val teachingSection = "Pour la classe" to ComprendreLinks.listPath(ComprendreLinks.ENSEIGNANTS)

/** « Fiche pédagogique · Lycée · 55 min ». */
internal fun teachingMeta(sheet: TeachingSheet): String = buildList {
    add("Fiche pédagogique")
    add(sheet.level.label)
    sheet.article.durationMinutes?.let { add("$it min") }
}.joinToString(" · ")

/** The words of the link from a course to one of its sheets: « Pour la classe : fiche pédagogique (lycée) ». */
internal fun teachingLinkLabel(sheet: TeachingSheet): String = "Pour la classe : fiche pédagogique (${sheet.level.inSentence})"

internal fun FlowContent.teachingCard(sheet: TeachingSheet, catalog: ComprendreCatalog, level: Int) {
    article(classes = "card") {
        p(classes = "kicker") { +teachingMeta(sheet) }
        headingOf(this, level) { a(href = ComprendreLinks.pathOf(sheet.article)!!) { +sheet.article.title } }
        sheet.article.summary?.let { p { +it } }
        catalog.courseOf(sheet)?.let { course -> p(classes = "muted") { +"Parcours : ${course.article.title}" } }
    }
}

/** The neutrality notice of every teaching sheet: a fixed text, never the writer's. */
private fun FlowContent.neutralityNotice() {
    p(classes = "notice teaching-neutrality") { +TEACHING_NEUTRALITY }
}

/** The list of the teaching sheets, by level (the lycée first, then the cycle 4). */
fun HTML.teachingSheetsDocument(catalog: ComprendreCatalog) {
    siteLayout(pageTitle = "Pour la classe", currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb(null, "Pour la classe")
        comprendreHead(null, "Pour la classe", "Des fiches pédagogiques pour faire vivre un parcours « Comprendre en 5 minutes » en 55 minutes avec un groupe : objectifs, déroulé minuté, questions à main levée, débat guidé. À projeter ou à imprimer.")
        neutralityNotice()
        p(classes = "muted") { +"Aucun compte, aucune donnée d’élève : rien n’est envoyé depuis la classe. La fiche se lit, se projette et s’imprime." }
        if (catalog.teachingSheets.isEmpty()) {
            p(classes = "muted") { +"Rien de publié pour l’instant." }
            return@siteLayout
        }
        TeachingLevel.entries.forEach { level ->
            val sheets = catalog.teachingSheets.filter { it.level == level }
            if (sheets.isEmpty()) return@forEach
            comprendreSection("niveau-${level.tagValue}", level.label) {
                div(classes = "card-list") { sheets.forEach { teachingCard(it, catalog, 3) } }
            }
        }
    }
}

/**
 * The page of one teaching sheet: the neutrality notice, the programme it serves, the linked course, the text
 * of the sheet (objectives, prerequisites, outline, guided debate), the questions of the course to ask « à main
 * levée », the sources, the licence, then the answers for the teacher, printed on a page of their own.
 */
fun HTML.teachingSheetDocument(sheet: TeachingSheet, catalog: ComprendreCatalog) {
    val renderer = ComprendreRenderer(catalog.glossary)
    val article = sheet.article
    val parts = splitBody(article.body)
    val course = catalog.courseOf(sheet)
    val questions = course?.steps?.mapNotNull { it.quiz }.orEmpty()
    siteLayout(pageTitle = article.title, currentSpace = Space.COMPRENDRE) {
        comprendreBreadcrumb(teachingSection, article.title)
        comprendreHead(teachingMeta(sheet), article.title, article.summary) { contentBadges(article) }
        neutralityNotice()
        comprendreSection("programme", "Programme officiel", "teaching-programme") {
            p { +sheet.programme }
        }
        comprendreSection("parcours-lie", "Le parcours de la séance", "teaching-course") {
            if (course == null) {
                p(classes = "muted") { +"Le parcours de cette fiche n’est pas publié pour l’instant : ses questions ne peuvent pas être reprises." }
            } else {
                p { a(href = ComprendreLinks.pathOf(course.article)!!) { +course.article.title } }
                p(classes = "muted") { +"Comprendre en 5 minutes · ${courseMeta(course)}. À projeter ou à imprimer depuis sa page." }
            }
            p(classes = "muted") { +"Pour imprimer cette fiche : menu du navigateur, puis « Imprimer ». Les réponses des questions s’impriment sur une page à part." }
        }
        readingBody { renderer.markdown(this, parts.text) }
        comprendreSection("questions", "Questions à main levée", "teaching-questions") {
            if (questions.isEmpty()) {
                p(classes = "muted") { +"Aucune question à reprendre pour l’instant." }
            } else {
                p(classes = "muted") { +"Les questions du parcours, à lire à voix haute ; la classe vote à main levée. Les bonnes réponses sont à part, pour l’enseignant." }
                ol(classes = "teaching-question-list") {
                    questions.forEach { quiz -> li { question(renderer, quiz) } }
                }
            }
        }
        glossaryTerms(renderer)
        sourcesSection(renderer, parts.sources)
        p(classes = "licence") { +"$TEACHING_LICENCE." }
        comprendreOrigin(article)
        if (course != null && questions.isNotEmpty()) answers(renderer, questions, course)
    }
}

/** A question of the course and its options (A, B, C...), without its answer. */
private fun FlowContent.question(renderer: ComprendreRenderer, quiz: QuizQuestion) {
    p(classes = "quiz-question") { renderer.inline(this, quiz.question, glossary = false) }
    ul(classes = "quiz-options") {
        quiz.options.forEachIndexed { index, option ->
            li {
                span(classes = "quiz-letter") { +"${teachingLetter(index)}." }
                +" "
                renderer.inline(this, option, glossary = false)
            }
        }
    }
}

/** The answers for the teacher, in a section of its own (a new page when printed). */
private fun FlowContent.answers(renderer: ComprendreRenderer, questions: List<QuizQuestion>, course: Parcours) {
    section(classes = "teaching-answers") {
        attributes["aria-labelledby"] = "reponses"
        h2 {
            id = "reponses"
            +"Réponses pour l’enseignant"
        }
        p(classes = "muted") { +"Les bonnes réponses des questions du parcours « ${course.article.title} », avec leur explication et leur source." }
        ol(classes = "teaching-answer-list") {
            questions.forEachIndexed { number, quiz ->
                li {
                    h3 { +"Question ${number + 1}" }
                    p {
                        strong { +"La bonne réponse : ${teachingLetter(quiz.correctIndex)}. " }
                        renderer.inline(this, quiz.options[quiz.correctIndex], glossary = false)
                    }
                    p { renderer.inline(this, quiz.explanation, glossary = false) }
                }
            }
        }
    }
}

private fun teachingLetter(index: Int): Char = 'A' + index
