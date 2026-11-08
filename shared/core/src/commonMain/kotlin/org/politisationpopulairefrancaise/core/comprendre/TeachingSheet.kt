package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.Problem

/**
 * The levels a teaching sheet serves (D-096): the lycée first (EMC from the seconde to the terminale), then
 * the cycle 4 (collège, from the 5e to the 3e). The level `atelier` of the popular education kit comes with
 * CO-24. Values never renamed: they are the `ppf-level` tags of signed events.
 */
enum class TeachingLevel(val tagValue: String) {
    LYCEE("lycee"),
    CYCLE_4("cycle-4"),
    ;

    companion object {
        fun fromTag(value: String?): TeachingLevel? = entries.firstOrNull { it.tagValue == value?.trim() }
    }
}

/** One timed step of the outline of a sheet: « - 10 min : ce que fait la classe ». */
data class TeachingStep(val minutes: Int, val text: String)

/**
 * A teaching sheet for the classroom (CO-13, D-096, docs/integration/comprendre-enseignants.md): how to bring
 * one course of "Comprendre" to life in 55 minutes with a group. Linked to its course by an `a` tag of role
 * `parcours` and to a level (`ppf-level`); it cites the official programme it serves (`ppf-programme`). Its
 * body has the sections of [TeachingSheetFormat], in that order. The quiz « à main levée » is never rewritten:
 * the readers take the questions of the linked course. No account, no data of pupils: the sheet is read,
 * projected and printed, it measures nothing.
 */
data class TeachingSheet(
    val article: Article,
    /** Address of the linked course: `30023:<key>:<identifier>`. */
    val courseAddress: String,
    val level: TeachingLevel,
    /** The programme served, in one sentence: title, Bulletin officiel, date, date of verification. */
    val programme: String,
    /** The text before the first section (the warning of an example content). */
    val introduction: String,
    val objectives: String,
    val prerequisites: String,
    /** The section « Déroulé » as written (its timed steps and the text around them). */
    val outline: String,
    val steps: List<TeachingStep>,
    /** The section « Débat guidé » without its objections block. */
    val debate: String,
    /** The objections of the guided debate, both sides of the debate, with their sourced answers. */
    val objections: List<Objection>,
    /** The optional section « Pour aller plus loin ». */
    val further: String?,
    val sources: String?,
) {
    /** The identifier of the linked course (the address cut at its first two colons). */
    val courseSlug: String get() = courseAddress.split(':', limit = 3).getOrElse(2) { "" }

    /** The sum of the minutes of the timed steps. */
    val totalMinutes: Int get() = steps.sumOf { it.minutes }

    companion object {
        /**
         * Reads a sheet; null when the content is not a teaching sheet or misses its course, its level or its
         * programme: it is then not shown. The body is read leniently (a missing section is empty).
         */
        fun from(article: Article): TeachingSheet? {
            if (article.type != ArticleType.FICHE_PEDAGOGIQUE) return null
            val event = article.event
            val course = event.tags.firstOrNull { it.size >= 4 && it[0] == "a" && it[3] == ComprendreTags.COURSE_LINK_ROLE }?.get(1)
                ?.takeIf { it.split(':', limit = 3).size == 3 && it.substringAfterLast(':').isNotBlank() }
                ?: return null
            val level = TeachingLevel.fromTag(event.firstTagValue(ComprendreTags.LEVEL)) ?: return null
            val programme = event.firstTagValue(ComprendreTags.PROGRAMME)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parsed = TeachingSheetFormat.parse(article.body)
            return TeachingSheet(
                article = article,
                courseAddress = course,
                level = level,
                programme = programme,
                introduction = parsed.introduction,
                objectives = parsed.section(TeachingSheetFormat.OBJECTIVES).orEmpty(),
                prerequisites = parsed.section(TeachingSheetFormat.PREREQUISITES).orEmpty(),
                outline = parsed.section(TeachingSheetFormat.OUTLINE).orEmpty(),
                steps = parsed.steps,
                debate = parsed.debate,
                objections = parsed.objections,
                further = parsed.section(TeachingSheetFormat.FURTHER),
                sources = parsed.section(TeachingSheetFormat.SOURCES),
            )
        }
    }
}

/** A sheet body as read: the introduction, each section by its title (first occurrence), the steps and the objections. */
class ParsedTeachingSheet(
    val introduction: String,
    /** The sections in the order of the body: title and text (blocks included). */
    val sections: List<Pair<String, String>>,
    val steps: List<TeachingStep>,
    /** The lines of the outline that start like a step but are not one. */
    val malformedSteps: List<String>,
    /** The section « Débat guidé » without its objections blocks. */
    val debate: String,
    /** The objections of the blocks of the section « Débat guidé ». */
    val objections: List<Objection>,
) {
    /** The text of the first section titled [title], trimmed; null when absent or empty. */
    fun section(title: String): String? = sections.firstOrNull { it.first == title }?.second?.trim()?.ifEmpty { null }
}

/**
 * The body of a teaching sheet (D-096): sections `## Objectifs`, `## Prérequis`, `## Déroulé` (timed steps
 * « - 10 min : … » whose minutes add up to the duration of the sheet), `## Débat guidé` (a `::: objections`
 * block with at least two objections, one from each side of the debate), optional `## Pour aller plus loin`,
 * then `## Sources`, in this order. The schema ([ComprendreSchemas.FICHE_PEDAGOGIQUE]) checks the presence of
 * the required sections; [problems] checks the rest, with French messages (keys of `EditorialMessages`).
 * The two sides of the debate cannot be checked by a machine: the review of the team does.
 */
object TeachingSheetFormat {
    const val OBJECTIVES: String = "Objectifs"
    const val PREREQUISITES: String = "Prérequis"
    const val OUTLINE: String = "Déroulé"
    const val DEBATE: String = "Débat guidé"
    const val FURTHER: String = "Pour aller plus loin"
    const val SOURCES: String = "Sources"

    /** Every section, in the order of the body. */
    val SECTIONS: List<String> = listOf(OBJECTIVES, PREREQUISITES, OUTLINE, DEBATE, FURTHER, SOURCES)

    /** The sections a sheet must have (checked by the engine, `BodySpec.requiredSections`). */
    val REQUIRED_SECTIONS: List<String> = SECTIONS - FURTHER

    /** At least two objections in the guided debate: one from each side. */
    const val MIN_OBJECTIONS: Int = 2

    /** The longest programme sentence (`ppf-programme`). */
    const val PROGRAMME_MAX: Int = 500

    const val RULE_SECTION_UNKNOWN: String = "comprendre.rule.teaching_section_unknown"
    const val RULE_SECTION_REPEATED: String = "comprendre.rule.teaching_section_repeated"
    const val RULE_SECTION_ORDER: String = "comprendre.rule.teaching_section_order"
    const val RULE_SECTION_EMPTY: String = "comprendre.rule.teaching_section_empty"
    const val RULE_OBJECTIONS: String = "comprendre.rule.teaching_objections"
    const val RULE_STEP: String = "comprendre.rule.teaching_step"
    const val RULE_NO_STEP: String = "comprendre.rule.teaching_no_step"
    const val RULE_DURATION: String = "comprendre.rule.teaching_duration"
    const val RULE_READABLE: String = "comprendre.rule.teaching_readable"

    fun parse(markdown: String): ParsedTeachingSheet {
        val introduction = mutableListOf<String>()
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        var depth = 0
        for (line in markdown.replace("\r\n", "\n").lines()) {
            val trimmed = line.trimEnd()
            val heading = if (depth == 0) HEADING.matchEntire(trimmed) else null
            if (heading != null) {
                sections += heading.groupValues[1].trim() to mutableListOf()
                continue
            }
            (sections.lastOrNull()?.second ?: introduction).add(line)
            if (BLOCK_START.matches(trimmed)) depth++ else if (BLOCK_END.matches(trimmed) && depth > 0) depth--
        }

        val steps = mutableListOf<TeachingStep>()
        val malformed = mutableListOf<String>()
        sections.firstOrNull { it.first == OUTLINE }?.second?.let { lines ->
            for (raw in outsideBlocks(lines)) {
                if (!STEP_START.containsMatchIn(raw)) continue // text, or a sub-item indented under a step
                val match = STEP.matchEntire(raw.trimEnd())
                val minutes = match?.groupValues?.get(1)?.toIntOrNull()
                if (match == null || minutes == null || minutes < 1) malformed += raw.trim() else steps += TeachingStep(minutes, match.groupValues[2].trim())
            }
        }

        val debateLines = sections.firstOrNull { it.first == DEBATE }?.second.orEmpty()
        val blocks = mutableListOf<List<String>>()
        val debate = ContentBlocks.extract(debateLines, ContentBlocks.OBJECTIONS, blocks).joinToString("\n").trim()
        val objections = blocks.flatMap { ContentBlocks.parseObjections(it).first }

        return ParsedTeachingSheet(
            introduction = introduction.joinToString("\n").trim(),
            sections = sections.map { (title, lines) -> title to lines.joinToString("\n").trim() },
            steps = steps,
            malformedSteps = malformed,
            debate = debate,
            objections = objections,
        )
    }

    /**
     * The problems of a sheet body beyond the presence of the required sections: unknown, repeated, misordered
     * or empty sections, malformed timed steps, steps whose minutes do not add up to [durationMinutes], fewer
     * than two objections in the guided debate. Located on the body, with French messages.
     */
    fun problems(markdown: String, durationMinutes: Int?): List<Problem> {
        val parsed = parse(markdown)
        val problems = mutableListOf<Problem>()
        fun problem(code: String, vararg args: String) {
            problems += Problem(code, EditorialEngine.BODY, args = args.toList())
        }

        val titles = parsed.sections.map { it.first }
        titles.filter { it !in SECTIONS }.distinct().forEach { problem(RULE_SECTION_UNKNOWN, it, SECTIONS.joinToString(", ")) }
        titles.filter { it in SECTIONS }.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { problem(RULE_SECTION_REPEATED, it, titles.count { t -> t == it }.toString()) }
        val known = titles.filter { it in SECTIONS }.distinct()
        if (known.zipWithNext().any { (a, b) -> SECTIONS.indexOf(a) > SECTIONS.indexOf(b) }) problem(RULE_SECTION_ORDER, SECTIONS.joinToString(", ") { "« ## $it »" })
        parsed.sections.filter { (title, text) -> title in SECTIONS && text.isBlank() }.map { it.first }.distinct().forEach { problem(RULE_SECTION_EMPTY, it) }

        if (OUTLINE in titles) {
            parsed.malformedSteps.forEach { problem(RULE_STEP, it) }
            if (parsed.steps.isEmpty()) {
                problem(RULE_NO_STEP)
            } else if (durationMinutes != null && parsed.steps.sumOf { it.minutes } != durationMinutes) {
                problem(RULE_DURATION, durationMinutes.toString(), parsed.steps.sumOf { it.minutes }.toString())
            }
        }
        if (DEBATE in titles && parsed.objections.size < MIN_OBJECTIONS) problem(RULE_OBJECTIONS, MIN_OBJECTIONS.toString(), parsed.objections.size.toString())
        return problems
    }

    /** The lines of [lines] outside every `:::` block. */
    private fun outsideBlocks(lines: List<String>): List<String> {
        val kept = mutableListOf<String>()
        var depth = 0
        for (line in lines) {
            val trimmed = line.trimEnd()
            when {
                BLOCK_START.matches(trimmed) -> depth++
                BLOCK_END.matches(trimmed) && depth > 0 -> depth--
                depth == 0 -> kept += line
            }
        }
        return kept
    }

    private const val SPACE: String = "[\\s\\u00A0\\u202F]"
    private val HEADING = Regex("^##$SPACE+(.+)$")

    /** A top-level list item (no indentation): in the outline, each is a timed step. */
    private val STEP_START = Regex("^[-*]$SPACE")
    private val STEP = Regex("^[-*]$SPACE+(\\d{1,3})$SPACE*min$SPACE*:$SPACE*(.+)$")
    private val BLOCK_START = Regex("^:::\\s*([a-z-]+)\\s*$")
    private val BLOCK_END = Regex("^:::\\s*$")
}
