package org.politisationpopulairefrancaise.core.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.politisationpopulairefrancaise.core.db.PpfDatabase

/** Opens the local database in the app's private storage; the schema is created or migrated automatically. */
object AndroidDatabase {
    fun open(context: Context, name: String = "ppf.db"): PpfDatabase =
        PpfDatabase(AndroidSqliteDriver(PpfDatabase.Schema, context, name))
}
