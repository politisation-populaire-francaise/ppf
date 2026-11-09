package org.politisationpopulairefrancaise.core.agir.timer

/** Someone the person can choose to receive the alert of a timer. */
data class TimerCandidate(
    /** Public key, lowercase hexadecimal. */
    val key: String,
    /** A verified legal team of the directory of "Agir". */
    val legalTeam: Boolean = false,
    /** The name to show (a legal team's name); null shows the short form of the key. */
    val label: String? = null,
) {
    fun toRecipient(): TimerRecipient = TimerRecipient(key, legalTeam, label)
}

/**
 * Where the screen of a timer finds the people who can receive its alert (D-088, point 6). In
 * step 1: the contacts of the person's private conversations (the device already knows them, the
 * server is never asked). The circles of « Communauté » (M28) will replace them by implementing
 * this interface, without touching the rest of the timer.
 */
fun interface TimerRecipientSource {
    /** The candidates for the account [me] (lowercase hexadecimal), never [me] itself, most recent first. */
    fun candidates(me: String): List<TimerCandidate>
}
