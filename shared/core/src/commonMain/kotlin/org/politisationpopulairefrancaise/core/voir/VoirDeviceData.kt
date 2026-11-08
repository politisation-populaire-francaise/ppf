package org.politisationpopulairefrancaise.core.voir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.device.DeviceErasable
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentStore

/**
 * The data of "Voir" in the local database, for the quick erase (S-44, M33): the contents the user
 * follows (they can reveal a political opinion), the views of the demands wall (themes, territories,
 * followed accounts), and the parliamentary open data downloaded (which months and chambers were
 * looked at). The drafts of requests and the anonymous reports waiting for a server live in memory
 * only; the interface forgets them with this part (`ui/voir`). The public copies of the contents
 * stay in the shared store of contents, erased by its own part (docs/integration/agir-securite-appareil.md).
 */
class VoirDeviceData(private val database: PpfDatabase) : DeviceErasable {
    override val erasableId: String = ID

    override suspend fun eraseFromDevice() = withContext(Dispatchers.Default) {
        ContentFollowStore(database).clear()
        RevendicationViewStore(database).reset()
        ParliamentStore(database).forget()
    }

    companion object {
        const val ID: String = "voir-data"
    }
}
