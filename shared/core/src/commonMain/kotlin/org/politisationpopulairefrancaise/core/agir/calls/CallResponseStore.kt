package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/**
 * The calls the user answered (AP-03), kept on the device only: answering publishes nothing, and the
 * server holds neither a counter nor a list of the people who answered (contract of the Agir space,
 * section 6). Only the address of the call is kept (`30023:<key>:<d>`, stable across its versions),
 * not the moment of the answer. An answer is erased [KEPT_AFTER_END] after the call is over
 * ([expired]), like the answers of the agenda a day after the event.
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

    companion object {
        /**
         * How long an answer is kept once its call is over ([Call.answersEndAt]): seven days, so that
         * the user still finds it just after the closing or the deadline, and no history of the calls
         * answered builds up on the device (decision of the owner, 7 November 2026, question 8, option C).
         */
        const val KEPT_AFTER_END: Long = 7 * 86_400

        /**
         * The addresses among [answered] whose call is over for more than [KEPT_AFTER_END] at [now].
         * A call the device does not hold any more, or without any date, keeps its answer.
         */
        fun expired(answered: Set<String>, calls: Collection<Call>, now: Long): Set<String> {
            val byAddress = calls.associateBy { it.address.toString() }
            return answered.filterTo(LinkedHashSet()) { address ->
                val end = byAddress[address]?.answersEndAt
                end != null && end + KEPT_AFTER_END <= now
            }
        }
    }
}
