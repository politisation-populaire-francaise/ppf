package org.politisationpopulairefrancaise.ui.agir.calls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.calls.CallDraft
import org.politisationpopulairefrancaise.core.agir.calls.CallResponseStore
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.calls.DateTimeText
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.content.OwnDemandState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The calls on the device: bundled examples, the server's published calls, the user's own and their review, the answers. */
class CallsLibraryTest {
    private val author = IdentityKeys.generate()
    private val someone = IdentityKeys.generate()

    private suspend fun eventually(condition: () -> Boolean) {
        repeat(2_400) {
            if (condition()) return
            delay(25)
        }
        throw AssertionError("condition not met in time")
    }

    private suspend fun signedCall(keys: IdentityKeys, slug: String, title: String = "[EXEMPLE] Réunion publique sur le marché couvert"): SignedEvent {
        val draft = CallDraft(
            type = CallType.SE_REUNIR,
            title = title,
            text = "[EXEMPLE] Une réunion ouverte pour parler de l'avenir du marché couvert.",
            territory = "Nancy",
            location = "Salle fictive du marché",
            dates = mapOf("start" to DateTimeText("20/11/2026", "19:00")),
            fields = mapOf("ppf-object" to "L'avenir du marché", "ppf-organizer" to "[EXEMPLE] Collectif fictif", "ppf-accessibility" to "Plain-pied"),
        )
        return LocalEventSigner(keys).sign(draft.toUnsignedEvent(keys.publicKey, NOW, slug))
    }

    private fun <T> withScope(block: suspend CoroutineScope.(CoroutineScope) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun bundledExamplesAreKeptOnlyFromTrustedEditors() = withScope { scope ->
        val trusted = CallsLibrary(LocalEventStore(JvmDatabase.inMemory()), EditorialTrust.bundledExamples(), null, scope, clock = { NOW })
        trusted.start()
        eventually { trusted.calls.size == 8 }
        assertEquals(LibraryStatus.Offline, trusted.status)
        assertEquals(1, trusted.calls.count { !it.isOpen })

        val untrusted = CallsLibrary(LocalEventStore(JvmDatabase.inMemory()), EditorialTrust(emptySet()), null, scope, clock = { NOW })
        untrusted.start()
        eventually { untrusted.status == LibraryStatus.Offline }
        assertEquals(emptyList(), untrusted.calls)
    }

    @Test
    fun publishedCallsComeFromTheServerByTheirTagAndPendingOnesLeave() = withScope { scope ->
        val server = CallServer(held = false)
        server.published.publish(signedCall(someone, "marche-couvert"))
        // A demand is not a call.
        val demand = LocalEventSigner(someone).sign(
            Article.build(someone.publicKey, "une-revendication", "[EXEMPLE] Une revendication", null, emptyList(), ArticleType.REVENDICATION, ContentOrigin.HUMAN, NOW, NOW, "Texte"),
        )
        server.published.publish(demand)
        val store = LocalEventStore(JvmDatabase.inMemory()) { NOW }
        // A call of the user kept on the device, never sent.
        val mine = signedCall(author, "ma-reunion", title = "[EXEMPLE] Ma réunion de quartier")
        store.store(mine, pending = true)

        val library = CallsLibrary(store, EditorialTrust(emptySet()), server, scope, clock = { NOW })
        library.setAccount(author.publicKey.hex)
        library.start()
        eventually { library.status is LibraryStatus.Synced }
        assertEquals(listOf(mine.id), server.received.map { it.id })
        assertTrue(library.calls.any { it.slug == "marche-couvert" })
        assertTrue(library.calls.none { it.slug == "une-revendication" })
        // Published at once (201): on the wall, and "Publié" among the user's own.
        assertEquals(OwnDemandState.PUBLISHED, library.myCalls.single().state)
        assertEquals(NOW, store.cursor(CallsLibrary.CURSOR))
    }

    @Test
    fun theReviewOfTheUsersCallsIsFollowedAndAnUnknownIdMeansPublished() = withScope { scope ->
        val server = CallServer(held = true)
        val store = LocalEventStore(JvmDatabase.inMemory()) { NOW }
        val first = signedCall(author, "premiere")
        val second = signedCall(author, "seconde", title = "[EXEMPLE] Une autre réunion de quartier")
        store.store(first, pending = true)
        store.store(second, pending = true)
        val library = CallsLibrary(store, EditorialTrust(emptySet()), server, scope, clock = { NOW })
        library.setAccount(author.publicKey.hex)
        library.start()
        eventually { library.status is LibraryStatus.Synced }
        assertEquals(setOf(OwnDemandState.IN_REVIEW), library.myCalls.map { it.state }.toSet())
        assertTrue(library.calls.isEmpty())

        // The server no longer knows the second one (decided long ago, or published directly).
        server.decisions.remove(second.id)
        library.refresh()
        eventually { library.myCalls.firstOrNull { it.call.slug == "seconde" }?.state == OwnDemandState.PUBLISHED }
        assertEquals(OwnDemandState.IN_REVIEW, library.myCalls.first { it.call.slug == "premiere" }.state)

        // Without a server answer, nothing is decided: the states stay as known.
        server.reachable = false
        library.refresh()
        eventually { library.status == LibraryStatus.Unreachable }
        assertEquals(OwnDemandState.IN_REVIEW, library.myCalls.first { it.call.slug == "premiere" }.state)
    }

    @Test
    fun answersAreKeptOnTheDeviceAcrossRestartsAndCanBeErased() = withScope { scope ->
        val database = JvmDatabase.inMemory()
        val store = LocalEventStore(database) { NOW }
        val first = CallsLibrary(store, EditorialTrust.bundledExamples(), null, scope, CallResponseStore(database), clock = { NOW })
        first.start()
        eventually { first.calls.size == 8 }
        val petition = first.calls.first { it.slug == Examples.PETITION }
        first.toggleResponse(petition)
        assertTrue(first.isResponded(petition))
        eventually { CallResponseStore(database).count() == 1L }

        val second = CallsLibrary(store, EditorialTrust.bundledExamples(), null, scope, CallResponseStore(database), clock = { NOW })
        second.start()
        eventually { second.responded == setOf(petition.address.toString()) }
        second.toggleResponse(petition)
        eventually { CallResponseStore(database).count() == 0L }
        second.toggleResponse(petition)
        second.clearResponses()
        eventually { CallResponseStore(database).count() == 0L && second.responded.isEmpty() }
    }

    @Test
    fun anAnswerIsErasedSevenDaysAfterTheCallIsOver() = withScope { scope ->
        // Decision of the owner (7 November 2026, question 8, option C): no history of the calls answered.
        val database = JvmDatabase.inMemory()
        val store = LocalEventStore(database) { NOW }
        val meeting = signedCall(someone, "reunion-marche-couvert")
        store.store(meeting)
        val address = "30023:${someone.publicKey.hex}:reunion-marche-couvert"
        CallResponseStore(database).respond(address)
        val start = org.politisationpopulairefrancaise.core.agir.calls.ParisTime.toEpochSeconds(org.politisationpopulairefrancaise.core.agir.calls.ParisDateTime(2026, 11, 20, 19, 0))

        // Six days after the meeting, the answer is still there.
        val before = CallsLibrary(store, EditorialTrust.bundledExamples(), null, scope, CallResponseStore(database), clock = { start + 6 * 86_400 })
        before.start()
        eventually { before.calls.any { it.slug == "reunion-marche-couvert" } && before.status == LibraryStatus.Offline }
        delay(100)
        assertEquals(setOf(address), before.responded)
        assertEquals(1L, CallResponseStore(database).count())

        // Seven days after, the next loading erases it, on the screen and in the database.
        val after = CallsLibrary(store, EditorialTrust.bundledExamples(), null, scope, CallResponseStore(database), clock = { start + CallResponseStore.KEPT_AFTER_END })
        after.start()
        eventually { CallResponseStore(database).count() == 0L }
        assertEquals(emptySet(), after.responded)
    }

    @Test
    fun thePracticalGuidesAndTheDemandsNeverShowTheCalls() = withScope { scope ->
        val store = LocalEventStore(JvmDatabase.inMemory())
        val calls = CallsLibrary(store, EditorialTrust.bundledExamples(), null, scope, clock = { NOW })
        calls.start()
        eventually { calls.calls.size == 8 }
        val content = ContentLibrary(store, EditorialTrust.bundledExamples(), null, scope)
        content.start()
        eventually { content.status == LibraryStatus.Offline && content.guides.isNotEmpty() }
        val callSlugs = calls.calls.map { it.slug }.toSet()
        assertEquals(26, content.guides.size)
        assertTrue(content.guides.none { it.slug in callSlugs })
        assertTrue(content.revendications.none { it.slug in callSlugs })
        assertEquals(24, content.revendications.size) // 8 examples and 16 real demands (D-152)
    }
}
