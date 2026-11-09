package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.LocalPetition
import org.politisationpopulairefrancaise.core.decider.LocalPetitionStatus
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_local_collectivity
import org.politisationpopulairefrancaise.ui.resources.decider_local_count
import org.politisationpopulairefrancaise.ui.resources.decider_local_how_to_sign
import org.politisationpopulairefrancaise.ui.resources.decider_local_law
import org.politisationpopulairefrancaise.ui.resources.decider_local_milestones
import org.politisationpopulairefrancaise.ui.resources.decider_local_no_count
import org.politisationpopulairefrancaise.ui.resources.decider_local_privacy
import org.politisationpopulairefrancaise.ui.resources.decider_local_read_on
import org.politisationpopulairefrancaise.ui.resources.decider_local_required_commune
import org.politisationpopulairefrancaise.ui.resources.decider_local_required_other
import org.politisationpopulairefrancaise.ui.resources.decider_local_sign
import org.politisationpopulairefrancaise.ui.resources.decider_local_status_collecting
import org.politisationpopulairefrancaise.ui.resources.decider_local_status_decided
import org.politisationpopulairefrancaise.ui.resources.decider_local_status_filed
import org.politisationpopulairefrancaise.ui.resources.decider_local_status_on_agenda
import org.politisationpopulairefrancaise.ui.resources.decider_local_status_refused
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.CorrectionsSection
import org.politisationpopulairefrancaise.ui.voir.NextStepsSection
import org.politisationpopulairefrancaise.ui.voir.OriginLine
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.SourcesSection
import org.politisationpopulairefrancaise.ui.voir.VoirTitle
import org.politisationpopulairefrancaise.ui.voir.hostOf
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl

/** Test tags of the local petitions (DE-26). */
object LocalPetitionTags {
    const val COUNT = "decider-local-count"
    const val LAW = "decider-local-law"
    const val SIGN = "decider-local-sign"
    const val HOW_TO_SIGN = "decider-local-how-to-sign"
    const val PRIVACY = "decider-local-privacy"
    fun page(slug: String) = "decider-local-$slug"
}

/**
 * The codes of the territories chosen on this device (the commune of « Mes élus » and its department), read from the
 * device only: they select the local elections, consultations and petitions. Empty while nothing is chosen.
 */
@Composable
internal fun rememberDeviceTerritories(dependencies: AppDependencies?): Set<String> {
    val database = dependencies?.database ?: return emptySet()
    var codes by remember(database) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(database) {
        codes = withContext(Dispatchers.Default) {
            runCatching { ElusStore(database).commune() }.getOrNull()?.let { setOf(it.communeCode, it.departmentCode) }.orEmpty()
        }
    }
    return codes
}

private val LocalPetitionStatus.label: StringResource get() = when (this) {
    LocalPetitionStatus.COLLECTING -> Res.string.decider_local_status_collecting
    LocalPetitionStatus.FILED -> Res.string.decider_local_status_filed
    LocalPetitionStatus.ON_AGENDA -> Res.string.decider_local_status_on_agenda
    LocalPetitionStatus.CONSULTATION_DECIDED -> Res.string.decider_local_status_decided
    LocalPetitionStatus.REFUSED -> Res.string.decider_local_status_refused
}

@Composable
private fun CountLine(petition: LocalPetition) {
    val palette = PpfTheme.palette
    val count = petition.latestCount
    if (count == null) {
        Text(stringResource(Res.string.decider_local_no_count), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(LocalPetitionTags.COUNT))
        return
    }
    Text(
        stringResource(Res.string.decider_local_count, signatures(count.count), signatures(petition.required)),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag(LocalPetitionTags.COUNT),
    )
    val fraction = (petition.progress ?: 0.0).toFloat().coerceIn(0f, 1f)
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier.fillMaxWidth().height(10.dp).background(palette.surface1, MaterialTheme.shapes.extraSmall)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (fraction >= 1f) palette.success else palette.brand, MaterialTheme.shapes.extraSmall))
    }
    Spacer(Modifier.height(4.dp))
    Text(stringResource(Res.string.decider_local_read_on, count.readOn.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
}

/** The card of the home: the collectivity, where the petition stands, the count against the legal threshold. */
@Composable
internal fun LocalPetitionCard(petition: LocalPetition, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(DeciderTags.card(petition.article.slug))) {
        PpfBadge(stringResource(petition.status.label), if (petition.status.isCollecting) BadgeStyle.SUCCESS else BadgeStyle.NEUTRAL)
        Spacer(Modifier.height(8.dp))
        VoirTitle(petition.title, maxLines = 4)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.decider_local_collectivity, petition.collectivity), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        CountLine(petition)
    }
}

/**
 * One local petition (DE-26): the collectivity, where it stands, the count against the threshold of L1112-16 (computed on
 * the device from the registered voters the team read), where and how to sign (never here), what the law says, and what
 * became of it.
 */
@Composable
internal fun LocalPetitionPage(petition: LocalPetition, actions: DeciderActions) {
    val palette = PpfTheme.palette
    val openUrl = rememberOpenUrl()
    Column(Modifier.fillMaxWidth().testTag(LocalPetitionTags.page(petition.article.slug))) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PpfBadge(stringResource(petition.status.label), if (petition.status.isCollecting) BadgeStyle.SUCCESS else BadgeStyle.NEUTRAL)
        }
        Spacer(Modifier.height(8.dp))
        VoirTitle(petition.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        petition.article.summary?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.decider_local_collectivity, petition.collectivity), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        PpfCard(Modifier.fillMaxWidth()) {
            CountLine(petition)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (petition.commune) Res.string.decider_local_required_commune else Res.string.decider_local_required_other,
                    signatures(petition.voters.count),
                    petition.voters.readOn.toFrench(),
                    petition.voters.source,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
            )
        }
        if (petition.status.isCollecting) {
            Spacer(Modifier.height(12.dp))
            petition.signUrl?.let { url ->
                PpfPrimaryButton(
                    onClick = { openUrl(url) },
                    text = stringResource(Res.string.decider_local_sign, hostOf(url)),
                    icon = Lucide.ExternalLink,
                    modifier = Modifier.fillMaxWidth().testTag(LocalPetitionTags.SIGN),
                )
            }
            petition.howToSign?.let { how ->
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().testTag(LocalPetitionTags.HOW_TO_SIGN)) {
                    Text(stringResource(Res.string.decider_local_how_to_sign), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(how, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoBox(Lucide.Lock, Modifier.testTag(LocalPetitionTags.PRIVACY)) {
            Text(stringResource(Res.string.decider_local_privacy), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        InfoBox(Lucide.Landmark, Modifier.testTag(LocalPetitionTags.LAW)) {
            Text(stringResource(Res.string.decider_local_law), style = MaterialTheme.typography.bodySmall)
        }
        if (petition.milestones.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(Res.string.decider_local_milestones))
            petition.milestones.forEach { milestone ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Lucide.Flag, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(milestone.date.toFrench(), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                        Text(milestone.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        MarkdownText(petition.article.body)
        SourcesSection(petition.sources)
        CorrectionsSection(petition.corrections)
        Spacer(Modifier.height(8.dp))
        NextStepsSection(petition.nextSteps, onNext = actions.next)
        Spacer(Modifier.height(8.dp))
        OriginLine(petition.article)
    }
}
