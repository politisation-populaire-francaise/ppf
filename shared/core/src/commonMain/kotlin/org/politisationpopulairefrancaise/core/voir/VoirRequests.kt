package org.politisationpopulairefrancaise.core.voir

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/*
 * Requests sent from "Voir" to the editorial team (cahier fonctionnel M17; VO-21 right of reply,
 * VO-22 log of corrections): a right of reply, or the report of an error. A request is a small
 * versioned JSON object (format "ppf-voir-request", docs/integration/voir-demandes.md) carried as
 * the text of a NIP-17 private message to the team's inbox key, wrapped NIP-59 like every private
 * message: only the team can read it (identity of a person, accusations), it travels through the
 * same relay, and the team answers in the requester's private messages. Nothing about it is public
 * until the team publishes the reply or the correction.
 */

/** Where requests go, and the rules they follow. */
object VoirRequests {
    /**
     * Public key (hexadecimal) of the editorial team's inbox. For now the key of the example
     * editorial team, so that the whole path can be tried; production uses a dedicated team inbox
     * key, distinct from the key that signs the contents, held by the publication director's team
     * (to set here, or to pass to [VoirRequestOutbox], before the public launch).
     */
    const val TEAM_INBOX: String = EditorialTrust.EXAMPLE_EDITOR

    /** Value of the `format` field: tells a request apart from any other private message. */
    const val FORMAT: String = "ppf-voir-request"

    /** Version of the format; a reader refuses versions it does not know. */
    const val VERSION: Int = 1

    /** A reply may always be this long, however short the content in question (VO-21). */
    const val MIN_REPLY_WORDS: Int = 1000

    const val MAX_NAME_CHARS: Int = 200
    const val MAX_FIELD_CHARS: Int = 4000
    const val MAX_SOURCE_CHARS: Int = 2000

    /**
     * Longest reply allowed for a content of [contentWords] words: the length of the content in
     * question, with a guaranteed minimum of [MIN_REPLY_WORDS] words (VO-21).
     */
    fun maxReplyWords(contentWords: Int): Int = maxOf(contentWords, MIN_REPLY_WORDS)

    /** Words of a text: runs of characters between spaces that hold at least one letter or digit. */
    fun countWords(text: String?): Int =
        text?.split(WHITESPACE)?.count { word -> word.any { it.isLetterOrDigit() } } ?: 0

    /** Words of a content of "Voir": its title, its summary and its body. */
    fun contentWords(article: Article): Int =
        countWords(article.title.removePrefix(VoirCatalog.EXAMPLE_MARKER)) + countWords(article.summary) + countWords(article.body)

    /** The text of the private message that carries [request]. */
    fun encode(request: VoirRequest): String = RequestJson.encodeToString(VoirRequest.serializer(), request)

    /** The request carried by a private message, or null for any other text (never throws). */
    fun decodeOrNull(text: String): VoirRequest? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains(FORMAT)) return null
        return try {
            RequestJson.decodeFromString(VoirRequest.serializer(), trimmed).takeIf { it.format == FORMAT && it.version == VERSION }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** True when [value] is an `http` or `https` web address of reasonable length, without spaces. */
    fun isWebAddress(value: String): Boolean {
        val v = value.trim()
        if (v.length > MAX_SOURCE_CHARS || v.any { it.isWhitespace() }) return false
        val rest = when {
            v.startsWith("https://", ignoreCase = true) -> v.substring(8)
            v.startsWith("http://", ignoreCase = true) -> v.substring(7)
            else -> return false
        }
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.length >= 3 && '.' in host
    }

    private val WHITESPACE = Regex("\\s+")
}

/** In what capacity the respondent answers. */
@Serializable
enum class ReplyCapacity {
    /** The person named or designated in the content. */
    @SerialName("person-named") PERSON_NAMED,

    /** Their legal representative (lawyer, legal guardian, representative of an organisation). */
    @SerialName("legal-representative") LEGAL_REPRESENTATIVE,
}

/** What is wrong with a request; the form says it before anything is sent. */
enum class RequestProblem {
    CONTENT_ADDRESS,
    RESPONDENT_MISSING,
    RESPONDENT_TOO_LONG,
    REPLY_MISSING,
    REPLY_TOO_LONG,
    STATEMENT_MISSING,
    WRONG_MISSING,
    FIELD_TOO_LONG,
    SOURCE_INVALID,

    /** The whole request is larger than one private message can carry ([ChatMessage.MAX_TEXT_BYTES]). */
    MESSAGE_TOO_LARGE,
}

/** A request to the editorial team about one content of "Voir". */
@Serializable
sealed class VoirRequest {
    abstract val format: String
    abstract val version: Int

    /** Stable address of the content, `kind:pubkey:identifier`. */
    abstract val content: String

    /** Title of the content when the request was written, for the team's convenience. */
    abstract val contentTitle: String?

    /** Every problem of this request; empty when it can be sent. */
    abstract fun problems(): List<RequestProblem>

    protected fun commonProblems(): List<RequestProblem> = buildList {
        if (runCatching { ContentAddress.parse(content) }.isFailure) add(RequestProblem.CONTENT_ADDRESS)
        if (VoirRequests.encode(this@VoirRequest).encodeToByteArray().size > ChatMessage.MAX_TEXT_BYTES) add(RequestProblem.MESSAGE_TOO_LARGE)
    }
}

/**
 * A right of reply (VO-21; LCEN, article 1-1, III, formerly article 6, IV; decree 2007-1527): sent
 * from an account, so that the team can answer and the person can follow up. The team checks the
 * identity in the conversation, then the publication director publishes the reply under the
 * content within three days, with the mention "Droit de réponse de [nom]".
 */
@Serializable
@SerialName("right-of-reply")
data class ReplyRequest(
    override val content: String,
    @SerialName("content_title") override val contentTitle: String? = null,
    /** Name of the person named or designated, as it should appear in the mention. */
    val respondent: String,
    val capacity: ReplyCapacity,
    /** The reply itself, plain text. */
    val reply: String,
    /** Words of the content in question when the request was written. */
    @SerialName("content_words") val contentWords: Int,
    /** The longest reply allowed: max(content words, 1000). */
    @SerialName("max_words") val maxWords: Int = VoirRequests.maxReplyWords(contentWords),
    /** Words of [reply], as counted on the device. */
    @SerialName("reply_words") val replyWords: Int = VoirRequests.countWords(reply),
    /** The respondent certifies that they are the person named, or their legal representative, and that the information given is accurate. */
    @SerialName("statement_of_truth") val statementOfTruth: Boolean,
    override val format: String = VoirRequests.FORMAT,
    override val version: Int = VoirRequests.VERSION,
) : VoirRequest() {
    override fun problems(): List<RequestProblem> = buildList {
        when {
            respondent.isBlank() -> add(RequestProblem.RESPONDENT_MISSING)
            respondent.length > VoirRequests.MAX_NAME_CHARS -> add(RequestProblem.RESPONDENT_TOO_LONG)
        }
        when {
            reply.isBlank() -> add(RequestProblem.REPLY_MISSING)
            VoirRequests.countWords(reply) > VoirRequests.maxReplyWords(contentWords) -> add(RequestProblem.REPLY_TOO_LONG)
        }
        if (!statementOfTruth) add(RequestProblem.STATEMENT_MISSING)
        addAll(commonProblems())
    }
}

/**
 * The report of an error in a content (VO-22): what is wrong, the correction proposed and,
 * optionally, a source. It may be sent anonymously (a key used for this report only): the team
 * then cannot answer.
 */
@Serializable
@SerialName("error-report")
data class ErrorReport(
    override val content: String,
    @SerialName("content_title") override val contentTitle: String? = null,
    /** What is wrong. */
    val wrong: String,
    /** The correction proposed, if any. */
    val correction: String? = null,
    /** Web address of a source that supports the correction, if any. */
    val source: String? = null,
    /** True when sent with a single-use key: nobody can answer it. */
    val anonymous: Boolean,
    override val format: String = VoirRequests.FORMAT,
    override val version: Int = VoirRequests.VERSION,
) : VoirRequest() {
    override fun problems(): List<RequestProblem> = buildList {
        if (wrong.isBlank()) add(RequestProblem.WRONG_MISSING)
        if (wrong.length > VoirRequests.MAX_FIELD_CHARS || (correction?.length ?: 0) > VoirRequests.MAX_FIELD_CHARS) add(RequestProblem.FIELD_TOO_LONG)
        if (source != null && !VoirRequests.isWebAddress(source)) add(RequestProblem.SOURCE_INVALID)
        addAll(commonProblems())
    }
}

/** What became of a request sent anonymously. */
enum class AnonymousDelivery {
    /** The server took it. */
    SENT,

    /** No server could take it now: kept in memory and sent again by [VoirRequestOutbox.flush]. */
    PENDING,

    /** The server refused it for good. */
    REJECTED,
}

/** The outcome of [VoirRequestOutbox.sendAnonymously]; [author] is the single-use key that signed it. */
data class AnonymousReceipt(val delivery: AnonymousDelivery, val author: PublicKey, val messageId: String)

/**
 * Sends anonymous requests to the team: each one is written and sealed by a brand new key used for
 * this request only, then gift wrapped for the team alone (no copy for the sender, who keeps
 * nothing that could link them to it). The key lives in memory until the server takes the request
 * or refuses it, so that a request written offline is wrapped again with fresh dates when it
 * leaves; it is then destroyed. Pending requests do not survive the end of the application (no
 * table holds them: protection by default, and no database migration for now).
 *
 * Requests sent with the user's account go through the account's [org.politisationpopulairefrancaise.core.messaging.MessagingService]
 * instead, like any private message, so that the team's answer arrives in the same conversation.
 */
class VoirRequestOutbox(
    private val remote: RemoteEvents?,
    teamInbox: String = VoirRequests.TEAM_INBOX,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private class Pending(val keys: IdentityKeys, val message: ChatMessage)

    val team: PublicKey = PublicKey.fromHex(teamInbox)

    private val lock = Mutex()
    private val pending = mutableListOf<Pending>()

    /** Requests waiting for a server. */
    suspend fun pendingCount(): Int = lock.withLock { pending.size }

    /**
     * Sends [request] signed by a single-use key. Throws `IllegalArgumentException` when the request
     * has a problem ([VoirRequest.problems]).
     */
    suspend fun sendAnonymously(request: VoirRequest, subject: String? = null): AnonymousReceipt {
        require(request.problems().isEmpty()) { "The request has problems: ${request.problems()}" }
        val keys = IdentityKeys.generate()
        val message = ChatMessage.compose(keys.publicKey, listOf(team), VoirRequests.encode(request), clock(), subject)
        val entry = Pending(keys, message)
        val delivery = lock.withLock { deliver(entry).also { if (it == AnonymousDelivery.PENDING) pending += entry } }
        return AnonymousReceipt(delivery, message.authorKey(), message.id)
    }

    /** Sends the pending requests again, oldest first; stops at the first network failure. Returns how many left. */
    suspend fun flush(): Int = lock.withLock {
        var sent = 0
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            when (deliver(entry)) {
                AnonymousDelivery.PENDING -> break
                AnonymousDelivery.SENT -> {
                    iterator.remove()
                    sent++
                }
                AnonymousDelivery.REJECTED -> iterator.remove()
            }
        }
        sent
    }

    /** Wraps [entry] for the team with fresh dates and publishes it; destroys its key once the server decided. */
    private suspend fun deliver(entry: Pending): AnonymousDelivery {
        val server = remote ?: return AnonymousDelivery.PENDING
        val wrap = GiftWrap.wrapFor(entry.message.toRumor(), LocalMessagingSigner(entry.keys), team, clock())
        val outcome = when (server.publish(wrap)) {
            is PublicationResult.Accepted -> AnonymousDelivery.SENT
            is PublicationResult.Rejected -> AnonymousDelivery.REJECTED
            is PublicationResult.Unavailable -> AnonymousDelivery.PENDING
        }
        if (outcome != AnonymousDelivery.PENDING) entry.keys.destroy()
        return outcome
    }

    private fun ChatMessage.authorKey(): PublicKey = PublicKey.fromHex(author)
}

private val RequestJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}
