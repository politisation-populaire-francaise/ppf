package org.politisationpopulairefrancaise.core.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.politisationpopulairefrancaise.core.db.PpfDatabase

/** Opens the local database in the app's sandbox; the schema is created or migrated automatically. */
object IosDatabase {
    fun open(name: String = "ppf.db"): PpfDatabase = PpfDatabase(NativeSqliteDriver(PpfDatabase.Schema, name))
}
