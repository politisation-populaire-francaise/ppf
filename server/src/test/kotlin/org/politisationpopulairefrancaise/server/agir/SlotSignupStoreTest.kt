package org.politisationpopulairefrancaise.server.agir

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallSlots
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignups
import org.politisationpopulairefrancaise.core.agir.calls.SlotWithdrawal
import org.politisationpopulairefrancaise.core.agir.calls.slots
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.DAY
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.callTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.nowSeconds
import org.politisationpopulairefrancaise.server.events.EventRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The signups to the slots of calls (AP-11) on the server: places counted, never exceeded, withdrawn, erased. */
class SlotSignupStoreTest {
    private val organizerKeys = IdentityKeys.generate()
    private val organizer = LocalEventSigner(organizerKeys)
    private val events = EventRepository(TestDatabase.dataSource)
    private val store = SlotSignupStore(TestDatabase.dataSource, events)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun publishedCall(places: Int): Call = runBlocking {
        val start = nowSeconds() + DAY
        val event = organizer.call("benevoles", callTags("benevoles", d = "piquet") + listOf(listOf(CallSlots.TAG, "c1", start.toString(), (start + 3600).toString(), places.toString(), "Piquet du matin")))
        events.store(event)
        requireNotNull(Call.from(event))
    }

    @Test
    fun aSlotNeverTakesMoreSignupsThanItsPlacesAndOnlyTheOrganizerReadsThem() = runBlocking {
        val call = publishedCall(places = 2)
        val slot = call.slots.single()
        val first = SlotSignups.prepare(call, slot, "Camille", work = 0)
        assertEquals(SlotSignupResult.Stored(first.request.envelope.id, 1), store.signUp(first.request, work = 0))
        // The same signup sent again takes no second place.
        assertEquals(SlotSignupResult.Stored(first.request.envelope.id, 1), store.signUp(first.request, work = 0))
        assertIs<SlotSignupResult.Stored>(store.signUp(SlotSignups.prepare(call, slot, "Lou", work = 0).request, work = 0))
        assertEquals(SlotSignupResult.Refused(SlotSignupStore.FULL), store.signUp(SlotSignups.prepare(call, slot, "Sam", work = 0).request, work = 0))
        assertEquals(mapOf("c1" to 2), store.counts(call.address.toString()))
        val names = store.envelopes(call.address.toString()).mapNotNull { SlotSignups.open(it, LocalMessagingSigner(organizerKeys), call.address.toString())?.name }
        assertEquals(setOf("Camille", "Lou"), names.toSet())
        // Withdrawn with its token only: then the place is free again.
        assertFalse(store.withdraw(SlotWithdrawal(first.request.envelope.id, "0".repeat(64))))
        assertTrue(store.withdraw(SlotWithdrawal(first.request.envelope.id, first.token)))
        assertEquals(mapOf("c1" to 1), store.counts(call.address.toString()))
    }

    @Test
    fun aSignupToAnUnknownSlotOrForSomeoneElseIsRefused() = runBlocking {
        val call = publishedCall(places = 5)
        val signup = SlotSignups.prepare(call, call.slots.single(), "Camille", work = 0)
        assertEquals(SlotSignupResult.Refused(SlotSignupStore.UNKNOWN_SLOT), store.signUp(signup.request.copy(slot = "c9"), work = 0))
        val other = "30023:${IdentityKeys.generate().publicKey.hex}:piquet"
        // The envelope is for the organizer of the first call, not for the author of this address.
        assertEquals("bad_envelope", (store.signUp(signup.request.copy(call = other), work = 0) as SlotSignupResult.Refused).refusal.code)
        assertEquals("bad_envelope", (store.signUp(signup.request, work = 30) as SlotSignupResult.Refused).refusal.code, "not enough proof of work")
        assertEquals(0, store.purge())
    }
}
