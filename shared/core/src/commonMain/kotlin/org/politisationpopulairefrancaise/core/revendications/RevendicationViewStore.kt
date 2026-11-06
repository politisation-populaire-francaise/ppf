package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/**
 * The layout of the demands wall chosen by the user (which views, in which order), kept on the
 * device only: views reveal political interests, a sensitive datum under article 9 of the GDPR,
 * so they are never sent anywhere. The temporary filters are not stored.
 */
class RevendicationViewStore(private val database: PpfDatabase) {

    private val queries get() = database.revendicationViewQueries

    /**
     * The saved layout in tab order, or null when the user never changed it (the default layout
     * then applies). Rows that cannot be read (unknown built-in id, unreadable query, invalid
     * name) are skipped: a damaged row never hides the others.
     */
    fun layout(): List<RevendicationView>? {
        val rows = queries.selectAll().executeAsList()
        if (rows.isEmpty()) return null
        return rows.mapNotNull { row -> read(row.id, row.name, row.query) }
    }

    /** Replaces the whole saved layout, in one transaction: the list order becomes the tab order. */
    fun save(layout: List<RevendicationView>) = database.transaction {
        queries.deleteAll()
        layout.forEachIndexed { index, view ->
            when (view) {
                is BuiltInRevendicationView -> queries.insert(view.id, index.toLong(), null, null)
                is CustomRevendicationView -> queries.insert(view.id, index.toLong(), view.name, view.query.toJson())
            }
        }
    }

    /** Forgets the saved layout: the default layout comes back. */
    fun reset() {
        queries.deleteAll()
    }

    fun count(): Long = queries.countAll().executeAsOne()

    private fun read(id: String, name: String?, query: String?): RevendicationView? = when {
        name == null && query == null -> RevendicationViews.builtIn(id)
        name != null && query != null && id.startsWith(RevendicationViews.CUSTOM_PREFIX) -> {
            val cleanName = RevendicationViews.normalizeName(name)
            val parsed = RevendicationQuery.fromJson(query)
            if (cleanName != null && parsed != null) CustomRevendicationView(id, cleanName, parsed) else null
        }
        else -> null
    }
}
