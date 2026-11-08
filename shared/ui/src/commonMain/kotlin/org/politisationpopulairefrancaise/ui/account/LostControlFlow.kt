package org.politisationpopulairefrancaise.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.AccountRecoveryClient
import org.politisationpopulairefrancaise.core.identity.Nip19
import org.politisationpopulairefrancaise.core.identity.NoticeOutcome
import org.politisationpopulairefrancaise.core.identity.PhraseLanguage
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.social.FollowStore

/** Who the notice names as the new account (a claim, never a proof of identity). */
enum class SuccessorChoice {
    /** The account already on this device (the new phone, where the user created another account). */
    CURRENT,

    /** A new account created on this device, whose phrase the user notes first. */
    NEW,

    /** No successor. */
    NONE,

    /** A key the user types (npub or hexadecimal). */
    OTHER,
}

/** What the user filled in the form of "J'ai perdu le contrôle de mon compte". */
data class LostControlInput(
    val phraseText: String,
    /** "Compromis depuis", seconds; between 30 days before now and now. */
    val since: Long,
    val successor: SuccessorChoice,
    val otherKey: String = "",
    val language: PhraseLanguage? = null,
)

/** Why the declaration could not go on; each one has a plain French message on screen. */
sealed interface LostControlError {
    data class Phrase(val error: RestoreError) : LostControlError

    /** The key typed for [SuccessorChoice.OTHER] is not a public key. */
    data object InvalidSuccessor : LostControlError

    /** The successor would be the compromised account itself. */
    data object SuccessorIsCompromised : LostControlError

    /** A new account cannot replace another account that is on this device. */
    data object DeviceHasAnotherAccount : LostControlError

    /** Without a server, nothing can be declared. */
    data object NoServer : LostControlError

    data object Unavailable : LostControlError
    data object NoCommitment : LostControlError
    data object WrongPhrase : LostControlError
    data object AlreadyCompromised : LostControlError
    data object SuccessorTaken : LostControlError
    data object InvalidNotice : LostControlError
    data object Refused : LostControlError
}

/** The steps of the declaration. */
sealed interface LostControlState {
    /** The form: phrase, "compromis depuis", successor. [error] tells why the last attempt stopped. */
    data class Form(val error: LostControlError? = null, val input: LostControlInput? = null) : LostControlState

    /** The new account's phrase, to note and check before the notice names it. */
    data class NewAccount(
        val input: LostControlInput,
        val phrase: RecoveryPhrase,
        val verifying: Boolean = false,
        val positions: List<Int>,
        val failed: Boolean = false,
    ) : LostControlState

    /** Everything is ready: the irreversible confirmation. */
    data class Confirm(val input: LostControlInput, val compromised: String, val successor: String?, val newPhrase: RecoveryPhrase?) : LostControlState

    data object Sending : LostControlState

    /** The notice was accepted by the server. */
    data class Done(val compromised: String, val since: Long, val successor: String?, val replacedThisDevice: Boolean) : LostControlState
}

/**
 * "J'ai perdu le contrôle de mon compte" (S-146, D-134): on any device, the user types the
 * phrase of the lost account, says since when it is out of their hands (30 days back at most),
 * names a successor if they wish, confirms after the explanation that it is irreversible, and the
 * device signs the notice (kind 1777) with the recovery key derived from the phrase, then
 * publishes it. The successor is never followed automatically by anyone.
 */
class LostControlFlow internal constructor(
    private val session: AccountSession,
    private val remote: RemoteEvents?,
    private val clock: () -> Long,
    private val pickPositions: () -> List<Int> = { (0 until RecoveryPhrase.DEFAULT_WORD_COUNT).shuffled().take(3).sorted() },
) {
    /** Null while the flow is closed. */
    var state: LostControlState? by mutableStateOf(null)
        private set

    fun begin() {
        state = LostControlState.Form()
    }

    fun cancel() {
        state = null
    }

    /** The account of this device (lowercase hex), offered as the successor; null without one. */
    fun deviceAccount(): String? = session.accountKey

    /** The successor offered by default: the account of this device if there is one, otherwise a new one. */
    fun defaultSuccessor(): SuccessorChoice = if (session.accountKey != null) SuccessorChoice.CURRENT else SuccessorChoice.NEW

    /** Checks the form and goes to the new account's phrase or to the confirmation. */
    fun submit(input: LostControlInput) {
        val (phrase, error) = session.parseForRecovery(input.phraseText, input.language)
        if (phrase == null) {
            state = LostControlState.Form(LostControlError.Phrase(error ?: RestoreError.Checksum), input)
            return
        }
        val compromisedKeys = phrase.deriveKeys()
        val compromised = compromisedKeys.publicKey.hex
        compromisedKeys.destroy()
        val device = session.accountKey
        val successor: String? = when (input.successor) {
            SuccessorChoice.CURRENT -> device
            SuccessorChoice.NONE, SuccessorChoice.NEW -> null
            SuccessorChoice.OTHER -> parseKey(input.otherKey) ?: run {
                state = LostControlState.Form(LostControlError.InvalidSuccessor, input)
                return
            }
        }
        if (successor == compromised || (input.successor == SuccessorChoice.CURRENT && device == null)) {
            state = LostControlState.Form(LostControlError.SuccessorIsCompromised, input)
            return
        }
        if (input.successor == SuccessorChoice.NEW && device != null && device != compromised) {
            state = LostControlState.Form(LostControlError.DeviceHasAnotherAccount, input)
            return
        }
        if (remote == null) {
            state = LostControlState.Form(LostControlError.NoServer, input)
            return
        }
        state = if (input.successor == SuccessorChoice.NEW) {
            LostControlState.NewAccount(input, RecoveryPhrase.generate(), positions = pickPositions())
        } else {
            LostControlState.Confirm(input, compromised, successor, newPhrase = null)
        }
    }

    /** The new account's phrase is noted: ask three of its words. */
    fun newPhraseNoted() {
        val current = state as? LostControlState.NewAccount ?: return
        state = current.copy(verifying = true, failed = false)
    }

    fun reviewNewPhrase() {
        val current = state as? LostControlState.NewAccount ?: return
        state = current.copy(verifying = false, failed = false)
    }

    fun checkNewPhrase(answers: List<String>) {
        val current = state as? LostControlState.NewAccount ?: return
        if (!AccountSession.matches(current.phrase, current.positions, answers)) {
            state = current.copy(failed = true)
            return
        }
        val (phrase, _) = session.parseForRecovery(current.input.phraseText, current.input.language)
        val compromisedKeys = phrase?.deriveKeys() ?: return
        val compromised = compromisedKeys.publicKey.hex
        compromisedKeys.destroy()
        val successorKeys = current.phrase.deriveKeys()
        val successor = successorKeys.publicKey.hex
        successorKeys.destroy()
        state = LostControlState.Confirm(current.input, compromised, successor, current.phrase)
    }

    /** Back from the confirmation to the form, without publishing anything. */
    fun back() {
        val current = state as? LostControlState.Confirm ?: return
        state = LostControlState.Form(input = current.input)
    }

    /** Signs and publishes the notice. Irreversible: the screen asked the confirmation first. */
    suspend fun confirm() {
        val confirm = state as? LostControlState.Confirm ?: return
        val remote = remote ?: run {
            state = LostControlState.Form(LostControlError.NoServer, confirm.input)
            return
        }
        val (phrase, _) = session.parseForRecovery(confirm.input.phraseText, confirm.input.language)
        if (phrase == null) {
            state = LostControlState.Form(LostControlError.Phrase(RestoreError.Checksum), confirm.input)
            return
        }
        state = LostControlState.Sending
        val now = clock()
        val since = confirm.input.since.coerceIn(now - AccountRecovery.MAX_BACKDATING_SECONDS, now)
        val outcome = AccountRecoveryClient(remote, clock).declareCompromised(phrase, since, confirm.successor?.let(PublicKey::fromHex))
        val error: LostControlError = when (outcome) {
            is NoticeOutcome.Published -> {
                val replaced = adopt(confirm, outcome)
                state = LostControlState.Done(outcome.notice.compromised, outcome.notice.since, outcome.notice.successor, replaced)
                return
            }
            NoticeOutcome.Unavailable -> LostControlError.Unavailable
            NoticeOutcome.NoCommitment -> LostControlError.NoCommitment
            NoticeOutcome.WrongPhrase -> LostControlError.WrongPhrase
            is NoticeOutcome.AlreadyCompromised -> LostControlError.AlreadyCompromised
            NoticeOutcome.SuccessorTaken -> LostControlError.SuccessorTaken
            NoticeOutcome.InvalidNotice -> LostControlError.InvalidNotice
            is NoticeOutcome.Rejected -> LostControlError.Refused
        }
        state = LostControlState.Form(error, confirm.input)
    }

    /**
     * After an accepted notice: the new account, if one was prepared, becomes this device's account
     * (its phrase checked, hence erased from the device); a compromised account of this device is
     * marked so. True when the device's account was replaced.
     */
    private fun adopt(confirm: LostControlState.Confirm, outcome: NoticeOutcome.Published): Boolean {
        val notice = outcome.notice
        val newPhrase = confirm.newPhrase
        if (newPhrase != null) {
            val created = newPhrase.deriveKeys()
            session.adoptSuccessor(created, StoredIdentity.of(created, null, verified = true, pendingRecoveryKey = StoredIdentity.recoveryKeyOf(newPhrase)))
            return true
        }
        if (session.accountKey == notice.compromised) session.markCompromised(notice.since, notice.createdAt, notice.successor)
        return false
    }

    private fun parseKey(text: String): String? {
        val value = text.trim()
        if (value.startsWith(Nip19.PUBLIC_KEY + "1", ignoreCase = true)) {
            return runCatching { PublicKey.fromNpub(value.lowercase()).hex }.getOrNull()
        }
        val hex = value.lowercase()
        return hex.takeIf { FollowStore.isValidKey(it) }
    }
}
