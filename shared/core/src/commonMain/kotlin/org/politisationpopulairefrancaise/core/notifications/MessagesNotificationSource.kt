package org.politisationpopulairefrancaise.core.notifications

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/**
 * Unread private messages received after a date, written by someone else than [me] (the user's
 * public key in hexadecimal, or null without an account: then nothing). Dated by their reception
 * on the device, so a message written long ago but received now is still notified.
 * The excerpt is cut to [excerptLength] characters; it is shown only if the user allowed details.
 */
class MessagesNotificationSource(
    private val database: PpfDatabase,
    private val me: () -> String?,
    private val excerptLength: Int = 80,
) : NotificationSource {

    override val name: String = "messages"

    override suspend fun collect(after: Long?): List<NotificationCandidate> {
        val self = me() ?: return emptyList()
        return database.messagingQueries.selectUnreadReceivedAfter(self, after ?: Long.MIN_VALUE, MAX_PER_ROUND).executeAsList().map { row ->
            NotificationCandidate(
                id = "message:${row.id}",
                category = NotificationCategory.MESSAGES,
                // The author's key; the interface replaces it by the contact's name it knows.
                title = row.author,
                detail = row.content.take(excerptLength),
                target = "ppf:messages/" + row.conversation_id,
                createdAt = row.received_at,
            )
        }
    }

    private companion object {
        const val MAX_PER_ROUND: Long = 100
    }
}
