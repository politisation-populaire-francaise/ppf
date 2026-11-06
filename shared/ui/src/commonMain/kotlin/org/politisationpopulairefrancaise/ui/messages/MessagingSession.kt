package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.Conversation
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.messaging.MessagingService
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.messaging.Nip44Exception
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState

/** Where the private messages stand with the server, modelled on LibraryStatus. */
sealed interface MessagingStatus {
    data object Loading : MessagingStatus

    /** No database on this platform yet: nothing can be stored, so nothing can be shown. */
    data object Unavailable : MessagingStatus

    /** No account on the device: reading and writing private messages needs keys. */
    data object NoAccount : MessagingStatus

    /** No server configured: messages are kept on the device and leave as soon as one answers. */
    data object Offline : MessagingStatus

    data class Synced(val received: Int) : MessagingStatus

    /** The server did not answer; what the device already holds is shown. */
    data object Unreachable : MessagingStatus
}

/** Why the last message could not be sent. */
enum class SendError {
    /** A recipient's key is not a valid secp256k1 point: nothing can be encrypted for it. */
    INVALID_RECIPIENT,

    /** More than [ChatMessage.MAX_TEXT_BYTES] bytes of text. */
    TOO_LONG,
}

/** A conversation of the list with what the row shows: its last message and whether it still waits for the server. */
data class ConversationSummary(val conversation: Conversation, val lastMessage: ChatMessage?, val pending: Boolean) {
    val id: String get() = conversation.id
}

/**
 * The conversation open on screen. It may not exist in the store yet: a conversation starts to
 * exist, on every device, with its first message.
 */
data class OpenConversation(
    val id: String,
    /** Every participant, sorted, the user included. */
    val participants: List<String>,
    val subject: String?,
) {
    fun others(me: String?): List<String> = participants.filter { it != me }
}

/**
 * The private conversations of the account (NIP-17 over NIP-59 and NIP-44, decision D-027) as the
 * screens need them: the list with its unread counts, the open thread, sending, and the exchanges
 * with the server (pending deliveries first, then the wraps addressed to the user). Everything is
 * read from and written to the device's [MessageStore]; the server only ever sees gift wraps.
 * Nothing about reading leaves the device: no read receipt, no typing indicator (S-125).
 *
 * One instance lives while the messages screen is shown, for one account: it refreshes when the
 * screen opens, on demand, and every [REFRESH_PERIOD_SECONDS] while a conversation is open.
 */
class MessagingSession(
    private val store: MessageStore?,
    private val signer: MessagingSigner?,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
) {
    /** Reads and writes on the store happen one at a time, off the main thread. */
    private val operations = Mutex()

    private val service: MessagingService? =
        if (store != null && signer != null) MessagingService(signer, store, remote ?: NoServer) else null

    /** The user's public key, hexadecimal, when there is an account. */
    val myKey: String? = signer?.publicKey?.hex

    /** Whether messages can be written and read on this device: a database and an account. */
    val canWrite: Boolean get() = service != null

    /** Whether a server is configured; without one, every message waits on the device. */
    val hasServer: Boolean get() = remote != null

    var status: MessagingStatus by mutableStateOf(MessagingStatus.Loading)
        private set

    /** True while the server is being asked; the refresh button shows it. */
    var refreshing: Boolean by mutableStateOf(false)
        private set

    var conversations: List<ConversationSummary> by mutableStateOf(emptyList())
        private set

    var open: OpenConversation? by mutableStateOf(null)
        private set

    /** The messages of the open conversation, oldest first. */
    var messages: List<ChatMessage> by mutableStateOf(emptyList())
        private set

    /** Ids of the user's own messages with at least one delivery still waiting for the server. */
    var pending: Set<String> by mutableStateOf(emptySet())
        private set

    var sendError: SendError? by mutableStateOf(null)
        private set

    val unreadTotal: Long get() = conversations.sumOf { it.conversation.unreadCount }

    fun start() {
        when {
            store == null -> status = MessagingStatus.Unavailable
            service == null -> status = MessagingStatus.NoAccount
            else -> scope.launch {
                reload()
                if (remote == null) status = MessagingStatus.Offline else sync()
            }
        }
    }

    /** Sends the pending deliveries, then fetches the wraps addressed to the user. No effect without a server. */
    fun refresh() {
        if (remote == null || service == null) return
        scope.launch { sync() }
    }

    /** Opens a conversation of the list; its messages are marked read on the device only. */
    fun open(conversation: Conversation) {
        openWith(conversation.participants, conversation.subject)
    }

    /**
     * Opens the conversation gathering the user and [recipientsHex], creating it on screen when it
     * does not exist yet; [subject] is given to its first message. Anything but valid keys is ignored.
     */
    fun openWith(recipientsHex: Collection<String>, subject: String? = null) {
        val me = myKey ?: return
        val keys = (recipientsHex + me).filter { FollowStore.isValidKey(it) }.distinct().sorted()
        if (keys.size < 2) return
        sendError = null
        open = OpenConversation(Conversation.idOf(keys), keys, subject?.trim()?.takeIf { it.isNotEmpty() })
        messages = emptyList()
        scope.launch { reload() }
    }

    fun close() {
        open = null
        messages = emptyList()
        sendError = null
    }

    /**
     * Checks that a message can be encrypted for [pubkeyHex]: a valid secp256k1 point. Done before
     * a conversation starts, so that a mistyped key is caught at once rather than at the first send.
     */
    fun canEncryptFor(pubkeyHex: String): Boolean {
        val signer = signer ?: return false
        if (!FollowStore.isValidKey(pubkeyHex) || pubkeyHex == myKey) return false
        return try {
            signer.encrypt(PublicKey.fromHex(pubkeyHex), "ok")
            true
        } catch (e: Nip44Exception) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Sends [text] in the open conversation: stored on the device at once, one gift wrap per
     * participant, deliveries the server cannot take now wait for the next refresh. Returns false
     * when nothing was sent; [sendError] then says why when the text itself is the cause.
     */
    suspend fun send(text: String): Boolean {
        val service = service ?: return false
        val target = open ?: return false
        val content = text.trim()
        if (content.isEmpty()) return false
        val recipients = target.others(myKey).map { PublicKey.fromHex(it) }
        // The subject travels with the first message of a conversation; the store keeps the newest one.
        val subject = target.subject?.takeIf { messages.isEmpty() }
        sendError = null
        try {
            withContext(Dispatchers.Default) { operations.withLock { service.send(content, recipients, subject) } }
        } catch (e: IllegalArgumentException) {
            sendError = if (content.encodeToByteArray().size > ChatMessage.MAX_TEXT_BYTES) SendError.TOO_LONG else SendError.INVALID_RECIPIENT
            return false
        }
        reload()
        return true
    }

    private suspend fun sync() {
        val service = service ?: return
        if (refreshing) return
        refreshing = true
        try {
            val (flushed, pulled) = withContext(Dispatchers.Default) {
                operations.withLock { service.flushOutgoing() to service.pull() }
            }
            reload()
            status = if (flushed.reachable && pulled.reachable) MessagingStatus.Synced(pulled.received.stored) else MessagingStatus.Unreachable
        } finally {
            refreshing = false
        }
    }

    /** Reads the list and the open thread again from the store; the open thread is marked read first. */
    private suspend fun reload() {
        val store = store ?: return
        val current = open
        val (list, thread, queued) = withContext(Dispatchers.Default) {
            operations.withLock {
                if (current != null) store.markRead(current.id)
                val queued = store.outgoing().map { it.messageId }.toSet()
                val list = store.conversations().map { conversation ->
                    val last = store.messages(conversation.id, limit = 1).lastOrNull()
                    ConversationSummary(conversation, last, pending = last != null && last.id in queued)
                }
                val thread = current?.let { store.messages(it.id) } ?: emptyList()
                Triple(list, thread, queued)
            }
        }
        conversations = list
        pending = queued
        if (open?.id == current?.id) {
            messages = thread
            // A subject that arrived meanwhile wins over the one typed when the conversation was opened.
            val stored = list.firstOrNull { it.id == current?.id }?.conversation
            if (current != null && stored != null && stored.subject != null && stored.subject != current.subject) {
                open = current.copy(subject = stored.subject)
            }
        }
    }

    /** Stands for the server when none is configured: everything waits on the device. */
    private object NoServer : RemoteEvents {
        override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Unavailable()
        override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
    }

    companion object {
        /** While a conversation is open, the server is asked again this often. */
        const val REFRESH_PERIOD_SECONDS: Int = 20
    }
}

/**
 * One session per account: it is built again when the account appears, changes or is erased, and
 * refreshes at once when a server is configured.
 */
@Composable
fun rememberMessagingSession(dependencies: AppDependencies, session: AccountSession): MessagingSession {
    val scope = rememberCoroutineScope()
    val accountKey = (session.state as? AccountUiState.Ready)?.publicKey?.hex
    return remember(dependencies, accountKey) {
        MessagingSession(
            store = dependencies.database?.let(::MessageStore),
            signer = session.messagingSigner,
            remote = dependencies.remote,
            scope = scope,
        ).also { it.start() }
    }
}
