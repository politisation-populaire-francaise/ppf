package org.politisationpopulairefrancaise.core.agir.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaStore
import org.politisationpopulairefrancaise.core.agir.calls.CallResponseStore
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignupStore
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyStore
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.device.DeviceErasable

/**
 * The data of "Agir" in the local database, for the quick erase (S-44): the answers to the
 * mobilizations and the proposals followed in the agenda, the answers to the calls and the signups to their slots, the
 * testimonies waiting or sent and their encrypted files. The rally point and the security settings
 * are erased by their own parts. The public copies of the contents (agenda, directory) stay in the
 * shared store of contents, whose own part is still to come (docs/integration/agir-securite-appareil.md).
 */
class AgirDeviceData(private val database: PpfDatabase) : DeviceErasable {
    override val erasableId: String = ID

    override suspend fun eraseFromDevice() = withContext(Dispatchers.Default) {
        val agenda = AgendaStore(database)
        agenda.clearResponses()
        agenda.proposals().keys.forEach(agenda::forgetProposal)
        CallResponseStore(database).clear()
        SlotSignupStore(database).clear()
        TestimonyStore(database).clear()
    }

    companion object {
        const val ID: String = "agir-data"
    }
}
