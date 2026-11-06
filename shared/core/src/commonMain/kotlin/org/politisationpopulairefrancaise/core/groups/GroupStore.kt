package org.politisationpopulairefrancaise.core.groups

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The rooms the user joined, kept on the device only. Joining publishes nothing and the server
 * holds no list of members (decision D-028): the "Mes salons" section is assembled here. The
 * descriptions and messages of the rooms themselves live in the event store, readable offline.
 */
class GroupStore(private val database: PpfDatabase, private val clock: () -> Long = ::currentEpochSeconds) {

    private val queries get() = database.groupsQueries

    /** Joins a room; joining it again changes nothing (the first date is kept). */
    fun join(groupId: String) {
        require(Group.isValidId(groupId)) { "Invalid room id" }
        queries.insert(groupId, clock())
    }

    /** Leaves a room; unknown ids are ignored. */
    fun leave(groupId: String) {
        queries.deleteByGroupId(groupId)
    }

    fun isMember(groupId: String): Boolean = queries.countByGroupId(groupId).executeAsOne() > 0

    /** Every joined room id, most recently joined first. */
    fun all(): List<String> = queries.selectAll().executeAsList()

    fun count(): Long = queries.countAll().executeAsOne()

    /** Forgets every room: called when the user erases the keys from the device. */
    fun clear() {
        queries.deleteAll()
    }
}
