package org.politisationpopulairefrancaise.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.politisationpopulairefrancaise.core.elus.ElusDirectory
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.elus.InMemoryElusStorage
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.comprendre.elus.CommuneChooserDialog
import org.politisationpopulairefrancaise.ui.comprendre.elus.ElusLibrary

/**
 * One « Mon territoire » for the whole application: the commune chosen for « Mes élus » (`ElusStore.commune()`, on the
 * device), which « Agir », « Décider » and « En direct » read. Any space may let the person choose or change it with the
 * same dialog as « Mes élus » ([CommuneChoiceDialog]), never with a setting of its own.
 */
class CommuneChoice internal constructor(internal val elus: ElusLibrary) {
    /** Opens the choice of the commune (postal code, then the commune). */
    fun open() = elus.startChoosing()

    /** Changes each time a commune is chosen or forgotten: a key for the screens that read the territory again. */
    val version: Any? get() = elus.mine
}

@Composable
fun rememberCommuneChoice(dependencies: AppDependencies): CommuneChoice {
    val scope = rememberCoroutineScope()
    val choice = remember(dependencies) {
        CommuneChoice(
            ElusLibrary(
                directory = ElusDirectory(dependencies.remote, dependencies.database?.let(::ElusStore) ?: InMemoryElusStorage()),
                scope = scope,
                dataSaver = dependencies.dataSaver,
            ),
        )
    }
    LaunchedEffect(choice) { choice.elus.load() }
    return choice
}

/** The dialog of the choice, shown while it is open; place it once in the screen that offers the choice. */
@Composable
fun CommuneChoiceDialog(choice: CommuneChoice) = CommuneChooserDialog(choice.elus)
