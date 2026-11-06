package org.politisationpopulairefrancaise.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.identity.AmbiguousPhraseLanguageException
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.InvalidRecoveryPhraseException
import org.politisationpopulairefrancaise.core.identity.Nip19
import org.politisationpopulairefrancaise.core.identity.PhraseLanguage
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.text.normalizeNfkd

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
    ) : AccountUiState

    data class Restoring(val error: RestoreError? = null) : AccountUiState

    data class Ready(
        val publicKey: PublicKey,
        val hasRecoveryPhrase: Boolean,
        val revealed: Revealed? = null,
    ) : AccountUiState
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

/**
 * Drives the account screens and talks to the device's [IdentityStore]. One instance lives for
 * the whole application; the keys stay in memory only while the session is alive.
 */
class AccountSession(
    private val store: IdentityStore,
    private val scope: CoroutineScope,
    /** Called once the keys are erased: what else the device must forget (the followed accounts). */
    private val onSignOut: () -> Unit = {},
    private val pickPositions: () -> List<Int> = { (0 until RecoveryPhrase.DEFAULT_WORD_COUNT).shuffled().take(3).sorted() },
) {
    var state: AccountUiState by mutableStateOf(AccountUiState.Loading)
        private set

    private var keys: IdentityKeys? = null
    private var stored: StoredIdentity? = null

    /** Signs on behalf of the account, when there is one. */
    val signer: EventSigner? get() = keys?.let(::LocalEventSigner)

    fun start() {
        scope.launch {
            state = try {
                val record = store.load()
                if (record == null) AccountUiState.NoAccount else ready(record)
            } catch (_: Exception) {
                AccountUiState.Unreadable
            }
        }
    }

    fun beginCreation() {
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

    /** [answers] are the words typed for [AccountUiState.Creating.checkedPositions], in the same order. */
    fun verifyAndCreate(answers: List<String>) {
        val creating = state as? AccountUiState.Creating ?: return
        val expected = creating.checkedPositions.map { creating.phrase.words[it] }
        val typed = answers.map { normalizeNfkd(it).trim().lowercase() }
        if (typed != expected) {
            state = creating.copy(verificationFailed = true)
            return
        }
        persist(creating.phrase.deriveKeys(), creating.phrase)
    }

    fun cancel() {
        state = AccountUiState.NoAccount
    }

    fun beginRestore() {
        state = AccountUiState.Restoring()
    }

    /** Restores from twelve words or from an "nsec1…" secret key; [language] settles an ambiguity. */
    fun restore(input: String, language: PhraseLanguage? = null) {
        val text = input.trim()
        if (text.startsWith(Nip19.SECRET_KEY + "1", ignoreCase = true)) {
            val restored = runCatching { IdentityKeys.fromNsec(text.lowercase()) }.getOrNull()
            if (restored == null) {
                state = AccountUiState.Restoring(RestoreError.InvalidSecretKey)
            } else {
                persist(restored, phrase = null)
            }
            return
        }
        val phrase = try {
            RecoveryPhrase.parse(text, language)
        } catch (e: InvalidRecoveryPhraseException) {
            state = AccountUiState.Restoring(
                when (e.reason) {
                    InvalidRecoveryPhraseException.Reason.WORD_COUNT -> RestoreError.WordCount
                    InvalidRecoveryPhraseException.Reason.UNKNOWN_WORDS -> RestoreError.UnknownWords(e.unknownWords)
                    InvalidRecoveryPhraseException.Reason.CHECKSUM -> RestoreError.Checksum
                },
            )
            return
        } catch (e: AmbiguousPhraseLanguageException) {
            state = AccountUiState.Restoring(RestoreError.AmbiguousLanguage(e.candidates))
            return
        }
        persist(phrase.deriveKeys(), phrase)
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

    /** Erases the keys from this device. Without the recovery phrase, the account is gone. */
    fun signOut() {
        scope.launch {
            store.clear()
            keys?.destroy()
            keys = null
            stored = null
            state = AccountUiState.NoAccount
            onSignOut()
        }
    }

    private fun persist(newKeys: IdentityKeys, phrase: RecoveryPhrase?) {
        val record = StoredIdentity.of(newKeys, phrase)
        scope.launch {
            store.save(record)
            state = ready(record, newKeys)
        }
    }

    private fun ready(record: StoredIdentity, loadedKeys: IdentityKeys = record.keys()): AccountUiState.Ready {
        keys?.takeIf { it !== loadedKeys }?.destroy()
        keys = loadedKeys
        stored = record
        return AccountUiState.Ready(loadedKeys.publicKey, hasRecoveryPhrase = record.recoveryWords != null)
    }
}

@Composable
fun rememberAccountSession(store: IdentityStore, onSignOut: () -> Unit = {}): AccountSession {
    val scope = rememberCoroutineScope()
    return remember(store) { AccountSession(store, scope, onSignOut).also { it.start() } }
}
