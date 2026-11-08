package org.politisationpopulairefrancaise.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.politisationpopulairefrancaise.core.identity.AccountRecoveryClient
import org.politisationpopulairefrancaise.core.identity.AccountStatus
import org.politisationpopulairefrancaise.core.identity.AccountStatusResult
import org.politisationpopulairefrancaise.core.identity.AmbiguousPhraseLanguageException
import org.politisationpopulairefrancaise.core.identity.CommitmentOutcome
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.InvalidRecoveryPhraseException
import org.politisationpopulairefrancaise.core.identity.Nip19
import org.politisationpopulairefrancaise.core.identity.PhraseLanguage
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.publication.CompromisedRefusals
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.text.normalizeNfkd
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** What "Mon espace" shows about the account. */
sealed interface AccountUiState {
    data object Loading : AccountUiState

    /** The stored record could not be read: never offer to create a new account over it. */
    data object Unreadable : AccountUiState

    data object NoAccount : AccountUiState

    data class Creating(
        val phrase: RecoveryPhrase,
        val step: CreationStep,
        /** Zero-based positions of the words asked back at the verification step. */
        val checkedPositions: List<Int>,
        val verificationFailed: Boolean = false,
        /** The user chose, after the warning, to keep the phrase on the device (principle 10). */
        val keepPhrase: Boolean = false,
    ) : AccountUiState

    data class Restoring(val error: RestoreError? = null) : AccountUiState

    data class Ready(
        val publicKey: PublicKey,
        val phrase: PhraseOnDevice,
        /** The user postponed the check of their copy and the reminder is due (a week later, D-134). */
        val reminderDue: Boolean = false,
        val revealed: Revealed? = null,
    ) : AccountUiState {
        val hasRecoveryPhrase: Boolean get() = phrase == PhraseOnDevice.KEPT || phrase == PhraseOnDevice.AWAITING_CHECK
    }
}

/** Where the recovery phrase stands on this device (S-146, D-134). */
enum class PhraseOnDevice {
    /** Checked by the user and kept on purpose, after the warning. */
    KEPT,

    /** On the device until the user checks their copy (check postponed, or account created before S-146). */
    AWAITING_CHECK,

    /** Checked, then erased from the device: the default. */
    ERASED,

    /** Never known to the application (imported by its secret key). */
    NONE,
}

enum class CreationStep { SHOW_PHRASE, VERIFY }

sealed interface RestoreError {
    data object WordCount : RestoreError
    data class UnknownWords(val words: List<String>) : RestoreError
    data object Checksum : RestoreError
    data class AmbiguousLanguage(val candidates: List<PhraseLanguage>) : RestoreError
    data object InvalidSecretKey : RestoreError
}

/** A secret shown on screen after the user confirmed the warning. */
sealed interface Revealed {
    data class Phrase(val phrase: RecoveryPhrase) : Revealed
    data class SecretKey(val nsec: String) : Revealed
}

/** Where the recovery key of the account stands with the server (S-146). */
enum class RecoveryKeyState {
    /** Not known yet: no server, or it has not answered. */
    UNKNOWN,

    /** Derived on this device, its commitment waits for the server. */
    PENDING,

    /** The server holds the commitment of the account. */
    SET,

    /** The account has no recovery key and the phrase is not on the device: the user must type it. */
    MISSING,

    /** The commitment on record names another recovery key than this phrase's. */
    MISMATCH,
}

/** The user's own account is declared compromised: since when, and the successor it names. */
data class OwnCompromise(val since: Long, val declaredAt: Long?, val successor: String?)

/** "Vérifier ma phrase": the check in progress, in Mon espace. */
data class PhraseCheckState(
    /** Zero-based positions asked when the phrase is on the device; null asks the whole phrase. */
    val positions: List<Int>?,
    /** Keep the phrase on the device after the check (chosen after a warning); erased otherwise. */
    val keep: Boolean,
    val failure: PhraseCheckFailure? = null,
    /** The check succeeded; [keep] then tells what became of the phrase. */
    val done: Boolean = false,
)

sealed interface PhraseCheckFailure {
    /** A word typed back does not match. */
    data object WrongWords : PhraseCheckFailure

    /** A valid phrase, but of another account. */
    data object OtherAccount : PhraseCheckFailure

    data class Invalid(val error: RestoreError) : PhraseCheckFailure
}

/**
 * Drives the account screens and talks to the device's [IdentityStore]. One instance lives for
 * the whole application; the keys stay in memory only while the session is alive.
 *
 * Since S-146 (D-134, docs/integration/compte-compromis.md): the phrase is erased from the device
 * once the user typed it back (kept only on request, after a warning); the commitment to the
 * recovery key (kind 1776) is published once through [remote]; the account's status tells when it
 * is declared compromised; [lostControl] declares an account compromised from its phrase.
 */
class AccountSession(
    private val store: IdentityStore,
    private val scope: CoroutineScope,
    /** Called once the keys are erased, and awaited: what else the device must forget (the followed accounts). */
    private val onSignOut: suspend () -> Unit = {},
    private val pickPositions: () -> List<Int> = { (0 until RecoveryPhrase.DEFAULT_WORD_COUNT).shuffled().take(3).sorted() },
    /** The server, through the single publication port; null keeps everything on the device. */
    private val remote: RemoteEvents? = null,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    var state: AccountUiState by mutableStateOf(AccountUiState.Loading)
        private set

    /** Where the recovery key stands with the server. */
    var recoveryKey: RecoveryKeyState by mutableStateOf(RecoveryKeyState.UNKNOWN)
        private set

    /** Set when the account of this device is declared compromised. */
    var compromise: OwnCompromise? by mutableStateOf(null)
        private set

    /** The check of the phrase in progress ("Vérifier ma phrase"), or null. */
    var phraseCheck: PhraseCheckState? by mutableStateOf(null)
        private set

    /** "J'ai perdu le contrôle de mon compte". */
    val lostControl: LostControlFlow = LostControlFlow(this, remote, clock)

    private var keys: IdentityKeys? = null
    private var stored: StoredIdentity? = null
    private val recoveryRound = Mutex()

    /** Signs on behalf of the account, when there is one. */
    val signer: EventSigner? get() = keys?.let(::LocalEventSigner)

    /**
     * Signs and encrypts on behalf of the account for the private messages (NIP-17, decision
     * D-027), when there is one. Like [signer], it never exposes the keys themselves.
     */
    val messagingSigner: MessagingSigner? get() = keys?.let(::LocalMessagingSigner)

    /** The public key of the account on this device, lowercase hex. */
    val accountKey: String? get() = keys?.publicKey?.hex

    fun start() {
        scope.launch {
            state = try {
                val record = store.load()
                if (record == null) AccountUiState.NoAccount else ready(record)
            } catch (_: Exception) {
                AccountUiState.Unreadable
            }
            syncRecovery()
        }
        // A publication refused as compromised, from any screen: read the status at once.
        (remote as? CompromisedRefusals)?.let { refusals ->
            scope.launch {
                refusals.refusedAsCompromised.collect { refused ->
                    val key = accountKey
                    if (key != null && key in refused && compromise == null) syncRecovery()
                }
            }
        }
    }

    fun beginCreation() {
        lostControl.cancel()
        state = AccountUiState.Creating(RecoveryPhrase.generate(), CreationStep.SHOW_PHRASE, pickPositions())
    }

    fun phraseNoted() {
        val creating = state as? AccountUiState.Creating ?: return
        state = creating.copy(step = CreationStep.VERIFY, verificationFailed = false)
    }

    fun reviewPhrase() {
        val creating = state as? AccountUiState.Creating ?: return
        state = creating.copy(step = CreationStep.SHOW_PHRASE, verificationFailed = false)
    }

    /** Keeps the phrase on the device after the creation (the screen asked the warning first), or not. */
    fun setCreationKeepPhrase(keep: Boolean) {
        val creating = state as? AccountUiState.Creating ?: return
        state = creating.copy(keepPhrase = keep)
    }

    /**
     * [answers] are the words typed for [AccountUiState.Creating.checkedPositions], in the same order.
     * The account is created and the phrase erased from the device, unless the user chose to keep it.
     */
    fun verifyAndCreate(answers: List<String>) {
        val creating = state as? AccountUiState.Creating ?: return
        if (!matches(creating.phrase, creating.checkedPositions, answers)) {
            state = creating.copy(verificationFailed = true)
            return
        }
        val phrase = creating.phrase
        val created = phrase.deriveKeys()
        persist(created, StoredIdentity.of(created, if (creating.keepPhrase) phrase else null, verified = true, pendingRecoveryKey = StoredIdentity.recoveryKeyOf(phrase)))
    }

    /**
     * Creates the account without the check: the phrase stays on the device until the user checks
     * their copy, and Mon espace reminds them a week later (D-134).
     */
    fun postponeVerification() {
        val creating = state as? AccountUiState.Creating ?: return
        val phrase = creating.phrase
        val keysOfPhrase = phrase.deriveKeys()
        persist(keysOfPhrase, StoredIdentity.of(keysOfPhrase, phrase, verified = false, checkDueAt = clock() + REMINDER_DELAY_SECONDS))
    }

    fun cancel() {
        lostControl.cancel()
        state = AccountUiState.NoAccount
    }

    fun beginRestore() {
        lostControl.cancel()
        state = AccountUiState.Restoring()
    }

    /**
     * Restores from twelve words or from an "nsec1…" secret key; [language] settles an ambiguity.
     * The words were just typed in full: they count as checked and are not kept on the device.
     */
    fun restore(input: String, language: PhraseLanguage? = null) {
        val text = input.trim()
        if (text.startsWith(Nip19.SECRET_KEY + "1", ignoreCase = true)) {
            val restored = runCatching { IdentityKeys.fromNsec(text.lowercase()) }.getOrNull()
            if (restored == null) {
                state = AccountUiState.Restoring(RestoreError.InvalidSecretKey)
            } else {
                persist(restored, StoredIdentity.of(restored, null))
            }
            return
        }
        val phrase = when (val parsed = parsePhrase(text, language)) {
            is ParsedPhrase.Valid -> parsed.phrase
            is ParsedPhrase.Invalid -> {
                state = AccountUiState.Restoring(parsed.error)
                return
            }
        }
        val restored = phrase.deriveKeys()
        persist(restored, StoredIdentity.of(restored, null, verified = true, pendingRecoveryKey = StoredIdentity.recoveryKeyOf(phrase)))
    }

    fun reveal(revealed: Revealed) {
        val ready = state as? AccountUiState.Ready ?: return
        state = ready.copy(revealed = revealed)
    }

    fun revealPhrase() {
        stored?.recoveryPhrase()?.let { reveal(Revealed.Phrase(it)) }
    }

    fun revealSecretKey() {
        keys?.let { reveal(Revealed.SecretKey(it.nsec)) }
    }

    fun hideRevealed() {
        val ready = state as? AccountUiState.Ready ?: return
        state = ready.copy(revealed = null)
    }

    /**
     * Starts "Vérifier ma phrase": three words when the phrase is on the device, the whole phrase
     * otherwise. [keep] asks to keep the phrase on the device afterwards (the screen showed the
     * warning first); a phrase already kept on purpose stays kept unless the user says otherwise.
     */
    fun beginPhraseCheck(keep: Boolean? = null) {
        val record = stored ?: return
        hideRevealed()
        val onDevice = record.recoveryWords != null
        phraseCheck = PhraseCheckState(
            positions = if (onDevice) pickPositions() else null,
            keep = keep ?: (onDevice && record.phraseVerified),
        )
    }

    fun setCheckKeep(keep: Boolean) {
        phraseCheck = phraseCheck?.copy(keep = keep)
    }

    fun closePhraseCheck() {
        phraseCheck = null
    }

    /** The words typed for [PhraseCheckState.positions], in the same order. */
    fun submitPhraseWords(answers: List<String>) {
        val check = phraseCheck ?: return
        val positions = check.positions ?: return
        val phrase = stored?.recoveryPhrase() ?: return
        if (!matches(phrase, positions, answers)) {
            phraseCheck = check.copy(failure = PhraseCheckFailure.WrongWords)
            return
        }
        phraseChecked(phrase, check)
    }

    /** The whole phrase typed again; it must give this account's key. */
    fun submitWholePhrase(text: String, language: PhraseLanguage? = null) {
        val check = phraseCheck ?: return
        val current = keys ?: return
        val phrase = when (val parsed = parsePhrase(text, language)) {
            is ParsedPhrase.Valid -> parsed.phrase
            is ParsedPhrase.Invalid -> {
                phraseCheck = check.copy(failure = PhraseCheckFailure.Invalid(parsed.error))
                return
            }
        }
        val derived = phrase.deriveKeys()
        val same = derived.publicKey == current.publicKey
        derived.destroy()
        if (!same) {
            phraseCheck = check.copy(failure = PhraseCheckFailure.OtherAccount)
            return
        }
        phraseChecked(phrase, check)
    }

    /** Erases the phrase kept on this device (the protective choice: no warning needed). */
    fun erasePhrase() {
        val record = stored ?: return
        val phrase = record.recoveryPhrase() ?: return
        if (!record.phraseVerified) return
        save(record.with(phrase = null, pendingRecoveryKey = pendingFor(record, phrase)))
    }

    /** Erases the keys from this device. Without the recovery phrase, the account is gone. */
    fun signOut() {
        scope.launch {
            store.clear()
            keys?.destroy()
            keys = null
            stored = null
            compromise = null
            phraseCheck = null
            recoveryKey = RecoveryKeyState.UNKNOWN
            state = AccountUiState.NoAccount
            onSignOut()
        }
    }

    /** Reads the status of the account again (the "Réessayer" of Mon espace). */
    fun refreshRecovery() {
        scope.launch { syncRecovery() }
    }

    /** Called by [lostControl] once a notice about the account of this device was accepted. */
    internal fun markCompromised(since: Long, declaredAt: Long?, successor: String?) {
        compromise = OwnCompromise(since, declaredAt, successor)
    }

    /** Called by [lostControl]: the device's account is replaced by the successor just created. */
    internal fun adoptSuccessor(newKeys: IdentityKeys, record: StoredIdentity) {
        compromise = null
        recoveryKey = RecoveryKeyState.UNKNOWN
        persist(newKeys, record)
    }

    private fun phraseChecked(phrase: RecoveryPhrase, check: PhraseCheckState) {
        val record = stored ?: return
        val updated = record.with(
            phrase = if (check.keep) phrase else null,
            phraseVerified = true,
            phraseCheckDueAt = null,
            pendingRecoveryKey = pendingFor(record, phrase),
        )
        phraseCheck = check.copy(failure = null, done = true)
        save(updated)
    }

    /**
     * The recovery key whose commitment must still be sent: the one already waiting, or the one of
     * [phrase] while the server is not known to hold a commitment (accounts created before S-146,
     * accounts imported by their secret key, D-134 section 2).
     */
    private fun pendingFor(record: StoredIdentity, phrase: RecoveryPhrase): String? =
        record.pendingRecoveryKey ?: if (record.recoveryCommitment == null && recoveryKey != RecoveryKeyState.SET) StoredIdentity.recoveryKeyOf(phrase) else null

    private fun save(record: StoredIdentity) {
        val current = keys ?: return
        scope.launch {
            store.save(record)
            if (keys !== current) return@launch
            val shown = state as? AccountUiState.Ready
            state = ready(record, current).copy(revealed = shown?.revealed?.takeIf { record.recoveryWords != null || it !is Revealed.Phrase })
            syncRecovery()
        }
    }

    private fun persist(newKeys: IdentityKeys, record: StoredIdentity) {
        phraseCheck = null
        scope.launch {
            store.save(record)
            state = ready(record, newKeys)
            syncRecovery()
        }
    }

    private fun ready(record: StoredIdentity, loadedKeys: IdentityKeys = record.keys()): AccountUiState.Ready {
        if (keys?.publicKey != loadedKeys.publicKey) {
            compromise = null
            recoveryKey = RecoveryKeyState.UNKNOWN
        }
        keys?.takeIf { it !== loadedKeys }?.destroy()
        keys = loadedKeys
        stored = record
        val phrase = when {
            record.recoveryWords == null -> if (record.phraseVerified) PhraseOnDevice.ERASED else PhraseOnDevice.NONE
            record.phraseVerified -> PhraseOnDevice.KEPT
            else -> PhraseOnDevice.AWAITING_CHECK
        }
        val due = record.awaitsPhraseCheck && (record.phraseCheckDueAt?.let { clock() >= it } ?: true)
        return AccountUiState.Ready(loadedKeys.publicKey, phrase, reminderDue = due)
    }

    /**
     * Publishes the commitment to the recovery key when it still waits, then reads the status of
     * the account (compromised or not, recovery key on record or not). One round at a time.
     */
    private suspend fun syncRecovery() {
        recoveryRound.withLock {
            val current = keys ?: return
            val record = stored ?: return
            val pending = record.pendingRecoveryKey
                ?: record.recoveryPhrase()?.takeIf { record.recoveryCommitment == null }?.let(StoredIdentity::recoveryKeyOf)
            val remote = remote
            if (remote == null) {
                recoveryKey = when {
                    record.recoveryCommitment != null -> RecoveryKeyState.SET
                    pending != null -> RecoveryKeyState.PENDING
                    else -> RecoveryKeyState.UNKNOWN
                }
                return
            }
            try {
                if (pending != null) {
                    val outcome = AccountRecoveryClient(remote, clock).ensureCommitment(LocalEventSigner(current), PublicKey.fromHex(pending))
                    if (keys !== current) return
                    when (outcome) {
                        is CommitmentOutcome.Published -> {
                            recoveryKey = RecoveryKeyState.SET
                            updateRecord(current) { it.with(pendingRecoveryKey = it.pendingRecoveryKey.takeIf { key -> key != pending }, recoveryCommitment = outcome.id) }
                        }
                        is CommitmentOutcome.AlreadySet -> {
                            recoveryKey = if (outcome.matches == false) RecoveryKeyState.MISMATCH else RecoveryKeyState.SET
                            updateRecord(current) { it.with(pendingRecoveryKey = it.pendingRecoveryKey.takeIf { key -> key != pending }, recoveryCommitment = outcome.id ?: it.recoveryCommitment) }
                        }
                        is CommitmentOutcome.Compromised -> applyStatus(outcome.status ?: statusOf(remote, current.publicKey.hex), record)
                        is CommitmentOutcome.Rejected, CommitmentOutcome.Unavailable -> recoveryKey = RecoveryKeyState.PENDING
                    }
                } else {
                    val status = statusOf(remote, current.publicKey.hex)
                    if (keys !== current) return
                    applyStatus(status, record)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An unexpected answer: what the device knows stays shown.
            }
        }
    }

    private suspend fun statusOf(remote: RemoteEvents, key: String): AccountStatus? =
        (remote.fetchAccountStatus(key) as? AccountStatusResult.Known)?.status

    private suspend fun applyStatus(status: AccountStatus?, record: StoredIdentity) {
        if (status == null) {
            recoveryKey = if (record.recoveryCommitment != null) RecoveryKeyState.SET else RecoveryKeyState.UNKNOWN
            return
        }
        if (status.compromised) {
            compromise = OwnCompromise(status.since ?: status.notice?.createdAt ?: clock(), status.notice?.createdAt, status.successor)
        }
        val commitment = status.commitment
        recoveryKey = if (commitment == null) RecoveryKeyState.MISSING else RecoveryKeyState.SET
        if (commitment != null && commitment != record.recoveryCommitment) keys?.let { current -> updateRecord(current) { it.with(recoveryCommitment = commitment) } }
    }

    /**
     * Changes the recovery fields of the newest record while [current] is still the account (a
     * check of the phrase may have changed it meanwhile: its choice is never undone here).
     */
    private suspend fun updateRecord(current: IdentityKeys, change: (StoredIdentity) -> StoredIdentity) {
        if (keys !== current) return
        val latest = stored ?: return
        val updated = change(latest)
        store.save(updated)
        if (keys === current) stored = updated
    }

    private sealed interface ParsedPhrase {
        data class Valid(val phrase: RecoveryPhrase) : ParsedPhrase
        data class Invalid(val error: RestoreError) : ParsedPhrase
    }

    private fun parsePhrase(text: String, language: PhraseLanguage?): ParsedPhrase = try {
        ParsedPhrase.Valid(RecoveryPhrase.parse(text, language))
    } catch (e: InvalidRecoveryPhraseException) {
        ParsedPhrase.Invalid(
            when (e.reason) {
                InvalidRecoveryPhraseException.Reason.WORD_COUNT -> RestoreError.WordCount
                InvalidRecoveryPhraseException.Reason.UNKNOWN_WORDS -> RestoreError.UnknownWords(e.unknownWords)
                InvalidRecoveryPhraseException.Reason.CHECKSUM -> RestoreError.Checksum
            },
        )
    } catch (e: AmbiguousPhraseLanguageException) {
        ParsedPhrase.Invalid(RestoreError.AmbiguousLanguage(e.candidates))
    }

    internal fun parseForRecovery(text: String, language: PhraseLanguage?): Pair<RecoveryPhrase?, RestoreError?> =
        when (val parsed = parsePhrase(text, language)) {
            is ParsedPhrase.Valid -> parsed.phrase to null
            is ParsedPhrase.Invalid -> null to parsed.error
        }

    companion object {
        /** A check postponed at signup is reminded a week later (D-134, decision 1). */
        const val REMINDER_DELAY_SECONDS: Long = 7L * 24 * 3600

        /** The words typed for [positions] of [phrase], whatever the case, spaces and accents. */
        internal fun matches(phrase: RecoveryPhrase, positions: List<Int>, answers: List<String>): Boolean {
            val expected = positions.map { phrase.words[it] }
            val typed = answers.map { normalizeNfkd(it).trim().lowercase() }
            return typed == expected
        }
    }
}

@Composable
fun rememberAccountSession(store: IdentityStore, onSignOut: suspend () -> Unit = {}, remote: RemoteEvents? = null): AccountSession {
    val scope = rememberCoroutineScope()
    return remember(store) { AccountSession(store, scope, onSignOut, remote = remote).also { it.start() } }
}
