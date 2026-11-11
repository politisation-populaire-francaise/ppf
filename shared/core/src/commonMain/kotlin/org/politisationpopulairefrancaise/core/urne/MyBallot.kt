package org.politisationpopulairefrancaise.core.urne

/** What the device found when it looked for its own ballot in the list published at the closing (DE-29). */
enum class MyBallotFinding {
    /** The ballot is in the list, with the choice made on this device. */
    COUNTED,

    /** The vote was withdrawn on this device, and the list says so: it is not counted, as asked. */
    WITHDRAWN,

    /** The ballot is in the list with the previous choice: the last one did not leave before the closing. */
    COUNTED_EARLIER_CHOICE,

    /** A choice was made on this device but never left before the closing: nothing is counted. */
    NOT_SENT,

    /** No ballot left this device for this box (or the device holds no token for it). */
    NOT_VOTED,

    /** The server accepted the ballot, and it is missing from the published list. */
    MISSING,

    /** The ballot is in the list with another choice, another level or another token. */
    ALTERED,
}

/** The two answers of DE-29: where this device's ballot stands, and whether the whole box recounts right. */
data class MyBallotCheck(val finding: MyBallotFinding, val recount: RecountResult) {
    /** Something is wrong: the ballot is missing or changed, or the recount does not match. */
    val alarming: Boolean get() = finding == MyBallotFinding.MISSING || finding == MyBallotFinding.ALTERED || recount != RecountResult.CORRECT
}

/**
 * « Mon bulletin a été compté » (DE-29; D-110, 6.7; D-112): after the closing, the device looks by itself for its
 * ballot in the published list, by the secret identifier `y` it drew, and replays the recount of the whole box
 * ([UrneRecount]). Nothing is sent and nothing is kept: the check is computed again each time, and neither `y`
 * nor the token is ever shown, so the device holds no receipt that could prove a choice to anyone else.
 *
 * The public key of the device's own level is the one pinned when its token arrived ([StoredToken.publicKey]):
 * a closing that announces another one fails the recount. The other levels are checked against the keys the
 * closing itself publishes (the device does not keep the list seen while the box was open).
 */
object MyBallot {

    fun check(box: UrneInfo, token: StoredToken?, closing: UrneClosing, group: Ristretto255): MyBallotCheck {
        val announced = if (token == null) closing.publicKeys else closing.publicKeys + (token.level.code to token.publicKey)
        val recount = when {
            closing.number != box.number || closing.id != box.id -> RecountResult.KEY_MISMATCH
            token != null && (closing.number != token.urneNumber || closing.id != token.urneId) -> RecountResult.KEY_MISMATCH
            else -> UrneRecount.check(closing, announced, box.choices, group)
        }
        return MyBallotCheck(findingOf(token, closing), recount)
    }

    /** Where the ballot of [token] stands in [closing]; the recount is separate ([check]). */
    fun findingOf(token: StoredToken?, closing: UrneClosing): MyBallotFinding {
        if (token == null) return MyBallotFinding.NOT_VOTED
        val y = token.y.toHexString()
        val ballot = closing.ballots.firstOrNull { it.y == y }
        val latest = token.choice
        val accepted = token.sentChoice
        if (ballot == null) {
            return when {
                accepted != null -> MyBallotFinding.MISSING
                latest != null -> MyBallotFinding.NOT_SENT
                else -> MyBallotFinding.NOT_VOTED
            }
        }
        // A ballot with this device's secret identifier but another token or level was not made here.
        if (ballot.token != token.token.toHexString() || ballot.level != token.level.code) return MyBallotFinding.ALTERED
        return when (ballot.choice) {
            // The last choice arrived, even when the device missed the server's answer.
            latest -> if (latest == UrneInfo.WITHDRAWN) MyBallotFinding.WITHDRAWN else MyBallotFinding.COUNTED
            accepted -> if (accepted == UrneInfo.WITHDRAWN) MyBallotFinding.WITHDRAWN else MyBallotFinding.COUNTED_EARLIER_CHOICE
            else -> MyBallotFinding.ALTERED
        }
    }
}
