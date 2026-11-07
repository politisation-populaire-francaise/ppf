package org.politisationpopulairefrancaise.core.groups

import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** A role in a room (D-131): the creator is its first administrator. */
enum class RoomRole(val id: String) {
    ADMIN("admin"),
    MODERATOR("moderator"),
    ;

    companion object {
        fun fromId(id: String?): RoomRole? = entries.firstOrNull { it.id == id }
    }
}

/** What one accepted moderation event did, for the journal of the room. */
sealed interface ModerationAction {
    data class Named(val target: String, val role: RoomRole) : ModerationAction

    data class RoleRemoved(val target: String) : ModerationAction

    data class Excluded(val target: String, val reason: String) : ModerationAction

    data class Readmitted(val target: String) : ModerationAction

    data class Edited(val name: String?, val about: String?) : ModerationAction

    data class Removed(val eventId: String, val reason: String, val byAuthor: Boolean) : ModerationAction
}

/** One line of the public journal of a room: who did what, when, signed by [event]. */
data class ModerationEntry(val event: SignedEvent, val actor: String, val action: ModerationAction) {
    val createdAt: Long get() = event.createdAt
}

/** A message removed from a room: by whom and why (the text itself is not served any more). */
data class RemovedMessage(val eventId: String, val by: String, val reason: String, val byAuthor: Boolean, val at: Long)

/**
 * The state of a room computed on the device from its description and its moderation events (NIP-29,
 * D-131), with the rules of the server: the creator is the first administrator; an administrator
 * names or removes roles and excludes anyone; a moderator excludes and readmits accounts without
 * role and removes messages; the author removes their own message; the last administrator keeps the
 * role. An event the server would have refused is ignored here too.
 *
 * The server applies the events in the order it receives them; the device, which does not know that
 * order, takes them by date then id. Both agree as long as clocks are honest; the server is the
 * reference (`GET /api/v1/groups/{id}/roles`).
 */
data class RoomModeration(
    val roles: Map<String, RoomRole>,
    /** Excluded keys and the reason of their exclusion. */
    val banned: Map<String, String>,
    val removed: Map<String, RemovedMessage>,
    /** The name and presentation in force after the edits, null when never edited. */
    val name: String?,
    val about: String?,
    /** Oldest first. */
    val journal: List<ModerationEntry>,
) {
    fun roleOf(pubkey: String?): RoomRole? = pubkey?.let(roles::get)

    fun isBanned(pubkey: String?): Boolean = pubkey != null && pubkey in banned

    val admins: List<String> get() = roles.filterValues { it == RoomRole.ADMIN }.keys.toList()
    val moderators: List<String> get() = roles.filterValues { it == RoomRole.MODERATOR }.keys.toList()

    /** Whether [actor] may remove the message of [author]. */
    fun canRemove(actor: String?, author: String): Boolean = actor != null && (roleOf(actor) != null || actor == author)

    /** Whether [actor] may name administrators and moderators, and take roles back. */
    fun canNameRoles(actor: String?): Boolean = roleOf(actor) == RoomRole.ADMIN

    /** Whether [actor] may exclude [target] (an administrator anyone but the last administrator; a moderator a key without role). */
    fun canExclude(actor: String?, target: String): Boolean = when (roleOf(actor)) {
        RoomRole.ADMIN -> !(roleOf(target) == RoomRole.ADMIN && admins.size <= 1)
        RoomRole.MODERATOR -> roleOf(target) == null
        null -> false
    }

    /** Whether [actor] may readmit the excluded [target]. */
    fun canReadmit(actor: String?, target: String): Boolean = isBanned(target) && roleOf(actor) != null

    /** Whether [actor] may give up or lose [target]'s role: never the last administrator. */
    fun canRemoveRole(actor: String?, target: String): Boolean =
        canNameRoles(actor) && roleOf(target) != null && !(roleOf(target) == RoomRole.ADMIN && admins.size <= 1)

    /** Whether [actor] may edit the name and presentation. */
    fun canEdit(actor: String?): Boolean = roleOf(actor) == RoomRole.ADMIN

    /** [group] with the name and presentation in force. */
    fun applyTo(group: Group): Group = group.copy(name = name ?: group.name, about = about ?: group.about)

    companion object {
        const val PUT_USER: Int = 9000
        const val REMOVE_USER: Int = 9001
        const val EDIT_METADATA: Int = 9002
        const val DELETE_EVENT: Int = 9005

        /** The moderation kinds the server accepts, fetched with the messages of a room. */
        val KINDS: List<Int> = listOf(PUT_USER, REMOVE_USER, EDIT_METADATA, DELETE_EVENT)

        const val TAG_GROUP: String = "h"
        const val TAG_KEY: String = "p"
        const val TAG_EVENT: String = "e"
        const val MAX_REASON_LENGTH: Int = 1000

        /**
         * Computes the state of [group] from [events] (any order, any kind: only the authentic
         * moderation events of this room count; the caller verifies signatures). [messageAuthors] gives
         * the author of a message of the room by id, to accept a removal by its author.
         */
        fun compute(group: Group, events: List<SignedEvent>, messageAuthors: (String) -> String? = { null }): RoomModeration {
            val roles = linkedMapOf(group.event.pubkey to RoomRole.ADMIN)
            val banned = linkedMapOf<String, String>()
            val removed = linkedMapOf<String, RemovedMessage>()
            var name: String? = null
            var about: String? = null
            val journal = mutableListOf<ModerationEntry>()
            val ordered = events
                .filter { it.kind in KINDS && it.tagValues(TAG_GROUP) == listOf(group.id) }
                .distinctBy { it.id }
                .sortedWith(compareBy<SignedEvent> { it.createdAt }.thenBy { it.id })
            fun adminCount() = roles.values.count { it == RoomRole.ADMIN }
            for (event in ordered) {
                val actor = event.pubkey
                val role = roles[actor]
                val action: ModerationAction? = when (event.kind) {
                    PUT_USER -> {
                        val tag = event.tags.filter { it.firstOrNull() == TAG_KEY }.singleOrNull()
                        val target = tag?.getOrNull(1)?.takeIf(::isHexKey)
                        val given = tag?.getOrNull(2)
                        val newRole = RoomRole.fromId(given)
                        when {
                            target == null || (given != null && newRole == null) || (tag.size > 3) -> null
                            role == RoomRole.ADMIN -> if (roles[target] == RoomRole.ADMIN && newRole != RoomRole.ADMIN && adminCount() <= 1) {
                                null
                            } else {
                                val wasBanned = banned.remove(target) != null
                                if (newRole != null) {
                                    roles[target] = newRole
                                    ModerationAction.Named(target, newRole)
                                } else if (roles.remove(target) != null) {
                                    ModerationAction.RoleRemoved(target)
                                } else if (wasBanned) {
                                    ModerationAction.Readmitted(target)
                                } else {
                                    null
                                }
                            }
                            role == RoomRole.MODERATOR && newRole == null && roles[target] == null ->
                                if (banned.remove(target) != null) ModerationAction.Readmitted(target) else null
                            else -> null
                        }
                    }
                    REMOVE_USER -> {
                        val target = event.tagValues(TAG_KEY).singleOrNull()?.takeIf(::isHexKey)
                        val reason = event.content.trim()
                        val allowed = target != null && reason.length in 1..MAX_REASON_LENGTH && when (role) {
                            RoomRole.ADMIN -> !(roles[target] == RoomRole.ADMIN && adminCount() <= 1)
                            RoomRole.MODERATOR -> roles[target] == null
                            null -> false
                        }
                        if (allowed && target != null) {
                            roles.remove(target)
                            banned[target] = reason
                            ModerationAction.Excluded(target, reason)
                        } else {
                            null
                        }
                    }
                    EDIT_METADATA -> {
                        val newName = event.firstTagValue(Group.TAG_NAME)?.takeIf { it.isNotBlank() }
                        val newAbout = event.firstTagValue(Group.TAG_ABOUT)
                        val allowedTags = setOf(TAG_GROUP, Group.TAG_NAME, Group.TAG_ABOUT)
                        if (role == RoomRole.ADMIN && (newName != null || newAbout != null) && event.tags.all { it.firstOrNull() in allowedTags }) {
                            if (newName != null) name = newName
                            if (newAbout != null) about = newAbout
                            ModerationAction.Edited(newName, newAbout)
                        } else {
                            null
                        }
                    }
                    DELETE_EVENT -> {
                        val target = event.tagValues(TAG_EVENT).singleOrNull()?.takeIf(::isHexKey)
                        val reason = event.content.trim()
                        val author = target?.let(messageAuthors)
                        val byAuthor = author != null && author == actor
                        if (target != null && reason.length in 1..MAX_REASON_LENGTH && (role != null || byAuthor)) {
                            removed.getOrPut(target) { RemovedMessage(target, actor, reason, byAuthor, event.createdAt) }
                            ModerationAction.Removed(target, reason, byAuthor)
                        } else {
                            null
                        }
                    }
                    else -> null
                }
                if (action != null) journal += ModerationEntry(event, actor, action)
            }
            return RoomModeration(roles.toMap(), banned.toMap(), removed.toMap(), name, about, journal)
        }

        private fun base(author: PublicKey, groupId: String, kind: Int, createdAt: Long, tags: List<List<String>>, content: String = ""): UnsignedEvent {
            require(Group.isValidId(groupId)) { "Invalid room id" }
            return UnsignedEvent.create(author, kind, createdAt, listOf(listOf(TAG_GROUP, groupId)) + tags, content)
        }

        private fun reasonOf(reason: String): String {
            val clean = reason.trim()
            require(clean.length in 1..MAX_REASON_LENGTH) { "A reason has 1 to $MAX_REASON_LENGTH characters" }
            return clean
        }

        /** Names [target] administrator or moderator ([role]), or, without a role, takes back their role and readmits them. */
        fun putUser(author: PublicKey, groupId: String, target: String, role: RoomRole?, createdAt: Long): UnsignedEvent {
            require(isHexKey(target)) { "A key is 64 lowercase hex characters" }
            return base(author, groupId, PUT_USER, createdAt, listOf(listOfNotNull(TAG_KEY, target, role?.id)))
        }

        /** Excludes [target] from the room, with a public [reason]. */
        fun removeUser(author: PublicKey, groupId: String, target: String, reason: String, createdAt: Long): UnsignedEvent {
            require(isHexKey(target)) { "A key is 64 lowercase hex characters" }
            return base(author, groupId, REMOVE_USER, createdAt, listOf(listOf(TAG_KEY, target)), reasonOf(reason))
        }

        /** Changes the name and/or the presentation of the room (never its territory nor its charter). */
        fun editMetadata(author: PublicKey, groupId: String, name: String?, about: String?, createdAt: Long): UnsignedEvent {
            val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
            val cleanAbout = about?.trim()
            require(cleanName != null || cleanAbout != null) { "An edit changes the name or the presentation" }
            require(cleanName == null || cleanName.length <= Group.MAX_NAME_LENGTH) { "A room name has 1 to ${Group.MAX_NAME_LENGTH} characters" }
            require(cleanAbout == null || cleanAbout.length <= Group.MAX_ABOUT_LENGTH) { "A description has at most ${Group.MAX_ABOUT_LENGTH} characters" }
            val tags = listOfNotNull(cleanName?.let { listOf(Group.TAG_NAME, it) }, cleanAbout?.let { listOf(Group.TAG_ABOUT, it) })
            return base(author, groupId, EDIT_METADATA, createdAt, tags)
        }

        /** Removes the message [eventId] from the room, with a public [reason]. */
        fun deleteEvent(author: PublicKey, groupId: String, eventId: String, reason: String, createdAt: Long): UnsignedEvent {
            require(isHexKey(eventId)) { "An event id is 64 lowercase hex characters" }
            return base(author, groupId, DELETE_EVENT, createdAt, listOf(listOf(TAG_EVENT, eventId)), reasonOf(reason))
        }

        private fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
