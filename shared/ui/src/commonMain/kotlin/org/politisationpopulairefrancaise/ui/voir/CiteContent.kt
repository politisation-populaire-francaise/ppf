package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.Citation
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.voir_cite
import org.politisationpopulairefrancaise.ui.resources.voir_cite_copied

/**
 * « Citer ce contenu » (S-144): copies the reference of the content (publisher, title, dates, version, stable
 * address of the site, fingerprint), the same as under its page on the site ([Citation]). The confirmation is
 * announced to screen readers and disappears after a few seconds.
 */
@Composable
internal fun CiteContent(item: VoirItem, modifier: Modifier = Modifier, now: () -> Long = ::currentEpochSeconds) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember(item.address) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(4_000)
            copied = false
        }
    }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PpfSecondaryButton(
            onClick = {
                val citation = Citation.of(item.article, VoirLinks.urlOf(item), CivilDate.fromEpochSeconds(now()))
                clipboard.setText(AnnotatedString(citation.text))
                copied = true
            },
            text = stringResource(Res.string.voir_cite),
            small = true,
            icon = Lucide.Copy,
            modifier = Modifier.testTag(VoirTags.CITE),
        )
        if (copied) {
            Text(
                stringResource(Res.string.voir_cite_copied),
                style = MaterialTheme.typography.bodySmall,
                color = PpfTheme.palette.success,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
