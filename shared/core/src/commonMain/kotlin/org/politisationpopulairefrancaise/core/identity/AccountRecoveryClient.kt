package org.politisationpopulairefrancaise.core.identity

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * What anyone may know of an account's recovery, as `GET /api/v1/accounts/<key>/status` serves it
 * (docs/FORMATS.md, section 5): whether it is compromised, since when, the successor it names, the
 * signed notice, and the id of its first commitment (null: the account has no recovery key).
 */
@Serializable
data class AccountStatus(
    val compromised: Boolean = false,
    val since: Long? = null,
    val successor: String? = null,
    val notice: SignedEvent? = null,
    val commitment: String? = null,
)

/** The answer of [RemoteEvents.fetchAccountStatus]. */
sealed interface AccountStatusResult {
    data class Known(val status: AccountStatus) : AccountStatusResult

    data class Unavailable(val cause: Throwable? = null) : AccountStatusResult
}

/** What became of the commitment of an account (kind 1776) after [AccountRecoveryClient.ensureCommitment]. */
sealed interface CommitmentOutcome {
    /** The server accepted the commitment just signed. */
    data class Published(val id: String) : CommitmentOutcome

    /**
     * The server already holds a commitment for the account. [matches] says whether it names the
     * recovery key of this phrase (null when it could not be read): false means the recovery key on
     * record is another one, so this phrase will not be able to declare the account compromised.
     */
    data class AlreadySet(val id: String?, val matches: Boolean?) : CommitmentOutcome

    /** The account is declared compromised: nothing can be published with it any more. */
    data class Compromised(val status: AccountStatus?) : CommitmentOutcome

    /** The server refused the commitment for another reason. */
    data class Rejected(val reason: RejectionReason) : CommitmentOutcome

    /** No server, or it did not answer: try again later. */
    data object Unavailable : CommitmentOutcome
}

/** What became of a notice of compromise (kind 1777) after [AccountRecoveryClient.declareCompromised]. */
sealed interface NoticeOutcome {
    /** The server accepted the notice: the account is declared compromised for everyone. */
    data class Published(val notice: CompromiseNotice) : NoticeOutcome

    /** The account never published a commitment: its phrase cannot declare it compromised. */
    data object NoCommitment : NoticeOutcome

    /** The commitment on record names another recovery key than this phrase's. */
    data object WrongPhrase : NoticeOutcome

    /** The account is already declared compromised (`409 already_compromised`). */
    data class AlreadyCompromised(val status: AccountStatus?) : NoticeOutcome

    /** The successor is already the successor of another account (`409 successor_taken`). */
    data object SuccessorTaken : NoticeOutcome

    /** The server judged the notice invalid (`400 invalid_notice`), for instance a date out of bounds. */
    data object InvalidNotice : NoticeOutcome

    /** Another definitive refusal. */
    data class Rejected(val reason: RejectionReason) : NoticeOutcome

    /** No server, or it did not answer: nothing was published, try again. */
    data object Unavailable : NoticeOutcome
}

/** A notice the device checked itself against the first commitment of the account it names. */
data class CheckedNotice(val notice: CompromiseNotice, val event: SignedEvent, val commitment: SignedEvent)

/**
 * The device side of "Déclarer mon compte compromis" (S-146, D-134,
 * docs/integration/compte-compromis.md): publishing the commitment of an account to its recovery
 * key once, declaring an account compromised from its phrase, and finding the notices that concern
 * the user's contacts. Everything goes through the single publication port ([remote]).
 */
class AccountRecoveryClient(
    private val remote: RemoteEvents,
    private val clock: () -> Long = ::currentEpochSeconds,
    /** Mixes the keys before they are cut in batches, so that the server never sees the list in order. */
    private val shuffle: (List<String>) -> List<String> = { it.shuffled() },
) {
    /**
     * Publishes the commitment of [account] to [recovery] unless the server already holds one for
     * the account (`commitment` of the status). Only the first commitment counts (D-134).
     */
    suspend fun ensureCommitment(account: EventSigner, recovery: PublicKey): CommitmentOutcome {
        val key = account.publicKey.hex
        val status = when (val answer = guarded { remote.fetchAccountStatus(key) }) {
            is AccountStatusResult.Known -> answer.status
            else -> return CommitmentOutcome.Unavailable
        }
        if (status.compromised) return CommitmentOutcome.Compromised(status)
        status.commitment?.let { id -> return CommitmentOutcome.AlreadySet(id, commitmentOf(key, id)?.let { it.recovery == recovery.hex }) }
        val event = AccountRecovery.signCommitment(account, recovery, clock())
        return when (val result = guarded { remote.publish(event) }) {
            is PublicationResult.Accepted -> CommitmentOutcome.Published(event.id)
            is PublicationResult.Rejected -> when (result.reason) {
                RejectionReason.RECOVERY_ALREADY_SET -> CommitmentOutcome.AlreadySet(null, null)
                RejectionReason.ACCOUNT_COMPROMISED -> CommitmentOutcome.Compromised(null)
                else -> CommitmentOutcome.Rejected(result.reason)
            }
            else -> CommitmentOutcome.Unavailable
        }
    }

    /**
     * Declares compromised the account of [phrase] since [since] (bounded to the 30 days before
     * now, D-134), naming [successor] when given. The notice is signed by the recovery key derived
     * from the phrase, which is destroyed right after.
     */
    suspend fun declareCompromised(phrase: RecoveryPhrase, since: Long, successor: PublicKey?): NoticeOutcome {
        val accountKeys = phrase.deriveKeys()
        val account = accountKeys.publicKey.hex
        accountKeys.destroy()
        val status = when (val answer = guarded { remote.fetchAccountStatus(account) }) {
            is AccountStatusResult.Known -> answer.status
            else -> return NoticeOutcome.Unavailable
        }
        if (status.compromised) return NoticeOutcome.AlreadyCompromised(status)
        val commitmentId = status.commitment ?: return NoticeOutcome.NoCommitment
        val commitment = commitmentOf(account, commitmentId) ?: return NoticeOutcome.Unavailable
        val recoveryKeys = phrase.deriveRecoveryKeys()
        try {
            if (commitment.recovery != recoveryKeys.publicKey.hex) return NoticeOutcome.WrongPhrase
            if (successor != null && (successor.hex == account || successor == recoveryKeys.publicKey)) return NoticeOutcome.InvalidNotice
            val now = clock()
            val event = AccountRecovery.signNotice(
                LocalEventSigner(recoveryKeys),
                commitment,
                createdAt = now,
                since = since.coerceIn(now - AccountRecovery.MAX_BACKDATING_SECONDS, now),
                successor = successor,
            )
            val notice = (AccountRecovery.parseNotice(event) as? NoticeCheck.Valid)?.notice ?: return NoticeOutcome.InvalidNotice
            return when (val result = guarded { remote.publish(event) }) {
                is PublicationResult.Accepted -> NoticeOutcome.Published(notice)
                is PublicationResult.Rejected -> when (result.reason) {
                    RejectionReason.ALREADY_COMPROMISED -> NoticeOutcome.AlreadyCompromised(null)
                    RejectionReason.SUCCESSOR_TAKEN -> NoticeOutcome.SuccessorTaken
                    RejectionReason.INVALID_NOTICE -> NoticeOutcome.InvalidNotice
                    else -> NoticeOutcome.Rejected(result.reason)
                }
                else -> NoticeOutcome.Unavailable
            }
        } finally {
            recoveryKeys.destroy()
        }
    }

    /**
     * The notices that concern [keys] (the user's contacts), checked on the device against the
     * first commitment of each account. The keys are mixed and asked in batches of [batchSize]
     * (`GET /api/v1/events?kind=1777&tag=p:<key>…`), so that the server never receives the whole
     * list in one request. Null when the server could not be asked.
     */
    suspend fun noticesFor(keys: Collection<String>, batchSize: Int = DEFAULT_BATCH): Map<String, CheckedNotice>? {
        require(batchSize in 1..TagFilter.MAX_PER_REQUEST)
        val wanted = keys.filter(::isHex64).toSet()
        if (wanted.isEmpty()) return emptyMap()
        val found = mutableListOf<SignedEvent>()
        for (batch in shuffle(wanted.toList()).chunked(batchSize)) {
            val filter = RemoteFilter(kinds = listOf(AccountRecovery.NOTICE_KIND), limit = batch.size * 4)
            when (val answer = guarded { remote.fetchByTags(batch.map { TagFilter("p", it) }, filter) }) {
                is FetchResult.Events -> found += answer.events
                else -> return null
            }
        }
        val byAccount = found
            .mapNotNull { event -> (AccountRecovery.parseNotice(event) as? NoticeCheck.Valid)?.let { it.notice to event } }
            .filter { (notice, _) -> notice.compromised in wanted }
            .groupBy { (notice, _) -> notice.compromised }
        if (byAccount.isEmpty()) return emptyMap()
        val commitments = when (
            val answer = guarded {
                remote.fetch(RemoteFilter(kinds = listOf(AccountRecovery.COMMITMENT_KIND), authors = byAccount.keys.toList(), limit = byAccount.size * 4))
            }
        ) {
            is FetchResult.Events -> answer.events
            else -> return null
        }
        val now = clock()
        return byAccount.mapNotNull { (account, notices) ->
            notices.sortedWith(compareBy<Pair<CompromiseNotice, SignedEvent>> { it.first.createdAt }.thenBy { it.first.id }).firstNotNullOfOrNull { (notice, event) ->
                val commitment = commitments.firstOrNull { it.id == notice.commitmentId && it.pubkey == account } ?: return@firstNotNullOfOrNull null
                (AccountRecovery.checkNotice(event, commitment, now) as? NoticeCheck.Valid)?.let { CheckedNotice(it.notice, event, commitment) }
            }?.let { account to it }
        }.toMap()
    }

    /** The commitment [id] of [account], read from the server and checked; null when unreadable. */
    private suspend fun commitmentOf(account: String, id: String): RecoveryCommitment? {
        val answer = guarded { remote.fetch(RemoteFilter(kinds = listOf(AccountRecovery.COMMITMENT_KIND), authors = listOf(account), limit = 20)) }
        val events = (answer as? FetchResult.Events)?.events ?: return null
        return events.firstOrNull { it.id == id }?.let(AccountRecovery::parseCommitment)?.takeIf { it.account == account }
    }

    /** Runs a call to the server; an unexpected failure is "unavailable" (null), never a crash. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun isHex64(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        /** Keys asked together for their notices: small enough to say little about the list. */
        const val DEFAULT_BATCH: Int = 10
    }
}
