package org.politisationpopulairefrancaise.core.messaging

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** What happened to the deliveries of one sent message (one gift wrap per participant, sender included). */
data class SendReport(
    val message: ChatMessage,
    /** Wraps the server accepted. */
    val delivered: Int = 0,
    /** Deliveries kept on the device because the server could not take them now; [MessagingService.flushOutgoing] retries them. */
    val queued: Int = 0,
    /** Wraps the server refused for good; the participant will not get this message. */
    val rejected: Int = 0,
)

/**
 * What a batch of received wraps produced. [others] are the authentic rumors of another kind than a
 * chat message that a space takes ([IncomingRumors], D-088), handed over by [MessagingService.pull].
 */
data class ReceiveReport(val stored: Int = 0, val duplicates: Int = 0, val ignored: Int = 0, val others: List<Rumor> = emptyList()) {
    operator fun plus(other: ReceiveReport): ReceiveReport =
        ReceiveReport(stored + other.stored, duplicates + other.duplicates, ignored + other.ignored, others + other.others)
}

/**
 * What a pull from the server produced; [reachable] is false when the server could not be contacted,
 * [complete] is false when the page budget stopped the pull before the oldest wraps (the next pull
 * starts again from the same date).
 */
data class PullReport(
    val fetched: Int = 0,
    val received: ReceiveReport = ReceiveReport(),
    val reachable: Boolean = true,
    val complete: Boolean = true,
)

data class FlushReport(val delivered: Int = 0, val rejected: Int = 0, val remaining: Int = 0, val reachable: Boolean = true)

/**
 * Private messaging of one identity (NIP-17 over NIP-59 and NIP-44, decision D-027): composes,
 * wraps and sends messages, opens and stores the wraps addressed to the user, through the same
 * publication port as every other content. The server only ever sees gift wraps: a one-time
 * author, the recipient's `p` tag, a random date and an opaque content. It holds no list of
 * conversations or contacts; those live in [MessageStore], on the device.
 */
class MessagingService(
    private val signer: MessagingSigner,
    private val store: MessageStore,
    private val remote: RemoteEvents,
    private val clock: () -> Long = ::currentEpochSeconds,
    /** Where the rumors that are not chat messages go (D-088): the registry of the process by default. */
    private val others: IncomingRumors = IncomingRumors.Process,
) {
    val publicKey: PublicKey get() = signer.publicKey

    /**
     * Sends [text] to [recipients]: builds one gift wrap per participant (the user included, so that
     * their other devices receive it too), then stores the message, records the deliveries and tries
     * to publish the wraps. Deliveries the server cannot take right now wait for [flushOutgoing],
     * which wraps them again with fresh dates. Nothing is stored if the text is too long or a
     * recipient key is not a valid secp256k1 point (`IllegalArgumentException`).
     */
    suspend fun send(text: String, recipients: Collection<PublicKey>, subject: String? = null, replyTo: String? = null): SendReport {
        val message = ChatMessage.compose(signer.publicKey, recipients, text, clock(), subject, replyTo)
        val wraps = message.participants.map { it to wrapFor(message, it) }
        store.store(message, read = true)
        message.participants.forEach { store.queueOutgoing(message.id, it) }
        var report = SendReport(message)
        var unreachable = false
        for ((recipient, wrap) in wraps) {
            if (unreachable) {
                report = report.copy(queued = report.queued + 1)
                continue
            }
            when (remote.publish(wrap)) {
                is PublicationResult.Accepted -> {
                    store.removeOutgoing(message.id, recipient)
                    report = report.copy(delivered = report.delivered + 1)
                }
                is PublicationResult.Rejected -> {
                    store.removeOutgoing(message.id, recipient)
                    report = report.copy(rejected = report.rejected + 1)
                }
                is PublicationResult.Unavailable -> {
                    unreachable = true
                    report = report.copy(queued = report.queued + 1)
                }
            }
        }
        return report
    }

    /**
     * Retries the pending deliveries, oldest first, each with a brand new gift wrap (fresh one-time
     * key and fresh random dates, so that a message written offline is dated like a new one and is
     * not missed by the recipients' pull window). Stops at the first network failure.
     */
    suspend fun flushOutgoing(): FlushReport {
        var report = FlushReport()
        val pending = store.outgoing()
        for ((index, delivery) in pending.withIndex()) {
            val message = store.find(delivery.messageId)
            if (message == null || message.author != signer.publicKey.hex) {
                // The conversation was deleted meanwhile, or the message belongs to another identity.
                if (message == null) store.removeOutgoing(delivery.messageId, delivery.recipient)
                continue
            }
            val wrap = try {
                wrapFor(message, delivery.recipient)
            } catch (e: IllegalArgumentException) {
                store.removeOutgoing(delivery.messageId, delivery.recipient)
                report = report.copy(rejected = report.rejected + 1)
                continue
            }
            when (remote.publish(wrap)) {
                is PublicationResult.Accepted -> {
                    store.removeOutgoing(delivery.messageId, delivery.recipient)
                    report = report.copy(delivered = report.delivered + 1)
                }
                is PublicationResult.Rejected -> {
                    store.removeOutgoing(delivery.messageId, delivery.recipient)
                    report = report.copy(rejected = report.rejected + 1)
                }
                is PublicationResult.Unavailable -> return report.copy(remaining = pending.size - index, reachable = false)
            }
        }
        return report
    }

    /**
     * Opens wraps addressed to the user and stores the chat messages they carry. Anything that is
     * not an authentic wrap for this key, carrying a well-formed chat message that names the user,
     * is counted as ignored and dropped without an exception. The user's own messages (the copies
     * wrapped for themselves) are stored as read. A date in the future is brought back to now, so
     * that no sender can pin a conversation at the top of the list. An authentic rumor of another
     * kind that a space takes ([IncomingRumors.takes], D-088) is returned in [ReceiveReport.others]
     * for [pull] to hand over; nothing of it is stored here.
     */
    fun receive(wraps: List<SignedEvent>): ReceiveReport {
        var report = ReceiveReport()
        val me = signer.publicKey.hex
        val latestAcceptable = clock() + FUTURE_TOLERANCE_SECONDS
        for (wrap in wraps) {
            var other: Rumor? = null
            val message = try {
                val rumor = GiftWrap.unwrap(wrap, signer)
                if (rumor.kind != EventKinds.CHAT_MESSAGE && others.takes(rumor.kind)) {
                    // D-088: another kind a space takes (an AG-55 timer), set aside for [pull], never stored here.
                    other = rumor
                    null
                } else {
                    ChatMessage.fromRumor(rumor).takeIf { me in it.participants }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }?.let { if (it.createdAt > latestAcceptable) it.copy(createdAt = latestAcceptable) else it }
            val taken = other
            report = when {
                taken != null -> report.copy(others = report.others + taken)
                message == null -> report.copy(ignored = report.ignored + 1)
                store.store(message, read = message.author == me) == MessageStoreOutcome.STORED -> report.copy(stored = report.stored + 1)
                else -> report.copy(duplicates = report.duplicates + 1)
            }
        }
        return report
    }

    /**
     * Fetches the wraps addressed to the user (`tag=p:<key>`, kind 1059) and stores their messages.
     * Wrap dates are randomized up to two days in the past, so each pull asks from the previous
     * complete pull minus that window and a margin; duplicates are harmless. Pages through the
     * server's limit, newest first; the first pull of a device fetches the whole history (the server
     * keeps at most 1000 wraps per recipient, which the default page budget covers).
     */
    suspend fun pull(pageSize: Int = DEFAULT_PAGE_SIZE, maxPages: Int = DEFAULT_MAX_PAGES): PullReport {
        val size = pageSize.coerceIn(1, SERVER_MAX_LIMIT)
        val startedAt = clock()
        val since = store.lastPullAt()?.let { it - GiftWrap.TIMESTAMP_JITTER_SECONDS - CLOCK_SKEW_MARGIN_SECONDS }
        val tags = listOf(TagFilter("p", signer.publicKey.hex))
        var until: Long? = null
        var fetched = 0
        var received = ReceiveReport()
        var held = 0
        repeat(maxPages) {
            val filter = RemoteFilter(kinds = listOf(EventKinds.GIFT_WRAP), since = since, until = until, limit = size)
            val page = when (val result = remote.fetchByTags(tags, filter)) {
                is FetchResult.Unavailable -> return PullReport(fetched, received, reachable = false, complete = false)
                is FetchResult.Events -> result.events
            }
            fetched += page.size
            val pageReport = receive(page)
            received += pageReport
            // D-088: the rumors of other kinds go to the handlers of their spaces.
            held += others.dispatch(pageReport.others, signer.publicKey.hex)
            if (page.size < size) {
                // A rumor nobody could take keeps the cursor where it was: the next pull fetches it again.
                if (held == 0) store.setLastPullAt(startedAt)
                return PullReport(fetched, received)
            }
            // Newest first, inclusive bounds: the next page starts at the oldest date seen. When that
            // date does not move, the whole page shares one second (a flood of wraps with one chosen
            // date): step past that second rather than stop, so that a flood cannot hide older wraps.
            val oldest = page.minOf { it.createdAt }
            until = if (oldest == until) oldest - 1 else oldest
        }
        return PullReport(fetched, received, complete = false)
    }

    private suspend fun wrapFor(message: ChatMessage, recipientHex: String): SignedEvent {
        val recipient = PublicKey.fromHex(recipientHex)
        return try {
            GiftWrap.wrapFor(message.toRumor(), signer, recipient, clock())
        } catch (e: Nip44Exception) {
            throw IllegalArgumentException("Recipient $recipientHex is not a valid public key", e)
        }
    }

    private companion object {
        /** Tolerance for clocks that disagree between the device and the server. */
        const val CLOCK_SKEW_MARGIN_SECONDS = 60 * 60L

        /** A received message dated further in the future than this is dated now instead. */
        const val FUTURE_TOLERANCE_SECONDS = 15 * 60L

        const val DEFAULT_PAGE_SIZE = 200
        const val DEFAULT_MAX_PAGES = 10

        /** The server never returns more than this many events per request. */
        const val SERVER_MAX_LIMIT = 500
    }
}
