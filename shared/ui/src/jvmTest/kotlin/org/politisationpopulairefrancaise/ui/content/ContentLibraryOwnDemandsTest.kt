package org.politisationpopulairefrancaise.ui.content

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.revendications.ProposalStatus
import org.politisationpopulairefrancaise.core.revendications.checks.RevendicationDraft
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.revendications.propose.ReviewingServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The user's own demands: kept apart until the team publishes them, then on the wall; someone
 * else's demand served by the server is on the wall (the server publishes only reviewed ones).
 */
class ContentLibraryOwnDemandsTest {
    private val keys = IdentityKeys.generate()
    private val signer = LocalEventSigner(keys)

    private suspend fun demand(title: String, signer: LocalEventSigner = this.signer): SignedEvent =
        signer.sign(RevendicationDraft(title = title, why = "[EXEMPLE]", themes = listOf("sante")).toUnsignedEvent(signer.publicKey, 1_793_000_000L))

    private fun CoroutineScope.library(store: LocalEventStore, remote: RemoteEvents?) =
        ContentLibrary(store, EditorialTrust.bundledExamples(), remote, this)

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(20)
    }

    @Test
    fun aDemandWaitingToLeaveIsTheUsersOwnAndNotOnTheWall() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val event = demand("[EXEMPLE] Un médecin de garde le soir dans chaque canton")
        store.store(event, pending = true)
        val library = library(store, remote = null)
        library.start()
        library.setAccount(keys.publicKey.hex)
        awaitUntil { library.myRevendications.isNotEmpty() }
        assertEquals(OwnDemandState.WAITING, library.myRevendications.single().state)
        assertTrue(library.revendications.none { it.event.id == event.id })
        coroutineContext.cancelChildren()
    }

    @Test
    fun theServersDecisionMovesTheDemandOntoTheWallOrKeepsItApartWithTheReason() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val server = ReviewingServer()
        val event = demand("[EXEMPLE] Un médecin de garde le soir dans chaque canton")
        store.store(event)
        server.held += event
        val someoneElse = demand("[EXEMPLE] Des bus de nuit dans chaque métropole", LocalEventSigner(IdentityKeys.generate()))
        store.store(someoneElse)

        val library = library(store, server)
        library.start()
        library.setAccount(keys.publicKey.hex)
        awaitUntil { library.myRevendications.singleOrNull()?.report != null }
        assertEquals(OwnDemandState.IN_REVIEW, library.myRevendications.single().state)
        assertTrue(library.revendications.any { it.event.id == someoneElse.id }, "served by the server: published")
        assertTrue(library.revendications.none { it.event.id == event.id })

        server.decision = ProposalStatus.REJECTED
        library.onDemandSent()
        awaitUntil { library.myRevendications.single().state == OwnDemandState.NOT_PUBLISHED }
        assertEquals("[EXEMPLE] Revendication déjà présente sur le mur.", library.myRevendications.single().reason)
        assertTrue(library.revendications.none { it.event.id == event.id })

        server.decision = ProposalStatus.APPROVED
        library.onDemandSent()
        awaitUntil { library.myRevendications.single().state == OwnDemandState.PUBLISHED }
        assertTrue(library.revendications.any { it.event.id == event.id }, "published: on the wall")

        library.setAccount(null)
        awaitUntil { library.myRevendications.isEmpty() }
        assertTrue(library.revendications.any { it.event.id == event.id }, "without the account it is a demand like the others")
        coroutineContext.cancelChildren()
    }

}
