package org.politisationpopulairefrancaise.ui.agir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.territoires.TerritoryCode
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.territoires.territoiresFor

/**
 * « Près de chez moi » in « Agir »: the commune the person chose in « Mes élus » and its department, read on the
 * device only (nothing new is kept, nothing leaves the device). A mobilization or a call is near when its territory,
 * as written by its author, names the commune or the department.
 */
data class MyTerritory(val commune: String, val department: String?) {
    /** The folded names a territory is compared with (the keys of the filters of the agenda and the calls). */
    val keys: Set<String> = listOfNotNull(commune, department).map(FrenchText::fold).filter { it.isNotEmpty() }.toSet()

    fun isNear(territory: String): Boolean = FrenchText.fold(territory) in keys

    companion object {
        /** The territory of the commune chosen in « Mes élus », or null when none is chosen (or the referential lacks it). */
        suspend fun of(dependencies: AppDependencies): MyTerritory? {
            val database = dependencies.database ?: return null
            val chosen = withContext(Dispatchers.Default) { ElusStore(database).commune() } ?: return null
            val territoires = territoiresFor(dependencies)
            val commune = TerritoryCode.commune(chosen.communeCode)?.let { territoires.find(it) } ?: return null
            val department = TerritoryCode.departement(chosen.departmentCode)?.let { territoires.find(it) }?.name
            return MyTerritory(commune.name, department)
        }
    }
}
