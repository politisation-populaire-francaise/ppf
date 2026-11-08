package org.politisationpopulairefrancaise.core.comprendre

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.device.DeviceErasable
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.elus.ElusStorage
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.territoires.TerritoiresReader
import org.politisationpopulairefrancaise.core.territoires.TerritoiresStorage
import org.politisationpopulairefrancaise.core.territoires.TerritoiresStore

/**
 * The parts of the quick erase (S-44, M33; contract docs/integration/agir-securite-appareil.md, section 2 bis)
 * that hold what « Comprendre » keeps on the device. Each part erases for good, without any network call, may
 * be called again, and throws when it could not (the registry goes on with the other parts):
 *
 * - [PROGRESSION]: the progress of the courses and their answers (`Learning.sq`): what someone studies can
 *   reveal political interests;
 * - [ELUS]: the chosen commune and every downloaded package of the directory of elected officials
 *   (`Elus.sq`): the department downloaded reveals where the person lives;
 * - [TERRITOIRES]: the downloaded department packages of the referential of territories, for the same reason;
 *   the national package and the index of the names of the communes stay, the same on every device.
 *
 * The bundled contents (courses, sheets, glossary) are not personal and are not erased here.
 */
object ComprendreDeviceData {
    const val PROGRESSION: String = "comprendre-progression"
    const val ELUS: String = "comprendre-elus"
    const val TERRITOIRES: String = "comprendre-territoires"

    /** The parts of « Comprendre » over the local [database], in the order they are erased. */
    fun erasables(database: PpfDatabase): List<DeviceErasable> =
        erasables(LearningProgressStore(database), ElusStore(database), TerritoiresStore(database))

    /** The same parts over given stores (tests, devices without a database). */
    fun erasables(progress: LearningProgressStore?, elus: ElusStorage, territoires: TerritoiresStorage): List<DeviceErasable> = listOfNotNull(
        progress?.let { store -> deviceErasable(PROGRESSION) { withContext(Dispatchers.Default) { store.clear() } } },
        deviceErasable(ELUS) { withContext(Dispatchers.Default) { elus.forget() } },
        deviceErasable(TERRITOIRES) { withContext(Dispatchers.Default) { eraseDepartments(territoires) } },
    )

    /** Deletes the department packages of the territories; keeps the national one and the index of the communes. */
    fun eraseDepartments(territoires: TerritoiresStorage) {
        territoires.codes()
            .filter { it != TerritoiresReader.NATIONAL_CODE && it != TerritoiresReader.COMMUNES_CODE }
            .forEach(territoires::delete)
    }
}
