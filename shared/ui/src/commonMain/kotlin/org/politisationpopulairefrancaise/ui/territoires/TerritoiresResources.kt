package org.politisationpopulairefrancaise.ui.territoires

import org.politisationpopulairefrancaise.core.territoires.InMemoryTerritoiresStorage
import org.politisationpopulairefrancaise.core.territoires.Territoires
import org.politisationpopulairefrancaise.core.territoires.TerritoiresStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.resources.Res

/** The national level of the referential of territories shipped with the application (D-090): offline from the first launch. */
const val BUNDLED_NATIONAL_TERRITOIRES: String = "files/territoires/national.json"

/** The shipped national level, as text; null when the file cannot be read. */
suspend fun bundledNationalTerritoires(): String? =
    runCatching { Res.readBytes(BUNDLED_NATIONAL_TERRITOIRES).decodeToString(throwOnInvalidSequence = true) }.getOrNull()

/**
 * The referential of territories for the screens (read only for the other spaces): the local
 * database when there is one, the server of [dependencies] for the downloads after a gesture, and
 * the shipped national level. Create it once per screen state (`remember(dependencies)`).
 */
fun territoiresFor(dependencies: AppDependencies): Territoires = Territoires(
    storage = dependencies.database?.let(::TerritoiresStore) ?: InMemoryTerritoiresStorage(),
    server = dependencies.remote,
    bundled = ::bundledNationalTerritoires,
)
