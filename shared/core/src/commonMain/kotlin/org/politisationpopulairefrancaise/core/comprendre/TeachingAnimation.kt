package org.politisationpopulairefrancaise.core.comprendre

/**
 * The slides of the animation mode of a teaching sheet (CO-24, step 2; docs/integration/comprendre-enseignants.md,
 * section 6): what a group leader projects, one slide at a time, in large type. The questions are those of the
 * linked course (never rewritten), the objections those of the guided debate of the sheet. An answer appears only
 * when the leader asks for it. Nothing is recorded: the slides are built in memory from the sheet and its course.
 */
sealed interface AnimationSlide {
    /** The opening slide: the title of the sheet and what follows. */
    data class Opening(val title: String, val courseTitle: String?, val questions: Int, val objections: Int) : AnimationSlide

    /** A question of the course: [number] from 1 to [total]. Its answer is hidden until revealed. */
    data class Question(val number: Int, val total: Int, val quiz: QuizQuestion) : AnimationSlide

    /** The question put to the group for the guided debate (the first paragraph of the section « Débat guidé »). */
    data class Debate(val text: String) : AnimationSlide

    /** An objection of the debate: [number] from 1 to [total]. Its sourced answer is hidden until revealed. */
    data class DebateObjection(val number: Int, val total: Int, val objection: Objection) : AnimationSlide

    /** The closing slide. */
    data object Closing : AnimationSlide

    /** True for a slide with an answer hidden at first. */
    val hasAnswer: Boolean get() = this is Question || this is DebateObjection
}

/** Builds the slides of a sheet. */
object TeachingAnimation {
    /**
     * The opening, the questions of [course] (none when the course is not published), the debate and its objections
     * when the sheet has them, then the closing.
     */
    fun slidesOf(sheet: TeachingSheet, course: Parcours?, courseTitle: String? = course?.article?.title): List<AnimationSlide> {
        val quizzes = course?.steps?.mapNotNull { it.quiz }.orEmpty()
        val debate = debateQuestion(sheet.debate)
        return buildList {
            add(AnimationSlide.Opening(sheet.article.title, courseTitle, quizzes.size, sheet.objections.size))
            quizzes.forEachIndexed { index, quiz -> add(AnimationSlide.Question(index + 1, quizzes.size, quiz)) }
            if (debate != null) add(AnimationSlide.Debate(debate))
            sheet.objections.forEachIndexed { index, objection -> add(AnimationSlide.DebateObjection(index + 1, sheet.objections.size, objection)) }
            add(AnimationSlide.Closing)
        }
    }

    /** The first paragraph of the section « Débat guidé » (the question put to the group); null when empty. */
    fun debateQuestion(debate: String): String? =
        debate.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { it.trim() }.firstOrNull { it.isNotEmpty() }
}

/**
 * Where the animation stands: the current slide and whether its answer is shown. Immutable: each gesture gives a new
 * state. « Next » first reveals a hidden answer, then moves on; « previous » goes back to the previous slide with its
 * answer hidden again.
 */
data class AnimationState(val slides: List<AnimationSlide>, val index: Int = 0, val revealed: Boolean = false) {
    init {
        require(slides.isNotEmpty()) { "an animation has at least one slide" }
        require(index in slides.indices) { "slide $index out of ${slides.size}" }
    }

    val current: AnimationSlide get() = slides[index]

    /** The position shown to the group, from 1. */
    val position: Int get() = index + 1

    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == slides.lastIndex

    /** True when the current slide has an answer still hidden. */
    val answerHidden: Boolean get() = current.hasAnswer && !revealed

    fun reveal(): AnimationState = if (current.hasAnswer) copy(revealed = true) else this

    fun next(): AnimationState = when {
        answerHidden -> reveal()
        isLast -> this
        else -> copy(index = index + 1, revealed = false)
    }

    fun previous(): AnimationState = if (isFirst) copy(revealed = false) else copy(index = index - 1, revealed = false)
}
