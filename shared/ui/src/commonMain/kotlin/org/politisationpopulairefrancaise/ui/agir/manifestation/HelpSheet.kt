package org.politisationpopulairefrancaise.ui.agir.manifestation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyNumbers
import org.politisationpopulairefrancaise.ui.agir.security.HelpWipeBlock
import org.politisationpopulairefrancaise.ui.agir.security.LocalDeviceSecurity
import org.politisationpopulairefrancaise.ui.agir.timer.TimerAlerts
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.help_button
import org.politisationpopulairefrancaise.ui.resources.help_button_description
import org.politisationpopulairefrancaise.ui.resources.help_close
import org.politisationpopulairefrancaise.ui.resources.help_exit_text
import org.politisationpopulairefrancaise.ui.resources.help_exit_title
import org.politisationpopulairefrancaise.ui.resources.help_lawyer_alert
import org.politisationpopulairefrancaise.ui.resources.help_lawyer_alert_text
import org.politisationpopulairefrancaise.ui.resources.help_lawyer_guide
import org.politisationpopulairefrancaise.ui.resources.help_lawyer_text
import org.politisationpopulairefrancaise.ui.resources.help_lawyer_title
import org.politisationpopulairefrancaise.ui.resources.help_medics_text
import org.politisationpopulairefrancaise.ui.resources.help_medics_title
import org.politisationpopulairefrancaise.ui.resources.help_rally_title
import org.politisationpopulairefrancaise.ui.resources.help_talk_associations
import org.politisationpopulairefrancaise.ui.resources.help_talk_associations_source
import org.politisationpopulairefrancaise.ui.resources.help_talk_source
import org.politisationpopulairefrancaise.ui.resources.help_talk_text
import org.politisationpopulairefrancaise.ui.resources.help_talk_title
import org.politisationpopulairefrancaise.ui.resources.help_title
import org.politisationpopulairefrancaise.ui.resources.help_vital_text
import org.politisationpopulairefrancaise.ui.resources.help_vital_title
import org.politisationpopulairefrancaise.ui.resources.sec_help_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Ids of the functions the sheet announces for later (tests, [ManifestationTags.SOON]). */
object HelpSoon {
    const val LAWYER_ALERT = "avocat"
    const val EXIT = "sortir"
    const val MEDICS = "street-medics"
}

/**
 * The AIDE button (AG-27, D-044): the accent colour, the word « AIDE » always written, one gesture
 * to the emergency sheet. It floats above the bottom bar on phones, heads the rail on tablets and
 * the side bar on computers. [fullWidth] fills the side bar. Nothing without the shell's help state.
 */
@Composable
fun HelpButton(modifier: Modifier = Modifier, fullWidth: Boolean = false, stacked: Boolean = false, compact: Boolean = false) {
    val help = LocalHelpState.current ?: return
    val palette = PpfTheme.palette
    val description = stringResource(Res.string.help_button_description)
    Button(
        onClick = help::open,
        shape = if (stacked) MaterialTheme.shapes.medium else CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        border = BorderStroke(2.dp, palette.surface0),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        contentPadding = when {
            stacked -> PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            compact -> PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            else -> PaddingValues(horizontal = 18.dp, vertical = 0.dp)
        },
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = if (compact) 44.dp else 56.dp)
            .semantics { contentDescription = description }
            .testTag(ManifestationTags.HELP_BUTTON),
    ) {
        if (stacked) {
            // The narrow rail of tablets: the icon above the word.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Lucide.LifeBuoy, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(stringResource(Res.string.help_button), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
            }
        } else {
            Icon(Lucide.LifeBuoy, contentDescription = null, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            Text(stringResource(Res.string.help_button), fontWeight = FontWeight.Bold, fontSize = if (compact) 16.sp else 18.sp, letterSpacing = 1.sp)
        }
    }
}

/**
 * The emergency sheet, over every screen while [HelpState.isOpen]: first the 15 and the 112, then
 * the 17, the 18 and the 114 by text message; « Besoin d'un avocat »; the functions announced for
 * later; the rally point. A guide opened from it is read in the same window, with a way back.
 */
@Composable
fun HelpSheetHost(help: HelpState) {
    if (!help.isOpen) return
    // The window of the sheet dials with the same handler as the screen below it (the platform's, or a test's).
    val uriHandler = LocalUriHandler.current
    Dialog(
        onDismissRequest = help::back,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val title = stringResource(Res.string.help_title)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = PpfTheme.palette.surface0,
                contentColor = PpfTheme.palette.ink,
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight)
                    .semantics { paneTitle = title }
                    .testTag(ManifestationTags.SHEET),
            ) {
                val guide = help.openGuide
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (guide != null) {
                        GuideReader(help.guide(guide), onBack = help::closeGuide)
                    } else {
                        HelpSheetContent(help, title)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun HelpSheetContent(help: HelpState, title: String) {
    val palette = PpfTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.LifeBuoy, contentDescription = null, tint = palette.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        PpfIconButton(Lucide.X, stringResource(Res.string.help_close), help::close, Modifier.testTag(ManifestationTags.SHEET_CLOSE), tint = palette.ink)
    }
    Spacer(Modifier.height(12.dp))

    // PPF never replaces the emergency services: the 15 and the 112 come before anything else.
    SectionHeading(stringResource(Res.string.help_vital_title), Lucide.TriangleExclamationPoint, color = palette.accentInk)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(Res.string.help_vital_text), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    EmergencyNumbersBlock(EmergencyNumbers.emergency)

    // AG-55: the alert of a relative without news, right after the emergency numbers (which always come first).
    val timer = help.timer
    if (timer != null && timer.alerts.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        TimerAlerts(timer)
    }

    SheetDivider()
    SectionHeading(stringResource(Res.string.help_lawyer_title), Lucide.ShieldCheck)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(Res.string.help_lawyer_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    Spacer(Modifier.height(8.dp))
    LegalContactsBlock(help)
    Spacer(Modifier.height(8.dp))
    GuideLink(stringResource(Res.string.help_lawyer_guide), ManifestationGuides.CUSTODY, help::showGuide)
    Spacer(Modifier.height(8.dp))
    SoonItem(HelpSoon.LAWYER_ALERT, stringResource(Res.string.help_lawyer_alert), stringResource(Res.string.help_lawyer_alert_text), Lucide.Send)

    // « Besoin de parler ? » (D-081, point 3): the listening lines, apart from the protest mode; never a vital emergency.
    SheetDivider()
    Column(Modifier.testTag(ManifestationTags.TALK)) {
        SectionHeading(stringResource(Res.string.help_talk_title), Lucide.MessageCircle)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.help_talk_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        EmergencyNumbersBlock(EmergencyNumbers.talk, source = Res.string.help_talk_source)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.help_talk_associations), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        EmergencyNumbersBlock(EmergencyNumbers.talkAssociations, source = Res.string.help_talk_associations_source)
    }

    SheetDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SoonItem(HelpSoon.MEDICS, stringResource(Res.string.help_medics_title), stringResource(Res.string.help_medics_text), Lucide.LifeBuoy)
        SoonItem(HelpSoon.EXIT, stringResource(Res.string.help_exit_title), stringResource(Res.string.help_exit_text), Lucide.HandHelping)
    }

    SheetDivider()
    SectionHeading(stringResource(Res.string.help_rally_title), Lucide.MapPin)
    Spacer(Modifier.height(4.dp))
    RallyPointBlock(help)

    // The quick erase (S-44), last: never pressed by mistake on the way to the numbers.
    if (LocalDeviceSecurity.current != null) {
        SheetDivider()
        SectionHeading(stringResource(Res.string.sec_help_title), Lucide.TriangleExclamationPoint)
        Spacer(Modifier.height(4.dp))
        HelpWipeBlock()
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.padding(vertical = 16.dp)) { HorizontalDivider(color = PpfTheme.palette.border) }
}
