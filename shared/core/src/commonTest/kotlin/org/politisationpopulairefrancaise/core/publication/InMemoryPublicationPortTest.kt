package org.politisationpopulairefrancaise.core.publication

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryPublicationPortTest {

    @Test
    fun acceptsAuthenticEventsOnceAndRejectsForgedOnes() = runTest {
        val port = InMemoryPublicationPort()
        val signer = LocalEventSigner(IdentityKeys.generate())
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] première publication")

        assertEquals(PublicationResult.Accepted(event.id), port.publish(event))
        assertEquals(PublicationResult.Accepted(event.id), port.publish(event))
        assertEquals(listOf(event), port.events)

        val forged = event.copy(content = "[EXEMPLE] contenu modifié")
        assertEquals(PublicationResult.Rejected(forged.id, RejectionReason.INVALID_SIGNATURE), port.publish(forged))
        assertEquals(listOf(event), port.events)
    }

    @Test
    fun servesEventsBackWithTheServerFilters() = runTest {
        val port = InMemoryPublicationPort()
        val alice = LocalEventSigner(IdentityKeys.generate())
        val bob = LocalEventSigner(IdentityKeys.generate())
        val old = alice.sign(kind = EventKinds.TEXT_NOTE, content = "a", createdAt = 100)
        val recent = alice.sign(kind = EventKinds.TEXT_NOTE, content = "b", createdAt = 300, tags = listOf(listOf("p", bob.publicKey.hex), listOf("t", "x")))
        val article = bob.sign(kind = EventKinds.LONG_FORM_ARTICLE, content = "c", createdAt = 200, tags = listOf(listOf("d", "fiche"), listOf("p", alice.publicKey.hex)))
        listOf(old, recent, article).forEach { port.publish(it) }

        assertEquals(FetchResult.Events(listOf(recent, article, old)), port.fetch(RemoteFilter()))
        assertEquals(FetchResult.Events(listOf(recent, old)), port.fetch(RemoteFilter(kinds = listOf(EventKinds.TEXT_NOTE))))
        assertEquals(FetchResult.Events(listOf(article)), port.fetch(RemoteFilter(authors = listOf(bob.publicKey.hex))))
        assertEquals(FetchResult.Events(listOf(article)), port.fetch(RemoteFilter(identifiers = listOf("fiche"))))
        assertEquals(FetchResult.Events(listOf(recent, article)), port.fetch(RemoteFilter(since = 200)))
        assertEquals(FetchResult.Events(listOf(article, old)), port.fetch(RemoteFilter(until = 200)))
        assertEquals(FetchResult.Events(listOf(recent)), port.fetch(RemoteFilter(limit = 1)))

        val toBob = listOf(TagFilter("p", bob.publicKey.hex))
        assertEquals(FetchResult.Events(listOf(recent)), port.fetchByTags(toBob))
        assertEquals(FetchResult.Events(emptyList()), port.fetchByTags(toBob, RemoteFilter(kinds = listOf(EventKinds.LONG_FORM_ARTICLE))))
        // Several values of one tag: any of them; several tag names: all of them.
        assertEquals(FetchResult.Events(listOf(recent, article)), port.fetchByTags(listOf(TagFilter("p", bob.publicKey.hex), TagFilter("p", alice.publicKey.hex))))
        assertEquals(FetchResult.Events(listOf(recent)), port.fetchByTags(listOf(TagFilter("p", bob.publicKey.hex), TagFilter("t", "x"))))
        assertEquals(FetchResult.Events(emptyList()), port.fetchByTags(listOf(TagFilter("p", alice.publicKey.hex), TagFilter("t", "x"))))
        assertEquals("p:${bob.publicKey.hex}", toBob.single().parameter)
    }
}
