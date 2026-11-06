package org.politisationpopulairefrancaise.core.messaging

import org.politisationpopulairefrancaise.core.db.FindConversation
import org.politisationpopulairefrancaise.core.db.Message
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.db.SelectConversations
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

enum class MessageStoreOutcome { STORED, DUPLICATE }

/** One delivery of the user's own message (to [recipient], hexadecimal key) not yet accepted by the server. */
data class OutgoingMessage(val messageId: String, val recipient: String)

/**
 * The device's copy of the user's conversations: decrypted messages, read marks, and the gift wraps
 * still to send. Modelled on LocalEventStore. Nothing here ever reaches the server: it only ever
 * sees gift wraps, which hide author, recipients, dates and content.
 */
class MessageStore(private val database: PpfDatabase, private val clock: () -> Long = ::currentEpochSeconds) {

    private val queries get() = database.messagingQueries

    /**
     * Stores a message already checked by the caller (authentic wrap, well-formed rumor). Duplicates
     * are detected by the rumor id, which is the same on every participant's device. The conversation
     * is created on first contact; its subject follows the newest message carrying one.
     */
    fun store(message: ChatMessage, read: Boolean = false): MessageStoreOutcome = database.transactionWithResult {
        if (queries.findMessage(message.id).executeAsOneOrNull() != null) return@transactionWithResult MessageStoreOutcome.DUPLICATE
        val existing = queries.findConversation(message.conversationId).executeAsOneOrNull()
        if (existing == null) {
            queries.insertConversation(
                id = message.conversationId,
                subject = message.subject,
                subject_at = message.subject?.let { message.createdAt },
                last_message_at = message.createdAt,
            )
        } else {
            queries.touchConversation(message.createdAt, message.conversationId)
            if (message.subject != null && (existing.subject_at == null || message.createdAt >= existing.subject_at)) {
                queries.updateConversationSubject(message.subject, message.createdAt, message.conversationId)
            }
        }
        queries.insertMessage(
            id = message.id,
            conversation_id = message.conversationId,
            author = message.author,
            content = message.text,
            created_at = message.createdAt,
            received_at = clock(),
            subject = message.subject,
            reply_to = message.replyTo,
            is_read = read,
        )
        MessageStoreOutcome.STORED
    }

    fun find(id: String): ChatMessage? = queries.findMessage(id).executeAsOneOrNull()?.toChatMessage()

    /** Conversations by most recent activity, with their number of unread messages. */
    fun conversations(limit: Int = 100): List<Conversation> =
        queries.selectConversations(limit.toLong()).executeAsList().map { it.toConversation() }

    fun conversation(id: String): Conversation? = queries.findConversation(id).executeAsOneOrNull()?.toConversation()

    /**
     * The newest [limit] messages of a conversation, oldest first (ready to display). With [before],
     * the messages that come before that one in the thread: the next page when scrolling up.
     */
    fun messages(conversationId: String, limit: Int = 200, before: ChatMessage? = null): List<ChatMessage> {
        val rows = if (before == null) {
            queries.selectMessages(conversationId, limit.toLong()).executeAsList()
        } else {
            queries.selectMessagesBefore(conversationId, before.createdAt, before.id, limit.toLong()).executeAsList()
        }
        return rows.asReversed().map { it.toChatMessage() }
    }

    /** Marks every message of the conversation as read. Read marks never leave the device (no read receipts). */
    fun markRead(conversationId: String) = queries.markConversationRead(conversationId)

    fun markMessageRead(id: String) = queries.markMessageRead(id)

    fun unreadCount(): Long = queries.countUnread().executeAsOne()

    fun count(): Long = queries.countMessages().executeAsOne()

    /** Deletes a conversation, its messages and their pending deliveries from this device only. */
    fun deleteConversation(id: String) = database.transaction {
        queries.selectMessages(id, Long.MAX_VALUE).executeAsList().forEach { queries.deleteOutgoingOf(it.id) }
        queries.deleteMessagesOf(id)
        queries.deleteConversation(id)
    }

    /** Records that the user's message [messageId] still has to reach [recipient] (hexadecimal key). */
    fun queueOutgoing(messageId: String, recipient: String) =
        queries.insertOutgoing(message_id = messageId, recipient = recipient, queued_at = clock())

    /** Deliveries still to make, oldest first. */
    fun outgoing(): List<OutgoingMessage> =
        queries.selectOutgoing().executeAsList().map { OutgoingMessage(it.message_id, it.recipient) }

    fun removeOutgoing(messageId: String, recipient: String) = queries.deleteOutgoing(messageId, recipient)

    fun outgoingCount(): Long = queries.countOutgoing().executeAsOne()

    /** Date of the last pull that reached the server, in seconds; null before the first one. */
    fun lastPullAt(): Long? = queries.getState(LAST_PULL_AT).executeAsOneOrNull()

    fun setLastPullAt(value: Long) = queries.setState(LAST_PULL_AT, value)

    private fun Message.toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        author = author,
        conversationId = conversation_id,
        participants = Conversation.participantsOf(conversation_id),
        text = content,
        createdAt = created_at,
        subject = subject,
        replyTo = reply_to,
    )

    private fun SelectConversations.toConversation(): Conversation =
        Conversation(id, Conversation.participantsOf(id), subject, last_message_at, unread_count)

    private fun FindConversation.toConversation(): Conversation =
        Conversation(id, Conversation.participantsOf(id), subject, last_message_at, unread_count)

    private companion object {
        const val LAST_PULL_AT = "last_pull_at"
    }
}
