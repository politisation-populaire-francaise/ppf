package org.politisationpopulairefrancaise.ui.content

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the library asks the server for the followed accounts' publications: by batches of at most
 * 20 keys taken in random order, through the existing `author` parameter, never the whole list in
 * one request; and only authentic events of the requested authors are kept.
 */
class ContentLibraryFollowTest {

    /** Serves notes by author only when authors are asked for; the general pull returns nothing. */
    private class FakeRemote : RemoteEvents {
        val filters = mutableListOf<RemoteFilter>()
        val toServe = mutableListOf<SignedEvent>()
        val intruders = mutableListOf<SignedEvent>()

        val byAuthors: List<RemoteFilter> get() = filters.filter { it.authors.isNotEmpty() }

        override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Accepted(event.id)

        override suspend fun fetch(filter: RemoteFilter): FetchResult {
            filters += filter
            if (filter.authors.isEmpty()) return FetchResult.Events(emptyList())
            return FetchResult.Events(toServe.filter { it.pubkey in filter.authors } + intruders)
        }
    }

    private suspend fun ContentLibrary.awaitSettled() = withTimeout(20_000) {
        while (status == LibraryStatus.Loading) delay(20)
    }

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(20)
    }

    @Test
    fun followedKeysAreRequestedInRandomBatchesOfAtMostTwenty() = runBlocking {
        val database = JvmDatabase.inMemory()
        val follows = FollowStore(database)
        val keys = List(45) { IdentityKeys.generate().publicKey.hex }
        keys.forEach(follows::follow)
        val remote = FakeRemote()
        val library = ContentLibrary(LocalEventStore(database), EditorialTrust.bundledExamples(), remote, this, follows)

        library.start()
        library.awaitSettled()

        assertEquals(keys.toSet(), library.followed)
        assertEquals(LibraryStatus.Synced(0), library.status)
        val batches = remote.byAuthors
        assertEquals(3, batches.size)
        assertEquals(listOf(20, 20, 5), batches.map { it.authors.size })
        assertTrue(batches.all { it.authors.size <= ContentLibrary.FOLLOW_BATCH })
        assertTrue(batches.all { it.kinds == listOf(EventKinds.TEXT_NOTE) })
        // Every key is asked exactly once, the whole list never in one request.
        assertEquals(keys.toSet(), batches.flatMap { it.authors }.toSet())
        assertEquals(keys.size, batches.sumOf { it.authors.size })
    }

    @Test
    fun followingFetchesTheAuthorsNotesAndKeepsOnlyAuthenticOnesFromTheRequestedAuthors() = runBlocking {
        val database = JvmDatabase.inMemory()
        val follows = FollowStore(database)
        val remote = FakeRemote()
        val alice = LocalEventSigner(IdentityKeys.generate())
        val stranger = LocalEventSigner(IdentityKeys.generate())
        val aliceNote = alice.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] note d’Alice", createdAt = 1_800_000_000)
        val forged = alice.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] contrefaçon", createdAt = 1_800_000_001).copy(sig = "0".repeat(128))
        val strangerNote = stranger.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] intrus", createdAt = 1_800_000_002)
        remote.toServe += listOf(aliceNote, forged)
        remote.intruders += strangerNote
        val library = ContentLibrary(LocalEventStore(database), EditorialTrust.bundledExamples(), remote, this, follows)

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

        library.unfollow(aliceKey)
        assertFalse(library.isFollowed(aliceKey))
        assertTrue(library.followedPosts.isEmpty())
        // The note itself stays in the local copy; only the link is dropped.
        assertTrue(library.posts.any { it.id == aliceNote.id })
        awaitUntil { !follows.isFollowed(aliceKey) }
    }
}
