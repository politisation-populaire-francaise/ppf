package org.politisationpopulairefrancaise.ui.content

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.social.FollowStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * How the library asks the server for the followed accounts' publications: by batches of at most
 * 20 keys taken in random order, through the existing `author` parameter, never the whole list in
 * one request and never in the general pass; only authentic events of the requested authors are
 * kept; following sends the new key alone; the batches wait when a publication just left.
 */
class ContentLibraryFollowTest {

    /** Serves notes by author only when authors are asked for; the general pull returns nothing. */
    private class FakeRemote : RemoteEvents {
        val filters = mutableListOf<RemoteFilter>()
        val toServe = mutableListOf<SignedEvent>()
        val intruders = mutableListOf<SignedEvent>()
        val published = mutableListOf<SignedEvent>()

        val byAuthors: List<RemoteFilter> get() = filters.filter { it.authors.isNotEmpty() }
        val general: List<RemoteFilter> get() = filters.filter { it.authors.isEmpty() }

        override suspend fun publish(event: SignedEvent): PublicationResult {
            published += event
            return PublicationResult.Accepted(event.id)
        }

        override suspend fun fetch(filter: RemoteFilter): FetchResult {
            filters += filter
            if (filter.authors.isEmpty()) return FetchResult.Events(emptyList())
            return FetchResult.Events(toServe.filter { it.pubkey in filter.authors } + intruders)
        }
    }

    private fun CoroutineScope.library(database: PpfDatabase, remote: FakeRemote, follows: FollowStore, random: Random = Random.Default) =
        ContentLibrary(LocalEventStore(database), EditorialTrust.bundledExamples(), remote, this, follows, random)

    private suspend fun ContentLibrary.awaitSettled() = withTimeout(20_000) {
        while (status == LibraryStatus.Loading) delay(20)
    }

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(20)
    }

    @Test
    fun followedKeysAreRequestedInRandomBatchesOfAtMostTwentyAndNeverInTheGeneralPass() = runBlocking {
        val database = JvmDatabase.inMemory()
        val follows = FollowStore(database)
        val keys = List(45) { IdentityKeys.generate().publicKey.hex }
        keys.forEach(follows::follow)
        val remote = FakeRemote()
        val library = library(database, remote, follows, random = Random(42))

        library.start()
        library.awaitSettled()

        assertEquals(keys.toSet(), library.followed)
        assertEquals(LibraryStatus.Synced(0), library.status)
        val batches = remote.byAuthors
        assertEquals(listOf(20, 20, 5), batches.map { it.authors.size })
        assertTrue(batches.all { it.authors.size <= ContentLibrary.FOLLOW_BATCH })
        assertTrue(batches.all { it.kinds == listOf(EventKinds.TEXT_NOTE) })
        // Every key is asked exactly once, in the shuffled order, which differs from the stored order
        // (most recently followed first): the batches say nothing about when each account was followed.
        val stored = follows.all()
        val expected = stored.shuffled(Random(42))
        assertEquals(expected, batches.flatMap { it.authors })
        assertNotEquals(stored, expected)
        // The general pass (guides and notes of everyone) carries no author: the list never travels whole.
        assertEquals(1, remote.general.size)
        assertEquals(listOf(Article.KIND, EventKinds.TEXT_NOTE), remote.general.single().kinds)
    }

    @Test
    fun followingSendsTheNewKeyAloneAndKeepsOnlyAuthenticNotesOfTheRequestedAuthor() = runBlocking {
        val database = JvmDatabase.inMemory()
        val follows = FollowStore(database)
        val remote = FakeRemote()
        val alice = LocalEventSigner(IdentityKeys.generate())
        val bob = LocalEventSigner(IdentityKeys.generate())
        val stranger = LocalEventSigner(IdentityKeys.generate())
        val aliceNote = alice.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] note d’Alice", createdAt = 1_800_000_000)
        val forged = alice.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] contrefaçon", createdAt = 1_800_000_001).copy(sig = "0".repeat(128))
        val strangerNote = stranger.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] intrus", createdAt = 1_800_000_002)
        remote.toServe += listOf(aliceNote, forged)
        remote.intruders += strangerNote
        val library = library(database, remote, follows)

        library.start()
        library.awaitSettled()
        assertTrue(library.followed.isEmpty())
        assertTrue(remote.byAuthors.isEmpty())
        assertTrue(library.followedPosts.isEmpty())

        val aliceKey = alice.publicKey.hex
        library.follow(aliceKey)
        assertTrue(library.isFollowed(aliceKey))
        awaitUntil { library.posts.any { it.id == aliceNote.id } }
        assertEquals(listOf(listOf(aliceKey)), remote.byAuthors.map { it.authors })
        assertEquals(listOf(aliceNote.id), library.followedPosts.map { it.id })
        assertFalse(library.posts.any { it.id == forged.id })
        assertFalse(library.posts.any { it.id == strangerNote.id })
        awaitUntil { follows.isFollowed(aliceKey) }

        // A second account: only its key leaves, not the list.
        val bobKey = bob.publicKey.hex
        library.follow(bobKey)
        awaitUntil { remote.byAuthors.size == 2 }
        assertEquals(listOf(bobKey), remote.byAuthors.last().authors)
        awaitUntil { follows.isFollowed(bobKey) }

        // Anything but a valid key is ignored, in memory and on the device.
        library.follow("npub1notakey")
        library.follow(aliceKey.uppercase())
        assertEquals(setOf(aliceKey, bobKey), library.followed)
        assertEquals(2, remote.byAuthors.size)

        library.unfollow(aliceKey)
        assertFalse(library.isFollowed(aliceKey))
        assertTrue(library.followedPosts.isEmpty())
        // The note itself stays in the local copy; only the link is dropped.
        assertTrue(library.posts.any { it.id == aliceNote.id })
        awaitUntil { !follows.isFollowed(aliceKey) }

        // Erasing the keys forgets the whole list, on the device too.
        library.clearFollowed()
        assertTrue(library.followed.isEmpty())
        awaitUntil { follows.count() == 0L }
    }

    @Test
    fun theBatchesWaitForTheNextRefreshWhenAPublicationLeavesInTheSamePass() = runBlocking {
        val database = JvmDatabase.inMemory()
        val follows = FollowStore(database)
        val remote = FakeRemote()
        val aliceKey = IdentityKeys.generate().publicKey.hex
        follows.follow(aliceKey)
        val me = LocalEventSigner(IdentityKeys.generate())
        val mine = me.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] en attente", createdAt = 1_800_000_000)
        LocalEventStore(database).store(mine, pending = true)
        val library = library(database, remote, follows)

        library.start()
        library.awaitSettled()
        // The pending note went up in this pass, so no author batch travelled with it.
        assertEquals(listOf(mine), remote.published)
        assertTrue(remote.byAuthors.isEmpty())
        assertEquals(1, remote.general.size)

        library.refresh()
        awaitUntil { remote.byAuthors.size == 1 }
        assertEquals(listOf(listOf(aliceKey)), remote.byAuthors.map { it.authors })
        assertEquals(1, remote.published.size)
    }
}
