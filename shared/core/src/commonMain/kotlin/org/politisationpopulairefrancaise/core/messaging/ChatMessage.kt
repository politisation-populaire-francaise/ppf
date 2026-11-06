package org.politisationpopulairefrancaise.core.messaging

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.signing.isLowercaseHex

/**
 * A private conversation (NIP-17 "chat room"): the set of its participants, the user included.
 * Its identifier is the participants' public keys sorted and joined by commas, so that every
 * participant computes the same identifier on their own device. Nothing of this leaves the device.
 */
data class Conversation(
    val id: String,
    /** Public keys in hexadecimal, sorted, the user's own key included. */
    val participants: List<String>,
    /** Latest `subject` given by any participant, if any. */
    val subject: String? = null,
    /** Date of the most recent message, in seconds. */
    val lastMessageAt: Long,
    val unreadCount: Long = 0,
) {
    companion object {
        /** The identifier of the conversation gathering exactly these keys (duplicates ignored). */
        fun idOf(participants: Iterable<String>): String {
            val keys = participants.distinct().sorted()
            require(keys.isNotEmpty()) { "A conversation has at least one participant" }
            keys.forEach { require(isLowercaseHex(it, 64)) { "Participants are 64-character lowercase hexadecimal public keys" } }
            return keys.joinToString(",")
        }

        fun participantsOf(id: String): List<String> = id.split(',')
    }
}

/**
 * A chat message (NIP-17, kind 14) as the application handles it: the decrypted content of a rumor,
 * stored only on the devices of its participants.
 */
data class ChatMessage(
    /** Id of the rumor: the same on every participant's device, used to drop duplicates. */
    val id: String,
    /** Public key of the author, hexadecimal. */
    val author: String,
    val conversationId: String,
    /** All participants, sorted, author included. */
    val participants: List<String>,
    /** Plain text, never Markdown nor HTML. */
    val text: String,
    /** Date written by the author, in seconds: the real date of the message (wraps carry random ones). */
    val createdAt: Long,
    val subject: String? = null,
    /** Id of the message this one replies to, if any. */
    val replyTo: String? = null,
) {
    /** Everyone but the author: the keys that receive a `p` tag and a gift wrap. */
    val recipients: List<String> get() = participants.filter { it != author }

    /** The rumor to seal: `p` tags for the recipients, `e` for a reply, `subject` when set. */
    fun toRumor(): Rumor {
        val tags = buildList {
            recipients.forEach { add(listOf("p", it)) }
            replyTo?.let { add(listOf("e", it)) }
            subject?.let { add(listOf("subject", it)) }
        }
        return Rumor.of(UnsignedEvent(author, createdAt, EventKinds.CHAT_MESSAGE, tags, text))
    }

    companion object {
        /** A new message from [author] to [recipients] (the author may be listed, it is simply not duplicated). */
        fun compose(
            author: PublicKey,
            recipients: Collection<PublicKey>,
            text: String,
            createdAt: Long,
            subject: String? = null,
            replyTo: String? = null,
        ): ChatMessage {
            require(text.isNotEmpty()) { "A message has a text" }
            val others = recipients.map { it.hex }.filter { it != author.hex }.distinct()
            require(others.isNotEmpty()) { "A message has at least one recipient other than its author" }
            replyTo?.let { require(isLowercaseHex(it, 64)) { "replyTo is a 64-character lowercase hexadecimal event id" } }
            val participants = (others + author.hex).sorted()
            val draft = ChatMessage(
                id = "",
                author = author.hex,
                conversationId = Conversation.idOf(participants),
                participants = participants,
                text = text,
                createdAt = createdAt,
                subject = subject?.takeIf { it.isNotBlank() },
                replyTo = replyTo,
            )
            return draft.copy(id = draft.toRumor().id)
        }

        /**
         * Reads a received rumor. Rejects anything that is not a well-formed kind 14 with at least
         * one valid `p` tag, so that every participant derives the same conversation.
         */
        fun fromRumor(rumor: Rumor): ChatMessage {
            require(rumor.kind == EventKinds.CHAT_MESSAGE) { "Not a chat message (kind ${rumor.kind})" }
            require(isLowercaseHex(rumor.pubkey, 64)) { "Malformed author" }
            val recipients = rumor.tagValues("p")
            require(recipients.isNotEmpty()) { "A chat message names its recipients" }
            recipients.forEach { require(isLowercaseHex(it, 64)) { "Malformed recipient" } }
            val participants = (recipients + rumor.pubkey).distinct().sorted()
            val replyTo = rumor.firstTagValue("e")?.takeIf { isLowercaseHex(it, 64) }
            return ChatMessage(
                id = rumor.id,
                author = rumor.pubkey,
                conversationId = Conversation.idOf(participants),
                participants = participants,
                text = rumor.content,
                createdAt = rumor.createdAt,
                subject = rumor.firstTagValue("subject")?.takeIf { it.isNotBlank() },
                replyTo = replyTo,
            )
        }
    }
}
