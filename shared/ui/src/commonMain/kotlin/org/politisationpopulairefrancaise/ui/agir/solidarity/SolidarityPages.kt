package org.politisationpopulairefrancaise.ui.agir.solidarity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.solidarity.FundManager
import org.politisationpopulairefrancaise.core.agir.solidarity.FundManagerState
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.core.agir.solidarity.VolunteerMission
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.LeaveDialogTags
import org.politisationpopulairefrancaise.ui.components.LeavePpfDialog
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the pages of a fund and of a mission. */
object SolidarityPageTags {
    const val FUND_PAGE = "solidarity-fund-page"
    const val FUND_BACK = "solidarity-fund-back"
    const val FUND_NAME = "solidarity-fund-name"
    const val FUND_VERIFIED = "solidarity-fund-verified"
    const val FUND_WITHDRAWN = "solidarity-fund-withdrawn"
    const val FUND_MANAGER = "solidarity-fund-manager"
    const val FUND_OPEN_ORGANISATION = "solidarity-fund-open-organisation"
    const val DONATE = "solidarity-donate"
    const val DONATE_WHERE = "solidarity-donate-where"
    const val NO_DONATION = "solidarity-no-donation"
    const val LEAVE_DIALOG = "solidarity-leave-dialog"
    const val LEAVE_STAY = "solidarity-leave-stay"
    const val LEAVE_OPEN = "solidarity-leave-open"

    const val MISSION_PAGE = "solidarity-mission-page"
    const val MISSION_BACK = "solidarity-mission-back"
    const val MISSION_TITLE = "solidarity-mission-title"
    const val MISSION_OFFER = "solidarity-mission-offer"
    const val MISSION_OFFER_SOON = "solidarity-mission-offer-soon"
}

/**
 * The page of a solidarity fund (AG-10): what it is, when the team checked it, who it serves, who
 * manages it (said in words: verified organisation, verification withdrawn, or not in the
 * directory), and the way to give.
 *
 * PPF never holds nor passes on any money (point of attention 12): "Donner sur la page de la caisse"
 * only opens the fund's own page, and only after "Vous quittez PPF", whose first and most visible
 * button keeps the user here and whose text warns against fake fundraisings. A closed fund or an
 * entry withdrawn by the team offers no link at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FundPage(
    fund: SolidarityFund,
    manager: FundManager,
    onBack: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onOpenOrganisation: ((Organisation) -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    var leaving by remember(fund.address) { mutableStateOf(false) }
    val link = fund.openableDonationLink

    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(SolidarityPageTags.FUND_PAGE)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.solidarite_fund_back), onBack, Modifier.testTag(SolidarityPageTags.FUND_BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PpfBadge(stringResource(SolidarityLabels.fundType(fund.type)), BadgeStyle.BRAND)
                FundStatusBadge(fund)
                if (fund.example) ExampleBadge()
            }
            Spacer(Modifier.height(12.dp))
            Text(
                fund.displayName,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }.testTag(SolidarityPageTags.FUND_NAME),
            )
            Spacer(Modifier.height(8.dp))
            val withdrawnOn = fund.withdrawnOn
            if (withdrawnOn != null) {
                // Transparency: the page says the entry was withdrawn, and when, instead of vanishing.
                Column(Modifier.testTag(SolidarityPageTags.FUND_WITHDRAWN)) {
                    IconLine(Lucide.TriangleExclamationPoint, palette.warning, stringResource(Res.string.solidarite_fund_withdrawn_on, withdrawnOn.toFrench()), palette.ink, label = true)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.solidarite_fund_withdrawn_explain), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
            } else {
                IconLine(
                    Lucide.BadgeCheck,
                    palette.success,
                    stringResource(Res.string.solidarite_fund_verified_on, fund.verifiedOn.toFrench()),
                    palette.success,
                    label = true,
                    modifier = Modifier.testTag(SolidarityPageTags.FUND_VERIFIED),
                )
            }
            Spacer(Modifier.height(6.dp))
            IconLine(Lucide.MapPin, palette.inkMuted, if (fund.isNational) stringResource(Res.string.solidarite_national) else fund.territory, palette.inkMuted)
            Spacer(Modifier.height(12.dp))
            Text(fund.summary, style = MaterialTheme.typography.bodyLarge, color = palette.ink)

            Section(stringResource(Res.string.solidarite_fund_beneficiary_title))
            Text(fund.beneficiary, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            fund.sector?.let { sector ->
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.solidarite_fund_sector, sector), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            fund.strike?.let { strike ->
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.solidarite_fund_strike, strike), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }

            Section(stringResource(Res.string.solidarite_fund_manager_title))
            // Read as one sentence by screen readers: the icon only repeats what the words say.
            ManagerLine(manager, modifier = Modifier.semantics(mergeDescendants = true) { }.testTag(SolidarityPageTags.FUND_MANAGER))
            val organisation = manager.organisation
            if (organisation != null && onOpenOrganisation != null) {
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton(
                    onClick = { onOpenOrganisation(organisation) },
                    text = stringResource(Res.string.solidarite_fund_open_organisation),
                    modifier = Modifier.testTag(SolidarityPageTags.FUND_OPEN_ORGANISATION),
                    icon = Lucide.Building2,
                )
            }

            Section(stringResource(Res.string.solidarite_fund_donate_title))
            if (link != null) {
                PpfPrimaryButton(
                    onClick = { leaving = true },
                    text = stringResource(Res.string.solidarite_fund_donate),
                    modifier = Modifier.testTag(SolidarityPageTags.DONATE),
                    icon = Lucide.ExternalLink,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.solidarite_fund_donate_where, fund.donationHost.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSubtle,
                    modifier = Modifier.testTag(SolidarityPageTags.DONATE_WHERE),
                )
            } else {
                val reason = when {
                    fund.withdrawn -> Res.string.solidarite_fund_withdrawn_no_link
                    fund.isClosed -> Res.string.solidarite_fund_closed
                    else -> Res.string.solidarite_fund_no_link
                }
                Text(stringResource(reason), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(SolidarityPageTags.NO_DONATION))
            }

            Section(stringResource(Res.string.solidarite_fund_presentation))
            SelectionContainer { MarkdownText(fund.presentation) }

            Footer(fund.updatedAt, fund.origin)
        }
    }

    if (leaving && link != null) {
        // The common "Vous quittez PPF" of every space (ui/components), which opens the page itself.
        LeavePpfDialog(
            url = link,
            title = stringResource(Res.string.solidarite_leave_title),
            text = stringResource(Res.string.solidarite_leave_text, fund.donationHost.orEmpty()),
            stayLabel = stringResource(Res.string.solidarite_leave_stay),
            openLabel = stringResource(Res.string.solidarite_leave_open),
            onStay = { leaving = false },
            onDone = { leaving = false },
            tags = LeaveDialogTags(SolidarityPageTags.LEAVE_DIALOG, SolidarityPageTags.LEAVE_STAY, SolidarityPageTags.LEAVE_OPEN),
            note = stringResource(Res.string.solidarite_leave_fundraising),
        )
    }
}

/**
 * The page of a volunteer mission (AG-12): what the team needs, the skills it asks for, where and
 * when. "Proposer mon aide" is shown greyed out with its explanation: nothing is sent until a safe
 * way to the team is decided (question 3 of docs/integration/agir-solidarite.md).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MissionPage(
    mission: VolunteerMission,
    onBack: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(SolidarityPageTags.MISSION_PAGE)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.solidarite_mission_back), onBack, Modifier.testTag(SolidarityPageTags.MISSION_BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MissionStatusBadge(mission)
                if (mission.remote) PpfBadge(stringResource(Res.string.solidarite_mission_remote), BadgeStyle.BRAND)
                if (mission.example) ExampleBadge()
            }
            Spacer(Modifier.height(12.dp))
            Text(
                mission.displayTitle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }.testTag(SolidarityPageTags.MISSION_TITLE),
            )
            Spacer(Modifier.height(8.dp))
            Text(mission.summary, style = MaterialTheme.typography.bodyLarge, color = palette.ink)

            Section(stringResource(Res.string.solidarite_mission_skills_title))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mission.skills.forEach { skill -> PpfBadge(stringResource(SolidarityLabels.skill(skill)), BadgeStyle.NEUTRAL) }
            }

            Section(stringResource(Res.string.solidarite_mission_where_title))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (mission.remote) IconLine(Lucide.Globe, palette.inkMuted, stringResource(Res.string.solidarite_mission_remote), palette.ink)
                mission.location?.let { location -> IconLine(Lucide.MapPin, palette.inkMuted, location, palette.ink) }
                IconLine(Lucide.MapPin, palette.inkMuted, if (mission.isNational) stringResource(Res.string.solidarite_national) else mission.territory, palette.inkMuted)
                mission.commitment?.let { commitment -> IconLine(Lucide.Clock, palette.inkMuted, stringResource(Res.string.solidarite_mission_commitment, commitment), palette.ink) }
                mission.people?.let { people -> IconLine(Lucide.Users, palette.inkMuted, pluralStringResource(Res.plurals.solidarite_mission_people, people, people), palette.ink) }
                mission.deadlineDate?.let { date -> IconLine(Lucide.Calendar, palette.inkMuted, stringResource(Res.string.solidarite_mission_deadline, date.toFrench()), palette.ink) }
            }

            Section(stringResource(Res.string.solidarite_mission_description))
            SelectionContainer { MarkdownText(mission.description) }

            Spacer(Modifier.height(24.dp))
            // Greyed out, and said in words to screen readers: nothing leaves the device until a safe way exists.
            val soon = stringResource(Res.string.solidarite_soon_state)
            PpfPrimaryButton(
                onClick = {},
                text = stringResource(Res.string.solidarite_mission_offer),
                enabled = false,
                modifier = Modifier.semantics { stateDescription = soon }.testTag(SolidarityPageTags.MISSION_OFFER),
            )
            Spacer(Modifier.height(8.dp))
            InfoBox(Lucide.Lock, Modifier.testTag(SolidarityPageTags.MISSION_OFFER_SOON)) {
                Text(stringResource(Res.string.solidarite_mission_offer_soon_title), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.solidarite_mission_offer_soon), style = MaterialTheme.typography.bodyMedium)
            }

            Footer(mission.updatedAt, mission.origin)
        }
    }
}

/** The state of a fund, written in its badge (never by colour alone). */
@Composable
internal fun FundStatusBadge(fund: SolidarityFund, modifier: Modifier = Modifier) {
    val style = when (fund.status) {
        SolidarityFormats.FUND_OPEN -> BadgeStyle.SUCCESS
        SolidarityFormats.FUND_CLOSED -> BadgeStyle.NEUTRAL
        else -> BadgeStyle.WARNING
    }
    PpfBadge(stringResource(SolidarityLabels.fundStatus(fund.status)), style, modifier)
}

/** The state of a mission, written in its badge. */
@Composable
internal fun MissionStatusBadge(mission: VolunteerMission, modifier: Modifier = Modifier) {
    PpfBadge(stringResource(SolidarityLabels.missionStatus(mission.status)), if (mission.isOpen) BadgeStyle.SUCCESS else BadgeStyle.NEUTRAL, modifier)
}

/** Who manages a fund, in words, with an icon that repeats what the words say. */
@Composable
internal fun ManagerLine(manager: FundManager, modifier: Modifier = Modifier, small: Boolean = false) {
    val palette = PpfTheme.palette
    val text = when (manager.state) {
        FundManagerState.VERIFIED -> stringResource(Res.string.solidarite_manager_verified, manager.name.orEmpty())
        FundManagerState.REVOKED -> stringResource(Res.string.solidarite_manager_revoked, manager.name.orEmpty())
        FundManagerState.UNLISTED -> {
            val name = manager.name
            if (name != null) stringResource(Res.string.solidarite_manager_unlisted, name) else stringResource(Res.string.solidarite_manager_unknown)
        }
    }
    val (icon, tint) = when (manager.state) {
        FundManagerState.VERIFIED -> Lucide.BadgeCheck to palette.success
        FundManagerState.REVOKED -> Lucide.TriangleExclamationPoint to palette.warning
        FundManagerState.UNLISTED -> Lucide.Info to palette.inkMuted
    }
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(if (small) 14.dp else 18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            color = if (small) palette.inkMuted else palette.ink,
        )
    }
}

@Composable
private fun IconLine(icon: ImageVector, tint: Color, text: String, color: Color, label: Boolean = false, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = if (label) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Footer(updatedAt: Long, origin: ContentOrigin?) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(24.dp))
    HorizontalDivider(color = palette.border)
    Spacer(Modifier.height(12.dp))
    val updated = stringResource(Res.string.solidarite_updated, CivilDate.fromEpochSeconds(updatedAt).toFrench())
    val written = when (origin) {
        ContentOrigin.HUMAN -> stringResource(Res.string.solidarite_origin_human)
        ContentOrigin.AI_ASSISTED -> stringResource(Res.string.solidarite_origin_ai)
        null -> null
    }
    Text(listOfNotNull(updated, written).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
}
