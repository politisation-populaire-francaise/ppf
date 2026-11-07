package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType

/** One question of a course (cahier fonctionnel, M13.1: "Quiz courts"): a single right answer. */
data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    /** Why the right answer is right, with its source; shown once the reader has answered. */
    val explanation: String,
) {
    fun isCorrect(choice: Int): Boolean = choice == correctIndex
}

/** A step of a course: a short text and, usually, the quiz that closes it. */
data class ParcoursStep(
    /** Position in the course, from 0. */
    val index: Int,
    val title: String,
    /** The text of the step, in the Markdown of the editorial contents (content/README.md), without its quiz. */
    val body: String,
    val quiz: QuizQuestion?,
)

/**
 * A course of the "Comprendre en 5 minutes" series (cahier fonctionnel, M13.1): an editorial
 * content of type `parcours` whose body is cut into steps by its level-2 headings. The text before
 * the first step is the introduction (its « En bref » box); a `::: quiz` block closes each step; a
 * `::: et-maintenant` block holds the concrete actions proposed at the end (« Et maintenant ? »,
 * cahier fonctionnel, section 3); the `## Sources` section comes last. See docs/FORMATS.md.
 */
data class Parcours(
    val article: Article,
    val introduction: String,
    val steps: List<ParcoursStep>,
    /** The « Et maintenant ? » actions, Markdown bullets; null when the course has none. */
    val nextActions: String?,
    /** The sources section, Markdown; null when missing (the content signer refuses that). */
    val sources: String?,
    /** Objections and their sourced answers (`::: objections`), shown at the end with « Et maintenant ? ». */
    val objections: List<Objection> = emptyList(),
) {
    /** The stable address of the course (`kind:pubkey:identifier`): progress is kept under it. */
    val address: String get() = article.address.toString()

    val quizCount: Int get() = steps.count { it.quiz != null }

    companion object {
        /** Reads a course; null if the content is not a course or has no step. */
        fun from(article: Article): Parcours? {
            if (article.type != ArticleType.PARCOURS) return null
            val parsed = ParcoursFormat.parse(article.body)
            if (parsed.steps.isEmpty()) return null
            return Parcours(article, parsed.introduction, parsed.steps, parsed.nextActions, parsed.sources, parsed.objections)
        }
    }
}

/** A course body as read, with what is wrong in it (the content signer refuses a course with problems). */
data class ParsedParcours(
    val introduction: String,
    val steps: List<ParcoursStep>,
    val nextActions: String?,
    val sources: String?,
    val problems: List<String>,
    val objections: List<Objection> = emptyList(),
)

/**
 * Reads the body of a course. Lenient for the application (a broken quiz is left out, the step
 * stays readable) and strict for the content signer, which refuses any [ParsedParcours.problems].
 */
object ParcoursFormat {
    const val QUIZ_BLOCK: String = "quiz"
    const val NEXT_ACTIONS_BLOCK: String = "et-maintenant"
    const val SOURCES_HEADING: String = "Sources"
    const val MIN_OPTIONS: Int = 2
    const val MAX_OPTIONS: Int = 5

    fun parse(markdown: String): ParsedParcours {
        val problems = mutableListOf<String>()
        val sections = splitSections(markdown.lines())

        val nextActionsBlocks = mutableListOf<List<String>>()
        val objectionBlocks = mutableListOf<List<String>>()
        val introLines = extractBlocks(extractBlocks(sections.introduction, NEXT_ACTIONS_BLOCK, nextActionsBlocks), ContentBlocks.OBJECTIONS, objectionBlocks)
        var sources: String? = null
        val steps = mutableListOf<ParcoursStep>()

        for (section in sections.headed) {
            val withoutActions = extractBlocks(extractBlocks(section.lines, NEXT_ACTIONS_BLOCK, nextActionsBlocks), ContentBlocks.OBJECTIONS, objectionBlocks)
            if (section.title.trim().equals(SOURCES_HEADING, ignoreCase = true)) {
                if (sources != null) problems += "more than one \"## $SOURCES_HEADING\" section"
                sources = withoutActions.joinToString("\n").trim().ifEmpty { null }
                continue
            }
            if (sources != null) problems += "step \"${section.title}\" comes after the sources"
            val quizBlocks = mutableListOf<List<String>>()
            val body = extractBlocks(withoutActions, QUIZ_BLOCK, quizBlocks).joinToString("\n").trim()
            if (quizBlocks.size > 1) problems += "step \"${section.title}\" has ${quizBlocks.size} quizzes (one at most)"
            val quiz = quizBlocks.firstOrNull()?.let { lines ->
                val (question, quizProblems) = parseQuiz(lines)
                problems += quizProblems.map { "step \"${section.title}\": $it" }
                question
            }
            if (quiz == null && quizBlocks.isEmpty()) problems += "step \"${section.title}\" has no quiz"
            steps += ParcoursStep(index = steps.size, title = section.title.trim(), body = body, quiz = quiz)
        }

        if (steps.isEmpty()) problems += "no step (a step starts with a \"## \" heading)"
        if (sources == null) problems += "no \"## $SOURCES_HEADING\" section"
        if (nextActionsBlocks.size > 1) problems += "more than one \"::: $NEXT_ACTIONS_BLOCK\" block"
        val nextActions = nextActionsBlocks.firstOrNull()?.joinToString("\n")?.trim()?.ifEmpty { null }
        if (nextActionsBlocks.isEmpty()) problems += "no \"::: $NEXT_ACTIONS_BLOCK\" block (« Et maintenant ? »)"
        val objections = objectionBlocks.flatMap { lines ->
            val (items, objectionProblems) = ContentBlocks.parseObjections(lines)
            problems += objectionProblems
            items
        }
        problems += steps.flatMap { step -> ContentBlocks.problems(step.body) }
        return ParsedParcours(introLines.joinToString("\n").trim(), steps, nextActions, sources, problems, objections)
    }

    /** A quiz block: one `? question` line, two to five `- [ ]` / `- [x]` options with one `[x]`, and `> explanation` lines. */
    fun parseQuiz(lines: List<String>): Pair<QuizQuestion?, List<String>> {
        val problems = mutableListOf<String>()
        val questions = mutableListOf<String>()
        val options = mutableListOf<String>()
        val correct = mutableListOf<Int>()
        val explanation = mutableListOf<String>()
        for (raw in lines) {
            val line = raw.trim()
            val option = OPTION.matchEntire(line)
            when {
                line.isEmpty() -> Unit
                line.startsWith("?") -> questions += line.removePrefix("?").trim()
                option != null -> {
                    if (option.groupValues[1].equals("x", ignoreCase = true)) correct += options.size
                    options += option.groupValues[2].trim()
                }
                line.startsWith(">") -> explanation += line.removePrefix(">").trim()
                else -> problems += "unexpected line in a quiz: \"$line\""
            }
        }
        if (questions.size != 1 || questions.single().isEmpty()) problems += "a quiz needs exactly one \"? question\" line"
        if (options.size !in MIN_OPTIONS..MAX_OPTIONS) problems += "a quiz needs $MIN_OPTIONS to $MAX_OPTIONS options, found ${options.size}"
        if (options.any { it.isEmpty() }) problems += "a quiz option is empty"
        if (correct.size != 1) problems += "a quiz needs exactly one right answer \"- [x]\", found ${correct.size}"
        val text = explanation.filter { it.isNotEmpty() }.joinToString(" ")
        if (text.isEmpty()) problems += "a quiz needs an explanation \"> ...\""
        // Lines the format does not know make the quiz unusable too: better left out than shown wrong.
        if (problems.isNotEmpty()) return null to problems
        return QuizQuestion(questions.single(), options.toList(), correct.single(), text) to emptyList()
    }

    private class Section(val title: String, val lines: List<String>)

    private class Sections(val introduction: List<String>, val headed: List<Section>)

    /** Cuts at level-2 headings that are not inside a `:::` block. */
    private fun splitSections(lines: List<String>): Sections {
        val introduction = mutableListOf<String>()
        val headed = mutableListOf<Section>()
        var title: String? = null
        var current = mutableListOf<String>()
        var depth = 0
        for (line in lines) {
            val trimmed = line.trimEnd()
            val heading = if (depth == 0) STEP_HEADING.matchEntire(trimmed) else null
            if (heading != null) {
                if (title == null) introduction += current else headed += Section(title, current)
                title = heading.groupValues[1]
                current = mutableListOf()
                continue
            }
            current += line
            if (BLOCK_START.matches(trimmed)) depth++ else if (BLOCK_END.matches(trimmed) && depth > 0) depth--
        }
        if (title == null) introduction += current else headed += Section(title, current)
        return Sections(introduction, headed)
    }

    /** Removes the `::: [name]` blocks from [lines], adding the inner lines of each to [found]. */
    private fun extractBlocks(lines: List<String>, name: String, found: MutableList<List<String>>): List<String> {
        val kept = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val start = BLOCK_START.matchEntire(lines[index].trimEnd())
            if (start != null && start.groupValues[1] == name) {
                val inner = mutableListOf<String>()
                index++
                while (index < lines.size && !BLOCK_END.matches(lines[index].trimEnd())) {
                    inner += lines[index]
                    index++
                }
                found += inner
                index++ // the closing line, if any
                continue
            }
            kept += lines[index]
            index++
        }
        return kept
    }

    private val STEP_HEADING = Regex("^##\\s+(.+)$")
    private val BLOCK_START = Regex("^:::\\s*([a-z-]+)\\s*$")
    private val BLOCK_END = Regex("^:::\\s*$")
    private val OPTION = Regex("^[-*]\\s+\\[( |x|X)]\\s*(.*)$")
}
