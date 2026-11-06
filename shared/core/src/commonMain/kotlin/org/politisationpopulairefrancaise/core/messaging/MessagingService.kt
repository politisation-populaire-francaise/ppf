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

/** What happened to the gift wraps of one sent message (one wrap per participant, sender included). */
data class SendReport(
    val message: ChatMessage,
    /** Wraps the server accepted. */
    val delivered: Int = 0,
    /** Wraps kept on the device because the server could not be reached; [MessagingService.flushOutgoing] retries them. */
    val queued: Int = 0,
    /** Wraps the server refused for good; the recipient will not get this message. */
    val rejected: Int = 0,
)

/** What a batch of received wraps produced. */
data class ReceiveReport(val stored: Int = 0, val duplicates: Int = 0, val ignored: Int = 0) {
    operator fun plus(other: ReceiveReport): ReceiveReport =
        ReceiveReport(stored + other.stored, duplicates + other.duplicates, ignored + other.ignored)
}

/**
 * What a pull from the server produced; [reachable] is false when the server could not be contacted,
 * [complete] is false when the page limit stopped the pull before the oldest wraps (the next pull
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
) {
    val publicKey: PublicKey get() = signer.publicKey

    /**
     * Sends [text] to [recipients]: stores the message locally, builds one gift wrap per participant
     * (the user included, so that other devices of the user receive it too), queues them and tries to
     * publish them. Wraps the server cannot take right now stay queued for [flushOutgoing].
     */
    suspend fun send(text: String, recipients: Collection<PublicKey>, subject: String? = null, replyTo: String? = null): SendReport {
        val message = ChatMessage.compose(signer.publicKey, recipients, text, clock(), subject, replyTo)
        store.store(message, read = true)
        val rumor = message.toRumor()
        val now = clock()
        val wraps = message.participants.map { hex -> GiftWrap.wrapFor(rumor, signer, PublicKey.fromHex(hex), now) }
        wraps.forEach { store.queueOutgoing(it, message.id) }
        var report = SendReport(message)
        var unreachable = false
        for (wrap in wraps) {
            if (unreachable) {
                report = report.copy(queued = report.queued + 1)
                continue
            }
            when (remote.publish(wrap)) {
                is PublicationResult.Accepted -> {
                    store.removeOutgoing(wrap.id)
                    report = report.copy(delivered = report.delivered + 1)
                }
                is PublicationResult.Rejected -> {
                    store.removeOutgoing(wrap.id)
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

    /** Retries the queued wraps, oldest first; stops at the first network failure. */
    suspend fun flushOutgoing(): FlushReport {
        var report = FlushReport()
        val pending = store.outgoing()
        for ((index, outgoing) in pending.withIndex()) {
            when (remote.publish(outgoing.wrap)) {
                is PublicationResult.Accepted -> {
                    store.removeOutgoing(outgoing.wrap.id)
                    report = report.copy(delivered = report.delivered + 1)
                }
                is PublicationResult.Rejected -> {
                    store.removeOutgoing(outgoing.wrap.id)
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
     * wrapped for themselves) are stored as read.
     */
    fun receive(wraps: List<SignedEvent>): ReceiveReport {
        var report = ReceiveReport()
        val me = signer.publicKey.hex
        for (wrap in wraps) {
            val message = try {
                val rumor = GiftWrap.unwrap(wrap, signer)
                ChatMessage.fromRumor(rumor).takeIf { me in it.participants }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            report = when {
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
     * server's limit, newest first. The first pull of a device fetches the whole history.
     */
    suspend fun pull(pageSize: Int = 200, maxPages: Int = 10): PullReport {
        val startedAt = clock()
        val since = store.lastPullAt()?.let { it - GiftWrap.TIMESTAMP_JITTER_SECONDS - CLOCK_SKEW_MARGIN_SECONDS }
        val tags = listOf(TagFilter("p", signer.publicKey.hex))
        var until: Long? = null
        var fetched = 0
        var received = ReceiveReport()
        repeat(maxPages) {
            val filter = RemoteFilter(kinds = listOf(EventKinds.GIFT_WRAP), since = since, until = until, limit = pageSize)
            val page = when (val result = remote.fetchByTags(tags, filter)) {
                is FetchResult.Unavailable -> return PullReport(fetched, received, reachable = false, complete = false)
                is FetchResult.Events -> result.events
            }
            fetched += page.size
            received += receive(page)
            // The next page is everything at or before the oldest date seen; when that date does not
            // move, the server has nothing older (or many wraps share one second: the next pull gets them).
            val oldest = page.minOfOrNull { it.createdAt }
            if (page.size < pageSize || oldest == until) {
                store.setLastPullAt(startedAt)
                return PullReport(fetched, received)
            }
            until = oldest
        }
        return PullReport(fetched, received, complete = false)
    }

    private companion object {
        /** Tolerance for clocks that disagree between the device and the server. */
        const val CLOCK_SKEW_MARGIN_SECONDS = 60 * 60L
    }
}
