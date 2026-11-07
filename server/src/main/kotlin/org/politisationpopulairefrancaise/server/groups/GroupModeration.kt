package org.politisationpopulairefrancaise.server.groups

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.TagsJson
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy.Decision
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** A role in a room (D-131): the creator is its first administrator. */
enum class GroupRole(val id: String) {
    ADMIN(GroupKinds.ROLE_ADMIN),
    MODERATOR(GroupKinds.ROLE_MODERATOR),
    ;

    companion object {
        fun fromId(id: String?): GroupRole? = entries.firstOrNull { it.id == id }
    }
}

/** One role in force, as `GET /api/v1/groups/{id}/roles` serves it. */
@Serializable
data class RoleEntry(
    val pubkey: String,
    val role: String,
    /** The key that named this role; the creator's own key for the first administrator. */
    @SerialName("granted_by") val grantedBy: String,
    /** Seconds since 1970, when the server received the nomination. */
    @SerialName("granted_at") val grantedAt: Long,
)

/** The roles in force in a room and the number of excluded accounts; never a list of members. */
@Serializable
data class GroupRoles(val group: String, val roles: List<RoleEntry>, val banned: Int)

/**
 * The roles and the moderation of the rooms (D-131, contract docs/integration/salons-roles.md,
 * section 2), computed from the moderation events in the order the server receives them:
 *
 * - put-user (9000): an administrator names an administrator or a moderator (role as third value of
 *   the `p` tag) or, without a role, takes a role back; in every case the key is readmitted if it was
 *   excluded. A moderator may send one without a role for a key that holds none: a readmission;
 * - remove-user (9001): an administrator excludes any key, a moderator a key without role; the key
 *   loses its role and its messages (kind 9) are refused in the room (`403 banned`);
 * - edit-metadata (9002): an administrator changes the name and/or the presentation;
 * - delete-event (9005): an administrator, a moderator or the author removes a message of the room,
 *   which is no longer served; the 9005 stays public with its reason;
 * - the last administrator can neither take back their own role nor exclude themselves (`409 last_admin`);
 * - an event of a key without the role it needs, at the moment it is received, is refused (`403 not_allowed`).
 *
 * Each event is checked, stored and applied in one transaction, under a lock of its room: two
 * moderation events of the same room are taken one after the other, in the order of reception.
 */
class GroupModeration(private val events: EventRepository, private val clock: () -> Instant = Instant::now) {

    /** The roles in force in [groupId], by date of nomination, and how many keys are excluded. */
    fun roles(groupId: String): GroupRoles = events.dataSource.connection.use { connection ->
        val roles = connection.prepareStatement(
            "SELECT pubkey, role, granted_by, granted_at FROM group_role WHERE group_id = ? ORDER BY granted_at, role, pubkey",
        ).use { statement ->
            statement.setString(1, groupId)
            val rows = statement.executeQuery()
            buildList {
                while (rows.next()) {
                    add(RoleEntry(rows.getString(1), rows.getString(2), rows.getString(3), rows.getObject(4, OffsetDateTime::class.java).toEpochSecond()))
                }
            }
        }
        val banned = connection.prepareStatement("SELECT count(*) FROM group_ban WHERE group_id = ?").use { statement ->
            statement.setString(1, groupId)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }
        GroupRoles(groupId, roles, banned)
    }

    fun roleOf(groupId: String, pubkey: String): GroupRole? = events.dataSource.connection.use { roleOf(it, groupId, pubkey) }

    fun isBanned(groupId: String, pubkey: String): Boolean = events.dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT 1 FROM group_ban WHERE group_id = ? AND pubkey = ?").use { statement ->
            statement.setString(1, groupId)
            statement.setString(2, pubkey)
            statement.executeQuery().next()
        }
    }

    /** The edits in force of [groupIds] (name and presentation merged over the successive edits). */
    fun edits(groupIds: Collection<String>): Map<String, GroupEdit> {
        if (groupIds.isEmpty()) return emptyMap()
        return events.dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT g.group_id, g.name, g.about, e.id, e.pubkey, e.created_at, e.kind, e.tags::text, e.content, e.sig " +
                    "FROM group_edit g JOIN events e ON e.id = g.event_id WHERE g.group_id = ANY(?)",
            ).use { statement ->
                statement.setArray(1, connection.createArrayOf("text", groupIds.distinct().toTypedArray()))
                val rows = statement.executeQuery()
                buildMap {
                    while (rows.next()) {
                        val event = SignedEvent(
                            id = rows.getString(4),
                            pubkey = rows.getString(5),
                            createdAt = rows.getLong(6),
                            kind = rows.getInt(7),
                            tags = TagsJson.decode(rows.getString(8)),
                            content = rows.getString(9),
                            sig = rows.getString(10),
                        )
                        put(rows.getString(1), GroupEdit(rows.getString(2), rows.getString(3), event))
                    }
                }
            }
        }
    }

    /**
     * Checks a moderation event of [group] against the state of the room, then stores and applies it,
     * all in one transaction under the lock of the room. [renameCheck] is asked about the new name of
     * an edit (a duplicate on the territory, a name reserved to an organisation...); it reads the
     * database through the connection it receives.
     */
    fun admitAndStore(event: SignedEvent, group: Group, renameCheck: (Connection, String) -> Decision.Refused?): GroupDirectory.Admission =
        events.transaction { connection ->
            lock(connection, group.id)
            val refusal = check(connection, event, group, renameCheck)
            if (refusal != null) {
                GroupDirectory.Admission.Refused(refusal)
            } else {
                val outcome = events.storeIn(connection, event)
                if (outcome == StoreOutcome.STORED) apply(connection, event, group)
                GroupDirectory.Admission.Stored(outcome)
            }
        }

    private fun check(connection: Connection, event: SignedEvent, group: Group, renameCheck: (Connection, String) -> Decision.Refused?): Decision.Refused? {
        val sender = roleOf(connection, group.id, event.pubkey)
        return when (event.kind) {
            GroupKinds.PUT_USER -> {
                val tag = event.tags.first { it.firstOrNull() == "p" }
                val target = tag[1]
                val role = GroupRole.fromId(tag.getOrNull(2))
                val targetRole = roleOf(connection, group.id, target)
                when {
                    sender == GroupRole.ADMIN ->
                        if (targetRole == GroupRole.ADMIN && role != GroupRole.ADMIN && adminCount(connection, group.id) <= 1) lastAdmin() else null
                    // A moderator readmits a key without role (or confirms a key without role): nothing more.
                    sender == GroupRole.MODERATOR && role == null && targetRole == null -> null
                    else -> notAllowed("Only an administrator names or removes a role; a moderator only readmits an account without role.")
                }
            }
            GroupKinds.REMOVE_USER -> {
                val target = event.tagValues("p").first()
                val targetRole = roleOf(connection, group.id, target)
                when {
                    sender == GroupRole.ADMIN ->
                        if (targetRole == GroupRole.ADMIN && adminCount(connection, group.id) <= 1) lastAdmin() else null
                    sender == GroupRole.MODERATOR && targetRole == null -> null
                    else -> notAllowed("Only an administrator excludes an account with a role; a moderator excludes an account without role.")
                }
            }
            GroupKinds.EDIT_METADATA -> when {
                sender != GroupRole.ADMIN -> notAllowed("Only an administrator edits the name and the presentation of a room.")
                else -> event.firstTagValue(GroupKinds.NAME_TAG)?.let { renameCheck(connection, it) }
            }
            GroupKinds.DELETE_EVENT -> {
                val targetId = event.tagValues("e").first()
                val target = messageInfo(connection, targetId)
                when {
                    target == null -> Decision.Refused(404, "event_not_found", "No message with this id on this relay.")
                    target.kind != GroupKinds.CHAT_MESSAGE || target.groups != listOf(group.id) ->
                        Decision.Refused(400, "not_a_message", "Only a message of this room is removed from it.")
                    sender != null || target.pubkey == event.pubkey -> null
                    else -> notAllowed("Only an administrator, a moderator or its author removes a message.")
                }
            }
            else -> Decision.Refused(400, "unsupported_kind", "This moderation event is not accepted.")
        }
    }

    private fun apply(connection: Connection, event: SignedEvent, group: Group) {
        val now = OffsetDateTime.ofInstant(clock(), ZoneOffset.UTC)
        when (event.kind) {
            GroupKinds.PUT_USER -> {
                val tag = event.tags.first { it.firstOrNull() == "p" }
                val target = tag[1]
                unban(connection, group.id, target)
                val role = GroupRole.fromId(tag.getOrNull(2))
                if (role == null) {
                    deleteRole(connection, group.id, target)
                } else {
                    connection.prepareStatement(
                        "INSERT INTO group_role (group_id, pubkey, role, granted_by, granted_at) VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT (group_id, pubkey) DO UPDATE SET role = EXCLUDED.role, granted_by = EXCLUDED.granted_by, granted_at = EXCLUDED.granted_at",
                    ).use { statement ->
                        statement.setString(1, group.id)
                        statement.setString(2, target)
                        statement.setString(3, role.id)
                        statement.setString(4, event.pubkey)
                        statement.setObject(5, now)
                        statement.executeUpdate()
                    }
                }
            }
            GroupKinds.REMOVE_USER -> {
                val target = event.tagValues("p").first()
                deleteRole(connection, group.id, target)
                connection.prepareStatement(
                    "INSERT INTO group_ban (group_id, pubkey, banned_by, reason, banned_at) VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT (group_id, pubkey) DO UPDATE SET banned_by = EXCLUDED.banned_by, reason = EXCLUDED.reason, banned_at = EXCLUDED.banned_at",
                ).use { statement ->
                    statement.setString(1, group.id)
                    statement.setString(2, target)
                    statement.setString(3, event.pubkey)
                    statement.setString(4, event.content.trim())
                    statement.setObject(5, now)
                    statement.executeUpdate()
                }
            }
            GroupKinds.EDIT_METADATA -> {
                val previous = edits(connection, group.id)
                val name = event.firstTagValue(GroupKinds.NAME_TAG) ?: previous?.name
                val about = event.firstTagValue(GroupKinds.ABOUT_TAG) ?: previous?.about
                connection.prepareStatement(
                    "INSERT INTO group_edit (group_id, event_id, name, about, edited_by, edited_at) VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (group_id) DO UPDATE SET event_id = EXCLUDED.event_id, name = EXCLUDED.name, about = EXCLUDED.about, " +
                        "edited_by = EXCLUDED.edited_by, edited_at = EXCLUDED.edited_at",
                ).use { statement ->
                    statement.setString(1, group.id)
                    statement.setString(2, event.id)
                    statement.setString(3, name)
                    statement.setString(4, about)
                    statement.setString(5, event.pubkey)
                    statement.setObject(6, now)
                    statement.executeUpdate()
                }
                // The directory searches and compares the name and presentation in force.
                connection.prepareStatement("UPDATE group_directory SET name_key = ?, about_key = ? WHERE group_id = ?").use { statement ->
                    statement.setString(1, GroupNames.normalize(name ?: group.name.orEmpty()))
                    statement.setString(2, GroupNames.normalize(about ?: group.about.orEmpty()))
                    statement.setString(3, group.id)
                    statement.executeUpdate()
                }
            }
            GroupKinds.DELETE_EVENT -> {
                connection.prepareStatement(
                    "INSERT INTO group_removed_event (event_id, group_id, removed_by, reason, removed_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
                ).use { statement ->
                    statement.setString(1, event.tagValues("e").first())
                    statement.setString(2, group.id)
                    statement.setString(3, event.pubkey)
                    statement.setString(4, event.content.trim())
                    statement.setObject(5, now)
                    statement.executeUpdate()
                }
            }
        }
    }

    private class MessageInfo(val pubkey: String, val kind: Int, val groups: List<String>)

    /** The author, kind and rooms of an event, removed or not. */
    private fun messageInfo(connection: Connection, id: String): MessageInfo? =
        connection.prepareStatement("SELECT pubkey, kind, tags::text FROM events WHERE id = ?").use { statement ->
            statement.setString(1, id)
            val rows = statement.executeQuery()
            if (!rows.next()) return null
            val tags = TagsJson.decode(rows.getString(3))
            MessageInfo(rows.getString(1), rows.getInt(2), tags.filter { it.size >= 2 && it[0] == GroupKinds.GROUP_TAG }.map { it[1] })
        }

    private fun edits(connection: Connection, groupId: String): GroupEdit? =
        connection.prepareStatement("SELECT name, about FROM group_edit WHERE group_id = ?").use { statement ->
            statement.setString(1, groupId)
            val rows = statement.executeQuery()
            if (rows.next()) GroupEdit(rows.getString(1), rows.getString(2), null) else null
        }

    private fun adminCount(connection: Connection, groupId: String): Int =
        connection.prepareStatement("SELECT count(*) FROM group_role WHERE group_id = ? AND role = 'admin'").use { statement ->
            statement.setString(1, groupId)
            val rows = statement.executeQuery()
            rows.next()
            rows.getInt(1)
        }

    private fun unban(connection: Connection, groupId: String, pubkey: String) {
        connection.prepareStatement("DELETE FROM group_ban WHERE group_id = ? AND pubkey = ?").use { statement ->
            statement.setString(1, groupId)
            statement.setString(2, pubkey)
            statement.executeUpdate()
        }
    }

    private fun deleteRole(connection: Connection, groupId: String, pubkey: String) {
        connection.prepareStatement("DELETE FROM group_role WHERE group_id = ? AND pubkey = ?").use { statement ->
            statement.setString(1, groupId)
            statement.setString(2, pubkey)
            statement.executeUpdate()
        }
    }

    private fun notAllowed(message: String) = Decision.Refused(403, "not_allowed", message)

    private fun lastAdmin() = Decision.Refused(409, "last_admin", "The last administrator of a room cannot give up their role nor exclude themselves; name another administrator first.")

    companion object {
        /** Namespace of the room locks (the two-key form of the advisory locks); "PPFG" in ASCII. */
        private const val LOCK_NAMESPACE: Int = 0x50504647

        /** The lock of one room, held until the end of the transaction of [connection]. */
        internal fun lock(connection: Connection, groupId: String) {
            connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)").use { statement ->
                statement.setInt(1, LOCK_NAMESPACE)
                statement.setInt(2, groupId.hashCode())
                statement.execute()
            }
        }

        internal fun roleOf(connection: Connection, groupId: String, pubkey: String): GroupRole? =
            connection.prepareStatement("SELECT role FROM group_role WHERE group_id = ? AND pubkey = ?").use { statement ->
                statement.setString(1, groupId)
                statement.setString(2, pubkey)
                val rows = statement.executeQuery()
                if (rows.next()) GroupRole.fromId(rows.getString(1)) else null
            }

        /**
         * Called by EventRepository within the transaction that stores [event]: for a room
         * description, the creator becomes the first administrator of a room that has no role yet,
         * and a new version of the description by its creator replaces the edits made so far.
         */
        fun described(connection: Connection, event: SignedEvent) {
            val group = Group.from(event) ?: return
            connection.prepareStatement("DELETE FROM group_edit WHERE group_id = ?").use { statement ->
                statement.setString(1, group.id)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO group_role (group_id, pubkey, role, granted_by, granted_at) " +
                    "SELECT ?, ?, 'admin', ?, now() WHERE NOT EXISTS (SELECT 1 FROM group_role WHERE group_id = ?)",
            ).use { statement ->
                statement.setString(1, group.id)
                statement.setString(2, event.pubkey)
                statement.setString(3, event.pubkey)
                statement.setString(4, group.id)
                statement.executeUpdate()
            }
        }
    }
}

/** The name and presentation in force after the edits of a room, and the latest edit event. */
data class GroupEdit(val name: String?, val about: String?, val event: SignedEvent?)
