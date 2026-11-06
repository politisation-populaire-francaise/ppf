package org.politisationpopulairefrancaise.server.relay

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

class WrapRetentionTest {

    private val repository = EventRepository(TestDatabase.dataSource)
    private val sender = LocalEventSigner(IdentityKeys.generate())
    private val recipient = "f".repeat(64)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun wrap(content: String): SignedEvent = runBlocking {
        // NIP-59 randomises created_at: the retention must not depend on it.
        sender.sign(kind = RelayKinds.GIFT_WRAP, content = content, tags = listOf(listOf("p", recipient)), createdAt = 1)
    }

    private fun backdate(event: SignedEvent, receivedAt: Instant) {
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE events SET received_at = ? WHERE id = ?").use { statement ->
                statement.setObject(1, receivedAt.atOffset(ZoneOffset.UTC))
                statement.setString(2, event.id)
                assertEquals(1, statement.executeUpdate())
            }
        }
    }

    @Test
    fun deletesOnlyTheEnvelopesReceivedBeforeTheRetentionPeriod() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val retention = WrapRetention(repository, 30.days, Clock.fixed(now, ZoneOffset.UTC))
        val fresh = wrap("[EXEMPLE] enveloppe récente")
        val stale = wrap("[EXEMPLE] enveloppe ancienne")
        val limit = wrap("[EXEMPLE] enveloppe à la limite")
        val oldNote = runBlocking { sender.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] note ancienne", createdAt = 1) }
        listOf(fresh, stale, limit, oldNote).forEach(repository::store)
        backdate(fresh, now.minusSeconds(29 * 86_400))
        backdate(stale, now.minusSeconds(31 * 86_400))
        backdate(limit, now.minusSeconds(30 * 86_400))
        backdate(oldNote, now.minusSeconds(400 * 86_400))

        assertEquals(1, retention.purgeOnce())
        assertNotNull(repository.find(fresh.id))
        assertNotNull(repository.find(limit.id), "received exactly at the cutoff is kept")
        assertNull(repository.find(stale.id))
        assertNotNull(repository.find(oldNote.id), "only envelopes expire")
        // The tag rows went with the envelope.
        assertEquals(listOf(limit, fresh).sortedBy { it.id }, repository.query(EventFilter(tags = mapOf("p" to listOf(recipient)))).sortedBy { it.id })
        assertEquals(0, retention.purgeOnce())
    }
}
