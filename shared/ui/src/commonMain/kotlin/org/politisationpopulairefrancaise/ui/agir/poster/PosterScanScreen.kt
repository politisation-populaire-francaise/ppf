package org.politisationpopulairefrancaise.ui.agir.poster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.poster.PosterContent
import org.politisationpopulairefrancaise.core.agir.poster.PosterQr
import org.politisationpopulairefrancaise.core.agir.poster.PosterScan
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaNames
import org.politisationpopulairefrancaise.ui.agir.agenda.PosterAddOutcome
import org.politisationpopulairefrancaise.ui.agir.agenda.StatusPill
import org.politisationpopulairefrancaise.ui.agir.agenda.partyName
import org.politisationpopulairefrancaise.ui.agir.agenda.whenText
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.components.qr.QrScanner
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.affiche_back
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_added
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_add
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_address_call
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_address_looking
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_address_missing
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_again
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_already
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_call_note
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_damaged
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_failed
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_forged
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_intro
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_newer
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_not_event
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_not_ppf
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_open
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_open_call
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_retry
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_signed_org
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_signed_team
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_signed_unknown
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_title
import org.politisationpopulairefrancaise.ui.resources.affiche_scan_verified
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.agir.agenda.typeLabel as mobilizationTypeLabel
import org.politisationpopulairefrancaise.ui.agir.calls.typeLabelResource as callTypeLabelResource

/**
 * « Scanner une affiche » (AG-61, step 1, Android): the camera reads the code of a poster through
 * the common scanner (the permission is asked only when this screen opens). A signed event is
 * checked on the phone, without network: a mobilization is shown with who signed it and « Ajouter à
 * mon agenda » (kept on the device like an event received from the server), a call is shown to read;
 * a forged code is refused with a clear message. A stable address opens the mobilization the agenda
 * holds, after the usual synchronization of the whole agenda when it does not hold it yet. Nothing
 * read is sent anywhere.
 *
 * [onOpened] closes this screen once the agenda shows the mobilization; [onOpenCall] opens a call
 * from its address where the caller can (null: the screen says where to find it). [initial] is a
 * code already read (tests, and the computer, which has no camera here).
 */
@Composable
internal fun PosterScanScreen(
    agenda: AgendaLibrary,
    names: AgendaNames,
    compact: Boolean,
    onBack: () -> Unit,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCall: ((String) -> Unit)? = null,
    initial: PosterScan? = null,
) {
    val palette = PpfTheme.palette
    var scan by remember { mutableStateOf(initial) }
    SystemBack(enabled = true, onBack = onBack)
    Column(
        modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(PosterTags.SCAN_SCREEN),
    ) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.affiche_back), onBack, Modifier.testTag(PosterTags.SCAN_BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.affiche_scan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(6.dp))
                Text(stringResource(Res.string.affiche_scan_intro), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            }
            val current = scan
            if (current == null) {
                // The scanner calls back for every image where it reads a code: the first reading is kept.
                QrScanner(onRead = { text -> if (scan == null) scan = PosterQr.read(text) }, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
            } else {
                Column(Modifier.fillMaxWidth().testTag(PosterTags.SCAN_RESULT), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (current) {
                        is PosterScan.Event -> ReadEvent(current.event, agenda, names, onOpened)
                        is PosterScan.Address -> ReadAddress(current, agenda, onOpened, onOpenCall)
                        PosterScan.Forged -> Message(Lucide.Ban, Res.string.affiche_scan_forged, palette.accentInk)
                        PosterScan.Damaged -> Message(Lucide.TriangleExclamationPoint, Res.string.affiche_scan_damaged, palette.warning)
                        PosterScan.NotPpf -> Message(Lucide.Info, Res.string.affiche_scan_not_ppf, palette.inkMuted)
                    }
                    PpfSecondaryButton(onClick = { scan = null }, text = stringResource(Res.string.affiche_scan_again), icon = Lucide.Camera, modifier = Modifier.testTag(PosterTags.SCAN_AGAIN))
                }
            }
        }
    }
}

/** A verified event: a mobilization to add to the agenda, a call to read, or something else. */
@Composable
private fun ReadEvent(event: SignedEvent, agenda: AgendaLibrary, names: AgendaNames, onOpened: () -> Unit) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var outcome by remember(event.id) { mutableStateOf<PosterAddOutcome?>(null) }
    var busy by remember(event.id) { mutableStateOf(false) }
    val mobilization = remember(event.id) { Mobilization.from(event) }
    val call = remember(event.id) { if (mobilization == null) Call.from(event) else null }
    if (mobilization == null && call == null) {
        Message(Lucide.Info, Res.string.affiche_scan_not_event, palette.inkMuted)
        return
    }
    Message(Lucide.ShieldCheck, Res.string.affiche_scan_verified, palette.success)
    Signer(event.pubkey, names)
    if (mobilization != null) {
        PpfCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mobilizationTypeLabel(mobilization.type), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                Spacer(Modifier.width(8.dp))
                if (mobilization.example) ExampleBadge()
                Spacer(Modifier.width(8.dp))
                StatusPill(mobilization.status)
            }
            Spacer(Modifier.height(6.dp))
            Text(mobilization.displayTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(whenText(mobilization, full = true), style = MaterialTheme.typography.bodyLarge)
            Text(PosterContent.placeOf(mobilization.location, mobilization.territory), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            mobilization.summary?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val done = outcome
        if (done == null || done == PosterAddOutcome.FAILED) {
            PpfPrimaryButton(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            outcome = agenda.addFromPoster(event)
                        } finally {
                            busy = false
                        }
                    }
                },
                text = stringResource(Res.string.affiche_scan_add),
                enabled = !busy,
                icon = Lucide.Calendar,
                modifier = Modifier.testTag(PosterTags.SCAN_ADD),
            )
        }
        if (done != null) {
            val text = when (done) {
                PosterAddOutcome.ADDED -> Res.string.affiche_scan_added
                PosterAddOutcome.ALREADY_THERE -> Res.string.affiche_scan_already
                PosterAddOutcome.NEWER_KNOWN -> Res.string.affiche_scan_newer
                PosterAddOutcome.NOT_A_MOBILIZATION -> Res.string.affiche_scan_not_event
                PosterAddOutcome.FAILED -> Res.string.affiche_scan_failed
            }
            Message(if (done == PosterAddOutcome.FAILED) Lucide.TriangleExclamationPoint else Lucide.CircleCheck, text, if (done == PosterAddOutcome.FAILED) palette.warning else palette.success)
            if (done != PosterAddOutcome.FAILED && done != PosterAddOutcome.NOT_A_MOBILIZATION) {
                PpfPrimaryButton(onClick = onOpened, text = stringResource(Res.string.affiche_scan_open), icon = Lucide.ChevronRight, modifier = Modifier.testTag(PosterTags.SCAN_OPEN))
            }
        }
    } else if (call != null) {
        PpfCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(callTypeLabelResource(call.type)), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                if (call.isExample) {
                    Spacer(Modifier.width(8.dp))
                    ExampleBadge()
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(call.displayTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            call.summary?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            }
            Spacer(Modifier.height(6.dp))
            Text(call.location?.let { PosterContent.placeOf(it, call.territory) } ?: call.territory, style = MaterialTheme.typography.bodyMedium)
            if (call.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Inset(Modifier.fillMaxWidth()) { SelectionContainer { Text(call.text, style = MaterialTheme.typography.bodyMedium) } }
            }
        }
        Message(Lucide.Info, Res.string.affiche_scan_call_note, palette.inkMuted)
    }
}

/** Who signed what was read: the team, a verified organisation, or an account the application does not know. */
@Composable
private fun Signer(key: String, names: AgendaNames) {
    val palette = PpfTheme.palette
    val (icon, text, tint) = when {
        names.isEditorialKey(key) -> Triple(Lucide.BadgeCheck, stringResource(Res.string.affiche_scan_signed_team), palette.success)
        names.isVerified(key) -> Triple(Lucide.BadgeCheck, stringResource(Res.string.affiche_scan_signed_org, partyName(names, key)), palette.success)
        else -> Triple(Lucide.TriangleExclamationPoint, stringResource(Res.string.affiche_scan_signed_unknown, shortNpub(key)), palette.warning)
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.testTag(PosterTags.SCAN_SIGNER), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A stable address: the mobilization the agenda holds (synchronized first when needed), or the way to a call. */
@Composable
private fun ReadAddress(scan: PosterScan.Address, agenda: AgendaLibrary, onOpened: () -> Unit, onOpenCall: ((String) -> Unit)?) {
    val palette = PpfTheme.palette
    val address = scan.address.toString()
    if (scan.address.kind != Mobilization.KIND) {
        if (onOpenCall != null) {
            PpfPrimaryButton(onClick = { onOpenCall(address) }, text = stringResource(Res.string.affiche_scan_open_call), icon = Lucide.ChevronRight, modifier = Modifier.testTag(PosterTags.SCAN_OPEN))
        } else {
            Message(Lucide.Info, Res.string.affiche_scan_address_call, palette.inkMuted)
        }
        return
    }
    var attempt by remember(address) { mutableIntStateOf(0) }
    var found by remember(address) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(address, attempt) {
        found = null
        val opened = agenda.openFromPoster(address)
        found = opened
        if (opened) onOpened()
    }
    when (found) {
        null -> Message(Lucide.RefreshCw, Res.string.affiche_scan_address_looking, palette.inkMuted)
        false -> {
            Message(Lucide.Info, Res.string.affiche_scan_address_missing, palette.inkMuted)
            PpfSecondaryButton(onClick = { attempt++ }, text = stringResource(Res.string.affiche_scan_retry), icon = Lucide.RefreshCw)
        }
        true -> Unit
    }
}

/** One sentence with its icon, announced by screen readers when it appears. */
@Composable
private fun Message(icon: ImageVector, text: StringResource, tint: Color) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag(PosterTags.SCAN_MESSAGE),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}
