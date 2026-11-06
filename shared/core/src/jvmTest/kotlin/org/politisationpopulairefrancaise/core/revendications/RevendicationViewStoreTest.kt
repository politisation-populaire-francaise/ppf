package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RevendicationViewStoreTest {

    private val database = JvmDatabase.inMemory()
    private val store = RevendicationViewStore(database)

    private val lyon = CustomRevendicationView(
        id = "custom:00112233445566ff",
        name = "Santé à Lyon",
        query = RevendicationQuery(
            themes = setOf("sante"),
            territories = setOf("metropole de lyon"),
            statuses = setOf(RevendicationStatus.EN_DEBAT),
            sort = RevendicationSort.POPULAR,
        ),
    )

    @Test
    fun anEmptyTableMeansTheDefaultLayout() {
        assertNull(store.layout())
        assertEquals(0, store.count())
    }

    @Test
    fun savesAndReadsBackTheLayoutInOrder() {
        val layout = listOf(RevendicationViews.ALL, lyon, RevendicationViews.theme("sante")!!, RevendicationViews.FOLLOWED)
        store.save(layout)
        assertEquals(layout, store.layout())
        assertEquals(4, store.count())

        // Saving replaces everything: order changes and removed views go away.
        val reordered = listOf(RevendicationViews.ALL, RevendicationViews.FOLLOWED, lyon)
        store.save(reordered)
        assertEquals(reordered, store.layout())
        assertEquals(3, store.count())
    }

    @Test
    fun resetBringsTheDefaultBack() {
        store.save(listOf(RevendicationViews.ALL, lyon))
        store.reset()
        assertNull(store.layout())
    }

    @Test
    fun unreadableRowsAreSkippedWithoutHidingTheOthers() {
        store.save(listOf(RevendicationViews.ALL, lyon))
        val queries = database.revendicationViewQueries
        queries.insert("theme:inconnu", 2, null, null) // a built-in view this version does not know
        queries.insert("custom:abababababababab", 3, "Cassée", "pas du json") // a damaged query
        queries.insert("custom:cdcdcdcdcdcdcdcd", 4, "   ", "{}") // an empty name
        queries.insert("suivis", 5, "Nom", null) // half a custom row
        queries.insert("theme:sante", 6, "Santé", "{}") // a custom row with a built-in id
        queries.insert("nationales", 7, null, null)
        assertEquals(listOf("tout", lyon.id, "nationales"), store.layout()?.map { it.id })
    }

    @Test
    fun theLayoutSurvivesReopeningTheDatabaseFile() {
        val directory = Files.createTempDirectory("ppf-views")
        val file = directory.resolve("ppf.db")
        try {
            RevendicationViewStore(JvmDatabase.open(file)).save(listOf(RevendicationViews.ALL, lyon, RevendicationViews.NATIONAL))
            assertEquals(listOf(RevendicationViews.ALL, lyon, RevendicationViews.NATIONAL), RevendicationViewStore(JvmDatabase.open(file)).layout())
        } finally {
            // The drivers stay open until the end of the process; a locked file is left to the system.
            runCatching { Files.deleteIfExists(file) }
            runCatching { Files.deleteIfExists(directory) }
        }
    }
}
