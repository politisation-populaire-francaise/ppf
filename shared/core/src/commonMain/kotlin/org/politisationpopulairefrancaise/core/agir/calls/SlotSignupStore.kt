package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** One signup of the user (AP-11): what the device keeps to show it and to withdraw it. */
data class MySlotSignup(val id: String, val callAddress: String, val slotId: String, val token: String, val slotEnd: Long)

/**
 * The signups of the user to the slots of calls, on the device only (table `call_slot_signup`). A signup is forgotten
 * [CallSlots.DEVICE_KEPT_AFTER_END] after the end of its slot, withdrawn by the user, or erased with the data of Agir.
 */
class SlotSignupStore(private val database: PpfDatabase, private val clock: () -> Long = ::currentEpochSeconds) {
    private val queries get() = database.callSlotSignupQueries

    fun add(signup: MySlotSignup) = queries.insert(signup.id, signup.callAddress, signup.slotId, signup.token, signup.slotEnd)

    /** The signups to [callAddress] still kept (the old ones are forgotten first). */
    fun ofCall(callAddress: String): List<MySlotSignup> {
        forgetOld()
        return queries.selectOfCall(callAddress).executeAsList().map { MySlotSignup(it.id, it.call_address, it.slot_id, it.token, it.slot_end) }
    }

    fun remove(id: String) = queries.deleteById(id)

    fun forgetOld(now: Long = clock()) = queries.deleteEndedBefore(now - CallSlots.DEVICE_KEPT_AFTER_END)

    fun count(): Long = queries.countAll().executeAsOne()

    fun clear() = queries.deleteAll()
}
