package org.politisationpopulairefrancaise.core.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The in-memory database of the tests used by several threads at once, as the applications do
 * (contents imported in the background while screens read and write): every write lands, nothing
 * fails. Before [SharedMemoryDriver], the threads shared one current transaction and tests failed
 * at random (« cannot start a transaction within a transaction », ConcurrentModificationException).
 */
class SharedMemoryDriverTest {

    @Test
    fun severalThreadsWriteAndReadAtOnce() = runBlocking {
        val database = JvmDatabase.inMemory()
        val store = LocalEventStore(database, clock = { 1_700_000_000 })
        val writers = (1..4).map { LocalEventSigner(IdentityKeys.generate()) }
        val perWriter = 60
        var notified = 0
        database.eventQueries.selectRecent(10L).addListener { synchronized(this@SharedMemoryDriverTest) { notified++ } }
        val jobs = writers.mapIndexed { w, signer ->
            async(Dispatchers.Default) {
                repeat(perWriter) { i ->
                    val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] $w-$i", createdAt = 1L + i)
                    assertEquals(LocalStoreOutcome.STORED, store.store(event))
                    // A read between two writes, from the same threads.
                    store.recent(limit = 5)
                }
            }
        } + async(Dispatchers.Default) {
            // Nested transactions on another thread, at the same time.
            repeat(perWriter) {
                database.transaction { database.transaction { database.eventQueries.selectRecent(1L).executeAsList() } }
            }
        }
        jobs.awaitAll()
        assertEquals((writers.size * perWriter).toLong(), store.count())
        assertEquals(true, notified > 0, "the listeners of the queries are told about the writes")
    }
}
