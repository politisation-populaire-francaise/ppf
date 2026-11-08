package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.CheckOutcome
import org.politisationpopulairefrancaise.core.editorial.CheckQuery
import org.politisationpopulairefrancaise.core.editorial.CheckResult
import org.politisationpopulairefrancaise.core.editorial.ContentCheck
import org.politisationpopulairefrancaise.core.editorial.ContentLookup
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * « Vérifier un contenu PPF » (S-141) in the application, even offline: the same check as the page /verifier of
 * the site ([ContentCheck]), on the trusted keys and the local copy of the device ([lookup]). Nothing is sent.
 */
@Composable
internal fun VerifyPage(lookup: () -> ContentLookup?, catalog: VoirCatalog, onOpen: (ContentAddress) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val device = remember { lookup() }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.voir_verify_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.voir_verify_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(16.dp))
        if (device == null) {
            Text(stringResource(Res.string.voir_verify_unavailable), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        PpfTextField(
            value = input,
            onValueChange = {
                input = it
                result = null
            },
            placeholder = stringResource(Res.string.voir_verify_field),
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag(VoirTags.VERIFY_INPUT),
        )
        Spacer(Modifier.height(8.dp))
        PpfPrimaryButton(
            onClick = {
                val query = input
                scope.launch { result = withContext(Dispatchers.Default) { ContentCheck.check(CheckQuery.parse(query), device) } }
            },
            text = stringResource(Res.string.voir_verify_button),
            icon = Lucide.ShieldCheck,
            modifier = Modifier.testTag(VoirTags.VERIFY_SUBMIT),
        )
        result?.let {
            Spacer(Modifier.height(16.dp))
            VerifyVerdict(it, catalog, onOpen)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.voir_verify_how), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@Composable
private fun VerifyVerdict(result: CheckResult, catalog: VoirCatalog, onOpen: (ContentAddress) -> Unit) {
    val palette = PpfTheme.palette
    val host = result.host.orEmpty()
    val (tone, title, text) = when (result.outcome) {
        CheckOutcome.AUTHENTIC_CURRENT -> Triple(Tone.OK, Res.string.voir_verify_authentic_current_title, stringResource(Res.string.voir_verify_authentic_current))
        CheckOutcome.AUTHENTIC_OUTDATED -> Triple(Tone.WARN, Res.string.voir_verify_authentic_outdated_title, stringResource(Res.string.voir_verify_authentic_outdated))
        CheckOutcome.AUTHENTIC_UNLISTED -> Triple(Tone.WARN, Res.string.voir_verify_authentic_unlisted_title, stringResource(Res.string.voir_verify_authentic_unlisted))
        CheckOutcome.NOT_PPF_KEY -> Triple(Tone.BAD, Res.string.voir_verify_not_ppf_title, stringResource(Res.string.voir_verify_not_ppf))
        CheckOutcome.ALTERED -> Triple(Tone.BAD, Res.string.voir_verify_altered_title, stringResource(Res.string.voir_verify_altered))
        CheckOutcome.UNKNOWN -> Triple(Tone.BAD, Res.string.voir_verify_unknown_title, stringResource(Res.string.voir_verify_unknown))
        CheckOutcome.OFFICIAL_LINK -> Triple(Tone.OK, Res.string.voir_verify_official_title, stringResource(Res.string.voir_verify_official, host))
        CheckOutcome.UNOFFICIAL_LINK -> Triple(Tone.BAD, Res.string.voir_verify_unofficial_title, stringResource(Res.string.voir_verify_unofficial, host))
        CheckOutcome.UNREADABLE -> Triple(Tone.BAD, Res.string.voir_verify_unreadable_title, stringResource(Res.string.voir_verify_unreadable))
    }
    val (container, accent) = when (tone) {
        Tone.OK -> palette.successTint to palette.success
        Tone.WARN -> palette.warningTint to palette.warning
        Tone.BAD -> palette.accentTint to palette.accent
    }
    // The verdict is said in words first; its colour only repeats them. Announced to screen readers.
    Surface(
        color = container,
        contentColor = palette.ink,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().border(2.dp, accent, MaterialTheme.shapes.medium).testTag(VoirTags.VERIFY_RESULT).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            val authentic = result.outcome == CheckOutcome.AUTHENTIC_CURRENT || result.outcome == CheckOutcome.AUTHENTIC_OUTDATED || result.outcome == CheckOutcome.AUTHENTIC_UNLISTED
            if (authentic) result.event?.let { VersionFacts(it, stringResource(Res.string.voir_verify_checked)) }
            if (result.outcome == CheckOutcome.AUTHENTIC_OUTDATED) result.current?.let { VersionFacts(it, stringResource(Res.string.voir_verify_current)) }
            // The content itself, when « Voir » has it on the device.
            val shown = (result.current ?: result.event)?.takeIf { authentic }?.let { Article.from(it)?.address }?.takeIf { catalog.item(it) != null }
            shown?.let { address ->
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton(onClick = { onOpen(address) }, text = stringResource(Res.string.voir_verify_open_content), small = true, modifier = Modifier.testTag(VoirTags.VERIFY_OPEN_CONTENT))
            }
        }
    }
}

/** What the person compares with what they received: title, date of the version, code and fingerprint. */
@Composable
private fun VersionFacts(event: SignedEvent, label: String) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(12.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
    Article.from(event)?.let { Text(it.title, style = MaterialTheme.typography.bodyLarge) }
    Text(stringResource(Res.string.voir_verify_version_of, CivilDate.fromEpochSeconds(event.createdAt).toFrench()), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(Res.string.voir_verify_code, ContentCheck.codeOf(event)), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(Res.string.voir_verify_fingerprint, event.id), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
}

private enum class Tone { OK, WARN, BAD }
