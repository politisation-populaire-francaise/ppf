package org.politisationpopulairefrancaise.core.social

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.signing.isLowercaseHex
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The accounts the user follows, kept on the device only (S-120, AG-22). Nothing here ever leaves
 * the device as a list: the "Suivis" view is assembled locally, and the server is asked for the
 * followed authors' publications in small random batches, never for the whole list.
 *
 * Keys are lowercase hex public keys, exactly as they appear in signed events.
 */
class FollowStore(private val database: PpfDatabase, private val clock: () -> Long = ::currentEpochSeconds) {

    private val queries get() = database.followQueries

    /** Follows an account; following it again changes nothing (the first date is kept). */
    fun follow(pubkeyHex: String) {
        require(isValidKey(pubkeyHex)) { "A followed key must be 64 lowercase hexadecimal characters" }
        queries.insert(pubkeyHex, clock())
    }

    /** Stops following an account; unknown keys are ignored. */
    fun unfollow(pubkeyHex: String) {
        queries.deleteByPubkey(pubkeyHex)
    }

    fun isFollowed(pubkeyHex: String): Boolean = queries.countByPubkey(pubkeyHex).executeAsOne() > 0

    /** Every followed key, most recently followed first. */
    fun all(): List<String> = queries.selectAll().executeAsList()

    fun count(): Long = queries.countAll().executeAsOne()

    /** Forgets every followed account: called when the user erases the keys from the device. */
    fun clear() {
        queries.deleteAll()
    }

    companion object {
        /** True for the only form a followed key may take: 64 lowercase hexadecimal characters. */
        fun isValidKey(pubkeyHex: String): Boolean = isLowercaseHex(pubkeyHex, 64)
    }
}
