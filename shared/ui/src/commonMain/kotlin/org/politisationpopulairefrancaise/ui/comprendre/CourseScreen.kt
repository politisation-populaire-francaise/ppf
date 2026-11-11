package org.politisationpopulairefrancaise.ui.comprendre

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.comprendre.ParcoursProgress
import org.politisationpopulairefrancaise.core.comprendre.ParcoursStep
import org.politisationpopulairefrancaise.core.comprendre.QuizAnswer
import org.politisationpopulairefrancaise.core.comprendre.QuizQuestion
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.content.FeedPost
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.content.ObjectionsBox
import org.politisationpopulairefrancaise.ui.content.SimpleMarkdown
import org.politisationpopulairefrancaise.ui.glossaire.GlossaryScope
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_back
import org.politisationpopulairefrancaise.ui.resources.comprendre_course_kind
import org.politisationpopulairefrancaise.ui.resources.comprendre_daily_note
import org.politisationpopulairefrancaise.ui.resources.comprendre_teaching_link
import org.politisationpopulairefrancaise.ui.resources.comprendre_workshop_link
import org.politisationpopulairefrancaise.ui.resources.comprendre_end_score
import org.politisationpopulairefrancaise.ui.resources.comprendre_end_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_end_unanswered
import org.politisationpopulairefrancaise.ui.resources.comprendre_finish
import org.politisationpopulairefrancaise.ui.resources.comprendre_next
import org.politisationpopulairefrancaise.ui.resources.comprendre_next_actions
import org.politisationpopulairefrancaise.ui.resources.comprendre_previous
import org.politisationpopulairefrancaise.ui.resources.comprendre_private_note
import org.politisationpopulairefrancaise.ui.resources.comprendre_progress_label
import org.politisationpopulairefrancaise.ui.resources.comprendre_quiz_correct_option
import org.politisationpopulairefrancaise.ui.resources.comprendre_quiz_retry
import org.politisationpopulairefrancaise.ui.resources.comprendre_quiz_right
import org.politisationpopulairefrancaise.ui.resources.comprendre_quiz_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_quiz_validate
import org.politisationpopulairefrancaise.ui.resources.comprendre_quiz_wrong
import org.politisationpopulairefrancaise.ui.resources.comprendre_quiz_your_answer
import org.politisationpopulairefrancaise.ui.resources.comprendre_restart
import org.politisationpopulairefrancaise.ui.resources.comprendre_resume
import org.politisationpopulairefrancaise.ui.resources.comprendre_review
import org.politisationpopulairefrancaise.ui.resources.comprendre_sources
import org.politisationpopulairefrancaise.ui.resources.comprendre_start
import org.politisationpopulairefrancaise.ui.resources.comprendre_step_of
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object CourseTags {
    const val COURSE = "course-detail"
    const val BACK = "course-back"
    const val START = "course-start"
    const val RESTART = "course-restart"
    const val NEXT = "course-next"
    const val PREVIOUS = "course-previous"
    const val STEP_TITLE = "course-step-title"
    const val DAILY_NOTE = "course-daily-note"
    const val TEACHING = "course-teaching-sheet"
    const val WORKSHOP = "course-workshop-sheet"
    const val PROGRESS = "course-progress"
    const val QUIZ = "course-quiz"
    const val VALIDATE = "course-quiz-validate"
    const val FEEDBACK = "course-quiz-feedback"
    const val RETRY = "course-quiz-retry"
    const val END = "course-end"
    const val SCORE = "course-score"
    const val NEXT_ACTIONS = "course-next-actions"
    const val REVIEW = "course-review"

    fun option(index: Int): String = "course-quiz-option-$index"

    fun action(spaceId: String): String = "course-action-$spaceId"
}

/**
 * An open course: its introduction, then one step per page with its quiz, then the end page with
 * the result, « Et maintenant ? » and the sources. [onBack] is null in the two-pane layout, where
 * the home stays visible on the left.
 */
@Composable
fun CourseScreen(
    comprendre: ComprendreLibrary,
    parcours: Parcours,
    onBack: (() -> Unit)?,
    onNavigate: (Space) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val page = comprendre.page
    val progress = comprendre.progressOf(parcours)
    val scroll = rememberScrollState()
    // Each page starts at its top.
    LaunchedEffect(parcours.address, page) { scroll.scrollTo(0) }
    Column(modifier.verticalScroll(scroll).padding(if (compact) 16.dp else 24.dp).testTag(CourseTags.COURSE)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.comprendre_back), onBack, Modifier.testTag(CourseTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PpfBadge(stringResource(Res.string.comprendre_course_kind), BadgeStyle.BRAND)
                if (parcours.article.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                    Spacer(Modifier.width(8.dp))
                    ExampleBadge()
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = displayTitle(parcours),
                style = if (page == CoursePage.Introduction) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(courseMetaLine(parcours), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            Spacer(Modifier.height(16.dp))
            // One page of the course is one content for the glossary: each term is linked once in it.
            GlossaryScope(key = parcours.address to page) {
                when (page) {
                    CoursePage.Introduction -> IntroductionPage(comprendre, parcours, progress)
                    is CoursePage.Step -> parcours.steps.getOrNull(page.index)?.let { step -> StepPage(comprendre, parcours, step, progress) }
                    CoursePage.End -> EndPage(comprendre, parcours, progress, onNavigate, onBack)
                }
            }
        }
    }
}

@Composable
private fun IntroductionPage(comprendre: ComprendreLibrary, parcours: Parcours, progress: ParcoursProgress?) {
    parcours.article.summary?.let {
        Text(it, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
    }
    if (parcours.introduction.isNotBlank()) {
        SelectionContainer { MarkdownText(parcours.introduction) }
        Spacer(Modifier.height(8.dp))
    }
    ProgressSummary(parcours, progress)
    Spacer(Modifier.height(16.dp))
    val resumable = progress != null && !progress.completed && progress.furthestStep > 0
    Buttons {
        PpfPrimaryButton(
            onClick = { comprendre.start(resume = true) },
            text = if (resumable) stringResource(Res.string.comprendre_resume, progress!!.furthestStep + 1) else stringResource(Res.string.comprendre_start),
            modifier = Modifier.testTag(CourseTags.START),
            icon = Lucide.ChevronRight,
        )
        if (progress != null) {
            PpfSecondaryButton(onClick = comprendre::restart, text = stringResource(Res.string.comprendre_restart), modifier = Modifier.testTag(CourseTags.RESTART), icon = Lucide.Repeat2)
        }
    }
    // « Pour la classe » (CO-13) and « Pour un atelier » (CO-24): the sheets of this course, for a teacher or a group leader.
    comprendre.teachingSheetsOf(parcours).forEach { sheet ->
        Spacer(Modifier.height(8.dp))
        PpfTextButton(
            onClick = { comprendre.openArticle(sheet.article) },
            text = if (sheet.isWorkshop) {
                stringResource(Res.string.comprendre_workshop_link, sheet.article.durationMinutes ?: sheet.totalMinutes)
            } else {
                stringResource(Res.string.comprendre_teaching_link, teachingLevelLabel(sheet.level))
            },
            modifier = Modifier.testTag(if (sheet.isWorkshop) CourseTags.WORKSHOP else CourseTags.TEACHING),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(Res.string.comprendre_private_note), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    // Licence, verification code and « Citer ce contenu » of the course (D-103, S-141, S-144).
    Spacer(Modifier.height(16.dp))
    ComprendreCite(parcours.article)
}

@Composable
private fun StepPage(comprendre: ComprendreLibrary, parcours: Parcours, step: ParcoursStep, progress: ParcoursProgress?) {
    StepIndicator(step.index, parcours.steps.size)
    Spacer(Modifier.height(16.dp))
    Text(
        text = step.title,
        style = MaterialTheme.typography.titleLarge,
        color = PpfTheme.palette.brand,
        modifier = Modifier.semantics { heading() }.testTag(CourseTags.STEP_TITLE),
    )
    Spacer(Modifier.height(12.dp))
    if (step.body.isNotBlank()) SelectionContainer { MarkdownText(step.body) }
    step.quiz?.let { quiz ->
        Spacer(Modifier.height(12.dp))
        QuizCard(
            quiz = quiz,
            answer = progress?.answers?.get(step.index),
            onAnswer = { choice -> comprendre.answer(step.index, choice) },
            onRetry = { comprendre.retry(step.index) },
            key = "${parcours.address}#${step.index}",
        )
    }
    // « Je découvre » (CO-19): once the lesson is read, a suggestion to come back tomorrow, never a lock.
    val lessonDone = step.quiz == null || progress?.answers?.containsKey(step.index) == true
    if (parcours.daily && lessonDone && step.index < parcours.steps.lastIndex) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.comprendre_daily_note, step.index + 2),
            style = MaterialTheme.typography.bodyMedium,
            color = PpfTheme.palette.inkMuted,
            modifier = Modifier.testTag(CourseTags.DAILY_NOTE),
        )
    }
    Spacer(Modifier.height(20.dp))
    Buttons {
        val last = step.index == parcours.steps.lastIndex
        PpfPrimaryButton(
            onClick = comprendre::next,
            text = stringResource(if (last) Res.string.comprendre_finish else Res.string.comprendre_next),
            modifier = Modifier.testTag(CourseTags.NEXT),
            icon = if (last) Lucide.Check else Lucide.ChevronRight,
        )
        PpfSecondaryButton(onClick = comprendre::previous, text = stringResource(Res.string.comprendre_previous), modifier = Modifier.testTag(CourseTags.PREVIOUS), icon = Lucide.ArrowLeft)
    }
}

/** "Étape 2 sur 4" and a bar, announced as one progress indicator by screen readers. */
@Composable
private fun StepIndicator(index: Int, count: Int) {
    val label = stringResource(Res.string.comprendre_step_of, index + 1, count)
    val description = stringResource(Res.string.comprendre_progress_label)
    Column(Modifier.fillMaxWidth().testTag(CourseTags.PROGRESS)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = PpfTheme.palette.inkMuted)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / count },
            modifier = Modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = "$description : $label" },
            color = PpfTheme.palette.brand,
            trackColor = PpfTheme.palette.border,
            drawStopIndicator = {},
        )
    }
}

/**
 * The quiz of a step. Before answering: the options as radio buttons and a button to validate.
 * After: each option says whether it is the right answer or the reader's, the result is announced
 * (polite live region) with its explanation, and the reader can try again after a wrong answer.
 */
@Composable
fun QuizCard(quiz: QuizQuestion, answer: QuizAnswer?, onAnswer: (Int) -> Unit, onRetry: () -> Unit, key: String) {
    val palette = PpfTheme.palette
    var choice by remember(key, answer) { mutableStateOf(answer?.choice) }
    PpfCard(Modifier.fillMaxWidth().testTag(CourseTags.QUIZ)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.ListChecks, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.comprendre_quiz_title), style = MaterialTheme.typography.labelLarge, color = palette.brand)
        }
        Spacer(Modifier.height(8.dp))
        Text(plain(quiz.question), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        if (answer == null) {
            Column(Modifier.selectableGroup()) {
                quiz.options.forEachIndexed { index, option ->
                    PpfRadioRow(
                        label = plain(option),
                        selected = choice == index,
                        onClick = { choice = index },
                        modifier = Modifier.testTag(CourseTags.option(index)),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PpfPrimaryButton(
                onClick = { choice?.let(onAnswer) },
                text = stringResource(Res.string.comprendre_quiz_validate),
                enabled = choice != null,
                modifier = Modifier.testTag(CourseTags.VALIDATE),
            )
        } else {
            quiz.options.forEachIndexed { index, option ->
                AnsweredOption(
                    text = plain(option),
                    correct = index == quiz.correctIndex,
                    chosen = index == answer.choice,
                    modifier = Modifier.testTag(CourseTags.option(index)),
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(CourseTags.FEEDBACK)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (answer.correct) Lucide.CircleCheck else Lucide.X,
                        contentDescription = null,
                        tint = if (answer.correct) palette.success else palette.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (answer.correct) Res.string.comprendre_quiz_right else Res.string.comprendre_quiz_wrong),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (answer.correct) palette.success else palette.accentStrong,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(SimpleMarkdown.inline(quiz.explanation), style = MaterialTheme.typography.bodyMedium)
            }
            if (!answer.correct) {
                Spacer(Modifier.height(8.dp))
                PpfTextButton(onClick = onRetry, text = stringResource(Res.string.comprendre_quiz_retry), modifier = Modifier.testTag(CourseTags.RETRY))
            }
        }
    }
}

/** An option once answered: no longer selectable, it says whether it is the right answer and whether the reader chose it. */
@Composable
private fun AnsweredOption(text: String, correct: Boolean, chosen: Boolean, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val icon: ImageVector? = when {
        correct -> Lucide.CircleCheck
        chosen -> Lucide.X
        else -> null
    }
    val tint = if (correct) palette.success else palette.accent
    val labels = buildList {
        if (correct) add(stringResource(Res.string.comprendre_quiz_correct_option))
        if (chosen) add(stringResource(Res.string.comprendre_quiz_your_answer))
    }
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) else Spacer(Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            if (labels.isNotEmpty()) {
                Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (correct) palette.success else palette.accentStrong)
            }
        }
    }
}

@Composable
private fun EndPage(comprendre: ComprendreLibrary, parcours: Parcours, progress: ParcoursProgress?, onNavigate: (Space) -> Unit, onBack: (() -> Unit)?) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(CourseTags.END)) {
        Text(stringResource(Res.string.comprendre_end_title), style = MaterialTheme.typography.titleLarge, color = palette.brand, modifier = Modifier.semantics { heading() })
        if (parcours.quizCount > 0) {
            Spacer(Modifier.height(8.dp))
            val right = progress?.correctCount(parcours.steps.size) ?: 0
            Text(
                stringResource(Res.string.comprendre_end_score, right, parcours.quizCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(CourseTags.SCORE),
            )
            val answered = progress?.answeredCount(parcours.steps.size) ?: 0
            if (answered < parcours.quizCount) {
                Text(stringResource(Res.string.comprendre_end_unanswered), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
        }
        if (parcours.objections.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            ObjectionsBox(parcours.objections)
        }
        parcours.nextActions?.let { actions ->
            Spacer(Modifier.height(20.dp))
            NextActionsCard(actions, onNavigate)
        }
        parcours.sources?.let { sources ->
            Spacer(Modifier.height(20.dp))
            Text(stringResource(Res.string.comprendre_sources), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            SelectionContainer { MarkdownText(sources, glossary = false) }
        }
        Spacer(Modifier.height(20.dp))
        Buttons {
            PpfSecondaryButton(onClick = comprendre::review, text = stringResource(Res.string.comprendre_review), modifier = Modifier.testTag(CourseTags.REVIEW), icon = Lucide.Repeat2)
            if (onBack != null) PpfTextButton(onClick = onBack, text = stringResource(Res.string.comprendre_back))
        }
    }
}

/**
 * « Et maintenant ? » (cahier fonctionnel, section 3): the concrete actions proposed at the end.
 * A bullet made of a single link to a space of the application (`[texte](ppf:revendications)`)
 * becomes a button; any other bullet is shown as text (links to other sites are shown, never opened).
 */
@Composable
internal fun NextActionsCard(markdown: String, onNavigate: (Space) -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(CourseTags.NEXT_ACTIONS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Megaphone, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.comprendre_next_actions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(8.dp))
        NextAction.parse(markdown).forEach { action ->
            when (action) {
                is NextAction.Open -> ActionLink(
                    label = action.label,
                    onClick = { onNavigate(action.space) },
                    modifier = Modifier.padding(vertical = 4.dp).testTag(CourseTags.action(action.space.id)),
                )
                is NextAction.Text -> Row(Modifier.padding(vertical = 4.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = palette.brand)
                    Spacer(Modifier.width(8.dp))
                    Text(SimpleMarkdown.inline(action.text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** An action that opens a space of the application: an outlined row whose label wraps on narrow panes. */
@Composable
private fun ActionLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
        shape = MaterialTheme.shapes.small,
        color = palette.surface2,
        contentColor = palette.ink,
        border = BorderStroke(1.dp, palette.borderStrong),
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = palette.brand, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
        }
    }
}

/** One line of « Et maintenant ? ». */
sealed interface NextAction {
    data class Open(val label: String, val space: Space) : NextAction

    data class Text(val text: String) : NextAction

    companion object {
        private val BULLET = Regex("^[-*]\\s+(.*)$")
        private val SPACE_LINK = Regex("^\\[([^\\]]+)]\\(ppf:([a-z-]+)\\)\\s*\\.?$")

        fun parse(markdown: String): List<NextAction> = markdown.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@mapNotNull null
            val text = BULLET.matchEntire(line)?.groupValues?.get(1)?.trim() ?: line
            val link = SPACE_LINK.matchEntire(text)
            val space = link?.groupValues?.get(2)?.let(Space::fromId)
            if (link != null && space != null) Open(link.groupValues[1].trim(), space) else Text(text)
        }
    }
}

@Composable
private fun ProgressSummary(parcours: Parcours, progress: ParcoursProgress?) {
    Text(progressLine(parcours, progress), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** The text of a quiz line without its Markdown marks (questions and options are short plain texts). */
private fun plain(text: String): String = SimpleMarkdown.inline(text).text

internal fun displayTitle(parcours: Parcours): String = parcours.article.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim()
