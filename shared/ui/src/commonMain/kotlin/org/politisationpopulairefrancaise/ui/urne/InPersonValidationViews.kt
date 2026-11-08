package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.urne.UrneAnswer
import org.politisationpopulairefrancaise.core.urne.ValidationAnswer
import org.politisationpopulairefrancaise.core.urne.ValidationProof
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.qr.QrCode
import org.politisationpopulairefrancaise.ui.components.qr.QrScanner
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.validation_close
import org.politisationpopulairefrancaise.ui.resources.validation_code_description
import org.politisationpopulairefrancaise.ui.resources.validation_code_text
import org.politisationpopulairefrancaise.ui.resources.validation_code_title
import org.politisationpopulairefrancaise.ui.resources.validation_code_waiting
import org.politisationpopulairefrancaise.ui.resources.validation_error_expired
import org.politisationpopulairefrancaise.ui.resources.validation_error_full
import org.politisationpopulairefrancaise.ui.resources.validation_error_invalid
import org.politisationpopulairefrancaise.ui.resources.validation_error_network
import org.politisationpopulairefrancaise.ui.resources.validation_error_no_session
import org.politisationpopulairefrancaise.ui.resources.validation_error_not_person
import org.politisationpopulairefrancaise.ui.resources.validation_error_organisation
import org.politisationpopulairefrancaise.ui.resources.validation_error_other
import org.politisationpopulairefrancaise.ui.resources.validation_error_replayed
import org.politisationpopulairefrancaise.ui.resources.validation_error_unavailable
import org.politisationpopulairefrancaise.ui.resources.validation_result_already
import org.politisationpopulairefrancaise.ui.resources.validation_result_pending
import org.politisationpopulairefrancaise.ui.resources.validation_scan
import org.politisationpopulairefrancaise.ui.resources.validation_validator_hint
import org.politisationpopulairefrancaise.ui.resources.validation_validator_key
import org.politisationpopulairefrancaise.ui.resources.validation_validator_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the in-person validation screens (D-112, 4.3). */
object ValidationTags {
    const val SHOW_CODE: String = "validation-show-code"
    const val VALIDATOR: String = "validation-validator"
    const val SESSION_KEY: String = "validation-session-key"
    const val SCAN: String = "validation-scan"
    const val RESULT: String = "validation-result"
}

/** How long a proof is shown before a new one is drawn: well within its five minutes of validity. */
private const val PROOF_RENEWAL_MILLIS: Long = 60_000

/**
 * The citizen's side (D-112, 4.3): the QR code of this account for a validator, a proof signed with the account's
 * key, renewed every minute and accepted once by the server. It holds nothing but the key and the moment.
 */
@Composable
fun ShowMyCodeDialog(voting: VotingState, onClose: () -> Unit) {
    var proof by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val signer = voting.signerProvider() ?: break
            proof = ValidationProof.sign(signer, currentEpochSeconds()).toJson()
            delay(PROOF_RENEWAL_MILLIS)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { PpfTextButton(onClick = onClose, text = stringResource(Res.string.validation_close)) },
        title = { Text(stringResource(Res.string.validation_code_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val shown = proof
                if (shown == null) {
                    Text(stringResource(Res.string.validation_code_waiting))
                } else {
                    QrCode(shown, contentDescription = stringResource(Res.string.validation_code_description), modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp))
                }
                Text(stringResource(Res.string.validation_code_text), style = MaterialTheme.typography.bodyMedium)
            }
        },
        modifier = Modifier.testTag(ValidationTags.SHOW_CODE),
    )
}

/**
 * The validator's side (D-112, 4.3): the key of this device's session (drawn here, kept in memory, to paste in
 * the team's back-office when opening the session), then the reading of the codes shown, each one sent signed with
 * the session key. [scanner] reads the codes (the camera on Android); the tests give their own.
 */
@Composable
fun ValidatorDialog(
    voting: VotingState,
    onClose: () -> Unit,
    scanner: @Composable (onRead: (String) -> Unit) -> Unit = { onRead -> QrScanner(onRead) },
) {
    val keys = remember { voting.validatorKeys ?: IdentityKeys.generate().also { voting.validatorKeys = it } }
    val session = remember(keys) { LocalEventSigner(keys) }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    val seen = remember { mutableSetOf<String>() }
    val results = remember { mutableStateListOf<ValidationResult>() }
    val onRead: (String) -> Unit = { text ->
        val proof = runCatching { SignedEvent.fromJson(text) }.getOrNull()
        when {
            proof == null -> results.add(0, ValidationResult.Refused("invalid_proof"))
            // The camera reads the same code many times: it is sent once.
            seen.add(proof.id) -> scope.launch {
                val outcome = when (val answer = voting.validate(session, proof)) {
                    is UrneAnswer.Ok -> ValidationResult.Answered(answer.value)
                    is UrneAnswer.Failure -> ValidationResult.Refused(answer.code)
                    null -> ValidationResult.Refused("validation_unavailable")
                }
                results.add(0, outcome)
                if (results.size > MAX_RESULTS) results.removeAt(results.lastIndex)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { PpfTextButton(onClick = onClose, text = stringResource(Res.string.validation_close)) },
        title = { Text(stringResource(Res.string.validation_validator_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.validation_validator_key), style = MaterialTheme.typography.labelLarge)
                SelectionContainer {
                    Text(keys.publicKey.npub, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag(ValidationTags.SESSION_KEY))
                }
                Text(stringResource(Res.string.validation_validator_hint), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
                if (scanning) {
                    scanner(onRead)
                } else {
                    PpfPrimaryButton(onClick = { scanning = true }, text = stringResource(Res.string.validation_scan), modifier = Modifier.testTag(ValidationTags.SCAN))
                }
                results.forEach { result -> Text(messageOf(result), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(ValidationTags.RESULT)) }
            }
        },
        modifier = Modifier.testTag(ValidationTags.VALIDATOR),
    )
}

private const val MAX_RESULTS = 5

/** What became of one code read. */
private sealed interface ValidationResult {
    data class Answered(val answer: ValidationAnswer) : ValidationResult

    data class Refused(val code: String?) : ValidationResult
}

@Composable
private fun messageOf(result: ValidationResult): String = when (result) {
    is ValidationResult.Answered -> {
        val week = result.answer.effectiveWeek?.let(CivilDate::parseIso)
        if (result.answer.status == ValidationAnswer.PENDING && week != null) {
            stringResource(Res.string.validation_result_pending, week.toFrench())
        } else {
            stringResource(Res.string.validation_result_already)
        }
    }
    is ValidationResult.Refused -> ERRORS[result.code]?.let { stringResource(it) }
        ?: if (result.code == null) stringResource(Res.string.validation_error_network) else stringResource(Res.string.validation_error_other, result.code)
}

private val ERRORS: Map<String, StringResource> = mapOf(
    "no_open_session" to Res.string.validation_error_no_session,
    "session_full" to Res.string.validation_error_full,
    "expired_proof" to Res.string.validation_error_expired,
    "proof_replayed" to Res.string.validation_error_replayed,
    "invalid_proof" to Res.string.validation_error_invalid,
    "not_a_person" to Res.string.validation_error_not_person,
    "organisation_not_verified" to Res.string.validation_error_organisation,
    "validation_unavailable" to Res.string.validation_error_unavailable,
)
