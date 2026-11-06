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
        requireKey(pubkeyHex)
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

    private fun requireKey(pubkeyHex: String) {
        require(isLowercaseHex(pubkeyHex, 64)) { "A followed key must be 64 lowercase hexadecimal characters" }
    }
}
