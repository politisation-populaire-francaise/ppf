package org.politisationpopulairefrancaise.core.groups

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * How long the server keeps the encrypted envelopes addressed to a user who is away (decision
 * D-028): three months at least, one year at most, chosen by the user for their own mailbox. The
 * choice is a NIP-78 application-data event (kind 30078, `d` = `ppf:mailbox`) signed by the user
 * and read by the server; devices keep their own copy of the messages without limit.
 */
enum class MailboxRetention(val days: Int) {
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    ONE_YEAR(365);

    companion object {
        const val KIND: Int = EventKinds.APP_DATA
        const val IDENTIFIER: String = "ppf:mailbox"
        const val TAG_RETENTION: String = "retention_days"

        /** What the server applies when the user never chose. */
        val DEFAULT: MailboxRetention = THREE_MONTHS

        /** The setting event, ready to be signed by [author]. */
        fun build(author: PublicKey, retention: MailboxRetention, createdAt: Long): UnsignedEvent =
            UnsignedEvent.create(author, KIND, createdAt, listOf(listOf("d", IDENTIFIER), listOf(TAG_RETENTION, retention.days.toString())))

        /**
         * The retention a setting event asks for, or null when the event is not a mailbox setting.
         * The server bounds the days to 90..365; a value between two choices is read as the choice
         * it is closest to, so that a setting written by another client is still shown.
         */
        fun from(event: SignedEvent): MailboxRetention? {
            if (event.kind != KIND || event.firstTagValue("d") != IDENTIFIER) return null
            val days = event.firstTagValue(TAG_RETENTION)?.trim()?.toIntOrNull() ?: return null
            val bounded = days.coerceIn(THREE_MONTHS.days, ONE_YEAR.days)
            return entries.minByOrNull { kotlin.math.abs(it.days - bounded) }
        }
    }
}
