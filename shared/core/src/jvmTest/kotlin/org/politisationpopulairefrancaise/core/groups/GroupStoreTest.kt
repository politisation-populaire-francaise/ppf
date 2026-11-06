package org.politisationpopulairefrancaise.core.groups

import org.politisationpopulairefrancaise.core.local.JvmDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupStoreTest {

    private var now = 1_000L
    private val database = JvmDatabase.inMemory()
    private val store = GroupStore(database, clock = { now })

    @Test
    fun joinsLeavesAndListsMostRecentFirst() {
        assertEquals(0, store.count())
        store.join("collectif-lyon")
        now = 2_000
        store.join("assemblee-paris")
        assertTrue(store.isMember("collectif-lyon"))
        assertTrue(store.isMember("assemblee-paris"))
        assertFalse(store.isMember("inconnu"))
        assertEquals(listOf("assemblee-paris", "collectif-lyon"), store.all())
        assertEquals(2, store.count())

        // Joining again keeps the first date.
        now = 3_000
        store.join("collectif-lyon")
        assertEquals(listOf("assemblee-paris", "collectif-lyon"), store.all())

        store.leave("collectif-lyon")
        store.leave("inconnu")
        assertFalse(store.isMember("collectif-lyon"))
        assertEquals(listOf("assemblee-paris"), store.all())

        store.clear()
        assertEquals(0, store.count())
    }

    @Test
    fun refusesInvalidIdsAndSurvivesOnTheSameDatabase() {
        assertFailsWith<IllegalArgumentException> { store.join("Pas Un Id") }
        store.join("ok")
        assertTrue(GroupStore(database).isMember("ok"))
    }
}
