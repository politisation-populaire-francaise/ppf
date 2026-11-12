package org.politisationpopulairefrancaise.ui.agir.engagement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.resources.engager_when_before
import org.politisationpopulairefrancaise.ui.resources.engager_when_rounds
import org.politisationpopulairefrancaise.ui.resources.engager_when_stand
import org.politisationpopulairefrancaise.ui.resources.engager_when_party
import org.politisationpopulairefrancaise.ui.resources.engager_now
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.engagement.ElectoralMilestone
import org.politisationpopulairefrancaise.core.agir.engagement.Engagement
import org.politisationpopulairefrancaise.core.agir.engagement.EngagementStep
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaFormat
import org.politisationpopulairefrancaise.ui.agir.manifestation.GuideReader
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.engager_back
import org.politisationpopulairefrancaise.ui.resources.engager_checked
import org.politisationpopulairefrancaise.ui.resources.engager_description
import org.politisationpopulairefrancaise.ui.resources.engager_in_comprendre
import org.politisationpopulairefrancaise.ui.resources.engager_neutral_source
import org.politisationpopulairefrancaise.ui.resources.engager_neutral_text
import org.politisationpopulairefrancaise.ui.resources.engager_neutral_title
import org.politisationpopulairefrancaise.ui.resources.engager_party_guide
import org.politisationpopulairefrancaise.ui.resources.engager_party_sheets
import org.politisationpopulairefrancaise.ui.resources.engager_party_text
import org.politisationpopulairefrancaise.ui.resources.engager_party_title
import org.politisationpopulairefrancaise.ui.resources.engager_presidential_open
import org.politisationpopulairefrancaise.ui.resources.engager_presidential_text
import org.politisationpopulairefrancaise.ui.resources.engager_presidential_title
import org.politisationpopulairefrancaise.ui.resources.engager_register_check
import org.politisationpopulairefrancaise.ui.resources.engager_register_course
import org.politisationpopulairefrancaise.ui.resources.engager_register_deadline
import org.politisationpopulairefrancaise.ui.resources.engager_register_how
import org.politisationpopulairefrancaise.ui.resources.engager_register_proxy
import org.politisationpopulairefrancaise.ui.resources.engager_register_title
import org.politisationpopulairefrancaise.ui.resources.engager_soon
import org.politisationpopulairefrancaise.ui.resources.engager_source
import org.politisationpopulairefrancaise.ui.resources.engager_stand_2028
import org.politisationpopulairefrancaise.ui.resources.engager_stand_course
import org.politisationpopulairefrancaise.ui.resources.engager_stand_legislatives
import org.politisationpopulairefrancaise.ui.resources.engager_stand_list
import org.politisationpopulairefrancaise.ui.resources.engager_stand_mates
import org.politisationpopulairefrancaise.ui.resources.engager_stand_text
import org.politisationpopulairefrancaise.ui.resources.engager_stand_title
import org.politisationpopulairefrancaise.ui.resources.engager_step
import org.politisationpopulairefrancaise.ui.resources.engager_title
import org.politisationpopulairefrancaise.ui.resources.engager_unconfirmed
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « S'engager en politique ». */
object EngagementTags {
    const val SCREEN = "engager-screen"
    const val BACK = "engager-back"
    const val STEP = "engager-step-" // followed by the step id
    const val SUMMARY = "engager-summary"
    const val SUMMARY_STEP = "engager-summary-step-"
    const val GUIDE = "engager-guide-" // followed by the slug of the guide
    const val GUIDE_SOON = "engager-guide-soon-" // followed by the slug of the guide
    const val COURSE = "engager-course-" // followed by the slug of the course
    const val CHECK_REGISTRATION = "engager-check-registration"
    const val REGISTER_PAGE = "engager-register-page"
    const val DECIDER = "engager-decider"
    const val COLLECTIVES = "engager-collectives"
    const val PARTIES = "engager-parties"
    const val NEUTRAL = "engager-neutral"
    const val SOURCE = "engager-source-" // followed by the milestone id, or l52-8
}

/** Where the steps lead outside the rubric; « Agir » plugs each of them. */
class EngagementLinks(
    /** « Décider »: the pact and the comparison of the candidates. */
    val openDecider: () -> Unit,
    /** « Voir »: the sheets of the parties, the same for all. */
    val openParties: () -> Unit,
    /** « Comprendre »: the courses (« S'inscrire et voter », « Se présenter à une élection »). */
    val openComprendre: () -> Unit,
    /** The collectifs of « Agir », to find running mates. */
    val openCollectives: () -> Unit,
)

private val READABLE = 720.dp

/**
 * « S'engager en politique » (D-196, D-213): four steps in the order of the electoral calendar, each with the
 * official procedure, the guides of « Comprendre » read inside « Agir » and the places of the application that
 * help (« Décider », the parties of « Voir », the collectifs). The same tools for every list and every party:
 * PPF supports none of them (code électoral, article L52-8), which the screen says.
 *
 * A guide not yet on the device is shown greyed out with an honest line.
 */
@Composable
fun EngagementScreen(
    library: ContentLibrary,
    windowClass: WindowClass,
    links: EngagementLinks,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    var readingSlug by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(readingSlug) { scroll.scrollTo(0) }
    SystemBack(enabled = readingSlug != null) { readingSlug = null }
    val guideOf: (String) -> Article? = { slug -> library.guides.firstOrNull { it.slug == slug } }

    Column(
        modifier.fillMaxSize().background(palette.surface1).verticalScroll(scroll)
            .padding(if (compact) 16.dp else 24.dp).testTag(EngagementTags.SCREEN),
    ) {
        readingSlug?.let { slug ->
            GuideReader(guideOf(slug), onBack = { readingSlug = null })
            return@Column
        }
        Column(Modifier.widthIn(max = READABLE).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.engager_back), onBack, Modifier.testTag(EngagementTags.BACK), tint = palette.ink)
            }
            PageHeader(stringResource(Res.string.engager_title), stringResource(Res.string.engager_description))
            // The four steps at a glance, the one of the moment marked; a tap brings it into view.
            val requesters = remember { EngagementStep.entries.associateWith { BringIntoViewRequester() } }
            val scope = rememberCoroutineScope()
            StepsSummary(currentStep(ParisTime.dateOf(ParisTime.epochDay(currentEpochSeconds()))), onSelect = { step -> scope.launch { requesters.getValue(step).bringIntoView() } })
            val stepModifier: (EngagementStep) -> Modifier = { step -> Modifier.bringIntoViewRequester(requesters.getValue(step)) }

            val guide: @Composable (String, StringResource) -> Unit = { slug, label ->
                GuideLink(slug, label, available = guideOf(slug) != null) { readingSlug = slug }
            }

            Step(EngagementStep.REGISTER, 1, Res.string.engager_register_title, Lucide.Vote, stepModifier(EngagementStep.REGISTER)) {
                val deadline = Engagement.REGISTRATION_DEADLINE
                Text(
                    stringResource(Res.string.engager_register_deadline, longDate(deadline), stringResource(Res.string.engager_unconfirmed)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Source(deadline.source, EngagementTags.SOURCE + deadline.id)
                val uris = LocalUriHandler.current
                PpfPrimaryButton(
                    onClick = { uris.openUri(Engagement.CHECK_REGISTRATION) },
                    text = stringResource(Res.string.engager_register_check),
                    icon = Lucide.Search,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(EngagementTags.CHECK_REGISTRATION),
                )
                PpfSecondaryButton(
                    onClick = { uris.openUri(Engagement.REGISTER_PAGE) },
                    text = stringResource(Res.string.engager_register_how),
                    icon = Lucide.ExternalLink,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(EngagementTags.REGISTER_PAGE),
                )
                guide(Engagement.GUIDE_PROXY, Res.string.engager_register_proxy)
                CourseLink(Engagement.COURSE_REGISTER, Res.string.engager_register_course, links.openComprendre)
            }

            Step(EngagementStep.PRESIDENTIAL, 2, Res.string.engager_presidential_title, Lucide.Landmark, stepModifier(EngagementStep.PRESIDENTIAL)) {
                Text(
                    stringResource(
                        Res.string.engager_presidential_text,
                        longDate(Engagement.PRESIDENTIAL_FIRST_ROUND),
                        longDate(Engagement.PRESIDENTIAL_SECOND_ROUND),
                        stringResource(Res.string.engager_unconfirmed),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Source(Engagement.CALENDAR_SOURCE, EngagementTags.SOURCE + Engagement.PRESIDENTIAL_FIRST_ROUND.id)
                PpfSecondaryButton(
                    onClick = links.openDecider,
                    text = stringResource(Res.string.engager_presidential_open),
                    modifier = Modifier.heightIn(min = 48.dp).testTag(EngagementTags.DECIDER),
                )
            }

            Step(EngagementStep.STAND, 3, Res.string.engager_stand_title, Lucide.Megaphone, stepModifier(EngagementStep.STAND)) {
                Text(stringResource(Res.string.engager_stand_text), style = MaterialTheme.typography.bodyLarge)
                Source(Engagement.CALENDAR_SOURCE, EngagementTags.SOURCE + "calendrier")
                guide(Engagement.GUIDE_CITIZEN_LIST, Res.string.engager_stand_list)
                guide(Engagement.GUIDE_2028, Res.string.engager_stand_2028)
                guide(Engagement.GUIDE_LEGISLATIVES, Res.string.engager_stand_legislatives)
                CourseLink(Engagement.COURSE_STAND, Res.string.engager_stand_course, links.openComprendre)
                PpfSecondaryButton(
                    onClick = links.openCollectives,
                    text = stringResource(Res.string.engager_stand_mates),
                    icon = Lucide.Users,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(EngagementTags.COLLECTIVES),
                )
            }

            Step(EngagementStep.PARTY, 4, Res.string.engager_party_title, Lucide.Building2, stepModifier(EngagementStep.PARTY)) {
                Text(stringResource(Res.string.engager_party_text), style = MaterialTheme.typography.bodyLarge)
                guide(Engagement.GUIDE_PARTY, Res.string.engager_party_guide)
                PpfSecondaryButton(
                    onClick = links.openParties,
                    text = stringResource(Res.string.engager_party_sheets),
                    modifier = Modifier.heightIn(min = 48.dp).testTag(EngagementTags.PARTIES),
                )
            }

            InfoBox(Lucide.Info, Modifier.testTag(EngagementTags.NEUTRAL)) {
                Text(stringResource(Res.string.engager_neutral_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.engager_neutral_text), style = MaterialTheme.typography.bodyMedium)
                Source(Engagement.L52_8, EngagementTags.SOURCE + "l52-8", Res.string.engager_neutral_source)
            }
            Text(
                stringResource(Res.string.engager_checked, Engagement.CHECKED_ON.toFrench()),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
            )
        }
    }
}

/** A numbered step of the calendar, its title and what it offers. */
@Composable
private fun Step(step: EngagementStep, number: Int, title: StringResource, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(modifier.fillMaxWidth().testTag(EngagementTags.STEP + step.id)) {
        PpfBadge(stringResource(Res.string.engager_step, number), BadgeStyle.NEUTRAL)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** The step of the moment in the electoral calendar: register until the deadline, then vote, then stand. */
internal fun currentStep(today: org.politisationpopulairefrancaise.core.time.CivilDate): EngagementStep = when {
    today <= Engagement.REGISTRATION_DEADLINE.date -> EngagementStep.REGISTER
    today <= Engagement.PRESIDENTIAL_SECOND_ROUND.date -> EngagementStep.PRESIDENTIAL
    else -> EngagementStep.STAND
}

/** The four steps in one card: number, name, when, and « C'est maintenant » on the step of the moment. */
@Composable
private fun StepsSummary(current: EngagementStep, onSelect: (EngagementStep) -> Unit) {
    val palette = PpfTheme.palette
    val rows = listOf(
        Triple(EngagementStep.REGISTER, Res.string.engager_register_title, stringResource(Res.string.engager_when_before, longDate(Engagement.REGISTRATION_DEADLINE))),
        Triple(EngagementStep.PRESIDENTIAL, Res.string.engager_presidential_title, stringResource(Res.string.engager_when_rounds, longDate(Engagement.PRESIDENTIAL_FIRST_ROUND), longDate(Engagement.PRESIDENTIAL_SECOND_ROUND))),
        Triple(EngagementStep.STAND, Res.string.engager_stand_title, stringResource(Res.string.engager_when_stand)),
        Triple(EngagementStep.PARTY, Res.string.engager_party_title, stringResource(Res.string.engager_when_party)),
    )
    PpfCard(Modifier.fillMaxWidth().testTag(EngagementTags.SUMMARY)) {
        rows.forEachIndexed { index, (step, title, moment) ->
            if (index > 0) Spacer(Modifier.height(4.dp))
            PpfCard(onClick = { onSelect(step) }, modifier = Modifier.fillMaxWidth().testTag(EngagementTags.SUMMARY_STEP + step.id)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).background(if (step == current) palette.brand else palette.brandTint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((index + 1).toString(), style = MaterialTheme.typography.titleSmall, color = if (step == current) palette.surface0 else palette.brand)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        Text(moment, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                        if (step == current) PpfBadge(stringResource(Res.string.engager_now), BadgeStyle.SUCCESS)
                    }
                    Icon(Lucide.ChevronDown, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** A guide of « Comprendre » read inside « Agir »; greyed out with an honest line while not on the device. */
@Composable
private fun GuideLink(slug: String, label: StringResource, available: Boolean, onOpen: () -> Unit) {
    if (available) {
        PpfTextButton(onClick = onOpen, text = stringResource(label), modifier = Modifier.heightIn(min = 48.dp).testTag(EngagementTags.GUIDE + slug))
    } else {
        Column(Modifier.testTag(EngagementTags.GUIDE_SOON + slug)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = PpfTheme.palette.inkSubtle)
            Text(stringResource(Res.string.engager_soon), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
        }
    }
}

/** A course of « Comprendre »: it opens in its space. */
@Composable
private fun CourseLink(slug: String, title: StringResource, onOpen: () -> Unit) {
    PpfTextButton(
        onClick = onOpen,
        text = stringResource(Res.string.engager_in_comprendre, stringResource(title)),
        modifier = Modifier.heightIn(min = 48.dp).testTag(EngagementTags.COURSE + slug),
    )
}

/** The official page a fact was read on. */
@Composable
private fun Source(url: String, tag: String, label: StringResource = Res.string.engager_source) {
    val uris = LocalUriHandler.current
    PpfTextButton(onClick = { uris.openUri(url) }, text = stringResource(label), modifier = Modifier.heightIn(min = 48.dp).testTag(tag))
}

/** « vendredi 12 mars 2027 »: the day of the week helps not to miss a deadline (French dates of the agenda). */
internal fun longDate(milestone: ElectoralMilestone): String =
    AgendaFormat.date(ParisTime.moment(ParisTime.toEpochSeconds(milestone.date, 12, 0)), withYear = true)
