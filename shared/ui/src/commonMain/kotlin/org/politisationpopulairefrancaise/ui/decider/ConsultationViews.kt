package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.ConsultationKind
import org.politisationpopulairefrancaise.core.decider.PublicConsultation
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_answer
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_basis_debate
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_basis_environment
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_basis_inquiry
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_basis_local_vote
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_basis_open_online
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_dates
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_days_left
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_kind_debate
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_kind_environment
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_kind_inquiry
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_kind_local_vote
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_kind_open_online
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_last_day
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_one_day_left
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_opens
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_organizer
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_over
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_privacy
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_see_official
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_until
import org.politisationpopulairefrancaise.ui.resources.decider_consultation_upcoming
import org.politisationpopulairefrancaise.ui.resources.decider_petition_open
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_category_note
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_consultation_ask
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_consultation_offer
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_consultation_stop
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.CorrectionsSection
import org.politisationpopulairefrancaise.ui.voir.NextStepsSection
import org.politisationpopulairefrancaise.ui.voir.OriginLine
import org.politisationpopulairefrancaise.ui.voir.SourcesSection
import org.politisationpopulairefrancaise.ui.voir.VoirTitle
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl

/** Test tags of the official consultations (DE-27). */
object ConsultationTags {
    const val ANSWER = "decider-consultation-answer"
    const val BASIS = "decider-consultation-basis"
    const val DATES = "decider-consultation-dates"
    const val PRIVACY = "decider-consultation-privacy"
    fun page(slug: String) = "decider-consultation-$slug"
}

internal val ConsultationKind.label: StringResource get() = when (this) {
    ConsultationKind.OPEN_ONLINE -> Res.string.decider_consultation_kind_open_online
    ConsultationKind.ENVIRONMENT_ONLINE -> Res.string.decider_consultation_kind_environment
    ConsultationKind.PUBLIC_INQUIRY -> Res.string.decider_consultation_kind_inquiry
    ConsultationKind.PUBLIC_DEBATE -> Res.string.decider_consultation_kind_debate
    ConsultationKind.LOCAL_VOTE -> Res.string.decider_consultation_kind_local_vote
}

/** What the law says of each kind, in one fixed sentence with its articles: the same for every consultation of the kind. */
private val ConsultationKind.basis: StringResource get() = when (this) {
    ConsultationKind.OPEN_ONLINE -> Res.string.decider_consultation_basis_open_online
    ConsultationKind.ENVIRONMENT_ONLINE -> Res.string.decider_consultation_basis_environment
    ConsultationKind.PUBLIC_INQUIRY -> Res.string.decider_consultation_basis_inquiry
    ConsultationKind.PUBLIC_DEBATE -> Res.string.decider_consultation_basis_debate
    ConsultationKind.LOCAL_VOTE -> Res.string.decider_consultation_basis_local_vote
}

@Composable
private fun timeText(consultation: PublicConsultation, today: CivilDate): String = when {
    consultation.isUpcoming(today) -> stringResource(Res.string.decider_consultation_opens, consultation.opensOn.toFrench())
    consultation.isOver(today) -> stringResource(Res.string.decider_consultation_over)
    else -> when (val days = consultation.daysLeft(today)) {
        0L -> stringResource(Res.string.decider_consultation_last_day)
        1L -> stringResource(Res.string.decider_consultation_one_day_left)
        else -> stringResource(Res.string.decider_consultation_days_left, days.toInt())
    }
}

@Composable
private fun StateBadges(consultation: PublicConsultation, today: CivilDate) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PpfBadge(stringResource(consultation.kind.label), BadgeStyle.BRAND)
        when {
            consultation.isUpcoming(today) -> PpfBadge(stringResource(Res.string.decider_consultation_upcoming), BadgeStyle.NEUTRAL)
            consultation.isOver(today) -> PpfBadge(stringResource(Res.string.decider_consultation_over), BadgeStyle.NEUTRAL)
            else -> PpfBadge(stringResource(Res.string.decider_petition_open), BadgeStyle.SUCCESS)
        }
    }
}

/** The card of the home: its kind, its title, who organizes it, and the time left to answer. */
@Composable
internal fun ConsultationCard(consultation: PublicConsultation, today: CivilDate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(DeciderTags.card(consultation.article.slug))) {
        StateBadges(consultation, today)
        Spacer(Modifier.height(8.dp))
        VoirTitle(consultation.title, maxLines = 4)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.decider_consultation_organizer, consultation.organizer), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Clock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(timeText(consultation, today), style = MaterialTheme.typography.bodySmall, color = palette.ink)
        }
    }
}

/**
 * One official consultation: its kind and what the law says of it, who organizes it, its dates, the official page where
 * one answers (after « Vous quittez PPF »), the guide to answer, and its reminders kept on the device. PPF collects nothing.
 */
@Composable
internal fun ConsultationPage(consultation: PublicConsultation, today: CivilDate, actions: DeciderActions, reminders: DeciderRemindersState?) {
    val palette = PpfTheme.palette
    val openUrl = rememberOpenUrl()
    Column(Modifier.fillMaxWidth().testTag(ConsultationTags.page(consultation.article.slug))) {
        StateBadges(consultation, today)
        Spacer(Modifier.height(8.dp))
        VoirTitle(consultation.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        consultation.article.summary?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.decider_consultation_organizer, consultation.organizer), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(Res.string.decider_consultation_dates, consultation.opensOn.toFrench(), consultation.closesOn.toFrench()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(ConsultationTags.DATES),
        )
        Text(timeText(consultation, today), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))
        if (consultation.isOpen(today)) {
            PpfPrimaryButton(
                onClick = { openUrl(consultation.officialUrl) },
                text = stringResource(Res.string.decider_consultation_answer, consultation.officialHost),
                icon = Lucide.ExternalLink,
                modifier = Modifier.fillMaxWidth().testTag(ConsultationTags.ANSWER),
            )
        } else {
            PpfSecondaryButton(
                onClick = { openUrl(consultation.officialUrl) },
                text = stringResource(Res.string.decider_consultation_see_official, consultation.officialHost),
                icon = Lucide.ExternalLink,
                modifier = Modifier.fillMaxWidth().testTag(ConsultationTags.ANSWER),
            )
        }
        Spacer(Modifier.height(8.dp))
        InfoBox(Lucide.Lock, Modifier.testTag(ConsultationTags.PRIVACY)) {
            Text(stringResource(Res.string.decider_consultation_privacy), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        InfoBox(Lucide.Landmark, Modifier.testTag(ConsultationTags.BASIS)) {
            Text(stringResource(consultation.kind.basis), style = MaterialTheme.typography.bodySmall)
        }
        if (reminders != null && !consultation.isOver(today)) {
            Spacer(Modifier.height(16.dp))
            ConsultationRemindersSection(consultation, reminders)
        }
        Spacer(Modifier.height(16.dp))
        MarkdownText(consultation.article.body)
        SourcesSection(consultation.sources)
        CorrectionsSection(consultation.corrections)
        Spacer(Modifier.height(8.dp))
        NextStepsSection(consultation.nextSteps, onNext = actions.next)
        Spacer(Modifier.height(8.dp))
        OriginLine(consultation.article)
    }
}

/** « Rappels » of a consultation: the offer while nothing is asked, then the reminders to come, each removable. */
@Composable
private fun ConsultationRemindersSection(consultation: PublicConsultation, reminders: DeciderRemindersState) {
    val palette = PpfTheme.palette
    val version = reminders.version
    val asked = remember(consultation, version) { reminders.isAsked(consultation) }
    val upcoming = remember(consultation, version) { reminders.upcoming(consultation) }
    Column(Modifier.fillMaxWidth().testTag(ElectionReminderTags.SECTION)) {
        Text(stringResource(Res.string.decider_reminders_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        if (!asked) {
            Text(stringResource(Res.string.decider_reminders_consultation_offer), style = MaterialTheme.typography.bodyMedium)
            if (!reminders.categoryOn) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.decider_reminders_category_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            PpfPrimaryButton(
                onClick = { reminders.ask(consultation) },
                text = stringResource(Res.string.decider_reminders_consultation_ask),
                icon = Lucide.Bell,
                modifier = Modifier.testTag(ElectionReminderTags.ASK),
            )
        } else {
            ReminderCategoryNotice(reminders)
            UpcomingReminders(upcoming, reminders, onStop = { reminders.stop(consultation) }, stopText = Res.string.decider_reminders_consultation_stop)
        }
    }
}
