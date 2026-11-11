package org.politisationpopulairefrancaise.ui.groups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupMessage
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.core.groups.RemovedMessage
import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.groups.RoomRole
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.GroupsResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.SimilarRoom
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.sync.SyncService
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies

/** Where the rooms come from and whether the server could be reached. */
sealed interface GroupsStatus {
    /** Nothing asked yet: the rooms are loaded when the view is first shown. */
    data object Idle : GroupsStatus

    data object Loading : GroupsStatus

    /** No database on this platform: nothing can be shown. */
    data object Unavailable : GroupsStatus

    /** No server configured: only the rooms already on the device. */
    data object Offline : GroupsStatus

    data object Synced : GroupsStatus

    /** The server did not answer; what the device already holds is shown. */
    data object Unreachable : GroupsStatus
}

/** What the creation form holds, kept while the user visits another view. */
data class GroupDraft(
    val name: String = "",
    val territory: String = "",
    val about: String = "",
    /** Unticked until the user ticks it: the charter is never accepted on their behalf. */
    val charterAccepted: Boolean = false,
    /** How the room differs from the rooms of close name the server listed (D-132), public. */
    val difference: String = "",
    /** The user confirmed the creation despite the rooms of close name. */
    val confirmSimilar: Boolean = false,
    /** Tags added to the description: those of a collectif of Agir (M24, D-087), empty for a plain room. */
    val extraTags: List<List<String>> = emptyList(),
) {
    val differenceTooLong: Boolean get() = difference.trim().length > Group.MAX_DIFFERENCE_LENGTH

    /** The id the room would get, empty while the name holds no letter or digit. */
    val proposedId: String get() = Group.idFor(name, territory)

    val nameTooLong: Boolean get() = name.trim().length > Group.MAX_NAME_LENGTH
    val territoryTooLong: Boolean get() = territory.trim().length > Group.MAX_TERRITORY_LENGTH
    val aboutTooLong: Boolean get() = about.trim().length > Group.MAX_ABOUT_LENGTH

    /** Every field the server requires is there and within bounds (the charter apart). */
    val complete: Boolean
        get() = proposedId.isNotEmpty() && territory.isNotBlank() && !nameTooLong && !territoryTooLong && !aboutTooLong
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

    /** Rooms of close name exist on the territory or a parent one (D-132): join one, or confirm with a difference. */
    data class Similar(val rooms: List<SimilarRoom>) : CreationError

    /** Three rooms created in the last 24 hours. */
    data object TooMany : CreationError

    /** A name of an organisation, from an account of less than 7 days. */
    data object TooRecent : CreationError
}

/** Why the server refused a message. */
enum class SendError { CLOSED, NOT_FOUND, BANNED, REFUSED }

/** Why the server refused a moderation action (D-131). */
enum class ModerationError { NOT_ALLOWED, LAST_ADMIN, REASON, OFFLINE, OTHER }

/** One line of a room: a message, or the place of a message removed by a moderator or its author. */
sealed interface RoomItem {
    val id: String
    val createdAt: Long

    data class Message(val message: GroupMessage) : RoomItem {
        override val id: String get() = message.id
        override val createdAt: Long get() = message.createdAt
    }

    /** [author] is known when the device had the message before its removal. */
    data class Removed(val removal: RemovedMessage, val author: String?, override val createdAt: Long) : RoomItem {
        override val id: String get() = removal.eventId
    }
}

/**
 * The rooms of collectives (NIP-29, decisions D-027 and D-028) as the screens need them: the rooms
 * known to the device (descriptions pulled from the server, kept as events for offline reading),
 * the ones the user joined (device only, never published), the room being read and its messages,
 * the creation form.
 *
 * Joining is a local act: the server never receives any membership. Creating a room and posting a
 * message are publications through the single publication port; a message written without network
 * waits on the device (`pending`) and leaves with the next synchronization. Creating a room needs
 * the server, which alone can tell whether the same room already exists.
 *
 * Nothing is asked of the server before the view is first shown ([ensureStarted]): a user who
 * never opens the rooms spends no data on them.
 */
class GroupsLibrary(
    private val store: LocalEventStore?,
    private val remote: RemoteEvents?,
    private val memberships: GroupStore?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::currentEpochSeconds,
    /** How long the search waits after the last key before asking the server. */
    private val searchDelayMillis: Long = 400,
) {
    private val membershipWrites = Mutex()
    private var started = false
    private var searchJob: Job? = null

    var status: GroupsStatus by mutableStateOf(GroupsStatus.Idle)
        private set

    /** Every room known on the device, by name. */
    var groups: List<Group> by mutableStateOf(emptyList())
        private set

    /** What the user typed in the search field; filters [visible] on name, territory and description. */
    var query: String by mutableStateOf("")
        private set

    /** The territory chosen among [territories], or null for all of them. */
    var territoryFilter: String? by mutableStateOf(null)
        private set

    /** Ids of the rooms the user joined. Private: never sent as a list. */
    var joined: Set<String> by mutableStateOf(emptySet())
        private set

    var selected: Group? by mutableStateOf(null)
        private set

    /** The messages of [selected] still shown (not removed), oldest first. */
    var messages: List<GroupMessage> by mutableStateOf(emptyList())
        private set

    /** The messages of [selected] and the places of the removed ones, oldest first. */
    var timeline: List<RoomItem> by mutableStateOf(emptyList())
        private set

    /** Roles, exclusions, removals and journal of [selected], computed on the device (D-131). */
    var moderation: RoomModeration? by mutableStateOf(null)
        private set

    var moderationBusy: Boolean by mutableStateOf(false)
        private set

    var moderationError: ModerationError? by mutableStateOf(null)
        private set

    /** A creation the server holds for the team's review (D-132): "En attente de vérification". */
    var heldCreation: Group? by mutableStateOf(null)
        private set

    /** The user's own messages not yet accepted by the server. */
    var pendingMessageIds: Set<String> by mutableStateOf(emptySet())
        private set

    var messagesStatus: GroupsStatus by mutableStateOf(GroupsStatus.Idle)
        private set

    var sendError: SendError? by mutableStateOf(null)
        private set

    /** What the user is typing in the open room. */
    var messageDraft: String by mutableStateOf("")

    var sending: Boolean by mutableStateOf(false)
        private set

    /** The creation form is open. */
    var creating: Boolean by mutableStateOf(false)
        private set

    var creationDraft: GroupDraft by mutableStateOf(GroupDraft())
        private set

    var creationBusy: Boolean by mutableStateOf(false)
        private set

    var creationError: CreationError? by mutableStateOf(null)
        private set

    /** A server is configured (even if unreachable right now). */
    val hasRemote: Boolean get() = remote != null

    /** The rooms matching the search text. */
    val visible: List<Group>
        get() {
            val text = Group.slugify(query.trim())
            if (text.isEmpty()) return groups
            return groups.filter { room ->
                Group.slugify(room.name).contains(text) ||
                    Group.slugify(room.territory ?: "").contains(text) ||
                    Group.slugify(room.about ?: "").contains(text)
            }
        }

    /** The joined rooms among [visible]: shown first. */
    val mine: List<Group> get() = visible.filter { it.id in joined }

    /** The territories of the joined rooms, normalized: suggestions on them come first. */
    private val joinedTerritories: Set<String>
        get() = groups.filter { it.id in joined }.mapNotNull { room -> room.territory?.let(Group::slugify)?.takeIf { it.isNotEmpty() } }.toSet()

    /**
     * The rooms to discover among [visible]: not joined, on the chosen territory if any, those on
     * the territories of the joined rooms first, then by name.
     */
    val suggestions: List<Group>
        get() {
            val place = territoryFilter?.let(Group::slugify)
            val near = joinedTerritories
            return visible
                .filter { it.id !in joined }
                .filter { room -> place == null || room.territory?.let(Group::slugify) == place }
                .sortedWith(compareBy<Group> { if (isNear(it, near)) 0 else 1 }.thenBy { it.displayName.lowercase() }.thenBy { it.id })
        }

    /** Whether [room] lies on the territory of a joined room. */
    fun isNearJoined(room: Group): Boolean = isNear(room, joinedTerritories)

    private fun isNear(room: Group, near: Set<String>): Boolean = room.territory?.let(Group::slugify)?.let { it in near } == true

    /**
     * The territories of the known rooms, as written by their creators, most rooms first: the
     * suggestions by territory. At most [MAX_TERRITORIES].
     */
    val territories: List<String>
        get() = groups
            .mapNotNull { room -> room.territory?.trim()?.takeIf { it.isNotEmpty() } }
            .groupBy(Group::slugify)
            .filterKeys { it.isNotEmpty() }
            .map { (_, labels) ->
                // The spelling most rooms use ("Lyon" rather than "lyon").
                val label = labels.groupingBy { it }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .first().key
                label to labels.size
            }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
            .take(MAX_TERRITORIES)
            .map { it.first }

    /** Loads the rooms the first time the view is shown; later calls do nothing. */
    fun ensureStarted() {
        if (started) return
        started = true
        if (store == null) {
            status = GroupsStatus.Unavailable
            return
        }
        scope.launch {
            loadMemberships()
            reload(store)
            if (remote == null) status = GroupsStatus.Offline else pullGroups(store, remote, query = null)
        }
    }

    /** Asks the server again, with the current search text and territory (no effect without a server). */
    fun refresh() {
        val store = store ?: return
        val remote = remote ?: return
        scope.launch { pullGroups(store, remote, query = query, territory = territoryFilter) }
    }

    /** Filters the known rooms at once, and asks the server for more once the user stops typing. */
    fun search(text: String) {
        query = text
        val store = store ?: return
        val remote = remote ?: return
        searchJob?.cancel()
        if (text.isBlank()) return
        searchJob = scope.launch {
            delay(searchDelayMillis)
            pullGroups(store, remote, query = text)
        }
    }

    /** Keeps the suggestions of one territory (null: all of them) and asks the server for its rooms. */
    fun selectTerritory(territory: String?) {
        territoryFilter = territory?.trim()?.takeIf { it.isNotEmpty() }
        val chosen = territoryFilter ?: return
        val store = store ?: return
        val remote = remote ?: return
        scope.launch { pullGroups(store, remote, query = null, territory = chosen) }
    }

    fun isMember(groupId: String): Boolean = groupId in joined

    /** Joins a room on this device. Nothing is published. */
    fun join(groupId: String) {
        if (!Group.isValidId(groupId) || groupId in joined) return
        joined = joined + groupId
        scope.launch { persistMembership { it.join(groupId) } }
    }

    /** Leaves a room on this device. Nothing is published; its messages stay readable. */
    fun leave(groupId: String) {
        if (groupId !in joined) return
        joined = joined - groupId
        scope.launch { persistMembership { it.leave(groupId) } }
    }

    /** Forgets every joined room, on the device too: for when the user erases the keys. */
    fun clearMemberships() {
        joined = emptySet()
        scope.launch { persistMembership { it.clear() } }
    }

    /** [clearMemberships], awaited to the end (the keys are being erased). */
    suspend fun clearMembershipsAndWait() {
        joined = emptySet()
        persistMembership { it.clear() }
    }

    /** Opens a room and fetches its new messages. */
    fun open(group: Group) {
        selected = group
        messages = emptyList()
        timeline = emptyList()
        moderation = null
        moderationError = null
        pendingMessageIds = emptySet()
        sendError = null
        messageDraft = ""
        creating = false
        scope.launch { pullMessages(group) }
    }

    fun close() {
        selected = null
        messages = emptyList()
        timeline = emptyList()
        moderation = null
        moderationError = null
        sendError = null
        messageDraft = ""
    }

    /** Fetches the new messages of the open room (no effect without a server). */
    fun refreshMessages() {
        val group = selected ?: return
        scope.launch { pullMessages(group) }
    }

    /** Opens the creation form, its name prefilled with [name] (the search text, typically). */
    fun beginCreation(name: String = query) {
        creationError = null
        heldCreation = null
        if (!creating) creationDraft = GroupDraft(name = name.trim())
        selected = null
        creating = true
    }

    fun cancelCreation() {
        creating = false
        creationError = null
        heldCreation = null
        creationDraft = GroupDraft()
    }

    /** Edits the form; an error about the previous values goes away. */
    fun updateDraft(draft: GroupDraft) {
        creationDraft = draft
        if (creationError != null && creationError !is CreationError.Offline) creationError = null
    }

    /** Types the difference with the rooms of close name, keeping their list on screen. */
    fun updateDifference(text: String) {
        creationDraft = creationDraft.copy(difference = text)
    }

    /** Creates the room despite the rooms of close name, with the difference typed (D-132). */
    suspend fun confirmSimilar(signer: EventSigner): Group? {
        if (creationDraft.difference.isBlank() || creationDraft.differenceTooLong) return null
        creationDraft = creationDraft.copy(confirmSimilar = true)
        return create(signer)
    }

    /** Joins a room listed by `group_similar`, fetching its description first when the device lacks it. */
    suspend fun joinSimilar(groupId: String) {
        val store = store
        val remote = remote
        if (groups.none { it.id == groupId } && store != null && remote != null) pullDescription(store, remote, groupId)
        joinExisting(groupId)
    }

    /** Dismisses the error of the last attempt, so that the user corrects the form. */
    fun dismissCreationError() {
        creationError = null
    }

    /**
     * The known rooms that look like the one being described, for the search before creating:
     * those on the same territory first. A hint only: the server's normalization differs (it keeps
     * every Unicode letter and the example marker) and its `group_exists` answer is what counts.
     */
    fun similarTo(draft: GroupDraft): List<Group> {
        val name = Group.slugify(draft.name)
        if (name.length < MIN_SIMILAR_LENGTH) return emptyList()
        val place = Group.slugify(draft.territory)
        return groups
            .filter { room ->
                val roomName = Group.slugify(room.name)
                roomName.contains(name) || (name.contains(roomName) && roomName.length >= MIN_SIMILAR_LENGTH)
            }
            .sortedWith(
                compareBy<Group> { room -> if (place.isNotEmpty() && room.territory?.let(Group::slugify) == place) 0 else 1 }
                    .thenBy { it.displayName.lowercase() },
            )
            .take(MAX_SIMILAR)
    }

    /** After a duplicate was refused: joins the existing room and opens it. */
    fun joinExisting(groupId: String) {
        join(groupId)
        cancelCreation()
        groups.firstOrNull { it.id == groupId }?.let(::open)
    }

    /**
     * Describes the room of [creationDraft] and publishes it. The id joins the name and the
     * territory; an id already known on the device, or owned by another key on the server, is
     * replaced by a numbered one. On success the creator joins the room on this device and the room
     * opens. On failure [creationError] says why and the form stays open with its values.
     */
    suspend fun create(signer: EventSigner): Group? {
        if (creationBusy) return null
        val draft = creationDraft
        creationError = null
        val store = store
        val remote = remote
        if (store == null || remote == null) {
            creationError = CreationError.Offline
            return null
        }
        if (!draft.complete || !draft.charterAccepted) {
            creationError = CreationError.InvalidName
            return null
        }
        val name = draft.name.trim()
        val territory = draft.territory.trim()
        val about = draft.about.trim().ifEmpty { null }
        val base = draft.proposedId
        // The rooms on the device that look alike are only a hint ([similarTo]): the server, with its
        // own normalization, alone decides what a duplicate is (409 group_exists).
        creationBusy = true
        try {
            var number = 1
            var id = base
            repeat(MAX_ID_ATTEMPTS) {
                // An id known on the device belongs to another room, or would replace one of ours.
                while (groups.any { it.id == id } && number < MAX_LOCAL_NUMBERS) {
                    number++
                    id = Group.numbered(base, number)
                }
                val difference = if (draft.confirmSimilar) draft.difference.trim().takeIf { it.isNotEmpty() } else null
                val unsigned = runCatching { Group.build(signer.publicKey, id, name, about, territory, createdAt = clock(), difference = difference).let { it.copy(tags = it.tags + draft.extraTags) } }.getOrNull()
                    ?: run { creationError = CreationError.InvalidName; return null }
                val event = signer.sign(unsigned)
                when (val result = remote.publish(event)) {
                    is PublicationResult.Accepted -> {
                        val group = Group.from(event) ?: run { creationError = CreationError.InvalidName; return null }
                        if (result.heldForReview) {
                            // Not public yet: the team checks the name first (D-132). Nothing is stored nor joined.
                            heldCreation = group
                            return null
                        }
                        withContext(Dispatchers.Default) { store.store(event) }
                        join(group.id)
                        reload(store)
                        creating = false
                        creationDraft = GroupDraft()
                        open(groups.firstOrNull { it.id == group.id } ?: group)
                        return group
                    }
                    is PublicationResult.Unavailable -> {
                        creationError = CreationError.Offline
                        return null
                    }
                    is PublicationResult.Rejected -> when (result.reason) {
                        RejectionReason.GROUP_TAKEN -> {
                            number++
                            id = Group.numbered(base, number)
                        }
                        RejectionReason.GROUP_EXISTS -> {
                            val existingId = result.existing ?: id
                            // The existing room may not be on the device yet: ask the server for its description.
                            if (groups.none { it.id == existingId }) pullDescription(store, remote, existingId)
                            val existing = groups.firstOrNull { it.id == existingId }
                            creationError = CreationError.Exists(existingId, existing?.displayName ?: name)
                            return null
                        }
                        RejectionReason.GROUP_SIMILAR -> {
                            creationError = CreationError.Similar(result.similar)
                            return null
                        }
                        RejectionReason.TOO_MANY_GROUPS -> {
                            creationError = CreationError.TooMany
                            return null
                        }
                        RejectionReason.ACCOUNT_TOO_RECENT -> {
                            creationError = CreationError.TooRecent
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
        } finally {
            creationBusy = false
        }
    }

    /**
     * Signs and stores [messageDraft] for the open room, joining it on this device; it leaves for
     * the server at once when one answers, otherwise it waits on the device. False when there is
     * nothing to send.
     */
    suspend fun send(signer: EventSigner): Boolean {
        val group = selected ?: return false
        val store = store ?: return false
        if (sending) return false
        val text = messageDraft
        val unsigned = runCatching { GroupMessage.build(signer.publicKey, group.id, text, createdAt = clock()) }.getOrNull() ?: return false
        sendError = null
        sending = true
        try {
            val event = signer.sign(unsigned)
            withContext(Dispatchers.Default) { store.storeSending(event) }
            messageDraft = ""
            join(group.id)
            reloadMessages(store, group)
            val remote = remote ?: return true
            when (val result = store.sending(event) { remote.publish(event) }) {
                is PublicationResult.Accepted -> withContext(Dispatchers.Default) { store.markPublished(event.id) }
                is PublicationResult.Unavailable -> messagesStatus = GroupsStatus.Unreachable // kept pending for the next synchronization
                is PublicationResult.Rejected -> {
                    withContext(Dispatchers.Default) { store.delete(event.id) }
                    // Give the text back so that nothing typed is lost.
                    if (messageDraft.isEmpty()) messageDraft = text
                    sendError = when (result.reason) {
                        RejectionReason.GROUP_CLOSED -> SendError.CLOSED
                        RejectionReason.GROUP_NOT_FOUND -> SendError.NOT_FOUND
                        RejectionReason.BANNED -> SendError.BANNED
                        else -> SendError.REFUSED
                    }
                }
            }
            reloadMessages(store, group)
            return true
        } finally {
            sending = false
        }
    }

    /** Removes [message] from the open room, with a public [reason] (moderators, administrators, its author). */
    suspend fun removeMessage(signer: EventSigner, message: GroupMessage, reason: String): Boolean =
        moderate(signer) { RoomModeration.deleteEvent(signer.publicKey, message.groupId, message.id, reason, moderationTime()) }

    /** Names [target] administrator or moderator ([role]), or takes their role back ([role] null). */
    suspend fun setRole(signer: EventSigner, target: String, role: RoomRole?): Boolean =
        moderate(signer) { group -> RoomModeration.putUser(signer.publicKey, group.id, target, role, moderationTime()) }

    /** Excludes [target] from the open room, with a public [reason]. */
    suspend fun exclude(signer: EventSigner, target: String, reason: String): Boolean =
        moderate(signer) { group -> RoomModeration.removeUser(signer.publicKey, group.id, target, reason, moderationTime()) }

    /** Readmits the excluded [target] in the open room. */
    suspend fun readmit(signer: EventSigner, target: String): Boolean =
        moderate(signer) { group -> RoomModeration.putUser(signer.publicKey, group.id, target, null, moderationTime()) }

    /** The date of this device's last accepted moderation action (seconds). */
    private var lastModerationAt = 0L

    /**
     * The date of a new moderation action: never the same second as the previous one of this device.
     * The journal of a room is read in the order of the dates, then of the identifiers (D-131): an
     * exclusion followed at once by a readmission, within the same second, could otherwise be read in
     * the other order, and the readmission refused (seen at random in a test, 11 November 2026).
     */
    private fun moderationTime(): Long = maxOf(clock(), lastModerationAt + 1)

    fun dismissModerationError() {
        moderationError = null
    }

    /**
     * Signs and publishes a moderation event of the open room. It needs the server at once, which
     * alone decides whether the account still holds the role: nothing waits on the device.
     */
    private suspend fun moderate(signer: EventSigner, build: (Group) -> UnsignedEvent): Boolean {
        val group = selected ?: return false
        val store = store ?: return false
        if (moderationBusy) return false
        moderationError = null
        val remote = remote ?: run { moderationError = ModerationError.OFFLINE; return false }
        val unsigned = runCatching { build(group) }.getOrNull() ?: run { moderationError = ModerationError.REASON; return false }
        moderationBusy = true
        try {
            val event = signer.sign(unsigned)
            return when (val result = remote.publish(event)) {
                is PublicationResult.Accepted -> {
                    lastModerationAt = maxOf(lastModerationAt, event.createdAt)
                    withContext(Dispatchers.Default) { store.store(event) }
                    reloadMessages(store, group)
                    if (event.kind == RoomModeration.EDIT_METADATA) reload(store)
                    true
                }
                is PublicationResult.Unavailable -> {
                    moderationError = ModerationError.OFFLINE
                    false
                }
                is PublicationResult.Rejected -> {
                    moderationError = when (result.reason) {
                        RejectionReason.NOT_ALLOWED -> ModerationError.NOT_ALLOWED
                        RejectionReason.LAST_ADMIN -> ModerationError.LAST_ADMIN
                        RejectionReason.REASON_REQUIRED -> ModerationError.REASON
                        else -> ModerationError.OTHER
                    }
                    false
                }
            }
        } finally {
            moderationBusy = false
        }
    }

    private suspend fun loadMemberships() {
        val memberships = memberships ?: return
        val stored = withContext(Dispatchers.Default) { memberships.all().toSet() }
        // Rooms joined before the list was read (a quick tap) are kept.
        joined = joined + stored
    }

    private suspend fun persistMembership(write: (GroupStore) -> Unit) {
        val memberships = memberships ?: return
        membershipWrites.withLock { withContext(Dispatchers.Default) { write(memberships) } }
    }

    /** Pulls the directory of rooms; every authentic description is kept on the device. */
    private suspend fun pullGroups(store: LocalEventStore, remote: RemoteEvents, query: String?, territory: String? = null) {
        status = GroupsStatus.Loading
        val result = remote.fetchGroups(
            query = query?.trim()?.takeIf { it.isNotEmpty() },
            territory = territory?.trim()?.takeIf { it.isNotEmpty() },
            limit = DIRECTORY_LIMIT,
        )
        status = when (result) {
            is GroupsResult.Unavailable -> GroupsStatus.Unreachable
            is GroupsResult.Groups -> {
                withContext(Dispatchers.Default) {
                    result.groups.forEach { room ->
                        if (room.event.verify() && Group.from(room.event)?.id == room.id) {
                            store.store(room.event)
                            // The edit in force of the name and presentation (D-131), kept to read it offline.
                            room.edit?.takeIf { it.kind == RoomModeration.EDIT_METADATA && it.tagValues("h") == listOf(room.id) && it.verify() }?.let { store.store(it) }
                        }
                    }
                }
                GroupsStatus.Synced
            }
        }
        reload(store)
    }

    /** Fetches the description of one room by its id (`GET /api/v1/events?kind=39000&identifier=…`) and keeps it if authentic. */
    private suspend fun pullDescription(store: LocalEventStore, remote: RemoteEvents, groupId: String) {
        val result = remote.fetch(RemoteFilter(kinds = listOf(Group.KIND), identifiers = listOf(groupId), limit = 1)) as? FetchResult.Events ?: return
        withContext(Dispatchers.Default) {
            result.events.forEach { event ->
                if (Group.from(event)?.id == groupId && event.verify()) store.store(event)
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
        // From the newest message received from the server: the user's own unsent ones do not count.
        val since = messages.filter { it.id !in pendingMessageIds }.maxOfOrNull { it.createdAt }
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
        // The public journal of the room (D-131): roles, exclusions, edits and removals, all of it.
        val journal = remote.fetchByTags(listOf(TagFilter(RoomModeration.TAG_GROUP, group.id)), RemoteFilter(kinds = RoomModeration.KINDS, limit = JOURNAL_LIMIT))
        if (journal is FetchResult.Events) {
            withContext(Dispatchers.Default) {
                journal.events.forEach { event ->
                    if (event.kind in RoomModeration.KINDS && event.tagValues(RoomModeration.TAG_GROUP) == listOf(group.id) && event.verify()) store.store(event)
                }
            }
        }
        reloadMessages(store, group)
    }

    private suspend fun reload(store: LocalEventStore) {
        val fresh = withContext(Dispatchers.Default) {
            // The edits of the name and presentation (D-131) apply to the rooms they belong to.
            val edits = store.recent(kinds = RoomModeration.KINDS, limit = JOURNAL_LIMIT * 4)
                .groupBy { it.tagValues(RoomModeration.TAG_GROUP).singleOrNull() }
            store.recent(kinds = listOf(Group.KIND), limit = DIRECTORY_LIMIT)
                .mapNotNull(Group::from)
                .map { room -> edits[room.id]?.takeIf { list -> list.any { it.kind == RoomModeration.EDIT_METADATA } }?.let { RoomModeration.compute(room, it).applyTo(room) } ?: room }
                .sortedWith(compareBy<Group> { it.displayName.lowercase() }.thenBy { it.id })
        }
        groups = fresh
        selected = selected?.let { current -> fresh.firstOrNull { it.id == current.id } ?: current }
    }

    private class RoomView(val messages: List<GroupMessage>, val timeline: List<RoomItem>, val moderation: RoomModeration, val pending: Set<String>)

    private suspend fun reloadMessages(store: LocalEventStore, group: Group) {
        val view = withContext(Dispatchers.Default) {
            val list = store.byTag(listOf(GroupMessage.KIND), GroupMessage.TAG_GROUP, group.id, limit = MESSAGES_PAGE)
                .mapNotNull(GroupMessage::from)
                .filter { it.groupId == group.id }
                .distinctBy { it.id }
                .sortedWith(compareBy<GroupMessage> { it.createdAt }.thenBy { it.id })
            val authors = list.associate { it.id to it.author }
            val journal: List<SignedEvent> = store.byTag(RoomModeration.KINDS, RoomModeration.TAG_GROUP, group.id, limit = JOURNAL_LIMIT)
            val state = RoomModeration.compute(group, journal, authors::get)
            val shown = list.filter { it.id !in state.removed }
            val known = list.associateBy { it.id }
            val items = shown.map { RoomItem.Message(it) } + state.removed.values.map { removal ->
                val original = known[removal.eventId]
                RoomItem.Removed(removal, original?.author, original?.createdAt ?: removal.at)
            }
            val waiting = store.pending().filter { it.kind == GroupMessage.KIND }.map { it.id }.toSet()
            RoomView(shown, items.sortedWith(compareBy<RoomItem> { it.createdAt }.thenBy { it.id }), state, waiting)
        }
        if (selected?.id != group.id) return
        messages = view.messages
        timeline = view.timeline
        moderation = view.moderation
        pendingMessageIds = view.pending
    }

    companion object {
        /** The server lists at most this many rooms; the device keeps as many. */
        const val DIRECTORY_LIMIT: Int = 500

        /** Messages asked for and shown per room. */
        const val MESSAGES_PAGE: Int = 200

        /** Moderation events of a room asked for and kept (its public journal). */
        const val JOURNAL_LIMIT: Int = 500

        /** How many ids are tried on the server when the derived one belongs to another key. */
        const val MAX_ID_ATTEMPTS: Int = 5

        /** How far the numbering goes to skip the ids already known on the device. */
        private const val MAX_LOCAL_NUMBERS: Int = 100

        /** The territories offered as suggestions. */
        const val MAX_TERRITORIES: Int = 12

        /** The similar rooms shown in the creation form. */
        const val MAX_SIMILAR: Int = 5

        /** A typed name shorter than this (once normalized) is not compared yet. */
        private const val MIN_SIMILAR_LENGTH: Int = 3
    }
}

/** The rooms of collectives for the whole run of the application; nothing is fetched before the view is shown. */
@Composable
fun rememberGroupsLibrary(dependencies: AppDependencies): GroupsLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        GroupsLibrary(
            store = dependencies.contentStore,
            remote = dependencies.remote,
            memberships = dependencies.database?.let(::GroupStore),
            scope = scope,
        )
    }
}
