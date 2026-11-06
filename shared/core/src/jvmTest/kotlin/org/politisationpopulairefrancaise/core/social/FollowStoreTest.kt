package org.politisationpopulairefrancaise.core.social

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowStoreTest {

    private val database = JvmDatabase.inMemory()
    private var now = 1_700_000_000L
    private val store = FollowStore(database, clock = { now })
    private val alice = IdentityKeys.generate().publicKey.hex
    private val bob = IdentityKeys.generate().publicKey.hex

    @Test
    fun followsUnfollowsAndAnswersWhetherAKeyIsFollowed() {
        assertFalse(store.isFollowed(alice))
        store.follow(alice)
        assertTrue(store.isFollowed(alice))
        assertFalse(store.isFollowed(bob))
        assertEquals(listOf(alice), store.all())
        assertEquals(1, store.count())

        store.unfollow(alice)
        assertFalse(store.isFollowed(alice))
        assertEquals(emptyList(), store.all())
        // Unfollowing an unknown key is harmless.
        store.unfollow(bob)
        assertEquals(0, store.count())
    }

    @Test
    fun followingTwiceKeepsOneEntryWithItsFirstDate() {
        store.follow(alice)
        now += 100
        store.follow(alice)
        now += 100
        store.follow(bob)
        assertEquals(2, store.count())
        // Most recently followed first: bob (now + 200), then alice (first date kept).
        assertEquals(listOf(bob, alice), store.all())
    }

    @Test
    fun refusesAnythingButALowercaseHexKey() {
        assertFailsWith<IllegalArgumentException> { store.follow("npub1" + "q".repeat(58)) }
        assertFailsWith<IllegalArgumentException> { store.follow(alice.uppercase()) }
        assertFailsWith<IllegalArgumentException> { store.follow(alice.take(63)) }
        assertFailsWith<IllegalArgumentException> { store.follow("") }
        assertEquals(0, store.count())
    }

    @Test
    fun theListSurvivesANewStoreOnTheSameDatabase() {
        store.follow(alice)
        store.follow(bob)
        val reopened = FollowStore(database)
        assertEquals(setOf(alice, bob), reopened.all().toSet())
        assertTrue(reopened.isFollowed(alice))
        // The follow table shares the database with the events without interfering with them.
        assertEquals(0, LocalEventStore(database).count())
    }
}
