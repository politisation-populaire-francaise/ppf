package org.politisationpopulairefrancaise.core.urne

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/** A token kept on the device for one ballot box (table vote_token of Vote.sq). */
data class StoredToken(
    val urneNumber: Long,
    val urneId: String,
    val level: VoteLevel,
    /** The public key seen at the delivery: a box announcing another key later is refused. */
    val publicKey: String,
    val y: ByteArray,
    val token: ByteArray,
    /** The choice made on this device, null before any vote. */
    val choice: String?,
    /** Seconds since 1970 after which the ballot may leave; null when nothing waits. */
    val sendAfter: Long?,
    /** The last choice the server accepted. */
    val sentChoice: String?,
)

/** A blinded request already sent, kept until its answer arrives. */
data class PendingBlind(val urneNumber: Long, val y: ByteArray, val blind: ByteArray, val blinded: ByteArray)

/**
 * The voting data of the device (D-110): tokens, blinded requests waiting for an answer, and the
 * account they belong to. Nothing here is ever sent as such; [clear] erases it all, as when the
 * keys are erased.
 */
class UrneTokenStore(private val database: PpfDatabase) {
    private val queries get() = database.voteQueries

    /** The account of the stored tokens and whether it is registered, or null when there is none. */
    fun state(): Pair<String, Boolean>? = queries.selectState().executeAsOneOrNull()?.let { it.account to it.registered }

    fun setState(account: String, registered: Boolean) = queries.setState(account, registered)

    fun saveBlind(blind: PendingBlind) = queries.insertBlind(blind.urneNumber, blind.y, blind.blind, blind.blinded)

    /** Forgets a request that will never be answered (its box is served or gone). */
    fun dropBlind(urneNumber: Long) = queries.deleteBlind(urneNumber)

    fun blinds(): List<PendingBlind> = queries.selectBlinds().executeAsList().map { PendingBlind(it.urne_number, it.y, it.blind, it.blinded) }

    fun saveToken(token: StoredToken) = database.transaction {
        queries.insertToken(token.urneNumber, token.urneId, token.level.code, token.publicKey, token.y, token.token)
        queries.deleteBlind(token.urneNumber)
    }

    fun token(urneNumber: Long): StoredToken? = queries.selectToken(urneNumber).executeAsOneOrNull()?.let(::toToken)

    fun tokens(): List<StoredToken> = queries.selectTokens().executeAsList().map(::toToken)

    /** Records [choice] for a ballot box; the ballot leaves after [sendAfter]. */
    fun choose(urneNumber: Long, choice: String, sendAfter: Long) = queries.setChoice(choice, sendAfter, urneNumber)

    fun due(now: Long): List<StoredToken> = queries.selectDue(now).executeAsList().mapNotNull { token(it) }

    /** The server accepted [choice] (a newer choice made meanwhile keeps waiting). */
    fun markSent(urneNumber: Long, choice: String) = queries.markSent(urneNumber, choice)

    /** Nothing more will leave for this box (closed, unknown). */
    fun stopSending(urneNumber: Long) = queries.stopSending(urneNumber)

    fun clear() = database.transaction {
        queries.deleteAllTokens()
        queries.deleteAllBlinds()
        queries.deleteState()
    }

    private fun toToken(row: org.politisationpopulairefrancaise.core.db.Vote_token) = StoredToken(
        urneNumber = row.urne_number,
        urneId = row.urne_id,
        level = VoteLevel.of(row.level) ?: VoteLevel.NON_VERIFIE,
        publicKey = row.public_key,
        y = row.y,
        token = row.token,
        choice = row.choice,
        sendAfter = row.send_after,
        sentChoice = row.sent_choice,
    )
}
