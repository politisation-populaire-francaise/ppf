package org.politisationpopulairefrancaise.ui.comprendre

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.comprendre.AnimationSlide
import org.politisationpopulairefrancaise.core.comprendre.AnimationState
import org.politisationpopulairefrancaise.core.comprendre.Parcours
import org.politisationpopulairefrancaise.core.comprendre.QuizQuestion
import org.politisationpopulairefrancaise.core.comprendre.TeachingAnimation
import org.politisationpopulairefrancaise.core.comprendre.TeachingSheet
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_answer
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_close
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_debate
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_end
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_end_text
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_keys
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_next
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_objection
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_opening
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_position
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_previous
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_question
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_reply
import org.politisationpopulairefrancaise.ui.resources.comprendre_animation_reveal
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the animation mode of a teaching sheet (CO-24, step 2). */
object AnimationTags {
    const val START = "teaching-animation-start"
    const val ROOT = "teaching-animation"
    const val POSITION = "teaching-animation-position"
    const val SLIDE = "teaching-animation-slide"
    const val ANSWER = "teaching-animation-answer"
    const val REVEAL = "teaching-animation-reveal"
    const val NEXT = "teaching-animation-next"
    const val PREVIOUS = "teaching-animation-previous"
    const val CLOSE = "teaching-animation-close"
}

/**
 * The animation mode of a teaching sheet (CO-24, step 2): one slide at a time, in large type, to project for a
 * group ([TeachingAnimation]: the questions of the course, then the guided debate and its objections). The answer
 * of a question or of an objection appears only when the leader asks for it. Arrows or Page Up and Page Down (a
 * presentation clicker) move between slides, Escape leaves. Offline, without an account, and nothing is recorded:
 * the position lives only in memory and is lost when the mode is left.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeachingAnimationView(sheet: TeachingSheet, course: Parcours?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val slides = remember(sheet.article.event.id, course?.address) { TeachingAnimation.slidesOf(sheet, course, course?.let { displayTitle(it) }) }
    var state by remember(slides) { mutableStateOf(AnimationState(slides)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val palette = PpfTheme.palette
    Column(
        modifier
            .fillMaxSize()
            .background(palette.surface0)
            .padding(24.dp)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.PageDown -> {
                        state = state.next()
                        true
                    }
                    Key.DirectionLeft, Key.PageUp -> {
                        state = state.previous()
                        true
                    }
                    Key.Escape -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .testTag(AnimationTags.ROOT),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.comprendre_animation_position, state.position, slides.size),
                style = MaterialTheme.typography.labelLarge,
                color = palette.inkMuted,
                modifier = Modifier.weight(1f).testTag(AnimationTags.POSITION).semantics { liveRegion = LiveRegionMode.Polite },
            )
            PpfTextButton(onClick = onClose, text = stringResource(Res.string.comprendre_animation_close), modifier = Modifier.testTag(AnimationTags.CLOSE))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp).testTag(AnimationTags.SLIDE),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Column(Modifier.widthIn(max = 1100.dp)) {
                when (val slide = state.current) {
                    is AnimationSlide.Opening -> OpeningSlide(slide)
                    is AnimationSlide.Question -> QuestionSlide(slide, state.revealed)
                    is AnimationSlide.Debate -> {
                        Kicker(stringResource(Res.string.comprendre_animation_debate))
                        Text(slide.text, style = MaterialTheme.typography.headlineMedium)
                    }
                    is AnimationSlide.DebateObjection -> {
                        Kicker(stringResource(Res.string.comprendre_animation_objection, slide.number, slide.total))
                        Text(slide.objection.objection, style = MaterialTheme.typography.headlineMedium)
                        if (state.revealed) {
                            Spacer(Modifier.height(24.dp))
                            Answer(stringResource(Res.string.comprendre_animation_reply), slide.objection.answer)
                        }
                    }
                    AnimationSlide.Closing -> {
                        Text(stringResource(Res.string.comprendre_animation_end), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(Res.string.comprendre_animation_end_text), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(24.dp))
                        PpfPrimaryButton(onClick = onClose, text = stringResource(Res.string.comprendre_animation_close))
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSecondaryButton(
                onClick = { state = state.previous() },
                text = stringResource(Res.string.comprendre_animation_previous),
                enabled = !state.isFirst,
                modifier = Modifier.testTag(AnimationTags.PREVIOUS),
            )
            if (state.answerHidden) {
                PpfPrimaryButton(onClick = { state = state.reveal() }, text = stringResource(Res.string.comprendre_animation_reveal), modifier = Modifier.testTag(AnimationTags.REVEAL))
            } else {
                PpfPrimaryButton(
                    onClick = { state = state.next() },
                    text = stringResource(Res.string.comprendre_animation_next),
                    enabled = !state.isLast,
                    modifier = Modifier.testTag(AnimationTags.NEXT),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.comprendre_animation_keys), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = PpfTheme.palette.brand, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OpeningSlide(slide: AnimationSlide.Opening) {
    Text(slide.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
    slide.courseTitle?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = MaterialTheme.typography.titleLarge, color = PpfTheme.palette.inkMuted)
    }
    Spacer(Modifier.height(24.dp))
    Text(stringResource(Res.string.comprendre_animation_opening, slide.questions, slide.objections), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun QuestionSlide(slide: AnimationSlide.Question, revealed: Boolean) {
    val quiz: QuizQuestion = slide.quiz
    Kicker(stringResource(Res.string.comprendre_animation_question, slide.number, slide.total))
    Text(quiz.question, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(20.dp))
    quiz.options.forEachIndexed { index, option ->
        Text("${'A' + index}. $option", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 6.dp))
    }
    if (revealed) {
        Spacer(Modifier.height(24.dp))
        val right = quiz.options.getOrElse(quiz.correctIndex) { "" }
        Answer(stringResource(Res.string.comprendre_animation_answer, "${'A' + quiz.correctIndex}. $right"), quiz.explanation)
    }
}

/** A revealed answer: said in words first (its colour only repeats them), announced to screen readers. */
@Composable
private fun Answer(title: String, text: String) {
    val palette = PpfTheme.palette
    Surface(
        Modifier.fillMaxWidth().testTag(AnimationTags.ANSWER).semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = palette.successTint,
        border = BorderStroke(1.dp, palette.success),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
