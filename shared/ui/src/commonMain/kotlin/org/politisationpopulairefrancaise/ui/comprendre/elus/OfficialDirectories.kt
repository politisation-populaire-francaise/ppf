package org.politisationpopulairefrancaise.ui.comprendre.elus

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.LeaveDialogTags
import org.politisationpopulairefrancaise.ui.components.LeavePpfDialog
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.leavingHost
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.elus_directories_title
import org.politisationpopulairefrancaise.ui.resources.elus_directory_an_text
import org.politisationpopulairefrancaise.ui.resources.elus_directory_an_title
import org.politisationpopulairefrancaise.ui.resources.elus_directory_europe_text
import org.politisationpopulairefrancaise.ui.resources.elus_directory_europe_title
import org.politisationpopulairefrancaise.ui.resources.elus_directory_mairies_text
import org.politisationpopulairefrancaise.ui.resources.elus_directory_mairies_title
import org.politisationpopulairefrancaise.ui.resources.elus_directory_rne_text
import org.politisationpopulairefrancaise.ui.resources.elus_directory_rne_title
import org.politisationpopulairefrancaise.ui.resources.elus_directory_senat_text
import org.politisationpopulairefrancaise.ui.resources.elus_directory_senat_title
import org.politisationpopulairefrancaise.ui.resources.elus_leave_open
import org.politisationpopulairefrancaise.ui.resources.elus_leave_stay
import org.politisationpopulairefrancaise.ui.resources.elus_leave_text
import org.politisationpopulairefrancaise.ui.resources.elus_leave_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** One official directory: its key (for the tests), its address and what it offers. */
class OfficialDirectory(val key: String, val url: String, val title: StringResource, val text: StringResource)

/**
 * The official directories that help to find one's elected officials (option C of the entry
 * « Annuaire officiel des élus », while the directory is not in the application or the server does
 * not answer). Addresses checked on their official sites on 7 November 2026.
 */
object OfficialDirectories {
    val all: List<OfficialDirectory> = listOf(
        OfficialDirectory("mairies", "https://lannuaire.service-public.gouv.fr/navigation/mairie", Res.string.elus_directory_mairies_title, Res.string.elus_directory_mairies_text),
        OfficialDirectory("assemblee", "https://www.assemblee-nationale.fr/dyn/vos-deputes", Res.string.elus_directory_an_title, Res.string.elus_directory_an_text),
        OfficialDirectory("senat", "https://www.senat.fr/senateurs/sencir.html", Res.string.elus_directory_senat_title, Res.string.elus_directory_senat_text),
        OfficialDirectory("europe", "https://www.europarl.europa.eu/meps/fr/search/advanced?countryCode=FR", Res.string.elus_directory_europe_title, Res.string.elus_directory_europe_text),
        OfficialDirectory("rne", "https://www.data.gouv.fr/fr/datasets/5c34c4d1634f4173183a64f1/", Res.string.elus_directory_rne_title, Res.string.elus_directory_rne_text),
    )
}

/** The list of the official directories, each behind the warning « Vous quittez PPF ». */
@Composable
fun OfficialDirectoriesSection(intro: String, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(ElusTags.DIRECTORIES)) {
        Text(stringResource(Res.string.elus_directories_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))
        OfficialDirectories.all.forEach { directory ->
            PpfCard(onClick = { onOpen(directory.url) }, modifier = Modifier.fillMaxWidth().testTag(ElusTags.directory(directory.key))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(directory.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(directory.text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                        Text(leavingHost(directory.url), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Lucide.ExternalLink, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** « Vous quittez PPF » before an official directory or the source of the list. */
@Composable
fun ElusLeaveDialog(url: String, onStay: () -> Unit, onDone: () -> Unit) {
    LeavePpfDialog(
        url = url,
        title = stringResource(Res.string.elus_leave_title),
        text = stringResource(Res.string.elus_leave_text, leavingHost(url)),
        stayLabel = stringResource(Res.string.elus_leave_stay),
        openLabel = stringResource(Res.string.elus_leave_open),
        onStay = onStay,
        onDone = onDone,
        tags = LeaveDialogTags(ElusTags.LEAVE_DIALOG, ElusTags.LEAVE_STAY, ElusTags.LEAVE_OPEN),
    )
}
