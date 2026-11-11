package org.politisationpopulairefrancaise.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.agir.AgirRoute
import org.politisationpopulairefrancaise.ui.components.ButtonPair
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.Callout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
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
    const val ILLUSTRATION = "tutorial-illustration"

    /** « Revoir la présentation de l'application », in Mon espace and in the AIDE sheet. */
    const val REPLAY = "tutorial-replay"

    /** One of the buttons of the last screen (and « Régler ma confidentialité »). */
    fun start(choice: TutorialStart): String = "tutorial-start-${choice.id}"
}

/** The pictures of the owner shown in the tutorial (design/identite/illustrations), lightened for the application. */
enum class TutorialIllustration(val image: DrawableResource, val description: StringResource, val banner: Boolean) {
    MARIANNE(Res.drawable.tutorial_marianne, Res.string.tutorial_marianne_description, banner = true),
    BONNET(Res.drawable.tutorial_bonnet, Res.string.tutorial_bonnet_description, banner = false),
}

/**
 * The screens of the tutorial, short version in white cards (D-241, 11 November 2026): one idea per
 * screen, about twenty-five words, a unifying tone; the owner's long texts of version 2 (D-186) are
 * kept in docs/ergonomie to be restored. FEATURES shows the seven rubrics as a list of pictograms.
 * [soon] lists what the screen mentions but is not built yet: shown in a « Bientôt » box.
 */
enum class TutorialStep(
    val title: StringResource,
    val text: StringResource?,
    val icon: ImageVector? = null,
    val illustration: TutorialIllustration? = null,
    val soon: StringResource? = null,
) {
    WHY(Res.string.tutorial_why_title, Res.string.tutorial_why_text, illustration = TutorialIllustration.MARIANNE),
    FEATURES(Res.string.tutorial_features_title, null),
    ACT(Res.string.tutorial_act_title, Res.string.tutorial_act_text, icon = Lucide.MapPin, soon = Res.string.tutorial_act_soon),
    UNITE(Res.string.tutorial_unite_title, Res.string.tutorial_unite_text, icon = Lucide.Users, soon = Res.string.tutorial_unite_soon),
    VOICE(Res.string.tutorial_voice_title, Res.string.tutorial_voice_text, illustration = TutorialIllustration.BONNET),
    PRIVACY(Res.string.tutorial_privacy_title, Res.string.tutorial_privacy_text, icon = Lucide.ShieldCheck),
    START(Res.string.tutorial_start_title, null),
}

/** The seven rubrics of the menu (D-240), shown on the second screen: pictogram, name, three words. */
private val tutorialFeatures: List<Triple<ImageVector, StringResource, StringResource>> = listOf(
    Triple(Lucide.Newspaper, Res.string.tutorial_feature_informer, Res.string.tutorial_feature_informer_text),
    Triple(Lucide.BookOpen, Res.string.tutorial_feature_comprendre, Res.string.tutorial_feature_comprendre_text),
    Triple(Lucide.Camera, Res.string.tutorial_feature_temoigner, Res.string.tutorial_feature_temoigner_text),
    Triple(Lucide.Megaphone, Res.string.tutorial_feature_revendiquer, Res.string.tutorial_feature_revendiquer_text),
    Triple(Lucide.Users, Res.string.tutorial_feature_unir, Res.string.tutorial_feature_unir_text),
    Triple(Lucide.Landmark, Res.string.tutorial_feature_politiser, Res.string.tutorial_feature_politiser_text),
    Triple(Lucide.HandFist, Res.string.tutorial_feature_agir, Res.string.tutorial_feature_agir_text),
)

/**
 * Where the buttons of the tutorial lead: a space, sometimes a rubric of "Agir", or the AIDE sheet. The
 * last screen shows « Recevoir de l'aide » on the whole width, then the eight others in the order of
 * the tutorial; « Régler ma confidentialité » is the button of the screen on data.
 */
enum class TutorialStart(
    val id: String,
    val label: StringResource,
    val space: Space?,
    val agirRoute: AgirRoute? = null,
    val opensHelp: Boolean = false,
) {
    HELP("aide", Res.string.tutorial_start_help, space = null, opensHelp = true),
    INFORM("informer", Res.string.tutorial_start_inform, Space.EN_DIRECT),
    UNDERSTAND("comprendre", Res.string.tutorial_start_understand, Space.COMPRENDRE),
    TESTIFY("temoigner", Res.string.tutorial_start_testify, Space.AGIR, AgirRoute.TESTIFY),
    DEMAND("revendiquer", Res.string.tutorial_start_demand, Space.REVENDICATIONS),
    GATHER("collectif", Res.string.tutorial_start_gather, Space.AGIR, AgirRoute.GATHER),
    POLITICS("politiser", Res.string.tutorial_start_politics, Space.AGIR, AgirRoute.ENGAGER),
    ACT("agir", Res.string.tutorial_start_act, Space.AGIR),
    SUPPORT("soutenir", Res.string.tutorial_start_support, Space.AGIR, AgirRoute.SOLIDARITY),
    PRIVACY_SETTINGS("confidentialite", Res.string.tutorial_privacy_settings, Space.MON_ESPACE);

    companion object {
        /** The two columns of the last screen, under « Recevoir de l'aide ». */
        val grid: List<TutorialStart> = listOf(INFORM, UNDERSTAND, TESTIFY, DEMAND, GATHER, POLITICS, ACT, SUPPORT)
    }
}

/**
 * The tutorial of the first launch (D-211, D-216, D-186): one subject per screen, « Passer » always
 * visible, readable without network (everything is in the application), usable with a screen reader
 * and a keyboard, on the three formats. Long texts scroll under their picture; in a low window the
 * picture scrolls with them. [onFinish] receives the button pressed, or null when the person skips
 * (the shell then opens « En direct »). The back gesture goes to the previous screen, and leaves the
 * tutorial from the first one.
 */
@Composable
fun FirstLaunchTutorial(onFinish: (TutorialStart?) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val steps = TutorialStep.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    SystemBack(enabled = true) { if (index > 0) index-- else onFinish(null) }
    val paneTitle = stringResource(step.title)
    // The blue of the identity around a white card (owner, 11 November 2026: « une belle card moderne, design a fond blanc »).
    Surface(
        color = palette.brand,
        contentColor = palette.onBrand,
        modifier = modifier.fillMaxSize().semantics { this.paneTitle = paneTitle }.testTag(TutorialTags.SCREEN),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The counter and « Passer », always at the top, whatever the length of the text.
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.tutorial_step, index + 1, steps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.onBrand,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }.testTag(TutorialTags.STEP),
                )
                val skip = stringResource(Res.string.tutorial_skip_description)
                TextButton(
                    onClick = { onFinish(null) },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.onBrand),
                    modifier = Modifier.heightIn(min = PpfTheme.dimens.touchTarget).semantics { contentDescription = skip }.testTag(TutorialTags.SKIP),
                ) { Text(stringResource(Res.string.tutorial_skip), style = MaterialTheme.typography.labelLarge) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                val cardMax = maxHeight
                Surface(
                    color = palette.surface2,
                    contentColor = palette.ink,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = cardMax),
                ) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                        val scroll = rememberScrollState()
                        LaunchedEffect(index) { scroll.scrollTo(0) }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                            StepContent(step, showIllustration = true, onFinish = onFinish)
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProgressDots(index, steps.size) }
                        // « Précédent » and « Suivant »; the last screen ends with its choices instead.
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColumnScope.StepContent(step: TutorialStep, showIllustration: Boolean, onFinish: (TutorialStart?) -> Unit) {
    val palette = PpfTheme.palette
    val illustration = step.illustration
    when {
        illustration != null && illustration.banner && showIllustration -> {
            Illustration(illustration)
            Spacer(Modifier.height(16.dp))
        }
        // The bonnet of the owner is shown as the icons of the other screens, in the same round.
        illustration != null && !illustration.banner -> {
            Box(Modifier.align(Alignment.CenterHorizontally).size(72.dp).background(palette.brandTint, CircleShape), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(illustration.image),
                    contentDescription = stringResource(illustration.description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp).testTag(TutorialTags.ILLUSTRATION),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        step.icon != null -> {
            Box(Modifier.align(Alignment.CenterHorizontally).size(72.dp).background(palette.brandTint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(step.icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    Text(
        text = stringResource(step.title),
        style = MaterialTheme.typography.titleLarge,
        color = palette.ink,
        textAlign = if (step == TutorialStep.START) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag(TutorialTags.TITLE),
    )
    step.text?.let {
        Spacer(Modifier.height(12.dp))
        TutorialMarkup(stringResource(it))
    }
    if (step == TutorialStep.FEATURES) {
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tutorialFeatures.forEach { (icon, name, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(palette.brandTint, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    val label = stringResource(name)
                    val words = stringResource(text)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.ink)) { append(label) }
                            append("  ")
                            append(words)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted,
                    )
                }
            }
        }
    }
    step.soon?.let {
        Spacer(Modifier.height(8.dp))
        Callout(CalloutType.CONSEIL, title = stringResource(Res.string.tutorial_soon), modifier = Modifier.testTag(TutorialTags.SOON)) {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (step == TutorialStep.PRIVACY) {
        Spacer(Modifier.height(8.dp))
        PpfSecondaryButton(
            onClick = { onFinish(TutorialStart.PRIVACY_SETTINGS) },
            text = stringResource(TutorialStart.PRIVACY_SETTINGS.label),
            icon = Lucide.Settings,
            modifier = Modifier.testTag(TutorialTags.start(TutorialStart.PRIVACY_SETTINGS)),
        )
    }
    if (step == TutorialStep.START) {
        Spacer(Modifier.height(20.dp))
        StartChoices(onFinish)
    }
}

/**
 * The Marianne of the owner as a wide banner, « assez grand pour percuter », on the transparent
 * background of her drawing (her request of 11 November 2026: no white card behind it).
 */
@Composable
private fun Illustration(illustration: TutorialIllustration) {
    Image(
        painter = painterResource(illustration.image),
        contentDescription = stringResource(illustration.description),
        contentScale = ContentScale.Fit,
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(2f).testTag(TutorialTags.ILLUSTRATION),
    )
}

/**
 * The texts of the tutorial, from their strings: paragraphs separated by an empty line, lines starting
 * with "- " shown as a bullet list, and **bold** passages. Nothing else is read as markup.
 */
@Composable
internal fun TutorialMarkup(markup: String) {
    val palette = PpfTheme.palette
    val blocks = remember(markup) { markup.split("\n\n").map { it.split("\n").filter(String::isNotBlank) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { lines ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { line ->
                    if (line.startsWith("- ")) {
                        Row {
                            Text("•", style = MaterialTheme.typography.bodyLarge, color = palette.brand, modifier = Modifier.width(18.dp))
                            Text(bold(line.removePrefix("- ")), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                        }
                    } else {
                        Text(bold(line), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                    }
                }
            }
        }
    }
}

/** **bold** passages of a line, in the ink of the theme. */
private fun bold(line: String): AnnotatedString = buildAnnotatedString {
    line.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

/** « Recevoir de l'aide » in red on the whole width, then the eight others two by two. */
@Composable
private fun StartChoices(onFinish: (TutorialStart?) -> Unit) {
    val palette = PpfTheme.palette
    Button(
        onClick = { onFinish(TutorialStart.HELP) },
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(TutorialTags.start(TutorialStart.HELP)),
    ) {
        Text(stringResource(TutorialStart.HELP.label), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(12.dp))
    // Each pair one above the other when a label would not fit whole (360 dp, large font).
    TutorialStart.grid.chunked(2).forEach { (first, second) ->
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
private fun StartButton(choice: TutorialStart, onFinish: (TutorialStart?) -> Unit) {
    PpfPrimaryButton(
        onClick = { onFinish(choice) },
        text = stringResource(choice.label),
        modifier = Modifier.heightIn(min = 56.dp).testTag(TutorialTags.start(choice)),
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
