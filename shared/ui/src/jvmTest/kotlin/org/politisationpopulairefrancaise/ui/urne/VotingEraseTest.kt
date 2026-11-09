package org.politisationpopulairefrancaise.ui.urne

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.device.DeviceEraseRegistry
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.urne.PendingBlind
import org.politisationpopulairefrancaise.core.urne.StoredToken
import org.politisationpopulairefrancaise.core.urne.UrneTokenStore
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The part « decider-urne » of the quick erase (S-44): the voting data of the device, erased for good and awaited. */
class VotingEraseTest {
    @Test
    fun theQuickEraseEmptiesTheVotingDataOfTheDeviceAndCanBeRepeated() = runBlocking {
        val store = UrneTokenStore(JvmDatabase.inMemory())
        store.setState("a".repeat(64), registered = true)
        store.saveBlind(PendingBlind(2, ByteArray(32) { 1 }, ByteArray(32) { 2 }, ByteArray(32) { 3 }))
        store.saveToken(StoredToken(1, "decider:revendication:exemple", VoteLevel.NON_VERIFIE, "b".repeat(64), ByteArray(32) { 4 }, ByteArray(32) { 5 }, null, null, null))
        store.choose(1, "pour", sendAfter = 1_794_000_000)
        val voting = VotingState(remote = null, store = store, scope = CoroutineScope(Dispatchers.Unconfined))
        voting.loadDevice()
        assertEquals(setOf(1L), voting.tokens)

        // Registered as the shell does, and erased with the other parts of the device.
        val registry = DeviceEraseRegistry()
        registry.register(deviceErasable(VotingState.ERASABLE_ID) { voting.eraseFromDevice() })
        val report = registry.eraseAll()
        assertEquals(listOf("decider-urne"), report.erased)
        assertTrue(report.complete)
        assertTrue(store.tokens().isEmpty())
        assertTrue(store.blinds().isEmpty())
        assertNull(store.state())
        assertTrue(voting.tokens.isEmpty())
        assertTrue(voting.choices.isEmpty())

        // A second erase (a retry after another part failed) does nothing more and does not fail.
        assertTrue(registry.eraseAll().complete)
    }
}
