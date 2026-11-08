package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import org.politisationpopulairefrancaise.core.messaging.SendReport
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.social.ContactVerificationStore
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import kotlin.concurrent.Volatile

/** Where the private messages stand with the server, modelled on LibraryStatus. */
sealed interface MessagingStatus {
    data object Loading : MessagingStatus

    /** No database on this platform yet: nothing can be stored, so nothing can be shown. */
    data object Unavailable : MessagingStatus

    /** No account on the device: reading and writing private messages needs keys. */
    data object NoAccount : MessagingStatus

    /** No server configured: messages are kept on the device and leave as soon as one answers. */
    data object Offline : MessagingStatus

    /** The pending deliveries left and the wraps addressed to the user were fetched; [received] new messages. */
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

    /** Anything else (the keys were erased meanwhile, the database refused): nothing was stored. */
    FAILED,

    /** A participant's account is declared compromised (S-146): nothing is written to it any more. */
    COMPROMISED_CONTACT,

    /** The user's own account is declared compromised: it no longer writes. */
    OWN_COMPROMISED,
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
 * One instance lives for the whole run of the application, like the content library, so that the
 * open conversation and the messages waiting for the server survive a visit to another space. It
 * follows the account of the device ([setAccount]) and forgets everything when the keys are erased
 * ([forgetAll]). The screen asks the server when it opens, on demand, and every
 * [REFRESH_PERIOD_SECONDS] while it stays shown.
 */
class MessagingSession(
    private val store: MessageStore?,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
    /** The contacts checked in person (S-128), on the device only; null without a database. */
    private val verifications: ContactVerificationStore? = null,
    /** The notices of compromise of the contacts (S-146); null shows none. */
    val alerts: ContactAlerts? = null,
) {
    /** The account whose conversations are shown, with its service; replaced when the account changes. */
    private class Account(val signer: MessagingSigner, val service: MessagingService) {
        val key: String = signer.publicKey.hex
    }

    /** Reads and writes on the store happen one at a time, off the main thread. */
    private val operations = Mutex()

    /**
     * Read off the main thread by the work started for an account: work whose account is no longer
     * this one stops without touching the store or the screen.
     */
    @Volatile
    private var account: Account? = null

    /** The erasing in progress after [forgetAll]; the next account waits for it. */
    private var clearing: Job? = null

    /** The account whose exchanges with the server are running, so that one pass at a time runs per account. */
    private var syncing: Account? = null

    /** The user's public key, hexadecimal, when there is an account. */
    var myKey: String? by mutableStateOf(null)
        private set

    /** Whether messages can be written on this device: a database and an account. */
    val canWrite: Boolean get() = store != null && myKey != null

    /** True when the user's own account is declared compromised (S-146): it no longer writes. */
    var ownCompromised: Boolean by mutableStateOf(false)
        private set

    fun markOwnCompromised(compromised: Boolean) {
        ownCompromised = compromised
    }

    /** The other participants of the open conversation whose account is declared compromised. */
    val compromisedInOpen: List<String>
        get() = open?.others(myKey).orEmpty().filter { alerts?.notices?.containsKey(it) == true }

    /**
     * Whether the user can write in the open conversation: an account that is not compromised, and
     * no participant declared compromised (whoever holds that key now would read the message).
     */
    val canWriteOpen: Boolean get() = canWrite && !ownCompromised && compromisedInOpen.isEmpty()

    /** Whether a server is configured; without one, every message waits on the device. */
    val hasServer: Boolean get() = remote != null

    var status: MessagingStatus by mutableStateOf(if (store == null) MessagingStatus.Unavailable else MessagingStatus.NoAccount)
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

    /**
     * The followed account whose flow is open in the "Flux" view (public key, hexadecimal). Kept
     * here so that it survives a visit to another space like the open conversation; only one of
     * the two is open at a time, which is what a phone shows full screen.
     */
    var openFlow: String? by mutableStateOf(null)
        private set

    val unreadTotal: Long get() = conversations.sumOf { it.conversation.unreadCount }

    /**
     * Follows the account of the device ([signer] is null without one). Another key shows that
     * account's conversations and asks the server at once; the same key changes nothing.
     */
    fun setAccount(signer: MessagingSigner?) {
        val key = signer?.publicKey?.hex
        if (key == myKey) return
        val store = store
        val next = if (store != null && signer != null) Account(signer, MessagingService(signer, store, remote ?: NoServer)) else null
        account = next
        myKey = next?.key
        clearView()
        status = when {
            store == null -> MessagingStatus.Unavailable
            next == null -> MessagingStatus.NoAccount
            else -> MessagingStatus.Loading
        }
        if (next != null) scope.launch { prepare(next) }
    }

    /**
     * Forgets every conversation on this device, because the keys were erased: the screen at once,
     * the store as soon as the work in progress ends (messages, read marks, pending deliveries and
     * the pull cursor). Nothing of that account stays readable here (protection by default); the
     * server keeps its gift wraps for the retention period, so restoring the account brings back
     * what it still holds.
     */
    fun forgetAll() {
        account = null
        myKey = null
        syncing = null
        refreshing = false
        clearView()
        status = if (store == null) MessagingStatus.Unavailable else MessagingStatus.NoAccount
        // The contacts checked in person belonged to that identity too.
        verifications?.clear()
        alerts?.clear()
        ownCompromised = false
        verificationsVersion++
        val store = store ?: return
        val previous = clearing
        clearing = scope.launch {
            previous?.join()
            safely { withContext(Dispatchers.Default) { operations.withLock { store.clear() } } }
        }
    }

    /** Sends the pending deliveries, then fetches the wraps addressed to the user. Without a server, reads the device again. */
    fun refresh() {
        val current = account ?: return
        scope.launch { if (remote == null) reload(current) else sync(current) }
    }

    /** Opens a conversation of the list. */
    fun open(conversation: Conversation) {
        openWith(conversation.participants, conversation.subject)
    }

    /**
     * Opens the conversation gathering the user and [recipientsHex], creating it on screen when it
     * does not exist yet; [subject] is given to its first message. Anything but valid keys is ignored.
     */
    fun openWith(recipientsHex: Collection<String>, subject: String? = null) {
        val current = account ?: return
        val keys = (recipientsHex + current.key).filter { FollowStore.isValidKey(it) }.distinct().sorted()
        if (keys.size < 2) return
        sendError = null
        openFlow = null
        val id = Conversation.idOf(keys)
        val stored = conversations.firstOrNull { it.id == id }?.conversation?.subject
        open = OpenConversation(id, keys, stored ?: subject?.trim()?.takeIf { it.isNotEmpty() })
        messages = emptyList()
        scope.launch { reload(current) }
    }

    fun close() {
        open = null
        messages = emptyList()
        sendError = null
    }

    /** Shows the flow of a followed account (closes the open conversation). */
    fun showFlow(pubkeyHex: String) {
        close()
        openFlow = pubkeyHex
    }

    fun closeFlow() {
        openFlow = null
    }

    /** Whether [pubkeyHex] was checked in person on this device (S-128). */
    fun isVerified(pubkeyHex: String): Boolean = verifications?.isVerified(pubkeyHex) == true

    /** Increases when a verification changes, so the screens read [isVerified] again. */
    var verificationsVersion: Int by mutableStateOf(0)
        private set

    /** Records that the user compared the safety number with [pubkeyHex] in person, or undoes it. */
    fun setVerified(pubkeyHex: String, verified: Boolean) {
        val store = verifications ?: return
        if (verified) store.markVerified(pubkeyHex) else store.unmarkVerified(pubkeyHex)
        verificationsVersion++
    }

    /**
     * Deletes the open conversation from this device: its messages and those still waiting to be
     * sent. The server keeps the encrypted envelopes it holds until their retention ends, unreadable
     * by it; the device does not fetch them again (its cursor has passed them).
     */
    fun deleteOpen() {
        val current = account ?: return
        val target = open ?: return
        val store = store ?: return
        open = null
        messages = emptyList()
        scope.launch {
            safely { withContext(Dispatchers.Default) { operations.withLock { if (account === current) store.deleteConversation(target.id) } } }
            reload(current)
        }
    }

    /**
     * Called by the conversation screen while it shows the open conversation: its messages are
     * marked read, on the device only (no read receipt ever leaves it).
     */
    fun markOpenRead() {
        val current = account ?: return
        val target = open ?: return
        val store = store ?: return
        val unread = conversations.firstOrNull { it.id == target.id }?.conversation?.unreadCount ?: 0L
        if (unread == 0L) return
        scope.launch {
            safely { withContext(Dispatchers.Default) { operations.withLock { if (account === current) store.markRead(target.id) } } }
            reload(current)
        }
    }

    /**
     * Checks that a message can be encrypted for [pubkeyHex]: a valid secp256k1 point, not the
     * user's own key. Done before a conversation starts, so that a mistyped key is caught at once
     * rather than at the first send.
     */
    fun canEncryptFor(pubkeyHex: String): Boolean {
        val signer = account?.signer ?: return false
        if (!FollowStore.isValidKey(pubkeyHex) || pubkeyHex == myKey) return false
        return try {
            signer.encrypt(PublicKey.fromHex(pubkeyHex), "ok")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sends [text] in the open conversation: stored on the device at once, one gift wrap per
     * participant, deliveries the server cannot take now wait for the next refresh. Returns false
     * when nothing was sent; [sendError] then says why.
     */
    suspend fun send(text: String): Boolean {
        val current = account ?: return false
        val target = open ?: return false
        val content = text.trim()
        if (content.isEmpty()) return false
        if (ownCompromised) {
            sendError = SendError.OWN_COMPROMISED
            return false
        }
        if (compromisedInOpen.isNotEmpty()) {
            sendError = SendError.COMPROMISED_CONTACT
            return false
        }
        val recipients = target.others(current.key).map { PublicKey.fromHex(it) }
        // The subject travels with the first message of a conversation; the store keeps the newest one.
        val subject = target.subject?.takeIf { messages.isEmpty() }
        sendError = null
        val sent = try {
            withContext(Dispatchers.Default) {
                operations.withLock { if (account === current) current.service.send(content, recipients, subject) else null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            sendError = if (content.encodeToByteArray().size > ChatMessage.MAX_TEXT_BYTES) SendError.TOO_LONG else SendError.INVALID_RECIPIENT
            return false
        } catch (e: Exception) {
            sendError = SendError.FAILED
            return false
        }
        reload(current)
        return sent != null
    }

    /**
     * Sends [text] to [recipientHex] without opening the conversation (a request to the editorial
     * team from "Voir", for example): stored on the device at once like any message, so that the
     * answer arrives in the same conversation and a delivery the server cannot take now waits for
     * the next refresh. Null when nothing was sent (no account, invalid key, text too long).
     */
    suspend fun sendTo(recipientHex: String, text: String, subject: String? = null): SendReport? {
        val current = account ?: return null
        if (store == null || text.isBlank() || ownCompromised || alerts?.notices?.containsKey(recipientHex) == true) return null
        val recipient = runCatching { PublicKey.fromHex(recipientHex) }.getOrNull() ?: return null
        val sent = try {
            withContext(Dispatchers.Default) {
                operations.withLock { if (account === current) current.service.send(text, listOf(recipient), subject) else null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        reload(current)
        return sent
    }

    private suspend fun prepare(current: Account) {
        val store = store ?: return
        clearing?.join()
        // The store holds the conversations of one identity. Any other's (left by a former account
        // whose keys disappeared without the application) must not become readable by this one;
        // its pull cursor goes with them, and this account's history comes back from the server.
        safely {
            withContext(Dispatchers.Default) {
                operations.withLock {
                    if (account === current && store.conversations(Int.MAX_VALUE).any { current.key !in it.participants }) store.clear()
                }
            }
        }
        reload(current)
        if (account !== current) return
        if (remote == null) status = MessagingStatus.Offline else sync(current)
    }

    private suspend fun sync(current: Account) {
        if (remote == null || syncing === current) return
        syncing = current
        refreshing = true
        try {
            val reports = safely {
                withContext(Dispatchers.Default) {
                    operations.withLock {
                        if (account !== current) null else current.service.flushOutgoing() to current.service.pull()
                    }
                }
            }
            reload(current)
            checkContacts(current)
            if (account === current) {
                status = if (reports != null && reports.first.reachable && reports.second.reachable) {
                    MessagingStatus.Synced(reports.second.received.stored)
                } else {
                    MessagingStatus.Unreachable
                }
            }
        } finally {
            if (syncing === current) {
                syncing = null
                refreshing = false
            }
        }
    }

    /**
     * Asks the notices of compromise of the participants of the conversations (S-146); a contact
     * newly found compromised loses its in-person check (it no longer proves anything).
     */
    private suspend fun checkContacts(current: Account) {
        val alerts = alerts ?: return
        val contacts = conversations.flatMap { it.conversation.participants }.filter { it != current.key }.distinct()
        if (contacts.isEmpty()) return
        val added = safely { alerts.refresh(contacts) }.orEmpty()
        if (account !== current) return
        val unchecked = added.filter { isVerified(it) }
        unchecked.forEach { setVerified(it, false) }
    }

    private class Snapshot(val list: List<ConversationSummary>, val thread: List<ChatMessage>, val queued: Set<String>)

    /** Reads the list and the open thread again from the store. */
    private suspend fun reload(current: Account) {
        val store = store ?: return
        val target = open
        val snapshot = safely {
            withContext(Dispatchers.Default) {
                operations.withLock {
                    if (account !== current) return@withLock null
                    val queued = store.outgoing().map { it.messageId }.toSet()
                    val list = store.conversations(LIST_LIMIT).filter { current.key in it.participants }.map { conversation ->
                        val last = store.messages(conversation.id, limit = 1).lastOrNull()
                        ConversationSummary(conversation, last, pending = last != null && last.id in queued)
                    }
                    val thread = target?.let { store.messages(it.id, THREAD_LIMIT) } ?: emptyList()
                    Snapshot(list, thread, queued)
                }
            }
        } ?: return
        if (account !== current) return
        conversations = snapshot.list
        pending = snapshot.queued
        alerts?.let { known -> safely { known.load(snapshot.list.flatMap { it.conversation.participants }.filter { it != current.key }.distinct()) } }
        val shown = open
        if (shown != null && shown.id == target?.id) {
            messages = snapshot.thread
            // A subject that arrived meanwhile wins over the one typed when the conversation was opened.
            val stored = snapshot.list.firstOrNull { it.id == shown.id }?.conversation?.subject
            if (stored != null && stored != shown.subject) open = shown.copy(subject = stored)
        }
    }

    private fun clearView() {
        conversations = emptyList()
        pending = emptySet()
        close()
    }

    /** Runs [block]; a failure (keys erased meanwhile, an unexpected answer) gives null rather than a crash. */
    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Stands for the server when none is configured: everything waits on the device. */
    private object NoServer : RemoteEvents {
        override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Unavailable()
        override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
    }

    companion object {
        /** While the messages screen is shown, the server is asked again this often. */
        const val REFRESH_PERIOD_SECONDS: Int = 20

        /** Conversations shown in the list, most recent first. */
        const val LIST_LIMIT: Int = 200

        /** Newest messages shown in a thread. */
        const val THREAD_LIMIT: Int = 500
    }
}

/**
 * The private conversations of the application, kept for the whole run (remembered by the shell,
 * like the content library). The shell tells it the account of the device and calls
 * [MessagingSession.forgetAll] when the keys are erased.
 */
@Composable
fun rememberMessagingSession(dependencies: AppDependencies): MessagingSession {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        MessagingSession(
            store = dependencies.database?.let(::MessageStore),
            remote = dependencies.remote,
            scope = scope,
            verifications = dependencies.database?.let(::ContactVerificationStore),
            alerts = ContactAlerts(dependencies.contentStore, dependencies.remote),
        )
    }
}
