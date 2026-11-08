package org.politisationpopulairefrancaise.ui.agir.manifestation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyNumbers
import org.politisationpopulairefrancaise.ui.agir.security.PreparePhoneBlock
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agir_back
import org.politisationpopulairefrancaise.ui.resources.help_rally_title
import org.politisationpopulairefrancaise.ui.resources.help_vital_text
import org.politisationpopulairefrancaise.ui.resources.manif_check_1
import org.politisationpopulairefrancaise.ui.resources.manif_check_10
import org.politisationpopulairefrancaise.ui.resources.manif_check_2
import org.politisationpopulairefrancaise.ui.resources.manif_check_3
import org.politisationpopulairefrancaise.ui.resources.manif_check_4
import org.politisationpopulairefrancaise.ui.resources.manif_check_5
import org.politisationpopulairefrancaise.ui.resources.manif_check_6
import org.politisationpopulairefrancaise.ui.resources.manif_check_7
import org.politisationpopulairefrancaise.ui.resources.manif_check_8
import org.politisationpopulairefrancaise.ui.resources.manif_check_9
import org.politisationpopulairefrancaise.ui.resources.manif_check_state_done
import org.politisationpopulairefrancaise.ui.resources.manif_check_state_todo
import org.politisationpopulairefrancaise.ui.resources.manif_checklist_text
import org.politisationpopulairefrancaise.ui.resources.manif_checklist_title
import org.politisationpopulairefrancaise.ui.resources.manif_example_text
import org.politisationpopulairefrancaise.ui.resources.manif_example_title
import org.politisationpopulairefrancaise.ui.resources.manif_guide_missing_short
import org.politisationpopulairefrancaise.ui.resources.manif_injury_open
import org.politisationpopulairefrancaise.ui.resources.manif_injury_text
import org.politisationpopulairefrancaise.ui.resources.manif_injury_title
import org.politisationpopulairefrancaise.ui.resources.manif_intro
import org.politisationpopulairefrancaise.ui.resources.manif_legal_title
import org.politisationpopulairefrancaise.ui.resources.manif_numbers_other
import org.politisationpopulairefrancaise.ui.resources.manif_numbers_title
import org.politisationpopulairefrancaise.ui.resources.manif_open_help
import org.politisationpopulairefrancaise.ui.resources.manif_phone_1
import org.politisationpopulairefrancaise.ui.resources.manif_phone_2
import org.politisationpopulairefrancaise.ui.resources.manif_phone_3
import org.politisationpopulairefrancaise.ui.resources.manif_phone_soon
import org.politisationpopulairefrancaise.ui.resources.manif_phone_title
import org.politisationpopulairefrancaise.ui.resources.manif_rights_text
import org.politisationpopulairefrancaise.ui.resources.manif_rights_title
import org.politisationpopulairefrancaise.ui.resources.manif_territory_hint
import org.politisationpopulairefrancaise.ui.resources.manif_territory_label
import org.politisationpopulairefrancaise.ui.resources.manif_testify
import org.politisationpopulairefrancaise.ui.resources.manif_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The items of « Avant de partir », in order ([EXEMPLE], to be read again with the organisations). */
internal val checklist: List<StringResource> = listOf(
    Res.string.manif_check_1, Res.string.manif_check_2, Res.string.manif_check_3, Res.string.manif_check_4, Res.string.manif_check_5,
    Res.string.manif_check_6, Res.string.manif_check_7, Res.string.manif_check_8, Res.string.manif_check_9, Res.string.manif_check_10,
)

/**
 * The protest mode (AG-13), a rubric of "Agir": everything to prepare a protest before leaving,
 * readable offline and without an account. The checklist is ticked in memory only; the guides are
 * those bundled with the application, read here; the territory typed for the legal teams stays in
 * memory; the rally point is the one of the AIDE button, kept on the device. Nothing is sent.
 */
@Composable
fun ManifestationScreen(help: HelpState, windowClass: WindowClass, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    LaunchedEffect(help) { help.prepare() }
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val open = guide
    if (open != null) {
        Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp)) {
            GuideReader(help.guide(open), onBack = { guide = null })
        }
        return
    }
    Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(scroll).padding(if (compact) 16.dp else 24.dp).testTag(ManifestationTags.SCREEN)) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.agir_back), onBack, Modifier.testTag(MANIFESTATION_BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
                PageHeader(stringResource(Res.string.manif_title), stringResource(Res.string.manif_intro))
            }
            PpfPrimaryButton(onClick = help::open, text = stringResource(Res.string.manif_open_help), icon = Lucide.LifeBuoy, modifier = Modifier.testTag(ManifestationTags.OPEN_HELP))
            InfoBox(Lucide.Info) {
                Text(stringResource(Res.string.manif_example_title), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.manif_example_text), style = MaterialTheme.typography.bodyMedium)
            }

            Section(stringResource(Res.string.manif_checklist_title), Lucide.ListChecks) {
                Text(stringResource(Res.string.manif_checklist_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                Spacer(Modifier.height(8.dp))
                checklist.forEachIndexed { index, item -> CheckItem(index, stringResource(item)) }
            }

            Section(stringResource(Res.string.manif_rights_title), Lucide.BookOpen) {
                Text(stringResource(Res.string.manif_rights_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ManifestationGuides.rights.forEach { slug ->
                        val article = help.guide(slug)
                        val label = article?.title?.trimStart()?.removePrefix("[EXEMPLE]")?.trim() ?: (slug + " (" + stringResource(Res.string.manif_guide_missing_short) + ")")
                        GuideLink(label, slug, onOpen = { guide = it })
                    }
                }
            }

            Section(stringResource(Res.string.manif_numbers_title), Lucide.Phone) {
                Text(stringResource(Res.string.help_vital_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                EmergencyNumbersBlock(EmergencyNumbers.emergency)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.manif_numbers_other), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                EmergencyNumbersBlock(EmergencyNumbers.listening)
            }

            Section(stringResource(Res.string.manif_legal_title), Lucide.ShieldCheck) {
                Text(stringResource(Res.string.manif_territory_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                PpfTextField(
                    value = help.territory,
                    onValueChange = help::typeTerritory,
                    placeholder = stringResource(Res.string.manif_territory_label),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(ManifestationTags.TERRITORY),
                )
                Text(stringResource(Res.string.manif_territory_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                Spacer(Modifier.height(8.dp))
                LegalContactsBlock(help)
            }

            Section(stringResource(Res.string.help_rally_title), Lucide.MapPin) {
                RallyPointBlock(help)
            }

            Section(stringResource(Res.string.manif_phone_title), Lucide.Lock) {
                listOf(Res.string.manif_phone_1, Res.string.manif_phone_2, Res.string.manif_phone_3).forEach { tip ->
                    Text("• " + stringResource(tip), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(12.dp))
                // « Préparer mon téléphone » (AG-56): the lock, the hidden content and the quick erase of PPF (M33).
                if (!PreparePhoneBlock()) {
                    Text(stringResource(Res.string.manif_phone_soon), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
            }

            Section(stringResource(Res.string.manif_injury_title), Lucide.Heart, Modifier.testTag(ManifestationTags.INJURY)) {
                Text(stringResource(Res.string.manif_injury_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                Spacer(Modifier.height(8.dp))
                GuideLink(stringResource(Res.string.manif_injury_open), ManifestationGuides.INJURY, onOpen = { guide = it })
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.manif_testify), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The test tag of the protest mode's way back to the home of "Agir". */
const val MANIFESTATION_BACK: String = "manifestation-back"

@Composable
private fun Section(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PpfCard(modifier.fillMaxWidth()) {
        SectionHeading(title, icon)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** One item of the checklist: a checkbox and its sentence, ticked as one control, in memory only. */
@Composable
private fun CheckItem(index: Int, text: String) {
    val palette = PpfTheme.palette
    var done by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (done) Res.string.manif_check_state_done else Res.string.manif_check_state_todo)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { done = it })
            .semantics { stateDescription = state }
            .testTag(ManifestationTags.CHECK + (index + 1)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = palette.brand, uncheckedColor = palette.borderStrong, checkmarkColor = palette.onBrand),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
