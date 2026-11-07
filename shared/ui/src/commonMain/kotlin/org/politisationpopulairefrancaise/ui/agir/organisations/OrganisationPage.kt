package org.politisationpopulairefrancaise.ui.agir.organisations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.components.LeaveDialogTags
import org.politisationpopulairefrancaise.ui.components.LeavePpfDialog
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.FollowButton
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.content.FeedPost
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.feed.RelativeTime
import org.politisationpopulairefrancaise.ui.feed.label
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.organisation_address_label
import org.politisationpopulairefrancaise.ui.resources.organisation_agenda
import org.politisationpopulairefrancaise.ui.resources.organisation_agenda_soon
import org.politisationpopulairefrancaise.ui.resources.organisation_back
import org.politisationpopulairefrancaise.ui.resources.organisation_call
import org.politisationpopulairefrancaise.ui.resources.organisation_contacts
import org.politisationpopulairefrancaise.ui.resources.organisation_contacts_none
import org.politisationpopulairefrancaise.ui.resources.organisation_email_label
import org.politisationpopulairefrancaise.ui.resources.organisation_follow_private
import org.politisationpopulairefrancaise.ui.resources.organisation_leave_go
import org.politisationpopulairefrancaise.ui.resources.organisation_leave_stay
import org.politisationpopulairefrancaise.ui.resources.organisation_leave_text
import org.politisationpopulairefrancaise.ui.resources.organisation_leave_title
import org.politisationpopulairefrancaise.ui.resources.organisation_national
import org.politisationpopulairefrancaise.ui.resources.organisation_no_account
import org.politisationpopulairefrancaise.ui.resources.organisation_origin_ai
import org.politisationpopulairefrancaise.ui.resources.organisation_origin_human
import org.politisationpopulairefrancaise.ui.resources.organisation_phone_label
import org.politisationpopulairefrancaise.ui.resources.organisation_presentation
import org.politisationpopulairefrancaise.ui.resources.organisation_publications
import org.politisationpopulairefrancaise.ui.resources.organisation_publications_follow
import org.politisationpopulairefrancaise.ui.resources.organisation_publications_none
import org.politisationpopulairefrancaise.ui.resources.organisation_updated
import org.politisationpopulairefrancaise.ui.resources.organisation_revoked_explain
import org.politisationpopulairefrancaise.ui.resources.organisation_revoked_on
import org.politisationpopulairefrancaise.ui.resources.organisation_verified_on
import org.politisationpopulairefrancaise.ui.resources.organisation_visit
import org.politisationpopulairefrancaise.ui.resources.organisation_website_label
import org.politisationpopulairefrancaise.ui.resources.organisation_write
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the page of an organisation. */
object OrganisationPageTags {
    const val PAGE = "organisation-page"
    const val BACK = "organisation-back"
    const val NAME = "organisation-name"
    const val VERIFIED = "organisation-verified"
    const val REVOKED = "organisation-revoked"
    const val FOLLOW = "organisation-follow"
    const val FOLLOW_PRIVATE = "organisation-follow-private"
    const val NO_ACCOUNT = "organisation-no-account"
    const val PHONE = "organisation-phone"
    const val EMAIL = "organisation-email"
    const val WEBSITE = "organisation-website-" // followed by the position of the site
    const val ADDRESS = "organisation-address"
    const val PUBLICATIONS = "organisation-publications"
    const val PUBLICATION = "organisation-publication-" // followed by the first 8 characters of the note id
    const val PUBLICATIONS_NONE = "organisation-publications-none"
    const val AGENDA = "organisation-agenda"
    const val AGENDA_SOON = "organisation-agenda-soon"
    const val LEAVE_DIALOG = "organisation-leave-dialog"
    const val LEAVE_STAY = "organisation-leave-stay"
    const val LEAVE_GO = "organisation-leave-go"
}

/**
 * The page of an organisation (AG-17): its presentation, the date the team verified it, its type
 * and territory, its public contacts, the button to follow it (AG-22, the list stays on the
 * device), its publications found on the device and a place for its agenda.
 *
 * Telephone and e-mail open the dialler and the mail program; a web site opens only after the
 * warning "Vous quittez PPF", whose first and most visible button keeps the user here. The
 * directory hosts organisations only: no space for a party or a candidate (point of attention 7).
 *
 * [organisationAgenda] receives the organisation's key; null shows "Agenda de l'organisation :
 * bientôt" until the agenda block is plugged in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrganisationPage(
    organisation: Organisation,
    publications: List<SignedEvent>,
    followed: Boolean,
    onToggleFollow: () -> Unit,
    onBack: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    organisationAgenda: (@Composable (pubkeyHex: String, modifier: Modifier) -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val uriHandler = LocalUriHandler.current
    var leavingFor by remember(organisation.address) { mutableStateOf<String?>(null) }
    val open: (String) -> Unit = { uri -> runCatching { uriHandler.openUri(uri) } }

    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(OrganisationPageTags.PAGE)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.organisation_back), onBack, Modifier.testTag(OrganisationPageTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    seed = organisation.slug,
                    letters = OrganisationLabels.initials(organisation.displayName),
                    size = 56.dp,
                    rounded = true,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Spacer(Modifier.width(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    PpfBadge(stringResource(OrganisationLabels.type(organisation.type)), BadgeStyle.BRAND)
                    if (organisation.example) ExampleBadge()
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                organisation.displayName,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }.testTag(OrganisationPageTags.NAME),
            )
            Spacer(Modifier.height(8.dp))
            val revokedOn = organisation.revokedOn
            if (revokedOn != null) {
                // Transparency: the page says the verification was withdrawn, and when, instead of vanishing.
                Column(Modifier.testTag(OrganisationPageTags.REVOKED)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.organisation_revoked_on, revokedOn.toFrench()), style = MaterialTheme.typography.labelLarge, color = palette.ink)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.organisation_revoked_explain), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(OrganisationPageTags.VERIFIED)) {
                    Icon(Lucide.BadgeCheck, contentDescription = null, tint = palette.success, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.organisation_verified_on, organisation.verifiedOn.toFrench()), style = MaterialTheme.typography.labelLarge, color = palette.success)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.MapPin, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (organisation.isNational) stringResource(Res.string.organisation_national) else organisation.territory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(organisation.summary, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            Spacer(Modifier.height(16.dp))

            // Follow (AG-22): only an organisation with an account has publications to follow.
            if (organisation.accountKey != null) {
                FollowButton(followed = followed, onToggle = onToggleFollow, small = false, modifier = Modifier.testTag(OrganisationPageTags.FOLLOW))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.testTag(OrganisationPageTags.FOLLOW_PRIVATE)) {
                    Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.organisation_follow_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
            } else {
                Text(
                    stringResource(Res.string.organisation_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(OrganisationPageTags.NO_ACCOUNT),
                )
            }

            Section(stringResource(Res.string.organisation_presentation))
            SelectionContainer { MarkdownText(organisation.presentation) }

            Section(stringResource(Res.string.organisation_contacts))
            ContactList(organisation, onOpen = open, onWebsite = { leavingFor = it })

            Section(stringResource(Res.string.organisation_publications))
            Publications(organisation, publications, followed)

            Section(stringResource(Res.string.organisation_agenda))
            val key = organisation.accountKey
            if (organisationAgenda != null && key != null) {
                // The tag sits on a box around the plugged agenda, so that the agenda's own tags stay its own.
                Box(Modifier.fillMaxWidth().testTag(OrganisationPageTags.AGENDA)) {
                    organisationAgenda(key, Modifier.fillMaxWidth())
                }
            } else {
                Text(
                    stringResource(Res.string.organisation_agenda_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(OrganisationPageTags.AGENDA_SOON),
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = palette.border)
            Spacer(Modifier.height(12.dp))
            val updated = stringResource(Res.string.organisation_updated, CivilDate.fromEpochSeconds(organisation.updatedAt).toFrench())
            val origin = when (organisation.origin) {
                ContentOrigin.HUMAN -> stringResource(Res.string.organisation_origin_human)
                ContentOrigin.AI_ASSISTED -> stringResource(Res.string.organisation_origin_ai)
                null -> null
            }
            Text(listOfNotNull(updated, origin).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
    }

    leavingFor?.let { website ->
        // The common "Vous quittez PPF" of every space (ui/components), which opens the site itself.
        LeavePpfDialog(
            url = website,
            title = stringResource(Res.string.organisation_leave_title),
            text = stringResource(Res.string.organisation_leave_text, Organisation.hostOf(website)),
            stayLabel = stringResource(Res.string.organisation_leave_stay),
            openLabel = stringResource(Res.string.organisation_leave_go),
            onStay = { leavingFor = null },
            onDone = { leavingFor = null },
            tags = LeaveDialogTags(OrganisationPageTags.LEAVE_DIALOG, OrganisationPageTags.LEAVE_STAY, OrganisationPageTags.LEAVE_GO),
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ContactList(organisation: Organisation, onOpen: (String) -> Unit, onWebsite: (String) -> Unit) {
    val phone = organisation.phone
    val phoneUri = organisation.phoneUri
    val email = organisation.email
    val emailUri = organisation.emailUri
    val nothing = phone == null && email == null && organisation.websites.isEmpty() && organisation.postalAddress == null
    if (nothing) {
        Text(stringResource(Res.string.organisation_contacts_none), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (phone != null && phoneUri != null) {
            ContactRow(
                icon = Lucide.Phone,
                label = stringResource(Res.string.organisation_phone_label),
                value = phone,
                action = stringResource(Res.string.organisation_call, phone),
                onClick = { onOpen(phoneUri) },
                modifier = Modifier.testTag(OrganisationPageTags.PHONE),
            )
        }
        if (email != null && emailUri != null) {
            ContactRow(
                icon = Lucide.Mail,
                label = stringResource(Res.string.organisation_email_label),
                value = email,
                action = stringResource(Res.string.organisation_write, email),
                onClick = { onOpen(emailUri) },
                modifier = Modifier.testTag(OrganisationPageTags.EMAIL),
            )
        }
        organisation.websites.forEachIndexed { index, website ->
            val host = Organisation.hostOf(website)
            ContactRow(
                icon = Lucide.Globe,
                label = stringResource(Res.string.organisation_website_label),
                value = host,
                action = stringResource(Res.string.organisation_visit, host),
                onClick = { onWebsite(website) },
                trailing = Lucide.ExternalLink,
                modifier = Modifier.testTag(OrganisationPageTags.WEBSITE + index),
            )
        }
        organisation.postalAddress?.let { address ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 6.dp).testTag(OrganisationPageTags.ADDRESS), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.MapPin, contentDescription = null, tint = PpfTheme.palette.inkMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.organisation_address_label), style = MaterialTheme.typography.labelMedium, color = PpfTheme.palette.inkSubtle)
                    SelectionContainer { Text(address, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

/** A contact that opens something: one button, at least 48 dp high, whose action screen readers announce. */
@Composable
private fun ContactRow(
    icon: ImageVector,
    label: String,
    value: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
) {
    val palette = PpfTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = palette.brand)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailing, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Publications(organisation: Organisation, publications: List<SignedEvent>, followed: Boolean) {
    val palette = PpfTheme.palette
    if (publications.isEmpty()) {
        Column(Modifier.testTag(OrganisationPageTags.PUBLICATIONS_NONE)) {
            Text(stringResource(Res.string.organisation_publications_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            if (organisation.accountKey != null && !followed) {
                Text(stringResource(Res.string.organisation_publications_follow), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
        }
        return
    }
    val now = remember(publications) { currentEpochSeconds() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(OrganisationPageTags.PUBLICATIONS)) {
        publications.forEach { note ->
            val post = FeedPost(note, editorial = false, pending = false)
            Inset(Modifier.fillMaxWidth().testTag(OrganisationPageTags.PUBLICATION + note.id.take(8))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(RelativeTime.between(note.createdAt, now).label(), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle, modifier = Modifier.weight(1f))
                    if (post.example) ExampleBadge()
                }
                Spacer(Modifier.height(4.dp))
                Text(post.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

