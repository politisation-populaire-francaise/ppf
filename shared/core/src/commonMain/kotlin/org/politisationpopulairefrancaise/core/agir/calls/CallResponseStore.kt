package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/**
 * The calls the user answered (AP-03), kept on the device only: answering publishes nothing, and the
 * server holds neither a counter nor a list of the people who answered (contract of the Agir space,
 * section 6). Only the address of the call is kept (`30023:<key>:<d>`, stable across its versions),
 * not the moment of the answer.
 */
class CallResponseStore(private val database: PpfDatabase) {

    private val queries get() = database.callResponseQueries

    /** Answers a call; answering again changes nothing. */
    fun respond(address: String) {
        require(address.isNotBlank()) { "A call address is required" }
        queries.insert(address)
    }

    /** Withdraws the answer; unknown addresses are ignored. */
    fun withdraw(address: String) {
        queries.deleteByAddress(address)
    }

    fun all(): Set<String> = queries.selectAll().executeAsList().toSet()

    fun count(): Long = queries.countAll().executeAsOne()

    /** Forgets every answer (the user's choice, or the erasure of the keys). */
    fun clear() {
        queries.deleteAll()
    }
}
