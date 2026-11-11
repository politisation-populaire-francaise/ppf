package org.politisationpopulairefrancaise.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.components.ButtonPair
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.tutorial_act_soon
import org.politisationpopulairefrancaise.ui.resources.tutorial_act_text
import org.politisationpopulairefrancaise.ui.resources.tutorial_act_title
import org.politisationpopulairefrancaise.ui.resources.tutorial_decide_text
import org.politisationpopulairefrancaise.ui.resources.tutorial_decide_title
import org.politisationpopulairefrancaise.ui.resources.tutorial_gather_soon
import org.politisationpopulairefrancaise.ui.resources.tutorial_gather_text
import org.politisationpopulairefrancaise.ui.resources.tutorial_gather_title
import org.politisationpopulairefrancaise.ui.resources.tutorial_help_soon
import org.politisationpopulairefrancaise.ui.resources.tutorial_help_text
import org.politisationpopulairefrancaise.ui.resources.tutorial_help_title
import org.politisationpopulairefrancaise.ui.resources.tutorial_home_text
import org.politisationpopulairefrancaise.ui.resources.tutorial_home_title
import org.politisationpopulairefrancaise.ui.resources.tutorial_next
import org.politisationpopulairefrancaise.ui.resources.tutorial_origin
import org.politisationpopulairefrancaise.ui.resources.tutorial_previous
import org.politisationpopulairefrancaise.ui.resources.tutorial_skip
import org.politisationpopulairefrancaise.ui.resources.tutorial_skip_description
import org.politisationpopulairefrancaise.ui.resources.tutorial_soon
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_act
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_act_description
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_ally
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_ally_description
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_opinion
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_opinion_description
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_prompt
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_title
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_understand
import org.politisationpopulairefrancaise.ui.resources.tutorial_start_understand_description
import org.politisationpopulairefrancaise.ui.resources.tutorial_step
import org.politisationpopulairefrancaise.ui.resources.tutorial_why_text
import org.politisationpopulairefrancaise.ui.resources.tutorial_why_title
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the tutorial, also useful for accessibility tooling. */
object TutorialTags {
    const val SCREEN = "tutorial"
    const val SKIP = "tutorial-skip"
    const val NEXT = "tutorial-next"
    const val PREVIOUS = "tutorial-previous"
    const val STEP = "tutorial-step"
    const val TITLE = "tutorial-title"
    const val SOON = "tutorial-soon"

    /** « Revoir la présentation de l'application », in Mon espace and in the AIDE sheet. */
    const val REPLAY = "tutorial-replay"

    /** One of the four buttons of the last screen. */
    fun start(choice: TutorialStart): String = "tutorial-start-${choice.id}"
}

/**
 * The screens of the tutorial, in the order fixed by the owner (D-216): why, privacy, Agir with the
 * map, gathering, deciding, the AIDE button, then « Je veux… ». [soon] lists what the screen
 * mentions but is not built yet: it is shown in a « Bientôt » box, never as available.
 */
enum class TutorialStep(val title: StringResource, val text: StringResource?, val icon: ImageVector, val soon: StringResource? = null) {
    WHY(Res.string.tutorial_why_title, Res.string.tutorial_why_text, Lucide.Megaphone),
    HOME(Res.string.tutorial_home_title, Res.string.tutorial_home_text, Lucide.ShieldCheck),
    ACT(Res.string.tutorial_act_title, Res.string.tutorial_act_text, Lucide.Map, Res.string.tutorial_act_soon),
    GATHER(Res.string.tutorial_gather_title, Res.string.tutorial_gather_text, Lucide.UsersRound, Res.string.tutorial_gather_soon),
    DECIDE(Res.string.tutorial_decide_title, Res.string.tutorial_decide_text, Lucide.Vote),
    HELP(Res.string.tutorial_help_title, Res.string.tutorial_help_text, Lucide.LifeBuoy, Res.string.tutorial_help_soon),
    START(Res.string.tutorial_start_title, null, Lucide.HandFist),
}

/**
 * The four buttons under « Je veux… » (D-216) and where they lead. « m'allier » opens Agir, whose
 * home lists the organisations and the collectives, until Agir offers to open that rubric directly.
 */
enum class TutorialStart(val id: String, val label: StringResource, val description: StringResource, val space: Space) {
    ACT("agir", Res.string.tutorial_start_act, Res.string.tutorial_start_act_description, Space.AGIR),
    OPINION("avis", Res.string.tutorial_start_opinion, Res.string.tutorial_start_opinion_description, Space.DECIDER),
    UNDERSTAND("comprendre", Res.string.tutorial_start_understand, Res.string.tutorial_start_understand_description, Space.COMPRENDRE),
    ALLY("allier", Res.string.tutorial_start_ally, Res.string.tutorial_start_ally_description, Space.AGIR),
}

/**
 * The tutorial of the first launch (D-211, D-216): one idea per screen, under a minute in all,
 * « Passer » always visible, readable without network (everything is in the application), usable
 * with a screen reader and a keyboard, on the three formats. [onFinish] receives the space chosen
 * on the last screen, or null when the person skips it (the shell then opens « En direct »).
 * The back gesture goes to the previous screen, and leaves the tutorial from the first one.
 */
@Composable
fun FirstLaunchTutorial(onFinish: (Space?) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val steps = TutorialStep.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    SystemBack(enabled = true) { if (index > 0) index-- else onFinish(null) }
    val paneTitle = stringResource(step.title)
    Surface(
        color = palette.surface1,
        contentColor = palette.ink,
        modifier = modifier.fillMaxSize().semantics { this.paneTitle = paneTitle }.testTag(TutorialTags.SCREEN),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The counter and « Passer », always at the top, whatever the length of the text.
            Row(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.tutorial_step, index + 1, steps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.inkMuted,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }.testTag(TutorialTags.STEP),
                )
                val skip = stringResource(Res.string.tutorial_skip_description)
                PpfTextButton(
                    onClick = { onFinish(null) },
                    text = stringResource(Res.string.tutorial_skip),
                    modifier = Modifier.heightIn(min = PpfTheme.dimens.touchTarget).semantics { contentDescription = skip }.testTag(TutorialTags.SKIP),
                )
            }
            ProgressDots(index, steps.size)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val low = maxHeight < 420.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = if (low) 8.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        StepContent(step, compactIcon = low, onFinish = onFinish)
                    }
                }
            }
            // « Précédent » and « Suivant »; the last screen ends with its four choices instead.
            Row(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    PpfSecondaryButton(onClick = { index-- }, text = stringResource(Res.string.tutorial_previous), modifier = Modifier.testTag(TutorialTags.PREVIOUS))
                }
                Spacer(Modifier.weight(1f))
                if (index < steps.lastIndex) {
                    PpfPrimaryButton(onClick = { index++ }, text = stringResource(Res.string.tutorial_next), modifier = Modifier.testTag(TutorialTags.NEXT))
                }
            }
        }
    }
}

@Composable
private fun StepContent(step: TutorialStep, compactIcon: Boolean, onFinish: (Space?) -> Unit) {
    val palette = PpfTheme.palette
    Box(
        Modifier.size(if (compactIcon) 56.dp else 96.dp).background(palette.brandTint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(step.icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(if (compactIcon) 28.dp else 48.dp))
    }
    Spacer(Modifier.height(if (compactIcon) 12.dp else 24.dp))
    Text(
        text = stringResource(step.title),
        style = MaterialTheme.typography.headlineSmall,
        color = palette.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }.testTag(TutorialTags.TITLE),
    )
    step.text?.let {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, textAlign = TextAlign.Center)
    }
    step.soon?.let {
        Spacer(Modifier.height(16.dp))
        Callout(CalloutType.CONSEIL, title = stringResource(Res.string.tutorial_soon), modifier = Modifier.testTag(TutorialTags.SOON)) {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (step == TutorialStep.START) {
        Spacer(Modifier.height(20.dp))
        StartChoices(onFinish)
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(Res.string.tutorial_origin), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, textAlign = TextAlign.Center)
}

/** « Je veux… » above the four buttons, without « je veux » in them (the owner's wording, D-216). */
@Composable
private fun StartChoices(onFinish: (Space?) -> Unit) {
    Text(
        text = stringResource(Res.string.tutorial_start_prompt),
        style = MaterialTheme.typography.titleMedium,
        color = PpfTheme.palette.ink,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    // Two by two, each pair one above the other when a label would not fit whole (360 dp, large font).
    TutorialStart.entries.chunked(2).forEach { (first, second) ->
        ButtonPair(
            first = { StartButton(first, onFinish) },
            second = { StartButton(second, onFinish) },
            modifier = Modifier.fillMaxWidth(),
            spacing = 12.dp,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StartButton(choice: TutorialStart, onFinish: (Space?) -> Unit) {
    val description = stringResource(choice.description)
    PpfPrimaryButton(
        onClick = { onFinish(choice.space) },
        text = stringResource(choice.label),
        modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = description }.testTag(TutorialTags.start(choice)),
    )
}

/** Where the person is in the tutorial, for the eyes (the counter above says it in words). */
@Composable
private fun ProgressDots(index: Int, count: Int) {
    val palette = PpfTheme.palette
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.size(width = if (i == index) 20.dp else 8.dp, height = 8.dp)
                    .background(if (i <= index) palette.brand else palette.border, CircleShape),
            )
        }
    }
}
