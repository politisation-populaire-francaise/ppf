package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.elus.ElusStore

/** The codes of the commune chosen on this device and of its department, for « Près de chez moi »; read here only. */
@Composable
internal fun rememberChosenTerritories(database: PpfDatabase?): Set<String> {
    if (database == null) return emptySet()
    var codes by remember(database) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(database) {
        codes = withContext(Dispatchers.Default) {
            runCatching { ElusStore(database).commune() }.getOrNull()?.let { setOf(it.communeCode, it.departmentCode) }.orEmpty()
        }
    }
    return codes
}
