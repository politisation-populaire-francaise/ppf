package org.politisationpopulairefrancaise.ui.groups

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
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupMessage
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.local.LocalStoreOutcome
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.GroupsResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.sync.SyncService
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies

/** Where the rooms come from and whether the server could be reached. */
sealed interface GroupsStatus {
    data object Loading : GroupsStatus

    /** No database on this platform: nothing can be shown. */
    data object Unavailable : GroupsStatus

    /** No server configured: only the rooms already on the device. */
    data object Offline : GroupsStatus

    data object Synced : GroupsStatus

    /** The server did not answer; what the device already holds is shown. */
    data object Unreachable : GroupsStatus
}

/** Why a room could not be created. */
sealed interface CreationError {
    /** A room of the same name exists on the same territory: the one to join. */
    data class Exists(val existingId: String, val existingName: String) : CreationError

    /** Every id tried is owned by another key. */
    data object Taken : CreationError

    /** The name holds no letter or digit, or a field is out of bounds. */
    data object InvalidName : CreationError

    /** No server, or no answer: creation needs the server to check duplicates. */
    data object Offline : CreationError

    data class Refused(val reason: RejectionReason) : CreationError
}

/** Why the server refused a message. */
enum class SendError { CLOSED, NOT_FOUND, REFUSED }

/**
 * The rooms of collectives (NIP-29) as the screens need them: the rooms known to the device
 * (descriptions pulled from the server, kept as events for offline reading), the ones the user
 * joined (device only, never published), the room being read and its messages.
 *
 * Joining is a local act: the server never receives any membership. Creating a room and posting a
 * message are publications through the single publication port; a message written without network
 * waits on the device (`pending`) and leaves with the next synchronization.
 */
class GroupsLibrary(
    private val store: LocalEventStore?,
    private val remote: RemoteEvents?,
    private val memberships: GroupStore?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private val membershipWrites = Mutex()

    var status: GroupsStatus by mutableStateOf(GroupsStatus.Loading)
        private set

    /** Every room known on the device, by name. */
    var groups: List<Group> by mutableStateOf(emptyList())
        private set

    /** What the user typed in the search field; filters [visible] on name, territory and description. */
    var query: String by mutableStateOf("")
        private set

    /** Ids of the rooms the user joined. Private: never sent as a list. */
    var joined: Set<String> by mutableStateOf(emptySet())
        private set

    var selected: Group? by mutableStateOf(null)
        private set

    /** The messages of [selected], oldest first. */
    var messages: List<GroupMessage> by mutableStateOf(emptyList())
        private set

    /** The user's own messages not yet accepted by the server. */
    var pendingMessageIds: Set<String> by mutableStateOf(emptySet())
        private set

    var messagesStatus: GroupsStatus by mutableStateOf(GroupsStatus.Loading)
        private set

    var sendError: SendError? by mutableStateOf(null)
        private set

    /** The creation form is open. */
    var creating: Boolean by mutableStateOf(false)
        private set

    var creationError: CreationError? by mutableStateOf(null)
        private set

    val visible: List<Group>
        get() {
            val text = query.trim().lowercase()
            if (text.isEmpty()) return groups
            return groups.filter { room ->
                room.displayName.lowercase().contains(text) ||
                    room.territory?.lowercase()?.contains(text) == true ||
                    room.about?.lowercase()?.contains(text) == true
            }
        }

    /** The joined rooms among [visible]. */
    val mine: List<Group> get() = visible.filter { it.id in joined }

    /** The other rooms among [visible]. */
    val others: List<Group> get() = visible.filter { it.id !in joined }

    /** A server is configured (even if unreachable right now). */
    val hasRemote: Boolean get() = remote != null

    fun start() {
        if (store == null) {
            status = GroupsStatus.Unavailable
            return
        }
        scope.launch {
            loadMemberships()
            reload(store)
            if (remote == null) status = GroupsStatus.Offline else pullGroups(store, remote)
        }
    }

    /** Asks the server again, with the current search text (no effect without a server). */
    fun refresh() {
        val store = store ?: return
        val remote = remote ?: return
        scope.launch { pullGroups(store, remote) }
    }

    fun search(text: String) {
        query = text
    }

    fun isMember(groupId: String): Boolean = groupId in joined

    /** Joins a room on this device. Nothing is published. */
    fun join(groupId: String) {
        if (!Group.isValidId(groupId) || groupId in joined) return
        joined = joined + groupId
        scope.launch { persistMembership { it.join(groupId) } }
    }

    fun leave(groupId: String) {
        if (groupId !in joined) return
        joined = joined - groupId
        scope.launch { persistMembership { it.leave(groupId) } }
    }

    fun toggleMembership(groupId: String) {
        if (groupId in joined) leave(groupId) else join(groupId)
    }

    /** Forgets every joined room, on the device too: for when the user erases the keys. */
    fun clearMemberships() {
        joined = emptySet()
        scope.launch { persistMembership { it.clear() } }
    }

    /** Opens a room and fetches its new messages. */
    fun open(group: Group) {
        selected = group
        messages = emptyList()
        pendingMessageIds = emptySet()
        sendError = null
        creating = false
        scope.launch { pullMessages(group) }
    }

    fun close() {
        selected = null
        messages = emptyList()
        sendError = null
    }

    /** Fetches the new messages of the open room (no effect without a server). */
    fun refreshMessages() {
        val group = selected ?: return
        scope.launch { pullMessages(group) }
    }

    fun beginCreation() {
        creationError = null
        creating = true
    }

    fun cancelCreation() {
        creating = false
        creationError = null
    }

    /** After a duplicate was refused: joins the existing room and opens it. */
    fun joinExisting(groupId: String) {
        join(groupId)
        cancelCreation()
        groups.firstOrNull { it.id == groupId }?.let(::open)
    }

    /**
     * Describes a new room and publishes it. The id is derived from the name; when another key
     * owns it, a numbered id is tried a few times. On success the creator joins the room on this
     * device and the room opens. On failure [creationError] says why and the form stays open.
     */
    suspend fun create(signer: EventSigner, name: String, territory: String, about: String?): Group? {
        creationError = null
        val store = store ?: run { creationError = CreationError.Offline; return null }
        val remote = remote ?: run { creationError = CreationError.Offline; return null }
        val slug = Group.slugify(name)
        if (slug.isEmpty()) {
            creationError = CreationError.InvalidName
            return null
        }
        var id = slug
        for (attempt in 1..MAX_ID_ATTEMPTS) {
            val unsigned = runCatching { Group.build(signer.publicKey, id, name, about, territory, createdAt = clock()) }.getOrNull()
                ?: run { creationError = CreationError.InvalidName; return null }
            val event = signer.sign(unsigned)
            when (val result = remote.publish(event)) {
                is PublicationResult.Accepted -> {
                    val group = Group.from(event) ?: run { creationError = CreationError.InvalidName; return null }
                    withContext(Dispatchers.Default) { store.store(event) }
                    join(group.id)
                    reload(store)
                    creating = false
                    open(groups.firstOrNull { it.id == group.id } ?: group)
                    return group
                }
                is PublicationResult.Unavailable -> {
                    creationError = CreationError.Offline
                    return null
                }
                is PublicationResult.Rejected -> when (result.reason) {
                    RejectionReason.GROUP_TAKEN -> {
                        val suffix = "-${attempt + 1}"
                        id = slug.take(Group.MAX_ID_LENGTH - suffix.length).trimEnd('-') + suffix
                    }
                    RejectionReason.GROUP_EXISTS -> {
                        val existingId = result.existing ?: id
                        // The existing room may not be on the device yet: ask the server for it by name.
                        if (groups.none { it.id == existingId }) pullGroups(store, remote, query = name)
                        val existing = groups.firstOrNull { it.id == existingId }
                        creationError = CreationError.Exists(existingId, existing?.displayName ?: existingId)
                        return null
                    }
                    else -> {
                        creationError = CreationError.Refused(result.reason)
                        return null
                    }
                }
            }
        }
        creationError = CreationError.Taken
        return null
    }

    /**
     * Signs and stores a message for the open room; it leaves for the server at once when one
     * answers, otherwise it waits on the device. False when there is nothing to send.
     */
    suspend fun send(signer: EventSigner, text: String): Boolean {
        val group = selected ?: return false
        val store = store ?: return false
        sendError = null
        val unsigned = runCatching { GroupMessage.build(signer.publicKey, group.id, text, createdAt = clock()) }.getOrNull() ?: return false
        val event = signer.sign(unsigned)
        withContext(Dispatchers.Default) { store.store(event, pending = true) }
        reloadMessages(store, group)
        val remote = remote ?: return true
        when (val result = remote.publish(event)) {
            is PublicationResult.Accepted -> withContext(Dispatchers.Default) { store.markPublished(event.id) }
            is PublicationResult.Unavailable -> Unit // kept pending for the next synchronization
            is PublicationResult.Rejected -> {
                withContext(Dispatchers.Default) { store.delete(event.id) }
                sendError = when (result.reason) {
                    RejectionReason.GROUP_CLOSED -> SendError.CLOSED
                    RejectionReason.GROUP_NOT_FOUND -> SendError.NOT_FOUND
                    else -> SendError.REFUSED
                }
            }
        }
        reloadMessages(store, group)
        return true
    }

    private suspend fun loadMemberships() {
        val memberships = memberships ?: return
        joined = withContext(Dispatchers.Default) { memberships.all().toSet() }
    }

    private suspend fun persistMembership(write: (GroupStore) -> Unit) {
        val memberships = memberships ?: return
        membershipWrites.withLock { withContext(Dispatchers.Default) { write(memberships) } }
    }

    /** Pulls the directory of rooms; every authentic description is kept on the device. */
    private suspend fun pullGroups(store: LocalEventStore, remote: RemoteEvents, query: String? = this.query) {
        status = GroupsStatus.Loading
        val result = remote.fetchGroups(query = query?.trim()?.takeIf { it.isNotEmpty() }, limit = DIRECTORY_LIMIT)
        status = when (result) {
            is GroupsResult.Unavailable -> GroupsStatus.Unreachable
            is GroupsResult.Groups -> {
                withContext(Dispatchers.Default) {
                    result.groups.forEach { room ->
                        if (room.event.verify() && Group.from(room.event)?.id == room.id) store.store(room.event)
                    }
                }
                GroupsStatus.Synced
            }
        }
        reload(store)
    }

    /** Sends what waits on the device, then fetches the messages of [group] newer than the device's copy. */
    private suspend fun pullMessages(group: Group) {
        val store = store ?: return
        val remote = remote
        if (remote == null) {
            messagesStatus = GroupsStatus.Offline
            reloadMessages(store, group)
            return
        }
        messagesStatus = GroupsStatus.Loading
        reloadMessages(store, group)
        val up = SyncService(store, remote).publishPending()
        if (!up.reachable) {
            messagesStatus = GroupsStatus.Unreachable
            reloadMessages(store, group)
            return
        }
        val since = messages.lastOrNull()?.createdAt
        val filter = RemoteFilter(kinds = listOf(GroupMessage.KIND), since = since, limit = MESSAGES_PAGE)
        messagesStatus = when (val result = remote.fetchByTags(listOf(TagFilter(GroupMessage.TAG_GROUP, group.id)), filter)) {
            is FetchResult.Unavailable -> GroupsStatus.Unreachable
            is FetchResult.Events -> {
                withContext(Dispatchers.Default) {
                    result.events.forEach { event ->
                        if (GroupMessage.from(event)?.groupId == group.id && event.verify()) store.store(event)
                    }
                }
                GroupsStatus.Synced
            }
        }
        reloadMessages(store, group)
    }

    private suspend fun reload(store: LocalEventStore) {
        val fresh = withContext(Dispatchers.Default) {
            store.recent(kinds = listOf(Group.KIND), limit = DIRECTORY_LIMIT)
                .mapNotNull(Group::from)
                .sortedWith(compareBy<Group> { it.displayName.lowercase() }.thenBy { it.id })
        }
        groups = fresh
        selected = selected?.let { current -> fresh.firstOrNull { it.id == current.id } ?: current }
    }

    private suspend fun reloadMessages(store: LocalEventStore, group: Group) {
        val (fresh, pending) = withContext(Dispatchers.Default) {
            val list = store.byTag(listOf(GroupMessage.KIND), GroupMessage.TAG_GROUP, group.id, limit = MESSAGES_PAGE)
                .mapNotNull(GroupMessage::from)
                .filter { it.groupId == group.id }
                .sortedWith(compareBy<GroupMessage> { it.createdAt }.thenBy { it.id })
            val waiting = store.pending().filter { it.kind == GroupMessage.KIND }.map { it.id }.toSet()
            list to waiting
        }
        if (selected?.id != group.id) return
        messages = fresh
        pendingMessageIds = pending
    }

    companion object {
        /** The server lists at most this many rooms; the device keeps as many. */
        const val DIRECTORY_LIMIT: Int = 500

        /** Messages asked for and shown per room. */
        const val MESSAGES_PAGE: Int = 200

        /** How many numbered ids are tried when the derived one belongs to another key. */
        const val MAX_ID_ATTEMPTS: Int = 5

        /** The outcome of storing a fetched event that matters here: it was new. */
        @Suppress("unused")
        private val STORED = LocalStoreOutcome.STORED
    }
}

@Composable
fun rememberGroupsLibrary(dependencies: AppDependencies): GroupsLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        GroupsLibrary(
            store = dependencies.contentStore,
            remote = dependencies.remote,
            memberships = dependencies.database?.let(::GroupStore),
            scope = scope,
        ).also { it.start() }
    }
}
