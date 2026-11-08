package org.politisationpopulairefrancaise.ui.comprendre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
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
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.Citation
import org.politisationpopulairefrancaise.core.editorial.ContentCheck
import org.politisationpopulairefrancaise.core.editorial.ContentLicenses
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_cite
import org.politisationpopulairefrancaise.ui.resources.comprendre_cite_copied
import org.politisationpopulairefrancaise.ui.resources.comprendre_license
import org.politisationpopulairefrancaise.ui.resources.comprendre_verify_code
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the end of a content of « Comprendre » (licence, verification code, « Citer ce contenu »). */
object ComprendreCiteTags {
    const val BLOCK = "comprendre-cite-block"
    const val LICENSE = "comprendre-license"
    const val CODE = "comprendre-verify-code"
    const val CITE = "comprendre-cite"
}

/**
 * The end of every content of « Comprendre » in the application, as under its page on the site:
 *
 * - the licence of the text (D-103, `ContentLicenses.of(type)`; none for a text whose rights stay with its author);
 * - the verification code of this version (S-141), the same as on the site and on the shared images, selectable,
 *   so that a copy received elsewhere can be checked in « Voir » (« Vérifier un contenu reçu ») or on the site;
 * - « Citer ce contenu » (S-144): copies the reference of its page on the site ([Citation], the same text as the
 *   site), with the day of the reader. A content without a page on the site has no reference to copy.
 *
 * The confirmation of the copy is announced to screen readers and disappears after a few seconds. Nothing is sent.
 */
@Composable
internal fun ComprendreCite(
    article: Article,
    modifier: Modifier = Modifier,
    path: String? = ComprendreLinks.pathOf(article),
    now: () -> Long = ::currentEpochSeconds,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember(article.event.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(4_000)
            copied = false
        }
    }
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(ComprendreCiteTags.BLOCK)) {
        ContentLicenses.of(article.type)?.let { license ->
            Text(
                stringResource(Res.string.comprendre_license, license.shortName, ContentLicenses.ATTRIBUTION),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
                modifier = Modifier.testTag(ComprendreCiteTags.LICENSE),
            )
            Spacer(Modifier.height(4.dp))
        }
        SelectionContainer {
            Text(
                stringResource(Res.string.comprendre_verify_code, ContentCheck.codeOf(article.event)),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
                modifier = Modifier.testTag(ComprendreCiteTags.CODE),
            )
        }
        if (path != null) {
            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                PpfSecondaryButton(
                    onClick = {
                        val citation = Citation.of(article, ComprendreLinks.urlOf(path), CivilDate.fromEpochSeconds(now()))
                        clipboard.setText(AnnotatedString(citation.text))
                        copied = true
                    },
                    text = stringResource(Res.string.comprendre_cite),
                    small = true,
                    icon = Lucide.Copy,
                    modifier = Modifier.testTag(ComprendreCiteTags.CITE),
                )
                if (copied) {
                    Text(
                        stringResource(Res.string.comprendre_cite_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.success,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}
