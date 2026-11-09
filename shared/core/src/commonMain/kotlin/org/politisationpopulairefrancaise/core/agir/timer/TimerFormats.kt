package org.politisationpopulairefrancaise.core.agir.timer

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.signing.isLowercaseHex

/**
 * Formats of AG-55 « Sans nouvelles de moi » (decision D-088; docs/integration/agir-minuteur.md,
 * docs/FORMATS.md section 4 quattuordecies).
 *
 * Every message of a timer is a rumor of kind [KIND], written by the account of the person who arms
 * it, sealed for one recipient and gift wrapped like a private message (NIP-59 over NIP-44): the
 * server relays envelopes and learns nothing, neither the kind, nor the author, nor the deadline.
 *
 * - **Deposit** (`ppf-action` = `depot`), sent to each recipient when the timer is armed: the
 *   deadline, `h` = SHA-256 of a random 32-byte secret kept on the sender's device, the prepared
 *   alert text and, if the person typed one, the planned place. The recipient's device keeps it
 *   sealed and shows the alert by itself at the deadline.
 * - **Cancellation** (`annulation`): reveals the secret; accepted only if its SHA-256 is the `h` of
 *   the same timer.
 * - **Report** (`report`): reveals the current secret, with a new deadline and the hash of a new
 *   secret, kept on the sender's device.
 *
 * Each rumor names one recipient only (`p`): a recipient never learns who else was chosen.
 */
object TimerFormats {
    const val KIND: Int = EventKinds.TIMER

    const val TAG_RECIPIENT: String = "p"
    const val TAG_TYPE: String = "ppf-type"
    const val TYPE: String = "minuteur"
    const val TAG_ACTION: String = "ppf-action"
    const val ACTION_DEPOSIT: String = "depot"
    const val ACTION_CANCEL: String = "annulation"
    const val ACTION_REPORT: String = "report"
    const val TAG_TIMER: String = "ppf-timer"
    const val TAG_DEADLINE: String = "ppf-deadline"
    const val TAG_HASH: String = "ppf-hash"
    const val TAG_SECRET: String = "ppf-secret"
    const val TAG_PLACE: String = "location"

    /** Longest alert text, in characters. */
    const val MAX_TEXT: Int = 1_000

    /** Longest planned place, in characters (the same limit as the place of a testimony). */
    const val MAX_PLACE: Int = 200

    /** A deadline is at least this far from the moment it is set. */
    const val MIN_DURATION_SECONDS: Long = 15 * 60L

    /** And at most this far: anything further is refused when arming and ignored when received. */
    const val MAX_DURATION_SECONDS: Long = 48 * 3_600L

    /** Size of the secret and of its hash, in bytes. */
    const val SECRET_BYTES: Int = 32

    /** The deposit of a new timer for [recipient]. */
    fun deposit(
        author: String,
        recipient: String,
        timerId: String,
        createdAt: Long,
        deadline: Long,
        hashHex: String,
        text: String,
        place: String?,
    ): Rumor {
        val extra = buildList {
            add(listOf(TAG_DEADLINE, deadline.toString()))
            add(listOf(TAG_HASH, hashHex))
            place?.let { add(listOf(TAG_PLACE, it)) }
        }
        return rumor(author, recipient, createdAt, ACTION_DEPOSIT, timerId, extra, text)
    }

    /** « Je vais bien »: reveals the current secret of the timer. */
    fun cancel(author: String, recipient: String, timerId: String, createdAt: Long, secretHex: String): Rumor =
        rumor(author, recipient, createdAt, ACTION_CANCEL, timerId, listOf(listOf(TAG_SECRET, secretHex)))

    /** « Repousser »: reveals the current secret and announces a new deadline and the hash of the next secret. */
    fun report(author: String, recipient: String, timerId: String, createdAt: Long, secretHex: String, deadline: Long, hashHex: String): Rumor =
        rumor(
            author, recipient, createdAt, ACTION_REPORT, timerId,
            listOf(listOf(TAG_SECRET, secretHex), listOf(TAG_DEADLINE, deadline.toString()), listOf(TAG_HASH, hashHex)),
        )

    /**
     * Reads a rumor received by [recipient] (lowercase hexadecimal key); null for anything that is
     * not a well-formed timer message addressed to them by someone else. Texts are cleaned (no
     * control characters but line breaks in the alert text) and cut to their limits.
     */
    fun read(rumor: Rumor, recipient: String): TimerMessage? {
        if (rumor.kind != KIND) return null
        if (!isLowercaseHex(rumor.pubkey, 64) || rumor.pubkey == recipient) return null
        if (rumor.firstTagValue(TAG_TYPE) != TYPE) return null
        if (recipient !in rumor.tagValues(TAG_RECIPIENT)) return null
        val timerId = rumor.firstTagValue(TAG_TIMER)?.takeIf { isLowercaseHex(it, 64) } ?: return null
        val author = rumor.pubkey
        val createdAt = rumor.createdAt
        return when (rumor.firstTagValue(TAG_ACTION)) {
            ACTION_DEPOSIT -> {
                val deadline = readDeadline(rumor, createdAt) ?: return null
                val hash = rumor.firstTagValue(TAG_HASH)?.takeIf { isLowercaseHex(it, 64) } ?: return null
                val text = clean(rumor.content, keepLineBreaks = true).take(MAX_TEXT).trim()
                if (text.isEmpty()) return null
                val place = rumor.firstTagValue(TAG_PLACE)?.let { clean(it, keepLineBreaks = false).take(MAX_PLACE).trim() }?.takeIf { it.isNotEmpty() }
                TimerMessage.Deposit(author, timerId, createdAt, deadline, hash, text, place)
            }
            ACTION_CANCEL -> {
                val secret = rumor.firstTagValue(TAG_SECRET)?.takeIf { isLowercaseHex(it, 64) } ?: return null
                TimerMessage.Cancel(author, timerId, createdAt, secret)
            }
            ACTION_REPORT -> {
                val secret = rumor.firstTagValue(TAG_SECRET)?.takeIf { isLowercaseHex(it, 64) } ?: return null
                val deadline = readDeadline(rumor, createdAt) ?: return null
                val hash = rumor.firstTagValue(TAG_HASH)?.takeIf { isLowercaseHex(it, 64) } ?: return null
                TimerMessage.Report(author, timerId, createdAt, secret, deadline, hash)
            }
            else -> null
        }
    }

    /** SHA-256 of a secret given in hexadecimal, in lowercase hexadecimal: what a deposit or a report announces. */
    fun hashOf(secretHex: String): String? =
        if (isLowercaseHex(secretHex, 64)) Crypto.sha256(secretHex.hexToByteArray()).toHexString() else null

    /** SHA-256 of a secret, in lowercase hexadecimal. */
    fun hashOf(secret: ByteArray): String = Crypto.sha256(secret).toHexString()

    /** The text the person typed, cleaned as on reception: no control characters but line breaks, trimmed. */
    fun cleanText(text: String): String = clean(text, keepLineBreaks = true).take(MAX_TEXT).trim()

    /** The place the person typed, cleaned as on reception; null when empty. */
    fun cleanPlace(place: String?): String? = place?.let { clean(it, keepLineBreaks = false).take(MAX_PLACE).trim() }?.takeIf { it.isNotEmpty() }

    private fun readDeadline(rumor: Rumor, createdAt: Long): Long? {
        val deadline = rumor.firstTagValue(TAG_DEADLINE)?.toLongOrNull() ?: return null
        return deadline.takeIf { it > createdAt && it - createdAt <= MAX_DURATION_SECONDS }
    }

    private fun rumor(
        author: String,
        recipient: String,
        createdAt: Long,
        action: String,
        timerId: String,
        extra: List<List<String>>,
        content: String = "",
    ): Rumor {
        val tags = listOf(
            listOf(TAG_RECIPIENT, recipient),
            listOf(TAG_TYPE, TYPE),
            listOf(TAG_ACTION, action),
            listOf(TAG_TIMER, timerId),
        ) + extra
        return Rumor.of(UnsignedEvent(author, createdAt, KIND, tags, content))
    }

    private fun clean(text: String, keepLineBreaks: Boolean): String =
        text.filter { c -> !c.isISOControl() || (keepLineBreaks && c == '\n') }
}

/** A timer message once read and checked by [TimerFormats.read]. */
sealed interface TimerMessage {
    /** The account that armed the timer (hexadecimal key), checked by the seal. */
    val author: String

    /** The identifier of the timer: 32 random bytes in hexadecimal, drawn when it is armed. */
    val timerId: String

    /** The date written by the author, in seconds. */
    val createdAt: Long

    data class Deposit(
        override val author: String,
        override val timerId: String,
        override val createdAt: Long,
        val deadline: Long,
        val hashHex: String,
        val text: String,
        val place: String?,
    ) : TimerMessage

    data class Cancel(
        override val author: String,
        override val timerId: String,
        override val createdAt: Long,
        val secretHex: String,
    ) : TimerMessage

    data class Report(
        override val author: String,
        override val timerId: String,
        override val createdAt: Long,
        val secretHex: String,
        val deadline: Long,
        val hashHex: String,
    ) : TimerMessage
}
